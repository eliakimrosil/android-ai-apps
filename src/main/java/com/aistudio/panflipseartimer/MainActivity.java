package com.aistudio.panflipseartimer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "pan-flip-sear-timer_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String PREF_CUSTOM_PRESETS = "custom_presets_json";
    private static final String PREF_HISTORY = "sear_history_json";
    private static final String PREF_KEEP_SCREEN_ON = "pref_keep_screen_on";
    private static final String PREF_SOUND_ENABLED = "pref_sound_enabled";
    private static final String PREF_VIBRATE_ENABLED = "pref_vibrate_enabled";
    private static final String PREF_TARGET_FLIPS = "pref_target_flips";
    private static final String PREF_SEAR_DURATION = "pref_sear_duration";

    // Built-in presets: Name, SecondsPerSide, TotalFlips, Doneness/Meat
    private static final String[][] DEFAULT_PRESETS = {
            {"Ribeye Sear (Med-Rare)", "120", "2", "High Cast Iron • 2 min/side"},
            {"Smash Burger Patty", "90", "1", "Screaming Hot • 90s crust then flip"},
            {"Crispy Salmon Skin", "180", "1", "Skin side 3m, Flesh side 1m"},
            {"Pork Chop Golden Sear", "150", "2", "Med-High • 2.5 min/side"},
            {"Tofu Crisp Blocks", "120", "4", "4-sided rotational sear 2 min each"}
    };

    // UI Elements
    private TextView tvThemeToggle;
    private TextView tvLockStatus;
    private TextView tvTimerDisplay;
    private TextView tvPhaseLabel;
    private TextView tvSideIndicator;
    private TextView tvFlipCounter;
    private TextView tvPresetDescription;
    private ProgressBar progressTimer;

    private Button btnStartPause;
    private Button btnReset;
    private Button btnManualFlip;
    private Button btnQuickPlus30;
    private Button btnQuickMinus15;

    private SeekBar seekDuration;
    private TextView tvDurationValue;
    private SeekBar seekFlips;
    private TextView tvFlipsValue;

    private Switch switchKeepScreenOn;
    private Switch switchSound;
    private Switch switchVibrate;

    private LinearLayout layoutPresetContainer;
    private LinearLayout layoutHistoryContainer;
    private ScrollView mainScrollView;

    private EditText etProteinName;
    private Button btnSaveCustomPreset;
    private Button btnExportJson;
    private Button btnImportJson;
    private Button btnClearHistory;

    // Timer and State Tracking
    private boolean isRunning = false;
    private boolean isPaused = false;
    private int currentIntervalSec = 120;
    private int currentTargetFlips = 2;
    private int completedFlips = 0;
    private int currentSide = 1;
    private long remainingMillis = 120000;
    private long totalIntervalMillis = 120000;
    private CountDownTimer countDownTimer;

    // Telemetry and Session Stats
    private String currentProteinTitle = "Ribeye Sear (Med-Rare)";
    private String currentPanDescription = "High Cast Iron • 2 min/side";
    private long sessionStartTime = 0;

    // Hardware alerts
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark

        int nightMode;
        if (themeMode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNightMode = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            nightMode = (systemNightMode == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lockscreen and Turn Screen On Sentinel Requirements
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        setContentView(R.layout.activity_main);

        initSystemServices();
        initViews();
        restorePreferences();
        applyStatusBarTheme();
        renderPresetButtons();
        renderHistoryList();
        updateDisplayTelemetry();
    }

    private void initSystemServices() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception ignored) {}
    }

    private void applyStatusBarTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);
        boolean isLightMode = false;
        if (themeMode == 1) {
            isLightMode = true;
        } else if (themeMode == 0) {
            int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isLightMode = (currentNightMode == Configuration.UI_MODE_NIGHT_NO);
        }

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (isLightMode) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (isLightMode) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        if (tvThemeToggle != null) {
            if (themeMode == 0) {
                tvThemeToggle.setText("Theme: Auto");
            } else if (themeMode == 1) {
                tvThemeToggle.setText("Theme: Light");
            } else {
                tvThemeToggle.setText("Theme: Dark");
            }
        }
    }

    private void initViews() {
        mainScrollView = findViewById(R.id.mainScrollView);
        tvThemeToggle = findViewById(R.id.btnThemeToggle);
        tvLockStatus = findViewById(R.id.tvLockStatus);
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay);
        tvPhaseLabel = findViewById(R.id.tvPhaseLabel);
        tvSideIndicator = findViewById(R.id.tvSideIndicator);
        tvFlipCounter = findViewById(R.id.tvFlipCounter);
        tvPresetDescription = findViewById(R.id.tvPresetDescription);
        progressTimer = findViewById(R.id.progressTimer);

        btnStartPause = findViewById(R.id.btnStartPause);
        btnReset = findViewById(R.id.btnReset);
        btnManualFlip = findViewById(R.id.btnManualFlip);
        btnQuickPlus30 = findViewById(R.id.btnQuickPlus30);
        btnQuickMinus15 = findViewById(R.id.btnQuickMinus15);

        seekDuration = findViewById(R.id.seekDuration);
        tvDurationValue = findViewById(R.id.tvDurationValue);
        seekFlips = findViewById(R.id.seekFlips);
        tvFlipsValue = findViewById(R.id.tvFlipsValue);

        switchKeepScreenOn = findViewById(R.id.switchKeepScreenOn);
        switchSound = findViewById(R.id.switchSound);
        switchVibrate = findViewById(R.id.switchVibrate);

        layoutPresetContainer = findViewById(R.id.layoutPresetContainer);
        layoutHistoryContainer = findViewById(R.id.layoutHistoryContainer);

        etProteinName = findViewById(R.id.etProteinName);
        btnSaveCustomPreset = findViewById(R.id.btnSaveCustomPreset);
        btnExportJson = findViewById(R.id.btnExportJson);
        btnImportJson = findViewById(R.id.btnImportJson);
        btnClearHistory = findViewById(R.id.btnClearHistory);

        // Theme Toggle Listener
        tvThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        // Main Timer Buttons
        btnStartPause.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleStartPause();
        });

        btnReset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetTimerState();
        });

        btnManualFlip.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            triggerManualFlip();
        });

        btnQuickPlus30.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            adjustRemainingSeconds(30);
        });

        btnQuickMinus15.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            adjustRemainingSeconds(-15);
        });

        // Seekbars
        seekDuration.setMax(600); // up to 10 minutes (600s), minimum 15s
        seekDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int adjusted = Math.max(15, progress);
                tvDurationValue.setText(formatTime(adjusted));
                if (fromUser && !isRunning) {
                    currentIntervalSec = adjusted;
                    totalIntervalMillis = currentIntervalSec * 1000L;
                    remainingMillis = totalIntervalMillis;
                    updateDisplayTelemetry();
                    savePreferences();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekFlips.setMax(8); // up to 8 flips
        seekFlips.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int adjusted = Math.max(1, progress);
                tvFlipsValue.setText(adjusted + (adjusted == 1 ? " Flip (2 Sides)" : " Flips"));
                if (fromUser && !isRunning) {
                    currentTargetFlips = adjusted;
                    updateDisplayTelemetry();
                    savePreferences();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Toggles
        switchKeepScreenOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateScreenWakeLock(isChecked);
            savePreferences();
        });

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> savePreferences());
        switchVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> savePreferences());

        // Custom Presets and IO
        btnSaveCustomPreset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveCustomPresetFromInput();
        });

        btnExportJson.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportFullJsonToShare();
        });

        btnImportJson.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            importJsonFromClipboard();
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

    private void updateScreenWakeLock(boolean keepOn) {
        if (keepOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            tvLockStatus.setText("LOCKED ON PAN • SCREEN AWAKE");
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            tvLockStatus.setText("SENTINEL IDLE • SCREEN NORMAL");
        }
    }

    private void toggleStartPause() {
        if (!isRunning) {
            startTimer(remainingMillis > 0 ? remainingMillis : (currentIntervalSec * 1000L));
        } else {
            pauseTimer();
        }
    }

    private void startTimer(long durationMillis) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        isRunning = true;
        isPaused = false;
        if (sessionStartTime == 0) {
            sessionStartTime = System.currentTimeMillis();
        }

        btnStartPause.setText("PAUSE SEAR");
        tvPhaseLabel.setText("ACTIVE SEAR IN PROGRESS");
        tvPhaseLabel.setTextColor(Color.parseColor("#FF5722")); // High-heat Flare Orange

        totalIntervalMillis = currentIntervalSec * 1000L;
        remainingMillis = durationMillis;

        countDownTimer = new CountDownTimer(remainingMillis, 50) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                updateDisplayTelemetry();
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                handleIntervalCompletion();
            }
        }.start();
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isRunning = false;
        isPaused = true;
        btnStartPause.setText("RESUME SEAR");
        tvPhaseLabel.setText("SEAR PAUSED");
        tvPhaseLabel.setTextColor(Color.parseColor("#FFC107"));
    }

    private void resetTimerState() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isRunning = false;
        isPaused = false;
        completedFlips = 0;
        currentSide = 1;
        sessionStartTime = 0;
        remainingMillis = currentIntervalSec * 1000L;
        totalIntervalMillis = remainingMillis;

        btnStartPause.setText("IGNITE SEAR");
        tvPhaseLabel.setText("READY FOR PAN CONTACT");
        tvPhaseLabel.setTextColor(Color.parseColor("#4CAF50"));

        updateDisplayTelemetry();
    }

    private void adjustRemainingSeconds(int deltaSeconds) {
        long deltaMillis = deltaSeconds * 1000L;
        remainingMillis = Math.max(5000L, remainingMillis + deltaMillis);
        if (remainingMillis > totalIntervalMillis) {
            totalIntervalMillis = remainingMillis;
        }
        if (isRunning) {
            startTimer(remainingMillis);
        } else {
            updateDisplayTelemetry();
        }
    }

    private void triggerManualFlip() {
        if (completedFlips >= currentTargetFlips) {
            Toast.makeText(this, "Target flips already achieved!", Toast.LENGTH_SHORT).show();
            return;
        }
        executeFlipTransition("MANUAL FLIP TRIGGERED");
    }

    private void handleIntervalCompletion() {
        executeFlipTransition("INTERVAL COMPLETE — TIME TO FLIP!");
    }

    private void executeFlipTransition(String reason) {
        completedFlips++;
        currentSide++;
        triggerAlertFeedback();

        if (completedFlips >= currentTargetFlips) {
            // Sear Completed!
            finishSearSession();
        } else {
            // Next flip / side
            remainingMillis = currentIntervalSec * 1000L;
            totalIntervalMillis = remainingMillis;
            tvPhaseLabel.setText("FLIP NOW! SIDE " + currentSide + " ACTIVE");
            tvPhaseLabel.setTextColor(Color.parseColor("#E91E63"));
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
            if (isRunning) {
                startTimer(remainingMillis);
            } else {
                updateDisplayTelemetry();
            }
        }
    }

    private void finishSearSession() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isRunning = false;
        isPaused = false;
        remainingMillis = 0;

        btnStartPause.setText("START NEW SEAR");
        tvPhaseLabel.setText("PERFECT SEAR COMPLETE — REST MEAT!");
        tvPhaseLabel.setTextColor(Color.parseColor("#00E676")); // Searing Emerald Success

        triggerExtendedFinishFeedback();
        recordSessionHistory();
        updateDisplayTelemetry();
    }

    private void triggerAlertFeedback() {
        if (switchVibrate.isChecked() && vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] pattern = {0, 200, 100, 200, 100, 400};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(500);
                }
            } catch (Exception ignored) {}
        }

        if (switchSound.isChecked()) {
            try {
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
                } else {
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    r.play();
                }
            } catch (Exception ignored) {}
        }
    }

    private void triggerExtendedFinishFeedback() {
        if (switchVibrate.isChecked() && vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] pattern = {0, 400, 200, 400, 200, 800};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(1000);
                }
            } catch (Exception ignored) {}
        }

        if (switchSound.isChecked()) {
            try {
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 800);
                }
            } catch (Exception ignored) {}
        }
    }

    private void updateDisplayTelemetry() {
        // Timer countdown MM:SS:ms or MM:SS
        long totalSecs = (remainingMillis + 999) / 1000;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        tvTimerDisplay.setText(String.format(Locale.US, "%02d:%02d", mins, secs));

        tvSideIndicator.setText("SIDE " + currentSide);
        tvFlipCounter.setText(completedFlips + " / " + currentTargetFlips + " FLIPS");

        if (totalIntervalMillis > 0) {
            int progress = (int) ((remainingMillis * 1000) / totalIntervalMillis);
            progressTimer.setProgress(Math.max(0, Math.min(1000, progress)));
        } else {
            progressTimer.setProgress(0);
        }
    }

    private void renderPresetButtons() {
        layoutPresetContainer.removeAllViews();

        // 1. Built-in presets
        for (final String[] preset : DEFAULT_PRESETS) {
            Button btn = createPresetBadgeButton(preset[0], Integer.parseInt(preset[1]), Integer.parseInt(preset[2]), preset[3]);
            layoutPresetContainer.addView(btn);
        }

        // 2. Custom stored presets from JSON
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String customJson = prefs.getString(PREF_CUSTOM_PRESETS, "[]");
        try {
            JSONArray array = new JSONArray(customJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String name = obj.optString("name", "Custom Preset");
                int sec = obj.optInt("seconds", 120);
                int flips = obj.optInt("flips", 2);
                String desc = obj.optString("desc", "User saved profile");
                Button btn = createPresetBadgeButton(name + " ★", sec, flips, desc);
                layoutPresetContainer.addView(btn);
            }
        } catch (JSONException ignored) {}
    }

    private Button createPresetBadgeButton(String title, int seconds, int flips, String desc) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(8, 0, 8, 0);
        btn.setLayoutParams(lp);
        btn.setText(title);
        btn.setTextSize(13);
        btn.setPadding(24, 12, 24, 12);
        btn.setBackgroundResource(android.R.drawable.btn_default);
        btn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            applyPreset(title, seconds, flips, desc);
        });
        return btn;
    }

    private void applyPreset(String title, int seconds, int flips, String desc) {
        if (isRunning) {
            Toast.makeText(this, "Reset current sear before changing preset", Toast.LENGTH_SHORT).show();
            return;
        }
        currentProteinTitle = title;
        currentPanDescription = desc;
        currentIntervalSec = seconds;
        currentTargetFlips = flips;
        completedFlips = 0;
        currentSide = 1;
        totalIntervalMillis = currentIntervalSec * 1000L;
        remainingMillis = totalIntervalMillis;

        seekDuration.setProgress(currentIntervalSec);
        seekFlips.setProgress(currentTargetFlips);
        tvDurationValue.setText(formatTime(currentIntervalSec));
        tvFlipsValue.setText(currentTargetFlips + (currentTargetFlips == 1 ? " Flip (2 Sides)" : " Flips"));
        tvPresetDescription.setText(title + " — " + desc);

        savePreferences();
        updateDisplayTelemetry();
        Toast.makeText(this, "Loaded: " + title, Toast.LENGTH_SHORT).show();
    }

    private void saveCustomPresetFromInput() {
        String name = etProteinName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a preset name", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String customJson = prefs.getString(PREF_CUSTOM_PRESETS, "[]");
        try {
            JSONArray array = new JSONArray(customJson);
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("seconds", currentIntervalSec);
            obj.put("flips", currentTargetFlips);
            obj.put("desc", "Custom: " + currentIntervalSec + "s per side • " + currentTargetFlips + " flips");
            array.put(obj);
            prefs.edit().putString(PREF_CUSTOM_PRESETS, array.toString()).apply();
            etProteinName.setText("");
            renderPresetButtons();
            Toast.makeText(this, "Saved preset: " + name, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Error saving preset", Toast.LENGTH_SHORT).show();
        }
    }

    private void recordSessionHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyJson = prefs.getString(PREF_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(historyJson);
            JSONObject entry = new JSONObject();
            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
            long totalElapsedSec = (System.currentTimeMillis() - sessionStartTime) / 1000;
            if (totalElapsedSec <= 0) totalElapsedSec = (long) currentIntervalSec * (completedFlips + 1);

            entry.put("timestamp", dateStr);
            entry.put("protein", currentProteinTitle);
            entry.put("durationSec", currentIntervalSec);
            entry.put("flipsCompleted", completedFlips);
            entry.put("totalTimeSec", totalElapsedSec);

            // Prepend new history record
            JSONArray updated = new JSONArray();
            updated.put(entry);
            for (int i = 0; i < Math.min(array.length(), 29); i++) {
                updated.put(array.getJSONObject(i));
            }

            prefs.edit().putString(PREF_HISTORY, updated.toString()).apply();
            renderHistoryList();
        } catch (JSONException ignored) {}
    }

    private void renderHistoryList() {
        layoutHistoryContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyJson = prefs.getString(PREF_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(historyJson);
            if (array.length() == 0) {
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText("No searing logs yet. Ignite the pan to start telemetry!");
                tvEmpty.setTextSize(13);
                tvEmpty.setTextColor(Color.GRAY);
                layoutHistoryContainer.addView(tvEmpty);
                return;
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                TextView tvItem = new TextView(this);
                String line = String.format(Locale.US,
                        "[%s] %s • %d flips • %ds/side (Total: %ds)",
                        obj.optString("timestamp", "--"),
                        obj.optString("protein", "Custom"),
                        obj.optInt("flipsCompleted", 0),
                        obj.optInt("durationSec", 0),
                        obj.optInt("totalTimeSec", 0)
                );
                tvItem.setText(line);
                tvItem.setTextSize(13);
                tvItem.setPadding(0, 8, 0, 8);
                layoutHistoryContainer.addView(tvItem);
            }
        } catch (JSONException ignored) {}
    }

    private void clearHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREF_HISTORY, "[]").apply();
        renderHistoryList();
        Toast.makeText(this, "Sear history cleared", Toast.LENGTH_SHORT).show();
    }

    private void exportFullJsonToShare() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        try {
            JSONObject bundle = new JSONObject();
            bundle.put("version", 1);
            bundle.put("exportTime", System.currentTimeMillis());
            bundle.put("presets", new JSONArray(prefs.getString(PREF_CUSTOM_PRESETS, "[]")));
            bundle.put("history", new JSONArray(prefs.getString(PREF_HISTORY, "[]")));
            bundle.put("settings_duration", currentIntervalSec);
            bundle.put("settings_flips", currentTargetFlips);

            String jsonString = bundle.toString(2);

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, jsonString);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "PanFlip Sentinel Backup");
            sendIntent.setType("application/json");

            Intent shareIntent = Intent.createChooser(sendIntent, "Export PanFlip Presets & History");
            startActivity(shareIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importJsonFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            CharSequence text = item.getText();
            if (text == null) {
                Toast.makeText(this, "Clipboard contains no readable text", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject bundle = new JSONObject(text.toString());
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();

            if (bundle.has("presets")) {
                editor.putString(PREF_CUSTOM_PRESETS, bundle.getJSONArray("presets").toString());
            }
            if (bundle.has("history")) {
                editor.putString(PREF_HISTORY, bundle.getJSONArray("history").toString());
            }
            editor.apply();

            renderPresetButtons();
            renderHistoryList();
            Toast.makeText(this, "Presets & history imported successfully!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: Invalid JSON in clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(PREF_SEAR_DURATION, currentIntervalSec);
        editor.putInt(PREF_TARGET_FLIPS, currentTargetFlips);
        editor.putBoolean(PREF_KEEP_SCREEN_ON, switchKeepScreenOn.isChecked());
        editor.putBoolean(PREF_SOUND_ENABLED, switchSound.isChecked());
        editor.putBoolean(PREF_VIBRATE_ENABLED, switchVibrate.isChecked());
        editor.apply();
    }

    private void restorePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentIntervalSec = prefs.getInt(PREF_SEAR_DURATION, 120);
        currentTargetFlips = prefs.getInt(PREF_TARGET_FLIPS, 2);

        seekDuration.setProgress(currentIntervalSec);
        tvDurationValue.setText(formatTime(currentIntervalSec));

        seekFlips.setProgress(currentTargetFlips);
        tvFlipsValue.setText(currentTargetFlips + (currentTargetFlips == 1 ? " Flip (2 Sides)" : " Flips"));

        boolean keepOn = prefs.getBoolean(PREF_KEEP_SCREEN_ON, true);
        switchKeepScreenOn.setChecked(keepOn);
        updateScreenWakeLock(keepOn);

        switchSound.setChecked(prefs.getBoolean(PREF_SOUND_ENABLED, true));
        switchVibrate.setChecked(prefs.getBoolean(PREF_VIBRATE_ENABLED, true));

        totalIntervalMillis = currentIntervalSec * 1000L;
        remainingMillis = totalIntervalMillis;
        tvPresetDescription.setText(currentProteinTitle + " — " + currentPanDescription);
    }

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format(Locale.US, "%d min %02d sec", m, s);
    }

    // Physical Keyboard Shortcuts for Desktop / Samsung DeX / Emulator
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_SPACE:
                    toggleStartPause();
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (isRunning) {
                        triggerManualFlip();
                    } else {
                        toggleStartPause();
                    }
                    return true;
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_EQUALS:
                case KeyEvent.KEYCODE_NUMPAD_ADD:
                    adjustRemainingSeconds(15);
                    return true;
                case KeyEvent.KEYCODE_MINUS:
                case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                    adjustRemainingSeconds(-15);
                    return true;
                case KeyEvent.KEYCODE_R:
                    resetTimerState();
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Window resizing in freeform/desktop preserves active countdown state
        updateDisplayTelemetry();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (toneGenerator != null) {
            try {
                toneGenerator.release();
            } catch (Exception ignored) {}
        }
    }
}