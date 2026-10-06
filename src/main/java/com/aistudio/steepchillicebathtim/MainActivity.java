package com.aistudio.steepchillicebathtim;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
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

    private static final String PREFS_NAME = "steep-chill-ice-bath-timer_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_LOGS_JSON = "key_plunge_logs";
    private static final String KEY_SAVED_TEMP = "key_saved_temp";
    private static final String KEY_SAVED_UNIT = "key_saved_unit"; // 0: C, 1: F
    private static final String KEY_TARGET_MIN = "key_target_min";
    private static final String KEY_TARGET_SEC = "key_target_sec";
    private static final String KEY_PREP_SEC = "key_prep_sec";
    private static final String KEY_GUIDED_BREATH = "key_guided_breath";
    private static final String KEY_SOUND_CHIMES = "key_sound_chimes";

    // Theme modes: 0 = Auto, 1 = Light, 2 = Dark
    private int mThemeMode = 0;

    // Timer states
    private enum State {
        IDLE, PREP, PLUNGING, PAUSED, COMPLETED
    }

    private State mState = State.IDLE;
    private CountDownTimer mTimer;
    private long mTimeLeftMillis = 0;
    private long mTotalSessionMillis = 0;
    private long mElapsedPlungeMillis = 0;
    private long mPlungeStartRealtime = 0;

    // Breath cycle states: Inhale (4s), Hold (4s), Exhale (4s)
    private static final int BREATH_CYCLE_MS = 12000;
    private CountDownTimer mBreathTimer;

    // Audio / Haptic
    private ToneGenerator mToneGen;
    private Vibrator mVibrator;

    // UI elements
    private Button mBtnThemeToggle;
    private TextView mTvStatusBadge;
    private TextView mTvTimerDisplay;
    private TextView mTvTimerSubtext;
    private ProgressBar mPbPlungeProgress;
    private TextView mTvBreathCue;
    private ProgressBar mPbBreathVisualizer;

    // Controls
    private Button mBtnStartPause;
    private Button mBtnReset;
    private Button mBtnLogManual;

    // Configuration Inputs
    private EditText mEtWaterTemp;
    private RadioGroup mRgTempUnit;
    private SeekBar mSbTargetMin;
    private SeekBar mSbTargetSec;
    private TextView mTvTargetDurationLabel;
    private SeekBar mSbPrepSec;
    private TextView mTvPrepDurationLabel;
    private Switch mSwGuidedBreath;
    private Switch mSwSoundChimes;
    private TextView mTvSafetyRecommendation;

    // Protocol presets
    private Button mBtnPresetBeginner;
    private Button mBtnPresetStandard;
    private Button mBtnPresetSovereign;

    // Metrics & History
    private TextView mTvTotalDipsCount;
    private TextView mTvTotalTimeCumulative;
    private LinearLayout mLlLogsContainer;
    private Button mBtnExportShare;
    private Button mBtnClearHistory;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME_MODE, 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        int nightMode;
        if (mode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int sysMode = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            nightMode = (sysMode == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mThemeMode = prefs.getInt(KEY_THEME_MODE, 0);

        try {
            mToneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
        } catch (Exception ignored) {
        }
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initViews();
        setupWindowDecor();
        loadSavedPreferences();
        renderHistoryLogs();
        updateSafetyCalculation();
    }

    private void setupWindowDecor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isLight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_NO;
                controller.setSystemBarsAppearance(
                        isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        }
    }

    private void initViews() {
        mBtnThemeToggle = findViewById(R.id.btnThemeToggle);
        mTvStatusBadge = findViewById(R.id.tvStatusBadge);
        mTvTimerDisplay = findViewById(R.id.tvTimerDisplay);
        mTvTimerSubtext = findViewById(R.id.tvTimerSubtext);
        mPbPlungeProgress = findViewById(R.id.pbPlungeProgress);
        mTvBreathCue = findViewById(R.id.tvBreathCue);
        mPbBreathVisualizer = findViewById(R.id.pbBreathVisualizer);

        mBtnStartPause = findViewById(R.id.btnStartPause);
        mBtnReset = findViewById(R.id.btnReset);
        mBtnLogManual = findViewById(R.id.btnLogManual);

        mEtWaterTemp = findViewById(R.id.etWaterTemp);
        mRgTempUnit = findViewById(R.id.rgTempUnit);
        mSbTargetMin = findViewById(R.id.sbTargetMin);
        mSbTargetSec = findViewById(R.id.sbTargetSec);
        mTvTargetDurationLabel = findViewById(R.id.tvTargetDurationLabel);
        mSbPrepSec = findViewById(R.id.sbPrepSec);
        mTvPrepDurationLabel = findViewById(R.id.tvPrepDurationLabel);
        mSwGuidedBreath = findViewById(R.id.swGuidedBreath);
        mSwSoundChimes = findViewById(R.id.swSoundChimes);
        mTvSafetyRecommendation = findViewById(R.id.tvSafetyRecommendation);

        mBtnPresetBeginner = findViewById(R.id.btnPresetBeginner);
        mBtnPresetStandard = findViewById(R.id.btnPresetStandard);
        mBtnPresetSovereign = findViewById(R.id.btnPresetSovereign);

        mTvTotalDipsCount = findViewById(R.id.tvTotalDipsCount);
        mTvTotalTimeCumulative = findViewById(R.id.tvTotalTimeCumulative);
        mLlLogsContainer = findViewById(R.id.llLogsContainer);
        mBtnExportShare = findViewById(R.id.btnExportShare);
        mBtnClearHistory = findViewById(R.id.btnClearHistory);

        // Update Theme Button Text
        updateThemeToggleText();

        mBtnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        // Sliders & Text
        mSbTargetMin.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTargetDurationText();
                updateSafetyCalculation();
            }
        });

        mSbTargetSec.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTargetDurationText();
                updateSafetyCalculation();
            }
        });

        mSbPrepSec.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvPrepDurationLabel.setText(progress + "s Ready Buffer");
            }
        });

        mRgTempUnit.setOnCheckedChangeListener((group, checkedId) -> updateSafetyCalculation());

        // Presets
        mBtnPresetBeginner.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            applyPreset(1, 0, 15);
        });

        mBtnPresetStandard.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            applyPreset(2, 30, 15);
        });

        mBtnPresetSovereign.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            applyPreset(4, 0, 10);
        });

        // Controls
        mBtnStartPause.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onStartPauseClicked();
        });

        mBtnReset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetTimer();
        });

        mBtnLogManual.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            recordSession(mElapsedPlungeMillis > 0 ? mElapsedPlungeMillis : getTargetDurationMillis(), true);
        });

        mBtnExportShare.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportPlungeLog();
        });

        mBtnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearHistory();
        });
    }

    private void updateThemeToggleText() {
        if (mThemeMode == 1) {
            mBtnThemeToggle.setText("Light Theme");
        } else if (mThemeMode == 2) {
            mBtnThemeToggle.setText("Dark Theme");
        } else {
            mBtnThemeToggle.setText("Auto Theme");
        }
    }

    private void cycleThemeMode() {
        mThemeMode = (mThemeMode + 1) % 3;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mThemeMode).apply();
        recreate();
    }

    private void updateTargetDurationText() {
        int m = mSbTargetMin.getProgress();
        int s = mSbTargetSec.getProgress();
        mTvTargetDurationLabel.setText(String.format(Locale.getDefault(), "%02d:%02d Target Immersion", m, s));
        if (mState == State.IDLE) {
            mTvTimerDisplay.setText(String.format(Locale.getDefault(), "%02d:%02d", m, s));
        }
    }

    private void applyPreset(int min, int sec, int prep) {
        mSbTargetMin.setProgress(min);
        mSbTargetSec.setProgress(sec);
        mSbPrepSec.setProgress(prep);
        updateTargetDurationText();
        Toast.makeText(this, "Loaded Protocol: " + min + "m " + sec + "s", Toast.LENGTH_SHORT).show();
    }

    private long getTargetDurationMillis() {
        return (mSbTargetMin.getProgress() * 60L + mSbTargetSec.getProgress()) * 1000L;
    }

    private void updateSafetyCalculation() {
        String tempStr = mEtWaterTemp.getText().toString().trim();
        double temp = 10.0; // default Celsius
        boolean isFahrenheit = mRgTempUnit.getCheckedRadioButtonId() == R.id.rbFahrenheit;
        if (!tempStr.isEmpty()) {
            try {
                temp = Double.parseDouble(tempStr);
            } catch (NumberFormatException ignored) {}
        }

        double tempC = isFahrenheit ? (temp - 32.0) * 5.0 / 9.0 : temp;
        long targetMs = getTargetDurationMillis();
        long targetSec = targetMs / 1000L;

        String advice;
        if (tempC < 4.0) {
            advice = "EXTREME VIGILANCE: Sub-4°C (near freezing). Never exceed 2-3 min. Risk of cold shock & peripheral numbness.";
        } else if (tempC <= 10.0) {
            if (targetSec > 300) {
                advice = "MODERATE/HIGH RISK: " + String.format(Locale.getDefault(), "%.1f°C", tempC) + " exceeds recommended 5-min threshold.";
            } else {
                advice = "OPTIMAL CRYOTHERAPY: Active cold shock protein synthesis. Control exhalations.";
            }
        } else if (tempC <= 15.0) {
            advice = "MILD ADAPTATION: Good recovery & vagus nerve conditioning window (3-10 min).";
        } else {
            advice = "ABOVE PLUNGE RANGE: Water temp is tepid (>15°C). Add ice blocks to stimulate cold adaptation.";
        }
        mTvSafetyRecommendation.setText(advice);
    }

    private void onStartPauseClicked() {
        if (mState == State.IDLE) {
            int prep = mSbPrepSec.getProgress();
            if (prep > 0) {
                startPrepTimer(prep * 1000L);
            } else {
                startPlungeTimer(getTargetDurationMillis());
            }
        } else if (mState == State.PREP) {
            // Skip prep and jump straight to plunge
            if (mTimer != null) mTimer.cancel();
            startPlungeTimer(getTargetDurationMillis());
        } else if (mState == State.PLUNGING) {
            pausePlungeTimer();
        } else if (mState == State.PAUSED) {
            resumePlungeTimer();
        } else if (mState == State.COMPLETED) {
            resetTimer();
        }
    }

    private void startPrepTimer(long durationMs) {
        mState = State.PREP;
        mTvStatusBadge.setText("PREPARING ENTRY");
        mBtnStartPause.setText("SKIP TO PLUNGE");
        mPbPlungeProgress.setProgress(0);
        mTvTimerSubtext.setText("Breathe deeply • Step into the water");

        if (mTimer != null) mTimer.cancel();
        playToneBeep(ToneGenerator.TONE_PROP_BEEP);

        mTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000);
                mTvTimerDisplay.setText(String.format(Locale.getDefault(), "00:%02d", sec + 1));
                if (millisUntilFinished <= 3100 && millisUntilFinished >= 2900) {
                    playToneBeep(ToneGenerator.TONE_PROP_BEEP);
                } else if (millisUntilFinished <= 2100 && millisUntilFinished >= 1900) {
                    playToneBeep(ToneGenerator.TONE_PROP_BEEP);
                } else if (millisUntilFinished <= 1100 && millisUntilFinished >= 900) {
                    playToneBeep(ToneGenerator.TONE_PROP_BEEP);
                }
            }

            @Override
            public void onFinish() {
                playDoubleChime();
                triggerVibration(new long[]{0, 200, 100, 300});
                startPlungeTimer(getTargetDurationMillis());
            }
        }.start();
    }

    private void startPlungeTimer(long durationMs) {
        mState = State.PLUNGING;
        mTotalSessionMillis = durationMs;
        mTimeLeftMillis = durationMs;
        mElapsedPlungeMillis = 0;
        mPlungeStartRealtime = SystemClock.elapsedRealtime();

        mTvStatusBadge.setText("SUBMERGED • ACTIVE");
        mBtnStartPause.setText("PAUSE");
        mBtnReset.setEnabled(true);
        mTvTimerSubtext.setText("Maintain autonomic calm • Slow diaphragmatic breath");

        runCountDown(mTimeLeftMillis);
        startBreathVisualizer();
    }

    private void pausePlungeTimer() {
        mState = State.PAUSED;
        if (mTimer != null) mTimer.cancel();
        stopBreathVisualizer();
        mElapsedPlungeMillis += (SystemClock.elapsedRealtime() - mPlungeStartRealtime);

        mTvStatusBadge.setText("PAUSED");
        mBtnStartPause.setText("RESUME");
        mTvTimerSubtext.setText("Submersion paused");
    }

    private void resumePlungeTimer() {
        mState = State.PLUNGING;
        mPlungeStartRealtime = SystemClock.elapsedRealtime();
        mTvStatusBadge.setText("SUBMERGED • ACTIVE");
        mBtnStartPause.setText("PAUSE");
        mTvTimerSubtext.setText("Maintain autonomic calm");

        runCountDown(mTimeLeftMillis);
        startBreathVisualizer();
    }

    private void runCountDown(long durationMs) {
        if (mTimer != null) mTimer.cancel();

        mTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                mTimeLeftMillis = millisUntilFinished;
                long totalElapsed = mTotalSessionMillis - millisUntilFinished;
                int progress = (int) ((totalElapsed * 100) / mTotalSessionMillis);
                mPbPlungeProgress.setProgress(progress);

                int minutes = (int) (millisUntilFinished / 1000) / 60;
                int seconds = (int) (millisUntilFinished / 1000) % 60;
                mTvTimerDisplay.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));

                // Minute mark chime
                if (seconds == 0 && (millisUntilFinished % 60000 < 200)) {
                    if (mSwSoundChimes.isChecked()) {
                        playToneBeep(ToneGenerator.TONE_CDMA_PIP);
                    }
                }
            }

            @Override
            public void onFinish() {
                mElapsedPlungeMillis = mTotalSessionMillis;
                onPlungeCompleted();
            }
        }.start();
    }

    private void onPlungeCompleted() {
        mState = State.COMPLETED;
        mPbPlungeProgress.setProgress(100);
        mTvTimerDisplay.setText("00:00");
        mTvStatusBadge.setText("SESSION COMPLETE");
        mTvTimerSubtext.setText("Exit calmly • Initiate warm-up protocol (Horse stance)");
        mBtnStartPause.setText("START NEW");
        stopBreathVisualizer();

        playFinishFanfare();
        triggerVibration(new long[]{0, 500, 200, 500, 200, 800});
        recordSession(mElapsedPlungeMillis, false);
    }

    private void resetTimer() {
        if (mTimer != null) mTimer.cancel();
        stopBreathVisualizer();
        mState = State.IDLE;
        mTimeLeftMillis = 0;
        mElapsedPlungeMillis = 0;
        mPbPlungeProgress.setProgress(0);
        mTvStatusBadge.setText("READY");
        mBtnStartPause.setText("START IMMERSION");
        mTvTimerSubtext.setText("Set temperature & duration below");
        updateTargetDurationText();
    }

    private void startBreathVisualizer() {
        if (!mSwGuidedBreath.isChecked()) {
            mTvBreathCue.setText("Autonomic Breath Control: Relaxed");
            mPbBreathVisualizer.setProgress(50);
            return;
        }

        if (mBreathTimer != null) mBreathTimer.cancel();
        mBreathTimer = new CountDownTimer(86400000L, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                long cyclePos = (SystemClock.elapsedRealtime() % BREATH_CYCLE_MS);
                if (cyclePos < 4000) {
                    mTvBreathCue.setText("INHALE SLOWLY (Nose)...");
                    int pct = (int) ((cyclePos * 100) / 4000);
                    mPbBreathVisualizer.setProgress(pct);
                } else if (cyclePos < 8000) {
                    mTvBreathCue.setText("HOLD & RELAX CHEST...");
                    mPbBreathVisualizer.setProgress(100);
                } else {
                    mTvBreathCue.setText("EXHALE PROLONGED (Mouth)...");
                    long exhaleProgress = cyclePos - 8000;
                    int pct = 100 - (int) ((exhaleProgress * 100) / 4000);
                    mPbBreathVisualizer.setProgress(pct);
                }
            }

            @Override
            public void onFinish() {
            }
        }.start();
    }

    private void stopBreathVisualizer() {
        if (mBreathTimer != null) {
            mBreathTimer.cancel();
            mBreathTimer = null;
        }
        mTvBreathCue.setText("Autonomic Breath Control: Standby");
        mPbBreathVisualizer.setProgress(0);
    }

    private void playToneBeep(int toneType) {
        if (mSwSoundChimes.isChecked() && mToneGen != null) {
            try {
                mToneGen.startTone(toneType, 150);
            } catch (Exception ignored) {}
        }
    }

    private void playDoubleChime() {
        if (mSwSoundChimes.isChecked() && mToneGen != null) {
            try {
                mToneGen.startTone(ToneGenerator.TONE_PROP_ACK, 250);
            } catch (Exception ignored) {}
        }
    }

    private void playFinishFanfare() {
        if (mSwSoundChimes.isChecked() && mToneGen != null) {
            try {
                mToneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 800);
            } catch (Exception ignored) {}
        }
    }

    private void triggerVibration(long[] pattern) {
        if (mVibrator == null || !mVibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            mVibrator.vibrate(pattern, -1);
        }
    }

    // --- Persistence & Session Logging ---
    private void recordSession(long durationMs, boolean isManual) {
        if (durationMs <= 0) {
            Toast.makeText(this, "No immersion duration recorded.", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentLogs = prefs.getString(KEY_LOGS_JSON, "[]");

        String tempVal = mEtWaterTemp.getText().toString().trim();
        if (tempVal.isEmpty()) tempVal = "10.0";
        String unit = (mRgTempUnit.getCheckedRadioButtonId() == R.id.rbFahrenheit) ? "°F" : "°C";
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

        try {
            JSONArray array = new JSONArray(currentLogs);
            JSONObject obj = new JSONObject();
            obj.put("date", dateStr);
            obj.put("durationMs", durationMs);
            obj.put("temp", tempVal + unit);
            obj.put("manual", isManual);
            array.put(0, obj); // Most recent first

            prefs.edit().putString(KEY_LOGS_JSON, array.toString()).apply();
            renderHistoryLogs();
            Toast.makeText(this, "Plunge logged successfully!", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void renderHistoryLogs() {
        mLlLogsContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentLogs = prefs.getString(KEY_LOGS_JSON, "[]");

        long totalTimeMs = 0;
        int count = 0;

        // Resolve theme colors dynamically for programmatic views
        TypedValue tvPrimary = new TypedValue();
        TypedValue tvSecondary = new TypedValue();
        TypedValue tvAccent = new TypedValue();
        TypedValue tvSurface = new TypedValue();

        getTheme().resolveAttribute(android.R.attr.textColorPrimary, tvPrimary, true);
        getTheme().resolveAttribute(android.R.attr.textColorSecondary, tvSecondary, true);
        getTheme().resolveAttribute(android.R.attr.colorAccent, tvAccent, true);
        getTheme().resolveAttribute(android.R.attr.colorBackgroundFloating, tvSurface, true);

        int density = (int) getResources().getDisplayMetrics().density;

        try {
            JSONArray array = new JSONArray(currentLogs);
            count = array.length();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String date = obj.optString("date", "Unknown");
                long durMs = obj.optLong("durationMs", 0);
                String temp = obj.optString("temp", "--");
                boolean manual = obj.optBoolean("manual", false);

                totalTimeMs += durMs;

                long s = durMs / 1000L;
                long m = s / 60L;
                long remS = s % 60L;
                String durFormatted = String.format(Locale.getDefault(), "%02d:%02d", m, remS);

                // Build log item container programmatically
                LinearLayout itemView = new LinearLayout(this);
                itemView.setOrientation(LinearLayout.HORIZONTAL);
                itemView.setGravity(Gravity.CENTER_VERTICAL);
                itemView.setBackgroundResource(tvSurface.resourceId);
                int pad = 12 * density;
                itemView.setPadding(pad, pad, pad, pad);

                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                itemLp.bottomMargin = 8 * density;
                itemView.setLayoutParams(itemLp);

                // Left Column: Date & Details
                LinearLayout leftCol = new LinearLayout(this);
                leftCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );
                leftCol.setLayoutParams(leftLp);

                TextView tvDate = new TextView(this);
                tvDate.setText(date + (manual ? " (Manual)" : ""));
                tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tvDate.setTypeface(null, Typeface.BOLD);
                if (tvPrimary.resourceId != 0) {
                    tvDate.setTextColor(getColor(tvPrimary.resourceId));
                } else {
                    tvDate.setTextColor(tvPrimary.data);
                }

                TextView tvDetails = new TextView(this);
                tvDetails.setText("Temp: " + temp);
                tvDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                if (tvSecondary.resourceId != 0) {
                    tvDetails.setTextColor(getColor(tvSecondary.resourceId));
                } else {
                    tvDetails.setTextColor(tvSecondary.data);
                }

                leftCol.addView(tvDate);
                leftCol.addView(tvDetails);

                // Right Column: Duration badge
                TextView tvDuration = new TextView(this);
                tvDuration.setText(durFormatted);
                tvDuration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                tvDuration.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                if (tvAccent.resourceId != 0) {
                    tvDuration.setTextColor(getColor(tvAccent.resourceId));
                } else {
                    tvDuration.setTextColor(tvAccent.data);
                }

                itemView.addView(leftCol);
                itemView.addView(tvDuration);

                mLlLogsContainer.addView(itemView);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mTvTotalDipsCount.setText(String.valueOf(count));
        long totalMin = (totalTimeMs / 1000L) / 60L;
        long totalSec = (totalTimeMs / 1000L) % 60L;
        mTvTotalTimeCumulative.setText(String.format(Locale.getDefault(), "%dm %02ds", totalMin, totalSec));
    }

    private void exportPlungeLog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentLogs = prefs.getString(KEY_LOGS_JSON, "[]");
        StringBuilder sb = new StringBuilder();
        sb.append("=== CRYO PULSE: COLD PLUNGE LOGS ===\n");

        try {
            JSONArray array = new JSONArray(currentLogs);
            if (array.length() == 0) {
                Toast.makeText(this, "No plunge history to export.", Toast.LENGTH_SHORT).show();
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                long durMs = obj.optLong("durationMs", 0);
                long s = durMs / 1000L;
                sb.append(String.format(Locale.getDefault(), "• %s | %02d:%02d | %s\n",
                        obj.optString("date"), s / 60, s % 60, obj.optString("temp")));
            }
        } catch (JSONException e) {
            sb.append(currentLogs);
        }

        String payload = sb.toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Plunge Logs", payload));
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "CryoPulse Cold Plunge History");
        sendIntent.putExtra(Intent.EXTRA_TEXT, payload);
        startActivity(Intent.createChooser(sendIntent, "Share Plunge Log"));
    }

    private void clearHistory() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_LOGS_JSON, "[]")
                .apply();
        renderHistoryLogs();
        Toast.makeText(this, "Plunge logs cleared.", Toast.LENGTH_SHORT).show();
    }

    private void loadSavedPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mEtWaterTemp.setText(prefs.getString(KEY_SAVED_TEMP, "10.0"));
        int unit = prefs.getInt(KEY_SAVED_UNIT, 0);
        if (unit == 1) {
            mRgTempUnit.check(R.id.rbFahrenheit);
        } else {
            mRgTempUnit.check(R.id.rbCelsius);
        }

        mSbTargetMin.setProgress(prefs.getInt(KEY_TARGET_MIN, 2));
        mSbTargetSec.setProgress(prefs.getInt(KEY_TARGET_SEC, 30));
        mSbPrepSec.setProgress(prefs.getInt(KEY_PREP_SEC, 15));
        mSwGuidedBreath.setChecked(prefs.getBoolean(KEY_GUIDED_BREATH, true));
        mSwSoundChimes.setChecked(prefs.getBoolean(KEY_SOUND_CHIMES, true));

        updateTargetDurationText();
        mTvPrepDurationLabel.setText(mSbPrepSec.getProgress() + "s Ready Buffer");
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_SAVED_TEMP, mEtWaterTemp.getText().toString());
        editor.putInt(KEY_SAVED_UNIT, mRgTempUnit.getCheckedRadioButtonId() == R.id.rbFahrenheit ? 1 : 0);
        editor.putInt(KEY_TARGET_MIN, mSbTargetMin.getProgress());
        editor.putInt(KEY_TARGET_SEC, mSbTargetSec.getProgress());
        editor.putInt(KEY_PREP_SEC, mSbPrepSec.getProgress());
        editor.putBoolean(KEY_GUIDED_BREATH, mSwGuidedBreath.isChecked());
        editor.putBoolean(KEY_SOUND_CHIMES, mSwSoundChimes.isChecked());
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePreferences();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTimer != null) mTimer.cancel();
        if (mBreathTimer != null) mBreathTimer.cancel();
        if (mToneGen != null) mToneGen.release();
    }

    // Desktop Keyboard Shortcut Dispatcher
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_SPACE:
                    onStartPauseClicked();
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (mState == State.IDLE) {
                        onStartPauseClicked();
                    } else if (mState == State.COMPLETED) {
                        resetTimer();
                    }
                    return true;
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_EQUALS:
                case KeyEvent.KEYCODE_NUMPAD_ADD:
                    mSbTargetMin.setProgress(Math.min(mSbTargetMin.getProgress() + 1, mSbTargetMin.getMax()));
                    return true;
                case KeyEvent.KEYCODE_MINUS:
                case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                    mSbTargetMin.setProgress(Math.max(mSbTargetMin.getProgress() - 1, 0));
                    return true;
                case KeyEvent.KEYCODE_R:
                    resetTimer();
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Freeform windowing desktop resizing: state maintained seamlessly
    }

    // Utility listener
    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}