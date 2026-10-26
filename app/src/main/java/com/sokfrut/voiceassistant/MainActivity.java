package com.sokfrut.voiceassistant;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Голосовой помощник, этап 2.
 *  - Офлайн-распознавание русской речи (Vosk), если модель установлена.
 *  - Если модели нет — системное распознавание речи.
 *  - Кнопка «Обновить модель» скачивает и проверяет модель через интернет.
 * Команды: «навигация до <место>» / «маршрут до <место>».
 */
public class MainActivity extends Activity {

    private static final int REQ_SPEECH = 1;
    private static final int REQ_AUDIO = 2;
    private static final float SAMPLE_RATE = 16000.0f;

    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView resultView;
    private TextView statusView;
    private Button speakButton;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private ModelManager modelManager;
    private Model voskModel;
    private SpeechService speechService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        speakButton = new Button(this);
        speakButton.setText("Говорить");
        speakButton.setOnClickListener(v -> onSpeakPressed());
        root.addView(speakButton);

        Button updateButton = new Button(this);
        updateButton.setText("Обновить модель");
        updateButton.setOnClickListener(v -> updateModel());
        root.addView(updateButton);

        resultView = new TextView(this);
        resultView.setGravity(Gravity.CENTER);
        resultView.setPadding(0, pad, 0, 0);
        resultView.setText("Нажмите «Говорить» и скажите, например: «навигация до Тверской улицы»");
        root.addView(resultView);

        statusView = new TextView(this);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, pad / 2, 0, 0);
        root.addView(statusView);

        setContentView(root);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("ru", "RU"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        }

        modelManager = new ModelManager(this);
        File installed = modelManager.installedModel();
        if (installed != null) {
            loadModel(installed);
        } else {
            statusView.setText("Офлайн-модель не установлена. Нажмите «Обновить модель».");
        }
    }

    // ---------- модель ----------

    private void updateModel() {
        modelManager.checkAndInstall(new ModelManager.Listener() {
            @Override
            public void onStatus(String text) {
                statusView.setText(text);
            }

            @Override
            public void onDone(File modelDir) {
                loadModel(modelDir);
            }

            @Override
            public void onError(String message) {
                statusView.setText("Ошибка обновления: " + message);
            }
        });
    }

    private void loadModel(File dir) {
        statusView.setText("Загружаю офлайн-модель…");
        new Thread(() -> {
            try {
                Model model = new Model(dir.getAbsolutePath());
                main.post(() -> {
                    stopListening();
                    if (voskModel != null) {
                        voskModel.close();
                    }
                    voskModel = model;
                    statusView.setText("Офлайн-распознавание готово (модель "
                            + modelManager.installedVersion() + ")");
                });
            } catch (Exception | UnsatisfiedLinkError e) {
                main.post(() -> statusView.setText("Не удалось загрузить модель: " + e.getMessage()));
            }
        }).start();
    }

    // ---------- распознавание ----------

    private void onSpeakPressed() {
        if (voskModel == null) {
            startSystemRecognizer();
            return;
        }
        if (speechService != null) {
            stopListening();
            return;
        }
        try {
            Recognizer recognizer = new Recognizer(voskModel, SAMPLE_RATE);
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            speechService.startListening(new RecognitionListener() {
                @Override
                public void onPartialResult(String hypothesis) {
                    String text = extract(hypothesis, "partial");
                    if (!text.isEmpty()) {
                        resultView.setText("… " + text);
                    }
                }

                @Override
                public void onResult(String hypothesis) {
                    String text = extract(hypothesis, "text");
                    if (!text.isEmpty()) {
                        resultView.setText("Вы сказали: " + text);
                        stopListening();
                        handleCommand(text);
                    }
                }

                @Override
                public void onFinalResult(String hypothesis) {
                    String text = extract(hypothesis, "text");
                    if (!text.isEmpty()) {
                        resultView.setText("Вы сказали: " + text);
                        stopListening();
                        handleCommand(text);
                    }
                }

                @Override
                public void onError(Exception exception) {
                    statusView.setText("Ошибка распознавания: " + exception.getMessage());
                    stopListening();
                }

                @Override
                public void onTimeout() {
                    stopListening();
                }
            });
            speakButton.setText("Слушаю… (нажмите, чтобы остановить)");
        } catch (IOException e) {
            statusView.setText("Не удалось запустить микрофон: " + e.getMessage());
            speechService = null;
        }
    }

    private void stopListening() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        speakButton.setText("Говорить");
    }

    private static String extract(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void startSystemRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите");
        try {
            startActivityForResult(intent, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "Системное распознавание недоступно. Нажмите «Обновить модель» для офлайн-режима.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SPEECH || resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            return;
        }
        String said = results.get(0);
        resultView.setText("Вы сказали: " + said);
        handleCommand(said);
    }

    // ---------- команды ----------

    private void handleCommand(String text) {
        String lower = text.toLowerCase(new Locale("ru", "RU")).trim();

        String destination = null;
        for (String prefix : new String[]{"навигация до ", "маршрут до ", "проложи маршрут до "}) {
            if (lower.startsWith(prefix)) {
                destination = text.substring(prefix.length()).trim();
                break;
            }
        }

        if (destination != null && !destination.isEmpty()) {
            openNavigation(destination);
            speak("Открываю маршрут до " + destination);
        } else {
            speak("Пока я умею только строить маршруты. Скажите: навигация до и название места.");
        }
    }

    private void openNavigation(String destination) {
        Uri yandex = Uri.parse("yandexmaps://maps.yandex.ru/?text=" + Uri.encode(destination));
        Intent yandexIntent = new Intent(Intent.ACTION_VIEW, yandex);
        yandexIntent.setPackage("ru.yandex.yandexmaps");
        if (yandexIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(yandexIntent);
            return;
        }

        Uri google = Uri.parse("google.navigation:q=" + Uri.encode(destination));
        Intent googleIntent = new Intent(Intent.ACTION_VIEW, google);
        googleIntent.setPackage("com.google.android.apps.maps");
        if (googleIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(googleIntent);
            return;
        }

        Uri geo = Uri.parse("geo:0,0?q=" + Uri.encode(destination));
        startActivity(new Intent(Intent.ACTION_VIEW, geo));
    }

    private void speak(String text) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply");
        }
    }

    @Override
    protected void onDestroy() {
        stopListening();
        if (voskModel != null) {
            voskModel.close();
        }
        if (tts != null) {
            tts.shutdown();
        }
        super.onDestroy();
    }
}
