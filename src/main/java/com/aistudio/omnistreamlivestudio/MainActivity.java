package com.aistudio.omnistreamlivestudio;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1003;
    private static final String PREFS_NAME = "omnistream-live-studio_prefs";

    // Header & Theming
    private Button btnThemeToggle;

    // Platform Presets
    private Button btnPlatformYoutube;
    private Button btnPlatformFacebook;
    private Button btnPlatformTwitch;
    private Button btnPlatformTiktok;
    private Button btnPlatformKick;
    private Button btnPlatformCustom;

    // Configuration Inputs
    private EditText etServerUrl;
    private EditText etStreamKey;
    private Button btnPasteKey;

    // Video Resolution Chips
    private Button btnRes1080p;
    private Button btnRes720p;
    private Button btnRes480p;

    // Bitrate Slider
    private SeekBar seekBitrate;
    private TextView tvBitrateValue;

    // Audio & Facecam Controls
    private Switch switchMicAudio;
    private Switch switchFacecam;
    private Button btnFlipCamera;

    // Telemetry Display
    private TextView tvLiveStatus;
    private TextView tvUptime;
    private TextView tvLiveBitrate;
    private TextView tvLiveFps;
    private TextView tvDroppedFrames;

    // Action Controls
    private Button btnStartStopStream;

    // Internal State
    private SharedPreferences prefs;
    private int selectedWidth = 1280;
    private int selectedHeight = 720;
    private int targetBitrateKbps = 2500;
    private int selectedPlatformIndex = 0; // 0: YT, 1: FB, 2: Twitch, 3: TikTok, 4: Kick, 5: Custom

    private static final String[] PRESET_URLS = {
            "rtmp://a.rtmp.youtube.com/live2",
            "rtmps://live-api-s.facebook.com:443/rtmp/",
            "rtmp://live.twitch.tv/app/",
            "rtmp://live-push.tiktok.com/live/",
            "rtmps://fa723794b6f7.global-contribute.live-video.net:443/app/",
            ""
    };

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable telemetryRunnable = new Runnable() {
        @Override
        public void run() {
            updateTelemetryDisplay();
            uiHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int mode = sp.getInt("pref_theme_mode", 0);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 0) {
            int sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (sysNight == 2) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            } else {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            }
        } else if (mode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        initViews();
        setupTheming();
        loadSavedPreferences();
        setupListeners();
        requestNecessaryPermissions();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnPlatformYoutube = findViewById(R.id.btnPlatformYoutube);
        btnPlatformFacebook = findViewById(R.id.btnPlatformFacebook);
        btnPlatformTwitch = findViewById(R.id.btnPlatformTwitch);
        btnPlatformTiktok = findViewById(R.id.btnPlatformTiktok);
        btnPlatformKick = findViewById(R.id.btnPlatformKick);
        btnPlatformCustom = findViewById(R.id.btnPlatformCustom);

        etServerUrl = findViewById(R.id.etServerUrl);
        etStreamKey = findViewById(R.id.etStreamKey);
        btnPasteKey = findViewById(R.id.btnPasteKey);

        btnRes1080p = findViewById(R.id.btnRes1080p);
        btnRes720p = findViewById(R.id.btnRes720p);
        btnRes480p = findViewById(R.id.btnRes480p);

        seekBitrate = findViewById(R.id.seekBitrate);
        tvBitrateValue = findViewById(R.id.tvBitrateValue);

        switchMicAudio = findViewById(R.id.switchMicAudio);
        switchFacecam = findViewById(R.id.switchFacecam);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);

        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        tvUptime = findViewById(R.id.tvUptime);
        tvLiveBitrate = findViewById(R.id.tvLiveBitrate);
        tvLiveFps = findViewById(R.id.tvLiveFps);
        tvDroppedFrames = findViewById(R.id.tvDroppedFrames);

        btnStartStopStream = findViewById(R.id.btnStartStopStream);
    }

    private void setupTheming() {
        int themeMode = prefs.getInt("pref_theme_mode", 0);
        if (themeMode == 0) {
            btnThemeToggle.setText("Auto");
        } else if (themeMode == 1) {
            btnThemeToggle.setText("Light");
        } else {
            btnThemeToggle.setText("Dark");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                if (!isNight) {
                    controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                } else {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    private void cycleThemeMode() {
        int current = prefs.getInt("pref_theme_mode", 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt("pref_theme_mode", next).apply();
        recreate();
    }

    private void loadSavedPreferences() {
        selectedPlatformIndex = prefs.getInt("pref_platform_index", 0);
        String savedKey = prefs.getString("pref_stream_key", "");
        String customUrl = prefs.getString("pref_custom_url", "");
        selectedWidth = prefs.getInt("pref_width", 1280);
        selectedHeight = prefs.getInt("pref_height", 720);
        targetBitrateKbps = prefs.getInt("pref_bitrate_kbps", 2500);
        boolean micEnabled = prefs.getBoolean("pref_mic_enabled", true);
        boolean facecamEnabled = prefs.getBoolean("pref_facecam_enabled", false);

        selectPlatform(selectedPlatformIndex);
        if (selectedPlatformIndex == 5 && !customUrl.isEmpty()) {
            etServerUrl.setText(customUrl);
        }
        etStreamKey.setText(savedKey);

        seekBitrate.setProgress(targetBitrateKbps);
        tvBitrateValue.setText(String.format(Locale.US, "%d kbps", targetBitrateKbps));

        updateResolutionButtons();
        switchMicAudio.setChecked(micEnabled);
        switchFacecam.setChecked(facecamEnabled);
    }

    private void saveCurrentPreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("pref_platform_index", selectedPlatformIndex);
        editor.putString("pref_stream_key", etStreamKey.getText().toString().trim());
        if (selectedPlatformIndex == 5) {
            editor.putString("pref_custom_url", etServerUrl.getText().toString().trim());
        }
        editor.putInt("pref_width", selectedWidth);
        editor.putInt("pref_height", selectedHeight);
        editor.putInt("pref_bitrate_kbps", targetBitrateKbps);
        editor.putBoolean("pref_mic_enabled", switchMicAudio.isChecked());
        editor.putBoolean("pref_facecam_enabled", switchFacecam.isChecked());
        editor.apply();
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnPlatformYoutube.setOnClickListener(v -> selectPlatform(0));
        btnPlatformFacebook.setOnClickListener(v -> selectPlatform(1));
        btnPlatformTwitch.setOnClickListener(v -> selectPlatform(2));
        btnPlatformTiktok.setOnClickListener(v -> selectPlatform(3));
        btnPlatformKick.setOnClickListener(v -> selectPlatform(4));
        btnPlatformCustom.setOnClickListener(v -> selectPlatform(5));

        btnPasteKey.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    etStreamKey.setText(text.toString().trim());
                    saveCurrentPreferences();
                }
            }
        });

        btnRes1080p.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedWidth = 1920;
            selectedHeight = 1080;
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        btnRes720p.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedWidth = 1280;
            selectedHeight = 720;
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        btnRes480p.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedWidth = 854;
            selectedHeight = 480;
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        seekBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 500) progress = 500;
                targetBitrateKbps = progress;
                tvBitrateValue.setText(String.format(Locale.US, "%d kbps", targetBitrateKbps));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveCurrentPreferences();
            }
        });

        switchMicAudio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveCurrentPreferences();
        });

        switchFacecam.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (isChecked) {
                if (checkOverlayPermission()) {
                    StreamService.toggleFloatingFacecam(MainActivity.this, true);
                } else {
                    switchFacecam.setChecked(false);
                    requestOverlayPermission();
                }
            } else {
                StreamService.toggleFloatingFacecam(MainActivity.this, false);
            }
            saveCurrentPreferences();
        });

        btnFlipCamera.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            StreamService.flipFacecamCamera(this);
        });

        btnStartStopStream.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleStreaming();
        });
    }

    private void selectPlatform(int index) {
        selectedPlatformIndex = index;
        Button[] platformButtons = {
                btnPlatformYoutube, btnPlatformFacebook, btnPlatformTwitch,
                btnPlatformTiktok, btnPlatformKick, btnPlatformCustom
        };

        for (int i = 0; i < platformButtons.length; i++) {
            if (i == index) {
                platformButtons[i].setBackgroundResource(R.drawable.chip_active);
            } else {
                platformButtons[i].setBackgroundResource(R.drawable.chip_inactive);
            }
        }

        if (index < 5) {
            etServerUrl.setText(PRESET_URLS[index]);
            etServerUrl.setEnabled(false);
        } else {
            etServerUrl.setEnabled(true);
        }
        saveCurrentPreferences();
    }

    private void updateResolutionButtons() {
        btnRes1080p.setBackgroundResource(selectedHeight == 1080 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnRes720p.setBackgroundResource(selectedHeight == 720 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnRes480p.setBackgroundResource(selectedHeight == 480 ? R.drawable.chip_active : R.drawable.chip_inactive);
    }

    private void requestNecessaryPermissions() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            }, REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean checkOverlayPermission() {
        return Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
    }

    private void toggleStreaming() {
        if (StreamService.isStreamingActive()) {
            Intent stopIntent = new Intent(this, StreamService.class);
            stopIntent.setAction(StreamService.ACTION_STOP);
            startService(stopIntent);
            updateUiStreamState(false);
        } else {
            String serverUrl = etServerUrl.getText().toString().trim();
            String streamKey = etStreamKey.getText().toString().trim();

            if (serverUrl.isEmpty()) {
                Toast.makeText(this, "Enter RTMP Ingest URL", Toast.LENGTH_SHORT).show();
                return;
            }
            if (streamKey.isEmpty()) {
                Toast.makeText(this, "Enter Stream Key", Toast.LENGTH_SHORT).show();
                return;
            }

            saveCurrentPreferences();

            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm != null) {
                startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_MEDIA_PROJECTION);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, StreamService.class);
                serviceIntent.setAction(StreamService.ACTION_START);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("resultData", data);
                serviceIntent.putExtra("serverUrl", etServerUrl.getText().toString().trim());
                serviceIntent.putExtra("streamKey", etStreamKey.getText().toString().trim());
                serviceIntent.putExtra("videoWidth", selectedWidth);
                serviceIntent.putExtra("videoHeight", selectedHeight);
                serviceIntent.putExtra("bitrateKbps", targetBitrateKbps);
                serviceIntent.putExtra("enableMic", switchMicAudio.isChecked());
                serviceIntent.putExtra("enableFacecam", switchFacecam.isChecked());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                updateUiStreamState(true);
            } else {
                Toast.makeText(this, "Screen recording permission was denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateUiStreamState(boolean isStreaming) {
        if (isStreaming) {
            btnStartStopStream.setText("STOP BROADCAST");
            btnStartStopStream.setBackgroundResource(R.drawable.btn_m3_outlined);
            tvLiveStatus.setText("LIVE BROADCASTING");
            tvLiveStatus.setTextColor(getColor(R.color.m3_primary));
        } else {
            btnStartStopStream.setText("GO LIVE NOW");
            btnStartStopStream.setBackgroundResource(R.drawable.btn_m3_primary);
            tvLiveStatus.setText("STUDIO READY (OFFLINE)");
            tvLiveStatus.setTextColor(getColor(R.color.m3_on_surface_variant));
        }
    }

    private void updateTelemetryDisplay() {
        boolean active = StreamService.isStreamingActive();
        updateUiStreamState(active);
        if (active) {
            long uptimeMs = StreamService.getStreamUptimeMs();
            long seconds = (uptimeMs / 1000) % 60;
            long minutes = (uptimeMs / (1000 * 60)) % 60;
            long hours = uptimeMs / (1000 * 60 * 60);
            tvUptime.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));

            tvLiveBitrate.setText(String.format(Locale.US, "%d kbps", StreamService.getCurrentBitrateKbps()));
            tvLiveFps.setText(String.format(Locale.US, "%.1f fps", StreamService.getCurrentFps()));
            tvDroppedFrames.setText(String.valueOf(StreamService.getDroppedFramesCount()));
        } else {
            tvUptime.setText("00:00:00");
            tvLiveBitrate.setText("0 kbps");
            tvLiveFps.setText("0.0 fps");
            tvDroppedFrames.setText("0");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                btnStartStopStream.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                if (getCurrentFocus() == null || !(getCurrentFocus() instanceof EditText)) {
                    switchMicAudio.toggle();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                seekBitrate.setProgress(Math.min(seekBitrate.getMax(), seekBitrate.getProgress() + 250));
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                seekBitrate.setProgress(Math.max(500, seekBitrate.getProgress() - 250));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserves layout without activity rebuild on desktop window resizing
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.post(telemetryRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(telemetryRunnable);
    }

    // =========================================================================
    // Embedded Pure-Java Foreground Streaming Service
    // =========================================================================
    public static class StreamService extends Service {

        public static final String ACTION_START = "com.aistudio.omnistreamlivestudio.START";
        public static final String ACTION_STOP = "com.aistudio.omnistreamlivestudio.STOP";
        private static final String CHANNEL_ID = "omnistream_channel_live";

        private static volatile boolean isStreaming = false;
        private static volatile long streamStartTime = 0;
        private static volatile int currentBitrateKbps = 0;
        private static volatile float currentFps = 0.0f;
        private static volatile long droppedFrames = 0;

        private MediaProjection mediaProjection;
        private VideoEncoder videoEncoder;
        private AudioEncoder audioEncoder;
        private RtmpMuxerClient rtmpClient;

        // Floating Facecam Window State
        private static StreamService serviceInstance;
        private WindowManager windowManager;
        private View floatingCamView;
        private CameraDevice cameraDevice;
        private CameraCaptureSession captureSession;
        private boolean isFrontCamera = true;

        public static boolean isStreamingActive() {
            return isStreaming;
        }

        public static long getStreamUptimeMs() {
            return isStreaming ? (SystemClock.elapsedRealtime() - streamStartTime) : 0;
        }

        public static int getCurrentBitrateKbps() {
            return currentBitrateKbps;
        }

        public static float getCurrentFps() {
            return currentFps;
        }

        public static long getDroppedFramesCount() {
            return droppedFrames;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            serviceInstance = this;
            createNotificationChannel();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent == null) return START_NOT_STICKY;
            String action = intent.getAction();

            if (ACTION_START.equals(action)) {
                startForeground(101, buildNotification("OmniStream Broadcasting Live..."));
                startStreamingPipeline(intent);
            } else if (ACTION_STOP.equals(action)) {
                stopStreamingPipeline();
                stopForeground(true);
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "OmniStream Studio Broadcast",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Shows active live streaming status & telemetry");
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
        }

        private Notification buildNotification(String contentText) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
            );

            Intent stopIntent = new Intent(this, StreamService.class);
            stopIntent.setAction(ACTION_STOP);
            PendingIntent stopPendingIntent = PendingIntent.getService(
                    this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
            );

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            return builder.setContentTitle("OmniStream Live Studio")
                    .setContentText(contentText)
                    .setSmallIcon(android.R.drawable.presence_video_online)
                    .setContentIntent(pendingIntent)
                    .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
                    .setOngoing(true)
                    .build();
        }

        private void startStreamingPipeline(Intent intent) {
            if (isStreaming) return;

            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent resultData = intent.getParcelableExtra("resultData");
            String serverUrl = intent.getStringExtra("serverUrl");
            String streamKey = intent.getStringExtra("streamKey");
            int width = intent.getIntExtra("videoWidth", 1280);
            int height = intent.getIntExtra("videoHeight", 720);
            int bitrate = intent.getIntExtra("bitrateKbps", 2500);
            boolean enableMic = intent.getBooleanExtra("enableMic", true);
            boolean enableFacecam = intent.getBooleanExtra("enableFacecam", false);

            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm == null || resultData == null) return;

            mediaProjection = mpm.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) return;

            isStreaming = true;
            streamStartTime = SystemClock.elapsedRealtime();
            droppedFrames = 0;
            currentBitrateKbps = bitrate;
            currentFps = 30.0f;

            rtmpClient = new RtmpMuxerClient(serverUrl, streamKey);
            rtmpClient.start();

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            videoEncoder = new VideoEncoder(mediaProjection, width, height, bitrate, metrics.densityDpi, rtmpClient);
            videoEncoder.start();

            if (enableMic) {
                audioEncoder = new AudioEncoder(rtmpClient);
                audioEncoder.start();
            }

            if (enableFacecam && Settings.canDrawOverlays(this)) {
                setupFloatingFacecam();
            }
        }

        private void stopStreamingPipeline() {
            isStreaming = false;

            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder = null;
            }
            if (audioEncoder != null) {
                audioEncoder.stop();
                audioEncoder = null;
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
            if (rtmpClient != null) {
                rtmpClient.stop();
                rtmpClient = null;
            }

            removeFloatingFacecam();
        }

        public static void toggleFloatingFacecam(Context context, boolean enable) {
            if (serviceInstance == null) return;
            if (enable) {
                serviceInstance.setupFloatingFacecam();
            } else {
                serviceInstance.removeFloatingFacecam();
            }
        }

        public static void flipFacecamCamera(Context context) {
            if (serviceInstance != null) {
                serviceInstance.isFrontCamera = !serviceInstance.isFrontCamera;
                serviceInstance.restartCameraCapture();
            }
        }

        private void setupFloatingFacecam() {
            if (floatingCamView != null || !Settings.canDrawOverlays(this)) return;

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    320, 400,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 100;
            params.y = 150;

            TextureView textureView = new TextureView(this);
            textureView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));

            floatingCamView = textureView;
            floatingCamView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(floatingCamView, params);
                            return true;
                    }
                    return false;
                }
            });

            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    openCamera(surface);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    closeCamera();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });

            windowManager.addView(floatingCamView, params);
        }

        private void openCamera(SurfaceTexture surfaceTexture) {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            try {
                String selectedId = null;
                for (String id : cm.getCameraIdList()) {
                    CameraCharacteristics characteristics = cm.getCameraCharacteristics(id);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (isFrontCamera && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        selectedId = id;
                        break;
                    } else if (!isFrontCamera && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        selectedId = id;
                        break;
                    }
                }
                if (selectedId == null && cm.getCameraIdList().length > 0) {
                    selectedId = cm.getCameraIdList()[0];
                }
                if (selectedId == null) return;

                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                cm.openCamera(selectedId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        cameraDevice = camera;
                        Surface surface = new Surface(surfaceTexture);
                        try {
                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.addTarget(surface);
                            camera.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession session) {
                                    captureSession = session;
                                    try {
                                        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                        session.setRepeatingRequest(builder.build(), null, null);
                                    } catch (CameraAccessException ignored) {}
                                }

                                @Override
                                public void onConfigureFailed(CameraCaptureSession session) {}
                            }, null);
                        } catch (CameraAccessException ignored) {}
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        camera.close();
                        cameraDevice = null;
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        camera.close();
                        cameraDevice = null;
                    }
                }, null);

            } catch (Exception ignored) {}
        }

        private void closeCamera() {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }

        private void restartCameraCapture() {
            closeCamera();
            if (floatingCamView instanceof TextureView) {
                TextureView tv = (TextureView) floatingCamView;
                if (tv.isAvailable()) {
                    openCamera(tv.getSurfaceTexture());
                }
            }
        }

        private void removeFloatingFacecam() {
            closeCamera();
            if (floatingCamView != null && windowManager != null) {
                try {
                    windowManager.removeView(floatingCamView);
                } catch (Exception ignored) {}
                floatingCamView = null;
            }
        }

        @Override
        public void onDestroy() {
            stopStreamingPipeline();
            serviceInstance = null;
            super.onDestroy();
        }
    }

    // =========================================================================
    // Video Encoder (MediaProjection + MediaCodec H.264 Baseline)
    // =========================================================================
    public static class VideoEncoder implements Runnable {
        private final MediaProjection projection;
        private final int width;
        private final int height;
        private final int bitrate;
        private final int dpi;
        private final RtmpMuxerClient rtmp;
        private final AtomicBoolean isRunning = new AtomicBoolean(false);

        private MediaCodec mediaCodec;
        private Surface inputSurface;
        private android.hardware.display.VirtualDisplay virtualDisplay;
        private Thread thread;

        public VideoEncoder(MediaProjection projection, int width, int height, int bitrateKbps, int dpi, RtmpMuxerClient rtmp) {
            this.projection = projection;
            this.width = width;
            this.height = height;
            this.bitrate = bitrateKbps * 1000;
            this.dpi = dpi;
            this.rtmp = rtmp;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "OmniStream-VideoEncoder");
            thread.start();
        }

        @Override
        public void run() {
            try {
                MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                inputSurface = mediaCodec.createInputSurface();
                mediaCodec.start();

                virtualDisplay = projection.createVirtualDisplay(
                        "OmniStreamScreenCapture",
                        width, height, dpi,
                        DisplayMetrics.DENSITY_DEFAULT,
                        inputSurface, null, null
                );

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                byte[] sps = null;
                byte[] pps = null;

                while (isRunning.get()) {
                    int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputIndex >= 0) {
                        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            byte[] chunk = new byte[bufferInfo.size];
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            outputBuffer.get(chunk);

                            boolean isConfig = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                            boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;

                            if (isConfig) {
                                // Extract SPS & PPS for AVCDecoderConfigurationRecord
                                int ppsIndex = -1;
                                for (int i = 0; i < chunk.length - 4; i++) {
                                    if (chunk[i] == 0 && chunk[i + 1] == 0 && chunk[i + 2] == 0 && chunk[i + 3] == 1) {
                                        int naluType = chunk[i + 4] & 0x1F;
                                        if (naluType == 8) {
                                            ppsIndex = i;
                                            break;
                                        }
                                    }
                                }
                                if (ppsIndex > 0) {
                                    sps = new byte[ppsIndex - 4];
                                    System.arraycopy(chunk, 4, sps, 0, sps.length);
                                    pps = new byte[chunk.length - ppsIndex - 4];
                                    System.arraycopy(chunk, ppsIndex + 4, pps, 0, pps.length);
                                    rtmp.sendAvcSequenceHeader(sps, pps);
                                }
                            } else {
                                rtmp.sendVideoFrame(chunk, isKeyFrame, bufferInfo.presentationTimeUs / 1000);
                            }
                        }
                        mediaCodec.releaseOutputBuffer(outputIndex, false);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                release();
            }
        }

        public void stop() {
            isRunning.set(false);
            if (thread != null) {
                thread.interrupt();
            }
        }

        private void release() {
            try {
                if (virtualDisplay != null) {
                    virtualDisplay.release();
                    virtualDisplay = null;
                }
                if (inputSurface != null) {
                    inputSurface.release();
                    inputSurface = null;
                }
                if (mediaCodec != null) {
                    mediaCodec.stop();
                    mediaCodec.release();
                    mediaCodec = null;
                }
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // Audio Encoder (AudioRecord + MediaCodec AAC-LC)
    // =========================================================================
    public static class AudioEncoder implements Runnable {
        private static final int SAMPLE_RATE = 44100;
        private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private static final int BITRATE = 64000;

        private final RtmpMuxerClient rtmp;
        private final AtomicBoolean isRunning = new AtomicBoolean(false);
        private Thread thread;
        private AudioRecord audioRecord;
        private MediaCodec mediaCodec;

        public AudioEncoder(RtmpMuxerClient rtmp) {
            this.rtmp = rtmp;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "OmniStream-AudioEncoder");
            thread.start();
        }

        @Override
        public void run() {
            int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        minBufSize * 2
                );

                MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);

                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();
                audioRecord.startRecording();

                byte[] pcmBuffer = new byte[2048];
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                long pts = 0;

                while (isRunning.get()) {
                    int readBytes = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                    if (readBytes > 0) {
                        int inputIndex = mediaCodec.dequeueInputBuffer(10000);
                        if (inputIndex >= 0) {
                            ByteBuffer inBuf = mediaCodec.getInputBuffer(inputIndex);
                            if (inBuf != null) {
                                inBuf.clear();
                                inBuf.put(pcmBuffer, 0, readBytes);
                                mediaCodec.queueInputBuffer(inputIndex, 0, readBytes, pts, 0);
                                pts += (readBytes * 1000000L) / (SAMPLE_RATE * 2);
                            }
                        }
                    }

                    int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    while (outputIndex >= 0) {
                        ByteBuffer outBuf = mediaCodec.getOutputBuffer(outputIndex);
                        if (outBuf != null && bufferInfo.size > 0) {
                            byte[] chunk = new byte[bufferInfo.size];
                            outBuf.position(bufferInfo.offset);
                            outBuf.limit(bufferInfo.offset + bufferInfo.size);
                            outBuf.get(chunk);

                            boolean isConfig = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                            if (isConfig) {
                                rtmp.sendAacSequenceHeader(chunk);
                            } else {
                                rtmp.sendAudioFrame(chunk, bufferInfo.presentationTimeUs / 1000);
                            }
                        }
                        mediaCodec.releaseOutputBuffer(outputIndex, false);
                        outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                release();
            }
        }

        public void stop() {
            isRunning.set(false);
            if (thread != null) {
                thread.interrupt();
            }
        }

        private void release() {
            try {
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
                if (mediaCodec != null) {
                    mediaCodec.stop();
                    mediaCodec.release();
                    mediaCodec = null;
                }
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // Pure Java RTMP / RTMPS Streaming Protocol Engine
    // Supports SSL & standard sockets, AMF0 Handshake & Multiplexing
    // =========================================================================
    public static class RtmpMuxerClient implements Runnable {
        private final String ingestUrl;
        private final String streamKey;
        private final BlockingQueue<RtmpPacket> packetQueue = new LinkedBlockingQueue<>(100);
        private final AtomicBoolean isRunning = new AtomicBoolean(false);

        private Socket socket;
        private OutputStream out;
        private InputStream in;
        private Thread thread;

        private byte[] aacHeader;
        private byte[] avcHeader;

        public static class RtmpPacket {
            int type;
            long timestamp;
            byte[] data;

            public RtmpPacket(int type, long timestamp, byte[] data) {
                this.type = type;
                this.timestamp = timestamp;
                this.data = data;
            }
        }

        public RtmpMuxerClient(String ingestUrl, String streamKey) {
            this.ingestUrl = ingestUrl;
            this.streamKey = streamKey;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "OmniStream-RtmpWorker");
            thread.start();
        }

        public void sendAvcSequenceHeader(byte[] sps, byte[] pps) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0x17); // Keyframe, AVC
            baos.write(0x00); // AVC sequence header
            baos.write(0x00); // Composition time
            baos.write(0x00);
            baos.write(0x00);

            // AVCDecoderConfigurationRecord
            baos.write(0x01); // configurationVersion
            baos.write(sps[1]); // AVCProfileIndication
            baos.write(sps[2]); // profile_compatibility
            baos.write(sps[3]); // AVCLevelIndication
            baos.write(0xFF); // lengthSizeMinusOne: 3 (4 bytes NALU length)
            baos.write(0xE1); // numOfSequenceParameterSets: 1
            baos.write((sps.length >> 8) & 0xFF);
            baos.write(sps.length & 0xFF);
            baos.write(sps, 0, sps.length);

            baos.write(0x01); // numOfPictureParameterSets: 1
            baos.write((pps.length >> 8) & 0xFF);
            baos.write(pps.length & 0xFF);
            baos.write(pps, 0, pps.length);

            avcHeader = baos.toByteArray();
            packetQueue.offer(new RtmpPacket(0x09, 0, avcHeader));
        }

        public void sendVideoFrame(byte[] naluData, boolean isKeyFrame, long timestamp) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(isKeyFrame ? 0x17 : 0x27);
            baos.write(0x01); // AVC NALU
            baos.write(0x00); // Composition time
            baos.write(0x00);
            baos.write(0x00);

            // Format Annex-B to AVCC 4-byte length prefix
            int length = naluData.length;
            int offset = 0;
            if (length >= 4 && naluData[0] == 0 && naluData[1] == 0 && naluData[2] == 0 && naluData[3] == 1) {
                offset = 4;
                length -= 4;
            }
            baos.write((length >> 24) & 0xFF);
            baos.write((length >> 16) & 0xFF);
            baos.write((length >> 8) & 0xFF);
            baos.write(length & 0xFF);
            baos.write(naluData, offset, length);

            if (!packetQueue.offer(new RtmpPacket(0x09, timestamp, baos.toByteArray()))) {
                StreamService.droppedFrames++;
            }
        }

        public void sendAacSequenceHeader(byte[] configData) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0xAF); // 44kHz, 16-bit, stereo, AAC
            baos.write(0x00); // AAC sequence header
            baos.write(configData, 0, configData.length);

            aacHeader = baos.toByteArray();
            packetQueue.offer(new RtmpPacket(0x08, 0, aacHeader));
        }

        public void sendAudioFrame(byte[] audioData, long timestamp) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0xAF); // 44kHz, 16-bit, stereo, AAC
            baos.write(0x01); // AAC raw data
            baos.write(audioData, 0, audioData.length);

            if (!packetQueue.offer(new RtmpPacket(0x08, timestamp, baos.toByteArray()))) {
                StreamService.droppedFrames++;
            }
        }

        @Override
        public void run() {
            try {
                boolean isSsl = ingestUrl.startsWith("rtmps://");
                String urlWithoutScheme = ingestUrl.replace("rtmps://", "").replace("rtmp://", "");
                String host = urlWithoutScheme;
                int port = isSsl ? 443 : 1935;

                int colonIdx = host.indexOf(':');
                int slashIdx = host.indexOf('/');
                String app = "live";

                if (slashIdx != -1) {
                    app = host.substring(slashIdx + 1);
                    host = host.substring(0, slashIdx);
                }

                if (colonIdx != -1) {
                    port = Integer.parseInt(host.substring(colonIdx + 1));
                    host = host.substring(0, colonIdx);
                }

                if (isSsl) {
                    SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    SSLSocket sslSocket = (SSLSocket) factory.createSocket();
                    sslSocket.connect(new InetSocketAddress(host, port), 10000);
                    sslSocket.startHandshake();
                    socket = sslSocket;
                } else {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), 10000);
                }

                out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);
                in = new BufferedInputStream(socket.getInputStream(), 64 * 1024);

                performRtmpHandshake();
                sendConnectCommand(app);
                sendReleaseStream(streamKey);
                sendFCPublish(streamKey);
                sendCreateStream();
                sendPublish(streamKey, app);

                while (isRunning.get()) {
                    RtmpPacket packet = packetQueue.take();
                    writeChunk(packet.type, packet.timestamp, packet.data);
                }
            } catch (Exception ignored) {
            } finally {
                stop();
            }
        }

        private void performRtmpHandshake() throws Exception {
            byte[] c0c1 = new byte[1537];
            c0c1[0] = 0x03; // RTMP Version 3
            new SecureRandom().nextBytes(c0c1);
            c0c1[0] = 0x03;
            // Zero timestamp
            c0c1[1] = 0; c0c1[2] = 0; c0c1[3] = 0; c0c1[4] = 0;
            out.write(c0c1);
            out.flush();

            byte[] s0s1s2 = new byte[3073];
            int read = 0;
            while (read < s0s1s2.length) {
                int r = in.read(s0s1s2, read, s0s1s2.length - read);
                if (r < 0) throw new Exception("Handshake socket closed early");
                read += r;
            }

            // Write C2
            byte[] c2 = new byte[1536];
            System.arraycopy(s0s1s2, 1, c2, 0, 1536);
            out.write(c2);
            out.flush();
        }

        private void sendConnectCommand(String app) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "connect");
            writeAmfNumber(amf, 1.0); // Transaction ID

            // Command Object (AMF0 Object)
            amf.write(0x03);
            writeAmfProperty(amf, "app", app);
            writeAmfProperty(amf, "flashVer", "FMLE/3.0 (compatible; FMSc/1.0)");
            writeAmfProperty(amf, "swfUrl", "");
            writeAmfProperty(amf, "tcUrl", ingestUrl);
            writeAmfProperty(amf, "fpad", false);
            writeAmfProperty(amf, "capabilities", 15.0);
            writeAmfProperty(amf, "audioCodecs", 3191.0);
            writeAmfProperty(amf, "videoCodecs", 252.0);
            writeAmfProperty(amf, "videoFunction", 1.0);
            amf.write(0x00);
            amf.write(0x00);
            amf.write(0x09); // End Object

            writeChunk(0x14, 0, amf.toByteArray());
        }

        private void sendReleaseStream(String key) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "releaseStream");
            writeAmfNumber(amf, 2.0);
            amf.write(0x05); // NULL
            writeAmfString(amf, key);
            writeChunk(0x14, 0, amf.toByteArray());
        }

        private void sendFCPublish(String key) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "FCPublish");
            writeAmfNumber(amf, 3.0);
            amf.write(0x05); // NULL
            writeAmfString(amf, key);
            writeChunk(0x14, 0, amf.toByteArray());
        }

        private void sendCreateStream() throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "createStream");
            writeAmfNumber(amf, 4.0);
            amf.write(0x05); // NULL
            writeChunk(0x14, 0, amf.toByteArray());
        }

        private void sendPublish(String key, String app) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "publish");
            writeAmfNumber(amf, 5.0);
            amf.write(0x05); // NULL
            writeAmfString(amf, key);
            writeAmfString(amf, "live");
            writeChunk(0x14, 0, amf.toByteArray());
        }

        private void writeChunk(int messageType, long timestamp, byte[] payload) throws Exception {
            int csid = (messageType == 0x09) ? 0x06 : 0x04;
            int length = payload.length;

            // Chunk Header Type 0 (11 bytes)
            out.write((byte) (csid & 0x3F));
            out.write((byte) ((timestamp >> 16) & 0xFF));
            out.write((byte) ((timestamp >> 8) & 0xFF));
            out.write((byte) (timestamp & 0xFF));

            out.write((byte) ((length >> 16) & 0xFF));
            out.write((byte) ((length >> 8) & 0xFF));
            out.write((byte) (length & 0xFF));

            out.write((byte) (messageType & 0xFF));
            out.write(0x01); // Stream ID 1 (little-endian)
            out.write(0x00);
            out.write(0x00);
            out.write(0x00);

            // Chunk Body with Type 3 Continuation (Chunk size = 128)
            int offset = 0;
            int chunkSize = 128;
            while (offset < length) {
                int sendBytes = Math.min(chunkSize, length - offset);
                out.write(payload, offset, sendBytes);
                offset += sendBytes;
                if (offset < length) {
                    out.write((byte) (0xC0 | (csid & 0x3F))); // Header Type 3
                }
            }
            out.flush();
        }

        private void writeAmfString(ByteArrayOutputStream baos, String s) throws Exception {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            baos.write(0x02); // String type
            baos.write((bytes.length >> 8) & 0xFF);
            baos.write(bytes.length & 0xFF);
            baos.write(bytes);
        }

        private void writeAmfNumber(ByteArrayOutputStream baos, double val) throws Exception {
            baos.write(0x00); // Number type
            long bits = Double.doubleToRawLongBits(val);
            for (int i = 7; i >= 0; i--) {
                baos.write((byte) ((bits >> (i * 8)) & 0xFF));
            }
        }

        private void writeAmfProperty(ByteArrayOutputStream baos, String key, Object value) throws Exception {
            byte[] kBytes = key.getBytes(StandardCharsets.UTF_8);
            baos.write((kBytes.length >> 8) & 0xFF);
            baos.write(kBytes.length & 0xFF);
            baos.write(kBytes);

            if (value instanceof String) {
                writeAmfString(baos, (String) value);
            } else if (value instanceof Double) {
                writeAmfNumber(baos, (Double) value);
            } else if (value instanceof Boolean) {
                baos.write(0x01); // Boolean type
                baos.write(((Boolean) value) ? 0x01 : 0x00);
            }
        }

        public void stop() {
            isRunning.set(false);
            if (thread != null) {
                thread.interrupt();
            }
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
            } catch (Exception ignored) {}
        }
    }
}