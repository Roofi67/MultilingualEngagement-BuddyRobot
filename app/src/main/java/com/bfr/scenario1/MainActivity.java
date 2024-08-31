package com.bfr.scenario1;

import static android.os.SystemClock.sleep;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bfr.buddy.ui.shared.FacialExpression;
import com.bfr.buddy.ui.shared.LabialExpression;
import com.bfr.buddy.speech.shared.ISTTCallback;
import com.bfr.buddy.speech.shared.STTResult;
import com.bfr.buddy.speech.shared.STTResultsData;
import com.bfr.buddysdk.BuddyActivity;
import com.bfr.buddysdk.BuddySDK;
import com.bfr.buddysdk.services.speech.STTTask;
import com.bfr.buddy.usb.shared.IUsbCommadRsp;
import com.bumptech.glide.Glide;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import com.microsoft.cognitiveservices.speech.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

public class MainActivity extends BuddyActivity {
    private static final String SUBSCRIPTION_KEY = "522c4a2067f34864aaa6a35388cc4e1c";
    private static final String SERVICE_REGION = "westeurope";
    private static final int PERMISSIONS_REQUEST_CODE = 1;

    private static final String TAG = "MainActivity";
    private ListView listViewFiles;
    private Button buttonBrowse;
    private TextView recognizedText;
    private ImageView imageView;
    private LinearLayout mainButtonsContainer;
    private Button buttonBack;
    private TextView sttState;
    private Handler handler = new Handler(Looper.getMainLooper());
    private String folderPath = "/storage/emulated/0/Movies";

    private STTTask sttTask;
    private boolean isSpeechServiceReady = false;
    private boolean isListening = false; // Flag to check if listening is active

