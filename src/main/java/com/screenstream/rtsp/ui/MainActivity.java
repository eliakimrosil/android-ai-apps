package com.screenstream.rtsp.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.screenstream.rtsp.R;
import com.screenstream.rtsp.camera.FloatingCameraManager;
import com.screenstream.rtsp.service.StreamService;
import com.screenstream.rtsp.utils.NetworkUtils;
import com.screenstream.rtsp.utils.StreamConfig;

public class MainActivity extends Activity implements StreamService.StreamListener {

    private static final int REQUEST_SCREEN_CAPTURE = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;
    private static final int REQUEST_OVERLAY_PERMISSION = 1003;
    private static final int REQUEST_CAMERA_PERMISSION = 1004;

    private Switch switchFloatingCamera;
    private LinearLayout layoutCameraStatusRow;
    private TextView tvCameraInfo;
    private Button btnToggleCameraFacing;
    private Button btnToggleCameraFlip;
    private Button btnToggleCameraRotate;
    private FloatingCameraManager floatingCameraManager;

    private TextView tvStatusBadge;
    private TextView tabRtsp;
    private TextView tabYoutube;
    private TextView tabFacebook;
    private TextView tabCustom;

    private LinearLayout layoutRtspTarget;
    private LinearLayout layoutRtmpTarget;
    private LinearLayout layoutPortRow;

    private TextView tvRtspUrl;
    private Button btnCopyUrl;
    private Button btnShareUrl;

    private TextView tvRtmpServerLabel;
    private EditText etRtmpUrl;
    private TextView tvRtmpStreamKeyLabel;
    private EditText etStreamKey;
    private Button btnPasteKey;
    private TextView tvRtmpHint;

    private Button btnStreamAction;

    private LinearLayout layoutStats;
    private TextView tvStatClients;
    private TextView tvStatFps;
    private TextView tvStatBitrate;
    private TextView tvStatUptime;

    private TextView chipPresetUltra;
    private TextView chipPresetBalanced;
    private TextView chipPresetEsports;
    private TextView chipPresetFast;

    private Spinner spinnerOrientation;
    private Spinner spinnerResolution;
    private Spinner spinnerFps;
    private Spinner spinnerBitrate;
    private Spinner spinnerAudio;
    private EditText etPort;

