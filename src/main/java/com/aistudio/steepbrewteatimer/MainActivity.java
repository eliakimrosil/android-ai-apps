package com.aistudio.steepbrewteatimer;

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
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "steep-brew-tea-timer_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_PROFILES_JSON = "pref_profiles_json";
    private static final String KEY_SELECTED_PROFILE = "pref_selected_profile";
    private static final String KEY_LOG_HISTORY = "pref_brew_history";

    // Presets Model
    public static class BrewProfile {
        String name;
        String category; // Tea or Coffee
        int baseTempC;
        int leafGrams;
        int waterMl;
        int[] infusionsSec; // Steeping curve in seconds
        String sensoryNotes;

        public BrewProfile(String name, String category, int baseTempC, int leafGrams, int waterMl, int[] infusionsSec, String sensoryNotes) {
            this.name = name;
            this.category = category;
            this.baseTempC = baseTempC;
            this.leafGrams = leafGrams;
            this.waterMl = waterMl;
            this.infusionsSec = infusionsSec;
            this.sensoryNotes = sensoryNotes;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("category", category);
            obj.put("baseTempC", baseTempC);
            obj.put("leafGrams", leafGrams);
            obj.put("waterMl", waterMl);
            JSONArray arr = new JSONArray();
            for (int s : infusionsSec) {
                arr.put(s);
            }
            obj.put("infusions", arr);
            obj.put("notes", sensoryNotes);
            return obj;
        }

        public static BrewProfile fromJson(JSONObject obj) throws JSONException {
            JSONArray arr = obj.getJSONArray("infusions");
            int[] inf = new int[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                inf[i] = arr.getInt(i);
            }
            return new BrewProfile(
                    obj.getString("name"),
                    obj.getString("category"),
                    obj.getInt("baseTempC"),
                    obj.getInt("leafGrams"),
                    obj.getInt("waterMl"),
                    inf,
                    obj.optString("notes", "")
            );
        }
    }

    private final ArrayList<BrewProfile> profileList = new ArrayList<>();
    private int currentProfileIndex = 0;
    private int currentInfusionIndex = 0; // 0-based index

    // Timer State
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private boolean isPaused = false;
    private long totalDurationMillis = 0;
    private long millisRemaining = 0;

    // Sensory & Hardware
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private SharedPreferences prefs;

    // Views
    private ScrollView mainScrollView;
    private Button btnThemeToggle;
    private TextView tvProfileName;
    private TextView tvProfileCategory;
    private TextView tvCurrentSteepOrdinal;
    private TextView tvInfusionProgressIndicator;
    private TextView tvTimerCountdown;
    private ProgressBar pbSteepProgress;
    private Button btnStartPause;
    private Button btnReset;
    private Button btnNextInfusion;
    private Button btnPrevInfusion;

    // Profile Tuning & Calibration Views
    private TextView tvWaterRatioValue;
    private TextView tvTempDisplay;
    private SeekBar sbWaterRatio;
    private SeekBar sbTemperature;
    private EditText etLeafWeight;
    private EditText etSensoryNotes;
    private Button btnSaveCustomProfile;
    private Button btnCopyTastingLog;
    private Button btnExportHistory;
    private Button btnClearHistory;
    private LinearLayout llPresetChipsContainer;
    private LinearLayout llInfusionCurveBar;
    private TextView tvTastingHistoryFeed;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = sp.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark
        int nightMode;
        if (themeMode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int sysNight = 0;
            try {
                sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {
            }
            nightMode = (sysNight == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and show when locked for dedicated kitchen/brew sentinel operation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        initHardwareFeedback();
        initDefaultProfilesIfNeeded();
        bindViews();
        setupThemeModeUi();
        loadActiveProfile();
        renderPresetChips();
        renderHistory();
        setupListeners();
    }

    private void initHardwareFeedback() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
        } catch (Exception e) {
            toneGenerator = null;
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setupThemeModeUi() {
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        String label = "Theme: Auto";
        if (themeMode == 1) {
            label = "Theme: Light";
        } else if (themeMode == 2) {
            label = "Theme: Dark";
        }
        btnThemeToggle.setText(label);

        // Dynamic Status Bar contrast
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (nightMode == Configuration.UI_MODE_NIGHT_NO) {
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
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void bindViews() {
        mainScrollView = findViewById(R.id.mainScrollView);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileCategory = findViewById(R.id.tvProfileCategory);
        tvCurrentSteepOrdinal = findViewById(R.id.tvCurrentSteepOrdinal);
        tvInfusionProgressIndicator = findViewById(R.id.tvInfusionProgressIndicator);
        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        pbSteepProgress = findViewById(R.id.pbSteepProgress);
        btnStartPause = findViewById(R.id.btnStartPause);
        btnReset = findViewById(R.id.btnReset);
        btnNextInfusion = findViewById(R.id.btnNextInfusion);
        btnPrevInfusion = findViewById(R.id.btnPrevInfusion);

        tvWaterRatioValue = findViewById(R.id.tvWaterRatioValue);
        tvTempDisplay = findViewById(R.id.tvTempDisplay);
        sbWaterRatio = findViewById(R.id.sbWaterRatio);
        sbTemperature = findViewById(R.id.sbTemperature);
        etLeafWeight = findViewById(R.id.etLeafWeight);
        etSensoryNotes = findViewById(R.id.etSensoryNotes);
        btnSaveCustomProfile = findViewById(R.id.btnSaveCustomProfile);
        btnCopyTastingLog = findViewById(R.id.btnCopyTastingLog);
        btnExportHistory = findViewById(R.id.btnExportHistory);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        llPresetChipsContainer = findViewById(R.id.llPresetChipsContainer);
        llInfusionCurveBar = findViewById(R.id.llInfusionCurveBar);
        tvTastingHistoryFeed = findViewById(R.id.tvTastingHistoryFeed);
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnStartPause.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleTimer();
        });

        btnReset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetCurrentSteep();
        });

        btnNextInfusion.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            advanceInfusion(1);
        });

        btnPrevInfusion.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            advanceInfusion(-1);
        });

        sbTemperature.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int temp = 50 + progress; // Range 50°C to 100°C
                tvTempDisplay.setText(temp + "°C (" + (int) (temp * 1.8 + 32) + "°F)");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbWaterRatio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int waterMl = 50 + (progress * 10); // 50ml to 550ml
                tvWaterRatioValue.setText(waterMl + " ml total liquor");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSaveCustomProfile.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveCurrentAsCustomPreset();
        });

        btnCopyTastingLog.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            copyActiveProfileToClipboard();
        });

        btnExportHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportBrewLogIntent();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearHistory();
        });
    }

    private void initDefaultProfilesIfNeeded() {
        String saved = prefs.getString(KEY_PROFILES_JSON, null);
        if (saved != null) {
            try {
                JSONArray arr = new JSONArray(saved);
                profileList.clear();
                for (int i = 0; i < arr.length(); i++) {
                    profileList.add(BrewProfile.fromJson(arr.getJSONObject(i)));
                }
                if (!profileList.isEmpty()) {
                    currentProfileIndex = prefs.getInt(KEY_SELECTED_PROFILE, 0);
                    if (currentProfileIndex >= profileList.size()) currentProfileIndex = 0;
                    return;
                }
            } catch (JSONException ignored) {
            }
        }

        // Bespoke Presets: Chinese Gongfu, Japanese Gyokuro, Wuyi Rock Oolong, Aeropress Specialty & V60
        profileList.clear();
        profileList.add(new BrewProfile("Gongfu Sheng Pu'er", "Raw Aged Tea", 98, 8, 120,
                new int[]{15, 20, 25, 30, 45, 60, 90, 120}, "Wild camphor, minerality, astringent honey finish"));
        profileList.add(new BrewProfile("Uji Gyokuro Imperial", "Shaded Green Tea", 60, 6, 80,
                new int[]{90, 40, 60, 90}, "Supreme savory umami, concentrated marine sweetness"));
        profileList.add(new BrewProfile("Wuyi Da Hong Pao", "Cliff Rock Oolong", 95, 7, 110,
                new int[]{20, 25, 30, 40, 60, 90, 120}, "Roasty charcoal aroma, orchid throat echo, mineral spine"));
        profileList.add(new BrewProfile("Specialty Pour-Over V60", "Single Origin Coffee", 93, 15, 250,
                new int[]{45, 60, 75}, "Bloom stage, first structural pour, final clarifying drawdown"));
        profileList.add(new BrewProfile("Aeropress Inverted Multi-Step", "Specialty Coffee", 88, 18, 200,
                new int[]{30, 60, 30}, "Agitation bloom, steep soak, 30s gentle uniform press"));
        profileList.add(new BrewProfile("First Flush Darjeeling", "Himalayan Black Tea", 85, 4, 200,
                new int[]{150, 180, 240}, "Crisp muscatel grape, spring floral zest, amber amber liquor"));

        persistProfiles();
    }

    private void persistProfiles() {
        try {
            JSONArray arr = new JSONArray();
            for (BrewProfile p : profileList) {
                arr.put(p.toJson());
            }
            prefs.edit().putString(KEY_PROFILES_JSON, arr.toString())
                    .putInt(KEY_SELECTED_PROFILE, currentProfileIndex)
                    .apply();
        } catch (JSONException ignored) {
        }
    }

    private void loadActiveProfile() {
        if (profileList.isEmpty()) return;
        if (currentProfileIndex >= profileList.size()) currentProfileIndex = 0;
        BrewProfile p = profileList.get(currentProfileIndex);

        tvProfileName.setText(p.name);
        tvProfileCategory.setText(p.category.toUpperCase(Locale.US));

        // Sync inputs
        int tempProgress = Math.max(0, Math.min(50, p.baseTempC - 50));
        sbTemperature.setProgress(tempProgress);
        tvTempDisplay.setText(p.baseTempC + "°C (" + (int) (p.baseTempC * 1.8 + 32) + "°F)");

        int waterProgress = Math.max(0, Math.min(50, (p.waterMl - 50) / 10));
        sbWaterRatio.setProgress(waterProgress);
        tvWaterRatioValue.setText(p.waterMl + " ml total liquor");

        etLeafWeight.setText(String.valueOf(p.leafGrams));
        etSensoryNotes.setText(p.sensoryNotes);

        currentInfusionIndex = 0;
        setupInfusionStep();
        renderInfusionCurveBar();
    }

    private void setupInfusionStep() {
        BrewProfile p = profileList.get(currentProfileIndex);
        if (currentInfusionIndex < 0) currentInfusionIndex = 0;
        if (currentInfusionIndex >= p.infusionsSec.length) {
            currentInfusionIndex = p.infusionsSec.length - 1;
        }

        int targetSec = p.infusionsSec[currentInfusionIndex];
        totalDurationMillis = targetSec * 1000L;
        millisRemaining = totalDurationMillis;

        tvCurrentSteepOrdinal.setText("Steep #" + (currentInfusionIndex + 1));
        tvInfusionProgressIndicator.setText((currentInfusionIndex + 1) + " of " + p.infusionsSec.length + " Planned Passes");

        updateTimerDisplay(millisRemaining);
        pbSteepProgress.setProgress(1000);
        btnStartPause.setText("Start Sentinel");

        btnPrevInfusion.setEnabled(currentInfusionIndex > 0);
        btnNextInfusion.setEnabled(currentInfusionIndex < p.infusionsSec.length - 1);
    }

    private void renderInfusionCurveBar() {
        llInfusionCurveBar.removeAllViews();
        BrewProfile p = profileList.get(currentProfileIndex);

        for (int i = 0; i < p.infusionsSec.length; i++) {
            final int index = i;
            TextView stepBox = new TextView(this);
            stepBox.setPadding(16, 12, 16, 12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(6, 4, 6, 4);
            stepBox.setLayoutParams(lp);

            boolean isCurrent = (i == currentInfusionIndex);
            stepBox.setText("#" + (i + 1) + " (" + p.infusionsSec[i] + "s)");
            stepBox.setTextSize(12);

            if (isCurrent) {
                stepBox.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
                stepBox.setTextColor(getColor(android.R.color.holo_orange_dark));
                stepBox.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                stepBox.setBackgroundResource(android.R.drawable.btn_default_small);
                stepBox.setTextColor(getColor(android.R.color.secondary_text_dark));
            }

            stepBox.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (isTimerRunning) {
                    stopTimer();
                }
                currentInfusionIndex = index;
                setupInfusionStep();
                renderInfusionCurveBar();
            });

            llInfusionCurveBar.addView(stepBox);
        }
    }

    private void renderPresetChips() {
        llPresetChipsContainer.removeAllViews();
        for (int i = 0; i < profileList.size(); i++) {
            final int idx = i;
            BrewProfile p = profileList.get(i);
            Button chip = new Button(this);
            chip.setText(p.name);
            chip.setAllCaps(false);
            chip.setTextSize(13);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(8, 0, 8, 0);
            chip.setLayoutParams(lp);

            if (i == currentProfileIndex) {
                chip.setBackgroundResource(android.R.drawable.btn_default);
                chip.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                chip.setBackgroundResource(android.R.drawable.btn_default_small);
            }

            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (isTimerRunning) {
                    stopTimer();
                }
                currentProfileIndex = idx;
                prefs.edit().putInt(KEY_SELECTED_PROFILE, currentProfileIndex).apply();
                loadActiveProfile();
                renderPresetChips();
            });

            llPresetChipsContainer.addView(chip);
        }
    }

    private void toggleTimer() {
        if (isTimerRunning) {
            pauseTimer();
        } else {
            startTimer(millisRemaining);
        }
    }

    private void startTimer(long duration) {
        if (duration <= 0) {
            setupInfusionStep();
            duration = totalDurationMillis;
        }

        isTimerRunning = true;
        isPaused = false;
        btnStartPause.setText("Pause Sentinel");

        countDownTimer = new CountDownTimer(duration, 50) {
            @Override
            public void onTick(long millisUntilFinished) {
                millisRemaining = millisUntilFinished;
                updateTimerDisplay(millisRemaining);
                if (totalDurationMillis > 0) {
                    int progress = (int) ((millisUntilFinished * 1000) / totalDurationMillis);
                    pbSteepProgress.setProgress(progress);
                }
            }

            @Override
            public void onFinish() {
                millisRemaining = 0;
                updateTimerDisplay(0);
                pbSteepProgress.setProgress(0);
                isTimerRunning = false;
                btnStartPause.setText("Steep Complete");

                triggerAlarmSequence();
                logCompletedBrew();

                // Auto-prompt advance
                BrewProfile p = profileList.get(currentProfileIndex);
                if (currentInfusionIndex < p.infusionsSec.length - 1) {
                    Toast.makeText(MainActivity.this, "Steep #" + (currentInfusionIndex + 1) + " extracted! Ready for next infusion.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Final leaf pass exhausted. Session complete!", Toast.LENGTH_LONG).show();
                }
            }
        }.start();
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        isPaused = true;
        btnStartPause.setText("Resume Steep");
    }

    private void stopTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        isPaused = false;
    }

    private void resetCurrentSteep() {
        stopTimer();
        setupInfusionStep();
    }

    private void advanceInfusion(int delta) {
        stopTimer();
        BrewProfile p = profileList.get(currentProfileIndex);
        int target = currentInfusionIndex + delta;
        if (target >= 0 && target < p.infusionsSec.length) {
            currentInfusionIndex = target;
            setupInfusionStep();
            renderInfusionCurveBar();
        }
    }

    private void updateTimerDisplay(long millis) {
        long totalSeconds = (millis + 999) / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        tvTimerCountdown.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
    }

    private void triggerAlarmSequence() {
        // High-pitched acoustic bell + rhythmic haptic cadence
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 1200);
            }
        } catch (Exception ignored) {
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 150, 250, 150, 400}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 250, 150, 250, 150, 400}, -1);
            }
        }
    }

    private void logCompletedBrew() {
        BrewProfile p = profileList.get(currentProfileIndex);
        int steepNumber = currentInfusionIndex + 1;
        int durationSec = p.infusionsSec[currentInfusionIndex];
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

        String logEntry = String.format(Locale.US, "[%s] %s (Pass #%d, %ds @ %d°C) | Ratio: %dg leaf / %dml",
                timestamp, p.name, steepNumber, durationSec, p.baseTempC, p.leafGrams, p.waterMl);

        String currentHistory = prefs.getString(KEY_LOG_HISTORY, "");
        String updatedHistory = logEntry + "\n" + currentHistory;
        prefs.edit().putString(KEY_LOG_HISTORY, updatedHistory).apply();

        renderHistory();
    }

    private void renderHistory() {
        String history = prefs.getString(KEY_LOG_HISTORY, "");
        if (history.trim().isEmpty()) {
            tvTastingHistoryFeed.setText("No infusions completed in this session yet. Launch a steep sentinel above.");
        } else {
            tvTastingHistoryFeed.setText(history.trim());
        }
    }

    private void clearHistory() {
        prefs.edit().remove(KEY_LOG_HISTORY).apply();
        renderHistory();
        Toast.makeText(this, "Extraction history cleared", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentAsCustomPreset() {
        String name = etSensoryNotes.getText().toString().trim();
        if (name.isEmpty()) {
            name = "Custom Extraction " + (profileList.size() + 1);
        } else if (name.length() > 24) {
            name = name.substring(0, 24);
        }

        int temp = 50 + sbTemperature.getProgress();
        int water = 50 + (sbWaterRatio.getProgress() * 10);
        int grams = 6;
        try {
            grams = Integer.parseInt(etLeafWeight.getText().toString().trim());
        } catch (NumberFormatException ignored) {
        }

        BrewProfile active = profileList.get(currentProfileIndex);
        int[] curves = active.infusionsSec.clone();

        BrewProfile newProfile = new BrewProfile(
                name,
                "Custom Protocol",
                temp,
                grams,
                water,
                curves,
                etSensoryNotes.getText().toString().trim()
        );

        profileList.add(newProfile);
        currentProfileIndex = profileList.size() - 1;
        persistProfiles();
        renderPresetChips();
        loadActiveProfile();

        Toast.makeText(this, "Saved new profile: " + name, Toast.LENGTH_SHORT).show();
    }

    private void copyActiveProfileToClipboard() {
        BrewProfile p = profileList.get(currentProfileIndex);
        StringBuilder sb = new StringBuilder();
        sb.append("=== STEEPPULSE BREW MANIFEST ===\n");
        sb.append("Protocol: ").append(p.name).append(" (").append(p.category).append(")\n");
        sb.append("Water Temp: ").append(p.baseTempC).append("°C\n");
        sb.append("Dose / Liquor: ").append(p.leafGrams).append("g / ").append(p.waterMl).append("ml\n");
        sb.append("Infusion Sequence: ");
        for (int i = 0; i < p.infusionsSec.length; i++) {
            sb.append("#").append(i + 1).append(": ").append(p.infusionsSec[i]).append("s ");
        }
        sb.append("\nTasting Notes: ").append(p.sensoryNotes).append("\n");

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("SteepPulse Profile", sb.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Tasting manifest copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportBrewLogIntent() {
        String history = prefs.getString(KEY_LOG_HISTORY, "No infusions recorded.");
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "STEEPPULSE EXTRACTION TELEMETRY:\n\n" + history);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Export Steep Log");
        startActivity(shareIntent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_SPACE:
                    btnStartPause.performClick();
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    btnNextInfusion.performClick();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (isTimerRunning) {
                        pauseTimer();
                        Toast.makeText(this, "Sentinel paused", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_EQUALS:
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_NUMPAD_ADD:
                    // Increment 5s on current timer if not running
                    if (!isTimerRunning) {
                        millisRemaining += 5000;
                        totalDurationMillis += 5000;
                        updateTimerDisplay(millisRemaining);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_MINUS:
                case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                    if (!isTimerRunning && millisRemaining > 5000) {
                        millisRemaining -= 5000;
                        totalDurationMillis -= 5000;
                        updateTimerDisplay(millisRemaining);
                        return true;
                    }
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Retain view tree state cleanly on freeform window resizing without layout reset
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}