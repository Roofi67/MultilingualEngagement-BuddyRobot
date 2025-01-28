package com.bfr.scenario1;

import static android.os.SystemClock.sleep;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.MediaStore;
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
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class MainActivity extends BuddyActivity {
    private static final String SUBSCRIPTION_KEY = "GET CREDENTIALS";
    private static final String SERVICE_REGION = "westeurope";
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private List<String> videoNames = new ArrayList<>();
    private VideoAdapter videoAdapter;
    private RecyclerView recyclerViewFiles;

    private boolean keepMood = false;


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


    private ImageView fullScreenImage;

    private SpeechRecognizer recognizer;
    private boolean isProcessing = false; // To prevent duplicate processing of the same speech input



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerViewFiles = findViewById(R.id.recyclerView_files);
        recyclerViewFiles.setLayoutManager(new GridLayoutManager(this, 3));
        videoAdapter = new VideoAdapter(this, videoNames, this::playVideo);  // Initialize adapter
        recyclerViewFiles.setAdapter(videoAdapter);  // Set adapter to RecyclerView

        initViews();
        //configureListeners();
        checkPermissions();
    }

    private void initViews() {
        mainButtonsContainer = findViewById(R.id.mainButtonsContainer);
        fullScreenImage = findViewById(R.id.welcomeImage);
        Button buttonListen = findViewById(R.id.button_listen);
        recognizedText = findViewById(R.id.recognizedText);
        imageView = findViewById(R.id.imageView);
        buttonBack = findViewById(R.id.button_back);
        sttState = findViewById(R.id.sttState);

        buttonListen.setOnClickListener(v -> {
            mainButtonsContainer.setVisibility(View.GONE);
            fullScreenImage.setVisibility(View.GONE);
            startContinuousRecognition();
        });

        buttonBack.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            }
            showMainButtons();
            fullScreenImage.setVisibility(View.VISIBLE);
            buttonBack.setVisibility(View.GONE);
        });
    }

