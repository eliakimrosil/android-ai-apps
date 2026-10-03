package com.aistudio.stealthdropsecretpad;

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
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "stealth-drop-secret-pad_prefs";
    private static final String KEY_SAVED_NOTES_JSON = "saved_notes_json";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0: Auto, 1: Dark, 2: Light
    private static final String KEY_CURRENT_DRAFT = "current_draft_text";
    private static final String KEY_AUTO_WIPE_SECONDS = "auto_wipe_seconds";
    private static final String KEY_STEALTH_MODE_ENABLED = "stealth_mode_enabled";
    private static final String KEY_MASK_SENSITIVE = "mask_sensitive_text";

    // Header Views
    private TextView tvAppTitle;
    private TextView tvStatusSubtitle;
    private Button btnThemeToggle;
    private Button btnPanicWipe;

    // Hero/Scratchpad Card Views
    private EditText etSecretNote;
    private TextView tvCharCount;
    private TextView tvWordCount;
    private TextView tvSecurityHash;
    private TextView tvWipeTimerCountdown;
    private ProgressBar pbWipeTimer;

    // Scratchpad Action Buttons
    private Button btnCopyClipboard;
    private Button btnPasteClipboard;
    private Button btnShareNote;
    private Button btnSaveSecret;
    private Button btnClearDraft;

    // Ephemeral Controls Card Views
    private SeekBar sbAutoWipeDuration;
    private TextView tvAutoWipeDurationLabel;
    private Switch swStealthMode;
    private Switch swMaskContent;
    private CheckBox cbSelfDestructOnBackground;

    // Vault/History Section
    private TextView tvVaultHeader;
    private LinearLayout llSavedNotesContainer;
    private Button btnClearAllVault;

    // State variables
    private SharedPreferences prefs;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private CountDownTimer wipeCountdownTimer;
    private long wipeIntervalMillis = 60000; // default 60s
    private boolean isWipeTimerActive = false;
    private boolean isMasked = false;
    private boolean isStealthScreenActive = false;
    private int currentThemeMode = 0; // 0=Auto, 1=Dark, 2=Light

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure flags for locked screen and secure windows if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initSystemServices();
        initViews();
        loadSavedPreferences();
        setupListeners();
        renderSavedNotes();
        resetAndStartAutoWipeTimer();
    }

    private void initSystemServices() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        } catch (Exception ignored) {
            toneGenerator = null;
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void initViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle);
        tvStatusSubtitle = findViewById(R.id.tvStatusSubtitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnPanicWipe = findViewById(R.id.btnPanicWipe);

        etSecretNote = findViewById(R.id.etSecretNote);
        tvCharCount = findViewById(R.id.tvCharCount);
        tvWordCount = findViewById(R.id.tvWordCount);
        tvSecurityHash = findViewById(R.id.tvSecurityHash);
        tvWipeTimerCountdown = findViewById(R.id.tvWipeTimerCountdown);
        pbWipeTimer = findViewById(R.id.pbWipeTimer);

        btnCopyClipboard = findViewById(R.id.btnCopyClipboard);
        btnPasteClipboard = findViewById(R.id.btnPasteClipboard);
        btnShareNote = findViewById(R.id.btnShareNote);
        btnSaveSecret = findViewById(R.id.btnSaveSecret);
        btnClearDraft = findViewById(R.id.btnClearDraft);

        sbAutoWipeDuration = findViewById(R.id.sbAutoWipeDuration);
        tvAutoWipeDurationLabel = findViewById(R.id.tvAutoWipeDurationLabel);
        swStealthMode = findViewById(R.id.swStealthMode);
        swMaskContent = findViewById(R.id.swMaskContent);
        cbSelfDestructOnBackground = findViewById(R.id.cbSelfDestructOnBackground);

        tvVaultHeader = findViewById(R.id.tvVaultHeader);
        llSavedNotesContainer = findViewById(R.id.llSavedNotesContainer);
        btnClearAllVault = findViewById(R.id.btnClearAllVault);
    }

    private void loadSavedPreferences() {
        currentThemeMode = prefs.getInt(KEY_THEME_MODE, 0);
        updateThemeToggleLabel();

        int savedWipeSec = prefs.getInt(KEY_AUTO_WIPE_SECONDS, 60);
        if (savedWipeSec < 10) savedWipeSec = 10;
        if (savedWipeSec > 300) savedWipeSec = 300;
        sbAutoWipeDuration.setProgress(savedWipeSec);
        wipeIntervalMillis = savedWipeSec * 1000L;
        tvAutoWipeDurationLabel.setText(getString(R.string.wipe_timer_format, savedWipeSec));

        boolean stealth = prefs.getBoolean(KEY_STEALTH_MODE_ENABLED, false);
        swStealthMode.setChecked(stealth);
        applyWindowSecurity(stealth);

        boolean mask = prefs.getBoolean(KEY_MASK_SENSITIVE, false);
        swMaskContent.setChecked(mask);
        setMaskedState(mask);

        String savedDraft = prefs.getString(KEY_CURRENT_DRAFT, "");
        if (savedDraft != null && !savedDraft.isEmpty()) {
            etSecretNote.setText(savedDraft);
            etSecretNote.setSelection(savedDraft.length());
        }
        updateTextCounters(etSecretNote.getText().toString());
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnPanicWipe.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            triggerPanicWipe();
        });

        btnCopyClipboard.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            copyNoteToClipboard();
        });

        btnPasteClipboard.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            pasteFromClipboard();
        });

        btnShareNote.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareCurrentNote();
        });

        btnSaveSecret.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveNoteToVault();
        });

        btnClearDraft.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearDraftInput();
        });

        btnClearAllVault.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearAllVaultNotes();
        });

        swStealthMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(KEY_STEALTH_MODE_ENABLED, isChecked).apply();
            applyWindowSecurity(isChecked);
        });

        swMaskContent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(KEY_MASK_SENSITIVE, isChecked).apply();
            setMaskedState(isChecked);
        });

        sbAutoWipeDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 10) progress = 10;
                tvAutoWipeDurationLabel.setText(getString(R.string.wipe_timer_format, progress));
                wipeIntervalMillis = progress * 1000L;
                if (fromUser) {
                    prefs.edit().putInt(KEY_AUTO_WIPE_SECONDS, progress).apply();
                    resetAndStartAutoWipeTimer();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        });

        etSecretNote.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s != null ? s.toString() : "";
                updateTextCounters(text);
                prefs.edit().putString(KEY_CURRENT_DRAFT, text).apply();
                resetAndStartAutoWipeTimer();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateTextCounters(String content) {
        int charCount = content.length();
        int wordCount = content.trim().isEmpty() ? 0 : content.trim().split("\\s+").length;

        tvCharCount.setText(getString(R.string.counter_chars, charCount));
        tvWordCount.setText(getString(R.string.counter_words, wordCount));

        if (charCount == 0) {
            tvSecurityHash.setText(R.string.hash_idle);
        } else {
            String sha256Short = computeSha256(content);
            tvSecurityHash.setText(getString(R.string.hash_prefix, sha256Short));
        }
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, 6); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase(Locale.US);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private void setMaskedState(boolean masked) {
        isMasked = masked;
        if (masked) {
            etSecretNote.setTransformationMethod(new android.text.method.PasswordTransformationMethod());
        } else {
            etSecretNote.setTransformationMethod(null);
        }
        etSecretNote.setSelection(etSecretNote.getText().length());
    }

    private void applyWindowSecurity(boolean enabled) {
        isStealthScreenActive = enabled;
        if (enabled) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            tvStatusSubtitle.setText(R.string.stealth_active_shield);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            tvStatusSubtitle.setText(R.string.app_tagline);
        }
    }

    private void resetAndStartAutoWipeTimer() {
        if (wipeCountdownTimer != null) {
            wipeCountdownTimer.cancel();
        }

        if (wipeIntervalMillis <= 0) {
            tvWipeTimerCountdown.setText(R.string.wipe_timer_off);
            pbWipeTimer.setProgress(0);
            return;
        }

        pbWipeTimer.setMax((int) (wipeIntervalMillis / 1000));
        pbWipeTimer.setProgress((int) (wipeIntervalMillis / 1000));

        isWipeTimerActive = true;
        wipeCountdownTimer = new CountDownTimer(wipeIntervalMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secLeft = (int) (millisUntilFinished / 1000);
                tvWipeTimerCountdown.setText(getString(R.string.wipe_countdown_format, secLeft));
                pbWipeTimer.setProgress(secLeft);
            }

            @Override
            public void onFinish() {
                isWipeTimerActive = false;
                pbWipeTimer.setProgress(0);
                tvWipeTimerCountdown.setText(R.string.wipe_triggered);
                clearDraftInput();
                triggerHapticSoundAlert();
            }
        };
        wipeCountdownTimer.start();
    }

    private void copyNoteToClipboard() {
        String text = etSecretNote.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.msg_empty_note, Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("StealthSecret", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                String pasteContent = item.getText().toString();
                etSecretNote.setText(pasteContent);
                etSecretNote.setSelection(pasteContent.length());
                Toast.makeText(this, R.string.msg_pasted, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, R.string.msg_clipboard_empty, Toast.LENGTH_SHORT).show();
    }

    private void shareCurrentNote() {
        String text = etSecretNote.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.msg_empty_note, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_title));
        startActivity(shareIntent);
    }

    private void clearDraftInput() {
        etSecretNote.setText("");
        prefs.edit().remove(KEY_CURRENT_DRAFT).apply();
        updateTextCounters("");
        resetAndStartAutoWipeTimer();
    }

    private void saveNoteToVault() {
        String text = etSecretNote.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.msg_empty_note, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String savedJson = prefs.getString(KEY_SAVED_NOTES_JSON, "[]");
            JSONArray array = new JSONArray(savedJson);

            JSONObject noteObj = new JSONObject();
            noteObj.put("id", System.currentTimeMillis());
            noteObj.put("content", text);
            noteObj.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

            array.put(noteObj);
            prefs.edit().putString(KEY_SAVED_NOTES_JSON, array.toString()).apply();

            clearDraftInput();
            renderSavedNotes();
            Toast.makeText(this, R.string.msg_note_vaulted, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.msg_vault_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderSavedNotes() {
        llSavedNotesContainer.removeAllViews();
        try {
            String savedJson = prefs.getString(KEY_SAVED_NOTES_JSON, "[]");
            JSONArray array = new JSONArray(savedJson);

            int count = array.length();
            tvVaultHeader.setText(getString(R.string.vault_title_count, count));

            if (count == 0) {
                TextView emptyTv = new TextView(this);
                emptyTv.setText(R.string.vault_empty_notice);
                emptyTv.setTextSize(14f);
                emptyTv.setTextColor(getColor(R.color.m3_outline));
                emptyTv.setPadding(16, 24, 16, 24);
                llSavedNotesContainer.addView(emptyTv);
                return;
            }

            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject obj = array.getJSONObject(i);
                final long id = obj.optLong("id", 0);
                final String content = obj.optString("content", "");
                final String time = obj.optString("timestamp", "");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundResource(R.drawable.card_m3_high);
                card.setPadding(32, 28, 32, 28);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                cardParams.setMargins(0, 0, 0, 20);
                card.setLayoutParams(cardParams);

                TextView tvTime = new TextView(this);
                tvTime.setText(time);
                tvTime.setTextSize(11f);
                tvTime.setTextColor(getColor(R.color.m3_primary));
                tvTime.setTypeface(null, Typeface.BOLD);
                card.addView(tvTime);

                TextView tvBody = new TextView(this);
                String display = isMasked ? "••••••••••••••••••••" : content;
                tvBody.setText(display);
                tvBody.setTextSize(14f);
                tvBody.setTextColor(getColor(R.color.m3_on_surface));
                tvBody.setPadding(0, 8, 0, 16);
                card.addView(tvBody);

                // Row action buttons
                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);

                Button btnRestore = new Button(this);
                btnRestore.setText(R.string.btn_restore);
                btnRestore.setBackgroundResource(R.drawable.btn_m3_tonal);
                btnRestore.setTextColor(getColor(R.color.m3_primary));
                btnRestore.setTextSize(12f);
                btnRestore.setPadding(24, 8, 24, 8);
                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                p1.setMarginEnd(12);
                btnRestore.setLayoutParams(p1);
                btnRestore.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    etSecretNote.setText(content);
                    etSecretNote.setSelection(content.length());
                    Toast.makeText(MainActivity.this, R.string.msg_restored_to_scratchpad, Toast.LENGTH_SHORT).show();
                });
                actions.addView(btnRestore);

                Button btnDelete = new Button(this);
                btnDelete.setText(R.string.btn_delete);
                btnDelete.setBackgroundResource(R.drawable.btn_m3_outlined);
                btnDelete.setTextColor(getColor(R.color.m3_outline));
                btnDelete.setTextSize(12f);
                btnDelete.setPadding(24, 8, 24, 8);
                btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                btnDelete.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    deleteVaultNoteById(id);
                });
                actions.addView(btnDelete);

                card.addView(actions);
                llSavedNotesContainer.addView(card);
            }
        } catch (Exception e) {
            tvVaultHeader.setText(R.string.vault_title_error);
        }
    }

    private void deleteVaultNoteById(long id) {
        try {
            String savedJson = prefs.getString(KEY_SAVED_NOTES_JSON, "[]");
            JSONArray array = new JSONArray(savedJson);
            JSONArray newArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj.optLong("id") != id) {
                    newArray.put(obj);
                }
            }
            prefs.edit().putString(KEY_SAVED_NOTES_JSON, newArray.toString()).apply();
            renderSavedNotes();
            Toast.makeText(this, R.string.msg_vault_item_deleted, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private void clearAllVaultNotes() {
        prefs.edit().remove(KEY_SAVED_NOTES_JSON).apply();
        renderSavedNotes();
        Toast.makeText(this, R.string.msg_vault_cleared, Toast.LENGTH_SHORT).show();
    }

    private void triggerPanicWipe() {
        triggerHapticSoundAlert();

        clearDraftInput();
        clearAllVaultNotes();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip();
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            } catch (Exception ignored) {}
        }

        Toast.makeText(this, R.string.msg_panic_wiped, Toast.LENGTH_LONG).show();
    }

    private void triggerHapticSoundAlert() {
        try {
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(250);
                }
            }
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200);
            }
        } catch (Exception ignored) {}
    }

    private void cycleThemeMode() {
        currentThemeMode = (currentThemeMode + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, currentThemeMode).apply();
        updateThemeToggleLabel();
        applyThemeMode(currentThemeMode);
    }

    private void updateThemeToggleLabel() {
        if (currentThemeMode == 1) {
            btnThemeToggle.setText(R.string.theme_mode_dark);
        } else if (currentThemeMode == 2) {
            btnThemeToggle.setText(R.string.theme_mode_light);
        } else {
            btnThemeToggle.setText(R.string.theme_mode_auto);
        }
    }

    private void applyThemeMode(int mode) {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (mode == 1) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
            } else if (mode == 2) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
            } else {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            // Enter or Numpad Enter saves note to vault when not focused on multi-line typing
            if ((keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && event.isCtrlPressed()) {
                saveNoteToVault();
                return true;
            }
            // Spacebar with Alt toggles mask/reveal
            if (keyCode == KeyEvent.KEYCODE_SPACE && event.isAltPressed()) {
                swMaskContent.toggle();
                return true;
            }
            // +/- keys to adjust auto-wipe interval
            if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                int cur = sbAutoWipeDuration.getProgress();
                sbAutoWipeDuration.setProgress(Math.min(cur + 10, 300));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MINUS) {
                int cur = sbAutoWipeDuration.getProgress();
                sbAutoWipeDuration.setProgress(Math.max(cur - 10, 10));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Freeform desktop resizing retains live state dynamically
        renderSavedNotes();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cbSelfDestructOnBackground != null && cbSelfDestructOnBackground.isChecked()) {
            clearDraftInput();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wipeCountdownTimer != null) {
            wipeCountdownTimer.cancel();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}