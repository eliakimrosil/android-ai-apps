package com.aistudio.workoutprogramtracke;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "workout-program-tracker_prefs";
    private static final String KEY_HISTORY = "workout_history_json";
    private static final String KEY_SAVED_PROGRAM = "selected_program_idx";
    private static final String KEY_SAVED_REST_SECONDS = "saved_rest_seconds";
    private static final String KEY_WORKOUT_IN_PROGRESS = "in_progress_json";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0=Auto, 1=Dark, 2=Light

    // Programs & Exercises
    private static final String[] PROGRAMS = {
            "Push/Pull/Legs (PPL)",
            "Upper / Lower Split",
            "5x5 Classic Strength",
            "Custom Full Body"
    };

    private static final String[][] PROGRAM_EXERCISES = {
            // PPL
            {"Barbell Bench Press", "Overhead Dumbbell Press", "Incline Dumbbell Flyes", "Tricep Pushdown", "Lateral Raises"},
            // Upper/Lower
            {"Barbell Squat", "Romanian Deadlift", "Pull-ups / Lat Pulldown", "Barbell Rows", "Standing Calf Raises"},
            // 5x5 Strength
            {"Back Squat (5x5)", "Barbell Bench (5x5)", "Deadlift (1x5)", "Barbell Overhead Press (5x5)", "Barbell Rows (5x5)"},
            // Custom
            {"Custom Compound 1", "Custom Isolation 1", "Custom Core 1", "Custom Cardio / Finisher"}
    };

    // Material 3 UI Elements
    private Button btnThemeToggle;
    private Button btnChipPPL;
    private Button btnChipUpperLower;
    private Button btnChip5x5;
    private Button btnChipCustom;
    private Button[] programChips;

    private Spinner spinnerProgram;
    private Spinner spinnerExercise;
    private EditText etWeight;
    private EditText etReps;
    private Button btnLogSet;
    private Button btnClearCurrentSets;
    private Button btnFinishWorkout;

    // Steppers
    private Button btnWeightMinus5;
    private Button btnWeightMinus25;
    private Button btnWeightPlus25;
    private Button btnWeightPlus5;

    private Button btnRepsMinus5;
    private Button btnRepsMinus1;
    private Button btnRepsPlus1;
    private Button btnRepsPlus5;

    // Rest Timer
    private TextView tvTimerCountdown;
    private ProgressBar pbTimer;
    private Button btnTimer60;
    private Button btnTimer90;
    private Button btnTimer120;
    private Button btnTimerReset;

    // Stats
    private TextView tvTotalTonnage;
    private TextView tvTotalSets;
    private TextView tvProgressiveCue;
    private LinearLayout layoutCurrentSetsContainer;
    private LinearLayout layoutHistoryContainer;

    private Button btnExportHistory;
    private Button btnCopySummary;
    private Button btnClearHistory;

    // State Variables
    private SharedPreferences prefs;
    private CountDownTimer restTimer;
    private long restTimeTotalMs = 90000;
    private long restTimeRemainingMs = 0;
    private boolean isTimerRunning = false;

    private ToneGenerator toneGenerator;
    private Vibrator vibrator;

    private static class WorkoutSet {
        String exercise;
        double weight;
        int reps;
        long timestamp;

        WorkoutSet(String exercise, double weight, int reps, long timestamp) {
            this.exercise = exercise;
            this.weight = weight;
            this.reps = reps;
            this.timestamp = timestamp;
        }

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("exercise", exercise);
            obj.put("weight", weight);
            obj.put("reps", reps);
            obj.put("timestamp", timestamp);
            return obj;
        }

        static WorkoutSet fromJson(JSONObject obj) {
            return new WorkoutSet(
                    obj.optString("exercise", "Lift"),
                    obj.optDouble("weight", 0.0),
                    obj.optInt("reps", 0),
                    obj.optLong("timestamp", System.currentTimeMillis())
            );
        }
    }

    private static class WorkoutLog {
        String program;
        long date;
        double totalVolume;
        int totalSetsCount;
        List<WorkoutSet> sets = new ArrayList<>();

        WorkoutLog(String program, long date) {
            this.program = program;
            this.date = date;
        }

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("program", program);
            obj.put("date", date);
            obj.put("totalVolume", totalVolume);
            obj.put("totalSetsCount", totalSetsCount);
            JSONArray arr = new JSONArray();
            for (WorkoutSet s : sets) {
                arr.put(s.toJson());
            }
            obj.put("sets", arr);
            return obj;
        }

        static WorkoutLog fromJson(JSONObject obj) {
            WorkoutLog log = new WorkoutLog(
                    obj.optString("program", "Custom"),
                    obj.optLong("date", System.currentTimeMillis())
            );
            log.totalVolume = obj.optDouble("totalVolume", 0.0);
            log.totalSetsCount = obj.optInt("totalSetsCount", 0);
            JSONArray arr = obj.optJSONArray("sets");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject sObj = arr.optJSONObject(i);
                    if (sObj != null) {
                        log.sets.add(WorkoutSet.fromJson(sObj));
                    }
                }
            }
            return log;
        }
    }

    private final List<WorkoutSet> currentSessionSets = new ArrayList<>();
    private final List<WorkoutLog> completedHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        applyInitialTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
        } catch (Exception ignored) {
            toneGenerator = null;
        }

        initViews();
        setupSpinnersAndChips();
        loadHistory();
        loadInProgressWorkout();
        setupListeners();
        updateCurrentSessionUI();
        updateVaultStats();
    }

    private void applyInitialTheme() {
        int mode = prefs.getInt(KEY_THEME_MODE, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager ui = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            if (ui != null) {
                if (mode == 1) {
                    ui.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
                } else if (mode == 2) {
                    ui.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
                } else {
                    ui.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
                }
            }
        }
    }

    private void toggleTheme() {
        int currentMode = prefs.getInt(KEY_THEME_MODE, 0);
        int nextMode = (currentMode + 1) % 3; // 0=Auto -> 1=Dark -> 2=Light
        prefs.edit().putInt(KEY_THEME_MODE, nextMode).apply();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager ui = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            if (ui != null) {
                int nightMode = UiModeManager.MODE_NIGHT_AUTO;
                String label = "System Default";
                if (nextMode == 1) {
                    nightMode = UiModeManager.MODE_NIGHT_YES;
                    label = "Dark OLED";
                } else if (nextMode == 2) {
                    nightMode = UiModeManager.MODE_NIGHT_NO;
                    label = "Light Expressive";
                }
                ui.setApplicationNightMode(nightMode);
                updateThemeButtonLabel(nextMode);
                Toast.makeText(this, "Theme switched: " + label, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        recreate();
    }

    private void updateThemeButtonLabel(int mode) {
        if (btnThemeToggle != null) {
            if (mode == 1) {
                btnThemeToggle.setText("🌙 Dark");
            } else if (mode == 2) {
                btnThemeToggle.setText("☀️ Light");
            } else {
                btnThemeToggle.setText("🌓 Auto");
            }
        }
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        int savedMode = prefs.getInt(KEY_THEME_MODE, 0);
        updateThemeButtonLabel(savedMode);

        btnChipPPL = findViewById(R.id.btnChipPPL);
        btnChipUpperLower = findViewById(R.id.btnChipUpperLower);
        btnChip5x5 = findViewById(R.id.btnChip5x5);
        btnChipCustom = findViewById(R.id.btnChipCustom);
        programChips = new Button[]{btnChipPPL, btnChipUpperLower, btnChip5x5, btnChipCustom};

        spinnerProgram = findViewById(R.id.spinnerProgram);
        spinnerExercise = findViewById(R.id.spinnerExercise);
        etWeight = findViewById(R.id.etWeight);
        etReps = findViewById(R.id.etReps);
        btnLogSet = findViewById(R.id.btnLogSet);
        btnClearCurrentSets = findViewById(R.id.btnClearCurrentSets);
        btnFinishWorkout = findViewById(R.id.btnFinishWorkout);

        btnWeightMinus5 = findViewById(R.id.btnWeightMinus5);
        btnWeightMinus25 = findViewById(R.id.btnWeightMinus25);
        btnWeightPlus25 = findViewById(R.id.btnWeightPlus25);
        btnWeightPlus5 = findViewById(R.id.btnWeightPlus5);

        btnRepsMinus5 = findViewById(R.id.btnRepsMinus5);
        btnRepsMinus1 = findViewById(R.id.btnRepsMinus1);
        btnRepsPlus1 = findViewById(R.id.btnRepsPlus1);
        btnRepsPlus5 = findViewById(R.id.btnRepsPlus5);

        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        pbTimer = findViewById(R.id.pbTimer);
        btnTimer60 = findViewById(R.id.btnTimer60);
        btnTimer90 = findViewById(R.id.btnTimer90);
        btnTimer120 = findViewById(R.id.btnTimer120);
        btnTimerReset = findViewById(R.id.btnTimerReset);

        tvTotalTonnage = findViewById(R.id.tvTotalTonnage);
        tvTotalSets = findViewById(R.id.tvTotalSets);
        tvProgressiveCue = findViewById(R.id.tvProgressiveCue);
        layoutCurrentSetsContainer = findViewById(R.id.layoutCurrentSetsContainer);
        layoutHistoryContainer = findViewById(R.id.layoutHistoryContainer);

        btnExportHistory = findViewById(R.id.btnExportHistory);
        btnCopySummary = findViewById(R.id.btnCopySummary);
        btnClearHistory = findViewById(R.id.btnClearHistory);

        restTimeTotalMs = prefs.getInt(KEY_SAVED_REST_SECONDS, 90) * 1000L;
        tvTimerCountdown.setText(formatTime(restTimeTotalMs));
    }

    private void setupSpinnersAndChips() {
        ArrayAdapter<String> programAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                PROGRAMS
        );
        programAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerProgram != null) {
            spinnerProgram.setAdapter(programAdapter);
        }

        int savedProgramIdx = prefs.getInt(KEY_SAVED_PROGRAM, 0);
        if (savedProgramIdx < 0 || savedProgramIdx >= PROGRAMS.length) {
            savedProgramIdx = 0;
        }
        selectProgram(savedProgramIdx, false);
    }

    private void selectProgram(int index, boolean userTriggered) {
        if (index < 0 || index >= PROGRAMS.length) return;
        prefs.edit().putInt(KEY_SAVED_PROGRAM, index).apply();
        if (spinnerProgram != null) {
            spinnerProgram.setSelection(index);
        }
        updateChipStyles(index);
        updateExerciseSpinner(index);
        updateProgressiveOverloadCue();
        if (userTriggered) {
            Toast.makeText(this, "Active Split: " + PROGRAMS[index], Toast.LENGTH_SHORT).show();
        }
    }

    private void updateChipStyles(int selectedIndex) {
        for (int i = 0; i < programChips.length; i++) {
            if (programChips[i] == null) continue;
            if (i == selectedIndex) {
                programChips[i].setBackgroundResource(R.drawable.chip_active);
                programChips[i].setTextColor(Color.parseColor("#381E72"));
                programChips[i].setTypeface(null, Typeface.BOLD);
            } else {
                programChips[i].setBackgroundResource(R.drawable.chip_inactive);
                programChips[i].setTextColor(Color.parseColor("#E6E1E5"));
                programChips[i].setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void updateExerciseSpinner(int programIndex) {
        if (programIndex < 0 || programIndex >= PROGRAM_EXERCISES.length) return;
        String[] exercises = PROGRAM_EXERCISES[programIndex];
        ArrayAdapter<String> exerciseAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                exercises
        );
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerExercise != null) {
            spinnerExercise.setAdapter(exerciseAdapter);

            spinnerExercise.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    updateProgressiveOverloadCue();
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {}
            });
        }
    }

    private void adjustWeight(double delta) {
        String str = etWeight.getText().toString().trim();
        double current = 0.0;
        try {
            if (!TextUtils.isEmpty(str)) {
                current = Double.parseDouble(str);
            }
        } catch (NumberFormatException ignored) {}
        current = Math.max(0.0, current + delta);
        etWeight.setText(String.format(Locale.US, "%.1f", current));
        etWeight.setSelection(etWeight.getText().length());
    }

    private void adjustReps(int delta) {
        String str = etReps.getText().toString().trim();
        int current = 0;
        try {
            if (!TextUtils.isEmpty(str)) {
                current = Integer.parseInt(str);
            }
        } catch (NumberFormatException ignored) {}
        current = Math.max(1, current + delta);
        etReps.setText(String.valueOf(current));
        etReps.setSelection(etReps.getText().length());
    }

    private void setupListeners() {
        if (btnThemeToggle != null) {
            btnThemeToggle.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                toggleTheme();
            });
        }

        // Material 3 Chip Selection Listeners
        for (int i = 0; i < programChips.length; i++) {
            final int idx = i;
            if (programChips[i] != null) {
                programChips[i].setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    selectProgram(idx, true);
                });
            }
        }

        // Weight Numeric Steppers
        if (btnWeightMinus5 != null) btnWeightMinus5.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); adjustWeight(-5.0); });
        if (btnWeightMinus25 != null) btnWeightMinus25.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); adjustWeight(-2.5); });
        if (btnWeightPlus25 != null) btnWeightPlus25.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); adjustWeight(2.5); });
        if (btnWeightPlus5 != null) btnWeightPlus5.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); adjustWeight(5.0); });

        // Reps Numeric Steppers
        if (btnRepsMinus5 != null) btnRepsMinus5.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); adjustReps(-5); });
        if (btnRepsMinus1 != null) btnRepsMinus1.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); adjustReps(-1); });
        if (btnRepsPlus1 != null) btnRepsPlus1.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); adjustReps(1); });
        if (btnRepsPlus5 != null) btnRepsPlus5.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); adjustReps(5); });

        if (btnLogSet != null) {
            btnLogSet.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                handleLogSet();
            });
        }

        if (btnClearCurrentSets != null) {
            btnClearCurrentSets.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (currentSessionSets.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Active set list is already empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentSessionSets.clear();
                saveInProgressWorkout();
                updateCurrentSessionUI();
                Toast.makeText(MainActivity.this, "Current workout reset", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnFinishWorkout != null) {
            btnFinishWorkout.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                handleFinishWorkout();
            });
        }

        if (btnTimer60 != null) btnTimer60.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); startRestTimer(60); });
        if (btnTimer90 != null) btnTimer90.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); startRestTimer(90); });
        if (btnTimer120 != null) btnTimer120.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); startRestTimer(120); });
        if (btnTimerReset != null) btnTimerReset.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); resetRestTimer(); });

        if (btnExportHistory != null) btnExportHistory.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); shareHistory(); });
        if (btnCopySummary != null) btnCopySummary.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); copySummaryToClipboard(); });

        if (btnClearHistory != null) {
            btnClearHistory.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (completedHistory.isEmpty()) {
                    Toast.makeText(MainActivity.this, "No workout logs in Vault to clear", Toast.LENGTH_SHORT).show();
                    return;
                }
                completedHistory.clear();
                prefs.edit().remove(KEY_HISTORY).apply();
                renderHistoryLogs();
                updateVaultStats();
                updateProgressiveOverloadCue();
                Toast.makeText(MainActivity.this, "History vault wiped", Toast.LENGTH_SHORT).show();
            });
        }
    }

    // Desktop Physical Keyboard Shortcuts
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            boolean isTyping = (etWeight != null && etWeight.hasFocus()) || (etReps != null && etReps.hasFocus());

            // Enter key logs set
            if ((keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && !isTyping) {
                handleLogSet();
                return true;
            }
            // Spacebar starts/resets timer
            if (keyCode == KeyEvent.KEYCODE_SPACE && !isTyping) {
                if (isTimerRunning) {
                    resetRestTimer();
                } else {
                    int sec = prefs.getInt(KEY_SAVED_REST_SECONDS, 90);
                    startRestTimer(sec);
                }
                return true;
            }
            // '+' or '=' adds 2.5kg
            if ((keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) && !isTyping) {
                adjustWeight(2.5);
                return true;
            }
            // '-' subtracts 2.5kg
            if (keyCode == KeyEvent.KEYCODE_MINUS && !isTyping) {
                adjustWeight(-2.5);
                return true;
            }
            // '[' decreases reps
            if (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET && !isTyping) {
                adjustReps(-1);
                return true;
            }
            // ']' increases reps
            if (keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET && !isTyping) {
                adjustReps(1);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Seamless desktop window resizing without state reset
        updateCurrentSessionUI();
        renderHistoryLogs();
        updateVaultStats();
    }

    private void handleLogSet() {
        String weightStr = etWeight.getText().toString().trim();
        String repsStr = etReps.getText().toString().trim();

        if (TextUtils.isEmpty(weightStr) || TextUtils.isEmpty(repsStr)) {
            Toast.makeText(this, "Enter both weight and reps", Toast.LENGTH_SHORT).show();
            return;
        }

        double weight;
        int reps;
        try {
            weight = Double.parseDouble(weightStr);
            reps = Integer.parseInt(repsStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid numeric input", Toast.LENGTH_SHORT).show();
            return;
        }

        if (weight < 0 || reps <= 0) {
            Toast.makeText(this, "Weight must be >= 0 and Reps > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        String exercise = (spinnerExercise != null && spinnerExercise.getSelectedItem() != null)
                ? spinnerExercise.getSelectedItem().toString()
                : "Exercise";

        WorkoutSet set = new WorkoutSet(exercise, weight, reps, System.currentTimeMillis());
        currentSessionSets.add(set);
        saveInProgressWorkout();
        updateCurrentSessionUI();

        int savedSeconds = prefs.getInt(KEY_SAVED_REST_SECONDS, 90);
        startRestTimer(savedSeconds);

        Toast.makeText(this, "Set logged! Rest timer ticking...", Toast.LENGTH_SHORT).show();
    }

    private void handleFinishWorkout() {
        if (currentSessionSets.isEmpty()) {
            Toast.makeText(this, "No sets logged in current workout!", Toast.LENGTH_SHORT).show();
            return;
        }

        int pIdx = (spinnerProgram != null) ? spinnerProgram.getSelectedItemPosition() : prefs.getInt(KEY_SAVED_PROGRAM, 0);
        if (pIdx < 0 || pIdx >= PROGRAMS.length) pIdx = 0;
        String programName = PROGRAMS[pIdx];
        WorkoutLog log = new WorkoutLog(programName, System.currentTimeMillis());

        double totalVol = 0.0;
        for (WorkoutSet s : currentSessionSets) {
            totalVol += (s.weight * s.reps);
            log.sets.add(s);
        }
        log.totalVolume = totalVol;
        log.totalSetsCount = currentSessionSets.size();

        completedHistory.add(0, log);
        saveHistory();

        currentSessionSets.clear();
        saveInProgressWorkout();

        resetRestTimer();
        updateCurrentSessionUI();
        renderHistoryLogs();
        updateVaultStats();
        updateProgressiveOverloadCue();

        Toast.makeText(this, "Workout saved to Vault! Great work!", Toast.LENGTH_LONG).show();
    }

    private void startRestTimer(int seconds) {
        if (restTimer != null) {
            restTimer.cancel();
        }

        prefs.edit().putInt(KEY_SAVED_REST_SECONDS, seconds).apply();
        restTimeTotalMs = seconds * 1000L;
        restTimeRemainingMs = restTimeTotalMs;
        if (pbTimer != null) {
            pbTimer.setMax((int) restTimeTotalMs);
            pbTimer.setProgress((int) restTimeTotalMs);
        }
        isTimerRunning = true;

        restTimer = new CountDownTimer(restTimeTotalMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                restTimeRemainingMs = millisUntilFinished;
                if (tvTimerCountdown != null) {
                    tvTimerCountdown.setText(formatTime(millisUntilFinished));
                }
                if (pbTimer != null) {
                    pbTimer.setProgress((int) millisUntilFinished);
                }
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                if (tvTimerCountdown != null) {
                    tvTimerCountdown.setText("00:00");
                }
                if (pbTimer != null) {
                    pbTimer.setProgress(0);
                }
                triggerRestAlarm();
            }
        }.start();
    }

    private void resetRestTimer() {
        if (restTimer != null) {
            restTimer.cancel();
            restTimer = null;
        }
        isTimerRunning = false;
        int seconds = prefs.getInt(KEY_SAVED_REST_SECONDS, 90);
        restTimeTotalMs = seconds * 1000L;
        if (tvTimerCountdown != null) {
            tvTimerCountdown.setText(formatTime(restTimeTotalMs));
        }
        if (pbTimer != null) {
            pbTimer.setProgress(pbTimer.getMax());
        }
    }

    private void triggerRestAlarm() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 400}, -1));
            } else {
                vibrator.vibrate(500);
            }
        }

        if (toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 400);
            } catch (Exception ignored) {}
        }

        Toast.makeText(this, "Rest Finished! Ready for next set!", Toast.LENGTH_LONG).show();
    }

    private String formatTime(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void updateCurrentSessionUI() {
        if (layoutCurrentSetsContainer == null) return;
        layoutCurrentSetsContainer.removeAllViews();
        if (currentSessionSets.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No sets logged yet. Select exercise, enter weight & reps above.");
            emptyTv.setTextColor(Color.parseColor("#CAC4D0"));
            emptyTv.setTextSize(13);
            emptyTv.setPadding(8, 16, 8, 16);
            layoutCurrentSetsContainer.addView(emptyTv);
            return;
        }

        int setNumber = 1;
        for (int i = 0; i < currentSessionSets.size(); i++) {
            WorkoutSet set = currentSessionSets.get(i);
            final int index = i;

            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(18, 14, 18, 14);
            itemRow.setBackgroundResource(R.drawable.card_m3_high);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 4, 0, 8);
            itemRow.setLayoutParams(rowParams);

            TextView tvInfo = new TextView(this);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            tvInfo.setLayoutParams(textParams);
            tvInfo.setText(String.format(Locale.US, "Set #%d: %s\n%.1f kg × %d reps  (Vol: %.0f kg)",
                    setNumber++, set.exercise, set.weight, set.reps, (set.weight * set.reps)));
            tvInfo.setTextColor(Color.parseColor("#E6E1E5"));
            tvInfo.setTextSize(13);
            tvInfo.setTypeface(Typeface.DEFAULT_BOLD);
            tvInfo.setLineSpacing(2.0f, 1.0f);

            Button btnRemove = new Button(this);
            btnRemove.setText("✕");
            btnRemove.setTextColor(Color.parseColor("#F2B8B5"));
            btnRemove.setBackgroundColor(Color.TRANSPARENT);
            btnRemove.setTextSize(16);
            btnRemove.setContentDescription("Remove set");
            btnRemove.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                currentSessionSets.remove(index);
                saveInProgressWorkout();
                updateCurrentSessionUI();
            });

            itemRow.addView(tvInfo);
            itemRow.addView(btnRemove);
            layoutCurrentSetsContainer.addView(itemRow);
        }
    }

    private void updateProgressiveOverloadCue() {
        if (tvProgressiveCue == null) return;
        if (spinnerExercise == null || spinnerExercise.getSelectedItem() == null) {
            tvProgressiveCue.setText("Overload Cue: Log consistently to unlock suggestions.");
            return;
        }

        String currentEx = spinnerExercise.getSelectedItem().toString();
        WorkoutSet lastBestSet = null;
        for (WorkoutLog log : completedHistory) {
            for (WorkoutSet s : log.sets) {
                if (s.exercise.equalsIgnoreCase(currentEx)) {
                    if (lastBestSet == null || (s.weight * s.reps) > (lastBestSet.weight * lastBestSet.reps)) {
                        lastBestSet = s;
                    }
                }
            }
            if (lastBestSet != null) break;
        }

        if (lastBestSet != null) {
            double targetWeight = lastBestSet.weight + 2.5;
            tvProgressiveCue.setText(String.format(Locale.US,
                    "Previous Best: %.1f kg × %d reps.\nOverload Target: Aim for %.1f kg × %d reps OR +1 rep with %.1f kg.",
                    lastBestSet.weight, lastBestSet.reps, targetWeight, lastBestSet.reps, lastBestSet.weight));
        } else {
            tvProgressiveCue.setText("Overload Cue: First time logging " + currentEx + ". Set a solid baseline weight!");
        }
    }

    private void updateVaultStats() {
        double totalKg = 0;
        int totalSets = 0;

        for (WorkoutLog log : completedHistory) {
            totalKg += log.totalVolume;
            totalSets += log.totalSetsCount;
        }

        double tonnage = totalKg / 1000.0;
        if (tvTotalTonnage != null) {
            tvTotalTonnage.setText(String.format(Locale.US, "%.2f T", tonnage));
        }
        if (tvTotalSets != null) {
            tvTotalSets.setText(String.valueOf(totalSets));
        }
    }

    private void renderHistoryLogs() {
        if (layoutHistoryContainer == null) return;
        layoutHistoryContainer.removeAllViews();

        if (completedHistory.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Vault is empty. Finish workouts to record volume history.");
            empty.setTextColor(Color.parseColor("#CAC4D0"));
            empty.setTextSize(13);
            empty.setPadding(8, 16, 8, 16);
            layoutHistoryContainer.addView(empty);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault());

        for (WorkoutLog log : completedHistory) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(18, 14, 18, 14);
            card.setBackgroundResource(R.drawable.card_m3_high);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 4, 0, 10);
            card.setLayoutParams(cardParams);

            TextView tvHeader = new TextView(this);
            tvHeader.setText(String.format("%s • %s", log.program, sdf.format(new Date(log.date))));
            tvHeader.setTextColor(Color.parseColor("#D0BCFF"));
            tvHeader.setTextSize(14);
            tvHeader.setTypeface(Typeface.DEFAULT_BOLD);

            TextView tvStats = new TextView(this);
            tvStats.setText(String.format(Locale.US, "Total Volume: %.1f kg | Sets: %d", log.totalVolume, log.totalSetsCount));
            tvStats.setTextColor(Color.parseColor("#E6E1E5"));
            tvStats.setTextSize(13);
            tvStats.setPadding(0, 4, 0, 8);

            card.addView(tvHeader);
            card.addView(tvStats);

            for (WorkoutSet s : log.sets) {
                TextView tvSet = new TextView(this);
                tvSet.setText(String.format(Locale.US, "  • %s: %.1f kg × %d", s.exercise, s.weight, s.reps));
                tvSet.setTextColor(Color.parseColor("#CAC4D0"));
                tvSet.setTextSize(12);
                card.addView(tvSet);
            }

            layoutHistoryContainer.addView(card);
        }
    }

    private void shareHistory() {
        if (completedHistory.isEmpty()) {
            Toast.makeText(this, "No workout logs to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== IronPulse Workout Vault Export ===\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        for (WorkoutLog log : completedHistory) {
            sb.append("\n[ ").append(sdf.format(new Date(log.date))).append(" ] ").append(log.program).append("\n");
            sb.append("Total Volume: ").append(String.format(Locale.US, "%.1f kg", log.totalVolume))
                    .append(" | Sets: ").append(log.totalSetsCount).append("\n");
            for (WorkoutSet s : log.sets) {
                sb.append(" - ").append(s.exercise).append(": ")
                        .append(s.weight).append(" kg x ").append(s.reps).append(" reps\n");
            }
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "IronPulse Workout Logs");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent, "Export Workout Vault via"));
    }

    private void copySummaryToClipboard() {
        double totalKg = 0;
        int totalSets = 0;
        for (WorkoutLog log : completedHistory) {
            totalKg += log.totalVolume;
            totalSets += log.totalSetsCount;
        }

        String summary = String.format(Locale.US,
                "IronPulse Stats: %d Workouts Completed | Total Sets: %d | Total Tonnage: %.2f Tonnes.",
                completedHistory.size(), totalSets, (totalKg / 1000.0));

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("IronPulse Summary", summary);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Summary copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveInProgressWorkout() {
        JSONArray arr = new JSONArray();
        for (WorkoutSet set : currentSessionSets) {
            try {
                arr.put(set.toJson());
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_WORKOUT_IN_PROGRESS, arr.toString()).apply();
    }

    private void loadInProgressWorkout() {
        currentSessionSets.clear();
        String json = prefs.getString(KEY_WORKOUT_IN_PROGRESS, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    currentSessionSets.add(WorkoutSet.fromJson(arr.getJSONObject(i)));
                }
            } catch (JSONException ignored) {}
        }
    }

    private void saveHistory() {
        JSONArray arr = new JSONArray();
        for (WorkoutLog log : completedHistory) {
            try {
                arr.put(log.toJson());
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void loadHistory() {
        completedHistory.clear();
        String json = prefs.getString(KEY_HISTORY, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    completedHistory.add(WorkoutLog.fromJson(arr.getJSONObject(i)));
                }
            } catch (JSONException ignored) {}
        }
        renderHistoryLogs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (restTimer != null) {
            restTimer.cancel();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}