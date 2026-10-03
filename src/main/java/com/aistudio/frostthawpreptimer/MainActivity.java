package com.aistudio.frostthawpreptimer;

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

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREF_NAME = "frost-thaw-prep-timer_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_FOOD_TYPE = "key_food_type";
    private static final String KEY_METHOD = "key_method";
    private static final String KEY_WEIGHT_GRAMS = "key_weight_grams";
    private static final String KEY_THICKNESS_CM = "key_thickness_cm";
    private static final String KEY_COLD_WATER_ALARM = "key_cold_water_alarm";
    private static final String KEY_TIMER_RUNNING = "key_timer_running";
    private static final String KEY_REMAINING_MILLIS = "key_remaining_millis";
    private static final String KEY_END_TIMESTAMP = "key_end_timestamp";
    private static final String KEY_HISTORY_DATA = "key_history_data";

    // Food Types: 0: Poultry (Chicken/Turkey), 1: Red Meat (Beef/Pork), 2: Seafood/Fish, 3: Casserole/Pre-cooked
    private int selectedFoodIndex = 0;
    // Methods: 0: Refrigerator (Safest), 1: Submerged Cold Water (Active 30m change), 2: Microwave Emergency
    private int selectedMethodIndex = 0;

    private int weightGrams = 1000;
    private int thicknessCm = 3;
    private boolean waterRefreshAlarmEnabled = true;

    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private long remainingMillis = 0;
    private long timerEndTimestamp = 0;

    // UI elements
    private Button btnThemeToggle;
    private Button btnFoodPoultry;
    private Button btnFoodRedMeat;
    private Button btnFoodFish;
    private Button btnFoodCasserole;

    private Button btnMethodFridge;
    private Button btnMethodColdWater;
    private Button btnMethodMicrowave;

    private SeekBar seekWeight;
    private TextView tvWeightDisplay;
    private SeekBar seekThickness;
    private TextView tvThicknessDisplay;

    private Switch switchWaterAlarm;
    private TextView tvWaterAlarmNote;

    private TextView tvCalculatedEstimate;
    private TextView tvDangerZoneSafetyProtocol;

    private TextView tvTimerCountdown;
    private TextView tvTimerStatus;
    private Button btnStartTimer;
    private Button btnResetTimer;
    private Button btnShareProtocol;
    private Button btnCopyProtocol;

    private LinearLayout layoutHistoryContainer;
    private Button btnClearHistory;

    private SharedPreferences prefs;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int themeMode = sp.getInt(KEY_THEME_MODE, 0);

        int nightMode;
        if (themeMode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            nightMode = (sysNight == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        initViews();
        setupWindowAppearance();
        loadSavedState();
        updateFoodSelectionUI();
        updateMethodSelectionUI();
        recalculateThawTimes();
        renderHistory();
        checkActiveRunningTimer();
    }

    private void setupWindowAppearance() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController insetsController = getWindow().getInsetsController();
            if (insetsController != null) {
                if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
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

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnFoodPoultry = findViewById(R.id.btnFoodPoultry);
        btnFoodRedMeat = findViewById(R.id.btnFoodRedMeat);
        btnFoodFish = findViewById(R.id.btnFoodFish);
        btnFoodCasserole = findViewById(R.id.btnFoodCasserole);

        btnMethodFridge = findViewById(R.id.btnMethodFridge);
        btnMethodColdWater = findViewById(R.id.btnMethodColdWater);
        btnMethodMicrowave = findViewById(R.id.btnMethodMicrowave);

        seekWeight = findViewById(R.id.seekWeight);
        tvWeightDisplay = findViewById(R.id.tvWeightDisplay);
        seekThickness = findViewById(R.id.seekThickness);
        tvThicknessDisplay = findViewById(R.id.tvThicknessDisplay);

        switchWaterAlarm = findViewById(R.id.switchWaterAlarm);
        tvWaterAlarmNote = findViewById(R.id.tvWaterAlarmNote);

        tvCalculatedEstimate = findViewById(R.id.tvCalculatedEstimate);
        tvDangerZoneSafetyProtocol = findViewById(R.id.tvDangerZoneSafetyProtocol);

        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        tvTimerStatus = findViewById(R.id.tvTimerStatus);
        btnStartTimer = findViewById(R.id.btnStartTimer);
        btnResetTimer = findViewById(R.id.btnResetTimer);
        btnShareProtocol = findViewById(R.id.btnShareProtocol);
        btnCopyProtocol = findViewById(R.id.btnCopyProtocol);

        layoutHistoryContainer = findViewById(R.id.layoutHistoryContainer);
        btnClearHistory = findViewById(R.id.btnClearHistory);

        // Theme Toggle
        updateThemeToggleTitle();
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        // Food Selectors
        btnFoodPoultry.setOnClickListener(v -> selectFood(0, v));
        btnFoodRedMeat.setOnClickListener(v -> selectFood(1, v));
        btnFoodFish.setOnClickListener(v -> selectFood(2, v));
        btnFoodCasserole.setOnClickListener(v -> selectFood(3, v));

        // Method Selectors
        btnMethodFridge.setOnClickListener(v -> selectMethod(0, v));
        btnMethodColdWater.setOnClickListener(v -> selectMethod(1, v));
        btnMethodMicrowave.setOnClickListener(v -> selectMethod(2, v));

        // Weight Slider (50g to 5000g, steps of 50g -> progress 1 to 100)
        seekWeight.setMax(100);
        seekWeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 1) progress = 1;
                weightGrams = progress * 50;
                tvWeightDisplay.setText(String.format(Locale.getDefault(), "%d g (%.2f kg / %.2f lbs)",
                        weightGrams, weightGrams / 1000f, weightGrams * 0.00220462f));
                saveUserInputs();
                recalculateThawTimes();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });

        // Thickness Slider (1cm to 15cm)
        seekThickness.setMax(14); // 0 corresponds to 1cm
        seekThickness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thicknessCm = progress + 1;
                float inches = thicknessCm * 0.393701f;
                tvThicknessDisplay.setText(String.format(Locale.getDefault(), "%d cm (approx. %.1f in)", thicknessCm, inches));
                saveUserInputs();
                recalculateThawTimes();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });

        // Cold water 30-min alarm switch
        switchWaterAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            waterRefreshAlarmEnabled = isChecked;
            saveUserInputs();
        });

        // Timer buttons
        btnStartTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (isTimerRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });

        btnResetTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetTimer();
        });

        btnShareProtocol.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareProtocol();
        });

        btnCopyProtocol.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            copyProtocolToClipboard();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearHistory();
        });
    }

    private void updateThemeToggleTitle() {
        int mode = prefs.getInt(KEY_THEME_MODE, 0);
        if (mode == 1) {
            btnThemeToggle.setText("☀️ Light Mode");
        } else if (mode == 2) {
            btnThemeToggle.setText("🌙 Dark Mode");
        } else {
            btnThemeToggle.setText("⚙️ Auto Mode");
        }
    }

    private void cycleThemeMode() {
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void selectFood(int index, View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        selectedFoodIndex = index;
        updateFoodSelectionUI();
        saveUserInputs();
        recalculateThawTimes();
    }

    private void selectMethod(int index, View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        selectedMethodIndex = index;
        updateMethodSelectionUI();
        saveUserInputs();
        recalculateThawTimes();
    }

    private void updateFoodSelectionUI() {
        btnFoodPoultry.setAlpha(selectedFoodIndex == 0 ? 1.0f : 0.6f);
        btnFoodRedMeat.setAlpha(selectedFoodIndex == 1 ? 1.0f : 0.6f);
        btnFoodFish.setAlpha(selectedFoodIndex == 2 ? 1.0f : 0.6f);
        btnFoodCasserole.setAlpha(selectedFoodIndex == 3 ? 1.0f : 0.6f);
    }

    private void updateMethodSelectionUI() {
        btnMethodFridge.setAlpha(selectedMethodIndex == 0 ? 1.0f : 0.6f);
        btnMethodColdWater.setAlpha(selectedMethodIndex == 1 ? 1.0f : 0.6f);
        btnMethodMicrowave.setAlpha(selectedMethodIndex == 2 ? 1.0f : 0.6f);

        if (selectedMethodIndex == 1) {
            switchWaterAlarm.setVisibility(View.VISIBLE);
            tvWaterAlarmNote.setVisibility(View.VISIBLE);
        } else {
            switchWaterAlarm.setVisibility(View.GONE);
            tvWaterAlarmNote.setVisibility(View.GONE);
        }
    }

    private void recalculateThawTimes() {
        long calculatedSeconds = computeDefrostDurationSeconds();
        if (!isTimerRunning) {
            remainingMillis = calculatedSeconds * 1000L;
            updateCountdownText(remainingMillis);
            tvTimerStatus.setText("READY TO MONITOR");
        }

        long hours = calculatedSeconds / 3600;
        long minutes = (calculatedSeconds % 3600) / 60;
        long secs = calculatedSeconds % 60;

        StringBuilder timeSb = new StringBuilder();
        if (hours > 0) {
            timeSb.append(hours).append(" hrs ");
        }
        timeSb.append(minutes).append(" mins");
        if (secs > 0 && hours == 0) {
            timeSb.append(" ").append(secs).append(" secs");
        }

        tvCalculatedEstimate.setText(timeSb.toString());

        // Danger zone & USDA safety guidelines dynamic advisory
        StringBuilder advisory = new StringBuilder();
        advisory.append("🔬 USDA FOOD SAFETY DIRECTIVE:\n");
        if (selectedMethodIndex == 0) {
            advisory.append("• Safe Zone: Keep refrigerator temperature at or below 40°F (4.4°C).\n");
            advisory.append("• Safe to refreeze if defrosted completely in refrigerator without cooking.\n");
            advisory.append("• Ground meats/poultry safe for 1-2 days after thaw; red beef/pork safe for 3-5 days.");
        } else if (selectedMethodIndex == 1) {
            advisory.append("• Active Submersion: Food must be in leak-proof airtight packaging.\n");
            advisory.append("• Water must be exchanged EVERY 30 MINUTES to stay below Danger Zone (40°F–140°F).\n");
            advisory.append("• MUST be cooked immediately once thawed; DO NOT refreeze raw!");
        } else {
            advisory.append("• Microwave Emergency Defrost: Thawing is uneven; edges begin cooking immediately.\n");
            advisory.append("• Bacteria multiply rapidly in partially cooked areas.\n");
            advisory.append("• CRITICAL: Cook immediately following microwave defrosting!");
        }
        tvDangerZoneSafetyProtocol.setText(advisory.toString());
    }

    /**
     * Physics/Food-Science based thaw duration computation:
     * - Base fridge rate: ~5 hours per 500g (approx 24h per 5 lbs) scaled non-linearly by thickness
     * - Submerged Cold Water: ~30 mins per 500g
     * - Microwave Defrost: ~8 mins per 500g with standing rest time
     */
    private long computeDefrostDurationSeconds() {
        double weightKg = weightGrams / 1000.0;
        double thicknessFactor = Math.pow(thicknessCm / 2.5, 0.75); // standard cut is ~2.5cm

        double foodFactor;
        switch (selectedFoodIndex) {
            case 0: // Poultry
                foodFactor = 1.15;
                break;
            case 1: // Red Meat
                foodFactor = 1.00;
                break;
            case 2: // Seafood/Fish (delicate, thaws faster)
                foodFactor = 0.65;
                break;
            case 3: // Casserole / Prepared meal (dense)
                foodFactor = 1.25;
                break;
            default:
                foodFactor = 1.0;
                break;
        }

        double totalHours;
        if (selectedMethodIndex == 0) {
            // Refrigerator: ~10 hours per kg base * thickness adjustment
            totalHours = (weightKg * 10.0) * thicknessFactor * foodFactor;
            if (totalHours < 2.0) totalHours = 2.0; // Minimum chill equilibrium
        } else if (selectedMethodIndex == 1) {
            // Cold Water: ~1.0 hour per kg base
            totalHours = (weightKg * 1.0) * thicknessFactor * foodFactor;
            if (totalHours < 0.35) totalHours = 0.35; // Min 20 mins
        } else {
            // Microwave: ~0.25 hour (15 mins) per kg base
            totalHours = (weightKg * 0.25) * thicknessFactor * foodFactor;
            if (totalHours < 0.08) totalHours = 0.08; // Min 5 mins
        }

        return (long) (totalHours * 3600);
    }

    private void startTimer() {
        if (remainingMillis <= 0) {
            remainingMillis = computeDefrostDurationSeconds() * 1000L;
        }

        timerEndTimestamp = System.currentTimeMillis() + remainingMillis;
        isTimerRunning = true;
        prefs.edit()
                .putBoolean(KEY_TIMER_RUNNING, true)
                .putLong(KEY_END_TIMESTAMP, timerEndTimestamp)
                .putLong(KEY_REMAINING_MILLIS, remainingMillis)
                .apply();

        btnStartTimer.setText("PAUSE MONITOR");
        tvTimerStatus.setText("ACTIVE SENTINEL GUARD");

        startCountDown(remainingMillis);
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        prefs.edit()
                .putBoolean(KEY_TIMER_RUNNING, false)
                .putLong(KEY_REMAINING_MILLIS, remainingMillis)
                .apply();

        btnStartTimer.setText("RESUME MONITOR");
        tvTimerStatus.setText("PAUSED");
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        prefs.edit()
                .putBoolean(KEY_TIMER_RUNNING, false)
                .putLong(KEY_REMAINING_MILLIS, 0)
                .putLong(KEY_END_TIMESTAMP, 0)
                .apply();

        btnStartTimer.setText("START SENTINEL");
        tvTimerStatus.setText("RESET TO ESTIMATE");
        recalculateThawTimes();
    }

    private void startCountDown(long durationMillis) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            int waterHalfHourTracker = 0;

            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                updateCountdownText(millisUntilFinished);

                // Cold water reminder: every 30 minutes (1800s)
                if (selectedMethodIndex == 1 && waterRefreshAlarmEnabled) {
                    waterHalfHourTracker++;
                    if (waterHalfHourTracker >= 1800) {
                        waterHalfHourTracker = 0;
                        triggerWaterRefreshAlert();
                    }
                }
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                isTimerRunning = false;
                prefs.edit().putBoolean(KEY_TIMER_RUNNING, false).putLong(KEY_REMAINING_MILLIS, 0).apply();
                updateCountdownText(0);
                btnStartTimer.setText("START SENTINEL");
                tvTimerStatus.setText("THAW COMPLETE! COOK SAFELY NOW");
                triggerFinalAlarm();
                recordSessionHistory();
            }
        }.start();
    }

    private void updateCountdownText(long millis) {
        long totalSeconds = millis / 1000;
        long hrs = totalSeconds / 3600;
        long mins = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;

        String formatted = String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs);
        tvTimerCountdown.setText(formatted);
    }

    private void triggerWaterRefreshAlert() {
        playAlertSoundAndVibrate(300);
        Toast.makeText(this, "⚠️ SENTINEL ALERT: Change submerged cold water now!", Toast.LENGTH_LONG).show();
    }

    private void triggerFinalAlarm() {
        playAlertSoundAndVibrate(1500);
        Toast.makeText(this, "🛡️ THAW COMPLETED: Move food from thaw staging now!", Toast.LENGTH_LONG).show();
    }

    private void playAlertSoundAndVibrate(long durationMs) {
        try {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (alert == null) {
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), alert);
            if (r != null) {
                r.play();
            }
        } catch (Exception ignored) {
        }

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(durationMs);
            }
        }
    }

    private void recordSessionHistory() {
        String foodName = getFoodName(selectedFoodIndex);
        String methodName = getMethodName(selectedMethodIndex);
        String entry = String.format(Locale.getDefault(), "%s (%dg, %dcm) via %s - Thawed OK",
                foodName, weightGrams, thicknessCm, methodName);

        try {
            String historyJson = prefs.getString(KEY_HISTORY_DATA, "[]");
            JSONArray array = new JSONArray(historyJson);
            JSONObject item = new JSONObject();
            item.put("timestamp", System.currentTimeMillis());
            item.put("summary", entry);
            array.put(0, item);

            // Cap at 15 items
            while (array.length() > 15) {
                array.remove(array.length() - 1);
            }

            prefs.edit().putString(KEY_HISTORY_DATA, array.toString()).apply();
            renderHistory();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void renderHistory() {
        layoutHistoryContainer.removeAllViews();
        String historyJson = prefs.getString(KEY_HISTORY_DATA, "[]");
        try {
            JSONArray array = new JSONArray(historyJson);
            if (array.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("No recorded thaw sessions yet. Complete a cycle to log telemetry.");
                empty.setTextColor(getColor(R.color.m3_on_surface_variant));
                empty.setTextSize(13f);
                empty.setPadding(0, 16, 0, 16);
                layoutHistoryContainer.addView(empty);
                return;
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                TextView row = new TextView(this);
                row.setText("• " + obj.getString("summary"));
                row.setTextColor(getColor(R.color.m3_on_surface));
                row.setTextSize(13f);
                row.setPadding(8, 12, 8, 12);
                layoutHistoryContainer.addView(row);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void clearHistory() {
        prefs.edit().remove(KEY_HISTORY_DATA).apply();
        renderHistory();
        Toast.makeText(this, "History cleared.", Toast.LENGTH_SHORT).show();
    }

    private String getFoodName(int idx) {
        switch (idx) {
            case 0: return "Poultry";
            case 1: return "Red Meat";
            case 2: return "Fish / Seafood";
            case 3: return "Casserole";
            default: return "Food";
        }
    }

    private String getMethodName(int idx) {
        switch (idx) {
            case 0: return "Refrigerator (Safe Slow)";
            case 1: return "Cold Water Submersion";
            case 2: return "Microwave Defrost";
            default: return "Defrost";
        }
    }

    private String buildShareableText() {
        return "🛡️ [ThawGuard Defrost Sentinel Protocol]\n" +
                "Item: " + getFoodName(selectedFoodIndex) + "\n" +
                "Weight: " + weightGrams + "g (" + String.format(Locale.getDefault(), "%.2f lbs", weightGrams * 0.00220462f) + ")\n" +
                "Thickness: " + thicknessCm + " cm\n" +
                "Thawing Method: " + getMethodName(selectedMethodIndex) + "\n" +
                "Calculated Duration: " + tvCalculatedEstimate.getText() + "\n" +
                "Protocol Guideline:\n" + tvDangerZoneSafetyProtocol.getText().toString();
    }

    private void shareProtocol() {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, buildShareableText());
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Food Safety Thaw Protocol");
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Export Thaw Protocol"));
    }

    private void copyProtocolToClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("ThawGuard Protocol", buildShareableText());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Protocol copied to clipboard.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveUserInputs() {
        prefs.edit()
                .putInt(KEY_FOOD_TYPE, selectedFoodIndex)
                .putInt(KEY_METHOD, selectedMethodIndex)
                .putInt(KEY_WEIGHT_GRAMS, weightGrams)
                .putInt(KEY_THICKNESS_CM, thicknessCm)
                .putBoolean(KEY_COLD_WATER_ALARM, waterRefreshAlarmEnabled)
                .apply();
    }

    private void loadSavedState() {
        selectedFoodIndex = prefs.getInt(KEY_FOOD_TYPE, 0);
        selectedMethodIndex = prefs.getInt(KEY_METHOD, 0);
        weightGrams = prefs.getInt(KEY_WEIGHT_GRAMS, 1000);
        thicknessCm = prefs.getInt(KEY_THICKNESS_CM, 3);
        waterRefreshAlarmEnabled = prefs.getBoolean(KEY_COLD_WATER_ALARM, true);

        // Sync views with restored state
        seekWeight.setProgress(weightGrams / 50);
        tvWeightDisplay.setText(String.format(Locale.getDefault(), "%d g (%.2f kg / %.2f lbs)",
                weightGrams, weightGrams / 1000f, weightGrams * 0.00220462f));

        seekThickness.setProgress(thicknessCm - 1);
        tvThicknessDisplay.setText(String.format(Locale.getDefault(), "%d cm (approx. %.1f in)",
                thicknessCm, thicknessCm * 0.393701f));

        switchWaterAlarm.setChecked(waterRefreshAlarmEnabled);
    }

    private void checkActiveRunningTimer() {
        boolean wasRunning = prefs.getBoolean(KEY_TIMER_RUNNING, false);
        long endTs = prefs.getLong(KEY_END_TIMESTAMP, 0);
        long savedRemaining = prefs.getLong(KEY_REMAINING_MILLIS, 0);

        if (wasRunning && endTs > 0) {
            long diff = endTs - System.currentTimeMillis();
            if (diff > 0) {
                remainingMillis = diff;
                isTimerRunning = true;
                btnStartTimer.setText("PAUSE MONITOR");
                tvTimerStatus.setText("ACTIVE SENTINEL GUARD");
                startCountDown(remainingMillis);
            } else {
                remainingMillis = 0;
                isTimerRunning = false;
                prefs.edit().putBoolean(KEY_TIMER_RUNNING, false).apply();
                updateCountdownText(0);
                tvTimerStatus.setText("CYCLE ELAPSED WHILE AWAY");
            }
        } else if (savedRemaining > 0) {
            remainingMillis = savedRemaining;
            updateCountdownText(remainingMillis);
            tvTimerStatus.setText("PAUSED");
            btnStartTimer.setText("RESUME MONITOR");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    btnStartTimer.performClick();
                    return true;
                case KeyEvent.KEYCODE_SPACE:
                    if (isTimerRunning) {
                        pauseTimer();
                    } else {
                        startTimer();
                    }
                    return true;
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_EQUALS:
                case KeyEvent.KEYCODE_NUMPAD_ADD:
                    seekWeight.setProgress(Math.min(seekWeight.getMax(), seekWeight.getProgress() + 2));
                    return true;
                case KeyEvent.KEYCODE_MINUS:
                case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                    seekWeight.setProgress(Math.max(1, seekWeight.getProgress() - 2));
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserves active running state cleanly without reload across split-screen/freeform resizing
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}