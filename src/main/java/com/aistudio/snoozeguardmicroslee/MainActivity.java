package com.aistudio.snoozeguardmicroslee;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String PREFS_NAME = "snooze-guard-microsleep-commute_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode";
    private static final String PREF_INTERVAL_SEC = "pref_interval_sec";
    private static final String PREF_SENSITIVITY = "pref_sensitivity";
    private static final String PREF_HEAD_NOD_DETECT = "pref_head_nod_detect";
    private static final String PREF_ALARM_SOUND_ENABLED = "pref_alarm_sound";
    private static final String PREF_ALARM_VIBE_ENABLED = "pref_alarm_vibe";
    private static final String PREF_HISTORY_JSON = "pref_history_json";

    // UI Elements
    private Button btnThemeToggle;
    private TextView tvStatusBadge;
    private TextView tvCountdownTimer;
    private TextView tvTelemetryStatus;
    private TextView tvTiltTelemetry;
    private TextView tvTriggerCount;
    private TextView tvIntervalDisplay;
    private TextView tvSensitivityDisplay;
    private SeekBar seekInterval;
    private SeekBar seekSensitivity;
    private Switch switchHeadNod;
    private Switch switchAudioAlarm;
    private Switch switchVibration;
    private Button btnToggleGuard;
    private Button btnImAwake;
    private Button btnExportJson;
    private Button btnImportJson;
    private Button btnClearHistory;
    private EditText etJsonData;
    private TextView tvEventHistoryLog;

    // Guard & Alarm State
    private boolean isGuardActive = false;
    private boolean isAlarmSounding = false;
    private int checkIntervalSec = 90; // Default: 90 seconds dead-man alert
    private int nodSensitivityLevel = 5; // Scale 1 to 10
    private int sessionIncidentCount = 0;
    private long remainingTimeMillis = 90000;

    private CountDownTimer vigilCountdownTimer;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Vibrator vibrator;
    private Ringtone alarmRingtone;
    private Handler alarmLoopHandler;
    private Runnable alarmLoopRunnable;

    // Motion filtering for head nod / sudden posture drop
    private float lastPitch = 0f;
    private static final int NOD_COOLDOWN_MS = 6000;
    private long lastNodTriggerTimestamp = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0); // 0 = Auto, 1 = Light, 2 = Dark
        int nightMode;

        if (themeMode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int sysMode = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            nightMode = (sysMode == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and show above keyguard during transit vigilance alerts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        setupWindowInsets();
        initSystemServices();
        bindViews();
        restorePreferences();
        setupListeners();
        updateThemeButtonLabel();
        updateDisplayClock(checkIntervalSec * 1000L);
        renderHistory();
    }

    private void setupWindowInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Window window = getWindow();
            WindowInsetsController insetsController = window.getInsetsController();
            if (insetsController != null) {
                int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (nightMode == Configuration.UI_MODE_NIGHT_NO) {
                    insetsController.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    insetsController.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    private void initSystemServices() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        alarmLoopHandler = new Handler(Looper.getMainLooper());

        Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (notificationUri == null) {
            notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        alarmRingtone = RingtoneManager.getRingtone(getApplicationContext(), notificationUri);
        if (alarmRingtone != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            alarmRingtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        }
    }

    private void bindViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvCountdownTimer = findViewById(R.id.tvCountdownTimer);
        tvTelemetryStatus = findViewById(R.id.tvTelemetryStatus);
        tvTiltTelemetry = findViewById(R.id.tvTiltTelemetry);
        tvTriggerCount = findViewById(R.id.tvTriggerCount);
        tvIntervalDisplay = findViewById(R.id.tvIntervalDisplay);
        tvSensitivityDisplay = findViewById(R.id.tvSensitivityDisplay);
        seekInterval = findViewById(R.id.seekInterval);
        seekSensitivity = findViewById(R.id.seekSensitivity);
        switchHeadNod = findViewById(R.id.switchHeadNod);
        switchAudioAlarm = findViewById(R.id.switchAudioAlarm);
        switchVibration = findViewById(R.id.switchVibration);
        btnToggleGuard = findViewById(R.id.btnToggleGuard);
        btnImAwake = findViewById(R.id.btnImAwake);
        btnExportJson = findViewById(R.id.btnExportJson);
        btnImportJson = findViewById(R.id.btnImportJson);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        etJsonData = findViewById(R.id.etJsonData);
        tvEventHistoryLog = findViewById(R.id.tvEventHistoryLog);
    }

    private void restorePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        checkIntervalSec = prefs.getInt(PREF_INTERVAL_SEC, 90);
        nodSensitivityLevel = prefs.getInt(PREF_SENSITIVITY, 5);
        boolean nodEnabled = prefs.getBoolean(PREF_HEAD_NOD_DETECT, true);
        boolean soundEnabled = prefs.getBoolean(PREF_ALARM_SOUND_ENABLED, true);
        boolean vibeEnabled = prefs.getBoolean(PREF_ALARM_VIBE_ENABLED, true);

        seekInterval.setProgress(checkIntervalSec);
        tvIntervalDisplay.setText(checkIntervalSec + " s");

        seekSensitivity.setProgress(nodSensitivityLevel);
        tvSensitivityDisplay.setText("Level " + nodSensitivityLevel);

        switchHeadNod.setChecked(nodEnabled);
        switchAudioAlarm.setChecked(soundEnabled);
        switchVibration.setChecked(vibeEnabled);
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(PREF_INTERVAL_SEC, checkIntervalSec);
        editor.putInt(PREF_SENSITIVITY, nodSensitivityLevel);
        editor.putBoolean(PREF_HEAD_NOD_DETECT, switchHeadNod.isChecked());
        editor.putBoolean(PREF_ALARM_SOUND_ENABLED, switchAudioAlarm.isChecked());
        editor.putBoolean(PREF_ALARM_VIBE_ENABLED, switchVibration.isChecked());
        editor.apply();
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        seekInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 15) progress = 15; // Minimum 15 seconds
                checkIntervalSec = progress;
                tvIntervalDisplay.setText(checkIntervalSec + " s");
                if (!isGuardActive) {
                    updateDisplayClock(checkIntervalSec * 1000L);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                savePreferences();
            }
        });

        seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 1) progress = 1;
                nodSensitivityLevel = progress;
                tvSensitivityDisplay.setText("Level " + nodSensitivityLevel);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                savePreferences();
            }
        });

        switchHeadNod.setOnCheckedChangeListener((b, isChecked) -> savePreferences());
        switchAudioAlarm.setOnCheckedChangeListener((b, isChecked) -> savePreferences());
        switchVibration.setOnCheckedChangeListener((b, isChecked) -> savePreferences());

        btnToggleGuard.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (isGuardActive) {
                stopVigilGuard("Vigil Stopped by Commuter");
            } else {
                startVigilGuard();
            }
        });

        btnImAwake.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            handleAwakeResponse();
        });

        btnExportJson.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportConfigAndHistoryToJson();
        });

        btnImportJson.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            importConfigAndHistoryFromJson();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearHistory();
        });
    }

    private void cycleThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int current = prefs.getInt(PREF_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(PREF_THEME_MODE, next).apply();
        recreate();
    }

    private void updateThemeButtonLabel() {
        int mode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_THEME_MODE, 0);
        if (mode == 1) {
            btnThemeToggle.setText("☀️ Light");
        } else if (mode == 2) {
            btnThemeToggle.setText("🌙 Dark");
        } else {
            btnThemeToggle.setText("⚡ Auto");
        }
    }

    private void startVigilGuard() {
        isGuardActive = true;
        btnToggleGuard.setText("STOP VIGIL");
        tvStatusBadge.setText("ACTIVE GUARD");
        tvStatusBadge.setBackgroundColor(Color.parseColor("#1B5E20"));
        tvStatusBadge.setTextColor(Color.WHITE);
        btnImAwake.setEnabled(true);
        btnImAwake.setAlpha(1.0f);

        if (switchHeadNod.isChecked() && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        logEvent("Vigil Guard activated. Interval: " + checkIntervalSec + "s");
        resetIntervalCountdown();
    }

    private void stopVigilGuard(String reason) {
        isGuardActive = false;
        btnToggleGuard.setText("START VIGIL");
        tvStatusBadge.setText("STANDBY");
        tvStatusBadge.setBackgroundColor(Color.parseColor("#424242"));
        tvStatusBadge.setTextColor(Color.WHITE);
        btnImAwake.setEnabled(false);
        btnImAwake.setAlpha(0.5f);

        if (vigilCountdownTimer != null) {
            vigilCountdownTimer.cancel();
        }

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        dismissAlarm();
        updateDisplayClock(checkIntervalSec * 1000L);
        logEvent("Guard Deactivated (" + reason + ")");
    }

    private void resetIntervalCountdown() {
        if (vigilCountdownTimer != null) {
            vigilCountdownTimer.cancel();
        }

        remainingTimeMillis = checkIntervalSec * 1000L;
        vigilCountdownTimer = new CountDownTimer(remainingTimeMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingTimeMillis = millisUntilFinished;
                updateDisplayClock(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                updateDisplayClock(0);
                triggerMicrosleepAlarm("Interval Dead-Man Timeout Elapsed");
            }
        }.start();
    }

    private void updateDisplayClock(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        tvCountdownTimer.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
    }

    private void handleAwakeResponse() {
        if (isAlarmSounding) {
            dismissAlarm();
            logEvent("Microsleep Alert Acknowledged by Commuter");
        } else {
            logEvent("Periodic Ping Reset by Commuter");
        }

        if (isGuardActive) {
            tvStatusBadge.setText("ACTIVE GUARD");
            tvStatusBadge.setBackgroundColor(Color.parseColor("#1B5E20"));
            tvStatusBadge.setTextColor(Color.WHITE);
            resetIntervalCountdown();
        }
    }

    private void triggerMicrosleepAlarm(String cause) {
        if (!isGuardActive) return;
        isAlarmSounding = true;
        sessionIncidentCount++;
        tvTriggerCount.setText(String.valueOf(sessionIncidentCount));

        tvStatusBadge.setText("MICROSLEEP DETECTED!");
        tvStatusBadge.setBackgroundColor(Color.parseColor("#B71C1C"));
        tvStatusBadge.setTextColor(Color.WHITE);

        logEvent("ALERT: " + cause + " (Total Incidents: " + sessionIncidentCount + ")");

        // Start pulse loop for audio and haptics
        if (alarmLoopRunnable == null) {
            alarmLoopRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isAlarmSounding) return;

                    if (switchAudioAlarm.isChecked() && alarmRingtone != null && !alarmRingtone.isPlaying()) {
                        alarmRingtone.play();
                    }

                    if (switchVibration.isChecked() && vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(700);
                        }
                    }

                    alarmLoopHandler.postDelayed(this, 1200);
                }
            };
        }
        alarmLoopHandler.post(alarmLoopRunnable);
    }

    private void dismissAlarm() {
        isAlarmSounding = false;
        if (alarmLoopHandler != null && alarmLoopRunnable != null) {
            alarmLoopHandler.removeCallbacks(alarmLoopRunnable);
        }
        if (alarmRingtone != null && alarmRingtone.isPlaying()) {
            alarmRingtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isGuardActive || !switchHeadNod.isChecked()) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float y = event.values[1];
            float z = event.values[2];

            // Pitch tilt angle in degrees: vertical = 90, forward drop = 0 or negative
            float pitchAngle = (float) (Math.atan2(y, z) * 180.0 / Math.PI);
            tvTiltTelemetry.setText(String.format(Locale.US, "Pitch Tilt: %.1f°", pitchAngle));

            long now = System.currentTimeMillis();
            if (now - lastNodTriggerTimestamp > NOD_COOLDOWN_MS) {
                // Higher sensitivity requires lower delta threshold
                float deltaThreshold = Math.max(12.0f, 40.0f - (nodSensitivityLevel * 2.8f));
                float delta = Math.abs(pitchAngle - lastPitch);

                // Detect sudden forward head plunge (pitch drop)
                if (delta > deltaThreshold && pitchAngle < 15.0f && lastPitch > 35.0f) {
                    lastNodTriggerTimestamp = now;
                    tvTelemetryStatus.setText("SUDDEN HEAD DROP DETECTED");
                    triggerMicrosleepAlarm("Sudden Head Nod / Posture Drop Detected");
                } else {
                    tvTelemetryStatus.setText("Telemetry Normal");
                }
            }
            lastPitch = pitchAngle;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void logEvent(String entry) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String fullEntry = "[" + timestamp + "] " + entry;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentHistory = prefs.getString(PREF_HISTORY_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(currentHistory);
            arr.put(fullEntry);
            if (arr.length() > 60) {
                // Keep the most recent 60 logs
                JSONArray trimmed = new JSONArray();
                for (int i = arr.length() - 60; i < arr.length(); i++) {
                    trimmed.put(arr.getString(i));
                }
                arr = trimmed;
            }
            prefs.edit().putString(PREF_HISTORY_JSON, arr.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        renderHistory();
    }

    private void renderHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyJson = prefs.getString(PREF_HISTORY_JSON, "[]");
        StringBuilder sb = new StringBuilder();
        try {
            JSONArray arr = new JSONArray(historyJson);
            for (int i = arr.length() - 1; i >= 0; i--) {
                sb.append(arr.getString(i)).append("\n");
            }
        } catch (JSONException e) {
            sb.append("No commute activity logs available.");
        }

        if (sb.length() == 0) {
            sb.append("System idle. Ready for commute vigil.");
        }
        tvEventHistoryLog.setText(sb.toString().trim());
    }

    private void clearHistory() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_HISTORY_JSON, "[]").apply();
        sessionIncidentCount = 0;
        tvTriggerCount.setText("0");
        renderHistory();
        Toast.makeText(this, "Vigil activity history cleared", Toast.LENGTH_SHORT).show();
    }

    private void exportConfigAndHistoryToJson() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("timestamp", System.currentTimeMillis());
            root.put("intervalSec", checkIntervalSec);
            root.put("nodSensitivity", nodSensitivityLevel);
            root.put("headNodEnabled", switchHeadNod.isChecked());
            root.put("audioAlarmEnabled", switchAudioAlarm.isChecked());
            root.put("vibrationEnabled", switchVibration.isChecked());
            root.put("history", new JSONArray(prefs.getString(PREF_HISTORY_JSON, "[]")));

            String jsonString = root.toString(2);
            etJsonData.setText(jsonString);

            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("SnoozeGuard Config", jsonString);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_TEXT, jsonString);
            startActivity(Intent.createChooser(shareIntent, "Export SnoozeGuard JSON"));

            Toast.makeText(this, "JSON copied & export chooser opened", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Export serialization error", Toast.LENGTH_SHORT).show();
        }
    }

    private void importConfigAndHistoryFromJson() {
        String input = etJsonData.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Paste JSON into the box first", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject root = new JSONObject(input);
            if (root.has("intervalSec")) {
                checkIntervalSec = root.getInt("intervalSec");
                seekInterval.setProgress(checkIntervalSec);
                tvIntervalDisplay.setText(checkIntervalSec + " s");
            }
            if (root.has("nodSensitivity")) {
                nodSensitivityLevel = root.getInt("nodSensitivity");
                seekSensitivity.setProgress(nodSensitivityLevel);
                tvSensitivityDisplay.setText("Level " + nodSensitivityLevel);
            }
            if (root.has("headNodEnabled")) {
                switchHeadNod.setChecked(root.getBoolean("headNodEnabled"));
            }
            if (root.has("audioAlarmEnabled")) {
                switchAudioAlarm.setChecked(root.getBoolean("audioAlarmEnabled"));
            }
            if (root.has("vibrationEnabled")) {
                switchVibration.setChecked(root.getBoolean("vibrationEnabled"));
            }
            if (root.has("history")) {
                JSONArray historyArr = root.getJSONArray("history");
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(PREF_HISTORY_JSON, historyArr.toString())
                        .apply();
                renderHistory();
            }

            savePreferences();
            Toast.makeText(this, "Settings imported successfully", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Invalid JSON structure", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (isAlarmSounding || isGuardActive) {
                        btnImAwake.performClick();
                        return true;
                    } else {
                        btnToggleGuard.performClick();
                        return true;
                    }
                case KeyEvent.KEYCODE_SPACE:
                    btnToggleGuard.performClick();
                    return true;
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_EQUALS:
                    seekInterval.setProgress(Math.min(300, seekInterval.getProgress() + 15));
                    return true;
                case KeyEvent.KEYCODE_MINUS:
                    seekInterval.setProgress(Math.max(15, seekInterval.getProgress() - 15));
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setupWindowInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isGuardActive && switchHeadNod.isChecked() && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // If accelerometer is running and user switches apps or docks, keep or unregister according to state
        if (!isGuardActive && sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVigilGuard("App Destroyed");
    }
}