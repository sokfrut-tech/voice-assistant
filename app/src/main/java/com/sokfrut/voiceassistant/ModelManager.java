package com.sokfrut.voiceassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Скачивает и обновляет офлайн-модель распознавания речи.
 *
 * Порядок безопасности:
 *  1. Описание модели берётся только по HTTPS из репозитория проекта.
 *  2. Скачанный файл сверяется с контрольной суммой SHA-256 из описания.
 *  3. Распаковка защищена от выхода за пределы папки и от «бомб» (лимит размера).
 *  4. Новая модель заменяет старую только после полной успешной установки.
 */
public class ModelManager {

    public interface Listener {
        void onStatus(String text);
        void onDone(File modelDir);
        void onError(String message);
    }

    static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/sokfrut-tech/voice-assistant/main/models/manifest.json";
    private static final String MODEL_KEY = "vosk-ru";
    private static final String PREFS = "models";
    private static final String KEY_VERSION = "version";
    private static final String KEY_DIR = "dir";
    private static final long MAX_UNPACKED_BYTES = 800L * 1024 * 1024;
    private static final int TIMEOUT_MS = 20000;

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Установленная модель или null. */
    public File installedModel() {
        String dir = prefs.getString(KEY_DIR, null);
        if (dir == null) {
            return null;
        }
        File f = new File(dir);
        return f.isDirectory() ? f : null;
    }

    public String installedVersion() {
        return prefs.getString(KEY_VERSION, "");
    }

    /** Проверяет описание модели и при необходимости скачивает и устанавливает новую версию. */
    public void checkAndInstall(Listener listener) {
        executor.execute(() -> {
            try {
                run(listener);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                main.post(() -> listener.onError(msg));
            }
        });
    }

    private void run(Listener l) throws Exception {
        status(l, "Проверяю обновления модели…");
        JSONObject entry = new JSONObject(readText(MANIFEST_URL))
                .getJSONObject("models").getJSONObject(MODEL_KEY);

        String version = entry.getString("version");
        String url = entry.getString("url");
        String sha256 = entry.getString("sha256").toLowerCase(Locale.ROOT);
        String folder = entry.getString("folder");

        if (!url.startsWith("https://")) {
            throw new IOException("Адрес модели должен быть https");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("В описании модели нет контрольной суммы");
        }
        if (folder.contains("/") || folder.contains("\\") || folder.contains("..")) {
            throw new IOException("Недопустимое имя папки модели");
        }

        File current = installedModel();
        if (current != null && version.equals(installedVersion())) {
            main.post(() -> l.onDone(current));
            return;
        }

        File zip = new File(context.getCacheDir(), "model.zip");
        status(l, "Скачиваю модель " + version + "…");
        String actual = download(url, zip, l);
        if (!actual.equals(sha256)) {
            zip.delete();
            throw new IOException("Контрольная сумма не совпала: файл повреждён или подменён");
        }

        status(l, "Устанавливаю модель…");
        File modelsRoot = new File(context.getFilesDir(), "models");
        File tmp = new File(modelsRoot, "vosk-ru-" + version + ".tmp");
        File target = new File(modelsRoot, "vosk-ru-" + version);
        deleteRecursively(tmp);
        deleteRecursively(target);
        if (!tmp.mkdirs()) {
            throw new IOException("Не удалось создать папку для модели");
        }
        unzip(zip, tmp);
        zip.delete();

        File unpacked = new File(tmp, folder);
        if (!unpacked.isDirectory()) {
            deleteRecursively(tmp);
            throw new IOException("В архиве нет ожидаемой папки модели");
        }
        if (!unpacked.renameTo(target)) {
            deleteRecursively(tmp);
            throw new IOException("Не удалось установить модель");
        }
        deleteRecursively(tmp);

        String oldDir = prefs.getString(KEY_DIR, null);
        prefs.edit().putString(KEY_VERSION, version).putString(KEY_DIR, target.getAbsolutePath()).apply();
        if (oldDir != null && !oldDir.equals(target.getAbsolutePath())) {
            deleteRecursively(new File(oldDir));
        }
        main.post(() -> l.onDone(target));
    }

    private void status(Listener l, String text) {
        main.post(() -> l.onStatus(text));
    }

    private HttpURLConnection open(String address) throws IOException {
        if (!address.startsWith("https://")) {
            throw new IOException("Разрешены только https-адреса");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != 200) {
            throw new IOException("Сервер ответил кодом " + code + " для " + address);
        }
        return c;
    }

    private String readText(String address) throws Exception {
        HttpURLConnection c = open(address);
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 1024 * 1024) {
                    throw new IOException("Описание модели слишком большое");
                }
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Скачивает файл и возвращает его SHA-256 (в нижнем регистре). */
    private String download(String address, File dest, Listener l) throws Exception {
        HttpURLConnection c = open(address);
        long total = c.getContentLengthLong();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            long lastReport = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
                done += n;
                if (total > 0 && done - lastReport > 2L * 1024 * 1024) {
                    lastReport = done;
                    int percent = (int) (done * 100 / total);
                    status(l, "Скачиваю модель… " + percent + "%");
                }
            }
        } finally {
            c.disconnect();
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }

    private void unzip(File zip, File destDir) throws IOException {
        String base = destDir.getCanonicalPath() + File.separator;
        long total = 0;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buf = new byte[64 * 1024];
            while ((entry = zin.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (!out.getCanonicalPath().startsWith(base)) {
                    throw new IOException("Небезопасный путь в архиве");
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_UNPACKED_BYTES) {
                            throw new IOException("Архив слишком большой после распаковки");
                        }
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
