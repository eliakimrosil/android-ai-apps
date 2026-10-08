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
import android.content.pm.ServiceInfo;
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
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1003;
    private static final String PREFS_NAME = "kim_live_studio_prefs";
    private static final String LEGACY_PREFS_NAME = "omnistream-live-studio_prefs";

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
    private TextView tvUrlLockBadge;
    private EditText etServerUrl;
    private EditText etStreamKey;
    private Button btnToggleKeyVisibility;
    private Button btnPasteKey;

    // Video Resolution Chips
    private Button btnRes1080p;
    private Button btnRes720p;
    private Button btnRes480p;

    // Video Frame Cadence (FPS) Chips
    private Button btnFps60;
    private Button btnFps30;
    private Button btnFps24;
    private TextView tvFpsBadge;

    // Stream Orientation Chips
    private Button btnOrientLandscape;
    private Button btnOrientPortrait;
    private Button btnOrientAuto;
    private TextView tvOrientationBadge;

    public static final int ORIENT_LANDSCAPE = 0;
    public static final int ORIENT_PORTRAIT = 1;
    public static final int ORIENT_AUTO = 2;
    private int selectedOrientation = ORIENT_LANDSCAPE;
    private int selectedResPreset = 1080;

    // Bitrate Slider & Presets
    private SeekBar seekBitrate;
    private TextView tvBitrateValue;
    private Button btnBitrate1500;
    private Button btnBitrate2500;
    private Button btnBitrate4500;
    private Button btnBitrate6000;
    private TextView tvBitrateHint;

    // Audio & Facecam Controls
    private Switch switchMicAudio;
    private TextView tvMicStatusHint;
    private Switch switchFacecam;
    private Button btnFlipCamera;
    private Button btnRotateCamera;
    private TextView tvOverlayStatus;

    // Telemetry Display & VU Meter
    private ImageView ivLiveBeacon;
    private TextView tvLiveStatus;
    private TextView tvUptime;
    private TextView tvLiveBitrate;
    private TextView tvLiveFps;
    private TextView tvDroppedFrames;
    private View[] vuSegments;
    private TextView tvVuLevel;

    // Action Controls
    private Button btnStartStopStream;

    // Internal State
    private boolean isKeyVisible = false;
    private SharedPreferences prefs;
    private int selectedWidth = 1280;
    private int selectedHeight = 720;
    private int selectedFps = 30;
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
        if (!sp.contains("pref_theme_mode")) {
            SharedPreferences legacy = newBase.getSharedPreferences(LEGACY_PREFS_NAME, MODE_PRIVATE);
            if (legacy.contains("pref_theme_mode")) {
                sp.edit().putInt("pref_theme_mode", legacy.getInt("pref_theme_mode", 0)).apply();
            }
        }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        migrateLegacyPreferencesIfNeeded();
        initViews();
        setupTheming();
        setupFacecamStateListener();
        loadSavedPreferences();
        setupListeners();
        requestNecessaryPermissions();
    }

    private void setupFacecamStateListener() {
        FloatingCamManager.getInstance(this).setStateListener(new FloatingCamManager.StateListener() {
            @Override
            public void onFacecamToggled(boolean isShowing) {
                switchFacecam.setChecked(isShowing);
                updateOverlayStatus();
                saveCurrentPreferences();
            }

            @Override
            public void onCameraFlipped(boolean isFront) {
                btnFlipCamera.setText(isFront ? "FLIP: FRONT" : "FLIP: REAR");
            }

            @Override
            public void onCameraRotated(int rotationDegrees) {
                if (btnRotateCamera != null) {
                    btnRotateCamera.setText("ROT: " + rotationDegrees + "°");
                }
            }
        });
    }

    private void startFacecamService() {
        Intent fIntent = new Intent(this, StreamService.class);
        fIntent.setAction(StreamService.ACTION_START_FACECAM);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(fIntent);
        } else {
            startService(fIntent);
        }
    }

    private void stopFacecamService() {
        Intent fIntent = new Intent(this, StreamService.class);
        fIntent.setAction(StreamService.ACTION_STOP_FACECAM);
        startService(fIntent);
    }

    private void migrateLegacyPreferencesIfNeeded() {
        try {
            SharedPreferences legacy = getSharedPreferences(LEGACY_PREFS_NAME, MODE_PRIVATE);
            if (!legacy.getAll().isEmpty() && !prefs.contains("pref_stream_key") && legacy.contains("pref_stream_key")) {
                SharedPreferences.Editor editor = prefs.edit();
                for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof String) {
                        editor.putString(entry.getKey(), (String) val);
                    } else if (val instanceof Integer) {
                        editor.putInt(entry.getKey(), (Integer) val);
                    } else if (val instanceof Boolean) {
                        editor.putBoolean(entry.getKey(), (Boolean) val);
                    } else if (val instanceof Float) {
                        editor.putFloat(entry.getKey(), (Float) val);
                    } else if (val instanceof Long) {
                        editor.putLong(entry.getKey(), (Long) val);
                    }
                }
                editor.apply();
            }
        } catch (Exception ignored) {
        }
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnPlatformYoutube = findViewById(R.id.btnPlatformYoutube);
        btnPlatformFacebook = findViewById(R.id.btnPlatformFacebook);
        btnPlatformTwitch = findViewById(R.id.btnPlatformTwitch);
        btnPlatformTiktok = findViewById(R.id.btnPlatformTiktok);
        btnPlatformKick = findViewById(R.id.btnPlatformKick);
        btnPlatformCustom = findViewById(R.id.btnPlatformCustom);

        tvUrlLockBadge = findViewById(R.id.tvUrlLockBadge);
        etServerUrl = findViewById(R.id.etServerUrl);
        etStreamKey = findViewById(R.id.etStreamKey);
        btnToggleKeyVisibility = findViewById(R.id.btnToggleKeyVisibility);
        btnPasteKey = findViewById(R.id.btnPasteKey);

        btnRes1080p = findViewById(R.id.btnRes1080p);
        btnRes720p = findViewById(R.id.btnRes720p);
        btnRes480p = findViewById(R.id.btnRes480p);

        btnFps60 = findViewById(R.id.btnFps60);
        btnFps30 = findViewById(R.id.btnFps30);
        btnFps24 = findViewById(R.id.btnFps24);
        tvFpsBadge = findViewById(R.id.tvFpsBadge);

        btnOrientLandscape = findViewById(R.id.btnOrientLandscape);
        btnOrientPortrait = findViewById(R.id.btnOrientPortrait);
        btnOrientAuto = findViewById(R.id.btnOrientAuto);
        tvOrientationBadge = findViewById(R.id.tvOrientationBadge);

        seekBitrate = findViewById(R.id.seekBitrate);
        tvBitrateValue = findViewById(R.id.tvBitrateValue);
        btnBitrate1500 = findViewById(R.id.btnBitrate1500);
        btnBitrate2500 = findViewById(R.id.btnBitrate2500);
        btnBitrate4500 = findViewById(R.id.btnBitrate4500);
        btnBitrate6000 = findViewById(R.id.btnBitrate6000);
        tvBitrateHint = findViewById(R.id.tvBitrateHint);

        switchMicAudio = findViewById(R.id.switchMicAudio);
        tvMicStatusHint = findViewById(R.id.tvMicStatusHint);
        switchFacecam = findViewById(R.id.switchFacecam);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        btnRotateCamera = findViewById(R.id.btnRotateCamera);
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus);

        ivLiveBeacon = findViewById(R.id.ivLiveBeacon);
        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        tvUptime = findViewById(R.id.tvUptime);
        tvLiveBitrate = findViewById(R.id.tvLiveBitrate);
        tvLiveFps = findViewById(R.id.tvLiveFps);
        tvDroppedFrames = findViewById(R.id.tvDroppedFrames);

        vuSegments = new View[] {
                findViewById(R.id.vuSeg1),
                findViewById(R.id.vuSeg2),
                findViewById(R.id.vuSeg3),
                findViewById(R.id.vuSeg4),
                findViewById(R.id.vuSeg5),
                findViewById(R.id.vuSeg6),
                findViewById(R.id.vuSeg7),
                findViewById(R.id.vuSeg8),
                findViewById(R.id.vuSeg9),
                findViewById(R.id.vuSeg10)
        };
        tvVuLevel = findViewById(R.id.tvVuLevel);

        btnStartStopStream = findViewById(R.id.btnStartStopStream);
    }

    private void setupTheming() {
        int themeMode = prefs.getInt("pref_theme_mode", 0);
        if (themeMode == 0) {
            btnThemeToggle.setText("MODE: AUTO");
        } else if (themeMode == 1) {
            btnThemeToggle.setText("MODE: DAY");
        } else {
            btnThemeToggle.setText("MODE: DARK");
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
        selectedOrientation = prefs.getInt("pref_orientation", ORIENT_LANDSCAPE);
        selectedResPreset = prefs.getInt("pref_res_preset", 1080);
        if (!prefs.contains("pref_res_preset")) {
            int savedHeight = prefs.getInt("pref_height", 1080);
            int savedWidth = prefs.getInt("pref_width", 1920);
            int minDim = Math.min(savedHeight, savedWidth);
            selectedResPreset = (minDim >= 1000) ? 1080 : ((minDim >= 700) ? 720 : 480);
        }
        computeResolutionDimensions();
        selectedFps = prefs.getInt("pref_fps", 30);
        targetBitrateKbps = prefs.getInt("pref_bitrate_kbps", 2500);
        boolean micEnabled = prefs.getBoolean("pref_mic_enabled", true);
        boolean facecamEnabled = prefs.getBoolean("pref_facecam_enabled", false);

        selectPlatform(selectedPlatformIndex);
        if (selectedPlatformIndex == 5 && !customUrl.isEmpty()) {
            etServerUrl.setText(customUrl);
        }
        etStreamKey.setText(savedKey);

        seekBitrate.setProgress(targetBitrateKbps);
        tvBitrateValue.setText(String.format(Locale.US, "%,d kbps", targetBitrateKbps));

        updateResolutionButtons();
        updateFpsButtons();
        updateOrientationButtons();
        updateBitrateHint();
        switchMicAudio.setChecked(micEnabled);
        switchFacecam.setChecked(facecamEnabled);
        if (facecamEnabled && checkOverlayPermission()) {
            FloatingCamManager.getInstance(this).showOverlay();
            startFacecamService();
        }
        updateOverlayStatus();
        updateMicHint(micEnabled);
    }

    private void saveCurrentPreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("pref_platform_index", selectedPlatformIndex);
        editor.putString("pref_stream_key", etStreamKey.getText().toString().trim());
        if (selectedPlatformIndex == 5) {
            editor.putString("pref_custom_url", etServerUrl.getText().toString().trim());
        }
        editor.putInt("pref_orientation", selectedOrientation);
        editor.putInt("pref_res_preset", selectedResPreset);
        editor.putInt("pref_width", selectedWidth);
        editor.putInt("pref_height", selectedHeight);
        editor.putInt("pref_fps", selectedFps);
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

        btnToggleKeyVisibility.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isKeyVisible = !isKeyVisible;
            if (isKeyVisible) {
                etStreamKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnToggleKeyVisibility.setText("HIDE");
            } else {
                etStreamKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnToggleKeyVisibility.setText("SHOW");
            }
            etStreamKey.setSelection(etStreamKey.getText().length());
        });

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
            selectedResPreset = 1080;
            computeResolutionDimensions();
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        btnRes720p.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedResPreset = 720;
            computeResolutionDimensions();
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        btnRes480p.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedResPreset = 480;
            computeResolutionDimensions();
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        btnFps60.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedFps = 60;
            updateFpsButtons();
            saveCurrentPreferences();
        });

        btnFps30.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedFps = 30;
            updateFpsButtons();
            saveCurrentPreferences();
        });

        btnFps24.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedFps = 24;
            updateFpsButtons();
            saveCurrentPreferences();
        });

        btnOrientLandscape.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedOrientation = ORIENT_LANDSCAPE;
            computeResolutionDimensions();
            updateOrientationButtons();
            updateBitrateHint();
            saveCurrentPreferences();
        });

        btnOrientPortrait.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedOrientation = ORIENT_PORTRAIT;
            computeResolutionDimensions();
            updateOrientationButtons();
            updateBitrateHint();
            saveCurrentPreferences();
        });

        btnOrientAuto.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedOrientation = ORIENT_AUTO;
            computeResolutionDimensions();
            updateOrientationButtons();
            updateBitrateHint();
            saveCurrentPreferences();
        });

        seekBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 500) progress = 500;
                targetBitrateKbps = progress;
                tvBitrateValue.setText(String.format(Locale.US, "%,d kbps", targetBitrateKbps));
                updateBitrateHint();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveCurrentPreferences();
            }
        });

        btnBitrate1500.setOnClickListener(v -> setBitrate(1500));
        btnBitrate2500.setOnClickListener(v -> setBitrate(2500));
        btnBitrate4500.setOnClickListener(v -> setBitrate(4500));
        btnBitrate6000.setOnClickListener(v -> setBitrate(6000));

        switchMicAudio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            updateMicHint(isChecked);
            saveCurrentPreferences();
        });

        switchFacecam.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (isChecked) {
                if (checkOverlayPermission()) {
                    FloatingCamManager.getInstance(MainActivity.this).showOverlay();
                    startFacecamService();
                    updateOverlayStatus();
                } else {
                    switchFacecam.setChecked(false);
                    requestOverlayPermission();
                }
            } else {
                FloatingCamManager.getInstance(MainActivity.this).hideOverlay();
                stopFacecamService();
                updateOverlayStatus();
            }
            saveCurrentPreferences();
        });

        btnFlipCamera.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FloatingCamManager.getInstance(this).flipCamera();
        });

        if (btnRotateCamera != null) {
            btnRotateCamera.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                FloatingCamManager.getInstance(this).rotateCamera();
            });
        }

        btnStartStopStream.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleStreaming();
        });
    }

    private void setBitrate(int kbps) {
        seekBitrate.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        targetBitrateKbps = kbps;
        seekBitrate.setProgress(kbps);
        tvBitrateValue.setText(String.format(Locale.US, "%,d kbps", targetBitrateKbps));
        updateBitrateHint();
        saveCurrentPreferences();
    }

    private void updateMicHint(boolean enabled) {
        if (tvMicStatusHint != null) {
            if (enabled) {
                tvMicStatusHint.setText("LIVE: AAC 128 kbps • 44.1 kHz Studio Audio");
                tvMicStatusHint.setTextColor(getColor(R.color.studio_text_secondary));
            } else {
                tvMicStatusHint.setText("MUTED: Commentary track disabled");
                tvMicStatusHint.setTextColor(getColor(R.color.studio_amber));
            }
        }
    }

    private void updateOverlayStatus() {
        if (tvOverlayStatus != null) {
            boolean hasPermission = checkOverlayPermission();
            boolean isShowing = FloatingCamManager.getInstance(this).isShowing();
            if (hasPermission) {
                if (isShowing) {
                    tvOverlayStatus.setText("Facecam: ACTIVE ON SCREEN (Tap ✕ to hide)");
                    tvOverlayStatus.setTextColor(getColor(R.color.studio_cyan));
                } else {
                    tvOverlayStatus.setText("Overlay Permission: Ready & Granted");
                    tvOverlayStatus.setTextColor(getColor(R.color.studio_green));
                }
            } else {
                tvOverlayStatus.setText("Overlay Permission: Tap Switch to Grant");
                tvOverlayStatus.setTextColor(getColor(R.color.studio_amber));
            }
        }
    }

    private void computeResolutionDimensions() {
        boolean isPortrait;
        if (selectedOrientation == ORIENT_PORTRAIT) {
            isPortrait = true;
        } else if (selectedOrientation == ORIENT_AUTO) {
            int configOrientation = getResources().getConfiguration().orientation;
            isPortrait = (configOrientation != Configuration.ORIENTATION_LANDSCAPE);
        } else {
            isPortrait = false;
        }

        if (selectedResPreset == 1080) {
            selectedWidth = isPortrait ? 1080 : 1920;
            selectedHeight = isPortrait ? 1920 : 1080;
        } else if (selectedResPreset == 720) {
            selectedWidth = isPortrait ? 720 : 1280;
            selectedHeight = isPortrait ? 1280 : 720;
        } else {
            selectedWidth = isPortrait ? 480 : 854;
            selectedHeight = isPortrait ? 854 : 480;
        }
    }

    private void updateOrientationButtons() {
        boolean isLand = (selectedOrientation == ORIENT_LANDSCAPE);
        boolean isPort = (selectedOrientation == ORIENT_PORTRAIT);
        boolean isAuto = (selectedOrientation == ORIENT_AUTO);

        btnOrientLandscape.setBackgroundResource(isLand ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnOrientLandscape.setTextColor(getColor(isLand ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnOrientPortrait.setBackgroundResource(isPort ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnOrientPortrait.setTextColor(getColor(isPort ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnOrientAuto.setBackgroundResource(isAuto ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnOrientAuto.setTextColor(getColor(isAuto ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        if (tvOrientationBadge != null) {
            if (isLand) {
                tvOrientationBadge.setText("16:9 LANDSCAPE");
                tvOrientationBadge.setTextColor(getColor(R.color.studio_cyan));
            } else if (isPort) {
                tvOrientationBadge.setText("9:16 PORTRAIT");
                tvOrientationBadge.setTextColor(getColor(R.color.studio_ruby));
            } else {
                tvOrientationBadge.setText("AUTO SENSOR");
                tvOrientationBadge.setTextColor(getColor(R.color.studio_green));
            }
        }
    }

    private void updateBitrateHint() {
        if (tvBitrateHint == null) return;
        String orientDesc;
        if (selectedOrientation == ORIENT_PORTRAIT) {
            orientDesc = "Portrait (9:16)";
        } else if (selectedOrientation == ORIENT_AUTO) {
            orientDesc = "Auto Canvas";
        } else {
            orientDesc = "Landscape (16:9)";
        }

        if (selectedResPreset >= 1080) {
            if (selectedFps == 60) {
                tvBitrateHint.setText("Pro 1080p 60fps " + orientDesc + " • Recommended: 5,000–6,000 kbps for high-motion gaming");
            } else {
                tvBitrateHint.setText("Crisp 1080p " + selectedFps + "fps " + orientDesc + " • Recommended: 4,000–4,500 kbps for studio streams");
            }
        } else if (selectedResPreset >= 720) {
            if (selectedFps == 60) {
                tvBitrateHint.setText("Smooth 720p 60fps " + orientDesc + " • Recommended: 3,000–3,500 kbps for fast gameplay");
            } else {
                tvBitrateHint.setText("Balanced 720p " + selectedFps + "fps " + orientDesc + " • Recommended: 2,000–2,500 kbps standard profile");
            }
        } else {
            tvBitrateHint.setText("Mobile 480p " + selectedFps + "fps " + orientDesc + " • Target: 1,000–1,500 kbps low-bandwidth profile");
        }
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
                platformButtons[i].setTextColor(getColor(R.color.studio_chip_active_text));
            } else {
                platformButtons[i].setBackgroundResource(R.drawable.chip_inactive);
                platformButtons[i].setTextColor(getColor(R.color.studio_chip_text));
            }
        }

        if (index == 3) {
            selectedOrientation = ORIENT_PORTRAIT;
            computeResolutionDimensions();
            updateOrientationButtons();
            updateResolutionButtons();
        }

        if (index < 5) {
            etServerUrl.setText(PRESET_URLS[index]);
            etServerUrl.setEnabled(false);
            if (tvUrlLockBadge != null) {
                tvUrlLockBadge.setText("PRESET LOCKED");
                tvUrlLockBadge.setTextColor(getColor(R.color.studio_cyan));
            }
        } else {
            etServerUrl.setEnabled(true);
            if (tvUrlLockBadge != null) {
                tvUrlLockBadge.setText("CUSTOM RTMP");
                tvUrlLockBadge.setTextColor(getColor(R.color.studio_ruby));
            }
        }
        saveCurrentPreferences();
    }

    private void updateResolutionButtons() {
        boolean is1080 = (selectedResPreset == 1080);
        boolean is720 = (selectedResPreset == 720);
        boolean is480 = (selectedResPreset == 480);

        btnRes1080p.setBackgroundResource(is1080 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnRes1080p.setTextColor(getColor(is1080 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnRes720p.setBackgroundResource(is720 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnRes720p.setTextColor(getColor(is720 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnRes480p.setBackgroundResource(is480 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnRes480p.setTextColor(getColor(is480 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        updateBitrateHint();
    }

    private void updateFpsButtons() {
        boolean is60 = (selectedFps == 60);
        boolean is30 = (selectedFps == 30);
        boolean is24 = (selectedFps == 24);

        btnFps60.setBackgroundResource(is60 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnFps60.setTextColor(getColor(is60 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnFps30.setBackgroundResource(is30 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnFps30.setTextColor(getColor(is30 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnFps24.setBackgroundResource(is24 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnFps24.setTextColor(getColor(is24 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        if (tvFpsBadge != null) {
            if (is60) {
                tvFpsBadge.setText("60 FPS SMOOTH");
                tvFpsBadge.setTextColor(getColor(R.color.studio_cyan));
            } else if (is30) {
                tvFpsBadge.setText("30 FPS STANDARD");
                tvFpsBadge.setTextColor(getColor(R.color.studio_green));
            } else {
                tvFpsBadge.setText("24 FPS CINEMA");
                tvFpsBadge.setTextColor(getColor(R.color.studio_amber));
            }
        }

        updateBitrateHint();
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
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            updateOverlayStatus();
            if (checkOverlayPermission()) {
                switchFacecam.setChecked(true);
                FloatingCamManager.getInstance(this).showOverlay();
                startFacecamService();
                Toast.makeText(this, "Facecam overlay enabled!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Overlay permission is required for floating facecam.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                computeResolutionDimensions();
                Intent serviceIntent = new Intent(this, StreamService.class);
                serviceIntent.setAction(StreamService.ACTION_START);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("resultData", data);
                serviceIntent.putExtra("serverUrl", etServerUrl.getText().toString().trim());
                serviceIntent.putExtra("streamKey", etStreamKey.getText().toString().trim());
                serviceIntent.putExtra("videoWidth", selectedWidth);
                serviceIntent.putExtra("videoHeight", selectedHeight);
                serviceIntent.putExtra("videoFps", selectedFps);
                serviceIntent.putExtra("videoOrientation", selectedOrientation);
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
        FloatingCamManager.getInstance(this).setLiveState(isStreaming);
        if (isStreaming) {
            btnStartStopStream.setText("■ STOP BROADCAST (ON AIR)");
            btnStartStopStream.setBackgroundResource(R.drawable.btn_m3_outlined);
            btnStartStopStream.setTextColor(getColor(R.color.studio_ruby));
            tvLiveStatus.setText("LIVE BROADCASTING (ON AIR)");
            tvLiveStatus.setTextColor(getColor(R.color.studio_ruby));
            if (ivLiveBeacon != null) ivLiveBeacon.setVisibility(View.VISIBLE);
        } else {
            btnStartStopStream.setText("● START BROADCAST (GO LIVE)");
            btnStartStopStream.setBackgroundResource(R.drawable.btn_m3_primary);
            btnStartStopStream.setTextColor(getColor(R.color.studio_chip_active_text));
            tvLiveStatus.setText("STUDIO READY (STANDBY)");
            tvLiveStatus.setTextColor(getColor(R.color.studio_text_secondary));
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

            tvLiveBitrate.setText(String.format(Locale.US, "%,d kbps", StreamService.getCurrentBitrateKbps()));
            tvLiveFps.setText(String.format(Locale.US, "%.1f fps", StreamService.getCurrentFps()));
            tvDroppedFrames.setText(String.valueOf(StreamService.getDroppedFramesCount()));

            int peak = StreamService.getCurrentAudioPeak();
            int level = 0;
            if (peak > 80) {
                double ratio = (double) peak / 32767.0;
                double db = 20.0 * Math.log10(ratio);
                if (db > -3.0) level = 10;
                else if (db > -6.0) level = 9;
                else if (db > -10.0) level = 8;
                else if (db > -14.0) level = 7;
                else if (db > -18.0) level = 6;
                else if (db > -24.0) level = 5;
                else if (db > -30.0) level = 4;
                else if (db > -36.0) level = 3;
                else if (db > -42.0) level = 2;
                else level = 1;
            }
            updateVuMeter(level, peak);
        } else {
            tvUptime.setText("00:00:00");
            tvLiveBitrate.setText("0 kbps");
            tvLiveFps.setText("0.0 fps");
            tvDroppedFrames.setText("0");
            updateVuMeter(0, 0);
        }
    }

    private void updateVuMeter(int level, int peak) {
        if (vuSegments == null) return;
        int onGreen = getColor(R.color.studio_vu_green);
        int onYellow = getColor(R.color.studio_vu_yellow);
        int onRed = getColor(R.color.studio_vu_red);
        int offColor = getColor(R.color.studio_vu_off);

        for (int i = 0; i < vuSegments.length; i++) {
            if (vuSegments[i] == null) continue;
            if (i < level) {
                if (i < 5) {
                    vuSegments[i].setBackgroundColor(onGreen);
                } else if (i < 8) {
                    vuSegments[i].setBackgroundColor(onYellow);
                } else {
                    vuSegments[i].setBackgroundColor(onRed);
                }
            } else {
                vuSegments[i].setBackgroundColor(offColor);
            }
        }
        if (tvVuLevel != null) {
            if (peak > 80) {
                double db = 20.0 * Math.log10((double) peak / 32767.0);
                tvVuLevel.setText(String.format(Locale.US, "%.0f dB", db));
            } else {
                tvVuLevel.setText("-inf dB");
            }
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
        if (selectedOrientation == ORIENT_AUTO) {
            computeResolutionDimensions();
            updateOrientationButtons();
            updateBitrateHint();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOverlayStatus();
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
        public static final String ACTION_START_FACECAM = "com.aistudio.omnistreamlivestudio.START_FACECAM";
        public static final String ACTION_STOP_FACECAM = "com.aistudio.omnistreamlivestudio.STOP_FACECAM";
        private static final String CHANNEL_ID = "kim_live_studio_channel_live";

        private static volatile boolean isStreaming = false;
        private static volatile long streamStartTime = 0;
        private static volatile int currentBitrateKbps = 0;
        static volatile float currentFps = 0.0f;
        private static volatile long droppedFrames = 0;
        public static volatile int currentAudioPeak = 0;

        private MediaProjection mediaProjection;
        private VideoEncoder videoEncoder;
        private AudioEncoder audioEncoder;
        private RtmpMuxerClient rtmpClient;

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

        public static int getCurrentAudioPeak() {
            return currentAudioPeak;
        }

        @Override
        public void onCreate() {
            super.onCreate();
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(101, buildNotification("KIM Live Studio is ON AIR..."),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION |
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA |
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } else {
                    startForeground(101, buildNotification("KIM Live Studio is ON AIR..."));
                }
                startStreamingPipeline(intent);
            } else if (ACTION_STOP.equals(action)) {
                stopStreamingPipeline();
                if (!FloatingCamManager.getInstance(this).isShowing()) {
                    stopForeground(true);
                    stopSelf();
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(101, buildNotification("KIM Live Studio Facecam Active"),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
                    }
                }
            } else if (ACTION_START_FACECAM.equals(action)) {
                if (!isStreaming) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(101, buildNotification("KIM Live Studio Facecam Active"),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
                    } else {
                        startForeground(101, buildNotification("KIM Live Studio Facecam Active"));
                    }
                }
                FloatingCamManager.getInstance(this).showOverlay();
            } else if (ACTION_STOP_FACECAM.equals(action)) {
                FloatingCamManager.getInstance(this).hideOverlay();
                if (!isStreaming) {
                    stopForeground(true);
                    stopSelf();
                }
            }
            return START_NOT_STICKY;
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "KIM Live Studio Broadcast",
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

            return builder.setContentTitle("KIM Live Studio")
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
            int fps = intent.getIntExtra("videoFps", 30);
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
            currentFps = (float) fps;

            FloatingCamManager.getInstance(this).setLiveState(true);
            if (enableFacecam && Settings.canDrawOverlays(this)) {
                FloatingCamManager.getInstance(this).showOverlay();
            }

            rtmpClient = new RtmpMuxerClient(serverUrl, streamKey, width, height, fps, bitrate);
            rtmpClient.start();

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            videoEncoder = new VideoEncoder(mediaProjection, width, height, fps, bitrate, metrics.densityDpi, rtmpClient);
            videoEncoder.start();

            if (enableMic) {
                audioEncoder = new AudioEncoder(rtmpClient);
                audioEncoder.start();
            }
        }

        private void stopStreamingPipeline() {
            isStreaming = false;
            currentAudioPeak = 0;
            FloatingCamManager.getInstance(this).setLiveState(false);

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
        }

        @Override
        public void onDestroy() {
            stopStreamingPipeline();
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
        private final int fps;
        private final int bitrate;
        private final int dpi;
        private final RtmpMuxerClient rtmp;
        private final AtomicBoolean isRunning = new AtomicBoolean(false);
        private long frameCount = 0;
        private long lastFpsCalcTime = 0;

        private MediaCodec mediaCodec;
        private Surface inputSurface;
        private android.hardware.display.VirtualDisplay virtualDisplay;
        private Thread thread;

        public VideoEncoder(MediaProjection projection, int width, int height, int fps, int bitrateKbps, int dpi, RtmpMuxerClient rtmp) {
            this.projection = projection;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrate = bitrateKbps * 1000;
            this.dpi = dpi;
            this.rtmp = rtmp;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "KimLive-VideoEncoder");
            thread.start();
        }

        @Override
        public void run() {
            try {
                MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                inputSurface = mediaCodec.createInputSurface();
                mediaCodec.start();

                virtualDisplay = projection.createVirtualDisplay(
                        "KimLiveScreenCapture",
                        width, height, dpi,
                        DisplayMetrics.DENSITY_DEFAULT,
                        inputSurface, null, null
                );

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                byte[] sps = null;
                byte[] pps = null;

                while (isRunning.get()) {
                    int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = mediaCodec.getOutputFormat();
                        ByteBuffer spsBuf = newFormat.getByteBuffer("csd-0");
                        ByteBuffer ppsBuf = newFormat.getByteBuffer("csd-1");
                        if (spsBuf != null && ppsBuf != null) {
                            byte[] s = new byte[spsBuf.remaining()];
                            spsBuf.get(s);
                            byte[] p = new byte[ppsBuf.remaining()];
                            ppsBuf.get(p);
                            sps = stripStartCode(s);
                            pps = stripStartCode(p);
                            rtmp.sendAvcSequenceHeader(sps, pps);
                        }
                    } else if (outputIndex >= 0) {
                        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            byte[] chunk = new byte[bufferInfo.size];
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            outputBuffer.get(chunk);

                            boolean isConfig = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                            boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;

                            if (isConfig) {
                                extractAndSendSpsPps(chunk);
                            } else {
                                rtmp.sendVideoFrame(chunk, isKeyFrame, bufferInfo.presentationTimeUs / 1000);
                                frameCount++;
                                long now = SystemClock.elapsedRealtime();
                                if (lastFpsCalcTime == 0) {
                                    lastFpsCalcTime = now;
                                } else if (now - lastFpsCalcTime >= 1000) {
                                    StreamService.currentFps = (float) (frameCount * 1000.0 / (now - lastFpsCalcTime));
                                    frameCount = 0;
                                    lastFpsCalcTime = now;
                                }
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

        private static byte[] stripStartCode(byte[] data) {
            if (data == null || data.length < 4) return data;
            if (data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 1) {
                byte[] res = new byte[data.length - 4];
                System.arraycopy(data, 4, res, 0, res.length);
                return res;
            } else if (data[0] == 0 && data[1] == 0 && data[2] == 1) {
                byte[] res = new byte[data.length - 3];
                System.arraycopy(data, 3, res, 0, res.length);
                return res;
            }
            return data;
        }

        private void extractAndSendSpsPps(byte[] data) {
            if (data == null || data.length < 4) return;
            List<Integer> starts = new ArrayList<>();
            List<Integer> lengths = new ArrayList<>();
            int i = 0;
            while (i < data.length - 2) {
                if (data[i] == 0 && data[i + 1] == 0) {
                    if (data[i + 2] == 1) {
                        starts.add(i);
                        lengths.add(3);
                        i += 3;
                        continue;
                    } else if (i < data.length - 3 && data[i + 2] == 0 && data[i + 3] == 1) {
                        starts.add(i);
                        lengths.add(4);
                        i += 4;
                        continue;
                    }
                }
                i++;
            }
            byte[] foundSps = null;
            byte[] foundPps = null;
            for (int idx = 0; idx < starts.size(); idx++) {
                int naluStart = starts.get(idx) + lengths.get(idx);
                int naluEnd = (idx + 1 < starts.size()) ? starts.get(idx + 1) : data.length;
                int len = naluEnd - naluStart;
                if (len <= 0) continue;
                int naluType = data[naluStart] & 0x1F;
                if (naluType == 7) {
                    foundSps = new byte[len];
                    System.arraycopy(data, naluStart, foundSps, 0, len);
                } else if (naluType == 8) {
                    foundPps = new byte[len];
                    System.arraycopy(data, naluStart, foundPps, 0, len);
                }
            }
            if (foundSps != null && foundPps != null) {
                rtmp.sendAvcSequenceHeader(foundSps, foundPps);
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
        private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private static final int BITRATE = 96000;

        private final RtmpMuxerClient rtmp;
        private final AtomicBoolean isRunning = new AtomicBoolean(false);
        private Thread thread;
        private AudioRecord audioRecord;
        private MediaCodec mediaCodec;
        private int channelCount = 2;
        private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;

        public AudioEncoder(RtmpMuxerClient rtmp) {
            this.rtmp = rtmp;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "KimLive-AudioEncoder");
            thread.start();
        }

        @Override
        public void run() {
            int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AUDIO_FORMAT);
            if (minBufSize <= 0) {
                channelConfig = AudioFormat.CHANNEL_IN_MONO;
                channelCount = 1;
                minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AUDIO_FORMAT);
            } else {
                channelConfig = AudioFormat.CHANNEL_IN_STEREO;
                channelCount = 2;
            }

            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        channelConfig,
                        AUDIO_FORMAT,
                        Math.max(minBufSize * 2, 4096)
                );

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    channelConfig = AudioFormat.CHANNEL_IN_MONO;
                    channelCount = 1;
                    minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AUDIO_FORMAT);
                    audioRecord = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            channelConfig,
                            AUDIO_FORMAT,
                            Math.max(minBufSize * 2, 4096)
                    );
                }

                MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, channelCount);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);

                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();
                audioRecord.startRecording();

                // Proactive sequence header for AAC-LC 44.1kHz (Stereo: 0x12, 0x10; Mono: 0x12, 0x08)
                byte[] defaultHeader = (channelCount == 2) ? new byte[]{0x12, 0x10} : new byte[]{0x12, 0x08};
                rtmp.sendAacSequenceHeader(defaultHeader);

                byte[] pcmBuffer = new byte[2048];
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                long pts = 0;
                int bytesPerFrame = channelCount * 2;

                while (isRunning.get()) {
                    int readBytes = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                    if (readBytes > 0) {
                        int maxAmp = 0;
                        for (int i = 0; i < readBytes - 1; i += 2) {
                            short sample = (short) ((pcmBuffer[i] & 0xFF) | (pcmBuffer[i + 1] << 8));
                            int abs = Math.abs((int) sample);
                            if (abs > maxAmp) maxAmp = abs;
                        }
                        StreamService.currentAudioPeak = maxAmp;

                        int inputIndex = mediaCodec.dequeueInputBuffer(10000);
                        if (inputIndex >= 0) {
                            ByteBuffer inBuf = mediaCodec.getInputBuffer(inputIndex);
                            if (inBuf != null) {
                                inBuf.clear();
                                inBuf.put(pcmBuffer, 0, readBytes);
                                mediaCodec.queueInputBuffer(inputIndex, 0, readBytes, pts, 0);
                                pts += (readBytes * 1000000L) / (SAMPLE_RATE * bytesPerFrame);
                            }
                        }
                    }

                    int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat outFmt = mediaCodec.getOutputFormat();
                        ByteBuffer csd0 = outFmt.getByteBuffer("csd-0");
                        if (csd0 != null) {
                            byte[] config = new byte[csd0.remaining()];
                            csd0.get(config);
                            rtmp.sendAacSequenceHeader(config);
                        }
                    } else {
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
            StreamService.currentAudioPeak = 0;
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
        private final int videoWidth;
        private final int videoHeight;
        private final int videoFps;
        private final int videoBitrateKbps;

        private final BlockingQueue<RtmpPacket> packetQueue = new LinkedBlockingQueue<>(200);
        private final AtomicBoolean isRunning = new AtomicBoolean(false);

        private Socket socket;
        private OutputStream out;
        private InputStream in;
        private Thread thread;
        private Thread readerThread;

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

        public RtmpMuxerClient(String ingestUrl, String streamKey, int videoWidth, int videoHeight, int videoFps, int videoBitrateKbps) {
            this.ingestUrl = ingestUrl;
            this.streamKey = streamKey;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.videoFps = videoFps;
            this.videoBitrateKbps = videoBitrateKbps;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "KimLive-RtmpWorker");
            thread.start();
        }

        public void sendAvcSequenceHeader(byte[] sps, byte[] pps) {
            if (sps == null || pps == null || sps.length < 4 || pps.length < 1) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0x17); // Keyframe, AVC
            baos.write(0x00); // AVC sequence header
            baos.write(0x00); // Composition time (3 bytes)
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

        public void sendVideoFrame(byte[] data, boolean isKeyFrame, long timestamp) {
            if (data == null || data.length < 4) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length + 64);
            baos.write(isKeyFrame ? 0x17 : 0x27);
            baos.write(0x01); // AVC NALU
            baos.write(0x00); // Composition time offset (3 bytes: 0)
            baos.write(0x00);
            baos.write(0x00);

            // Parse Annex-B start codes (3-byte: 00 00 01 or 4-byte: 00 00 00 01)
            List<Integer> startCodeIndices = new ArrayList<>();
            List<Integer> startCodeLengths = new ArrayList<>();
            int i = 0;
            while (i < data.length - 2) {
                if (data[i] == 0 && data[i + 1] == 0) {
                    if (data[i + 2] == 1) {
                        startCodeIndices.add(i);
                        startCodeLengths.add(3);
                        i += 3;
                        continue;
                    } else if (i < data.length - 3 && data[i + 2] == 0 && data[i + 3] == 1) {
                        startCodeIndices.add(i);
                        startCodeLengths.add(4);
                        i += 4;
                        continue;
                    }
                }
                i++;
            }

            if (startCodeIndices.isEmpty()) {
                baos.write((data.length >> 24) & 0xFF);
                baos.write((data.length >> 16) & 0xFF);
                baos.write((data.length >> 8) & 0xFF);
                baos.write(data.length & 0xFF);
                baos.write(data, 0, data.length);
            } else {
                for (int idx = 0; idx < startCodeIndices.size(); idx++) {
                    int start = startCodeIndices.get(idx) + startCodeLengths.get(idx);
                    int end = (idx + 1 < startCodeIndices.size()) ? startCodeIndices.get(idx + 1) : data.length;
                    int naluLen = end - start;
                    if (naluLen <= 0) continue;

                    int naluType = data[start] & 0x1F;
                    // If SPS (7) or PPS (8) is inside a keyframe buffer, extract for seq header if needed
                    if (naluType == 7 || naluType == 8) {
                        // Omit separate SPS/PPS from sample slice data in AVCC format
                        continue;
                    }

                    baos.write((naluLen >> 24) & 0xFF);
                    baos.write((naluLen >> 16) & 0xFF);
                    baos.write((naluLen >> 8) & 0xFF);
                    baos.write(naluLen & 0xFF);
                    baos.write(data, start, naluLen);
                }
            }

            if (!packetQueue.offer(new RtmpPacket(0x09, timestamp, baos.toByteArray()))) {
                StreamService.droppedFrames++;
            }
        }

        public void sendAacSequenceHeader(byte[] configData) {
            if (configData == null || configData.length == 0) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0xAF); // 44kHz, 16-bit, stereo, AAC
            baos.write(0x00); // AAC sequence header
            baos.write(configData, 0, configData.length);

            aacHeader = baos.toByteArray();
            packetQueue.offer(new RtmpPacket(0x08, 0, aacHeader));
        }

        public void sendAudioFrame(byte[] audioData, long timestamp) {
            if (audioData == null || audioData.length == 0) return;
            int offset = 0;
            int len = audioData.length;
            // Strip ADTS header if present (7 or 9 bytes)
            if (len >= 7 && (audioData[0] & 0xFF) == 0xFF && (audioData[1] & 0xF0) == 0xF0) {
                int headerLen = ((audioData[1] & 0x01) == 0) ? 9 : 7;
                if (len > headerLen) {
                    offset = headerLen;
                    len -= headerLen;
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(len + 2);
            baos.write(0xAF); // 44kHz, 16-bit, stereo, AAC
            baos.write(0x01); // AAC raw data
            baos.write(audioData, offset, len);

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

                app = app.replaceAll("/+$", "");

                Log.i("KimLive-RTMP", "Connecting to " + host + ":" + port + " app=" + app + " (ssl=" + isSsl + ")...");
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
                Log.i("KimLive-RTMP", "Connected! Performing RTMP handshake...");

                out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);
                in = new BufferedInputStream(socket.getInputStream(), 64 * 1024);

                performRtmpHandshake();
                Log.i("KimLive-RTMP", "RTMP Handshake successful! Sending commands...");
                sendSetChunkSize(4096);
                sendConnectCommand(app);
                sendReleaseStream(streamKey);
                sendFCPublish(streamKey);
                sendCreateStream();
                sendPublish(streamKey, app);
                sendMetaData(videoWidth, videoHeight, videoFps, videoBitrateKbps);
                Log.i("KimLive-RTMP", "Broadcast published! Streaming video & audio chunks...");

                startReaderThread();

                while (isRunning.get()) {
                    RtmpPacket packet = packetQueue.take();
                    writeChunk(packet.type, packet.timestamp, packet.data);
                }
            } catch (Exception e) {
                Log.e("KimLive-RTMP", "RTMP Streaming Exception: " + e.getMessage(), e);
            } finally {
                stop();
            }
        }

        private void startReaderThread() {
            readerThread = new Thread(() -> {
                byte[] buf = new byte[8192];
                try {
                    while (isRunning.get() && in != null) {
                        int r = in.read(buf);
                        if (r < 0) break;
                    }
                } catch (Exception ignored) {}
            }, "KimLive-RtmpReader");
            readerThread.setDaemon(true);
            readerThread.start();
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

        private void sendSetChunkSize(int chunkSize) throws Exception {
            out.write(0x02); // CSID 2, Type 0
            out.write(0x00); out.write(0x00); out.write(0x00); // Timestamp 0
            out.write(0x00); out.write(0x00); out.write(0x04); // Length 4
            out.write(0x01); // Message Type 1: Set Chunk Size
            out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00); // Stream ID 0
            out.write((chunkSize >> 24) & 0xFF);
            out.write((chunkSize >> 16) & 0xFF);
            out.write((chunkSize >> 8) & 0xFF);
            out.write(chunkSize & 0xFF);
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

        private void sendMetaData(int width, int height, int fps, int bitrateKbps) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "@setDataFrame");
            writeAmfString(amf, "onMetaData");

            // ECMA Array (0x08)
            amf.write(0x08);
            // Array length = 7
            amf.write(0x00); amf.write(0x00); amf.write(0x00); amf.write(0x07);

            writeAmfProperty(amf, "width", (double) width);
            writeAmfProperty(amf, "height", (double) height);
            writeAmfProperty(amf, "framerate", (double) fps);
            writeAmfProperty(amf, "videocodecid", 7.0); // AVC
            writeAmfProperty(amf, "videodatarate", (double) bitrateKbps);
            writeAmfProperty(amf, "audiocodecid", 10.0); // AAC
            writeAmfProperty(amf, "audiodatarate", 96.0);
            // End object marker: 00 00 09
            amf.write(0x00); amf.write(0x00); amf.write(0x09);

            writeChunk(0x12, 0, amf.toByteArray()); // Msg Type 0x12 = AMF0 Data
        }

        private void writeChunk(int messageType, long timestamp, byte[] payload) throws Exception {
            int csid;
            if (messageType == 0x09) {
                csid = 0x06; // Video
            } else if (messageType == 0x08) {
                csid = 0x04; // Audio
            } else if (messageType == 0x12) {
                csid = 0x05; // Data / Metadata
            } else {
                csid = 0x03; // AMF Command
            }
            int length = payload.length;

            boolean hasExtendedTs = timestamp >= 0xFFFFFF;
            int tsField = hasExtendedTs ? 0xFFFFFF : (int) timestamp;

            // Chunk Header Type 0 (11 bytes)
            out.write((byte) (csid & 0x3F));
            out.write((byte) ((tsField >> 16) & 0xFF));
            out.write((byte) ((tsField >> 8) & 0xFF));
            out.write((byte) (tsField & 0xFF));

            out.write((byte) ((length >> 16) & 0xFF));
            out.write((byte) ((length >> 8) & 0xFF));
            out.write((byte) (length & 0xFF));

            out.write((byte) (messageType & 0xFF));
            out.write(0x01); // Stream ID 1 (little-endian)
            out.write(0x00);
            out.write(0x00);
            out.write(0x00);

            if (hasExtendedTs) {
                out.write((byte) ((timestamp >> 24) & 0xFF));
                out.write((byte) ((timestamp >> 16) & 0xFF));
                out.write((byte) ((timestamp >> 8) & 0xFF));
                out.write((byte) (timestamp & 0xFF));
            }

            // Chunk Body with Type 3 Continuation (Chunk size = 4096)
            int offset = 0;
            int chunkSize = 4096;
            while (offset < length) {
                int sendBytes = Math.min(chunkSize, length - offset);
                out.write(payload, offset, sendBytes);
                offset += sendBytes;
                if (offset < length) {
                    out.write((byte) (0xC0 | (csid & 0x3F))); // Header Type 3
                    if (hasExtendedTs) {
                        out.write((byte) ((timestamp >> 24) & 0xFF));
                        out.write((byte) ((timestamp >> 16) & 0xFF));
                        out.write((byte) ((timestamp >> 8) & 0xFF));
                        out.write((byte) (timestamp & 0xFF));
                    }
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
            if (readerThread != null) {
                readerThread.interrupt();
            }
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
            } catch (Exception ignored) {}
        }
    }
}