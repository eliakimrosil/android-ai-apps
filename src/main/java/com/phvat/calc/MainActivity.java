package com.phvat.calc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class MainActivity extends Activity {

    private LinearLayout rootLayout;
    private TextView tvAppTitle;
    private TextView tvAppSubtitle;
    private Button btnTheme;

    private LinearLayout cardAmount;
    private TextView tvAmountLabel;
    private LinearLayout llInputBox;
    private TextView tvPesoSymbol;
    private EditText etAmount;

    private Button chip100;
    private Button chip500;
    private Button chip1000;
    private Button chip5000;
    private Button chip10000;

    private LinearLayout cardMode;
    private TextView tvModeLabel;
    private RadioGroup rgVatMode;
    private RadioButton rbInclusive;
    private RadioButton rbExclusive;
    private View dividerMode;
    private Switch swSeniorPwd;

    private LinearLayout cardBreakdown;
    private TextView tvBreakdownLabel;
    private TextView tvTotalLabel;
    private TextView tvTotalVal;
    private View dividerBreakdown;
    private TextView tvVatableLabel;
    private TextView tvVatableVal;
    private TextView tvVatLabel;
    private TextView tvVatVal;
    private LinearLayout llDiscountRow;
    private TextView tvDiscountLabel;
    private TextView tvDiscountVal;
    private TextView tvFormulaNote;

    private Button btnClear;
    private Button btnCopy;

    private boolean isDarkTheme = true;
    private SharedPreferences prefs;

    private final DecimalFormat currencyFmt = new DecimalFormat("₱#,##0.00");
    private final DecimalFormat plainFmt = new DecimalFormat("#.##");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("ph_vat_prefs", MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean("dark_theme", true);

        initViews();
        setupListeners();
        applyTheme(isDarkTheme);
        calculate();
    }

    private void initViews() {
        rootLayout = findViewById(R.id.root_layout);
        tvAppTitle = findViewById(R.id.tv_app_title);
        tvAppSubtitle = findViewById(R.id.tv_app_subtitle);
        btnTheme = findViewById(R.id.btn_theme);

        cardAmount = findViewById(R.id.card_amount);
        tvAmountLabel = findViewById(R.id.tv_amount_label);
        llInputBox = findViewById(R.id.ll_input_box);
        tvPesoSymbol = findViewById(R.id.tv_peso_symbol);
        etAmount = findViewById(R.id.et_amount);

        chip100 = findViewById(R.id.chip_100);
        chip500 = findViewById(R.id.chip_500);
        chip1000 = findViewById(R.id.chip_1000);
        chip5000 = findViewById(R.id.chip_5000);
        chip10000 = findViewById(R.id.chip_10000);

        cardMode = findViewById(R.id.card_mode);
        tvModeLabel = findViewById(R.id.tv_mode_label);
        rgVatMode = findViewById(R.id.rg_vat_mode);
        rbInclusive = findViewById(R.id.rb_inclusive);
        rbExclusive = findViewById(R.id.rb_exclusive);
        dividerMode = findViewById(R.id.divider_mode);
        swSeniorPwd = findViewById(R.id.sw_senior_pwd);

        cardBreakdown = findViewById(R.id.card_breakdown);
        tvBreakdownLabel = findViewById(R.id.tv_breakdown_label);
        tvTotalLabel = findViewById(R.id.tv_total_label);
        tvTotalVal = findViewById(R.id.tv_total_val);
        dividerBreakdown = findViewById(R.id.divider_breakdown);
        tvVatableLabel = findViewById(R.id.tv_vatable_label);
        tvVatableVal = findViewById(R.id.tv_vatable_val);
        tvVatLabel = findViewById(R.id.tv_vat_label);
        tvVatVal = findViewById(R.id.tv_vat_val);
        llDiscountRow = findViewById(R.id.ll_discount_row);
        tvDiscountLabel = findViewById(R.id.tv_discount_label);
        tvDiscountVal = findViewById(R.id.tv_discount_val);
        tvFormulaNote = findViewById(R.id.tv_formula_note);

        btnClear = findViewById(R.id.btn_clear);
        btnCopy = findViewById(R.id.btn_copy);
    }

    private void setupListeners() {
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isDarkTheme = !isDarkTheme;
                prefs.edit().putBoolean("dark_theme", isDarkTheme).apply();
                applyTheme(isDarkTheme);
                calculate();
            }
        });

        etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculate();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        rgVatMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                calculate();
            }
        });

        swSeniorPwd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                calculate();
            }
        });

        setupPreset(chip100, 100);
        setupPreset(chip500, 500);
        setupPreset(chip1000, 1000);
        setupPreset(chip5000, 5000);
        setupPreset(chip10000, 10000);

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etAmount.setText("");
                calculate();
            }
        });

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copySummary();
            }
        });
    }

    private void setupPreset(Button btn, final double addValue) {
        if (btn == null) return;
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double current = parseInput();
                double next = current + addValue;
                etAmount.setText(plainFmt.format(next));
                etAmount.setSelection(etAmount.getText().length());
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void applyTheme(boolean isDark) {
        int bgMain = isDark ? 0xFF0F1117 : 0xFFF4F6F9;
        int bgCard = isDark ? 0xFF1A1D27 : 0xFFFFFFFF;
        int bgInner = isDark ? 0xFF222634 : 0xFFF8FAFC;
        int strokeColor = isDark ? 0xFF2E3446 : 0xFFE2E8F0;

        int textHigh = isDark ? 0xFFF0F3F8 : 0xFF0F172A;
        int textMid = isDark ? 0xFF9CA3AF : 0xFF64748B;
        int textMuted = isDark ? 0xFF6B7280 : 0xFF94A3B8;

        int accentGold = isDark ? 0xFFFFD54F : 0xFFB45309;
        int accentCyan = isDark ? 0xFF00E5FF : 0xFF0284C7;
        int accentEmerald = isDark ? 0xFF00E676 : 0xFF15803D;

        // Window status/nav bar
        getWindow().setStatusBarColor(bgMain);
        getWindow().setNavigationBarColor(bgMain);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (!isDark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        }

        // Root
        rootLayout.setBackgroundColor(bgMain);

        // Header
        tvAppTitle.setTextColor(accentGold);
        tvAppSubtitle.setTextColor(textMid);
        btnTheme.setText(isDark ? "☀ Light" : "🌙 Dark");
        btnTheme.setTextColor(isDark ? 0xFFFFD54F : 0xFF0F172A);

        // Drawables
        GradientDrawable cardDraw1 = new GradientDrawable();
        cardDraw1.setColor(bgCard);
        cardDraw1.setStroke(dpToPx(1), strokeColor);
        cardDraw1.setCornerRadius(dpToPx(16));
        cardAmount.setBackground(cardDraw1);

        GradientDrawable cardDraw2 = new GradientDrawable();
        cardDraw2.setColor(bgCard);
        cardDraw2.setStroke(dpToPx(1), strokeColor);
        cardDraw2.setCornerRadius(dpToPx(16));
        cardMode.setBackground(cardDraw2);

        GradientDrawable cardDraw3 = new GradientDrawable();
        cardDraw3.setColor(bgCard);
        cardDraw3.setStroke(dpToPx(1), strokeColor);
        cardDraw3.setCornerRadius(dpToPx(16));
        cardBreakdown.setBackground(cardDraw3);

        GradientDrawable inputDraw = new GradientDrawable();
        inputDraw.setColor(bgInner);
        inputDraw.setStroke(dpToPx(1), isDark ? strokeColor : 0xFFCBD5E1);
        inputDraw.setCornerRadius(dpToPx(12));
        llInputBox.setBackground(inputDraw);

        // Text & elements in card 1
        tvAmountLabel.setTextColor(textMid);
        tvPesoSymbol.setTextColor(accentGold);
        etAmount.setTextColor(textHigh);
        etAmount.setHintTextColor(textMuted);

        // Chips
        Button[] chips = {chip100, chip500, chip1000, chip5000, chip10000, btnTheme};
        for (Button chip : chips) {
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(isDark ? 0xFF222634 : 0xFFF1F5F9);
            chipBg.setStroke(dpToPx(1), strokeColor);
            chipBg.setCornerRadius(dpToPx(20));
            chip.setBackground(chipBg);
            if (chip != btnTheme) chip.setTextColor(textMid);
        }

        // Mode card
        tvModeLabel.setTextColor(accentCyan);
        rbInclusive.setTextColor(textHigh);
        rbExclusive.setTextColor(textHigh);
        swSeniorPwd.setTextColor(textHigh);
        dividerMode.setBackgroundColor(strokeColor);

        // Breakdown card
        tvBreakdownLabel.setTextColor(accentGold);
        tvTotalLabel.setTextColor(textHigh);
        tvTotalVal.setTextColor(accentEmerald);
        dividerBreakdown.setBackgroundColor(strokeColor);

        tvVatableLabel.setTextColor(textMid);
        tvVatableVal.setTextColor(textHigh);
        tvVatLabel.setTextColor(textMid);
        tvVatVal.setTextColor(accentCyan);
        tvDiscountLabel.setTextColor(accentGold);
        tvDiscountVal.setTextColor(accentGold);
        tvFormulaNote.setTextColor(textMuted);

        // Bottom action buttons
        GradientDrawable clearDraw = new GradientDrawable();
        clearDraw.setColor(isDark ? 0xFF1A2736 : 0xFFF1F5F9);
        clearDraw.setStroke(dpToPx(1), strokeColor);
        clearDraw.setCornerRadius(dpToPx(12));
        btnClear.setBackground(clearDraw);
        btnClear.setTextColor(textMid);

        GradientDrawable copyDraw = new GradientDrawable();
        copyDraw.setColor(isDark ? 0xFF1A2736 : 0xFFEFF6FF);
        copyDraw.setStroke(dpToPx(1), isDark ? accentCyan : 0xFF0284C7);
        copyDraw.setCornerRadius(dpToPx(12));
        btnCopy.setBackground(copyDraw);
        btnCopy.setTextColor(isDark ? accentGold : 0xFF0284C7);
    }

    private double parseInput() {
        String str = etAmount.getText().toString().trim();
        if (str.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void calculate() {
        double input = parseInput();
        boolean isInclusive = rbInclusive.isChecked();
        boolean isSeniorPwd = swSeniorPwd.isChecked();

        double vatableSales;
        double vatAmount;
        double discount = 0.0;
        double total;

        if (isSeniorPwd) {
            if (isInclusive) {
                vatableSales = input / 1.12;
            } else {
                vatableSales = input;
            }
            vatAmount = 0.0;
            discount = vatableSales * 0.20;
            total = vatableSales - discount;

            llDiscountRow.setVisibility(View.VISIBLE);
            tvDiscountVal.setText("-" + currencyFmt.format(discount));
            tvVatVal.setText("₱0.00 (Exempt)");
            tvFormulaNote.setText("PH Rule (RA 9994/10754): Net = Amount ÷ 1.12 | 12% VAT = Exempt | Less 20% Discount");
        } else {
            llDiscountRow.setVisibility(View.GONE);

            if (isInclusive) {
                vatableSales = input / 1.12;
                vatAmount = input - vatableSales;
                total = input;
                tvFormulaNote.setText("Formula: Vatable Sales = Amount ÷ 1.12 | 12% VAT = Vatable Sales × 0.12");
            } else {
                vatableSales = input;
                vatAmount = input * 0.12;
                total = input + vatAmount;
                tvFormulaNote.setText("Formula: 12% VAT = Amount × 0.12 | Total = Amount + 12% VAT");
            }
            tvVatVal.setText(currencyFmt.format(vatAmount));
        }

        tvVatableVal.setText(currencyFmt.format(vatableSales));
        tvTotalVal.setText(currencyFmt.format(total));
    }

    private void copySummary() {
        String inputStr = etAmount.getText().toString().trim();
        if (inputStr.isEmpty()) inputStr = "0.00";

        StringBuilder sb = new StringBuilder();
        sb.append("--- PH VAT Calculation Summary ---\n");
        sb.append("Mode: ").append(rbInclusive.isChecked() ? "VAT Inclusive" : "VAT Exclusive").append("\n");
        sb.append("Entered Amount: ₱").append(inputStr).append("\n");
        sb.append("Vatable Sales: ").append(tvVatableVal.getText()).append("\n");
        sb.append("12% VAT: ").append(tvVatVal.getText()).append("\n");
        if (swSeniorPwd.isChecked()) {
            sb.append("Senior/PWD 20% Discount: ").append(tvDiscountVal.getText()).append("\n");
        }
        sb.append("Total Amount Payable: ").append(tvTotalVal.getText()).append("\n");
        sb.append("BIR Standard 12% VAT Rate");

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("VAT Calculation", sb.toString());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Calculation copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }
}
