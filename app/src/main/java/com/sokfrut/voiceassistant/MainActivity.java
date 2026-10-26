package com.sokfrut.voiceassistant;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Первый этап: кнопка, распознавание русской речи и простые команды.
 * Команды:
 *   «навигация до <место>» / «маршрут до <место>» — открыть маршрут в Яндекс Картах
 *   (если их нет, в Google Maps).
 */
public class MainActivity extends Activity {

    private static final int REQ_SPEECH = 1;
    private static final int REQ_AUDIO = 2;

    private TextView resultView;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        Button speakButton = new Button(this);
        speakButton.setText("Говорить");
        speakButton.setOnClickListener(v -> startListening());
        root.addView(speakButton);

        resultView = new TextView(this);
        resultView.setGravity(Gravity.CENTER);
        resultView.setPadding(0, pad, 0, 0);
        resultView.setText("Нажмите «Говорить» и скажите, например: «навигация до Тверской улицы»");
        root.addView(resultView);

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
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите");
        try {
            startActivityForResult(intent, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "Распознавание речи недоступно на этом телефоне", Toast.LENGTH_LONG).show();
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
        if (tts != null) {
            tts.shutdown();
        }
        super.onDestroy();
    }
}
