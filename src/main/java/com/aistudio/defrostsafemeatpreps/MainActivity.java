package com.aistudio.defrostsafemeatpreps;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
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

    private static final String PREFS_NAME = "defrost-safe-meat-prep-sentinel_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_LOGS_JSON = "saved_prep_logs";
    private static final String KEY_LAST_WEIGHT = "last_weight";
    private static final String KEY_LAST_THICKNESS = "last_thickness";
    private static final String KEY_LAST_MEAT_TYPE = "last_meat_type";
    private static final String KEY_LAST_METHOD = "last_method";
    private static final String KEY_LAST_UNIT_LBS = "last_unit_lbs";

    // Meat Types
    private static final String[] MEAT_TYPES = {
            "Poultry (Chicken / Turkey)",
            "Red Meat (Beef / Steak / Roast)",
            "Ground Meat (Beef, Pork, Poultry)",
            "Pork (Chops / Loin)",
            "Fish & Delicate Seafood"
    };

    // UI Components
    private Button btnThemeToggle;
    private Spinner spinnerMeatType;
    private EditText etWeight;
    private Switch switchUnit;
    private TextView tvWeightLabel;
    private SeekBar seekThickness;
    private TextView tvThicknessValue;
    private RadioGroup rgMethod;
    private RadioButton rbFridge, rbColdWater, rbMicrowave;
    private Button btnCalculate;

    // Results Card
    private TextView tvEstimatedTime;
    private TextView tvDangerZoneBadge;
    private TextView tvSafetySteps;
    private TextView tvMaxSafeStorage;

    // Live Sentinel Timer Card
    private TextView tvTimerCountdown;
    private TextView tvWaterExchangeCountdown;
    private Button btnStartTimer;
    private Button btnResetTimer;
    private TextView tvTimerStatus;

    // Log & Action Buttons
    private Button btnSaveLog;
    private Button btnShareLog;
    private Button btnCopyAdvice;
    private Button btnClearLogs;
    private LinearLayout layoutLogContainer;

    // Active Calculation State
    private int selectedMeatIndex = 0;
    private double calculatedTotalMinutes = 0;
    private double currentWeightVal = 1.0;
    private boolean isLbs = true;
    private int currentThicknessQuarterInches = 4; // default 1.0 inch (4 * 0.25)
    private int currentMethodId = R.id.rbFridge;

    // Countdown Timer State
    private CountDownTimer mainTimer;
    private boolean isTimerRunning = false;
    private long remainingMillis = 0;
    private long totalDurationMillis = 0;

    // Audio & Haptic feedback
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark
        int targetNightMode;

        if (themeMode == 1) {
            targetNightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            targetNightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemSetting = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (systemSetting == 2) {
                targetNightMode = Configuration.UI_MODE_NIGHT_YES;
            } else {
                int currentNight = newBase.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                targetNightMode = (currentNight == Configuration.UI_MODE_NIGHT_YES) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
            }
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | targetNightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initSystemFeedback();
        bindViews();
        setupThemeToggle();
        setupSpinnersAndInputs();
        setupListeners();
        restoreSavedState();
        calculateAndRender();
        renderLogs();
    }

    private void initSystemFeedback() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
        } catch (Exception ignored) {
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void bindViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        spinnerMeatType = findViewById(R.id.spinnerMeatType);
        etWeight = findViewById(R.id.etWeight);
        switchUnit = findViewById(R.id.switchUnit);
        tvWeightLabel = findViewById(R.id.tvWeightLabel);
        seekThickness = findViewById(R.id.seekThickness);
        tvThicknessValue = findViewById(R.id.tvThicknessValue);
        rgMethod = findViewById(R.id.rgMethod);
        rbFridge = findViewById(R.id.rbFridge);
        rbColdWater = findViewById(R.id.rbColdWater);
        rbMicrowave = findViewById(R.id.rbMicrowave);
        btnCalculate = findViewById(R.id.btnCalculate);

        tvEstimatedTime = findViewById(R.id.tvEstimatedTime);
        tvDangerZoneBadge = findViewById(R.id.tvDangerZoneBadge);
        tvSafetySteps = findViewById(R.id.tvSafetySteps);
        tvMaxSafeStorage = findViewById(R.id.tvMaxSafeStorage);

        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        tvWaterExchangeCountdown = findViewById(R.id.tvWaterExchangeCountdown);
        btnStartTimer = findViewById(R.id.btnStartTimer);
        btnResetTimer = findViewById(R.id.btnResetTimer);
        tvTimerStatus = findViewById(R.id.tvTimerStatus);

        btnSaveLog = findViewById(R.id.btnSaveLog);
        btnShareLog = findViewById(R.id.btnShareLog);
        btnCopyAdvice = findViewById(R.id.btnCopyAdvice);
        btnClearLogs = findViewById(R.id.btnClearLogs);
        layoutLogContainer = findViewById(R.id.layoutLogContainer);
    }

    private void setupThemeToggle() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0);

        if (themeMode == 1) {
            btnThemeToggle.setText("☀ Theme: Light");
        } else if (themeMode == 2) {
            btnThemeToggle.setText("🌙 Theme: Dark");
        } else {
            btnThemeToggle.setText("⚙ Theme: Auto");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int nightMask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (nightMask == Configuration.UI_MODE_NIGHT_NO) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }

        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });
    }

    private void cycleThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3; // 0 (Auto) -> 1 (Light) -> 2 (Dark) -> 0
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void setupSpinnersAndInputs() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                MEAT_TYPES
        );
        spinnerMeatType.setAdapter(adapter);

        spinnerMeatType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMeatIndex = position;
                calculateAndRender();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        switchUnit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isLbs = !isChecked;
            tvWeightLabel.setText(isLbs ? "Weight (lbs):" : "Weight (kg):");
            double parsed = parseDouble(etWeight.getText().toString(), 1.0);
            if (isChecked) {
                // converted to kg
                etWeight.setText(String.format(Locale.US, "%.2f", parsed * 0.45359237));
            } else {
                // converted to lbs
                etWeight.setText(String.format(Locale.US, "%.2f", parsed * 2.20462));
            }
            calculateAndRender();
        });

        seekThickness.setMax(16); // Up to 4.0 inches in 0.25 inch increments
        seekThickness.setProgress(currentThicknessQuarterInches);
        seekThickness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 1) {
                    progress = 1;
                    seekBar.setProgress(1);
                }
                currentThicknessQuarterInches = progress;
                double inches = progress * 0.25;
                double cm = inches * 2.54;
                tvThicknessValue.setText(String.format(Locale.US, "%.2f in (%.1f cm)", inches, cm));
                if (fromUser) {
                    calculateAndRender();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        rgMethod.setOnCheckedChangeListener((group, checkedId) -> {
            currentMethodId = checkedId;
            calculateAndRender();
        });
    }

    private void setupListeners() {
        btnCalculate.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            calculateAndRender();
        });

        btnStartTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleTimer();
        });

        btnResetTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetTimer();
        });

        btnSaveLog.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveCurrentLog();
        });

        btnShareLog.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareAdvice();
        });

        btnCopyAdvice.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            copyAdviceToClipboard();
        });

        btnClearLogs.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearAllLogs();
        });
    }

    private void restoreSavedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedMeatIndex = prefs.getInt(KEY_LAST_MEAT_TYPE, 0);
        if (selectedMeatIndex >= 0 && selectedMeatIndex < MEAT_TYPES.length) {
            spinnerMeatType.setSelection(selectedMeatIndex);
        }

        isLbs = prefs.getBoolean(KEY_LAST_UNIT_LBS, true);
        switchUnit.setChecked(!isLbs);
        tvWeightLabel.setText(isLbs ? "Weight (lbs):" : "Weight (kg):");

        float weight = prefs.getFloat(KEY_LAST_WEIGHT, 1.5f);
        etWeight.setText(String.format(Locale.US, "%.2f", weight));

        currentThicknessQuarterInches = prefs.getInt(KEY_LAST_THICKNESS, 4);
        seekThickness.setProgress(currentThicknessQuarterInches);
        double inches = currentThicknessQuarterInches * 0.25;
        double cm = inches * 2.54;
        tvThicknessValue.setText(String.format(Locale.US, "%.2f in (%.1f cm)", inches, cm));

        currentMethodId = prefs.getInt(KEY_LAST_METHOD, R.id.rbFridge);
        if (currentMethodId == R.id.rbColdWater) {
            rbColdWater.setChecked(true);
        } else if (currentMethodId == R.id.rbMicrowave) {
            rbMicrowave.setChecked(true);
        } else {
            rbFridge.setChecked(true);
        }
    }

    private void persistCurrentInputs() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_LAST_MEAT_TYPE, selectedMeatIndex);
        editor.putBoolean(KEY_LAST_UNIT_LBS, isLbs);
        editor.putFloat(KEY_LAST_WEIGHT, (float) currentWeightVal);
        editor.putInt(KEY_LAST_THICKNESS, currentThicknessQuarterInches);
        editor.putInt(KEY_LAST_METHOD, currentMethodId);
        editor.apply();
    }

    private double parseDouble(String str, double def) {
        try {
            return Double.parseDouble(str.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void calculateAndRender() {
        currentWeightVal = parseDouble(etWeight.getText().toString(), 1.0);
        if (currentWeightVal <= 0.05) {
            currentWeightVal = 0.05;
        }

        // Standardize weight to lbs for USDA math
        double weightInLbs = isLbs ? currentWeightVal : currentWeightVal * 2.20462;
        double thicknessInches = currentThicknessQuarterInches * 0.25;
        double thicknessFactor = Math.max(0.7, thicknessInches / 1.0); // 1.0 in is baseline

        calculatedTotalMinutes = 0;
        String timeDisplay = "";
        String badge = "";
        String instructions = "";
        String storageNotice = "";

        if (currentMethodId == R.id.rbFridge) {
            // USDA rule: 4 to 5 lbs takes ~24 hours (~5 to 6 hours per lb).
            // Seafood / small cuts defrost faster (~4 hours/lb).
            double hoursPerLb;
            if (selectedMeatIndex == 4) { // Fish / Seafood
                hoursPerLb = 3.5 * thicknessFactor;
                storageNotice = "1 to 2 days maximum safely refrigerated after thaw.";
            } else if (selectedMeatIndex == 2) { // Ground Meat
                hoursPerLb = 4.5 * thicknessFactor;
                storageNotice = "1 to 2 days maximum safely refrigerated after thaw.";
            } else if (selectedMeatIndex == 0) { // Poultry
                hoursPerLb = 5.0 * thicknessFactor;
                storageNotice = "1 to 2 days maximum safely refrigerated after thaw.";
            } else if (selectedMeatIndex == 3) { // Pork
                hoursPerLb = 4.8 * thicknessFactor;
                storageNotice = "3 to 5 days safely refrigerated after thaw.";
            } else { // Red Meat / Roast
                hoursPerLb = 5.2 * thicknessFactor;
                storageNotice = "3 to 5 days safely refrigerated after thaw.";
            }

            double totalHours = weightInLbs * hoursPerLb;
            calculatedTotalMinutes = totalHours * 60.0;

            int hrs = (int) Math.floor(totalHours);
            int mins = (int) Math.round((totalHours - hrs) * 60);
            timeDisplay = hrs + "h " + mins + "m";

            badge = "SAFE ZONE (UNDER 40°F / 4°C)";
            instructions = "• Keep wrapped on bottom shelf of refrigerator to prevent juices dripping.\n"
                    + "• Consistent temperature under 40°F completely inhibits pathogenic bacteria growth.\n"
                    + "• Safe to refreeze if unused, though texture quality may drop.";

        } else if (currentMethodId == R.id.rbColdWater) {
            // USDA rule: ~30 minutes per lb for small cuts; submerged, cold tap water changed every 30 mins.
            double rateMinutesPerLb;
            if (selectedMeatIndex == 4) { // Fish
                rateMinutesPerLb = 20.0 * thicknessFactor;
            } else if (selectedMeatIndex == 2) { // Ground meat
                rateMinutesPerLb = 30.0 * thicknessFactor;
            } else {
                rateMinutesPerLb = 32.0 * thicknessFactor;
            }

            calculatedTotalMinutes = weightInLbs * rateMinutesPerLb;
            int hrs = (int) Math.floor(calculatedTotalMinutes / 60.0);
            int mins = (int) Math.round(calculatedTotalMinutes % 60);

            if (hrs > 0) {
                timeDisplay = hrs + "h " + mins + "m";
            } else {
                timeDisplay = mins + " mins";
            }

            badge = "ACTIVE SUBMERSION PROTOCOL";
            instructions = "• Seal tightly in leak-proof zipper bag to prevent bacterial infusion and soggy meat.\n"
                    + "• Fully submerge in cold tap water (never warm or hot water!).\n"
                    + "• MANDATORY: Drain and refresh water every 30 minutes to maintain temperature under 40°F.\n"
                    + "• Must be cooked immediately after thawing before refreezing.";
            storageNotice = "Cook IMMEDIATELY upon completion. Do not store raw.";

        } else if (currentMethodId == R.id.rbMicrowave) {
            // Microwave: ~7 to 9 minutes per pound at 30% defrost power, plus mandatory flipping.
            double minutesPerLb = 7.5 * thicknessFactor;
            calculatedTotalMinutes = weightInLbs * minutesPerLb;
            int mins = (int) Math.round(calculatedTotalMinutes);
            timeDisplay = mins + " mins";

            badge = "DANGER ZONE ACCELERATION RISK";
            instructions = "• Remove all outer packaging and foam trays.\n"
                    + "• Set Microwave to DEFROST (30% power level).\n"
                    + "• Flip, rotate, and separate outer pieces halfway through defrosting.\n"
                    + "• CRITICAL: Edges may begin cooking and enter the bacterial Danger Zone (40°F - 140°F).\n"
                    + "• NEVER store or hold back in the fridge; cook completely right away.";
            storageNotice = "Must be cooked IMMEDIATELY without delay. No raw holding.";
        }

        tvEstimatedTime.setText(timeDisplay);
        tvDangerZoneBadge.setText(badge);
        tvSafetySteps.setText(instructions);
        tvMaxSafeStorage.setText(storageNotice);

        if (!isTimerRunning) {
            totalDurationMillis = (long) (calculatedTotalMinutes * 60 * 1000);
            remainingMillis = totalDurationMillis;
            updateCountdownText(remainingMillis);
        }

        persistCurrentInputs();
    }

    private void toggleTimer() {
        if (isTimerRunning) {
            pauseTimer();
        } else {
            startTimer();
        }
    }

    private void startTimer() {
        if (remainingMillis <= 0) {
            totalDurationMillis = (long) (calculatedTotalMinutes * 60 * 1000);
            remainingMillis = totalDurationMillis;
        }

        mainTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                updateCountdownText(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                remainingMillis = 0;
                updateCountdownText(0);
                btnStartTimer.setText("Start Sentinel Timer");
                tvTimerStatus.setText("THAW CYCLE EXPIRED - PROCEED WITH IMMEDIATE COOKING OR PREP");
                triggerAlarmNotification();
            }
        }.start();

        isTimerRunning = true;
        btnStartTimer.setText("Pause Timer");
        tvTimerStatus.setText("Active Sentinel Monitoring in Progress...");
    }

    private void pauseTimer() {
        if (mainTimer != null) {
            mainTimer.cancel();
        }
        isTimerRunning = false;
        btnStartTimer.setText("Resume Timer");
        tvTimerStatus.setText("Timer Paused");
    }

    private void resetTimer() {
        if (mainTimer != null) {
            mainTimer.cancel();
        }
        isTimerRunning = false;
        totalDurationMillis = (long) (calculatedTotalMinutes * 60 * 1000);
        remainingMillis = totalDurationMillis;
        updateCountdownText(remainingMillis);
        btnStartTimer.setText("Start Sentinel Timer");
        tvTimerStatus.setText("Sentinel Ready");
    }

    private void updateCountdownText(long millis) {
        long seconds = millis / 1000;
        long hrs = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;

        tvTimerCountdown.setText(String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs));

        if (currentMethodId == R.id.rbColdWater) {
            tvWaterExchangeCountdown.setVisibility(View.VISIBLE);
            // 30 minute cycle is 1800 seconds
            long elapsedSeconds = (totalDurationMillis - millis) / 1000;
            long nextRefreshSeconds = 1800 - (elapsedSeconds % 1800);
            long refreshMins = nextRefreshSeconds / 60;
            long refreshSecs = nextRefreshSeconds % 60;
            tvWaterExchangeCountdown.setText(String.format(Locale.US, "Next Water Refresh: %02d:%02d", refreshMins, refreshSecs));
        } else {
            tvWaterExchangeCountdown.setVisibility(View.GONE);
        }
    }

    private void triggerAlarmNotification() {
        if (toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500);
            } catch (Exception ignored) {
            }
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(1000);
            }
        }
        Toast.makeText(this, "Sentinel Alert: Meat Defrost Complete!", Toast.LENGTH_LONG).show();
    }

    private void saveCurrentLog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existingJson = prefs.getString(KEY_LOGS_JSON, "[]");
        try {
            JSONArray array = new JSONArray(existingJson);
            JSONObject obj = new JSONObject();
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

            String methodName = (currentMethodId == R.id.rbColdWater) ? "Cold Water Submersion" :
                    (currentMethodId == R.id.rbMicrowave) ? "Microwave Defrost" : "Refrigerator Safe";

            obj.put("timestamp", timeStamp);
            obj.put("meat", MEAT_TYPES[selectedMeatIndex]);
            obj.put("weight", String.format(Locale.US, "%.2f %s", currentWeightVal, isLbs ? "lbs" : "kg"));
            obj.put("method", methodName);
            obj.put("duration", tvEstimatedTime.getText().toString());

            array.put(0, obj); // Insert at beginning
            if (array.length() > 20) {
                // limit to 20 logs
                JSONArray trimmed = new JSONArray();
                for (int i = 0; i < 20; i++) {
                    trimmed.put(array.get(i));
                }
                array = trimmed;
            }

            prefs.edit().putString(KEY_LOGS_JSON, array.toString()).apply();
            renderLogs();
            Toast.makeText(this, "Batch preparation logged to Sentinel archive.", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Failed to save log", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderLogs() {
        layoutLogContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existingJson = prefs.getString(KEY_LOGS_JSON, "[]");

        try {
            JSONArray array = new JSONArray(existingJson);
            if (array.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("No defrost logs recorded yet. Tap 'Log Batch' above.");
                empty.setPadding(16, 24, 16, 24);
                empty.setTextColor(Color.GRAY);
                layoutLogContainer.addView(empty);
                return;
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                View logRow = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
                TextView text1 = logRow.findViewById(android.R.id.text1);
                TextView text2 = logRow.findViewById(android.R.id.text2);

                text1.setText(obj.getString("meat") + " • " + obj.getString("weight"));
                text1.setTextSize(14f);
                text2.setText(obj.getString("method") + " (" + obj.getString("duration") + ") - " + obj.getString("timestamp"));
                text2.setTextSize(12f);
                text2.setTextColor(Color.GRAY);

                layoutLogContainer.addView(logRow);
            }
        } catch (JSONException ignored) {
        }
    }

    private void clearAllLogs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_LOGS_JSON, "[]").apply();
        renderLogs();
        Toast.makeText(this, "Archive cleared.", Toast.LENGTH_SHORT).show();
    }

    private String generateShareableReport() {
        String methodName = (currentMethodId == R.id.rbColdWater) ? "Cold Water Submersion" :
                (currentMethodId == R.id.rbMicrowave) ? "Microwave Defrost" : "Refrigerator Safe";

        return "❄ ChillThaw Defrost Sentinel Report ❄\n"
                + "Item: " + MEAT_TYPES[selectedMeatIndex] + "\n"
                + "Weight: " + String.format(Locale.US, "%.2f %s", currentWeightVal, isLbs ? "lbs" : "kg") + "\n"
                + "Thickness: " + String.format(Locale.US, "%.2f in", currentThicknessQuarterInches * 0.25) + "\n"
                + "Method: " + methodName + "\n"
                + "Calculated Thaw Time: " + tvEstimatedTime.getText() + "\n"
                + "Safety Status: " + tvDangerZoneBadge.getText() + "\n"
                + "Max Post-Thaw Safe Hold: " + tvMaxSafeStorage.getText() + "\n\n"
                + "USDA Guidelines:\n" + tvSafetySteps.getText();
    }

    private void shareAdvice() {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, generateShareableReport());
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Defrost Protocol");
        startActivity(shareIntent);
    }

    private void copyAdviceToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Defrost Protocol", generateShareableReport());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Protocol copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                btnCalculate.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                btnStartTimer.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                int progress = seekThickness.getProgress();
                if (progress < seekThickness.getMax()) {
                    seekThickness.setProgress(progress + 1);
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                int progress = seekThickness.getProgress();
                if (progress > 1) {
                    seekThickness.setProgress(progress - 1);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Retain live state and recalculate UI sizing seamlessly
        calculateAndRender();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainTimer != null) {
            mainTimer.cancel();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}