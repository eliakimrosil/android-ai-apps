package com.aistudio.laundrycyclesentinel;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
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
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private static final String PREFS_NAME = "laundry-cycle-sentinel_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_ACTIVE_TIMER_END = "pref_timer_end";
    private static final String KEY_IS_RUNNING = "pref_is_running";
    private static final String KEY_CYCLE_TYPE = "pref_cycle_type";
    private static final String KEY_WASHER_OR_DRYER = "pref_appliance_type";
    private static final String KEY_LOAD_LABEL = "pref_load_label";
    private static final String KEY_HISTORY = "pref_cycle_history";
    private static final String KEY_CUSTOM_MINUTES = "pref_custom_minutes";
    private static final String KEY_SOUND_ENABLED = "pref_sound_enabled";
    private static final String KEY_VIBRATE_ENABLED = "pref_vibrate_enabled";

    // Preset cycles in minutes: Washer & Dryer
    private static final int TIME_QUICK_WASH = 25;
    private static final int TIME_NORMAL_WASH = 45;
    private static final int TIME_HEAVY_DUTY = 65;
    private static final int TIME_DELICATES = 35;
    private static final int TIME_BEDDING = 75;
    private static final int TIME_DRY_QUICK = 30;
    private static final int TIME_DRY_NORMAL = 50;
    private static final int TIME_DRY_BEDDING = 70;

    // UI elements
    private Button btnThemeToggle;
    private TextView tvStatusBadge;
    private TextView tvCountdownTimer;
    private TextView tvCycleDescription;
    private TextView tvApplianceIndicator;
    private Button btnToggleAppliance;
    private EditText etLoadNotes;
    private SeekBar sbDurationAdjuster;
    private TextView tvDurationValue;

    // Preset Buttons
    private Button btnPresetQuick;
    private Button btnPresetNormal;
    private Button btnPresetHeavy;
    private Button btnPresetDelicates;
    private Button btnPresetBedding;

    // Controls
    private Button btnStartPauseTimer;
    private Button btnResetTimer;
    private Button btnFinishNow;
    private Switch swSoundAlarm;
    private Switch swVibrateAlarm;

    // History and Export
    private Button btnExportHistory;
    private Button btnClearHistory;
    private LinearLayout llHistoryContainer;

    // State Variables
    private SharedPreferences prefs;
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private long totalDurationMillis = TIME_NORMAL_WASH * 60 * 1000L;
    private long remainingMillis = TIME_NORMAL_WASH * 60 * 1000L;
    private String currentAppliance = "WASHER"; // WASHER or DRYER
    private String currentCycleName = "Normal Cotton";
    private ToneGenerator toneGenerator;
    private Ringtone activeRingtone;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = sp.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark
        Configuration config = new Configuration(newBase.getResources().getConfiguration());

        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNightMode = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (systemNightMode == 2) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            } else {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and show over lockscreen for critical laundry finish sentinel alerts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupToneGenerator();
        setupThemeBar();
        restoreSavedState();
        renderHistory();
        setupListeners();
    }

    private void setupToneGenerator() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
        } catch (Exception ignored) {
        }
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvCountdownTimer = findViewById(R.id.tvCountdownTimer);
        tvCycleDescription = findViewById(R.id.tvCycleDescription);
        tvApplianceIndicator = findViewById(R.id.tvApplianceIndicator);
        btnToggleAppliance = findViewById(R.id.btnToggleAppliance);
        etLoadNotes = findViewById(R.id.etLoadNotes);
        sbDurationAdjuster = findViewById(R.id.sbDurationAdjuster);
        tvDurationValue = findViewById(R.id.tvDurationValue);

        btnPresetQuick = findViewById(R.id.btnPresetQuick);
        btnPresetNormal = findViewById(R.id.btnPresetNormal);
        btnPresetHeavy = findViewById(R.id.btnPresetHeavy);
        btnPresetDelicates = findViewById(R.id.btnPresetDelicates);
        btnPresetBedding = findViewById(R.id.btnPresetBedding);

        btnStartPauseTimer = findViewById(R.id.btnStartPauseTimer);
        btnResetTimer = findViewById(R.id.btnResetTimer);
        btnFinishNow = findViewById(R.id.btnFinishNow);
        swSoundAlarm = findViewById(R.id.swSoundAlarm);
        swVibrateAlarm = findViewById(R.id.swVibrateAlarm);

        btnExportHistory = findViewById(R.id.btnExportHistory);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        llHistoryContainer = findViewById(R.id.llHistoryContainer);
    }

    private void setupThemeBar() {
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        String label;
        if (themeMode == 1) {
            label = "Light";
        } else if (themeMode == 2) {
            label = "Dark";
        } else {
            label = "Auto";
        }
        btnThemeToggle.setText("Theme: " + label);

        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (!isNight) {
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
        }
    }

    private void cycleThemeMode() {
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void restoreSavedState() {
        currentAppliance = prefs.getString(KEY_WASHER_OR_DRYER, "WASHER");
        currentCycleName = prefs.getString(KEY_CYCLE_TYPE, "Normal Cotton");
        etLoadNotes.setText(prefs.getString(KEY_LOAD_LABEL, ""));
        swSoundAlarm.setChecked(prefs.getBoolean(KEY_SOUND_ENABLED, true));
        swVibrateAlarm.setChecked(prefs.getBoolean(KEY_VIBRATE_ENABLED, true));

        int savedMinutes = prefs.getInt(KEY_CUSTOM_MINUTES, TIME_NORMAL_WASH);
        sbDurationAdjuster.setProgress(Math.max(5, Math.min(150, savedMinutes)));
        updateDurationDisplay(sbDurationAdjuster.getProgress());

        updateApplianceUi();

        boolean wasRunning = prefs.getBoolean(KEY_IS_RUNNING, false);
        long targetEnd = prefs.getLong(KEY_ACTIVE_TIMER_END, 0);
        long now = System.currentTimeMillis();

        if (wasRunning && targetEnd > now) {
            remainingMillis = targetEnd - now;
            startCountdown(remainingMillis, true);
        } else if (wasRunning && targetEnd <= now && targetEnd > 0) {
            handleCycleFinished();
        } else {
            remainingMillis = sbDurationAdjuster.getProgress() * 60 * 1000L;
            updateCountdownText(remainingMillis);
            updateStatusBadge(false, false);
        }
    }

    private void updateApplianceUi() {
        if ("DRYER".equalsIgnoreCase(currentAppliance)) {
            tvApplianceIndicator.setText("DRYER MODE");
            tvApplianceIndicator.setBackgroundColor(Color.parseColor("#B3541E")); // Warm Terracotta for Heat
            btnToggleAppliance.setText("Switch to Washer");
            btnPresetQuick.setText("Express Dry (30m)");
            btnPresetNormal.setText("Normal Dry (50m)");
            btnPresetHeavy.setText("Heavy Dry (70m)");
            btnPresetDelicates.setText("Gentle Dry (35m)");
            btnPresetBedding.setText("Bulky Dry (75m)");
        } else {
            tvApplianceIndicator.setText("WASHER MODE");
            tvApplianceIndicator.setBackgroundColor(Color.parseColor("#0F766E")); // Teal Cyan for Water
            btnToggleAppliance.setText("Switch to Dryer");
            btnPresetQuick.setText("Quick Wash (25m)");
            btnPresetNormal.setText("Normal Cotton (45m)");
            btnPresetHeavy.setText("Heavy Duty (65m)");
            btnPresetDelicates.setText("Delicates (35m)");
            btnPresetBedding.setText("Bedding/Bulky (75m)");
        }
        tvCycleDescription.setText(currentCycleName + " | " + currentAppliance);
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnToggleAppliance.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (isTimerRunning) {
                Toast.makeText(this, "Pause or Reset the current cycle first", Toast.LENGTH_SHORT).show();
                return;
            }
            if ("WASHER".equals(currentAppliance)) {
                currentAppliance = "DRYER";
                currentCycleName = "Normal Dry";
                setPresetTime(TIME_DRY_NORMAL);
            } else {
                currentAppliance = "WASHER";
                currentCycleName = "Normal Cotton";
                setPresetTime(TIME_NORMAL_WASH);
            }
            prefs.edit().putString(KEY_WASHER_OR_DRYER, currentAppliance).putString(KEY_CYCLE_TYPE, currentCycleName).apply();
            updateApplianceUi();
        });

        sbDurationAdjuster.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int safeVal = Math.max(5, progress);
                    updateDurationDisplay(safeVal);
                    if (!isTimerRunning) {
                        remainingMillis = safeVal * 60 * 1000L;
                        totalDurationMillis = remainingMillis;
                        updateCountdownText(remainingMillis);
                        prefs.edit().putInt(KEY_CUSTOM_MINUTES, safeVal).apply();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnPresetQuick.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if ("DRYER".equals(currentAppliance)) {
                currentCycleName = "Express Dry";
                setPresetTime(TIME_DRY_QUICK);
            } else {
                currentCycleName = "Quick Wash";
                setPresetTime(TIME_QUICK_WASH);
            }
        });

        btnPresetNormal.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if ("DRYER".equals(currentAppliance)) {
                currentCycleName = "Normal Dry";
                setPresetTime(TIME_DRY_NORMAL);
            } else {
                currentCycleName = "Normal Cotton";
                setPresetTime(TIME_NORMAL_WASH);
            }
        });

        btnPresetHeavy.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if ("DRYER".equals(currentAppliance)) {
                currentCycleName = "Heavy Dry";
                setPresetTime(TIME_DRY_BEDDING);
            } else {
                currentCycleName = "Heavy Duty";
                setPresetTime(TIME_HEAVY_DUTY);
            }
        });

        btnPresetDelicates.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentCycleName = "Delicates / Gentle";
            setPresetTime(TIME_DELICATES);
        });

        btnPresetBedding.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentCycleName = "Bedding / Bulky";
            setPresetTime(TIME_BEDDING);
        });

        btnStartPauseTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleTimerState();
        });

        btnResetTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetCurrentCycle();
        });

        btnFinishNow.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            forceFinishCycle();
        });

        swSoundAlarm.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, isChecked).apply();
        });

        swVibrateAlarm.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_VIBRATE_ENABLED, isChecked).apply();
        });

        btnExportHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportHistoryText();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearHistory();
        });
    }

    private void setPresetTime(int minutes) {
        if (isTimerRunning) {
            Toast.makeText(this, "Pause or Reset to apply preset", Toast.LENGTH_SHORT).show();
            return;
        }
        sbDurationAdjuster.setProgress(minutes);
        updateDurationDisplay(minutes);
        remainingMillis = minutes * 60 * 1000L;
        totalDurationMillis = remainingMillis;
        updateCountdownText(remainingMillis);
        tvCycleDescription.setText(currentCycleName + " | " + currentAppliance);
        prefs.edit().putInt(KEY_CUSTOM_MINUTES, minutes)
                .putString(KEY_CYCLE_TYPE, currentCycleName)
                .apply();
    }

    private void updateDurationDisplay(int minutes) {
        tvDurationValue.setText(minutes + " Minutes");
    }

    private void toggleTimerState() {
        if (isTimerRunning) {
            // Pause
            pauseTimer();
        } else {
            // Start
            if (remainingMillis <= 0) {
                remainingMillis = sbDurationAdjuster.getProgress() * 60 * 1000L;
            }
            startCountdown(remainingMillis, false);
        }
    }

    private void startCountdown(long millis, boolean isRestored) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        stopAlarmNotification();
        isTimerRunning = true;
        btnStartPauseTimer.setText("Pause Sentinel");
        updateStatusBadge(true, false);

        long targetEndTime = System.currentTimeMillis() + millis;
        prefs.edit()
                .putBoolean(KEY_IS_RUNNING, true)
                .putLong(KEY_ACTIVE_TIMER_END, targetEndTime)
                .putString(KEY_LOAD_LABEL, etLoadNotes.getText().toString().trim())
                .apply();

        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                updateCountdownText(remainingMillis);
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                updateCountdownText(0);
                handleCycleFinished();
            }
        }.start();

        if (!isRestored && toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        }
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        btnStartPauseTimer.setText("Resume Sentinel");
        updateStatusBadge(false, false);
        prefs.edit()
                .putBoolean(KEY_IS_RUNNING, false)
                .putLong(KEY_ACTIVE_TIMER_END, 0)
                .apply();

        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 150);
        }
    }

    private void resetCurrentCycle() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        stopAlarmNotification();
        isTimerRunning = false;
        btnStartPauseTimer.setText("Start Sentinel");
        remainingMillis = sbDurationAdjuster.getProgress() * 60 * 1000L;
        totalDurationMillis = remainingMillis;
        updateCountdownText(remainingMillis);
        updateStatusBadge(false, false);

        prefs.edit()
                .putBoolean(KEY_IS_RUNNING, false)
                .putLong(KEY_ACTIVE_TIMER_END, 0)
                .apply();
    }

    private void forceFinishCycle() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        remainingMillis = 0;
        updateCountdownText(0);
        handleCycleFinished();
    }

    private void handleCycleFinished() {
        isTimerRunning = false;
        btnStartPauseTimer.setText("Start Sentinel");
        updateStatusBadge(false, true);

        prefs.edit()
                .putBoolean(KEY_IS_RUNNING, false)
                .putLong(KEY_ACTIVE_TIMER_END, 0)
                .apply();

        // Trigger Audio Alarm
        triggerAlarmNotification();

        // Save Cycle to History
        saveHistoryEntry();
        renderHistory();
    }

    private void updateStatusBadge(boolean active, boolean finished) {
        if (finished) {
            tvStatusBadge.setText("CYCLE COMPLETE - UNLOAD NOW");
            tvStatusBadge.setBackgroundColor(Color.parseColor("#B91C1C")); // Alarm Red
            tvStatusBadge.setTextColor(Color.WHITE);
        } else if (active) {
            tvStatusBadge.setText("SENTINEL ACTIVE - RUNNING");
            tvStatusBadge.setBackgroundColor(Color.parseColor("#15803D")); // Operational Green
            tvStatusBadge.setTextColor(Color.WHITE);
        } else {
            tvStatusBadge.setText("SENTINEL STANDBY - IDLE");
            tvStatusBadge.setBackgroundColor(Color.parseColor("#374151")); // Neutral Gray
            tvStatusBadge.setTextColor(Color.WHITE);
        }
    }

    private void updateCountdownText(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        tvCountdownTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void triggerAlarmNotification() {
        if (swSoundAlarm.isChecked()) {
            try {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                activeRingtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
                if (activeRingtone != null) {
                    activeRingtone.play();
                }
            } catch (Exception ignored) {
            }
        }

        if (swVibrateAlarm.isChecked()) {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 600, 300, 600, 300, 1000};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1),
                            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        }
    }

    private void stopAlarmNotification() {
        if (activeRingtone != null && activeRingtone.isPlaying()) {
            activeRingtone.stop();
        }
    }

    private void saveHistoryEntry() {
        String historyRaw = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(historyRaw);
            JSONObject item = new JSONObject();
            String dateFormatted = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            String note = etLoadNotes.getText().toString().trim();
            if (note.isEmpty()) {
                note = "Standard Load";
            }
            item.put("timestamp", dateFormatted);
            item.put("appliance", currentAppliance);
            item.put("cycle", currentCycleName);
            item.put("duration", sbDurationAdjuster.getProgress() + "m");
            item.put("note", note);

            // Keep top 20
            JSONArray newArr = new JSONArray();
            newArr.put(item);
            for (int i = 0; i < Math.min(19, arr.length()); i++) {
                newArr.put(arr.getJSONObject(i));
            }
            prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private void renderHistory() {
        llHistoryContainer.removeAllViews();
        String historyRaw = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(historyRaw);
            if (arr.length() == 0) {
                TextView emptyTv = new TextView(this);
                emptyTv.setText("No previous cycles recorded. Run a cycle to build your log.");
                emptyTv.setPadding(16, 24, 16, 24);
                llHistoryContainer.addView(emptyTv);
                return;
            }

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                View card = createHistoryRow(obj);
                llHistoryContainer.addView(card);
            }
        } catch (JSONException e) {
            TextView errorTv = new TextView(this);
            errorTv.setText("History format error. Reset logs.");
            llHistoryContainer.addView(errorTv);
        }
    }

    private View createHistoryRow(JSONObject obj) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(24, 20, 24, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8);
        row.setLayoutParams(params);
        row.setBackgroundColor(Color.parseColor("#1F2937")); // Slate Card Container

        TextView title = new TextView(this);
        String appliance = obj.optString("appliance", "WASHER");
        String cycle = obj.optString("cycle", "Standard");
        String duration = obj.optString("duration", "45m");
        title.setText(appliance + " • " + cycle + " (" + duration + ")");
        title.setTextSize(15);
        title.setTextColor(Color.parseColor("#F9FAFB"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView subtitle = new TextView(this);
        String note = obj.optString("note", "");
        String timestamp = obj.optString("timestamp", "");
        subtitle.setText("Finished: " + timestamp + " | Note: " + note);
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#9CA3AF"));

        row.addView(title);
        row.addView(subtitle);
        return row;
    }

    private void clearHistory() {
        prefs.edit().putString(KEY_HISTORY, "[]").apply();
        renderHistory();
        Toast.makeText(this, "Cycle history cleared", Toast.LENGTH_SHORT).show();
    }

    private void exportHistoryText() {
        String historyRaw = prefs.getString(KEY_HISTORY, "[]");
        StringBuilder sb = new StringBuilder();
        sb.append("=== CycleGuard: Laundry Sentinel Report ===\n");
        try {
            JSONArray arr = new JSONArray(historyRaw);
            if (arr.length() == 0) {
                Toast.makeText(this, "No history to export", Toast.LENGTH_SHORT).show();
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                sb.append(obj.optString("timestamp")).append(" | ")
                        .append(obj.optString("appliance")).append(" | ")
                        .append(obj.optString("cycle")).append(" (")
                        .append(obj.optString("duration")).append(") - Note: ")
                        .append(obj.optString("note")).append("\n");
            }
        } catch (JSONException e) {
            sb.append("Error parsing cycles.\n");
        }

        String exported = sb.toString();

        // Clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Laundry Cycles", exported);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }

        // Share Intent
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, exported);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Laundry Log");
        startActivity(shareIntent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                toggleTimerState();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (isTimerRunning) {
                    forceFinishCycle();
                } else {
                    toggleTimerState();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS || keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET) {
                adjustDurationByMinutes(5);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS || keyCode == KeyEvent.KEYCODE_LEFT_BRACKET) {
                adjustDurationByMinutes(-5);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void adjustDurationByMinutes(int delta) {
        int currentProgress = sbDurationAdjuster.getProgress();
        int newProgress = Math.max(5, Math.min(150, currentProgress + delta));
        sbDurationAdjuster.setProgress(newProgress);
        updateDurationDisplay(newProgress);
        if (!isTimerRunning) {
            remainingMillis = newProgress * 60 * 1000L;
            totalDurationMillis = remainingMillis;
            updateCountdownText(remainingMillis);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Retain view configuration without refreshing activity
        setupThemeBar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Persist typed notes and state
        prefs.edit().putString(KEY_LOAD_LABEL, etLoadNotes.getText().toString().trim()).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarmNotification();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}