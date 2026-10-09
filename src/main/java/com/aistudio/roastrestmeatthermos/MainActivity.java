package com.aistudio.roastrestmeatthermos;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private static final String PREFS_NAME = "roast-rest-meat-thermo-sentinel_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_UNIT_CELSIUS = "pref_unit_celsius";
    private static final String KEY_PROTEIN_INDEX = "pref_protein_index";
    private static final String KEY_DONENESS_INDEX = "pref_doneness_index";
    private static final String KEY_COOK_METHOD_INDEX = "pref_cook_method_index";
    private static final String KEY_THICKNESS = "pref_thickness";
    private static final String KEY_BONE_IN = "pref_bone_in";
    private static final String KEY_FOIL_TENT = "pref_foil_tent";
    private static final String KEY_PULL_TEMP = "pref_pull_temp";
    private static final String KEY_HISTORY = "pref_rest_history";

    // Protein profiles: {Name, TargetMedRareF, CarryoverBaseF, RestMinsPerInch}
    private static final String[] PROTEINS = {"Beef Prime Rib / Ribeye", "Pork Loin / Tenderloin", "Whole Poultry / Turkey", "Rack of Lamb"};
    private static final double[] PROTEIN_BASE_CARRYOVER_F = {7.0, 5.0, 8.5, 6.0};
    private static final double[] PROTEIN_REST_MINS_PER_INCH = {5.0, 4.0, 6.0, 4.5};

    private static final String[] DONENESS_NAMES = {"Rare", "Medium Rare", "Medium", "Medium Well", "Well Done"};
    private static final double[] DONENESS_TARGET_F = {125.0, 135.0, 145.0, 155.0, 165.0};

    private static final String[] COOK_METHODS = {"Smoker / Low & Slow (225°F)", "Oven Roast (350°F)", "High Heat Sear / Grill (450°F+)"};
    private static final double[] COOK_METHOD_MULT = {0.8, 1.0, 1.35}; // High heat causes steeper temp gradient -> more carryover

    private SharedPreferences prefs;

    // Views
    private Button btnThemeToggle;
    private Button btnUnitToggle;
    private Button btnProteinPrev, btnProteinNext;
    private TextView tvProteinValue;
    private Button btnDonenessPrev, btnDonenessNext;
    private TextView tvDonenessValue;
    private Button btnMethodPrev, btnMethodNext;
    private TextView tvMethodValue;
    private SeekBar seekThickness;
    private TextView tvThicknessValue;
    private Button btnToggleBone, btnToggleFoil;
    private EditText etPullTemp;
    private Button btnTempMinus, btnTempPlus;

    // Telemetry & Results
    private TextView tvEstCarryover;
    private TextView tvEstPeakTemp;
    private TextView tvTargetDiff;
    private TextView tvRecommendedRest;
    private TextView tvRestAdvice;

    // Rest Sentinel Timer Views
    private TextView tvTimerCountdown;
    private TextView tvTimerStatus;
    private Button btnTimerStartPause, btnTimerReset, btnLogResult;

    // History Log View
    private TextView tvHistoryLog;
    private Button btnShareLog, btnClearLog;

    // State Variables
    private int selectedProtein = 0;
    private int selectedDoneness = 1; // Default: Medium Rare
    private int selectedMethod = 1;   // Default: Oven Roast
    private int thicknessInchesQuarter = 8; // in 0.25 inches (8 = 2.0 inches)
    private boolean isBoneIn = false;
    private boolean isFoilTented = true;
    private boolean isCelsius = false;

    private double calculatedCarryoverF = 0.0;
    private double calculatedPeakF = 0.0;
    private int calculatedRestDurationSeconds = 0;

    // Live Timer
    private CountDownTimer restTimer;
    private long timerRemainingMillis = 0;
    private boolean isTimerRunning = false;
    private Ringtone alarmRingtone;
    private Vibrator vibrator;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = sp.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark
        int nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;

        if (themeMode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNight = 0;
            try {
                systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {}
            nightMode = (systemNight == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (alert == null) {
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }
            alarmRingtone = RingtoneManager.getRingtone(getApplicationContext(), alert);
        } catch (Exception ignored) {}

        initViews();
        loadSavedPreferences();
        setupListeners();
        updateThemeButtonLabel();
        updateLightStatusBar();
        calculateTelemetry();
        renderHistory();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        btnProteinPrev = findViewById(R.id.btnProteinPrev);
        btnProteinNext = findViewById(R.id.btnProteinNext);
        tvProteinValue = findViewById(R.id.tvProteinValue);
        btnDonenessPrev = findViewById(R.id.btnDonenessPrev);
        btnDonenessNext = findViewById(R.id.btnDonenessNext);
        tvDonenessValue = findViewById(R.id.tvDonenessValue);
        btnMethodPrev = findViewById(R.id.btnMethodPrev);
        btnMethodNext = findViewById(R.id.btnMethodNext);
        tvMethodValue = findViewById(R.id.tvMethodValue);
        seekThickness = findViewById(R.id.seekThickness);
        tvThicknessValue = findViewById(R.id.tvThicknessValue);
        btnToggleBone = findViewById(R.id.btnToggleBone);
        btnToggleFoil = findViewById(R.id.btnToggleFoil);
        etPullTemp = findViewById(R.id.etPullTemp);
        btnTempMinus = findViewById(R.id.btnTempMinus);
        btnTempPlus = findViewById(R.id.btnTempPlus);

        tvEstCarryover = findViewById(R.id.tvEstCarryover);
        tvEstPeakTemp = findViewById(R.id.tvEstPeakTemp);
        tvTargetDiff = findViewById(R.id.tvTargetDiff);
        tvRecommendedRest = findViewById(R.id.tvRecommendedRest);
        tvRestAdvice = findViewById(R.id.tvRestAdvice);

        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        tvTimerStatus = findViewById(R.id.tvTimerStatus);
        btnTimerStartPause = findViewById(R.id.btnTimerStartPause);
        btnTimerReset = findViewById(R.id.btnTimerReset);
        btnLogResult = findViewById(R.id.btnLogResult);

        tvHistoryLog = findViewById(R.id.tvHistoryLog);
        btnShareLog = findViewById(R.id.btnShareLog);
        btnClearLog = findViewById(R.id.btnClearLog);
    }

    private void loadSavedPreferences() {
        isCelsius = prefs.getBoolean(KEY_UNIT_CELSIUS, false);
        selectedProtein = Math.max(0, Math.min(PROTEINS.length - 1, prefs.getInt(KEY_PROTEIN_INDEX, 0)));
        selectedDoneness = Math.max(0, Math.min(DONENESS_NAMES.length - 1, prefs.getInt(KEY_DONENESS_INDEX, 1)));
        selectedMethod = Math.max(0, Math.min(COOK_METHODS.length - 1, prefs.getInt(KEY_COOK_METHOD_INDEX, 1)));
        thicknessInchesQuarter = prefs.getInt(KEY_THICKNESS, 8);
        isBoneIn = prefs.getBoolean(KEY_BONE_IN, false);
        isFoilTented = prefs.getBoolean(KEY_FOIL_TENT, true);

        float defaultPullF = 125.0f;
        float savedPull = prefs.getFloat(KEY_PULL_TEMP, defaultPullF);
        if (isCelsius) {
            etPullTemp.setText(String.format(Locale.US, "%.1f", toCelsius(savedPull)));
        } else {
            etPullTemp.setText(String.format(Locale.US, "%.1f", (double) savedPull));
        }

        seekThickness.setProgress(thicknessInchesQuarter);
        updateSelectorsUI();
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleTemperatureUnit();
        });

        btnProteinPrev.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedProtein = (selectedProtein - 1 + PROTEINS.length) % PROTEINS.length;
            updateSelectorsUI();
            calculateTelemetry();
        });

        btnProteinNext.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedProtein = (selectedProtein + 1) % PROTEINS.length;
            updateSelectorsUI();
            calculateTelemetry();
        });

        btnDonenessPrev.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedDoneness = (selectedDoneness - 1 + DONENESS_NAMES.length) % DONENESS_NAMES.length;
            updateSelectorsUI();
            calculateTelemetry();
        });

        btnDonenessNext.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedDoneness = (selectedDoneness + 1) % DONENESS_NAMES.length;
            updateSelectorsUI();
            calculateTelemetry();
        });

        btnMethodPrev.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedMethod = (selectedMethod - 1 + COOK_METHODS.length) % COOK_METHODS.length;
            updateSelectorsUI();
            calculateTelemetry();
        });

        btnMethodNext.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedMethod = (selectedMethod + 1) % COOK_METHODS.length;
            updateSelectorsUI();
            calculateTelemetry();
        });

        seekThickness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thicknessInchesQuarter = Math.max(2, progress);
                double inches = thicknessInchesQuarter * 0.25;
                tvThicknessValue.setText(String.format(Locale.US, "%.2f\" / %.1f cm", inches, inches * 2.54));
                calculateTelemetry();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveInputPreferences();
            }
        });

        btnToggleBone.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isBoneIn = !isBoneIn;
            updateToggleButtonsUI();
            calculateTelemetry();
            saveInputPreferences();
        });

        btnToggleFoil.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isFoilTented = !isFoilTented;
            updateToggleButtonsUI();
            calculateTelemetry();
            saveInputPreferences();
        });

        btnTempMinus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            adjustPullTemp(-1.0);
        });

        btnTempPlus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            adjustPullTemp(1.0);
        });

        etPullTemp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                calculateTelemetry();
            }
        });

        btnTimerStartPause.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleTimer();
        });

        btnTimerReset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetTimer();
        });

        btnLogResult.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            recordCurrentRoast();
        });

        btnShareLog.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareLogHistory();
        });

        btnClearLog.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearLogHistory();
        });
    }

    private void updateSelectorsUI() {
        tvProteinValue.setText(PROTEINS[selectedProtein]);
        double targetF = DONENESS_TARGET_F[selectedDoneness];
        String targetStr = isCelsius ? String.format(Locale.US, "%.1f°C", toCelsius(targetF)) : String.format(Locale.US, "%.0f°F", targetF);
        tvDonenessValue.setText(String.format("%s (%s)", DONENESS_NAMES[selectedDoneness], targetStr));
        tvMethodValue.setText(COOK_METHODS[selectedMethod]);
        double inches = thicknessInchesQuarter * 0.25;
        tvThicknessValue.setText(String.format(Locale.US, "%.2f\" / %.1f cm", inches, inches * 2.54));
        btnUnitToggle.setText(isCelsius ? "Display: °C" : "Display: °F");
        updateToggleButtonsUI();
    }

    private void updateToggleButtonsUI() {
        btnToggleBone.setText(isBoneIn ? "Bone: Bone-In (+Density)" : "Bone: Boneless");
        btnToggleFoil.setText(isFoilTented ? "Cover: Tented Foil (Insulated)" : "Cover: Open Air / Bare");
    }

    private void adjustPullTemp(double delta) {
        try {
            double current = Double.parseDouble(etPullTemp.getText().toString().trim());
            current += delta;
            etPullTemp.setText(String.format(Locale.US, "%.1f", current));
        } catch (NumberFormatException e) {
            etPullTemp.setText(isCelsius ? "52.0" : "125.0");
        }
        saveInputPreferences();
    }

    private void toggleTemperatureUnit() {
        try {
            double currentVal = Double.parseDouble(etPullTemp.getText().toString().trim());
            if (isCelsius) {
                // Switching to F
                double valF = (currentVal * 9.0 / 5.0) + 32.0;
                isCelsius = false;
                etPullTemp.setText(String.format(Locale.US, "%.1f", valF));
            } else {
                // Switching to C
                double valC = toCelsius(currentVal);
                isCelsius = true;
                etPullTemp.setText(String.format(Locale.US, "%.1f", valC));
            }
        } catch (Exception ignored) {
            isCelsius = !isCelsius;
        }
        prefs.edit().putBoolean(KEY_UNIT_CELSIUS, isCelsius).apply();
        updateSelectorsUI();
        calculateTelemetry();
    }

    private double getEnteredPullTempF() {
        try {
            double entered = Double.parseDouble(etPullTemp.getText().toString().trim());
            return isCelsius ? ((entered * 9.0 / 5.0) + 32.0) : entered;
        } catch (Exception e) {
            return 125.0;
        }
    }

    private double toCelsius(double f) {
        return (f - 32.0) * 5.0 / 9.0;
    }

    private void calculateTelemetry() {
        double pullF = getEnteredPullTempF();
        double baseCarryover = PROTEIN_BASE_CARRYOVER_F[selectedProtein];
        double thicknessInches = thicknessInchesQuarter * 0.25;

        // Thickness coefficient: Thicker cuts store significantly more thermal inertia in outer bands
        double thicknessFactor = Math.pow(thicknessInches / 2.0, 0.65);
        double methodFactor = COOK_METHOD_MULT[selectedMethod];
        double boneFactor = isBoneIn ? 0.90 : 1.0; // Bone acts as heat sink reducing peak rise slightly
        double foilFactor = isFoilTented ? 1.25 : 1.0; // Foil stops evaporative cooling and traps heat

        calculatedCarryoverF = baseCarryover * thicknessFactor * methodFactor * boneFactor * foilFactor;
        // Clamp carryover to realistic culinary limits (2°F - 25°F)
        calculatedCarryoverF = Math.max(2.0, Math.min(26.0, calculatedCarryoverF));
        calculatedPeakF = pullF + calculatedCarryoverF;

        // Recommended Rest Time in Seconds
        double restMinutes = thicknessInches * PROTEIN_REST_MINS_PER_INCH[selectedProtein] * (isFoilTented ? 1.15 : 1.0);
        restMinutes = Math.max(5.0, Math.min(45.0, restMinutes));
        calculatedRestDurationSeconds = (int) (restMinutes * 60);

        // Display Telemetry
        double targetF = DONENESS_TARGET_F[selectedDoneness];
        double diffF = calculatedPeakF - targetF;

        if (isCelsius) {
            double carryoverC = calculatedCarryoverF * 5.0 / 9.0;
            tvEstCarryover.setText(String.format(Locale.US, "+%.1f°C", carryoverC));
            tvEstPeakTemp.setText(String.format(Locale.US, "%.1f°C", toCelsius(calculatedPeakF)));
            double diffC = diffF * 5.0 / 9.0;
            String sign = diffC >= 0 ? "+" : "";
            tvTargetDiff.setText(String.format(Locale.US, "%s%.1f°C vs target", sign, diffC));
        } else {
            tvEstCarryover.setText(String.format(Locale.US, "+%.1f°F", calculatedCarryoverF));
            tvEstPeakTemp.setText(String.format(Locale.US, "%.1f°F", calculatedPeakF));
            String sign = diffF >= 0 ? "+" : "";
            tvTargetDiff.setText(String.format(Locale.US, "%s%.1f°F vs target", sign, diffF));
        }

        int restMins = calculatedRestDurationSeconds / 60;
        int restSecs = calculatedRestDurationSeconds % 60;
        tvRecommendedRest.setText(String.format(Locale.US, "%02d:%02d min", restMins, restSecs));

        // Qualitative advice
        StringBuilder advice = new StringBuilder();
        if (Math.abs(diffF) <= 2.0) {
            advice.append("🎯 Optimal Pull! On track for perfect ").append(DONENESS_NAMES[selectedDoneness]).append(".");
        } else if (diffF > 2.0) {
            advice.append("⚠️ OVERSHOOT RISK: Estimated to peak ").append(String.format(Locale.US, "%.1f°", Math.abs(diffF)))
                    .append(" above target. Remove foil tent immediately or pull earlier next time.");
        } else {
            advice.append("❄️ UNDERSHOOT NOTICE: Estimated to peak ").append(String.format(Locale.US, "%.1f°", Math.abs(diffF)))
                    .append(" below target. Wrap tight with foil and place in warm oven (turned off).");
        }
        tvRestAdvice.setText(advice.toString());

        if (!isTimerRunning && timerRemainingMillis == 0) {
            updateTimerDisplay(calculatedRestDurationSeconds * 1000L);
        }
    }

    private void updateTimerDisplay(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        tvTimerCountdown.setText(String.format(Locale.US, "%02d:%02d", m, s));
    }

    private void toggleTimer() {
        if (isTimerRunning) {
            pauseTimer();
        } else {
            startTimer();
        }
    }

    private void startTimer() {
        if (timerRemainingMillis <= 0) {
            timerRemainingMillis = calculatedRestDurationSeconds * 1000L;
        }

        restTimer = new CountDownTimer(timerRemainingMillis, 500) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerRemainingMillis = millisUntilFinished;
                updateTimerDisplay(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                timerRemainingMillis = 0;
                updateTimerDisplay(0);
                tvTimerStatus.setText("STATUS: REST COMPLETE - READY TO CARVE!");
                btnTimerStartPause.setText("Start Rest Timer");
                triggerAlarm();
            }
        }.start();

        isTimerRunning = true;
        btnTimerStartPause.setText("Pause Timer");
        tvTimerStatus.setText("STATUS: RESTING & STABILIZING JUICES...");
    }

    private void pauseTimer() {
        if (restTimer != null) {
            restTimer.cancel();
        }
        isTimerRunning = false;
        btnTimerStartPause.setText("Resume Timer");
        tvTimerStatus.setText("STATUS: PAUSED");
    }

    private void resetTimer() {
        if (restTimer != null) {
            restTimer.cancel();
        }
        isTimerRunning = false;
        timerRemainingMillis = calculatedRestDurationSeconds * 1000L;
        updateTimerDisplay(timerRemainingMillis);
        btnTimerStartPause.setText("Start Rest Timer");
        tvTimerStatus.setText("STATUS: STANDBY");
    }

    private void triggerAlarm() {
        if (alarmRingtone != null && !alarmRingtone.isPlaying()) {
            try {
                alarmRingtone.play();
            } catch (Exception ignored) {}
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400, 200, 600}, -1));
            } else {
                vibrator.vibrate(1000);
            }
        }
        Toast.makeText(this, "Rest Finished! Internal pressure normalized. Slice & serve!", Toast.LENGTH_LONG).show();
    }

    private void recordCurrentRoast() {
        try {
            JSONArray history = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            JSONObject item = new JSONObject();
            item.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            item.put("protein", PROTEINS[selectedProtein]);
            item.put("doneness", DONENESS_NAMES[selectedDoneness]);
            double pullF = getEnteredPullTempF();
            item.put("pullF", String.format(Locale.US, "%.1f°F", pullF));
            item.put("carryoverF", String.format(Locale.US, "+%.1f°F", calculatedCarryoverF));
            item.put("peakF", String.format(Locale.US, "%.1f°F", calculatedPeakF));
            item.put("restMins", String.format(Locale.US, "%.1f min", (calculatedRestDurationSeconds / 60.0)));
            item.put("method", COOK_METHODS[selectedMethod]);
            item.put("foil", isFoilTented ? "Tented" : "Bare");

            history.put(item);
            prefs.edit().putString(KEY_HISTORY, history.toString()).apply();
            renderHistory();
            Toast.makeText(this, "Session Saved to Thermal Journal!", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Failed to save journal record.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderHistory() {
        try {
            JSONArray history = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            if (history.length() == 0) {
                tvHistoryLog.setText("No recorded rests yet. Log your completed roasts to calibrate carryover precision.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject obj = history.getJSONObject(i);
                sb.append("• [").append(obj.optString("timestamp")).append("] ")
                  .append(obj.optString("protein")).append(" (").append(obj.optString("doneness")).append(")\n")
                  .append("  Pull: ").append(obj.optString("pullF"))
                  .append("  | Est Rise: ").append(obj.optString("carryoverF"))
                  .append("  | Peak: ").append(obj.optString("peakF")).append("\n")
                  .append("  Rest Duration: ").append(obj.optString("restMins"))
                  .append(" (").append(obj.optString("foil")).append(", ").append(obj.optString("method")).append(")\n\n");
            }
            tvHistoryLog.setText(sb.toString().trim());
        } catch (JSONException e) {
            tvHistoryLog.setText("Error reading history journal.");
        }
    }

    private void shareLogHistory() {
        String logText = tvHistoryLog.getText().toString();
        if (logText.isEmpty() || logText.startsWith("No recorded")) {
            Toast.makeText(this, "No history to export.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "RoastRest Thermal Log Journal");
        sendIntent.putExtra(Intent.EXTRA_TEXT, "=== RoastRest Meat Sentinel Thermal Journal ===\n\n" + logText);
        startActivity(Intent.createChooser(sendIntent, "Export Thermal Journal"));
    }

    private void clearLogHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
        renderHistory();
        Toast.makeText(this, "Journal cleared.", Toast.LENGTH_SHORT).show();
    }

    private void saveInputPreferences() {
        double pullF = getEnteredPullTempF();
        prefs.edit()
                .putInt(KEY_PROTEIN_INDEX, selectedProtein)
                .putInt(KEY_DONENESS_INDEX, selectedDoneness)
                .putInt(KEY_COOK_METHOD_INDEX, selectedMethod)
                .putInt(KEY_THICKNESS, thicknessInchesQuarter)
                .putBoolean(KEY_BONE_IN, isBoneIn)
                .putBoolean(KEY_FOIL_TENT, isFoilTented)
                .putFloat(KEY_PULL_TEMP, (float) pullF)
                .apply();
    }

    private void cycleThemeMode() {
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void updateThemeButtonLabel() {
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        if (current == 1) {
            btnThemeToggle.setText("Theme: Light");
        } else if (current == 2) {
            btnThemeToggle.setText("Theme: Dark");
        } else {
            btnThemeToggle.setText("Theme: Auto");
        }
    }

    private void updateLightStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int currentNight = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (currentNight == Configuration.UI_MODE_NIGHT_NO) {
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                calculateTelemetry();
                recordCurrentRoast();
                return true;
            } else if (code == KeyEvent.KEYCODE_SPACE) {
                toggleTimer();
                return true;
            } else if (code == KeyEvent.KEYCODE_PLUS || code == KeyEvent.KEYCODE_NUMPAD_ADD || code == KeyEvent.KEYCODE_EQUALS) {
                adjustPullTemp(1.0);
                return true;
            } else if (code == KeyEvent.KEYCODE_MINUS || code == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) {
                adjustPullTemp(-1.0);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLightStatusBar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveInputPreferences();
    }

    @Override
    protected void onDestroy() {
        if (restTimer != null) {
            restTimer.cancel();
        }
        if (alarmRingtone != null && alarmRingtone.isPlaying()) {
            alarmRingtone.stop();
        }
        super.onDestroy();
    }
}