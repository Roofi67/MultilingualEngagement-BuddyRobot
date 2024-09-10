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
        Button buttonListen = findViewById(R.id.button_listen);
        listViewFiles = findViewById(R.id.listView_files);
        recognizedText = findViewById(R.id.recognizedText);
        imageView = findViewById(R.id.imageView);
        buttonBack = findViewById(R.id.button_back);
        sttState = findViewById(R.id.sttState);

        buttonListen.setOnClickListener(v -> {
            mainButtonsContainer.setVisibility(View.GONE);
            startContinuousRecognition();
        });

        buttonBack.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            }
            showMainButtons();
        });
    }

    private void configureListeners() {
        listViewFiles.setOnItemClickListener(this::onVideoSelected);
    }

    private void checkPermissionsAndLoadFiles() {
        Log.i(TAG, "checkPermissionsAndLoadFiles: Checking permissions for READ_EXTERNAL_STORAGE");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "checkPermissionsAndLoadFiles: Permission not granted. Requesting permission.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
        } else {
            Log.i(TAG, "checkPermissionsAndLoadFiles: Permission granted. Loading video files.");
            loadVideoFiles();
        }
    }

    private void loadVideoFiles() {
        Log.i(TAG, "loadVideoFiles: Loading video files from directory.");
        File directory = new File(folderPath);
        ArrayList<String> videoNames = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".mp4")) {
                        videoNames.add(file.getName());
                        Log.d(TAG, "Loaded video file: " + file.getName());
                    }
                }
            }
            runOnUiThread(() -> {
                if (!videoNames.isEmpty()) {
                    Log.i(TAG, "loadVideoFiles: Videos found. Updating ListView.");
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoNames);
                    listViewFiles.setAdapter(adapter);
                    listViewFiles.setVisibility(View.VISIBLE);
                    mainButtonsContainer.setVisibility(View.GONE);
                    showBackButton();
                } else {
                    Log.i(TAG, "loadVideoFiles: No videos found.");
                    Toast.makeText(this, "No videos found.", Toast.LENGTH_LONG).show();
                }
            });
        } else {
            runOnUiThread(() -> {
                Log.i(TAG, "loadVideoFiles: Directory not found.");
                Toast.makeText(this, "Directory not found.", Toast.LENGTH_LONG).show();
            });
        }
    }


    private void onVideoSelected(AdapterView<?> parent, View view, int position, long id) {
        // Fetch the file path when the video is selected manually
        String filePath = folderPath + "/" + parent.getItemAtPosition(position).toString();
        Log.d(TAG, "onVideoSelected (Manual Press): FilePath: " + filePath);  // Log the file path for manual press

        // Play the video
        playVideo(filePath);
    }

    private void playVideo(String filePath) {
        try {
            File videoFile = new File(filePath);
            Log.d(TAG, "Attempting to play video at path: " + videoFile.getAbsolutePath());  // Log the absolute path

            if (!videoFile.exists()) {
                Log.e(TAG, "File does not exist: " + filePath);
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }

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
    private void moveBuddy(float speed, float distance, Runnable onSuccess) {
        Log.i(TAG, "Sending moveBuddy command: speed=" + speed + ", distance=" + distance);

        runOnUiThread(() -> {
            recognizedText.setText("Moving...");
            recognizedText.setVisibility(View.VISIBLE);
        });

        BuddySDK.USB.moveBuddy(speed, distance, new IUsbCommadRsp.Stub() {
            @Override
            public void onSuccess(String s) throws RemoteException {
                Log.i(TAG, "moveBuddy success: " + s);
                runOnUiThread(() -> {
                    recognizedText.setText("Move successful");
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                });
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

        runOnUiThread(() -> {
            recognizedText.setText("Rotating...");
            recognizedText.setVisibility(View.VISIBLE);
        });

        BuddySDK.USB.rotateBuddy(rotspeed, angle, new IUsbCommadRsp.Stub() {
            @Override
            public void onSuccess(String s) throws RemoteException {
                Log.i(TAG, "rotateBuddy success: " + s);
                runOnUiThread(() -> {
                    recognizedText.setText("Rotate successful");
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                });
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

                runOnUiThread(() -> {
                    processSpeechCommand(speechText);
                    updateImageViewBasedOnSpeech(speechText);
                });
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
                recognizedText.setVisibility(View.VISIBLE);
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

    private synchronized void processSpeechCommand(String speechText) {
        Log.i(TAG, "Processing command: " + speechText);
        if (isProcessing) {
            Log.i(TAG, "Already processing another command.");
            return;
        }
        isProcessing = true;

        // Normalize the input to lower case and handle case sensitivity and spaces.
        final String normalizedSpeechText = speechText.toLowerCase().trim();

        if (normalizedSpeechText.startsWith("play ")) {
            // Extract video name from the command, removing "play " and trimming any spaces
            final String videoNameFromCommand = normalizedSpeechText.substring(5).trim().replaceAll("\\s+", "");

            // Ensure the video list is loaded before proceeding
            if (listViewFiles.getAdapter() == null) {
                Log.i(TAG, "Loading videos because the video list is not yet available.");
                checkPermissionsAndLoadFiles(); // Load the video files
            }

            // Delayed execution to wait for file load and list update
            handler.postDelayed(() -> {
                ArrayAdapter<String> adapter = (ArrayAdapter<String>) listViewFiles.getAdapter();
                if (adapter != null) {
                    boolean found = false;
                    for (int i = 0; i < adapter.getCount(); i++) {
                        String item = adapter.getItem(i); // Get the actual file name from the list

                        // Extract the file name without extension and remove spaces to match the speech command
                        String fileNameWithoutExtension = item.substring(0, item.lastIndexOf('.')).replaceAll("\\s+", "");

                        // Compare the normalized file name and the command name, ignoring case
                        if (fileNameWithoutExtension.equalsIgnoreCase(videoNameFromCommand)) {
                            Log.d(TAG, "Video found: " + videoNameFromCommand + ", simulating click.");
                            onVideoSelected(listViewFiles, null, i, i); // Simulate video selection
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        Log.e(TAG, "Video not found: " + videoNameFromCommand);
                        runOnUiThread(() -> Toast.makeText(this, "Video not found: " + videoNameFromCommand, Toast.LENGTH_SHORT).show());
                    }
                } else {
                    Log.e(TAG, "Video list not available yet. Cannot play video.");
                    runOnUiThread(() -> Toast.makeText(this, "Video list not available.", Toast.LENGTH_SHORT).show());
                }
            }, 1000); // Adjust delay based on file load time
        } else if (normalizedSpeechText.contains("show the videos")) {
            Log.i(TAG, "Command to show videos recognized.");
            runOnUiThread(this::checkPermissionsAndLoadFiles);
        } else if (normalizedSpeechText.contains("remove the videos")) {
            Log.i(TAG, "Command to remove videos recognized.");
            runOnUiThread(() -> {
                listViewFiles.setVisibility(View.GONE);  // Hide the video list
                recognizedText.setText("Videos list removed.");
                recognizedText.setVisibility(View.VISIBLE);
            });
        } else if (normalizedSpeechText.contains("move forward")) {
            Log.i(TAG, "Command to move forward recognized.");
            runOnUiThread(() -> {
                moveBuddy(0.3F, 0.2F, () -> {
                    recognizedText.setText("Move successful");
                    showBackButton();
                    continueListening();
                });
            });
        } else if (normalizedSpeechText.contains("turn left")) {
            Log.i(TAG, "Command to rotate left recognized.");
            runOnUiThread(() -> {
                rotateBuddy(90, 110, () -> {
                    recognizedText.setText("Rotate successful");
                    showBackButton();
                    continueListening();
                });
            });
        } else if (normalizedSpeechText.contains("turn right")) {
            Log.i(TAG, "Command to rotate right recognized.");
            runOnUiThread(() -> {
                rotateBuddy(90, -110, () -> {
                    recognizedText.setText("Rotate successful");
                    showBackButton();
                    continueListening();
                });
            });
        } else {
            handleGreetings(normalizedSpeechText);
        }

        isProcessing = false;
    }
    private void playVideoByName(String videoName) {
        // Log the original video name from the speech command
        Log.e(TAG, "Original video name from speech command: " + videoName);

        // Normalize the video name to remove spaces and capitalize the first letter
        videoName = videoName.replaceAll("\\s+", "");  // Remove spaces
        videoName = Character.toUpperCase(videoName.charAt(0)) + videoName.substring(1);  // Capitalize first letter

        // Log the normalized video name
        Log.e(TAG, "Normalized video name: " + videoName);

        boolean found = false;
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) listViewFiles.getAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                String item = adapter.getItem(i);
                String fileNameWithoutExtension = item.substring(0, item.lastIndexOf('.'));

                // Log the file being checked against the command
                Log.e(TAG, "Checking file: " + fileNameWithoutExtension + " against command: " + videoName);

                // If the file name matches the command
                if (fileNameWithoutExtension.equalsIgnoreCase(videoName)) {
                    // Generate the file path
                    String filePath = folderPath + "/" + item;

                    // Log the file path being used for playback
                    Log.e(TAG, "playVideoByName (Speech Command): FilePath: " + filePath);  // Log the file path for speech command

                    // Play the video
                    playVideo(filePath);
                    found = true;
                    break;
                }
            }

            // If no video is found, log the attempted path
            if (!found) {
                // Log the path that was tried, assuming the file would have been named videoName.mp4
                String attemptedFilePath = folderPath + "/" + videoName + ".mp4";
                Log.e(TAG, "Video not found: " + videoName + ". Attempted path: " + attemptedFilePath);
                Toast.makeText(this, "Video not found: " + videoName, Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "Adapter is null or empty");
        }
    }



    private void continueListening() {
        if (isListening) {
            Log.i(TAG, "Resuming speech recognition.");
            startContinuousRecognition();
        }
    }

    private synchronized void handleGreetings(String speechText) {
        // Check for any of the greetings first to avoid multiple processing
        if (speechText.contains("hello") || speechText.contains("hi") || speechText.contains("how are you") ||
                speechText.contains("ciao") || speechText.contains("come stai") || speechText.contains("buongiorno") ||
                speechText.contains("bonjour") || speechText.contains("comment ça va")) {

            isProcessing = true;  // Flag to indicate processing is underway

            // Handle Italian greetings
            if (speechText.contains("ciao") || speechText.contains("come stai") || speechText.contains("buongiorno")) {
                sayText("Ciao! Come stai? Sono Buddy, il tuo amichevole compagno robot." +
                        " Spero che tu stia passando una buona giornata." +
                        " Ti auguro una giornata ancora più luminosa. Buona fortuna!", "it-IT-IsabellaMultilingualNeural");
                return;  // Return after handling to avoid further greetings
            }

            // Handle French greetings
            if (speechText.contains("bonjour") || speechText.contains("comment ça va")) {
                sayText("Bonjour! Comment ça va? Je suis Buddy, ton compagnon robot amical." +
                        " J'espère que tu passes une bonne journée." +
                        " Je te souhaite une journée encore plus lumineuse. Bonne chance!", "it-IT-IsabellaMultilingualNeural");
                return;  // Return after handling to avoid further greetings
            }

            // Handle English greetings
            if (speechText.contains("hello") || speechText.contains("hi") || speechText.contains("how are you")) {
                sayText("Hello! how are you? I'm Buddy, your friendly robot companion." +
                        " I hope you are having a good day." +
                        " I wish you a brighter day ahead. Good luck", "it-IT-IsabellaMultilingualNeural");
                return;  // Return after handling to avoid further greetings
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
                        Thread.sleep(500);
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
                isProcessing = false;
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
                Log.i(TAG, "onRequestPermissionsResult: All permissions granted. Initializing recognizer and loading videos.");
                initializeRecognizer();
                loadVideoFiles();  // Load video files once permissions are granted
            } else {
                Log.i(TAG, "onRequestPermissionsResult: Permissions not granted. Showing error message.");
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