    private StreamConfig config;
    private StreamService streamService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            StreamService.LocalBinder localBinder = (StreamService.LocalBinder) binder;
            streamService = localBinder.getService();
            isBound = true;
            if (streamService.isStreaming()) {
                updateUiStreaming(true, streamService.getCurrentTargetInfo());
            } else {
                updateUiStreaming(false, null);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            streamService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = StreamConfig.load(this);

        initViews();
        setupTabs();
        setupSpinners();
        setupPresets();
        setupFloatingCamera();
        updateTargetDisplay();
        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (floatingCameraManager != null && switchFloatingCamera != null) {
            boolean showing = floatingCameraManager.isShowing();
            switchFloatingCamera.setChecked(showing);
            if (layoutCameraStatusRow != null) {
                layoutCameraStatusRow.setVisibility(showing ? View.VISIBLE : View.GONE);
                updateCameraInfoText();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        StreamService.addListener(this);
        Intent intent = new Intent(this, StreamService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        StreamService.removeListener(this);
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        saveCurrentConfig();
        super.onStop();
    }

    private void initViews() {
        tvStatusBadge = findViewById(R.id.tv_status_badge);
        tabRtsp = findViewById(R.id.tab_rtsp);
        tabYoutube = findViewById(R.id.tab_youtube);
        tabFacebook = findViewById(R.id.tab_facebook);
        tabCustom = findViewById(R.id.tab_custom);

        layoutRtspTarget = findViewById(R.id.layout_rtsp_target);
        layoutRtmpTarget = findViewById(R.id.layout_rtmp_target);
        layoutPortRow = findViewById(R.id.layout_port_row);

        tvRtspUrl = findViewById(R.id.tv_rtsp_url);
        btnCopyUrl = findViewById(R.id.btn_copy_url);
        btnShareUrl = findViewById(R.id.btn_share_url);

        tvRtmpServerLabel = findViewById(R.id.tv_rtmp_server_label);
        etRtmpUrl = findViewById(R.id.et_rtmp_url);
        tvRtmpStreamKeyLabel = findViewById(R.id.tv_rtmp_stream_key_label);
        etStreamKey = findViewById(R.id.et_stream_key);
        btnPasteKey = findViewById(R.id.btn_paste_key);
        tvRtmpHint = findViewById(R.id.tv_rtmp_hint);

        btnStreamAction = findViewById(R.id.btn_stream_action);

        layoutStats = findViewById(R.id.layout_stats);
        tvStatClients = findViewById(R.id.tv_stat_clients);
        tvStatFps = findViewById(R.id.tv_stat_fps);
        tvStatBitrate = findViewById(R.id.tv_stat_bitrate);
        tvStatUptime = findViewById(R.id.tv_stat_uptime);

        chipPresetUltra = findViewById(R.id.chip_preset_ultra);
        chipPresetBalanced = findViewById(R.id.chip_preset_balanced);
        chipPresetEsports = findViewById(R.id.chip_preset_esports);
        chipPresetFast = findViewById(R.id.chip_preset_fast);

        spinnerOrientation = findViewById(R.id.spinner_orientation);
        spinnerResolution = findViewById(R.id.spinner_resolution);
        spinnerFps = findViewById(R.id.spinner_fps);
        spinnerBitrate = findViewById(R.id.spinner_bitrate);
        spinnerAudio = findViewById(R.id.spinner_audio);
        etPort = findViewById(R.id.et_port);

        btnCopyUrl.setOnClickListener(v -> copyToClipboard(tvRtspUrl.getText().toString(), "RTSP URL"));
        btnShareUrl.setOnClickListener(v -> shareRtspUrl());

        btnPasteKey.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    etStreamKey.setText(text.toString().trim());
                    Toast.makeText(this, "Stream key pasted!", Toast.LENGTH_SHORT).show();
                    saveCurrentConfig();
                }
            }
        });

        btnStreamAction.setOnClickListener(v -> {
            if (streamService != null && streamService.isStreaming()) {
                stopStreamService();
            } else {
                startStreamWorkflow();
            }
        });

        switchFloatingCamera = findViewById(R.id.switch_floating_camera);
        layoutCameraStatusRow = findViewById(R.id.layout_camera_status_row);
        tvCameraInfo = findViewById(R.id.tv_camera_info);
        btnToggleCameraFacing = findViewById(R.id.btn_toggle_camera_facing);
        btnToggleCameraFlip = findViewById(R.id.btn_toggle_camera_flip);
        btnToggleCameraRotate = findViewById(R.id.btn_toggle_camera_rotate);

