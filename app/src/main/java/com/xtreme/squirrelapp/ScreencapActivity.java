package com.xtreme.squirrelapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreencapActivity extends AppCompatActivity {

    private MediaRecorder recorder;
    private Context context;
    private MediaProjectionManager projectionManager;
    private VirtualDisplay display;
    private MediaProjection projection;
    Button recordButton;
    private boolean isRecording = false;
    private File outputRoot;
    private String outputFile;

    private final DateFormat fileFormat =
            new SimpleDateFormat("'Screencap_'yyyy-MM-dd-HH-mm-ss'.mp4'", Locale.US);
    String outputName = fileFormat.format(new Date());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screencap);
        context = getApplicationContext();
        recordButton = (Button) findViewById(R.id.button_record);
        projectionManager = (MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE);
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        outputRoot = new File(picturesDir, "screencap");

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    startRecording();
                } else {
                    isRecording = false;
                    recordButton.setText(R.string.recording_start);
                }
            }
        };
        recordButton.setOnClickListener(clickListener);
    }

    private void stopRecording() {
        projection.stop();
        recorder.reset();
        recorder.release();
        display.release();
        recorder.stop();
    }

    private void startRecording() {
        MediaProjectionManager manager =
                (MediaProjectionManager) this.getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent = manager.createScreenCaptureIntent();
        this.startActivityForResult(intent, 4242);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }
        new ScreenCapTask().execute(data);
        recordButton.setText(R.string.recording_stop);

        super.onActivityResult(requestCode, resultCode, data);
    }


    private class ScreenCapTask extends AsyncTask<Intent, Void, Void> {

        @Override
        protected Void doInBackground(Intent... params) {
            recorder = new MediaRecorder();
            RecordingInfo recordingInfo = getRecordingInfo();

            outputFile = new File(outputRoot, outputName).getAbsolutePath();

            outputRoot.mkdir();

            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoFrameRate(recordingInfo.frameRate);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(recordingInfo.width, recordingInfo.height);
            recorder.setVideoEncodingBitRate(8 * 1000 * 1000);
            recorder.setOutputFile(outputFile);
            try {
                recorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            projection = projectionManager.getMediaProjection(Activity.RESULT_OK, params[0]);
            Surface surface = recorder.getSurface();
            display =
                    projection.createVirtualDisplay("display_name", recordingInfo.width, recordingInfo.height,
                            recordingInfo.density, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, surface, null, null);

            isRecording = true;

            recorder.start();
            SystemClock.sleep(5000);
            stopRecording();
            return null;
        }
    }

    private RecordingInfo getRecordingInfo() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(displayMetrics);
        int displayWidth = displayMetrics.widthPixels;
        int displayHeight = displayMetrics.heightPixels;
        int displayDensity = displayMetrics.densityDpi;
        // Get the best camera profile available. We assume MediaRecorder supports the highest.
        CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        int cameraWidth = camcorderProfile != null ? camcorderProfile.videoFrameWidth : -1;
        int cameraHeight = camcorderProfile != null ? camcorderProfile.videoFrameHeight : -1;
        int cameraFrameRate = camcorderProfile != null ? camcorderProfile.videoFrameRate : 30;


        return calculateRecordingInfo(displayWidth, displayHeight, displayDensity, false,
                cameraWidth, cameraHeight, cameraFrameRate, 100);
    }


    static RecordingInfo calculateRecordingInfo(int displayWidth, int displayHeight,
                                                int displayDensity, boolean isLandscapeDevice, int cameraWidth, int cameraHeight,
                                                int cameraFrameRate, int sizePercentage) {
        // Scale the display size before any maximum size calculations.
        displayWidth = displayWidth * sizePercentage / 100;
        displayHeight = displayHeight * sizePercentage / 100;

        if (cameraWidth == -1 && cameraHeight == -1) {
            // No cameras. Fall back to the display size.
            return new RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity);
        }

        int frameWidth = isLandscapeDevice ? cameraWidth : cameraHeight;
        int frameHeight = isLandscapeDevice ? cameraHeight : cameraWidth;
        if (frameWidth >= displayWidth && frameHeight >= displayHeight) {
            // Frame can hold the entire display. Use exact values.
            return new RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity);
        }

        // Calculate new width or height to preserve aspect ratio.
        if (isLandscapeDevice) {
            frameWidth = displayWidth * frameHeight / displayHeight;
        } else {
            frameHeight = displayHeight * frameWidth / displayWidth;
        }
        return new RecordingInfo(frameWidth, frameHeight, cameraFrameRate, displayDensity);
    }

    static final class RecordingInfo {
        final int width;
        final int height;
        final int frameRate;
        final int density;

        RecordingInfo(int width, int height, int frameRate, int density) {
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.density = density;
        }
    }
}