    private SpeechRecognizer recognizer;
    private boolean isProcessing = false; // To prevent duplicate processing of the same speech input

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        configureListeners();
        checkPermissions();
    }

    private void initViews() {
        mainButtonsContainer = findViewById(R.id.mainButtonsContainer);
        buttonBrowse = findViewById(R.id.button_browse);
        Button buttonHello = findViewById(R.id.buttonHello);
        Button buttonListen = findViewById(R.id.button_listen);
        Button buttonAdvance = findViewById(R.id.button_advance);
        listViewFiles = findViewById(R.id.listView_files);
        recognizedText = findViewById(R.id.recognizedText);
        imageView = findViewById(R.id.imageView);
        buttonBack = findViewById(R.id.button_back);
        sttState = findViewById(R.id.sttState);

        buttonHello.setOnClickListener(view -> {
            BuddySDK.Speech.startSpeaking("Hello, I am Buddy");
        });
        buttonListen.setOnClickListener(v -> {
            mainButtonsContainer.setVisibility(View.GONE);
            startContinuousRecognition();
        });
        buttonAdvance.setOnClickListener(view -> {
            mainButtonsContainer.setVisibility(View.GONE);
            recognizedText.setText("Advancing...");
            recognizedText.setVisibility(View.VISIBLE);
            showBackButton();
            AdvanceFunct();
        });
        buttonBack.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            }
            showMainButtons();
        });
    }

    private void configureListeners() {
        buttonBrowse.setOnClickListener(v -> {
            mainButtonsContainer.setVisibility(View.GONE);
            listViewFiles.setVisibility(View.VISIBLE);
            showBackButton();
            checkPermissionsAndLoadFiles();
        });
        listViewFiles.setOnItemClickListener(this::onVideoSelected);
    }

    private void checkPermissionsAndLoadFiles() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
        } else {
            loadVideoFiles();
        }
    }

    private void loadVideoFiles() {
        File directory = new File(folderPath);
        ArrayList<String> videoNames = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (file.getName().endsWith(".mp4")) {
                    videoNames.add(file.getName());
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoNames);
            listViewFiles.setAdapter(adapter);
        } else {
            Toast.makeText(this, "No videos found.", Toast.LENGTH_LONG).show();
        }
    }

    private void onVideoSelected(AdapterView<?> parent, View view, int position, long id) {
        String filePath = folderPath + "/" + parent.getItemAtPosition(position).toString();
        Log.d(TAG, "onVideoSelected: FilePath: " + filePath);
        playVideo(filePath);
    }

    private void playVideo(String filePath) {
        try {
            File videoFile = new File(filePath);
            Uri videoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", videoFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "playVideo: Exception: " + e.getMessage(), e);
            Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show();
        }
    }

    private void AdvanceFunct() {
        float initialSpeed = 0.3F;
        float initialDistance = 1.2F;
        float angle = -110;
        float rotspeed = 90;
        float secondSpeed = 0.3F;
        float secondDistance = 0.8F;

        long moveDuration = calculateMoveDuration(initialSpeed, initialDistance);
        long rotationDuration = calculateRotationDuration(angle, rotspeed);

        moveBuddy(initialSpeed, initialDistance, () -> {
            handler.postDelayed(() -> rotateBuddy(rotspeed, angle, () -> {
                handler.postDelayed(() -> moveBuddy(secondSpeed, secondDistance, null), rotationDuration);
            }), moveDuration);
        });
    }

    private long calculateMoveDuration(float speed, float distance) {
        return (long) (distance / speed * 1000);
    }

    private long calculateRotationDuration(float angle, float rotspeed) {
        return (long) (Math.abs(angle) / rotspeed * 1000);
    }

    private void moveBuddy(float speed, float distance, Runnable onSuccess) {
        Log.i(TAG, "Sending moveBuddy command: speed=" + speed + ", distance=" + distance);
        BuddySDK.USB.moveBuddy(speed, distance, new IUsbCommadRsp.Stub() {
            @Override
            public void onSuccess(String s) throws RemoteException {
                Log.i(TAG, "moveBuddy success: " + s);
                runOnUiThread(() -> recognizedText.setText("Move successful"));
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }

            @Override
            public void onFailed(String s) throws RemoteException {
                Log.i(TAG, "moveBuddy failed: " + s);
                runOnUiThread(() -> recognizedText.setText("Fail to advance"));
            }
        });
    }

    private void rotateBuddy(float rotspeed, float angle, Runnable onSuccess) {
        Log.i(TAG, "Sending rotateBuddy command: rotspeed=" + rotspeed + ", angle=" + angle);
        BuddySDK.USB.rotateBuddy(rotspeed, angle, new IUsbCommadRsp.Stub() {
            @Override
            public void onSuccess(String s) throws RemoteException {
                Log.i(TAG, "rotateBuddy success: " + s);
                runOnUiThread(() -> recognizedText.setText("Rotate successful"));
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }

            @Override
            public void onFailed(String s) throws RemoteException {
                Log.i(TAG, "rotateBuddy failed: " + s);
                runOnUiThread(() -> recognizedText.setText("Fail to rotate"));
            }
        });
    }

    private void initializeRecognizer() {
        SpeechConfig config = SpeechConfig.fromSubscription(SUBSCRIPTION_KEY, SERVICE_REGION);
        List<String> languages = Arrays.asList("en-US", "fr-FR", "it-IT");
        AutoDetectSourceLanguageConfig autoDetectSourceLanguageConfig = AutoDetectSourceLanguageConfig.fromLanguages(languages);

        recognizer = new SpeechRecognizer(config, autoDetectSourceLanguageConfig);

        recognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                Log.i(TAG, "Recognized: " + e.getResult().getText());
                runOnUiThread(() -> recognizedText.setText("Recognized: " + e.getResult().getText()));
                String speechText = e.getResult().getText().toLowerCase();
                respondToGreeting(speechText);
                updateImageViewBasedOnSpeech(speechText);
            } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                Log.i(TAG, "No speech could be recognized.");
                runOnUiThread(() -> recognizedText.setText("No speech could be recognized."));
            }
        });

        recognizer.canceled.addEventListener((s, e) -> {
            Log.i(TAG, "Canceled: Reason=" + e.getReason() + "\nErrorDetails: " + e.getErrorDetails());
            runOnUiThread(() -> recognizedText.setText("Recognition canceled."));
        });

        recognizer.sessionStarted.addEventListener((s, e) -> Log.i(TAG, "Session started."));
        recognizer.sessionStopped.addEventListener((s, e) -> Log.i(TAG, "Session stopped."));
    }

    private void startContinuousRecognition() {
        try {
            if (recognizer == null) {
                initializeRecognizer();
            }
            recognizer.startContinuousRecognitionAsync();
            isListening = true;
            runOnUiThread(() -> {
                sttState.setText("Listening...");
                sttState.setVisibility(View.VISIBLE);
                buttonBack.setVisibility(View.VISIBLE);
                recognizedText.setVisibility(View.VISIBLE); // Ensure the recognized text view is visible
            });
        } catch (Exception e) {
            Log.e(TAG, "Error starting continuous recognition: " + e.getMessage());
        }
    }

    private void updateImageViewBasedOnSpeech(String speechText) {
        int drawableId = -1;
        boolean isGif = false;

        if (speechText.contains("cat")) {
            drawableId = R.drawable.cat_image;
        } else if (speechText.contains("dog")) {
            drawableId = R.drawable.dog_image;
        } else if (speechText.contains("goodbye") || speechText.contains("arrivederci") || speechText.contains("au revoir")) {
            isGif = true;
        }

        if (!isGif && drawableId != -1) {
            Drawable image = ContextCompat.getDrawable(getApplicationContext(), drawableId);
            if (image != null) {
                runOnUiThread(() -> {
                    imageView.setImageDrawable(image);
                    imageView.setVisibility(View.VISIBLE);
                });
            } else {
                Log.e(TAG, "Drawable not found");
                runOnUiThread(() -> imageView.setVisibility(View.GONE));
            }
        } else if (isGif) {
            runOnUiThread(() -> {
                Glide.with(this).asGif().load(R.drawable.hello_gif).into(imageView);
                imageView.setVisibility(View.VISIBLE);
            });
        } else {
            runOnUiThread(() -> imageView.setVisibility(View.GONE));
        }

        runOnUiThread(() -> {
            sttState.setVisibility(View.GONE);
            buttonBack.setVisibility(View.VISIBLE);
        });
    }

    private synchronized void respondToGreeting(String speechText) {
        if (isProcessing) {
            return; // Skip processing if we're already processing a speech input
        }

        if (speechText.contains("hello") || speechText.contains("hi") || speechText.contains("how are you") ||
                speechText.contains("ciao") || speechText.contains("come stai") ||
                speechText.contains("bonjour") || speechText.contains("comment ça va")) {

            isProcessing = true; // Set the flag to indicate we're processing

            if (speechText.contains("ciao") || speechText.contains("come stai")) {
                // Italian greeting detected
                sayText("Ciao! Come stai? Sono Buddy, il tuo amichevole compagno robot." +
                        " Spero che tu stia passando una buona giornata." +
                        " Ti auguro una giornata ancora più luminosa. Buona fortuna!", "it-IT-IsabellaMultilingualNeural");
            } else if (speechText.contains("bonjour") || speechText.contains("comment ça va")) {
                // French greeting detected
                sayText("Bonjour! Comment ça va? Je suis Buddy, ton compagnon robot amical." +
                        " J'espère que tu passes une bonne journée." +
                        " Je te souhaite une journée encore plus lumineuse. Bonne chance!", "it-IT-IsabellaMultilingualNeural");
            } else {
                // Default to English
                sayText("Hello! how are you? I'm Buddy, your friendly robot companion." +
                        " I hope you are having a good day." +
                        " I wish you a brighter day ahead. Good luck", "it-IT-IsabellaMultilingualNeural");
            }
        }
    }

    private void sayText(String text, String voiceName) {
        handler.post(() -> {
            try {
                SpeechConfig config = SpeechConfig.fromSubscription(SUBSCRIPTION_KEY, SERVICE_REGION);
                config.setSpeechSynthesisVoiceName(voiceName);
                SpeechSynthesizer synthesizer = new SpeechSynthesizer(config);

                new Thread(() -> {
                    try {
                        Thread.sleep(500); // Sleep for 0.5 seconds
                        BuddySDK.UI.setLabialExpression(LabialExpression.SPEAK_HAPPY);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                SpeechSynthesisResult result = synthesizer.SpeakText(text);
                BuddySDK.UI.setLabialExpression(LabialExpression.NO_EXPRESSION);

                result.close();
                synthesizer.close();
            } catch (Exception e) {
                Log.i("info", "Error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isProcessing = false; // Reset the flag after processing is complete
            }
        });
    }

    private void stopListening() {
        if (recognizer != null) {
            recognizer.stopContinuousRecognitionAsync();
            isListening = false;
            runOnUiThread(() -> {
                sttState.setText("Stopped listening.");
                sttState.setVisibility(View.GONE);
                showMainButtons();
            });
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_CODE);
            } else {
                initializeRecognizer();
            }
        } else {
            initializeRecognizer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                initializeRecognizer();
            } else {
                Log.i("info", "Permissions not granted!");
                Toast.makeText(this, "Permissions are required for this app to function", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showMainButtons() {
        mainButtonsContainer.setVisibility(View.VISIBLE);
        listViewFiles.setVisibility(View.GONE);
        recognizedText.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        buttonBack.setVisibility(View.GONE);
        sttState.setVisibility(View.GONE);
    }

    private void showBackButton() {
        buttonBack.setVisibility(View.VISIBLE);
    }
}