        etPort.setText(String.valueOf(config.port));
    }

    private void setupFloatingCamera() {
        floatingCameraManager = FloatingCameraManager.getInstance(this);
        floatingCameraManager.setFrontCamera(config.floatingCameraFacingFront);
        floatingCameraManager.setFlipped(config.floatingCameraFlipped);
        floatingCameraManager.setUserRotation(config.floatingCameraRotation);
        floatingCameraManager.setSizeIndex(config.floatingCameraSizeIndex);

        floatingCameraManager.setStateChangeListener(new FloatingCameraManager.OnOverlayStateChangeListener() {
            @Override
            public void onOverlayVisibilityChanged(boolean visible) {
                runOnUiThread(() -> {
                    if (switchFloatingCamera != null) {
                        switchFloatingCamera.setChecked(visible);
                    }
                    if (layoutCameraStatusRow != null) {
                        layoutCameraStatusRow.setVisibility(visible ? View.VISIBLE : View.GONE);
                    }
                    updateCameraInfoText();
                    config.floatingCameraEnabled = visible;
                    config.save(MainActivity.this);
                });
            }

            @Override
            public void onOverlayParamsChanged(boolean front, boolean flipped, int userRotation, int sizeIndex) {
                runOnUiThread(() -> {
                    config.floatingCameraFacingFront = front;
                    config.floatingCameraFlipped = flipped;
                    config.floatingCameraRotation = userRotation;
                    config.floatingCameraSizeIndex = sizeIndex;
                    config.save(MainActivity.this);
                    updateCameraInfoText();
                });
            }
        });

        switchFloatingCamera.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!checkOverlayAndCameraPermissions()) {
                    switchFloatingCamera.setChecked(false);
                    return;
                }
                boolean shown = floatingCameraManager.show();
                if (shown) {
                    layoutCameraStatusRow.setVisibility(View.VISIBLE);
                    updateCameraInfoText();
                    config.floatingCameraEnabled = true;
                    config.save(this);
                } else {
                    switchFloatingCamera.setChecked(false);
                }
            } else {
                floatingCameraManager.hide();
                layoutCameraStatusRow.setVisibility(View.GONE);
                config.floatingCameraEnabled = false;
                config.save(this);
            }
        });

        if (btnToggleCameraFacing != null) {
            btnToggleCameraFacing.setOnClickListener(v -> {
                boolean newFacing = !floatingCameraManager.isFrontCamera();
                floatingCameraManager.setFrontCamera(newFacing);
                config.floatingCameraFacingFront = newFacing;
                config.floatingCameraFlipped = floatingCameraManager.isFlipped();
                config.save(this);
                updateCameraInfoText();
                if (floatingCameraManager.isShowing()) {
                    floatingCameraManager.reopenCamera();
                }
            });
        }

        if (btnToggleCameraFlip != null) {
            btnToggleCameraFlip.setOnClickListener(v -> {
                floatingCameraManager.toggleFlip();
                config.floatingCameraFlipped = floatingCameraManager.isFlipped();
                config.save(this);
                updateCameraInfoText();
            });
        }

        if (btnToggleCameraRotate != null) {
            btnToggleCameraRotate.setOnClickListener(v -> {
                floatingCameraManager.cycleRotation();
                config.floatingCameraRotation = floatingCameraManager.getUserRotation();
                config.save(this);
                updateCameraInfoText();
            });
        }

        if (config.floatingCameraEnabled) {
            if (hasOverlayPermission() && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                switchFloatingCamera.setChecked(true);
                floatingCameraManager.show();
                layoutCameraStatusRow.setVisibility(View.VISIBLE);
                updateCameraInfoText();
            }
        }
    }

    private void updateCameraInfoText() {
        if (floatingCameraManager == null || tvCameraInfo == null) return;
        boolean front = floatingCameraManager.isFrontCamera();
        boolean flipped = floatingCameraManager.isFlipped();
        int rot = floatingCameraManager.getUserRotation();

        tvCameraInfo.setText(String.format("1:1 • %s • %s • %d°",
                front ? "Front" : "Back",
                flipped ? "Mirrored" : "Normal",
                rot));

        if (btnToggleCameraFacing != null) {
            btnToggleCameraFacing.setText(front ? "📷 Front" : "📷 Back");
        }
        if (btnToggleCameraFlip != null) {
            btnToggleCameraFlip.setText(flipped ? "⇄ Mirrored" : "⇄ Normal");
        }
        if (btnToggleCameraRotate != null) {
            btnToggleCameraRotate.setText("↻ " + rot + "°");
        }
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private boolean checkOverlayAndCameraPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Enable 'Display over other apps' to use floating camera", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return false;
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return false;
        }

        return true;
    }

    private void setupTabs() {
        tabRtsp.setOnClickListener(v -> selectTab(StreamConfig.TARGET_RTSP));
        tabYoutube.setOnClickListener(v -> selectTab(StreamConfig.TARGET_YOUTUBE));
        tabFacebook.setOnClickListener(v -> selectTab(StreamConfig.TARGET_FACEBOOK));
        tabCustom.setOnClickListener(v -> selectTab(StreamConfig.TARGET_CUSTOM));

        updateTabSelection();
    }

    private void selectTab(int target) {
        saveCurrentConfig();
        config.streamTarget = target;
        updateTabSelection();
        config.save(this);
    }

    private void updateTabSelection() {
        int t = config.streamTarget;
        boolean isRtsp = (t == StreamConfig.TARGET_RTSP);
        boolean isYt = (t == StreamConfig.TARGET_YOUTUBE);
        boolean isFb = (t == StreamConfig.TARGET_FACEBOOK);
        boolean isCustom = (t == StreamConfig.TARGET_CUSTOM);

        tabRtsp.setBackgroundResource(isRtsp ? R.drawable.chip_preset_selected : R.drawable.chip_preset);
        tabRtsp.setTextColor(getColor(isRtsp ? R.color.primary : R.color.text_primary));

        tabYoutube.setBackgroundResource(isYt ? R.drawable.chip_preset_selected : R.drawable.chip_preset);
        tabYoutube.setTextColor(getColor(isYt ? R.color.stop_red : R.color.text_primary));

        tabFacebook.setBackgroundResource(isFb ? R.drawable.chip_preset_selected : R.drawable.chip_preset);
        tabFacebook.setTextColor(getColor(isFb ? R.color.secondary : R.color.text_primary));

        tabCustom.setBackgroundResource(isCustom ? R.drawable.chip_preset_selected : R.drawable.chip_preset);
        tabCustom.setTextColor(getColor(isCustom ? R.color.primary : R.color.text_primary));

        layoutRtspTarget.setVisibility(isRtsp ? View.VISIBLE : View.GONE);
        layoutRtmpTarget.setVisibility(!isRtsp ? View.VISIBLE : View.GONE);
        layoutPortRow.setVisibility(isRtsp ? View.VISIBLE : View.GONE);

        if (isYt) {
            tvRtmpServerLabel.setText("YOUTUBE RTMP SERVER URL");
            etRtmpUrl.setText(config.youtubeServerUrl);
            tvRtmpStreamKeyLabel.setText("YOUTUBE STREAM KEY");
            etStreamKey.setHint("Paste YouTube stream key (xxxx-xxxx-xxxx-xxxx-xxxx)");
            etStreamKey.setText(config.youtubeStreamKey);
            tvRtmpHint.setText("Uses standard RTMP (rtmp://) or secure RTMPS (rtmps://). Both supported!");
        } else if (isFb) {
            tvRtmpServerLabel.setText("FACEBOOK RTMPS SERVER URL");
            etRtmpUrl.setText(config.fbServerUrl);
            tvRtmpStreamKeyLabel.setText("FACEBOOK STREAM KEY");
            etStreamKey.setHint("Paste FB-xxxx stream key...");
            etStreamKey.setText(config.fbStreamKey);
            tvRtmpHint.setText("Get your stream key from facebook.com/live/producer > Streaming software.");
        } else if (isCustom) {
            tvRtmpServerLabel.setText("CUSTOM RTMP SERVER URL");
            etRtmpUrl.setText(config.customRtmpUrl);
            tvRtmpStreamKeyLabel.setText("STREAM KEY");
            etStreamKey.setHint("Paste RTMP stream key...");
            etStreamKey.setText(config.customStreamKey);
            tvRtmpHint.setText("Works with Twitch, Kick, TikTok, or private RTMP servers.");
        }
    }

    private void updateResolutionSpinner() {
        String[] resolutions;
        if (config.orientation == StreamConfig.ORIENTATION_PORTRAIT) {
            resolutions = new String[]{
                    "720p (720x1280)",
                    "1080p (1080x1920)",
                    "Native (Portrait)",
                    "480p (480x854)"
            };
        } else if (config.orientation == StreamConfig.ORIENTATION_AUTO) {
            resolutions = new String[]{
                    "720p (HD Auto)",
                    "1080p (FHD Auto)",
                    "Native (Auto)",
                    "480p (SD Auto)"
            };
        } else {
            resolutions = new String[]{
                    "720p (1280x720)",
                    "1080p (1920x1080)",
                    "Native (Landscape)",
                    "480p (854x480)"
            };
        }
        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, resolutions);
        spinnerResolution.setAdapter(resAdapter);
        if (config.targetDimension == 1080) spinnerResolution.setSelection(1);
        else if (config.targetDimension <= 0) spinnerResolution.setSelection(2);
        else if (config.targetDimension == 480) spinnerResolution.setSelection(3);
        else spinnerResolution.setSelection(0);
    }

    private void setupSpinners() {
        String[] orientations = new String[]{
                "Landscape 16:9",
                "Portrait 9:16 (Shorts)",
                "Auto (Screen)"
        };
        ArrayAdapter<String> oriAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, orientations);
        spinnerOrientation.setAdapter(oriAdapter);
        if (config.orientation == StreamConfig.ORIENTATION_PORTRAIT) spinnerOrientation.setSelection(1);
        else if (config.orientation == StreamConfig.ORIENTATION_AUTO) spinnerOrientation.setSelection(2);
        else spinnerOrientation.setSelection(0);

        updateResolutionSpinner();

        String[] fpsList = new String[]{"60 FPS (Gaming)", "30 FPS (Standard)", "90 FPS (High Refresh)", "120 FPS (Ultra Smooth)"};
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fpsList);
        spinnerFps.setAdapter(fpsAdapter);
        if (config.fps == 30) spinnerFps.setSelection(1);
        else if (config.fps == 90) spinnerFps.setSelection(2);
        else if (config.fps == 120) spinnerFps.setSelection(3);
        else spinnerFps.setSelection(0);

        String[] bitrates = new String[]{"4 Mbps", "6 Mbps", "8 Mbps", "12 Mbps", "16 Mbps", "20 Mbps"};
        ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, bitrates);
        spinnerBitrate.setAdapter(bitrateAdapter);
        int br = config.bitrate / 1000000;
        if (br <= 4) spinnerBitrate.setSelection(0);
        else if (br == 6) spinnerBitrate.setSelection(1);
        else if (br == 8) spinnerBitrate.setSelection(2);
        else if (br == 12) spinnerBitrate.setSelection(3);
        else if (br == 16) spinnerBitrate.setSelection(4);
        else spinnerBitrate.setSelection(5);

        String[] audios = new String[]{"Internal Game Audio", "Microphone", "Mute (No Audio)", "Game Audio + Mic (Dual)"};
        ArrayAdapter<String> audioAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, audios);
        spinnerAudio.setAdapter(audioAdapter);
        spinnerAudio.setSelection(Math.min(config.audioMode, 3));

        spinnerOrientation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int oldOri = config.orientation;
                if (position == 1) config.orientation = StreamConfig.ORIENTATION_PORTRAIT;
                else if (position == 2) config.orientation = StreamConfig.ORIENTATION_AUTO;
                else config.orientation = StreamConfig.ORIENTATION_LANDSCAPE;

                if (oldOri != config.orientation) {
                    updateResolutionSpinner();
                    saveCurrentConfig();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AdapterView.OnItemSelectedListener changeListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveCurrentConfig();
                updateTargetDisplay();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerResolution.setOnItemSelectedListener(changeListener);
        spinnerFps.setOnItemSelectedListener(changeListener);
        spinnerBitrate.setOnItemSelectedListener(changeListener);
        spinnerAudio.setOnItemSelectedListener(changeListener);
    }

    private void setupPresets() {
        chipPresetUltra.setOnClickListener(v -> applyPreset(1080, 60, 10000000, chipPresetUltra));
        chipPresetBalanced.setOnClickListener(v -> applyPreset(720, 60, 6000000, chipPresetBalanced));
        chipPresetEsports.setOnClickListener(v -> applyPreset(720, 120, 12000000, chipPresetEsports));
        chipPresetFast.setOnClickListener(v -> applyPreset(720, 30, 3500000, chipPresetFast));
    }

    private void applyPreset(int targetDim, int fps, int bitrate, View selectedChip) {
        config.targetDimension = targetDim;
        config.fps = fps;
        config.bitrate = bitrate;

        if (targetDim == 1080) spinnerResolution.setSelection(1);
        else if (targetDim == 720) spinnerResolution.setSelection(0);

        if (fps == 30) spinnerFps.setSelection(1);
        else if (fps == 60) spinnerFps.setSelection(0);
        else if (fps == 120) spinnerFps.setSelection(3);

        int mbps = bitrate / 1000000;
        if (mbps <= 4) spinnerBitrate.setSelection(0);
        else if (mbps <= 6) spinnerBitrate.setSelection(1);
        else if (mbps <= 8) spinnerBitrate.setSelection(2);
        else if (mbps <= 12) spinnerBitrate.setSelection(3);

        updateChipHighlight(selectedChip);
        config.save(this);
    }

    private void updateChipHighlight(View selectedChip) {
        chipPresetUltra.setBackgroundResource(R.drawable.chip_preset);
        chipPresetBalanced.setBackgroundResource(R.drawable.chip_preset);
        chipPresetEsports.setBackgroundResource(R.drawable.chip_preset);
        chipPresetFast.setBackgroundResource(R.drawable.chip_preset);

        chipPresetUltra.setTextColor(getColor(R.color.text_primary));
        chipPresetBalanced.setTextColor(getColor(R.color.text_primary));
        chipPresetEsports.setTextColor(getColor(R.color.text_primary));
        chipPresetFast.setTextColor(getColor(R.color.text_primary));

        if (selectedChip instanceof TextView) {
            selectedChip.setBackgroundResource(R.drawable.chip_preset_selected);
            ((TextView) selectedChip).setTextColor(getColor(R.color.primary));
        }
    }

    private void saveCurrentConfig() {
        int oriPos = spinnerOrientation.getSelectedItemPosition();
        if (oriPos == 1) config.orientation = StreamConfig.ORIENTATION_PORTRAIT;
        else if (oriPos == 2) config.orientation = StreamConfig.ORIENTATION_AUTO;
        else config.orientation = StreamConfig.ORIENTATION_LANDSCAPE;

        int resPos = spinnerResolution.getSelectedItemPosition();
        if (resPos == 0) config.targetDimension = 720;
        else if (resPos == 1) config.targetDimension = 1080;
        else if (resPos == 2) config.targetDimension = 0;
        else if (resPos == 3) config.targetDimension = 480;

        int fpsPos = spinnerFps.getSelectedItemPosition();
        if (fpsPos == 0) config.fps = 60;
        else if (fpsPos == 1) config.fps = 30;
        else if (fpsPos == 2) config.fps = 90;
        else if (fpsPos == 3) config.fps = 120;

        int brPos = spinnerBitrate.getSelectedItemPosition();
        int[] brValues = {4000000, 6000000, 8000000, 12000000, 16000000, 20000000};
        if (brPos >= 0 && brPos < brValues.length) {
            config.bitrate = brValues[brPos];
        }

        config.audioMode = spinnerAudio.getSelectedItemPosition();

        String url = etRtmpUrl.getText().toString().trim();
        config.setActiveRtmpUrl(url);

        String key = etStreamKey.getText().toString().trim();
        config.setActiveStreamKey(key);

        try {
            config.port = Integer.parseInt(etPort.getText().toString().trim());
        } catch (Exception e) {
            config.port = 8554;
        }

        config.save(this);
    }

    private void updateTargetDisplay() {
        String hostIp = NetworkUtils.getPrimaryIpAddress(this);
        int port = config.port > 0 ? config.port : 8554;
        String url = "rtsp://" + hostIp + ":" + port + "/live";
        tvRtspUrl.setText(url);
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.RECORD_AUDIO
                }, REQUEST_PERMISSIONS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSIONS);
            }
        }
    }

    private void startStreamWorkflow() {
        saveCurrentConfig();

        if (config.streamTarget != StreamConfig.TARGET_RTSP) {
            String key = config.getActiveStreamKey();
            if (key.isEmpty()) {
                String platform = (config.streamTarget == StreamConfig.TARGET_YOUTUBE) ? "YouTube" :
                                  (config.streamTarget == StreamConfig.TARGET_FACEBOOK) ? "Facebook" : "RTMP";
                Toast.makeText(this, "Please enter your " + platform + " Stream Key first!", Toast.LENGTH_LONG).show();
                etStreamKey.requestFocus();
                return;
            }
        }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, StreamService.class);
                serviceIntent.setAction(StreamService.ACTION_START);
                serviceIntent.putExtra(StreamService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(StreamService.EXTRA_RESULT_DATA, data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } else {
                Toast.makeText(this, "Screen capture permission is required to stream", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    if (switchFloatingCamera != null) {
                        switchFloatingCamera.setChecked(true);
                    }
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            } else {
                Toast.makeText(this, "Overlay permission not granted", Toast.LENGTH_SHORT).show();
                if (switchFloatingCamera != null) {
                    switchFloatingCamera.setChecked(false);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (hasOverlayPermission() && switchFloatingCamera != null) {
                    switchFloatingCamera.setChecked(true);
                }
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                if (switchFloatingCamera != null) {
                    switchFloatingCamera.setChecked(false);
                }
            }
        }
    }

    private void stopStreamService() {
        Intent serviceIntent = new Intent(this, StreamService.class);
        serviceIntent.setAction(StreamService.ACTION_STOP);
        startService(serviceIntent);
    }

    private void updateUiStreaming(boolean streaming, String targetInfo) {
        if (streaming) {
            tvStatusBadge.setText(R.string.status_live);
            tvStatusBadge.setBackgroundResource(R.drawable.badge_status_live);
            tvStatusBadge.setTextColor(getColor(R.color.live_green));

            btnStreamAction.setText(R.string.stop_stream);
            btnStreamAction.setBackgroundResource(R.drawable.btn_stop);
            btnStreamAction.setTextColor(getColor(R.color.text_primary));

            if (targetInfo != null && targetInfo.startsWith("rtsp://")) {
                tvRtspUrl.setText(targetInfo);
            }

            disableConfigControls(true);
        } else {
            tvStatusBadge.setText(R.string.status_offline);
            tvStatusBadge.setBackgroundResource(R.drawable.badge_status_offline);
            tvStatusBadge.setTextColor(getColor(R.color.text_secondary));

            btnStreamAction.setText(R.string.start_stream);
            btnStreamAction.setBackgroundResource(R.drawable.btn_start);
            btnStreamAction.setTextColor(getColor(R.color.on_primary));

            tvStatClients.setText("Idle");
            tvStatFps.setText("0");
            tvStatBitrate.setText("0.0M");
            tvStatUptime.setText("00:00");

            disableConfigControls(false);
            updateTargetDisplay();
        }
    }

    private void disableConfigControls(boolean disabled) {
        tabRtsp.setEnabled(!disabled);
        tabYoutube.setEnabled(!disabled);
        tabFacebook.setEnabled(!disabled);
        tabCustom.setEnabled(!disabled);
        etRtmpUrl.setEnabled(!disabled);
        etStreamKey.setEnabled(!disabled);
        btnPasteKey.setEnabled(!disabled);

        spinnerResolution.setEnabled(!disabled);
        spinnerFps.setEnabled(!disabled);
        spinnerBitrate.setEnabled(!disabled);
        spinnerAudio.setEnabled(!disabled);
        etPort.setEnabled(!disabled);
        chipPresetUltra.setEnabled(!disabled);
        chipPresetBalanced.setEnabled(!disabled);
        chipPresetEsports.setEnabled(!disabled);
        chipPresetFast.setEnabled(!disabled);
    }

    private void copyToClipboard(String text, String label) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, label + " copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareRtspUrl() {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, tvRtspUrl.getText().toString());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share RTSP URL"));
    }

    @Override
    public void onStreamStarted(String targetInfo) {
        runOnUiThread(() -> updateUiStreaming(true, targetInfo));
    }

    @Override
    public void onStreamStopped() {
        runOnUiThread(() -> updateUiStreaming(false, null));
    }

    @Override
    public void onStatsUpdate(int activeClients, float fps, float bitrateMbps, long uptimeSeconds) {
        runOnUiThread(() -> {
            if (config.streamTarget != StreamConfig.TARGET_RTSP) {
                tvStatClients.setText("Live");
            } else {
                tvStatClients.setText(String.valueOf(activeClients));
            }

            tvStatFps.setText(String.format("%.0f", fps));
            tvStatBitrate.setText(String.format("%.1fM", bitrateMbps));

            long min = uptimeSeconds / 60;
            long sec = uptimeSeconds % 60;
            tvStatUptime.setText(String.format("%02d:%02d", min, sec));
        });
    }

    @Override
    public void onStreamError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            updateUiStreaming(false, null);
        });
    }
}
