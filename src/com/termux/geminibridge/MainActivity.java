package com.termux.geminibridge;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String TAG = "GeminiBridge";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setShowWhenLocked(true);
        setTurnScreenOn(true);

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, null);
        }

        boolean launched = false;
        try {
            // Direct launch Gemini via its explicit Bard entry point activity
            Intent bardIntent = new Intent(Intent.ACTION_MAIN);
            bardIntent.setComponent(new ComponentName("com.google.android.apps.bard", "com.google.android.apps.bard.shellapp.BardEntryPointActivity"));
            bardIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            bardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(bardIntent);
            launched = true;
            Log.d(TAG, "Launched Gemini via explicit ComponentName");
        } catch (Exception e) {
            Log.w(TAG, "Explicit launch failed: " + e.getMessage());
        }

        if (!launched) {
            try {
                Intent geminiIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.bard");
                if (geminiIntent != null) {
                    geminiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(geminiIntent);
                    launched = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "LaunchIntent failed: " + e.getMessage());
            }
        }

        finish();
    }
}
