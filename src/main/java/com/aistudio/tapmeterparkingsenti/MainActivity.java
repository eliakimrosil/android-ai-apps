package com.aistudio.tapmeterparkingsenti;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

    private static final String PREFS_NAME = "tap-meter-parking-sentinel_prefs";
    private static final String KEY_DURATION_MINUTES = "pref_duration_minutes";
    private static final String KEY_SPOT_LABEL = "pref_spot_label";
    private static final String KEY_NOTES = "pref_notes";
    private static final String KEY_RATE_PER_HR = "pref_rate_per_hr";
    private static final String KEY_START_TIME = "pref_start_time";
    private static final String KEY_END_TIME = "pref_end_time";
    private static final String KEY_IS_ACTIVE = "pref_is_active";
    private static final String KEY_ALERT_BUFFER = "pref_alert_buffer";
    private static final String KEY_SOUND_ALERT = "pref_sound_alert";
    private static final String KEY_VIBRATE_ALERT = "pref_vibrate_alert";
    private static final String KEY_HISTORY = "pref_session_history";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0: Auto, 1: Light, 2: Dark

    // Views
    private TextView tvStatusHeader;
    private TextView tvCountdownTimer;
    private TextView tvTimeTarget;
    private TextView tvEstimatedCost;
    private ProgressBar pbParkingProgress;

    private EditText etSpotLabel;
    private EditText etRatePerHour;
    private EditText etNotes;

    private TextView tvDurationDisplay;
    private Button btnMinus15;
    private Button btnMinus5;
    private Button btnPlus5;
    private Button btnPlus15;
    private Button btnPlus30;
    private Button btnPlus60;

    private SeekBar sbAlertBuffer;
    private TextView tvAlertBufferLabel;

    private Switch swSound;
    private Switch swVibrate;
    private Switch swKeepScreenOn;

    private Button btnStartParking;
    private Button btnStopParking;
    private Button btnExtend15;
    private Button btnShareSpot;
    private Button btnCopySpot;
    private Button btnClearHistory;
    private Button btnThemeToggle;

    private LinearLayout llHistoryContainer;

    // State Variables
    private int selectedDurationMinutes = 60;
    private int alertBufferMinutes = 5;
    private long parkingStartTime = 0;
    private long parkingEndTime = 0;
    private boolean isParkingActive = false;
    private boolean soundAlertEnabled = true;
    private boolean vibrateAlertEnabled = true;
    private boolean alertTriggered = false;
    private int currentThemeMode = 0; // 0=Auto, 1=Light, 2=Dark

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTick();
            if (isParkingActive) {
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn on screen on lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        setContentView(R.layout.activity_main);

        initViews();
        loadPreferences();
        setupListeners();
        renderHistory();

        if (isParkingActive) {
            timerHandler.post(timerRunnable);
        } else {
            updateIdleDisplay();
        }
    }

    private void initViews() {
        tvStatusHeader = findViewById(R.id.tvStatusHeader);
        tvCountdownTimer = findViewById(R.id.tvCountdownTimer);
        tvTimeTarget = findViewById(R.id.tvTimeTarget);
        tvEstimatedCost = findViewById(R.id.tvEstimatedCost);
        pbParkingProgress = findViewById(R.id.pbParkingProgress);

        etSpotLabel = findViewById(R.id.etSpotLabel);
        etRatePerHour = findViewById(R.id.etRatePerHour);
        etNotes = findViewById(R.id.etNotes);

        tvDurationDisplay = findViewById(R.id.tvDurationDisplay);
        btnMinus15 = findViewById(R.id.btnMinus15);
        btnMinus5 = findViewById(R.id.btnMinus5);
        btnPlus5 = findViewById(R.id.btnPlus5);
        btnPlus15 = findViewById(R.id.btnPlus15);
        btnPlus30 = findViewById(R.id.btnPlus30);
        btnPlus60 = findViewById(R.id.btnPlus60);

        sbAlertBuffer = findViewById(R.id.sbAlertBuffer);
        tvAlertBufferLabel = findViewById(R.id.tvAlertBufferLabel);

        swSound = findViewById(R.id.swSound);
        swVibrate = findViewById(R.id.swVibrate);
        swKeepScreenOn = findViewById(R.id.swKeepScreenOn);

        btnStartParking = findViewById(R.id.btnStartParking);
        btnStopParking = findViewById(R.id.btnStopParking);
        btnExtend15 = findViewById(R.id.btnExtend15);
        btnShareSpot = findViewById(R.id.btnShareSpot);
        btnCopySpot = findViewById(R.id.btnCopySpot);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        llHistoryContainer = findViewById(R.id.llHistoryContainer);
    }

    private void setupListeners() {
        btnMinus15.setOnClickListener(v -> adjustDuration(-15, v));
        btnMinus5.setOnClickListener(v -> adjustDuration(-5, v));
        btnPlus5.setOnClickListener(v -> adjustDuration(5, v));
        btnPlus15.setOnClickListener(v -> adjustDuration(15, v));
        btnPlus30.setOnClickListener(v -> adjustDuration(30, v));
        btnPlus60.setOnClickListener(v -> adjustDuration(60, v));

        sbAlertBuffer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                alertBufferMinutes = Math.max(1, progress);
                tvAlertBufferLabel.setText(getString(R.string.alert_buffer_format, alertBufferMinutes));
                savePreferences();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        swSound.setOnCheckedChangeListener((btn, isChecked) -> {
            soundAlertEnabled = isChecked;
            savePreferences();
        });

        swVibrate.setOnCheckedChangeListener((btn, isChecked) -> {
            vibrateAlertEnabled = isChecked;
            savePreferences();
        });

        swKeepScreenOn.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });

        btnStartParking.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            startParkingSession();
        });

        btnStopParking.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            stopParkingSession(false);
        });

        btnExtend15.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            extendParkingSession(15);
        });

        btnShareSpot.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareCurrentSpot();
        });

        btnCopySpot.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            copySpotToClipboard();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearParkingHistory();
        });

        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        TextWatcher saveWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                savePreferences();
                updateCostEstimation();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etSpotLabel.addTextChangedListener(saveWatcher);
        etRatePerHour.addTextChangedListener(saveWatcher);
        etNotes.addTextChangedListener(saveWatcher);
    }

    private void adjustDuration(int deltaMinutes, View triggerView) {
        triggerView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        selectedDurationMinutes = Math.max(5, Math.min(1440, selectedDurationMinutes + deltaMinutes));
        tvDurationDisplay.setText(getString(R.string.duration_min_format, selectedDurationMinutes));
        updateCostEstimation();
        savePreferences();
    }

    private void startParkingSession() {
        parkingStartTime = System.currentTimeMillis();
        parkingEndTime = parkingStartTime + (selectedDurationMinutes * 60L * 1000L);
        isParkingActive = true;
        alertTriggered = false;

        savePreferences();
        updateTick();

        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
    }

    private void stopParkingSession(boolean naturalExpiration) {
        if (!isParkingActive) return;

        saveToHistory(naturalExpiration);

        isParkingActive = false;
        parkingStartTime = 0;
        parkingEndTime = 0;
        alertTriggered = false;

        timerHandler.removeCallbacks(timerRunnable);
        savePreferences();
        updateIdleDisplay();
        renderHistory();
    }

    private void extendParkingSession(int minutes) {
        if (!isParkingActive) return;
        parkingEndTime += (minutes * 60L * 1000L);
        alertTriggered = false;
        savePreferences();
        updateTick();
    }

    private void updateTick() {
        if (!isParkingActive) {
            updateIdleDisplay();
            return;
        }

        long now = System.currentTimeMillis();
        long diff = parkingEndTime - now;
        long totalDuration = Math.max(1, parkingEndTime - parkingStartTime);
        long elapsed = now - parkingStartTime;

        int progress = (int) Math.min(100, Math.max(0, (elapsed * 100) / totalDuration));
        pbParkingProgress.setProgress(progress);

        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String expireTimeStr = timeFormat.format(new Date(parkingEndTime));
        tvTimeTarget.setText(getString(R.string.expires_at_format, expireTimeStr));

        if (diff > 0) {
            long hours = diff / (1000 * 60 * 60);
            long minutes = (diff / (1000 * 60)) % 60;
            long seconds = (diff / 1000) % 60;
            tvCountdownTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));

            long alertThresholdMs = alertBufferMinutes * 60L * 1000L;
            if (diff <= alertThresholdMs) {
                tvStatusHeader.setText(R.string.status_expiring_soon);
                tvCountdownTimer.setTextColor(getColor(android.R.color.holo_red_light));
                if (!alertTriggered) {
                    alertTriggered = true;
                    triggerAlertFeedback();
                }
            } else {
                tvStatusHeader.setText(R.string.status_parked_active);
                tvCountdownTimer.setTextColor(getColor(android.R.color.white));
            }
        } else {
            // Expired
            long over = Math.abs(diff);
            long hours = over / (1000 * 60 * 60);
            long minutes = (over / (1000 * 60)) % 60;
            long seconds = (over / 1000) % 60;

            tvStatusHeader.setText(R.string.status_meter_expired);
            tvCountdownTimer.setTextColor(getColor(android.R.color.holo_red_dark));
            tvCountdownTimer.setText(String.format(Locale.getDefault(), "+%02d:%02d:%02d", hours, minutes, seconds));

            if (!alertTriggered) {
                alertTriggered = true;
                triggerAlertFeedback();
            }
        }

        btnStartParking.setEnabled(false);
        btnStopParking.setEnabled(true);
        btnExtend15.setEnabled(true);

        updateCostEstimation();
    }

    private void updateIdleDisplay() {
        tvStatusHeader.setText(R.string.status_meter_standby);
        tvCountdownTimer.setTextColor(getColor(android.R.color.white));
        tvCountdownTimer.setText(String.format(Locale.getDefault(), "%02d:00:00", selectedDurationMinutes / 60));
        tvTimeTarget.setText(R.string.no_active_session);
        pbParkingProgress.setProgress(0);

        btnStartParking.setEnabled(true);
        btnStopParking.setEnabled(false);
        btnExtend15.setEnabled(false);

        updateCostEstimation();
    }

    private void updateCostEstimation() {
        String rateStr = etRatePerHour.getText().toString().trim();
        double rate = 0.0;
        if (!rateStr.isEmpty()) {
            try {
                rate = Double.parseDouble(rateStr);
            } catch (NumberFormatException ignored) {}
        }

        double durationHours = selectedDurationMinutes / 60.0;
        if (isParkingActive && parkingEndTime > parkingStartTime) {
            durationHours = (parkingEndTime - parkingStartTime) / (1000.0 * 60.0 * 60.0);
        }

        double totalCost = rate * durationHours;
        tvEstimatedCost.setText(String.format(Locale.getDefault(), "$%.2f", totalCost));
    }

    private void triggerAlertFeedback() {
        if (vibrateAlertEnabled) {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 400, 200, 400}, -1);
                }
            }
        }

        if (soundAlertEnabled) {
            try {
                Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notificationUri);
                if (r != null) {
                    r.play();
                } else {
                    ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
                }
            } catch (Exception ignored) {}
        }
    }

    private void shareCurrentSpot() {
        String spot = etSpotLabel.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();

        StringBuilder sb = new StringBuilder();
        sb.append("🚗 Parking Sentinel Details:\n");
        sb.append("Spot/Level: ").append(spot.isEmpty() ? "Unspecified" : spot).append("\n");
        if (isParkingActive) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault());
            sb.append("Expires: ").append(sdf.format(new Date(parkingEndTime))).append("\n");
        }
        if (!notes.isEmpty()) {
            sb.append("Notes: ").append(notes).append("\n");
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share Parking Spot");
        startActivity(shareIntent);
    }

    private void copySpotToClipboard() {
        String spot = etSpotLabel.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();
        String clip = "Spot: " + (spot.isEmpty() ? "N/A" : spot) + " | Notes: " + (notes.isEmpty() ? "N/A" : notes);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData data = ClipData.newPlainText("Parking Sentinel Spot", clip);
            clipboard.setPrimaryClip(data);
            Toast.makeText(this, "Parking info copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToHistory(boolean naturalExpiration) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyRaw = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(historyRaw);
            JSONObject item = new JSONObject();
            item.put("spot", etSpotLabel.getText().toString().trim());
            item.put("notes", etNotes.getText().toString().trim());
            item.put("start", parkingStartTime);
            item.put("end", parkingEndTime);
            item.put("completed", naturalExpiration);
            item.put("rate", etRatePerHour.getText().toString().trim());

            // Prepend new history record
            JSONArray newArr = new JSONArray();
            newArr.put(item);
            for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                newArr.put(arr.get(i));
            }

            prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    private void renderHistory() {
        llHistoryContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyRaw = prefs.getString(KEY_HISTORY, "[]");

        try {
            JSONArray arr = new JSONArray(historyRaw);
            if (arr.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText(R.string.no_parking_history);
                empty.setTextColor(getColor(android.R.color.darker_gray));
                empty.setPadding(12, 16, 12, 16);
                llHistoryContainer.addView(empty);
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String spot = obj.optString("spot", "Standard Spot");
                if (spot.isEmpty()) spot = "Standard Spot";
                String notes = obj.optString("notes", "");
                long start = obj.optLong("start", 0);
                long end = obj.optLong("end", 0);

                long mins = Math.max(1, (end - start) / (1000 * 60));

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundResource(R.drawable.card_m3_high);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(0, 0, 0, 16);
                card.setLayoutParams(lp);
                card.setPadding(24, 20, 24, 20);

                TextView tvHeader = new TextView(this);
                tvHeader.setText(String.format(Locale.getDefault(), "📍 %s (%d mins)", spot, mins));
                tvHeader.setTextSize(15f);
                tvHeader.setTextColor(getColor(android.R.color.white));
                card.addView(tvHeader);

                TextView tvSub = new TextView(this);
                tvSub.setText(String.format(Locale.getDefault(), "Parked: %s", sdf.format(new Date(start))));
                tvSub.setTextSize(12f);
                tvSub.setTextColor(getColor(android.R.color.darker_gray));
                card.addView(tvSub);

                if (!notes.isEmpty()) {
                    TextView tvNote = new TextView(this);
                    tvNote.setText(notes);
                    tvNote.setTextSize(12f);
                    tvNote.setTextColor(getColor(android.R.color.white));
                    tvNote.setPadding(0, 8, 0, 0);
                    card.addView(tvNote);
                }

                llHistoryContainer.addView(card);
            }
        } catch (JSONException ignored) {}
    }

    private void clearParkingHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_HISTORY, "[]").apply();
        renderHistory();
        Toast.makeText(this, "Parking history cleared", Toast.LENGTH_SHORT).show();
    }

    private void cycleThemeMode() {
        currentThemeMode = (currentThemeMode + 1) % 3;
        applyThemeMode(currentThemeMode);
        savePreferences();
    }

    private void applyThemeMode(int mode) {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                switch (mode) {
                    case 1:
                        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
                        btnThemeToggle.setText(R.string.theme_light);
                        break;
                    case 2:
                        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
                        btnThemeToggle.setText(R.string.theme_dark);
                        break;
                    default:
                        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
                        btnThemeToggle.setText(R.string.theme_auto);
                        break;
                }
            } else {
                btnThemeToggle.setText(mode == 1 ? R.string.theme_light : (mode == 2 ? R.string.theme_dark : R.string.theme_auto));
            }
        }
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_DURATION_MINUTES, selectedDurationMinutes);
        editor.putInt(KEY_ALERT_BUFFER, alertBufferMinutes);
        editor.putString(KEY_SPOT_LABEL, etSpotLabel.getText().toString());
        editor.putString(KEY_RATE_PER_HR, etRatePerHour.getText().toString());
        editor.putString(KEY_NOTES, etNotes.getText().toString());
        editor.putLong(KEY_START_TIME, parkingStartTime);
        editor.putLong(KEY_END_TIME, parkingEndTime);
        editor.putBoolean(KEY_IS_ACTIVE, isParkingActive);
        editor.putBoolean(KEY_SOUND_ALERT, soundAlertEnabled);
        editor.putBoolean(KEY_VIBRATE_ALERT, vibrateAlertEnabled);
        editor.putInt(KEY_THEME_MODE, currentThemeMode);
        editor.apply();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedDurationMinutes = prefs.getInt(KEY_DURATION_MINUTES, 60);
        alertBufferMinutes = prefs.getInt(KEY_ALERT_BUFFER, 5);
        parkingStartTime = prefs.getLong(KEY_START_TIME, 0);
        parkingEndTime = prefs.getLong(KEY_END_TIME, 0);
        isParkingActive = prefs.getBoolean(KEY_IS_ACTIVE, false);
        soundAlertEnabled = prefs.getBoolean(KEY_SOUND_ALERT, true);
        vibrateAlertEnabled = prefs.getBoolean(KEY_VIBRATE_ALERT, true);
        currentThemeMode = prefs.getInt(KEY_THEME_MODE, 0);

        etSpotLabel.setText(prefs.getString(KEY_SPOT_LABEL, ""));
        etRatePerHour.setText(prefs.getString(KEY_RATE_PER_HR, "2.50"));
        etNotes.setText(prefs.getString(KEY_NOTES, ""));

        tvDurationDisplay.setText(getString(R.string.duration_min_format, selectedDurationMinutes));
        sbAlertBuffer.setProgress(alertBufferMinutes);
        tvAlertBufferLabel.setText(getString(R.string.alert_buffer_format, alertBufferMinutes));

        swSound.setChecked(soundAlertEnabled);
        swVibrate.setChecked(vibrateAlertEnabled);

        applyThemeMode(currentThemeMode);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (!isParkingActive) {
                        btnStartParking.performClick();
                    } else {
                        btnStopParking.performClick();
                    }
                    return true;
                case KeyEvent.KEYCODE_SPACE:
                    if (isParkingActive) {
                        btnExtend15.performClick();
                    } else {
                        adjustDuration(15, btnPlus15);
                    }
                    return true;
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_EQUALS:
                    adjustDuration(5, btnPlus5);
                    return true;
                case KeyEvent.KEYCODE_MINUS:
                    adjustDuration(-5, btnMinus5);
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserves memory states without recreation in Desktop/Freeform mode
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
    }
}