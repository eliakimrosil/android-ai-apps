package com.aistudio.clipscrubsanitizesha;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "clip-scrub-sanitize-share_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_AUTO_SCRUB_CLIP = "pref_auto_scrub_clipboard";
    private static final String KEY_STRIP_URL_PARAMS = "pref_strip_url_params";
    private static final String KEY_STRIP_AFFILIATES = "pref_strip_affiliates";
    private static final String KEY_REPLACE_REDIRECTS = "pref_replace_redirects";
    private static final String KEY_STRIP_PII = "pref_strip_pii";
    private static final String KEY_SAVED_INPUT = "pref_saved_input_text";
    private static final String KEY_HISTORY_DATA = "pref_history_json_array";

    // Known tracking parameters to scrub from query strings
    private static final Set<String> TRACKING_KEYS = new HashSet<>(Arrays.asList(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
            "fbclid", "gclid", "gclsrc", "dclid", "msclkid",
            "igshid", "si", "feature", "ref", "ref_src", "ref_url",
            "_hsenc", "_hsmi", "mc_cid", "mc_eid",
            "zanpid", "sc_channel", "cmpid", "trk", "trkcampaign",
            "yclid", "wickedid", "wt_mc", "oicd", "spm"
    ));

    // Known affiliate tracking keys
    private static final Set<String> AFFILIATE_KEYS = new HashSet<>(Arrays.asList(
            "tag", "ascsubtag", "aff_id", "affiliate", "affid", "aff",
            "camp", "creative", "linkcode", "creativeasin", "source_id"
    ));

    // Redirect unwrapping keys (e.g. google.com/url?q=..., out.php?url=...)
    private static final List<String> REDIRECT_PARAM_KEYS = Arrays.asList(
            "url", "u", "q", "target", "dest", "destination", "redirect", "redir"
    );

    // Regex patterns for URLs, Emails, and Phone Numbers
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)\\d{3}[-.\\s]?\\d{4}"
    );

    // UI elements
    private TextView tvStatusBadge;
    private Button btnThemeToggle;
    private EditText etInputPayload;
    private TextView tvInputMeta;
    private Button btnPasteClipboard;
    private Button btnClearInput;
    private Switch switchUrlTracking;
    private Switch switchAffiliates;
    private Switch switchRedirects;
    private Switch switchPiiScrub;
    private Switch switchAutoClipboard;
    private Button btnScrubAction;
    private TextView tvOutputPreview;
    private TextView tvMetricsBadge;
    private Button btnCopyOutput;
    private Button btnShareClean;
    private LinearLayout layoutHistoryContainer;
    private Button btnClearHistory;

    private SharedPreferences prefs;
    private ClipboardManager clipboardManager;
    private UiModeManager uiModeManager;

    private int themeMode = 0; // 0 = System/Auto, 1 = Light, 2 = Dark
    private List<HistoryItem> historyList = new ArrayList<>();

    private static class HistoryItem {
        String timestamp;
        String originalText;
        String cleanedText;
        int trackersRemoved;

        HistoryItem(String timestamp, String originalText, String cleanedText, int trackersRemoved) {
            this.timestamp = timestamp;
            this.originalText = originalText;
            this.cleanedText = cleanedText;
            this.trackersRemoved = trackersRemoved;
        }

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("timestamp", timestamp);
            obj.put("original", originalText);
            obj.put("cleaned", cleanedText);
            obj.put("removed", trackersRemoved);
            return obj;
        }

        static HistoryItem fromJson(JSONObject obj) {
            return new HistoryItem(
                    obj.optString("timestamp", ""),
                    obj.optString("original", ""),
                    obj.optString("cleaned", ""),
                    obj.optInt("removed", 0)
            );
        }
    }

    private static class ScrubResult {
        String sanitizedText;
        int strippedCount;
        int charsSaved;

        ScrubResult(String sanitizedText, int strippedCount, int charsSaved) {
            this.sanitizedText = sanitizedText;
            this.strippedCount = strippedCount;
            this.charsSaved = charsSaved;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);

        initViews();
        restorePreferences();
        handleIncomingIntent(getIntent());
        setupListeners();
        renderHistoryList();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (switchAutoClipboard != null && switchAutoClipboard.isChecked()) {
            readFromClipboard(false);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Configuration change (desktop window resize / orientation) handled gracefully.
        // Live state, dynamic input, and sanitized output are retained in memory.
        updateInputMetadata(etInputPayload.getText() != null ? etInputPayload.getText().toString() : "");
    }

    private void initViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        etInputPayload = findViewById(R.id.etInputPayload);
        tvInputMeta = findViewById(R.id.tvInputMeta);
        btnPasteClipboard = findViewById(R.id.btnPasteClipboard);
        btnClearInput = findViewById(R.id.btnClearInput);

        switchUrlTracking = findViewById(R.id.switchUrlTracking);
        switchAffiliates = findViewById(R.id.switchAffiliates);
        switchRedirects = findViewById(R.id.switchRedirects);
        switchPiiScrub = findViewById(R.id.switchPiiScrub);
        switchAutoClipboard = findViewById(R.id.switchAutoClipboard);

        btnScrubAction = findViewById(R.id.btnScrubAction);
        tvOutputPreview = findViewById(R.id.tvOutputPreview);
        tvMetricsBadge = findViewById(R.id.tvMetricsBadge);
        btnCopyOutput = findViewById(R.id.btnCopyOutput);
        btnShareClean = findViewById(R.id.btnShareClean);

        layoutHistoryContainer = findViewById(R.id.layoutHistoryContainer);
        btnClearHistory = findViewById(R.id.btnClearHistory);
    }

    private void restorePreferences() {
        themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        updateThemeToggleLabel();
        applySavedTheme(false);

        switchUrlTracking.setChecked(prefs.getBoolean(KEY_STRIP_URL_PARAMS, true));
        switchAffiliates.setChecked(prefs.getBoolean(KEY_STRIP_AFFILIATES, true));
        switchRedirects.setChecked(prefs.getBoolean(KEY_REPLACE_REDIRECTS, true));
        switchPiiScrub.setChecked(prefs.getBoolean(KEY_STRIP_PII, false));
        switchAutoClipboard.setChecked(prefs.getBoolean(KEY_AUTO_SCRUB_CLIP, false));

        String savedInput = prefs.getString(KEY_SAVED_INPUT, "");
        if (!TextUtils.isEmpty(savedInput)) {
            etInputPayload.setText(savedInput);
            etInputPayload.setSelection(savedInput.length());
            updateInputMetadata(savedInput);
        }

        // Restore History
        historyList.clear();
        String jsonHistory = prefs.getString(KEY_HISTORY_DATA, "[]");
        try {
            JSONArray arr = new JSONArray(jsonHistory);
            for (int i = 0; i < arr.length(); i++) {
                historyList.add(HistoryItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            themeMode = (themeMode + 1) % 3;
            prefs.edit().putInt(KEY_THEME_MODE, themeMode).apply();
            updateThemeToggleLabel();
            applySavedTheme(true);
        });

        btnPasteClipboard.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            readFromClipboard(true);
        });

        btnClearInput.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            etInputPayload.setText("");
            tvOutputPreview.setText(R.string.output_empty_placeholder);
            tvMetricsBadge.setText(R.string.status_ready);
            prefs.edit().remove(KEY_SAVED_INPUT).apply();
            showStatusBadge("Input Cleared");
        });

        etInputPayload.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = s != null ? s.toString() : "";
                updateInputMetadata(current);
                prefs.edit().putString(KEY_SAVED_INPUT, current).apply();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        switchUrlTracking.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean(KEY_STRIP_URL_PARAMS, isChecked).apply());
        switchAffiliates.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean(KEY_STRIP_AFFILIATES, isChecked).apply());
        switchRedirects.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean(KEY_REPLACE_REDIRECTS, isChecked).apply());
        switchPiiScrub.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean(KEY_STRIP_PII, isChecked).apply());
        switchAutoClipboard.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean(KEY_AUTO_SCRUB_CLIP, isChecked).apply());

        btnScrubAction.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            performScrubOperation();
        });

        btnCopyOutput.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            copyCleanedOutputToClipboard();
        });

        btnShareClean.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareCleanedOutput();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            historyList.clear();
            prefs.edit().putString(KEY_HISTORY_DATA, "[]").apply();
            renderHistoryList();
            showStatusBadge("History Cleared");
        });
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(sharedText)) {
                etInputPayload.setText(sharedText);
                etInputPayload.setSelection(sharedText.length());
                updateInputMetadata(sharedText);
                showStatusBadge("Incoming Share Received");
                performScrubOperation();
            }
        }
    }

    private void readFromClipboard(boolean notifyIfEmpty) {
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            if (notifyIfEmpty) {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence text = clip.getItemAt(0).getText();
            if (!TextUtils.isEmpty(text)) {
                etInputPayload.setText(text);
                etInputPayload.setSelection(text.length());
                updateInputMetadata(text.toString());
                showStatusBadge("Pasted from Clipboard");
                if (switchAutoClipboard.isChecked()) {
                    performScrubOperation();
                }
            } else if (notifyIfEmpty) {
                Toast.makeText(this, "No plain text found in clipboard", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateInputMetadata(String text) {
        int length = text.length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        tvInputMeta.setText(String.format(Locale.getDefault(), "%d characters • %d words", length, words));
    }

    private void performScrubOperation() {
        String rawInput = etInputPayload.getText() != null ? etInputPayload.getText().toString().trim() : "";
        if (TextUtils.isEmpty(rawInput)) {
            Toast.makeText(this, "Please provide text or links to scrub", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean stripParams = switchUrlTracking.isChecked();
        boolean stripAffiliates = switchAffiliates.isChecked();
        boolean replaceRedirects = switchRedirects.isChecked();
        boolean scrubPii = switchPiiScrub.isChecked();

        ScrubResult result = sanitizeText(rawInput, stripParams, stripAffiliates, replaceRedirects, scrubPii);

        tvOutputPreview.setText(result.sanitizedText);
        tvMetricsBadge.setText(String.format(Locale.getDefault(),
                "Sanitized: %d items removed | %d bytes saved",
                result.strippedCount, Math.max(0, result.charsSaved)));
        showStatusBadge("Sanitization Completed");

        addHistoryRecord(rawInput, result.sanitizedText, result.strippedCount);
    }

    private ScrubResult sanitizeText(String input, boolean stripParams, boolean stripAffiliates,
                                     boolean replaceRedirects, boolean scrubPii) {
        int strippedCount = 0;
        int originalLen = input.length();

        Matcher urlMatcher = URL_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (urlMatcher.find()) {
            String originalUrl = urlMatcher.group();
            String processedUrl = sanitizeSingleUrl(originalUrl, stripParams, stripAffiliates, replaceRedirects);
            if (!originalUrl.equals(processedUrl)) {
                strippedCount++;
            }
            urlMatcher.appendReplacement(sb, Matcher.quoteReplacement(processedUrl));
        }
        urlMatcher.appendTail(sb);

        String intermediate = sb.toString();

        if (scrubPii) {
            Matcher emailMatcher = EMAIL_PATTERN.matcher(intermediate);
            StringBuffer emailSb = new StringBuffer();
            while (emailMatcher.find()) {
                strippedCount++;
                emailMatcher.appendReplacement(emailSb, "[REDACTED_EMAIL]");
            }
            emailMatcher.appendTail(emailSb);
            intermediate = emailSb.toString();

            Matcher phoneMatcher = PHONE_PATTERN.matcher(intermediate);
            StringBuffer phoneSb = new StringBuffer();
            while (phoneMatcher.find()) {
                strippedCount++;
                phoneMatcher.appendReplacement(phoneSb, "[REDACTED_PHONE]");
            }
            phoneMatcher.appendTail(phoneSb);
            intermediate = phoneSb.toString();
        }

        int newLen = intermediate.length();
        int saved = originalLen - newLen;
        return new ScrubResult(intermediate, strippedCount, saved);
    }

    private String sanitizeSingleUrl(String urlString, boolean stripParams, boolean stripAffiliates, boolean unwrapRedirects) {
        try {
            Uri uri = Uri.parse(urlString);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return urlString;
            }

            // 1. Check for redirects
            if (unwrapRedirects) {
                for (String redirKey : REDIRECT_PARAM_KEYS) {
                    String candidate = uri.getQueryParameter(redirKey);
                    if (!TextUtils.isEmpty(candidate)) {
                        try {
                            String decoded = URLDecoder.decode(candidate, StandardCharsets.UTF_8.name());
                            if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                                return sanitizeSingleUrl(decoded, stripParams, stripAffiliates, unwrapRedirects);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            // 2. Query param filtering
            Set<String> queryNames = uri.getQueryParameterNames();
            if (queryNames == null || queryNames.isEmpty()) {
                return uri.toString();
            }

            Uri.Builder cleanUriBuilder = uri.buildUpon().clearQuery();
            for (String key : queryNames) {
                String lowerKey = key.toLowerCase(Locale.ROOT);

                if (stripParams && (TRACKING_KEYS.contains(lowerKey) || lowerKey.startsWith("utm_"))) {
                    continue; // Skip tracking
                }
                if (stripAffiliates && AFFILIATE_KEYS.contains(lowerKey)) {
                    continue; // Skip affiliate
                }

                List<String> values = uri.getQueryParameters(key);
                for (String val : values) {
                    cleanUriBuilder.appendQueryParameter(key, val);
                }
            }

            return cleanUriBuilder.build().toString();
        } catch (Exception e) {
            return urlString;
        }
    }

    private void copyCleanedOutputToClipboard() {
        String cleanText = tvOutputPreview.getText() != null ? tvOutputPreview.getText().toString() : "";
        if (TextUtils.isEmpty(cleanText) || cleanText.equals(getString(R.string.output_empty_placeholder))) {
            Toast.makeText(this, "Nothing to copy yet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (clipboardManager != null) {
            ClipData clip = ClipData.newPlainText("Sanitized Clip", cleanText);
            clipboardManager.setPrimaryClip(clip);
            showStatusBadge("Copied to Clipboard");
            Toast.makeText(this, "Cleaned text copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCleanedOutput() {
        String cleanText = tvOutputPreview.getText() != null ? tvOutputPreview.getText().toString() : "";
        if (TextUtils.isEmpty(cleanText) || cleanText.equals(getString(R.string.output_empty_placeholder))) {
            Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, cleanText);
        Intent shareChooser = Intent.createChooser(sendIntent, "Share Sanitized Payload");
        startActivity(shareChooser);
    }

    private void addHistoryRecord(String original, String cleaned, int trackersCount) {
        String timeStamp = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date());
        HistoryItem item = new HistoryItem(timeStamp, original, cleaned, trackersCount);
        historyList.add(0, item);

        // Keep maximum 25 items
        if (historyList.size() > 25) {
            historyList.remove(historyList.size() - 1);
        }

        saveHistoryToPrefs();
        renderHistoryList();
    }

    private void saveHistoryToPrefs() {
        JSONArray arr = new JSONArray();
        for (HistoryItem item : historyList) {
            try {
                arr.put(item.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_HISTORY_DATA, arr.toString()).apply();
    }

    private void renderHistoryList() {
        layoutHistoryContainer.removeAllViews();
        if (historyList.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText(R.string.history_empty_note);
            tvEmpty.setTextColor(0xFF8E918F);
            tvEmpty.setTextSize(13);
            tvEmpty.setPadding(16, 16, 16, 16);
            layoutHistoryContainer.addView(tvEmpty);
            return;
        }

        for (int i = 0; i < historyList.size(); i++) {
            final HistoryItem item = historyList.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(R.drawable.card_m3_high);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 16);
            row.setLayoutParams(lp);
            row.setPadding(24, 20, 24, 20);

            // Row header with timestamp and tracker count
            LinearLayout metaLayout = new LinearLayout(this);
            metaLayout.setOrientation(LinearLayout.HORIZONTAL);

            TextView tvTime = new TextView(this);
            tvTime.setText(item.timestamp);
            tvTime.setTextColor(0xFF8E918F);
            tvTime.setTextSize(11);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
            );
            tvTime.setLayoutParams(metaLp);

            TextView tvBadge = new TextView(this);
            tvBadge.setText(String.format(Locale.getDefault(), "%d Scrubbed", item.trackersRemoved));
            tvBadge.setTextColor(0xFF80D49C);
            tvBadge.setTextSize(11);

            metaLayout.addView(tvTime);
            metaLayout.addView(tvBadge);
            row.addView(metaLayout);

            // Cleaned snippet
            TextView tvContent = new TextView(this);
            tvContent.setText(item.cleanedText);
            tvContent.setMaxLines(2);
            tvContent.setEllipsize(TextUtils.TruncateAt.END);
            tvContent.setTextColor(0xFFE2E2E6);
            tvContent.setTextSize(13);
            tvContent.setPadding(0, 8, 0, 8);
            row.addView(tvContent);

            // Quick re-use on tap
            row.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                etInputPayload.setText(item.cleanedText);
                etInputPayload.setSelection(item.cleanedText.length());
                tvOutputPreview.setText(item.cleanedText);
                tvMetricsBadge.setText(String.format(Locale.getDefault(), "Loaded from history: %s", item.timestamp));
                showStatusBadge("Restored from History");
            });

            layoutHistoryContainer.addView(row);
        }
    }

    private void showStatusBadge(String message) {
        if (tvStatusBadge != null) {
            tvStatusBadge.setText(message);
        }
    }

    private void updateThemeToggleLabel() {
        switch (themeMode) {
            case 1:
                btnThemeToggle.setText(R.string.theme_light);
                break;
            case 2:
                btnThemeToggle.setText(R.string.theme_dark);
                break;
            default:
                btnThemeToggle.setText(R.string.theme_auto);
                break;
        }
    }

    private void applySavedTheme(boolean showToast) {
        if (uiModeManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            switch (themeMode) {
                case 1:
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
                    break;
                case 2:
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
                    break;
                default:
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
                    break;
            }
        }
        if (showToast) {
            Toast.makeText(this, "Theme: " + btnThemeToggle.getText(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            // Enter or Numpad Enter triggers primary scrub action
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.isCtrlPressed() || !etInputPayload.hasFocus()) {
                    performScrubOperation();
                    return true;
                }
            }
            // Spacebar toggles auto clipboard scrub when outside input focus
            if (keyCode == KeyEvent.KEYCODE_SPACE && !etInputPayload.hasFocus()) {
                switchAutoClipboard.toggle();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}