//    private void configureListeners() {
//        listViewFiles.setOnItemClickListener(this::onVideoSelected);
//    }

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
        File directory = new File(folderPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles(file -> file.getName().endsWith(".mp4"));
            if (files != null && files.length > 0) {
                videoNames.clear();  // Clear the previous list of videos
                for (File file : files) {
                    videoNames.add(file.getAbsolutePath());  // Add absolute paths of video files
                }
                videoAdapter.notifyDataSetChanged();  // Notify adapter to refresh RecyclerView
                recyclerViewFiles.setVisibility(View.VISIBLE);  // Make sure RecyclerView is visible
            } else {
                Toast.makeText(this, "No videos found.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Directory not found.", Toast.LENGTH_LONG).show();
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
            if (!videoFile.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri videoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", videoFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
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


        if (speechText.contains("show")) {
            Log.i(TAG, "Command to show videos recognized.");
            runOnUiThread(this::loadVideoFiles);
        } else if (speechText.contains("remove")) {
            Log.i(TAG, "Command to remove videos recognized.");
            runOnUiThread(() -> {
                recyclerViewFiles.setVisibility(View.GONE);  // Hide the video list
                //showMainButtons();  // Show the main buttons again
                recognizedText.setText("Videos list  removed.");
                recognizedText.setVisibility(View.VISIBLE);
            });
        } else if (speechText.contains("move")) {
            Log.i(TAG, "Command to move forward recognized.");
            runOnUiThread(() -> {
                moveBuddy(0.3F, 0.2F, () -> {
                    recognizedText.setText("Move successful");
                    showBackButton();
                    continueListening();
                });
            });
        } else if (speechText.contains("left")) {
            Log.i(TAG, "Command to rotate left recognized.");
            runOnUiThread(() -> {
                rotateBuddy(90, -110, () -> {
                    recognizedText.setText("Rotate successful");
                    showBackButton();
                    continueListening();
                });
            });
        } else if (speechText.contains("right")) {
            Log.i(TAG, "Command to rotate right recognized.");
            runOnUiThread(() -> {
                rotateBuddy(90, 110, () -> {
                    recognizedText.setText("Rotate successful");
                    showBackButton();
                    continueListening();
                });
            });
        } else {
            handleGreetings(speechText);
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
                keepMood = true;
                setMoodTemporarily(FacialExpression.HAPPY);
                return;  // Return after handling to avoid further greetings
            }

            // Handle French greetings
            if (speechText.contains("bonjour") || speechText.contains("comment ça va")) {
                sayText("Bonjour! Comment ça va? Je suis Buddy, ton compagnon robot amical." +
                        " J'espère que tu passes une bonne journée." +
                        " Je te souhaite une journée encore plus lumineuse. Bonne chance!", "it-IT-IsabellaMultilingualNeural");
                setMoodTemporarily(FacialExpression.HAPPY);
                return;  // Return after handling to avoid further greetings
            }

            // Handle English greetings
            if (speechText.contains("hello") || speechText.contains("hi") || speechText.contains("how are you")) {
                sayText("Hello! how are you? I'm Buddy, your friendly robot companion." +
                        " I hope you are having a good day." +
                        " I wish you a brighter day ahead. Good luck", "it-IT-IsabellaMultilingualNeural");
                setMoodTemporarily(FacialExpression.HAPPY);
                return;  // Return after handling to avoid further greetings
            }
        }

//        if (keepMood) {
//            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                BuddySDK.UI.setMood(FacialExpression.NEUTRAL); // Set to neutral after 3 seconds
//                keepMood = false; // Reset the flag
//            }, 3000); // 3000 milliseconds for 3 seconds
//        }

        isProcessing = false;
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
                        //BuddySDK.UI.setMood(FacialExpression.HAPPY);
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
    private void setMoodTemporarily(FacialExpression expression) {
        BuddySDK.UI.setMood(expression);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            BuddySDK.UI.setMood(FacialExpression.NEUTRAL); // Change NEUTRAL to your default mood if different
        }, 3000);
    }



    public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
        private Context context;
        private List<String> videoPaths;
        private LayoutInflater inflater;
        private OnVideoSelectedListener listener;

        // Constructor with listener
        public VideoAdapter(Context context, List<String> videoPaths, OnVideoSelectedListener listener) {
            this.context = context;
            this.videoPaths = videoPaths;
            this.inflater = LayoutInflater.from(context);
            this.listener = listener;
        }

        // Constructor without listener
        public VideoAdapter(Context context, List<String> videoPaths) {
            this(context, videoPaths, null); // Call the main constructor, but pass null for listener
        }

        @Override
        public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.video_item, parent, false);
            return new VideoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(VideoViewHolder holder, int position) {
            String videoPath = videoPaths.get(position);

            // Create a video thumbnail
            Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Images.Thumbnails.MINI_KIND);
            holder.thumbnail.setImageBitmap(thumbnail);

            // Set up click listener for video selection
            if (listener != null) {
                holder.itemView.setOnClickListener(v -> listener.onVideoSelected(videoPath));
            }

            // Load the video thumbnail using Glide or another method
            Uri uri = Uri.fromFile(new File(videoPath));
            Glide.with(context)
                    .load(uri)
                    .centerCrop()  // Center-crop the thumbnail
                    .into(holder.thumbnail);
        }

        @Override
        public int getItemCount() {
            return videoPaths.size();
        }

        public class VideoViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnail;

            public VideoViewHolder(View itemView) {
                super(itemView);
                thumbnail = itemView.findViewById(R.id.video_thumbnail);
            }
        }
    }

    public interface OnVideoSelectedListener {
        void onVideoSelected(String videoPath);
    }


    private void showMainButtons() {
        mainButtonsContainer.setVisibility(View.VISIBLE);
        //listViewFiles.setVisibility(View.GONE);
        recognizedText.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        //buttonBack.setVisibility(View.GONE);
        sttState.setVisibility(View.GONE);
    }

    private void showBackButton() {
        buttonBack.setVisibility(View.VISIBLE);
    }
}
