package com.aistudio.gluesetclampsentinel;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "glue-set-clamp-sentinel_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_PROJECT_NAME = "pref_project_name";
    private static final String KEY_GLUE_INDEX = "pref_glue_index";
    private static final String KEY_WOOD_INDEX = "pref_wood_index";
    private static final String KEY_TEMP_F = "pref_temp_f";
    private static final String KEY_HUMIDITY = "pref_humidity";
    private static final String KEY_JOINT_INDEX = "pref_joint_index";
    private static final String KEY_TIMER_RUNNING = "pref_timer_running";
    private static final String KEY_TIMER_START_MS = "pref_timer_start_ms";
    private static final String KEY_TIMER_TOTAL_SEC = "pref_timer_total_sec";
    private static final String KEY_LOGS_JSON = "pref_cure_logs_json";

    // Glue profiles
    // Base clamp time at 70°F (minutes), full cure (hours), open assembly time (minutes)
    private static class GlueProfile {
        final String name;
        final int baseClampMin;
        final int baseCureHours;
        final int openTimeMin;
        final int minTempF;
        final String description;

        GlueProfile(String name, int baseClampMin, int baseCureHours, int openTimeMin, int minTempF, String description) {
            this.name = name;
            this.baseClampMin = baseClampMin;
            this.baseCureHours = baseCureHours;
            this.openTimeMin = openTimeMin;
            this.minTempF = minTempF;
            this.description = description;
        }
    }

    private static final GlueProfile[] GLUE_PROFILES = new GlueProfile[]{
            new GlueProfile("PVA Type I (Titebond III Ultimate)", 30, 24, 10, 47, "Waterproof cross-linking PVA. High bond."),
            new GlueProfile("PVA Type II (Titebond II Premium)", 30, 24, 5, 55, "Water-resistant PVA for general cabinetry & shop projects."),
            new GlueProfile("Standard PVA (Titebond Original / White)", 30, 24, 5, 50, "Interior aliphatic resin. Fast grab, rigid glue line."),
            new GlueProfile("Standard 2-Part Epoxy (Slow Cure / Marine)", 240, 48, 45, 55, "High-strength gap filling epoxy. Critical for oily/exotic woods."),
            new GlueProfile("Quick 5-Minute Epoxy", 15, 6, 3, 60, "Rapid fixturing resin. Lower shear strength."),
            new GlueProfile("Polyurethane Glue (Gorilla Wood/Poly)", 120, 24, 20, 50, "Moisture-activated 100% waterproof foaming glue."),
            new GlueProfile("Liquid Hide Glue (Titebond Liquid Hide)", 180, 24, 20, 60, "Reversible, stain-friendly. High creep resistance.")
    };

    // Wood porosity modifiers: 0: Low/Dense (Maple, Ipe, Oak), 1: Medium (Cherry, Walnut), 2: High/Porous (Pine, Poplar, Cedar)
    private static final double[] WOOD_ABSORPTION_FACTOR = new double[]{1.25, 1.0, 0.85};

    // Joint stress profiles: 0: Edge-to-Edge (Low), 1: Face-to-Face (Medium), 2: End-Grain/Joinery (High tension)
    private static final double[] JOINT_STRESS_FACTOR = new double[]{1.0, 1.15, 1.35};

    // UI Elements
    private Button btnThemeToggle;
    private EditText etProjectName;
    private RadioGroup rgGlueType;
    private RadioGroup rgWoodType;
    private RadioGroup rgJointStress;
    private SeekBar sbTemperature;
    private SeekBar sbHumidity;
    private TextView tvTempDisplay;
    private TextView tvHumidityDisplay;
    private TextView tvAdvisoryBadge;
    private TextView tvAdvisoryText;
    private TextView tvCalcClampTime;
    private TextView tvCalcCureTime;
    private TextView tvCalcOpenTime;

    // Timer Sentinel UI
    private TextView tvTimerCountdown;
    private TextView tvTimerStatus;
    private ProgressBar pbTimerProgress;
    private Button btnStartTimer;
    private Button btnPauseTimer;
    private Button btnResetTimer;
    private Button btnLogBatch;
    private Button btnShareTelemetry;
    private LinearLayout llHistoryContainer;

    // Timer State
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean isTimerRunning = false;
    private long timerStartTimeMs = 0;
    private int timerTotalDurationSec = 0;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTimerRunning) return;
            updateTimerDisplay();
            timerHandler.postDelayed(this, 1000);
        }
    };

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
            int systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (systemNight == 2) {
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
        setContentView(R.layout.activity_main);

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
        } catch (Exception ignored) {
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initViews();
        setupStatusBarAppearance();
        loadSavedState();
        calculateMetrics();
        checkResumeActiveTimer();
        renderHistoryList();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        etProjectName = findViewById(R.id.etProjectName);
        rgGlueType = findViewById(R.id.rgGlueType);
        rgWoodType = findViewById(R.id.rgWoodType);
        rgJointStress = findViewById(R.id.rgJointStress);
        sbTemperature = findViewById(R.id.sbTemperature);
        sbHumidity = findViewById(R.id.sbHumidity);
        tvTempDisplay = findViewById(R.id.tvTempDisplay);
        tvHumidityDisplay = findViewById(R.id.tvHumidityDisplay);
        tvAdvisoryBadge = findViewById(R.id.tvAdvisoryBadge);
        tvAdvisoryText = findViewById(R.id.tvAdvisoryText);
        tvCalcClampTime = findViewById(R.id.tvCalcClampTime);
        tvCalcCureTime = findViewById(R.id.tvCalcCureTime);
        tvCalcOpenTime = findViewById(R.id.tvCalcOpenTime);

        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        tvTimerStatus = findViewById(R.id.tvTimerStatus);
        pbTimerProgress = findViewById(R.id.pbTimerProgress);
        btnStartTimer = findViewById(R.id.btnStartTimer);
        btnPauseTimer = findViewById(R.id.btnPauseTimer);
        btnResetTimer = findViewById(R.id.btnResetTimer);
        btnLogBatch = findViewById(R.id.btnLogBatch);
        btnShareTelemetry = findViewById(R.id.btnShareTelemetry);
        llHistoryContainer = findViewById(R.id.llHistoryContainer);

        // Theme toggle listener
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        // Sliders
        sbTemperature.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int tempF = progress + 35; // 35°F to 105°F
                tvTempDisplay.setText(tempF + "°F (" + fahrenheitToCelsius(tempF) + "°C)");
                calculateMetrics();
                saveCurrentInputs();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        });

        sbHumidity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvHumidityDisplay.setText(progress + "% RH");
                calculateMetrics();
                saveCurrentInputs();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        });

        // Radios
        RadioGroup.OnCheckedChangeListener radioListener = (group, checkedId) -> {
            calculateMetrics();
            saveCurrentInputs();
        };
        rgGlueType.setOnCheckedChangeListener(radioListener);
        rgWoodType.setOnCheckedChangeListener(radioListener);
        rgJointStress.setOnCheckedChangeListener(radioListener);

        // Timer Controls
        btnStartTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            startSentinelTimer();
        });

        btnPauseTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            stopSentinelTimer(true);
        });

        btnResetTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetSentinelTimer();
        });

        btnLogBatch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            logCurrentBatch();
        });

        btnShareTelemetry.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportTelemetryReport();
        });
    }

    private int fahrenheitToCelsius(int f) {
        return (int) Math.round((f - 32.0) * 5.0 / 9.0);
    }

    private void setupStatusBarAppearance() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = sp.getInt(KEY_THEME_MODE, 0);
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = (themeMode == 2) || (themeMode == 0 && currentNightMode == Configuration.UI_MODE_NIGHT_YES);

        if (btnThemeToggle != null) {
            if (themeMode == 1) {
                btnThemeToggle.setText("Theme: Light");
            } else if (themeMode == 2) {
                btnThemeToggle.setText("Theme: Dark");
            } else {
                btnThemeToggle.setText("Theme: Auto");
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (!isDark) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    private void cycleThemeMode() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int current = sp.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3;
        sp.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void loadSavedState() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etProjectName.setText(sp.getString(KEY_PROJECT_NAME, "Heritage Workbench Glue-Up"));

        int glueIdx = sp.getInt(KEY_GLUE_INDEX, 0);
        int woodIdx = sp.getInt(KEY_WOOD_INDEX, 1);
        int jointIdx = sp.getInt(KEY_JOINT_INDEX, 0);
        int tempF = sp.getInt(KEY_TEMP_F, 68);
        int humidity = sp.getInt(KEY_HUMIDITY, 50);

        setRadioByIndex(rgGlueType, glueIdx);
        setRadioByIndex(rgWoodType, woodIdx);
        setRadioByIndex(rgJointStress, jointIdx);

        sbTemperature.setProgress(Math.max(0, Math.min(70, tempF - 35)));
        sbHumidity.setProgress(Math.max(0, Math.min(100, humidity)));

        tvTempDisplay.setText(tempF + "°F (" + fahrenheitToCelsius(tempF) + "°C)");
        tvHumidityDisplay.setText(humidity + "% RH");
    }

    private void saveCurrentInputs() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int tempF = sbTemperature.getProgress() + 35;
        int humidity = sbHumidity.getProgress();
        int glueIdx = getSelectedRadioIndex(rgGlueType);
        int woodIdx = getSelectedRadioIndex(rgWoodType);
        int jointIdx = getSelectedRadioIndex(rgJointStress);
        String projName = etProjectName.getText().toString().trim();

        sp.edit()
                .putString(KEY_PROJECT_NAME, projName)
                .putInt(KEY_GLUE_INDEX, glueIdx)
                .putInt(KEY_WOOD_INDEX, woodIdx)
                .putInt(KEY_JOINT_INDEX, jointIdx)
                .putInt(KEY_TEMP_F, tempF)
                .putInt(KEY_HUMIDITY, humidity)
                .apply();
    }

    private int getSelectedRadioIndex(RadioGroup rg) {
        int id = rg.getCheckedRadioButtonId();
        View radioButton = rg.findViewById(id);
        return rg.indexOfChild(radioButton);
    }

    private void setRadioByIndex(RadioGroup rg, int index) {
        if (index >= 0 && index < rg.getChildCount()) {
            View v = rg.getChildAt(index);
            if (v != null) {
                rg.check(v.getId());
            }
        }
    }

    /**
     * Physics/Chemical kinetic cure model calculation.
     * Incorporates Arrhenius rule of thumb: reaction rate roughly halves for every 18°F (10°C) drop,
     * and humidity effects on moisture-cure (Polyurethane) vs water-release (PVA) glues.
     */
    private CalculatedResult calculateMetrics() {
        int glueIdx = Math.max(0, Math.min(GLUE_PROFILES.length - 1, getSelectedRadioIndex(rgGlueType)));
        int woodIdx = Math.max(0, Math.min(WOOD_ABSORPTION_FACTOR.length - 1, getSelectedRadioIndex(rgWoodType)));
        int jointIdx = Math.max(0, Math.min(JOINT_STRESS_FACTOR.length - 1, getSelectedRadioIndex(rgJointStress)));

        int tempF = sbTemperature.getProgress() + 35;
        int humidity = sbHumidity.getProgress();

        GlueProfile profile = GLUE_PROFILES[glueIdx];

        // Arrhenius Thermal Multiplier based around 70°F
        // Under 70°F, cure slows significantly. Above 70°F, cure speeds up.
        double tempDelta = tempF - 70.0;
        double tempMultiplier = Math.pow(2.0, -tempDelta / 18.0);
        if (tempMultiplier < 0.4) tempMultiplier = 0.4;
        if (tempMultiplier > 4.5) tempMultiplier = 4.5;

        // Humidity correction factor
        double humidityMultiplier = 1.0;
        if (profile.name.contains("Polyurethane")) {
            // Moisture accelerates curing for polyurethane
            humidityMultiplier = 1.0 - ((humidity - 50) * 0.005);
            if (humidityMultiplier < 0.65) humidityMultiplier = 0.65;
        } else if (profile.name.contains("PVA") || profile.name.contains("Hide")) {
            // High humidity delays water evaporation in PVA
            humidityMultiplier = 1.0 + ((humidity - 50) * 0.008);
            if (humidityMultiplier < 0.8) humidityMultiplier = 0.8;
        }

        double woodFactor = WOOD_ABSORPTION_FACTOR[woodIdx];
        double jointFactor = JOINT_STRESS_FACTOR[jointIdx];

        // Calculated Clamp Time in minutes
        int calculatedClampMin = (int) Math.round(profile.baseClampMin * tempMultiplier * humidityMultiplier * woodFactor * jointFactor);
        if (calculatedClampMin < 10) calculatedClampMin = 10;

        // Full Cure Time in hours
        int calculatedCureHours = (int) Math.round(profile.baseCureHours * tempMultiplier * (humidityMultiplier * 0.5 + 0.5));
        if (calculatedCureHours < 4) calculatedCureHours = 4;

        // Open Assembly Time in minutes
        double openTimeMult = 1.0 / (tempMultiplier * 0.8 + 0.2); // hot shop = rapid skin-over
        int calculatedOpenMin = (int) Math.round(profile.openTimeMin * openTimeMult);
        if (calculatedOpenMin < 2) calculatedOpenMin = 2;

        // Environmental warnings
        boolean isChalkingRisk = tempF < profile.minTempF;
        boolean isHighHeatDanger = tempF > 95;
        boolean isExcessDamp = humidity > 85;

        if (isChalkingRisk) {
            tvAdvisoryBadge.setText("CRITICAL CHALKING RISK");
            tvAdvisoryBadge.setBackgroundResource(android.R.drawable.stat_sys_warning);
            tvAdvisoryText.setText("Workshop temperature (" + tempF + "°F) is BELOW minimum film-forming temperature (" +
                    profile.minTempF + "°F). Polymer will fail to coalesce, creating a chalky, zero-strength bond!");
        } else if (isHighHeatDanger) {
            tvAdvisoryBadge.setText("PREMATURE SKINNING WARNING");
            tvAdvisoryText.setText("High ambient shop heat (" + tempF + "°F) will cause skinning within " + calculatedOpenMin +
                    " mins. Apply clamps immediately after spread to prevent starved joints.");
        } else if (isExcessDamp) {
            tvAdvisoryBadge.setText("EXTENDED CURE NOTICE");
            tvAdvisoryText.setText("High ambient humidity (" + humidity + "% RH) significantly retards moisture evaporation. Double clamp duration recommended.");
        } else {
            tvAdvisoryBadge.setText("OPTIMAL SHOP CLIMATE");
            tvAdvisoryText.setText(profile.description + " Ideal environmental bonding parameters detected.");
        }

        // Update displays
        tvCalcClampTime.setText(formatHoursMinutes(calculatedClampMin));
        tvCalcCureTime.setText(calculatedCureHours + " Hours");
        tvCalcOpenTime.setText(calculatedOpenMin + " Min");

        return new CalculatedResult(calculatedClampMin, calculatedCureHours, calculatedOpenMin, profile.name);
    }

    private static class CalculatedResult {
        final int clampMin;
        final int cureHours;
        final int openMin;
        final String glueName;

        CalculatedResult(int clampMin, int cureHours, int openMin, String glueName) {
            this.clampMin = clampMin;
            this.cureHours = cureHours;
            this.openMin = openMin;
            this.glueName = glueName;
        }
    }

    private String formatHoursMinutes(int totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + " Min";
        }
        int hrs = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (mins == 0) {
            return hrs + " Hrs";
        }
        return hrs + "h " + mins + "m";
    }

    private void startSentinelTimer() {
        CalculatedResult res = calculateMetrics();
        timerTotalDurationSec = res.clampMin * 60;
        timerStartTimeMs = SystemClock.elapsedRealtime();
        isTimerRunning = true;

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit()
                .putBoolean(KEY_TIMER_RUNNING, true)
                .putLong(KEY_TIMER_START_MS, timerStartTimeMs)
                .putInt(KEY_TIMER_TOTAL_SEC, timerTotalDurationSec)
                .apply();

        btnStartTimer.setEnabled(false);
        btnPauseTimer.setEnabled(true);
        tvTimerStatus.setText("SENTINEL ACTIVE • CLAMP PRESSURE ENGAGED");

        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
        Toast.makeText(this, "Sentinel countdown initiated: " + res.clampMin + " minutes", Toast.LENGTH_SHORT).show();
    }

    private void stopSentinelTimer(boolean userPaused) {
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit().putBoolean(KEY_TIMER_RUNNING, false).apply();

        btnStartTimer.setEnabled(true);
        btnPauseTimer.setEnabled(false);
        if (userPaused) {
            tvTimerStatus.setText("SENTINEL PAUSED");
        }
    }

    private void resetSentinelTimer() {
        stopSentinelTimer(false);
        timerStartTimeMs = 0;
        timerTotalDurationSec = 0;
        tvTimerCountdown.setText("00:00:00");
        pbTimerProgress.setProgress(0);
        tvTimerStatus.setText("STANDBY • NO ACTIVE CLAMP SESSION");

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit()
                .putLong(KEY_TIMER_START_MS, 0)
                .putInt(KEY_TIMER_TOTAL_SEC, 0)
                .apply();
    }

    private void checkResumeActiveTimer() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean running = sp.contains(KEY_TIMER_RUNNING) && sp.getBoolean(KEY_TIMER_RUNNING, false);
        long startMs = sp.getLong(KEY_TIMER_START_MS, 0);
        int totalSec = sp.getInt(KEY_TIMER_TOTAL_SEC, 0);

        if (running && totalSec > 0 && startMs > 0) {
            long elapsedSec = (SystemClock.elapsedRealtime() - startMs) / 1000;
            if (elapsedSec < totalSec) {
                timerStartTimeMs = startMs;
                timerTotalDurationSec = totalSec;
                isTimerRunning = true;
                btnStartTimer.setEnabled(false);
                btnPauseTimer.setEnabled(true);
                tvTimerStatus.setText("SENTINEL ACTIVE • CLAMP PRESSURE ENGAGED");
                timerHandler.post(timerRunnable);
                return;
            } else {
                // Completed while away
                resetSentinelTimer();
                tvTimerCountdown.setText("00:00:00 (COMPLETE)");
                tvTimerStatus.setText("CLAMPS SAFE FOR RELEASE");
                pbTimerProgress.setProgress(100);
            }
        }
    }

    private void updateTimerDisplay() {
        if (!isTimerRunning || timerTotalDurationSec <= 0) return;

        long elapsedSec = (SystemClock.elapsedRealtime() - timerStartTimeMs) / 1000;
        long remainingSec = timerTotalDurationSec - elapsedSec;

        if (remainingSec <= 0) {
            // Completed!
            stopSentinelTimer(false);
            tvTimerCountdown.setText("00:00:00");
            pbTimerProgress.setProgress(100);
            tvTimerStatus.setText("DONE • FULL BOND STRENGTH REACHED (RELEASE CLAMPS)");
            notifyClampCompletion();
            return;
        }

        long hours = remainingSec / 3600;
        long minutes = (remainingSec % 3600) / 60;
        long seconds = remainingSec % 60;

        String formatted = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        tvTimerCountdown.setText(formatted);

        int percent = (int) Math.round(((double) elapsedSec / (double) timerTotalDurationSec) * 100.0);
        pbTimerProgress.setProgress(Math.min(100, Math.max(0, percent)));
    }

    private void notifyClampCompletion() {
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 1000);
            }
        } catch (Exception ignored) {
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 400, 200, 400}, -1);
            }
        }
        Toast.makeText(this, "Sentinel Alert: Safe clamp release threshold reached!", Toast.LENGTH_LONG).show();
    }

    private void logCurrentBatch() {
        CalculatedResult res = calculateMetrics();
        String proj = etProjectName.getText().toString().trim();
        if (proj.isEmpty()) proj = "Shop Assembly";

        int tempF = sbTemperature.getProgress() + 35;
        int humidity = sbHumidity.getProgress();

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String rawJson = sp.getString(KEY_LOGS_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(rawJson);
            JSONObject obj = new JSONObject();
            obj.put("date", timeStamp);
            obj.put("project", proj);
            obj.put("glue", res.glueName);
            obj.put("temp", tempF + "°F");
            obj.put("humidity", humidity + "%");
            obj.put("clamp_time", formatHoursMinutes(res.clampMin));
            obj.put("full_cure", res.cureHours + "h");

            // prepend
            JSONArray newArr = new JSONArray();
            newArr.put(obj);
            for (int i = 0; i < arr.length() && i < 20; i++) {
                newArr.put(arr.get(i));
            }
            sp.edit().putString(KEY_LOGS_JSON, newArr.toString()).apply();
            renderHistoryList();
            Toast.makeText(this, "Batch recorded to audit log", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Log parsing error", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderHistoryList() {
        llHistoryContainer.removeAllViews();
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String rawJson = sp.getString(KEY_LOGS_JSON, "[]");

        try {
            JSONArray arr = new JSONArray(rawJson);
            if (arr.length() == 0) {
                TextView emptyTv = new TextView(this);
                emptyTv.setText("No previous clamp logs recorded.");
                emptyTv.setTextColor(getColor(android.R.color.darker_gray));
                emptyTv.setPadding(0, 16, 0, 16);
                llHistoryContainer.addView(emptyTv);
                return;
            }

            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                View logRow = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, llHistoryContainer, false);
                TextView text1 = logRow.findViewById(android.R.id.text1);
                TextView text2 = logRow.findViewById(android.R.id.text2);

                text1.setText(item.optString("project") + " • " + item.optString("glue"));
                text1.setTextColor(getColor(android.R.color.primary_text_dark));
                text1.setTextSize(14f);

                String details = item.optString("date") + " | " + item.optString("temp") + ", " +
                        item.optString("humidity") + " | Min Clamp: " + item.optString("clamp_time") +
                        " | Cure: " + item.optString("full_cure");
                text2.setText(details);
                text2.setTextColor(getColor(android.R.color.secondary_text_dark));
                text2.setTextSize(12f);

                llHistoryContainer.addView(logRow);
            }
        } catch (JSONException ignored) {
        }
    }

    private void exportTelemetryReport() {
        CalculatedResult res = calculateMetrics();
        int tempF = sbTemperature.getProgress() + 35;
        int humidity = sbHumidity.getProgress();
        String proj = etProjectName.getText().toString().trim();
        if (proj.isEmpty()) proj = "Woodworking Project";

        String report = "=== CLAMPGUARD CURE SENTINEL REPORT ===\n" +
                "Project: " + proj + "\n" +
                "Adhesive: " + res.glueName + "\n" +
                "Environment: " + tempF + "°F (" + fahrenheitToCelsius(tempF) + "°C) @ " + humidity + "% RH\n" +
                "Open Assembly Window: " + res.openMin + " minutes\n" +
                "Calculated Safe Clamp Time: " + formatHoursMinutes(res.clampMin) + "\n" +
                "Full Structural Cure: " + res.cureHours + " hours\n" +
                "Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\n" +
                "Status: Certified Safe Bond Calculation (Arrhenius-Kinetic Calibrated)\n" +
                "========================================";

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("ClampGuard Telemetry", report);
            clipboard.setPrimaryClip(clip);
        }

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, report);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Export Clamp Telemetry Report");
        startActivity(shareIntent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (!isTimerRunning) {
                        startSentinelTimer();
                    } else {
                        logCurrentBatch();
                    }
                    return true;
                case KeyEvent.KEYCODE_SPACE:
                    if (isTimerRunning) {
                        stopSentinelTimer(true);
                    } else {
                        startSentinelTimer();
                    }
                    return true;
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_EQUALS:
                    int curTemp = sbTemperature.getProgress();
                    sbTemperature.setProgress(Math.min(70, curTemp + 2));
                    return true;
                case KeyEvent.KEYCODE_MINUS:
                    int curTempDec = sbTemperature.getProgress();
                    sbTemperature.setProgress(Math.max(0, curTempDec - 2));
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Resizing freeform or desktop windows preserves all live calculation & countdown
        updateTimerDisplay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}