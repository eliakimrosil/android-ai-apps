package com.aistudio.fasttrackintermitten;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
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

    private static final String PREFS_NAME = "fast-track-intermittent-fasting-sentinel_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_IS_FASTING = "is_fasting";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_TARGET_HOURS = "target_hours";
    private static final String KEY_WATER_ML = "water_ml";
    private static final String KEY_FAST_HISTORY = "fast_history_json";

    // Fasting Protocol definitions
    private static final int PROTOCOL_16_8 = 16;
    private static final int PROTOCOL_18_6 = 18;
    private static final int PROTOCOL_20_4 = 20;
    private static final int PROTOCOL_24_0 = 24;

    private SharedPreferences mPrefs;
    private Handler mHandler;
    private Runnable mTimerRunnable;

    // State Variables
    private boolean mIsFasting = false;
    private long mStartTimeMillis = 0;
    private int mTargetHours = 16;
    private int mWaterIntakeMl = 0;
    private boolean mAlertTriggered = false;

    // Views
    private Button mBtnThemeToggle;
    private TextView mTvStatusBadge;
    private TextView mTvTimerDigits;
    private TextView mTvTargetReadout;
    private TextView mTvElapsedReadout;
    private TextView mTvRemainingReadout;
    private TextView mTvMetabolicStage;
    private ProgressBar mProgressFasting;
    private Button mBtnToggleFast;
    private Button mBtnResetFast;

    // Protocol Buttons
    private Button mBtnProto16;
    private Button mBtnProto18;
    private Button mBtnProto20;
    private Button mBtnProto24;

    // Hydration Views
    private TextView mTvWaterCount;
    private Button mBtnAddWater250;
    private Button mBtnAddWater500;
    private Button mBtnResetWater;

    // Target Customization
    private SeekBar mSbTargetHours;
    private TextView mTvSeekValue;

    // History and Telemetry Views
    private TextView mTvHistoryLog;
    private Button mBtnExportData;
    private Button mBtnImportData;
    private Button mBtnClearHistory;

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
            // Auto: inspect secure settings or system resources
            int sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (sysNight == 2) {
                nightMode = Configuration.UI_MODE_NIGHT_YES;
            } else if (sysNight == 1) {
                nightMode = Configuration.UI_MODE_NIGHT_NO;
            } else {
                nightMode = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK);
            }
        }

        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configure lockscreen readiness for persistent sentinel timers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupStatusBarAppearance();
        loadSavedState();
        setupListeners();
        startTimerLoop();
    }

    private void initViews() {
        mBtnThemeToggle = findViewById(R.id.btnThemeToggle);
        mTvStatusBadge = findViewById(R.id.tvStatusBadge);
        mTvTimerDigits = findViewById(R.id.tvTimerDigits);
        mTvTargetReadout = findViewById(R.id.tvTargetReadout);
        mTvElapsedReadout = findViewById(R.id.tvElapsedReadout);
        mTvRemainingReadout = findViewById(R.id.tvRemainingReadout);
        mTvMetabolicStage = findViewById(R.id.tvMetabolicStage);
        mProgressFasting = findViewById(R.id.progressFasting);
        mBtnToggleFast = findViewById(R.id.btnToggleFast);
        mBtnResetFast = findViewById(R.id.btnResetFast);

        mBtnProto16 = findViewById(R.id.btnProto16);
        mBtnProto18 = findViewById(R.id.btnProto18);
        mBtnProto20 = findViewById(R.id.btnProto20);
        mBtnProto24 = findViewById(R.id.btnProto24);

        mTvWaterCount = findViewById(R.id.tvWaterCount);
        mBtnAddWater250 = findViewById(R.id.btnAddWater250);
        mBtnAddWater500 = findViewById(R.id.btnAddWater500);
        mBtnResetWater = findViewById(R.id.btnResetWater);

        mSbTargetHours = findViewById(R.id.sbTargetHours);
        mTvSeekValue = findViewById(R.id.tvSeekValue);

        mTvHistoryLog = findViewById(R.id.tvHistoryLog);
        mBtnExportData = findViewById(R.id.btnExportData);
        mBtnImportData = findViewById(R.id.btnImportData);
        mBtnClearHistory = findViewById(R.id.btnClearHistory);
    }

    private void setupStatusBarAppearance() {
        int themeMode = mPrefs.getInt(KEY_THEME_MODE, 0);
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        String themeLabel;
        if (themeMode == 1) {
            themeLabel = "Light Theme";
        } else if (themeMode == 2) {
            themeLabel = "Dark Theme";
        } else {
            themeLabel = "Auto Theme";
        }
        mBtnThemeToggle.setText(themeLabel);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Window window = getWindow();
            WindowInsetsController insetsController = window.getInsetsController();
            if (insetsController != null) {
                if (!isNight) {
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

    private void cycleThemeMode() {
        int current = mPrefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3;
        mPrefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void loadSavedState() {
        mIsFasting = mPrefs.getBoolean(KEY_IS_FASTING, false);
        mStartTimeMillis = mPrefs.getLong(KEY_START_TIME, 0);
        mTargetHours = mPrefs.getInt(KEY_TARGET_HOURS, PROTOCOL_16_8);
        mWaterIntakeMl = mPrefs.getInt(KEY_WATER_ML, 0);

        mSbTargetHours.setProgress(mTargetHours);
        mTvSeekValue.setText(mTargetHours + "h");

        updateProtocolButtonHighlight();
        updateWaterUi();
        renderHistory();
        updateUiState();
    }

    private void saveCurrentFastState() {
        mPrefs.edit()
                .putBoolean(KEY_IS_FASTING, mIsFasting)
                .putLong(KEY_START_TIME, mStartTimeMillis)
                .putInt(KEY_TARGET_HOURS, mTargetHours)
                .putInt(KEY_WATER_ML, mWaterIntakeMl)
                .apply();
    }

    private void setupListeners() {
        mBtnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        mBtnToggleFast.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleFastingSession();
        });

        mBtnResetFast.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            confirmResetCurrentFast();
        });

        mBtnProto16.setOnClickListener(v -> selectProtocol(PROTOCOL_16_8, v));
        mBtnProto18.setOnClickListener(v -> selectProtocol(PROTOCOL_18_6, v));
        mBtnProto20.setOnClickListener(v -> selectProtocol(PROTOCOL_20_4, v));
        mBtnProto24.setOnClickListener(v -> selectProtocol(PROTOCOL_24_0, v));

        mSbTargetHours.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 1) {
                    seekBar.setProgress(1);
                    progress = 1;
                }
                mTvSeekValue.setText(progress + "h");
                if (fromUser) {
                    mTargetHours = progress;
                    updateProtocolButtonHighlight();
                    saveCurrentFastState();
                    updateUiState();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        });

        mBtnAddWater250.setOnClickListener(v -> addWater(250, v));
        mBtnAddWater500.setOnClickListener(v -> addWater(500, v));
        mBtnResetWater.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            mWaterIntakeMl = 0;
            saveCurrentFastState();
            updateWaterUi();
        });

        mBtnExportData.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportData();
        });

        mBtnImportData.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showImportDialog();
        });

        mBtnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            confirmClearHistory();
        });
    }

    private void selectProtocol(int hours, View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        mTargetHours = hours;
        mSbTargetHours.setProgress(hours);
        mTvSeekValue.setText(hours + "h");
        updateProtocolButtonHighlight();
        saveCurrentFastState();
        updateUiState();
    }

    private void updateProtocolButtonHighlight() {
        mBtnProto16.setSelected(mTargetHours == PROTOCOL_16_8);
        mBtnProto18.setSelected(mTargetHours == PROTOCOL_18_6);
        mBtnProto20.setSelected(mTargetHours == PROTOCOL_20_4);
        mBtnProto24.setSelected(mTargetHours == PROTOCOL_24_0);

        // Visual opacity for selected vs normal
        float selAlpha = 1.0f;
        float unselAlpha = 0.55f;
        mBtnProto16.setAlpha(mTargetHours == PROTOCOL_16_8 ? selAlpha : unselAlpha);
        mBtnProto18.setAlpha(mTargetHours == PROTOCOL_18_6 ? selAlpha : unselAlpha);
        mBtnProto20.setAlpha(mTargetHours == PROTOCOL_20_4 ? selAlpha : unselAlpha);
        mBtnProto24.setAlpha(mTargetHours == PROTOCOL_24_0 ? selAlpha : unselAlpha);
    }

    private void addWater(int ml, View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        mWaterIntakeMl += ml;
        saveCurrentFastState();
        updateWaterUi();
    }

    private void updateWaterUi() {
        mTvWaterCount.setText(mWaterIntakeMl + " mL");
    }

    private void toggleFastingSession() {
        long now = System.currentTimeMillis();
        if (!mIsFasting) {
            // Start fasting
            mIsFasting = true;
            mStartTimeMillis = now;
            mAlertTriggered = false;
            saveCurrentFastState();
            Toast.makeText(this, "Sentinel Engaged: Fasting started!", Toast.LENGTH_SHORT).show();
        } else {
            // End fasting and record to history
            long duration = now - mStartTimeMillis;
            recordFastHistory(mStartTimeMillis, now, duration, mTargetHours);
            mIsFasting = false;
            mStartTimeMillis = 0;
            mAlertTriggered = false;
            saveCurrentFastState();
            renderHistory();
            Toast.makeText(this, "Fast completed and saved to Sentinel log!", Toast.LENGTH_SHORT).show();
        }
        updateUiState();
    }

    private void confirmResetCurrentFast() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Current Fast?")
                .setMessage("This will cancel the active fasting timer without logging it to history.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    mIsFasting = false;
                    mStartTimeMillis = 0;
                    mAlertTriggered = false;
                    saveCurrentFastState();
                    updateUiState();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startTimerLoop() {
        mTimerRunnable = new Runnable() {
            @Override
            public void run() {
                updateUiState();
                mHandler.postDelayed(this, 1000);
            }
        };
        mHandler.post(mTimerRunnable);
    }

    private void updateUiState() {
        mTvTargetReadout.setText(mTargetHours + "h 00m");

        if (!mIsFasting) {
            mTvStatusBadge.setText("IDLE / EATING WINDOW");
            mTvTimerDigits.setText("00:00:00");
            mTvElapsedReadout.setText("00h 00m 00s");
            mTvRemainingReadout.setText(String.format(Locale.getDefault(), "%02dh 00m 00s", mTargetHours));
            mTvMetabolicStage.setText("Stage: Digestion & Anabolism (Fed State)");
            mProgressFasting.setProgress(0);
            mBtnToggleFast.setText("START FAST");
            mBtnResetFast.setVisibility(View.GONE);
            return;
        }

        mBtnResetFast.setVisibility(View.VISIBLE);
        mBtnToggleFast.setText("END FAST");

        long now = System.currentTimeMillis();
        long elapsedMillis = Math.max(0, now - mStartTimeMillis);
        long targetMillis = (long) mTargetHours * 3600L * 1000L;
        long remainingMillis = targetMillis - elapsedMillis;

        // Timer Digits Display (HH:MM:SS)
        long seconds = (elapsedMillis / 1000) % 60;
        long minutes = (elapsedMillis / (1000 * 60)) % 60;
        long hours = (elapsedMillis / (1000 * 60 * 60));
        mTvTimerDigits.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
        mTvElapsedReadout.setText(String.format(Locale.getDefault(), "%02dh %02dm %02ds", hours, minutes, seconds));

        // Remaining or Overtime
        if (remainingMillis > 0) {
            long remSec = (remainingMillis / 1000) % 60;
            long remMin = (remainingMillis / (1000 * 60)) % 60;
            long remHours = (remainingMillis / (1000 * 60 * 60));
            mTvRemainingReadout.setText(String.format(Locale.getDefault(), "%02dh %02dm %02ds", remHours, remMin, remSec));
            mTvStatusBadge.setText("SENTINEL ACTIVE: FASTING");
        } else {
            long overtime = Math.abs(remainingMillis);
            long ovSec = (overtime / 1000) % 60;
            long ovMin = (overtime / (1000 * 60)) % 60;
            long ovHours = (overtime / (1000 * 60 * 60));
            mTvRemainingReadout.setText(String.format(Locale.getDefault(), "+%02dh %02dm %02ds (Surpassed)", ovHours, ovMin, ovSec));
            mTvStatusBadge.setText("GOAL ACHIEVED (IN BONUS STATE)");

            if (!mAlertTriggered) {
                mAlertTriggered = true;
                notifyGoalReached();
            }
        }

        // Progress Bar
        int progressPct = (int) Math.min(100, (elapsedMillis * 100) / targetMillis);
        mProgressFasting.setProgress(progressPct);

        // Metabolic Stage Telemetry (Based on scientific fasting biology)
        double elapsedHours = elapsedMillis / (1000.0 * 3600.0);
        if (elapsedHours < 4.0) {
            mTvMetabolicStage.setText("Stage 1 (0-4h): Blood Sugar & Insulin Dropping");
        } else if (elapsedHours < 8.0) {
            mTvMetabolicStage.setText("Stage 2 (4-8h): Digestion Complete, Glycogen Depletion");
        } else if (elapsedHours < 12.0) {
            mTvMetabolicStage.setText("Stage 3 (8-12h): Fat Burning Initiated (Early Ketosis)");
        } else if (elapsedHours < 16.0) {
            mTvMetabolicStage.setText("Stage 4 (12-16h): Active Ketosis & Metabolic Shift");
        } else if (elapsedHours < 24.0) {
            mTvMetabolicStage.setText("Stage 5 (16-24h): Peak Autophagy & Cellular Cleansing");
        } else {
            mTvMetabolicStage.setText("Stage 6 (24h+): Deep Ketosis, Growth Hormone Elevation");
        }
    }

    private void notifyGoalReached() {
        try {
            // Sound
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), alert);
            if (r != null) {
                r.play();
            }
            // Vibration
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 150, 400}, -1));
                } else {
                    vibrator.vibrate(500);
                }
            }
        } catch (Exception ignored) {
        }
        Toast.makeText(this, "Sentinel Alert: Fasting target reached!", Toast.LENGTH_LONG).show();
    }

    private void recordFastHistory(long start, long end, long durationMillis, int targetHours) {
        try {
            String existingJson = mPrefs.getString(KEY_FAST_HISTORY, "[]");
            JSONArray array = new JSONArray(existingJson);

            JSONObject item = new JSONObject();
            item.put("start", start);
            item.put("end", end);
            item.put("duration", durationMillis);
            item.put("targetHours", targetHours);
            item.put("waterMl", mWaterIntakeMl);

            array.put(item);
            mPrefs.edit().putString(KEY_FAST_HISTORY, array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void renderHistory() {
        String existingJson = mPrefs.getString(KEY_FAST_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(existingJson);
            if (array.length() == 0) {
                mTvHistoryLog.setText("No recorded fasting sessions yet.\nEngage the sentinel to begin telemetry logging.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject obj = array.getJSONObject(i);
                long start = obj.optLong("start");
                long end = obj.optLong("end");
                long dur = obj.optLong("duration");
                int tgt = obj.optInt("targetHours", 16);
                int water = obj.optInt("waterMl", 0);

                long hours = dur / (1000 * 3600);
                long minutes = (dur / (1000 * 60)) % 60;

                sb.append("• ").append(sdf.format(new Date(start))).append(" -> ").append(sdf.format(new Date(end))).append("\n")
                  .append("  Duration: ").append(hours).append("h ").append(minutes).append("m")
                  .append(" | Target: ").append(tgt).append("h")
                  .append(" | Water: ").append(water).append(" mL\n\n");
            }
            mTvHistoryLog.setText(sb.toString().trim());
        } catch (JSONException e) {
            mTvHistoryLog.setText("Error parsing history log.");
        }
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Clear History?")
                .setMessage("This will permanently erase all logged fasting records.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    mPrefs.edit().putString(KEY_FAST_HISTORY, "[]").apply();
                    renderHistory();
                    Toast.makeText(this, "Fasting history cleared.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportData() {
        JSONObject exportObj = new JSONObject();
        try {
            exportObj.put("version", 1);
            exportObj.put("isFasting", mIsFasting);
            exportObj.put("startTime", mStartTimeMillis);
            exportObj.put("targetHours", mTargetHours);
            exportObj.put("waterMl", mWaterIntakeMl);
            exportObj.put("history", new JSONArray(mPrefs.getString(KEY_FAST_HISTORY, "[]")));

            String jsonString = exportObj.toString(2);

            // Copy to clipboard
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("FastTrack_Backup", jsonString);
                clipboard.setPrimaryClip(clip);
            }

            // Share via real OS Intent chooser
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "FastTrack Sentinel Backup");
            shareIntent.putExtra(Intent.EXTRA_TEXT, jsonString);
            startActivity(Intent.createChooser(shareIntent, "Export Sentinel Data"));

            Toast.makeText(this, "Data exported & copied to clipboard!", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Failed to build export package.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Import JSON Telemetry");

        final EditText input = new EditText(this);
        input.setHint("Paste FastTrack JSON backup string here");
        input.setLines(6);
        input.setMaxLines(10);
        builder.setView(input);

        builder.setPositiveButton("Import", (dialog, which) -> {
            String jsonRaw = input.getText().toString().trim();
            if (jsonRaw.isEmpty()) {
                Toast.makeText(MainActivity.this, "Import content is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject obj = new JSONObject(jsonRaw);
                if (obj.has("history")) {
                    mPrefs.edit().putString(KEY_FAST_HISTORY, obj.getJSONArray("history").toString()).apply();
                }
                if (obj.has("targetHours")) {
                    mTargetHours = obj.getInt("targetHours");
                    mPrefs.edit().putInt(KEY_TARGET_HOURS, mTargetHours).apply();
                }
                if (obj.has("waterMl")) {
                    mWaterIntakeMl = obj.getInt("waterMl");
                    mPrefs.edit().putInt(KEY_WATER_ML, mWaterIntakeMl).apply();
                }
                loadSavedState();
                Toast.makeText(MainActivity.this, "Data successfully restored!", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Toast.makeText(MainActivity.this, "Invalid JSON structure.", Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            // Primary Action: Enter / Numpad Enter toggles fast
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                mBtnToggleFast.performClick();
                return true;
            }
            // Spacebar: Hydration increment 250ml
            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                mBtnAddWater250.performClick();
                return true;
            }
            // Plus Key: Increment target hour
            if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                if (mTargetHours < 36) {
                    mSbTargetHours.setProgress(mTargetHours + 1);
                }
                return true;
            }
            // Minus Key: Decrement target hour
            if (keyCode == KeyEvent.KEYCODE_MINUS) {
                if (mTargetHours > 1) {
                    mSbTargetHours.setProgress(mTargetHours - 1);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserves continuous runtime state without recreating activity on window resizing
        updateUiState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHandler != null && mTimerRunnable != null) {
            mHandler.removeCallbacks(mTimerRunnable);
        }
    }
}