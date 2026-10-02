package com.aistudio.offlinefuellogtracke;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
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

    private static final String PREFS_NAME = "offline-fuel-log-tracker_prefs";
    private static final String KEY_LOGS = "fuel_logs_json";
    private static final String KEY_UNIT_SYSTEM = "unit_system"; // 0: Metric (km, L, L/100km), 1: Imperial (mi, gal, MPG)
    private static final String KEY_CURRENCY = "currency_symbol";
    private static final String KEY_THEME_MODE = "ui_theme_mode"; // 0: Auto, 1: Light, 2: Dark
    private static final String KEY_PRICE_PER_UNIT = "draft_price_unit";

    // Data structures
    public static class FuelEntry {
        public long timestamp;
        public double odometer;
        public double fuelAmount;
        public double totalCost;
        public boolean isFullTank;
        public String notes;

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("timestamp", timestamp);
            obj.put("odometer", odometer);
            obj.put("fuelAmount", fuelAmount);
            obj.put("totalCost", totalCost);
            obj.put("isFullTank", isFullTank);
            obj.put("notes", notes != null ? notes : "");
            return obj;
        }

        public static FuelEntry fromJson(JSONObject obj) {
            FuelEntry entry = new FuelEntry();
            entry.timestamp = obj.optLong("timestamp", System.currentTimeMillis());
            entry.odometer = obj.optDouble("odometer", 0.0);
            entry.fuelAmount = obj.optDouble("fuelAmount", 0.0);
            entry.totalCost = obj.optDouble("totalCost", 0.0);
            entry.isFullTank = obj.optBoolean("isFullTank", true);
            entry.notes = obj.optString("notes", "");
            return entry;
        }
    }

    private final List<FuelEntry> fuelEntries = new ArrayList<>();
    private SharedPreferences prefs;
    private UiModeManager uiModeManager;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;

    // State Variables
    private int currentUnitSystem = 0; // 0 = Metric (L, km), 1 = US Imperial (gal, mi)
    private int currentThemeMode = 0;   // 0 = Auto, 1 = Light, 2 = Dark
    private String currencySymbol = "$";

    // Views
    private ScrollView mainScrollView;
    private Button btnThemeToggle;
    private Button btnUnitMetric;
    private Button btnUnitImperial;
    private Button btnExportData;
    private Button btnClearAll;

    // Metrics Dashboard
    private TextView tvAvgConsumption;
    private TextView tvAvgConsumptionLabel;
    private TextView tvTotalDistance;
    private TextView tvTotalFuel;
    private TextView tvTotalSpent;
    private TextView tvCostPerDistance;

    // Input fields
    private EditText etOdometer;
    private EditText etFuelVolume;
    private EditText etPricePerUnit;
    private EditText etTotalCost;
    private EditText etNotes;
    private Switch switchFullTank;
    private Button btnStepOdoMinus;
    private Button btnStepOdoPlus;
    private Button btnStepFuelMinus;
    private Button btnStepFuelPlus;
    private Button btnAddLog;

    // Log History List
    private LinearLayout layoutHistoryContainer;
    private TextView tvEmptyHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65);
        } catch (Exception ignored) {
            toneGenerator = null;
        }

        bindViews();
        loadPreferences();
        setupListeners();
        renderDashboardAndHistory();
    }

    private void bindViews() {
        mainScrollView = findViewById(R.id.mainScrollView);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnUnitMetric = findViewById(R.id.btnUnitMetric);
        btnUnitImperial = findViewById(R.id.btnUnitImperial);
        btnExportData = findViewById(R.id.btnExportData);
        btnClearAll = findViewById(R.id.btnClearAll);

        tvAvgConsumption = findViewById(R.id.tvAvgConsumption);
        tvAvgConsumptionLabel = findViewById(R.id.tvAvgConsumptionLabel);
        tvTotalDistance = findViewById(R.id.tvTotalDistance);
        tvTotalFuel = findViewById(R.id.tvTotalFuel);
        tvTotalSpent = findViewById(R.id.tvTotalSpent);
        tvCostPerDistance = findViewById(R.id.tvCostPerDistance);

        etOdometer = findViewById(R.id.etOdometer);
        etFuelVolume = findViewById(R.id.etFuelVolume);
        etPricePerUnit = findViewById(R.id.etPricePerUnit);
        etTotalCost = findViewById(R.id.etTotalCost);
        etNotes = findViewById(R.id.etNotes);
        switchFullTank = findViewById(R.id.switchFullTank);

        btnStepOdoMinus = findViewById(R.id.btnStepOdoMinus);
        btnStepOdoPlus = findViewById(R.id.btnStepOdoPlus);
        btnStepFuelMinus = findViewById(R.id.btnStepFuelMinus);
        btnStepFuelPlus = findViewById(R.id.btnStepFuelPlus);
        btnAddLog = findViewById(R.id.btnAddLog);

        layoutHistoryContainer = findViewById(R.id.layoutHistoryContainer);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
    }

    private void loadPreferences() {
        currentUnitSystem = prefs.getInt(KEY_UNIT_SYSTEM, 0);
        currentThemeMode = prefs.getInt(KEY_THEME_MODE, 0);
        currencySymbol = prefs.getString(KEY_CURRENCY, "$");
        String savedPrice = prefs.getString(KEY_PRICE_PER_UNIT, "");
        if (!TextUtils.isEmpty(savedPrice)) {
            etPricePerUnit.setText(savedPrice);
        }

        updateUnitSystemButtons();
        updateThemeButtonLabel();

        // Load fuel logs
        fuelEntries.clear();
        String jsonLogs = prefs.getString(KEY_LOGS, "[]");
        try {
            JSONArray arr = new JSONArray(jsonLogs);
            for (int i = 0; i < arr.length(); i++) {
                fuelEntries.add(FuelEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveLogsToPrefs() {
        JSONArray arr = new JSONArray();
        for (FuelEntry entry : fuelEntries) {
            try {
                arr.put(entry.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply();
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            performTactileFeedback(v);
            cycleTheme();
        });

        btnUnitMetric.setOnClickListener(v -> {
            performTactileFeedback(v);
            if (currentUnitSystem != 0) {
                currentUnitSystem = 0;
                prefs.edit().putInt(KEY_UNIT_SYSTEM, currentUnitSystem).apply();
                updateUnitSystemButtons();
                renderDashboardAndHistory();
            }
        });

        btnUnitImperial.setOnClickListener(v -> {
            performTactileFeedback(v);
            if (currentUnitSystem != 1) {
                currentUnitSystem = 1;
                prefs.edit().putInt(KEY_UNIT_SYSTEM, currentUnitSystem).apply();
                updateUnitSystemButtons();
                renderDashboardAndHistory();
            }
        });

        btnExportData.setOnClickListener(v -> {
            performTactileFeedback(v);
            exportData();
        });

        btnClearAll.setOnClickListener(v -> {
            performTactileFeedback(v);
            clearAllLogs();
        });

        // Stepper Buttons
        btnStepOdoMinus.setOnClickListener(v -> {
            performTactileFeedback(v);
            stepOdometer(-10);
        });

        btnStepOdoPlus.setOnClickListener(v -> {
            performTactileFeedback(v);
            stepOdometer(10);
        });

        btnStepFuelMinus.setOnClickListener(v -> {
            performTactileFeedback(v);
            stepFuel(-1.0);
        });

        btnStepFuelPlus.setOnClickListener(v -> {
            performTactileFeedback(v);
            stepFuel(1.0);
        });

        // Automatic total cost calculation if price and volume are populated
        View.OnFocusChangeListener autoCalcCostListener = (v, hasFocus) -> {
            if (!hasFocus) {
                calculateCostIfApplicable();
            }
        };
        etFuelVolume.setOnFocusChangeListener(autoCalcCostListener);
        etPricePerUnit.setOnFocusChangeListener(autoCalcCostListener);

        btnAddLog.setOnClickListener(v -> {
            performTactileFeedback(v);
            saveNewEntry();
        });
    }

    private void performTactileFeedback(View v) {
        if (v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        if (toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 30);
            } catch (Exception ignored) {
            }
        }
    }

    private void stepOdometer(double delta) {
        String valStr = etOdometer.getText().toString().trim();
        double current = 0;
        if (!TextUtils.isEmpty(valStr)) {
            try {
                current = Double.parseDouble(valStr);
            } catch (NumberFormatException ignored) {
            }
        } else if (!fuelEntries.isEmpty()) {
            current = fuelEntries.get(0).odometer;
        }
        current = Math.max(0, current + delta);
        etOdometer.setText(String.format(Locale.US, "%.1f", current));
        etOdometer.setSelection(etOdometer.getText().length());
    }

    private void stepFuel(double delta) {
        String valStr = etFuelVolume.getText().toString().trim();
        double current = 0;
        if (!TextUtils.isEmpty(valStr)) {
            try {
                current = Double.parseDouble(valStr);
            } catch (NumberFormatException ignored) {
            }
        }
        current = Math.max(0, current + delta);
        etFuelVolume.setText(String.format(Locale.US, "%.2f", current));
        etFuelVolume.setSelection(etFuelVolume.getText().length());
        calculateCostIfApplicable();
    }

    private void calculateCostIfApplicable() {
        String volStr = etFuelVolume.getText().toString().trim();
        String priceStr = etPricePerUnit.getText().toString().trim();
        if (!TextUtils.isEmpty(volStr) && !TextUtils.isEmpty(priceStr)) {
            try {
                double vol = Double.parseDouble(volStr);
                double price = Double.parseDouble(priceStr);
                if (vol > 0 && price > 0) {
                    double total = vol * price;
                    etTotalCost.setText(String.format(Locale.US, "%.2f", total));
                    prefs.edit().putString(KEY_PRICE_PER_UNIT, priceStr).apply();
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void updateUnitSystemButtons() {
        boolean isMetric = (currentUnitSystem == 0);
        btnUnitMetric.setSelected(isMetric);
        btnUnitImperial.setSelected(!isMetric);

        // Adjust UI hints based on unit
        String distUnit = isMetric ? "km" : "mi";
        String volUnit = isMetric ? "L" : "gal";
        etOdometer.setHint("Odometer (" + distUnit + ")");
        etFuelVolume.setHint("Fuel added (" + volUnit + ")");
        etPricePerUnit.setHint("Price per " + volUnit + " (" + currencySymbol + ")");
        tvAvgConsumptionLabel.setText(isMetric ? "AVERAGE CONSUMPTION (L/100km)" : "AVERAGE ECONOMY (MPG)");
    }

    private void cycleTheme() {
        currentThemeMode = (currentThemeMode + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, currentThemeMode).apply();
        applyThemeMode(currentThemeMode);
        updateThemeButtonLabel();
    }

    private void updateThemeButtonLabel() {
        if (currentThemeMode == 1) {
            btnThemeToggle.setText("Theme: Light");
        } else if (currentThemeMode == 2) {
            btnThemeToggle.setText("Theme: Dark");
        } else {
            btnThemeToggle.setText("Theme: Auto");
        }
    }

    private void applyThemeMode(int mode) {
        if (uiModeManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (mode == 1) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
            } else if (mode == 2) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
            } else {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
            }
        }
    }

    private void saveNewEntry() {
        String odoStr = etOdometer.getText().toString().trim();
        String volStr = etFuelVolume.getText().toString().trim();
        String costStr = etTotalCost.getText().toString().trim();
        String priceStr = etPricePerUnit.getText().toString().trim();
        String notesStr = etNotes.getText().toString().trim();
        boolean isFull = switchFullTank.isChecked();

        if (TextUtils.isEmpty(odoStr)) {
            etOdometer.setError("Odometer is required");
            etOdometer.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(volStr)) {
            etFuelVolume.setError("Fuel volume is required");
            etFuelVolume.requestFocus();
            return;
        }

        double odo, vol, cost = 0.0;
        try {
            odo = Double.parseDouble(odoStr);
            vol = Double.parseDouble(volStr);
            if (!TextUtils.isEmpty(costStr)) {
                cost = Double.parseDouble(costStr);
            } else if (!TextUtils.isEmpty(priceStr)) {
                cost = vol * Double.parseDouble(priceStr);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numeric figures", Toast.LENGTH_SHORT).show();
            return;
        }

        if (vol <= 0) {
            etFuelVolume.setError("Fuel volume must be > 0");
            return;
        }

        if (!fuelEntries.isEmpty()) {
            double lastOdo = fuelEntries.get(0).odometer;
            if (odo < lastOdo) {
                etOdometer.setError("Odometer cannot decrease from last (" + lastOdo + ")");
                etOdometer.requestFocus();
                return;
            }
        }

        FuelEntry entry = new FuelEntry();
        entry.timestamp = System.currentTimeMillis();
        entry.odometer = odo;
        entry.fuelAmount = vol;
        entry.totalCost = cost;
        entry.isFullTank = isFull;
        entry.notes = notesStr;

        // Add to front (latest first)
        fuelEntries.add(0, entry);
        saveLogsToPrefs();

        // Clear input form
        etFuelVolume.setText("");
        etTotalCost.setText("");
        etNotes.setText("");

        renderDashboardAndHistory();
        Toast.makeText(this, "Fuel entry logged successfully", Toast.LENGTH_SHORT).show();
    }

    private void renderDashboardAndHistory() {
        boolean isMetric = (currentUnitSystem == 0);
        String distUnit = isMetric ? "km" : "mi";
        String volUnit = isMetric ? "L" : "gal";

        // Calculate statistics
        if (fuelEntries.isEmpty()) {
            tvAvgConsumption.setText("--");
            tvTotalDistance.setText("0 " + distUnit);
            tvTotalFuel.setText("0 " + volUnit);
            tvTotalSpent.setText(currencySymbol + "0.00");
            tvCostPerDistance.setText("-- / " + distUnit);
            layoutHistoryContainer.removeAllViews();
            tvEmptyHistory.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyHistory.setVisibility(View.GONE);
        layoutHistoryContainer.removeAllViews();

        double totalFuel = 0.0;
        double totalCost = 0.0;
        for (FuelEntry e : fuelEntries) {
            totalFuel += e.fuelAmount;
            totalCost += e.totalCost;
        }

        double minOdo = fuelEntries.get(fuelEntries.size() - 1).odometer;
        double maxOdo = fuelEntries.get(0).odometer;
        double totalDistance = Math.max(0, maxOdo - minOdo);

        tvTotalDistance.setText(String.format(Locale.US, "%.1f %s", totalDistance, distUnit));
        tvTotalFuel.setText(String.format(Locale.US, "%.1f %s", totalFuel, volUnit));
        tvTotalSpent.setText(String.format(Locale.US, "%s%.2f", currencySymbol, totalCost));

        if (totalDistance > 0 && totalCost > 0) {
            double costPerDist = totalCost / totalDistance;
            tvCostPerDistance.setText(String.format(Locale.US, "%s%.2f / %s", currencySymbol, costPerDist, distUnit));
        } else {
            tvCostPerDistance.setText("-- / " + distUnit);
        }

        // Full-tank calculation across adjacent logs
        double cumulativeFullTankDistance = 0.0;
        double cumulativeFullTankFuel = 0.0;

        for (int i = 0; i < fuelEntries.size() - 1; i++) {
            FuelEntry current = fuelEntries.get(i);
            FuelEntry prev = fuelEntries.get(i + 1);
            if (current.isFullTank && prev.isFullTank) {
                double deltaD = current.odometer - prev.odometer;
                if (deltaD > 0) {
                    cumulativeFullTankDistance += deltaD;
                    cumulativeFullTankFuel += current.fuelAmount;
                }
            }
        }

        if (cumulativeFullTankDistance > 0 && cumulativeFullTankFuel > 0) {
            if (isMetric) {
                double lPer100 = (cumulativeFullTankFuel / cumulativeFullTankDistance) * 100.0;
                tvAvgConsumption.setText(String.format(Locale.US, "%.2f", lPer100));
            } else {
                double mpg = cumulativeFullTankDistance / cumulativeFullTankFuel;
                tvAvgConsumption.setText(String.format(Locale.US, "%.2f", mpg));
            }
        } else if (totalDistance > 0 && totalFuel > 0) {
            // Estimate if full tank pairs are insufficient
            if (isMetric) {
                double lPer100 = (totalFuel / totalDistance) * 100.0;
                tvAvgConsumption.setText(String.format(Locale.US, "~%.2f", lPer100));
            } else {
                double mpg = totalDistance / totalFuel;
                tvAvgConsumption.setText(String.format(Locale.US, "~%.2f", mpg));
            }
        } else {
            tvAvgConsumption.setText("--");
        }

        // Populate History Cards
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault());
        for (int i = 0; i < fuelEntries.size(); i++) {
            final int index = i;
            FuelEntry entry = fuelEntries.get(i);

            LinearLayout rowCard = new LinearLayout(this);
            rowCard.setOrientation(LinearLayout.VERTICAL);
            rowCard.setPadding(32, 28, 32, 28);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 24);
            rowCard.setLayoutParams(lp);

            // Background card style
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(getColor(R.color.m3_surface_container_high));
            shape.setCornerRadius(24f);
            rowCard.setBackground(shape);

            // Row 1: Date + Full Tank badge
            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tvDate = new TextView(this);
            tvDate.setText(sdf.format(new Date(entry.timestamp)));
            tvDate.setTextColor(getColor(R.color.m3_on_surface_variant));
            tvDate.setTextSize(12f);
            LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            tvDate.setLayoutParams(dateLp);

            TextView tvBadge = new TextView(this);
            tvBadge.setText(entry.isFullTank ? "FULL TANK" : "PARTIAL");
            tvBadge.setTextSize(11f);
            tvBadge.setTextColor(entry.isFullTank ? getColor(R.color.m3_primary) : getColor(R.color.m3_outline));
            tvBadge.setPadding(16, 6, 16, 6);

            headerRow.addView(tvDate);
            headerRow.addView(tvBadge);
            rowCard.addView(headerRow);

            // Row 2: Metrics (Odometer, Volume, Total Cost)
            LinearLayout dataRow = new LinearLayout(this);
            dataRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams dataLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dataLp.setMargins(0, 16, 0, 12);
            dataRow.setLayoutParams(dataLp);

            TextView tvOdo = new TextView(this);
            tvOdo.setText(String.format(Locale.US, "%.1f %s", entry.odometer, distUnit));
            tvOdo.setTextColor(getColor(R.color.m3_on_surface));
            tvOdo.setTextSize(15f);
            tvOdo.setTypeface(null, android.graphics.Typeface.BOLD);
            tvOdo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView tvVol = new TextView(this);
            tvVol.setText(String.format(Locale.US, "%.2f %s", entry.fuelAmount, volUnit));
            tvVol.setTextColor(getColor(R.color.m3_on_surface));
            tvVol.setTextSize(15f);
            tvVol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView tvCost = new TextView(this);
            tvCost.setText(String.format(Locale.US, "%s%.2f", currencySymbol, entry.totalCost));
            tvCost.setTextColor(getColor(R.color.m3_primary));
            tvCost.setTextSize(15f);
            tvCost.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCost.setGravity(android.view.Gravity.END);
            tvCost.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            dataRow.addView(tvOdo);
            dataRow.addView(tvVol);
            dataRow.addView(tvCost);
            rowCard.addView(dataRow);

            // Row 3: Incremental trip economy if available
            if (i < fuelEntries.size() - 1) {
                FuelEntry prevEntry = fuelEntries.get(i + 1);
                double legDistance = entry.odometer - prevEntry.odometer;
                if (legDistance > 0 && entry.isFullTank && prevEntry.isFullTank) {
                    TextView tvTripEconomy = new TextView(this);
                    String statText;
                    if (isMetric) {
                        double legConsumption = (entry.fuelAmount / legDistance) * 100.0;
                        statText = String.format(Locale.US, "Leg Econ: %.2f L/100km (Distance: %.1f km)", legConsumption, legDistance);
                    } else {
                        double legMpg = legDistance / entry.fuelAmount;
                        statText = String.format(Locale.US, "Leg Econ: %.2f MPG (Distance: %.1f mi)", legMpg, legDistance);
                    }
                    tvTripEconomy.setText(statText);
                    tvTripEconomy.setTextSize(12f);
                    tvTripEconomy.setTextColor(getColor(R.color.m3_primary));
                    rowCard.addView(tvTripEconomy);
                }
            }

            // Notes if any
            if (!TextUtils.isEmpty(entry.notes)) {
                TextView tvNote = new TextView(this);
                tvNote.setText(entry.notes);
                tvNote.setTextSize(13f);
                tvNote.setTextColor(getColor(R.color.m3_on_surface_variant));
                tvNote.setPadding(0, 8, 0, 8);
                rowCard.addView(tvNote);
            }

            // Delete action button
            Button btnDelete = new Button(this);
            btnDelete.setText("Remove Entry");
            btnDelete.setTextSize(12f);
            btnDelete.setTextColor(getColor(R.color.m3_error));
            btnDelete.setBackgroundResource(R.drawable.btn_m3_outlined);
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            delLp.setMargins(0, 12, 0, 0);
            btnDelete.setLayoutParams(delLp);
            btnDelete.setOnClickListener(v -> {
                performTactileFeedback(v);
                fuelEntries.remove(index);
                saveLogsToPrefs();
                renderDashboardAndHistory();
                Toast.makeText(MainActivity.this, "Entry removed", Toast.LENGTH_SHORT).show();
            });

            rowCard.addView(btnDelete);
            layoutHistoryContainer.addView(rowCard);
        }
    }

    private void exportData() {
        if (fuelEntries.isEmpty()) {
            Toast.makeText(this, "No data available to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Timestamp,Odometer,FuelAmount,TotalCost,FullTank,Notes\n");
        for (FuelEntry e : fuelEntries) {
            sb.append(e.timestamp).append(",")
                    .append(e.odometer).append(",")
                    .append(e.fuelAmount).append(",")
                    .append(e.totalCost).append(",")
                    .append(e.isFullTank).append(",")
                    .append("\"").append(e.notes.replace("\"", "\"\"")).append("\"\n");
        }

        String csvData = sb.toString();

        // Copy to clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("FuelTrackerCSV", csvData);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }

        // Native share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "TankPulse Fuel Logs Export");
        shareIntent.putExtra(Intent.EXTRA_TEXT, csvData);
        startActivity(Intent.createChooser(shareIntent, "Export Fuel Logs"));
        Toast.makeText(this, "Export ready & copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void clearAllLogs() {
        if (fuelEntries.isEmpty()) return;
        fuelEntries.clear();
        saveLogsToPrefs();
        renderDashboardAndHistory();
        Toast.makeText(this, "All records cleared", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (getCurrentFocus() != null && getCurrentFocus() != btnAddLog) {
                        btnAddLog.performClick();
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_NUMPAD_ADD:
                    stepFuel(1.0);
                    return true;
                case KeyEvent.KEYCODE_MINUS:
                case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                    stepFuel(-1.0);
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Retain view tree & live state seamlessly during freeform desktop resizing
    }

    @Override
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }
}