package com.aistudio.aurafocusminimalistp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "aurafocus-minimalist-pomodoro-ambient-sound_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_FOCUS_DURATION = "pref_focus_duration";
    private static final String KEY_SHORT_BREAK_DURATION = "pref_short_break_duration";
    private static final String KEY_LONG_BREAK_DURATION = "pref_long_break_duration";
    private static final String KEY_COMPLETED_SESSIONS = "pref_completed_sessions";
    private static final String KEY_TOTAL_FOCUS_MINUTES = "pref_total_focus_minutes";
    private static final String KEY_SESSION_HISTORY = "pref_session_history";
    private static final String KEY_AMBIENT_TYPE = "pref_ambient_type";
    private static final String KEY_AMBIENT_VOLUME = "pref_ambient_volume";

    // Session Modes
    private static final int MODE_FOCUS = 0;
    private static final int MODE_SHORT_BREAK = 1;
    private static final int MODE_LONG_BREAK = 2;

    // Ambient Sound Types
    private static final int AMBIENT_OFF = 0;
    private static final int AMBIENT_WHITE_NOISE = 1;
    private static final int AMBIENT_PINK_NOISE = 2;
    private static final int AMBIENT_RAIN = 3;
    private static final int AMBIENT_DEEP_DRONE = 4;

    private int currentMode = MODE_FOCUS;
    private boolean isTimerRunning = false;
    private long timeRemainingMillis;
    private long totalDurationMillis;

    private int focusDurationMinutes = 25;
    private int shortBreakDurationMinutes = 5;
    private int longBreakDurationMinutes = 15;
    private int completedSessionsCount = 0;
    private int totalFocusMinutes = 0;
    private String sessionHistoryLog = "";

    private int currentAmbientType = AMBIENT_OFF;
    private float ambientVolume = 0.5f;

    private CountDownTimer countDownTimer;
    private AudioTrack audioTrack;
    private Thread audioThread;
    private volatile boolean isAudioPlaying = false;

    // UI Elements
    private TextView tvModeIndicator;
    private TextView tvTimerDisplay;
    private TextView tvSessionCounter;
    private TextView tvStatsTotalTime;
    private TextView tvStatsCompleted;
    private TextView tvHistoryLog;
    private Button btnThemeToggle;
    private Button btnStartPause;
    private Button btnReset;
    private Button btnSkip;
    private Button btnModeFocus;
    private Button btnModeShortBreak;
    private Button btnModeLongBreak;
    private Button btnAmbientOff;
    private Button btnAmbientWhite;
    private Button btnAmbientPink;
    private Button btnAmbientRain;
    private Button btnAmbientDrone;
    private SeekBar seekAmbientVolume;
    private SeekBar seekFocusDuration;
    private TextView tvFocusDurationLabel;
    private Button btnShareStats;
    private Button btnClearHistory;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
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

        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen alive when timer is active and permit presentation over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.activity_main);

        loadPreferences();
        initializeViews();
        setupStatusBarTheme();
        updateThemeButtonLabel();
        updateTimerDisplayForCurrentMode();
        updateStatsUI();
        updateModeTabStyles();
        updateAmbientSoundButtonStyles();

        // Restore sound state if active
        if (currentAmbientType != AMBIENT_OFF) {
            startAmbientSoundGenerator(currentAmbientType);
        }
    }

    private void initializeViews() {
        tvModeIndicator = findViewById(R.id.tvModeIndicator);
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay);
        tvSessionCounter = findViewById(R.id.tvSessionCounter);
        tvStatsTotalTime = findViewById(R.id.tvStatsTotalTime);
        tvStatsCompleted = findViewById(R.id.tvStatsCompleted);
        tvHistoryLog = findViewById(R.id.tvHistoryLog);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnStartPause = findViewById(R.id.btnStartPause);
        btnReset = findViewById(R.id.btnReset);
        btnSkip = findViewById(R.id.btnSkip);
        btnModeFocus = findViewById(R.id.btnModeFocus);
        btnModeShortBreak = findViewById(R.id.btnModeShortBreak);
        btnModeLongBreak = findViewById(R.id.btnModeLongBreak);
        btnAmbientOff = findViewById(R.id.btnAmbientOff);
        btnAmbientWhite = findViewById(R.id.btnAmbientWhite);
        btnAmbientPink = findViewById(R.id.btnAmbientPink);
        btnAmbientRain = findViewById(R.id.btnAmbientRain);
        btnAmbientDrone = findViewById(R.id.btnAmbientDrone);
        seekAmbientVolume = findViewById(R.id.seekAmbientVolume);
        seekFocusDuration = findViewById(R.id.seekFocusDuration);
        tvFocusDurationLabel = findViewById(R.id.tvFocusDurationLabel);
        btnShareStats = findViewById(R.id.btnShareStats);
        btnClearHistory = findViewById(R.id.btnClearHistory);

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
            resetCurrentTimer();
        });

        btnSkip.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            skipCurrentSession();
        });

        btnModeFocus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(MODE_FOCUS);
        });

        btnModeShortBreak.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(MODE_SHORT_BREAK);
        });

        btnModeLongBreak.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            switchMode(MODE_LONG_BREAK);
        });

        // Ambient sound selectors
        btnAmbientOff.setOnClickListener(v -> selectAmbientSound(AMBIENT_OFF, v));
        btnAmbientWhite.setOnClickListener(v -> selectAmbientSound(AMBIENT_WHITE_NOISE, v));
        btnAmbientPink.setOnClickListener(v -> selectAmbientSound(AMBIENT_PINK_NOISE, v));
        btnAmbientRain.setOnClickListener(v -> selectAmbientSound(AMBIENT_RAIN, v));
        btnAmbientDrone.setOnClickListener(v -> selectAmbientSound(AMBIENT_DEEP_DRONE, v));

        seekAmbientVolume.setProgress((int) (ambientVolume * 100));
        seekAmbientVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ambientVolume = progress / 100.0f;
                if (audioTrack != null && isAudioPlaying) {
                    audioTrack.setVolume(ambientVolume);
                }
                savePreferences();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekFocusDuration.setProgress(focusDurationMinutes);
        tvFocusDurationLabel.setText(focusDurationMinutes + " Min");
        seekFocusDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int duration = Math.max(5, progress);
                focusDurationMinutes = duration;
                tvFocusDurationLabel.setText(duration + " Min");
                if (currentMode == MODE_FOCUS && !isTimerRunning) {
                    updateTimerDisplayForCurrentMode();
                }
                savePreferences();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnShareStats.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareFocusSummary();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearHistory();
        });
    }

    private void selectAmbientSound(int type, View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        currentAmbientType = type;
        updateAmbientSoundButtonStyles();
        savePreferences();
        startAmbientSoundGenerator(type);
    }

    private void setupStatusBarTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
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

    private void updateThemeButtonLabel() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        switch (themeMode) {
            case 1:
                btnThemeToggle.setText("Light Theme");
                break;
            case 2:
                btnThemeToggle.setText("Dark Theme");
                break;
            case 0:
            default:
                btnThemeToggle.setText("Auto Theme");
                break;
        }
    }

    private void cycleThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void switchMode(int mode) {
        if (isTimerRunning) {
            pauseTimer();
        }
        currentMode = mode;
        updateModeTabStyles();
        updateTimerDisplayForCurrentMode();
    }

    private void updateModeTabStyles() {
        int activeAlpha = 255;
        int inactiveAlpha = 140;

        btnModeFocus.getBackground().setAlpha(currentMode == MODE_FOCUS ? activeAlpha : inactiveAlpha);
        btnModeShortBreak.getBackground().setAlpha(currentMode == MODE_SHORT_BREAK ? activeAlpha : inactiveAlpha);
        btnModeLongBreak.getBackground().setAlpha(currentMode == MODE_LONG_BREAK ? activeAlpha : inactiveAlpha);

        if (currentMode == MODE_FOCUS) {
            tvModeIndicator.setText("DEEP FOCUS INTERVAL");
        } else if (currentMode == MODE_SHORT_BREAK) {
            tvModeIndicator.setText("SHORT REST & HYDRATION");
        } else {
            tvModeIndicator.setText("EXTENDED RESTORATIVE BREAK");
        }
    }

    private void updateAmbientSoundButtonStyles() {
        btnAmbientOff.setSelected(currentAmbientType == AMBIENT_OFF);
        btnAmbientWhite.setSelected(currentAmbientType == AMBIENT_WHITE_NOISE);
        btnAmbientPink.setSelected(currentAmbientType == AMBIENT_PINK_NOISE);
        btnAmbientRain.setSelected(currentAmbientType == AMBIENT_RAIN);
        btnAmbientDrone.setSelected(currentAmbientType == AMBIENT_DEEP_DRONE);
    }

    private void updateTimerDisplayForCurrentMode() {
        long minutes;
        if (currentMode == MODE_FOCUS) {
            minutes = focusDurationMinutes;
        } else if (currentMode == MODE_SHORT_BREAK) {
            minutes = shortBreakDurationMinutes;
        } else {
            minutes = longBreakDurationMinutes;
        }
        totalDurationMillis = minutes * 60 * 1000;
        timeRemainingMillis = totalDurationMillis;
        renderTime(timeRemainingMillis);
    }

    private void renderTime(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        tvTimerDisplay.setText(String.format(Locale.US, "%02d:%02d", m, s));
    }

    private void toggleTimer() {
        if (isTimerRunning) {
            pauseTimer();
        } else {
            startTimer();
        }
    }

    private void startTimer() {
        if (timeRemainingMillis <= 0) {
            updateTimerDisplayForCurrentMode();
        }
        isTimerRunning = true;
        btnStartPause.setText("Pause");

        countDownTimer = new CountDownTimer(timeRemainingMillis, 250) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeRemainingMillis = millisUntilFinished;
                renderTime(timeRemainingMillis);
            }

            @Override
            public void onFinish() {
                timeRemainingMillis = 0;
                renderTime(0);
                onSessionCompleted();
            }
        }.start();
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        btnStartPause.setText("Start");
    }

    private void resetCurrentTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        btnStartPause.setText("Start");
        updateTimerDisplayForCurrentMode();
    }

    private void skipCurrentSession() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        btnStartPause.setText("Start");

        // Advance to next expected mode without logging completed session
        if (currentMode == MODE_FOCUS) {
            if ((completedSessionsCount + 1) % 4 == 0) {
                switchMode(MODE_LONG_BREAK);
            } else {
                switchMode(MODE_SHORT_BREAK);
            }
        } else {
            switchMode(MODE_FOCUS);
        }
    }

    private void onSessionCompleted() {
        isTimerRunning = false;
        btnStartPause.setText("Start");

        triggerAlarmAndHaptics();

        String timestamp = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date());

        if (currentMode == MODE_FOCUS) {
            completedSessionsCount++;
            totalFocusMinutes += focusDurationMinutes;
            String entry = timestamp + " - Focus (" + focusDurationMinutes + "m)\n";
            sessionHistoryLog = entry + sessionHistoryLog;

            savePreferences();
            updateStatsUI();

            // Auto-advance to break
            if (completedSessionsCount % 4 == 0) {
                switchMode(MODE_LONG_BREAK);
                Toast.makeText(this, "Focus block complete! Take a long 15m break.", Toast.LENGTH_LONG).show();
            } else {
                switchMode(MODE_SHORT_BREAK);
                Toast.makeText(this, "Focus cycle complete! Take a quick 5m break.", Toast.LENGTH_LONG).show();
            }
        } else {
            String entry = timestamp + " - " + (currentMode == MODE_SHORT_BREAK ? "Short Break" : "Long Break") + "\n";
            sessionHistoryLog = entry + sessionHistoryLog;
            savePreferences();
            updateStatsUI();
            switchMode(MODE_FOCUS);
            Toast.makeText(this, "Break finished. Ready to focus again!", Toast.LENGTH_LONG).show();
        }
    }

    private void triggerAlarmAndHaptics() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] timings = {0, 300, 200, 300, 200, 500};
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, -1));
                } else {
                    vibrator.vibrate(800);
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), notificationUri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception ignored) {
        }
    }

    private void updateStatsUI() {
        tvSessionCounter.setText("Completed Rounds: " + completedSessionsCount);
        tvStatsTotalTime.setText(totalFocusMinutes + "m");
        tvStatsCompleted.setText(String.valueOf(completedSessionsCount));
        tvHistoryLog.setText(sessionHistoryLog.isEmpty() ? "No sessions completed yet." : sessionHistoryLog.trim());
    }

    private void clearHistory() {
        sessionHistoryLog = "";
        completedSessionsCount = 0;
        totalFocusMinutes = 0;
        savePreferences();
        updateStatsUI();
        Toast.makeText(this, "Session logs and counts reset", Toast.LENGTH_SHORT).show();
    }

    private void shareFocusSummary() {
        String shareBody = "AuraFocus Productivity Summary:\n" +
                "- Total Focus Time: " + totalFocusMinutes + " mins\n" +
                "- Completed Focus Sessions: " + completedSessionsCount + "\n\n" +
                "Recent Session Log:\n" + (sessionHistoryLog.isEmpty() ? "None" : sessionHistoryLog);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "AuraFocus Study & Work Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(shareIntent, "Share AuraFocus Log via"));
    }

    // Procedural Ambient Sound Synthesis Engine
    private synchronized void startAmbientSoundGenerator(final int ambientType) {
        stopAmbientSoundGenerator();

        if (ambientType == AMBIENT_OFF) {
            return;
        }

        isAudioPlaying = true;
        audioThread = new Thread(() -> {
            int sampleRate = 22050;
            int minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            int bufferSize = Math.max(minBufferSize, 4096);
            short[] audioBuffer = new short[bufferSize];

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();

            audioTrack = new AudioTrack(
                    attributes,
                    format,
                    bufferSize * 2,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            audioTrack.setVolume(ambientVolume);
            audioTrack.play();

            Random rand = new Random();
            double phase = 0.0;
            double pinkB0 = 0, pinkB1 = 0, pinkB2 = 0;

            while (isAudioPlaying) {
                for (int i = 0; i < audioBuffer.length; i++) {
                    double sample = 0.0;

                    switch (ambientType) {
                        case AMBIENT_WHITE_NOISE:
                            // Pure uncorrelated Gaussian noise
                            sample = (rand.nextDouble() * 2.0 - 1.0) * 0.22;
                            break;

                        case AMBIENT_PINK_NOISE:
                            // Paul Kellet's filter for pink spectrum (-3dB/octave)
                            double white = rand.nextDouble() * 2.0 - 1.0;
                            pinkB0 = 0.99765 * pinkB0 + white * 0.0990460;
                            pinkB1 = 0.96300 * pinkB1 + white * 0.2965164;
                            pinkB2 = 0.57000 * pinkB2 + white * 1.0526913;
                            sample = (pinkB0 + pinkB1 + pinkB2 + white * 0.1848) * 0.08;
                            break;

                        case AMBIENT_RAIN:
                            // Pink noise base with random droplet impact bursts
                            double w = rand.nextDouble() * 2.0 - 1.0;
                            pinkB0 = 0.99 * pinkB0 + w * 0.09;
                            double base = pinkB0 * 0.15;
                            // Random droplet crackle
                            if (rand.nextFloat() < 0.003f) {
                                base += (rand.nextDouble() - 0.5) * 0.7;
                            }
                            sample = base;
                            break;

                        case AMBIENT_DEEP_DRONE:
                            // Dual sine wave binaural warmth around 110Hz and 114Hz
                            phase += (2.0 * Math.PI * 110.0) / sampleRate;
                            if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI;
                            double drone = Math.sin(phase) * 0.25;
                            drone += Math.sin(phase * 1.036) * 0.15;
                            sample = drone;
                            break;
                    }

                    // Clamp to short 16-bit range
                    sample = Math.max(-1.0, Math.min(1.0, sample));
                    audioBuffer[i] = (short) (sample * 32767);
                }

                if (audioTrack != null && isAudioPlaying) {
                    audioTrack.write(audioBuffer, 0, audioBuffer.length);
                }
            }
        });
        audioThread.start();
    }

    private synchronized void stopAmbientSoundGenerator() {
        isAudioPlaying = false;
        if (audioThread != null) {
            try {
                audioThread.join(200);
            } catch (InterruptedException ignored) {
            }
            audioThread = null;
        }
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {
            }
            audioTrack = null;
        }
    }

    // Physical Keyboard Shortcuts for desktop / freeform DEX modes
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                toggleTimer();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                btnStartPause.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_R) {
                resetCurrentTimer();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_EQUALS || keyCode == KeyEvent.KEYCODE_PLUS) {
                focusDurationMinutes = Math.min(90, focusDurationMinutes + 5);
                seekFocusDuration.setProgress(focusDurationMinutes);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                focusDurationMinutes = Math.max(5, focusDurationMinutes - 5);
                seekFocusDuration.setProgress(focusDurationMinutes);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Retain dynamic screen metrics without restarting activity loop
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        focusDurationMinutes = prefs.getInt(KEY_FOCUS_DURATION, 25);
        shortBreakDurationMinutes = prefs.getInt(KEY_SHORT_BREAK_DURATION, 5);
        longBreakDurationMinutes = prefs.getInt(KEY_LONG_BREAK_DURATION, 15);
        completedSessionsCount = prefs.getInt(KEY_COMPLETED_SESSIONS, 0);
        totalFocusMinutes = prefs.getInt(KEY_TOTAL_FOCUS_MINUTES, 0);
        sessionHistoryLog = prefs.getString(KEY_SESSION_HISTORY, "");
        currentAmbientType = prefs.getInt(KEY_AMBIENT_TYPE, AMBIENT_OFF);
        ambientVolume = prefs.getFloat(KEY_AMBIENT_VOLUME, 0.5f);
    }

    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_FOCUS_DURATION, focusDurationMinutes)
                .putInt(KEY_SHORT_BREAK_DURATION, shortBreakDurationMinutes)
                .putInt(KEY_LONG_BREAK_DURATION, longBreakDurationMinutes)
                .putInt(KEY_COMPLETED_SESSIONS, completedSessionsCount)
                .putInt(KEY_TOTAL_FOCUS_MINUTES, totalFocusMinutes)
                .putString(KEY_SESSION_HISTORY, sessionHistoryLog)
                .putInt(KEY_AMBIENT_TYPE, currentAmbientType)
                .putFloat(KEY_AMBIENT_VOLUME, ambientVolume)
                .apply();
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePreferences();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        stopAmbientSoundGenerator();
    }
}