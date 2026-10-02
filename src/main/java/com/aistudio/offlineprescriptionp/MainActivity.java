package com.aistudio.offlineprescriptionp;

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
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private static final String PREFS_NAME = "offline-prescription-pill-refill-sentinel_prefs";
    private static final String KEY_MEDICATIONS_JSON = "saved_medications_json";
    private static final String KEY_THEME_MODE = "saved_theme_mode"; // 0: Auto/System, 1: Light, 2: Dark

    // Data Model
    public static class Medication {
        String id;
        String name;
        String dosage;
        int currentCount;
        int dailyDose;
        int refillThreshold;
        long lastTakenTimestamp;

        public Medication(String id, String name, String dosage, int currentCount, int dailyDose, int refillThreshold, long lastTakenTimestamp) {
            this.id = id;
            this.name = name;
            this.dosage = dosage;
            this.currentCount = Math.max(0, currentCount);
            this.dailyDose = Math.max(1, dailyDose);
            this.refillThreshold = Math.max(1, refillThreshold);
            this.lastTakenTimestamp = lastTakenTimestamp;
        }

        public int getDaysRemaining() {
            if (dailyDose <= 0) return 0;
            return currentCount / dailyDose;
        }

        public boolean isRefillUrgent() {
            return currentCount <= refillThreshold;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name);
            obj.put("dosage", dosage);
            obj.put("currentCount", currentCount);
            obj.put("dailyDose", dailyDose);
            obj.put("refillThreshold", refillThreshold);
            obj.put("lastTakenTimestamp", lastTakenTimestamp);
            return obj;
        }

        public static Medication fromJson(JSONObject obj) {
            return new Medication(
                    obj.optString("id", String.valueOf(System.currentTimeMillis())),
                    obj.optString("name", "Unnamed Med"),
                    obj.optString("dosage", "1 tablet"),
                    obj.optInt("currentCount", 30),
                    obj.optInt("dailyDose", 1),
                    obj.optInt("refillThreshold", 7),
                    obj.optLong("lastTakenTimestamp", 0L)
            );
        }
    }

    // Views
    private ScrollView rootScrollView;
    private Button btnThemeToggle;
    private TextView tvSentinelStatus;
    private TextView tvTotalMedsCount;
    private TextView tvCriticalRefillsCount;
    private EditText etMedName;
    private EditText etDosage;
    private EditText etCurrentCount;
    private EditText etDailyDose;
    private SeekBar seekRefillThreshold;
    private TextView tvThresholdValue;
    private Button btnSaveMedication;
    private Button btnQuickDoseSelected;
    private Button btnExportReport;
    private LinearLayout containerMedicationsList;

    // State
    private final ArrayList<Medication> medicationList = new ArrayList<>();
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private int currentThemeMode = 0; // 0 = Auto, 1 = Light, 2 = Dark
    private int selectedMedicationIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        currentThemeMode = prefs.getInt(KEY_THEME_MODE, 0);

        initializeViews();
        setupListeners();
        loadMedicationsFromStorage();
        updateSentinelDashboard();
        renderMedicationList();
        updateThemeToggleLabel();
    }

    private void initializeViews() {
        rootScrollView = findViewById(R.id.rootScrollView);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        tvSentinelStatus = findViewById(R.id.tvSentinelStatus);
        tvTotalMedsCount = findViewById(R.id.tvTotalMedsCount);
        tvCriticalRefillsCount = findViewById(R.id.tvCriticalRefillsCount);
        etMedName = findViewById(R.id.etMedName);
        etDosage = findViewById(R.id.etDosage);
        etCurrentCount = findViewById(R.id.etCurrentCount);
        etDailyDose = findViewById(R.id.etDailyDose);
        seekRefillThreshold = findViewById(R.id.seekRefillThreshold);
        tvThresholdValue = findViewById(R.id.tvThresholdValue);
        btnSaveMedication = findViewById(R.id.btnSaveMedication);
        btnQuickDoseSelected = findViewById(R.id.btnQuickDoseSelected);
        btnExportReport = findViewById(R.id.btnExportReport);
        containerMedicationsList = findViewById(R.id.containerMedicationsList);

        seekRefillThreshold.setMax(30);
        seekRefillThreshold.setProgress(7);
        tvThresholdValue.setText(getString(R.string.threshold_days_format, 7));
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        seekRefillThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int threshold = Math.max(1, progress);
                tvThresholdValue.setText(getString(R.string.threshold_days_format, threshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSaveMedication.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveNewMedicationFromInput();
        });

        btnQuickDoseSelected.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            triggerQuickDosePrimary();
        });

        btnExportReport.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportAuditReport();
        });
    }

    private void cycleThemeMode() {
        currentThemeMode = (currentThemeMode + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, currentThemeMode).apply();
        applyThemeMode(currentThemeMode);
        updateThemeToggleLabel();
    }

    private void applyThemeMode(int mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            if (uiModeManager != null) {
                switch (mode) {
                    case 1:
                        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
                        break;
                    case 2:
                        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
                        break;
                    case 0:
                    default:
                        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
                        break;
                }
            }
        }
    }

    private void updateThemeToggleLabel() {
        switch (currentThemeMode) {
            case 1:
                btnThemeToggle.setText(R.string.theme_light);
                break;
            case 2:
                btnThemeToggle.setText(R.string.theme_dark);
                break;
            case 0:
            default:
                btnThemeToggle.setText(R.string.theme_auto);
                break;
        }
    }

    private void saveNewMedicationFromInput() {
        String name = etMedName.getText().toString().trim();
        String dosage = etDosage.getText().toString().trim();
        String countStr = etCurrentCount.getText().toString().trim();
        String doseStr = etDailyDose.getText().toString().trim();
        int threshold = Math.max(1, seekRefillThreshold.getProgress());

        if (TextUtils.isEmpty(name)) {
            etMedName.setError(getString(R.string.error_required));
            etMedName.requestFocus();
            return;
        }

        int currentCount = 30;
        if (!TextUtils.isEmpty(countStr)) {
            try {
                currentCount = Integer.parseInt(countStr);
            } catch (NumberFormatException ignored) {}
        }

        int dailyDose = 1;
        if (!TextUtils.isEmpty(doseStr)) {
            try {
                dailyDose = Integer.parseInt(doseStr);
            } catch (NumberFormatException ignored) {}
        }
        if (dailyDose <= 0) dailyDose = 1;

        if (TextUtils.isEmpty(dosage)) {
            dosage = "1 dose";
        }

        String id = "med_" + System.currentTimeMillis();
        Medication newMed = new Medication(id, name, dosage, currentCount, dailyDose, threshold, 0L);
        medicationList.add(0, newMed);
        selectedMedicationIndex = 0;

        etMedName.setText("");
        etDosage.setText("");
        etCurrentCount.setText("");
        etDailyDose.setText("");

        saveMedicationsToStorage();
        updateSentinelDashboard();
        renderMedicationList();
        playAffirmationHapticAndTone();
        Toast.makeText(this, getString(R.string.med_added, name), Toast.LENGTH_SHORT).show();
    }

    private void triggerQuickDosePrimary() {
        if (medicationList.isEmpty()) {
            Toast.makeText(this, R.string.no_meds_recorded, Toast.LENGTH_SHORT).show();
            return;
        }

        int targetIndex = (selectedMedicationIndex >= 0 && selectedMedicationIndex < medicationList.size()) ? selectedMedicationIndex : 0;
        recordDoseTaken(targetIndex);
    }

    private void recordDoseTaken(int index) {
        if (index < 0 || index >= medicationList.size()) return;
        Medication med = medicationList.get(index);
        if (med.currentCount <= 0) {
            playUrgentAlert();
            Toast.makeText(this, getString(R.string.depleted_alert, med.name), Toast.LENGTH_LONG).show();
            return;
        }

        med.currentCount = Math.max(0, med.currentCount - med.dailyDose);
        med.lastTakenTimestamp = System.currentTimeMillis();

        if (med.isRefillUrgent()) {
            playUrgentAlert();
            Toast.makeText(this, getString(R.string.refill_needed_toast, med.name, med.currentCount), Toast.LENGTH_LONG).show();
        } else {
            playAffirmationHapticAndTone();
            Toast.makeText(this, getString(R.string.dose_logged, med.name, med.currentCount), Toast.LENGTH_SHORT).show();
        }

        saveMedicationsToStorage();
        updateSentinelDashboard();
        renderMedicationList();
    }

    private void adjustMedicationInventory(int index, int delta) {
        if (index < 0 || index >= medicationList.size()) return;
        Medication med = medicationList.get(index);
        med.currentCount = Math.max(0, med.currentCount + delta);
        saveMedicationsToStorage();
        updateSentinelDashboard();
        renderMedicationList();
    }

    private void removeMedication(int index) {
        if (index < 0 || index >= medicationList.size()) return;
        String name = medicationList.get(index).name;
        medicationList.remove(index);
        if (selectedMedicationIndex >= medicationList.size()) {
            selectedMedicationIndex = medicationList.size() - 1;
        }
        saveMedicationsToStorage();
        updateSentinelDashboard();
        renderMedicationList();
        Toast.makeText(this, getString(R.string.removed_med, name), Toast.LENGTH_SHORT).show();
    }

    private void updateSentinelDashboard() {
        int total = medicationList.size();
        int urgentCount = 0;
        for (Medication m : medicationList) {
            if (m.isRefillUrgent()) {
                urgentCount++;
            }
        }

        tvTotalMedsCount.setText(String.valueOf(total));
        tvCriticalRefillsCount.setText(String.valueOf(urgentCount));

        if (urgentCount > 0) {
            tvSentinelStatus.setText(getString(R.string.sentinel_warning, urgentCount));
            tvSentinelStatus.setTextColor(getResources().getColor(R.color.m3_error, getTheme()));
        } else if (total > 0) {
            tvSentinelStatus.setText(R.string.sentinel_all_healthy);
            tvSentinelStatus.setTextColor(getResources().getColor(R.color.m3_primary, getTheme()));
        } else {
            tvSentinelStatus.setText(R.string.sentinel_idle);
            tvSentinelStatus.setTextColor(getResources().getColor(R.color.m3_outline, getTheme()));
        }
    }

    private void renderMedicationList() {
        containerMedicationsList.removeAllViews();

        if (medicationList.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(R.string.empty_medication_state);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(32, 48, 32, 48);
            emptyView.setTextColor(getResources().getColor(R.color.m3_outline, getTheme()));
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            containerMedicationsList.addView(emptyView);
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        for (int i = 0; i < medicationList.size(); i++) {
            final int index = i;
            final Medication med = medicationList.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_m3_high);
            card.setElevation(dpToPx(2));
            card.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, dpToPx(12));
            card.setLayoutParams(cardParams);

            if (index == selectedMedicationIndex) {
                card.setBackgroundResource(R.drawable.card_m3_selected);
            }

            // Top Header: Name, Dosage, Days badge
            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout titleCol = new LinearLayout(this);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams titleColParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            titleCol.setLayoutParams(titleColParams);

            TextView tvName = new TextView(this);
            tvName.setText(med.name);
            tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tvName.setTypeface(Typeface.DEFAULT_BOLD);
            tvName.setTextColor(getResources().getColor(R.color.m3_on_surface, getTheme()));

            TextView tvSub = new TextView(this);
            tvSub.setText(getString(R.string.med_details_format, med.dosage, med.dailyDose));
            tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvSub.setTextColor(getResources().getColor(R.color.m3_outline, getTheme()));

            titleCol.addView(tvName);
            titleCol.addView(tvSub);

            // Refill alert pill / Days count
            TextView tvStatusPill = new TextView(this);
            int days = med.getDaysRemaining();
            tvStatusPill.setText(getString(R.string.days_left_pill, days));
            tvStatusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvStatusPill.setTypeface(Typeface.DEFAULT_BOLD);
            tvStatusPill.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));

            if (med.isRefillUrgent()) {
                tvStatusPill.setBackgroundResource(R.drawable.chip_warning);
                tvStatusPill.setTextColor(getResources().getColor(R.color.m3_on_error, getTheme()));
            } else {
                tvStatusPill.setBackgroundResource(R.drawable.chip_active);
                tvStatusPill.setTextColor(getResources().getColor(R.color.m3_on_primary, getTheme()));
            }

            headerRow.addView(titleCol);
            headerRow.addView(tvStatusPill);
            card.addView(headerRow);

            // Count info and Last Taken
            LinearLayout infoRow = new LinearLayout(this);
            infoRow.setOrientation(LinearLayout.HORIZONTAL);
            infoRow.setGravity(Gravity.CENTER_VERTICAL);
            infoRow.setPadding(0, dpToPx(8), 0, dpToPx(8));

            TextView tvInventory = new TextView(this);
            tvInventory.setText(getString(R.string.stock_count_format, med.currentCount, med.refillThreshold));
            tvInventory.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tvInventory.setTextColor(getResources().getColor(R.color.m3_on_surface, getTheme()));
            LinearLayout.LayoutParams invParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            tvInventory.setLayoutParams(invParams);

            TextView tvLastTaken = new TextView(this);
            String lastTakenStr = med.lastTakenTimestamp > 0
                    ? getString(R.string.last_taken_format, dateFormat.format(new Date(med.lastTakenTimestamp)))
                    : getString(R.string.never_taken);
            tvLastTaken.setText(lastTakenStr);
            tvLastTaken.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvLastTaken.setTextColor(getResources().getColor(R.color.m3_outline, getTheme()));

            infoRow.addView(tvInventory);
            infoRow.addView(tvLastTaken);
            card.addView(infoRow);

            // Action row: -1, +1, Dose Taken, Delete
            LinearLayout actionsRow = new LinearLayout(this);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setGravity(Gravity.CENTER_VERTICAL);

            Button btnMinus = createPillButton("-", R.drawable.btn_stepper);
            btnMinus.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                adjustMedicationInventory(index, -1);
            });

            Button btnPlus = createPillButton("+", R.drawable.btn_stepper);
            btnPlus.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                adjustMedicationInventory(index, +1);
            });

            Button btnDose = createPillButton(getString(R.string.take_dose), R.drawable.btn_m3_primary);
            btnDose.setTextColor(getResources().getColor(R.color.m3_on_primary, getTheme()));
            btnDose.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                selectedMedicationIndex = index;
                recordDoseTaken(index);
            });

            Button btnDelete = createPillButton(getString(R.string.delete_btn), R.drawable.btn_m3_outlined);
            btnDelete.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                removeMedication(index);
            });

            LinearLayout.LayoutParams btnDoseParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            btnDoseParams.setMargins(dpToPx(6), 0, dpToPx(6), 0);
            btnDose.setLayoutParams(btnDoseParams);

            actionsRow.addView(btnMinus);
            actionsRow.addView(btnPlus);
            actionsRow.addView(btnDose);
            actionsRow.addView(btnDelete);

            card.addView(actionsRow);

            card.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                selectedMedicationIndex = index;
                renderMedicationList();
            });

            containerMedicationsList.addView(card);
        }
    }

    private Button createPillButton(String text, int backgroundRes) {
        Button b = new Button(this);
        b.setText(text);
        b.setBackgroundResource(backgroundRes);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setAllCaps(false);
        b.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
        b.setMinHeight(dpToPx(38));
        b.setMinimumHeight(dpToPx(38));
        return b;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void playAffirmationHapticAndTone() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(45);
            }
        }
    }

    private void playUrgentAlert() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] pattern = {0, 150, 100, 200};
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(300);
            }
        }
        try {
            Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notificationUri);
            if (r != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes attributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    r.setAudioAttributes(attributes);
                }
                r.play();
            }
        } catch (Exception ignored) {}
    }

    private void exportAuditReport() {
        if (medicationList.isEmpty()) {
            Toast.makeText(this, R.string.no_meds_recorded, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== DOSEGUARD OFFLINE AUDIT & REFILL REPORT ===\n");
        sb.append("Generated on: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");

        int critical = 0;
        for (Medication m : medicationList) {
            sb.append("• ").append(m.name).append(" (").append(m.dosage).append(")\n");
            sb.append("   - Remaining: ").append(m.currentCount).append(" units\n");
            sb.append("   - Daily Consumption: ").append(m.dailyDose).append(" units/day\n");
            sb.append("   - Est. Days Remaining: ").append(m.getDaysRemaining()).append(" days\n");
            sb.append("   - Refill Threshold: ").append(m.refillThreshold).append(" units\n");
            if (m.isRefillUrgent()) {
                sb.append("   - [!] STATUS: REFILL CRITICAL NEEDED\n");
                critical++;
            } else {
                sb.append("   - STATUS: OK\n");
            }
            sb.append("\n");
        }

        sb.append("Summary: ").append(medicationList.size()).append(" monitored medications. ")
          .append(critical).append(" require urgent pharmacy refill.");

        String report = sb.toString();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("DoseGuard Report", report);
            clipboard.setPrimaryClip(clip);
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "DoseGuard Prescription Refill Sentinel Report");
        sendIntent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_report_title)));

        Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show();
    }

    private void loadMedicationsFromStorage() {
        medicationList.clear();
        String json = prefs.getString(KEY_MEDICATIONS_JSON, null);
        if (!TextUtils.isEmpty(json)) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    medicationList.add(Medication.fromJson(arr.getJSONObject(i)));
                }
            } catch (JSONException ignored) {}
        }

        // Demo seed if clean installation
        if (medicationList.isEmpty()) {
            medicationList.add(new Medication("seed_1", "Atorvastatin", "20mg Capsule", 14, 1, 7, System.currentTimeMillis() - 86400000L));
            medicationList.add(new Medication("seed_2", "Lisinopril", "10mg Tablet", 4, 1, 7, System.currentTimeMillis() - 43200000L));
            medicationList.add(new Medication("seed_3", "Metformin", "500mg Tablet", 45, 2, 10, System.currentTimeMillis() - 12000000L));
            saveMedicationsToStorage();
        }
    }

    private void saveMedicationsToStorage() {
        JSONArray arr = new JSONArray();
        for (Medication m : medicationList) {
            try {
                arr.put(m.toJson());
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_MEDICATIONS_JSON, arr.toString()).apply();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (getCurrentFocus() instanceof EditText) {
                    saveNewMedicationFromInput();
                    return true;
                } else {
                    triggerQuickDosePrimary();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                if (!(getCurrentFocus() instanceof EditText)) {
                    triggerQuickDosePrimary();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS || keyCode == KeyEvent.KEYCODE_NUMPAD_ADD) {
                if (!(getCurrentFocus() instanceof EditText) && !medicationList.isEmpty()) {
                    int target = (selectedMedicationIndex >= 0 && selectedMedicationIndex < medicationList.size()) ? selectedMedicationIndex : 0;
                    adjustMedicationInventory(target, +1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_MINUS || keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) {
                if (!(getCurrentFocus() instanceof EditText) && !medicationList.isEmpty()) {
                    int target = (selectedMedicationIndex >= 0 && selectedMedicationIndex < medicationList.size()) ? selectedMedicationIndex : 0;
                    adjustMedicationInventory(target, -1);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserves UI tree state & scroll positions seamlessly across desktop window resize
        renderMedicationList();
        updateSentinelDashboard();
    }
}