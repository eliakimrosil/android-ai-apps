package com.aistudio.parkpinofflinespotfi;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {

    private static final String PREFS_NAME = "park-pin-offline-spot-finder_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_PIN_LAT = "pin_lat";
    private static final String KEY_PIN_LON = "pin_lon";
    private static final String KEY_PIN_ALT = "pin_alt";
    private static final String KEY_PIN_LEVEL = "pin_level";
    private static final String KEY_PIN_SPOT = "pin_spot";
    private static final String KEY_PIN_NOTE = "pin_note";
    private static final String KEY_PIN_TIME = "pin_time";
    private static final String KEY_METER_EXPIRY = "meter_expiry";
    private static final String KEY_HISTORY_JSON = "history_json";

    private static final int PERMISSION_REQ_CODE = 101;

    // Hardware Sensors & Location
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor magneticSensor;
    private Sensor accelerometerSensor;

    private float[] gravityMatrix = new float[3];
    private float[] geomagneticMatrix = new float[3];
    private boolean hasGravity = false;
    private boolean hasGeomagnetic = false;
    private float currentAzimuthDeg = 0f;

    // State Variables
    private Location currentGpsLocation;
    private boolean hasPinnedLocation = false;
    private double pinnedLatitude = 0.0;
    private double pinnedLongitude = 0.0;
    private double pinnedAltitude = 0.0;
    private String pinnedLevel = "";
    private String pinnedSpot = "";
    private String pinnedNote = "";
    private long pinnedTimestamp = 0L;

    // Meter Timer State
    private long meterExpiryTimeMillis = 0L;
    private CountDownTimer meterCountDownTimer;

    // Views
    private Button btnThemeToggle;
    private TextView tvGpsTelemetry;
    private TextView tvCompassHeading;
    private TextView tvDistanceBearing;
    private TextView tvDirectionArrow;
    private TextView tvLevelGuidance;
    private EditText etLevelNumber;
    private EditText etSpotNumber;
    private EditText etParkingNotes;
    private Button btnDropPin;
    private Button btnClearPin;
    private Button btnShareLocation;
    private TextView tvMeterCountdown;
    private Button btnMeter15m;
    private Button btnMeter30m;
    private Button btnMeter60m;
    private Button btnMeterClear;
    private LinearLayout containerHistory;
    private Button btnClearHistory;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark
        Configuration config = new Configuration(newBase.getResources().getConfiguration());

        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (systemNight == 2) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            } else {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and show when locked for immediate spot navigation recovery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.activity_main);

        initViews();
        setupThemeToggle();
        setupHardwareSensors();
        loadPersistedState();
        checkPermissionsAndStartUpdates();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        tvGpsTelemetry = findViewById(R.id.tvGpsTelemetry);
        tvCompassHeading = findViewById(R.id.tvCompassHeading);
        tvDistanceBearing = findViewById(R.id.tvDistanceBearing);
        tvDirectionArrow = findViewById(R.id.tvDirectionArrow);
        tvLevelGuidance = findViewById(R.id.tvLevelGuidance);
        etLevelNumber = findViewById(R.id.etLevelNumber);
        etSpotNumber = findViewById(R.id.etSpotNumber);
        etParkingNotes = findViewById(R.id.etParkingNotes);
        btnDropPin = findViewById(R.id.btnDropPin);
        btnClearPin = findViewById(R.id.btnClearPin);
        btnShareLocation = findViewById(R.id.btnShareLocation);
        tvMeterCountdown = findViewById(R.id.tvMeterCountdown);
        btnMeter15m = findViewById(R.id.btnMeter15m);
        btnMeter30m = findViewById(R.id.btnMeter30m);
        btnMeter60m = findViewById(R.id.btnMeter60m);
        btnMeterClear = findViewById(R.id.btnMeterClear);
        containerHistory = findViewById(R.id.containerHistory);
        btnClearHistory = findViewById(R.id.btnClearHistory);

        btnDropPin.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            dropCurrentAnchor();
        });

        btnClearPin.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearCurrentAnchor();
        });

        btnShareLocation.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareAnchorDetails();
        });

        btnMeter15m.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            addMeterTime(15);
        });

        btnMeter30m.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            addMeterTime(30);
        });

        btnMeter60m.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            addMeterTime(60);
        });

        btnMeterClear.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cancelMeterTimer();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearHistory();
        });
    }

    private void setupThemeToggle() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        updateThemeButtonLabel(themeMode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                if (!isNight) {
                    controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                } else {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }

        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });
    }

    private void updateThemeButtonLabel(int mode) {
        if (mode == 1) {
            btnThemeToggle.setText("Theme: Light");
        } else if (mode == 2) {
            btnThemeToggle.setText("Theme: Dark");
        } else {
            btnThemeToggle.setText("Theme: Auto");
        }
    }

    private void cycleThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void setupHardwareSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor == null) {
                accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            }
        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void checkPermissionsAndStartUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQ_CODE);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (locationManager == null) return;
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, this);
                    Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastGps != null) {
                        onLocationChanged(lastGps);
                    }
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1.0f, this);
                    if (currentGpsLocation == null) {
                        Location lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (lastNet != null) {
                            onLocationChanged(lastNet);
                        }
                    }
                }
            }
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                tvGpsTelemetry.setText("Telemetry: GPS Permission Required for Precision Anchor");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (rotationVectorSensor != null) {
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
            } else {
                if (accelerometerSensor != null) {
                    sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
                }
                if (magneticSensor != null) {
                    sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI);
                }
            }
        }
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            currentAzimuthDeg = (float) Math.toDegrees(orientation[0]);
            if (currentAzimuthDeg < 0) currentAzimuthDeg += 360f;
            updateHud();
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravityMatrix, 0, 3);
            hasGravity = true;
            computeOrientationFallback();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagneticMatrix, 0, 3);
            hasGeomagnetic = true;
            computeOrientationFallback();
        }
    }

    private void computeOrientationFallback() {
        if (hasGravity && hasGeomagnetic) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravityMatrix, geomagneticMatrix)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                currentAzimuthDeg = (float) Math.toDegrees(orientation[0]);
                if (currentAzimuthDeg < 0) currentAzimuthDeg += 360f;
                updateHud();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onLocationChanged(Location location) {
        currentGpsLocation = location;
        String altText = location.hasAltitude() ? String.format(Locale.US, "%.1fm", location.getAltitude()) : "N/A";
        String accText = location.hasAccuracy() ? String.format(Locale.US, "±%.1fm", location.getAccuracy()) : "±--";
        tvGpsTelemetry.setText(String.format(Locale.US, "GPS: %.6f, %.6f | Alt: %s | Acc: %s",
                location.getLatitude(), location.getLongitude(), altText, accText));
        updateHud();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    private void updateHud() {
        tvCompassHeading.setText(String.format(Locale.US, "Heading: %03d° %s", (int) currentAzimuthDeg, getCompassCardinal((int) currentAzimuthDeg)));

        if (!hasPinnedLocation) {
            tvDistanceBearing.setText("No Active Spot Anchor Pinned");
            tvDirectionArrow.setText("⚓");
            tvLevelGuidance.setText("Deck / Level: --");
            return;
        }

        if (currentGpsLocation != null) {
            Location pinLoc = new Location("Pinned");
            pinLoc.setLatitude(pinnedLatitude);
            pinLoc.setLongitude(pinnedLongitude);

            float distanceMeters = currentGpsLocation.distanceTo(pinLoc);
            float bearingToPin = currentGpsLocation.bearingTo(pinLoc);
            if (bearingToPin < 0) bearingToPin += 360f;

            // Relative bearing relative to current phone orientation
            float relativeBearing = (bearingToPin - currentAzimuthDeg + 360f) % 360f;
            String arrowChar = getArrowForBearing(relativeBearing);

            tvDistanceBearing.setText(String.format(Locale.US, "Dist: %.1fm | Target: %03d°", distanceMeters, (int) bearingToPin));
            tvDirectionArrow.setText(arrowChar);

            // Altitude comparison if available
            if (currentGpsLocation.hasAltitude() && pinnedAltitude != 0.0) {
                double diffAlt = currentGpsLocation.getAltitude() - pinnedAltitude;
                String vertGuidance;
                if (Math.abs(diffAlt) < 2.0) {
                    vertGuidance = "Same Elevation / Deck";
                } else if (diffAlt > 0) {
                    vertGuidance = String.format(Locale.US, "Go Down %.1fm (~%.0f floors)", diffAlt, diffAlt / 3.0);
                } else {
                    vertGuidance = String.format(Locale.US, "Go Up %.1fm (~%.0f floors)", Math.abs(diffAlt), Math.abs(diffAlt) / 3.0);
                }
                tvLevelGuidance.setText(String.format(Locale.US, "Floor/Deck: Level %s (Spot: %s) • %s",
                        pinnedLevel.isEmpty() ? "--" : pinnedLevel,
                        pinnedSpot.isEmpty() ? "--" : pinnedSpot,
                        vertGuidance));
            } else {
                tvLevelGuidance.setText(String.format(Locale.US, "Floor/Deck: Level %s (Spot: %s)",
                        pinnedLevel.isEmpty() ? "--" : pinnedLevel,
                        pinnedSpot.isEmpty() ? "--" : pinnedSpot));
            }
        } else {
            tvDistanceBearing.setText("Acquiring GPS for distance calculation...");
            tvDirectionArrow.setText("◎");
        }
    }

    private String getCompassCardinal(int deg) {
        String[] cardinals = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};
        return cardinals[(int) Math.round(((double) deg % 360) / 45)];
    }

    private String getArrowForBearing(float relDeg) {
        if (relDeg >= 337.5 || relDeg < 22.5) return "▲ Straight Ahead";
        if (relDeg >= 22.5 && relDeg < 67.5) return "↗ Slight Right";
        if (relDeg >= 67.5 && relDeg < 112.5) return "▶ Hard Right";
        if (relDeg >= 112.5 && relDeg < 157.5) return "↘ Behind Right";
        if (relDeg >= 157.5 && relDeg < 202.5) return "▼ Behind You";
        if (relDeg >= 202.5 && relDeg < 247.5) return "↙ Behind Left";
        if (relDeg >= 247.5 && relDeg < 292.5) return "◀ Hard Left";
        return "↖ Slight Left";
    }

    private void dropCurrentAnchor() {
        if (currentGpsLocation == null) {
            Toast.makeText(this, "Awaiting GPS satellite fix before anchoring", Toast.LENGTH_SHORT).show();
            return;
        }

        hasPinnedLocation = true;
        pinnedLatitude = currentGpsLocation.getLatitude();
        pinnedLongitude = currentGpsLocation.getLongitude();
        pinnedAltitude = currentGpsLocation.hasAltitude() ? currentGpsLocation.getAltitude() : 0.0;
        pinnedLevel = etLevelNumber.getText().toString().trim();
        pinnedSpot = etSpotNumber.getText().toString().trim();
        pinnedNote = etParkingNotes.getText().toString().trim();
        pinnedTimestamp = System.currentTimeMillis();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_PIN_LAT, String.valueOf(pinnedLatitude))
                .putString(KEY_PIN_LON, String.valueOf(pinnedLongitude))
                .putString(KEY_PIN_ALT, String.valueOf(pinnedAltitude))
                .putString(KEY_PIN_LEVEL, pinnedLevel)
                .putString(KEY_PIN_SPOT, pinnedSpot)
                .putString(KEY_PIN_NOTE, pinnedNote)
                .putLong(KEY_PIN_TIME, pinnedTimestamp)
                .apply();

        saveToHistory(pinnedLatitude, pinnedLongitude, pinnedLevel, pinnedSpot, pinnedNote, pinnedTimestamp);
        renderHistoryList();
        updateHud();

        Toast.makeText(this, "Spot Anchored Successfully!", Toast.LENGTH_SHORT).show();
    }

    private void clearCurrentAnchor() {
        hasPinnedLocation = false;
        pinnedLatitude = 0.0;
        pinnedLongitude = 0.0;
        pinnedAltitude = 0.0;
        pinnedLevel = "";
        pinnedSpot = "";
        pinnedNote = "";
        pinnedTimestamp = 0L;

        etLevelNumber.setText("");
        etSpotNumber.setText("");
        etParkingNotes.setText("");

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_PIN_LAT)
                .remove(KEY_PIN_LON)
                .remove(KEY_PIN_ALT)
                .remove(KEY_PIN_LEVEL)
                .remove(KEY_PIN_SPOT)
                .remove(KEY_PIN_NOTE)
                .remove(KEY_PIN_TIME)
                .apply();

        updateHud();
        Toast.makeText(this, "Anchor Cleared", Toast.LENGTH_SHORT).show();
    }

    private void shareAnchorDetails() {
        if (!hasPinnedLocation) {
            Toast.makeText(this, "Drop an anchor before sharing", Toast.LENGTH_SHORT).show();
            return;
        }

        String mapUrl = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", pinnedLatitude, pinnedLongitude);
        String dateFormatted = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(pinnedTimestamp));

        StringBuilder sb = new StringBuilder();
        sb.append("📍 ParkPin Offline Spot Anchor\n");
        sb.append("Time: ").append(dateFormatted).append("\n");
        if (!pinnedLevel.isEmpty()) sb.append("Deck/Floor: Level ").append(pinnedLevel).append("\n");
        if (!pinnedSpot.isEmpty()) sb.append("Bay/Spot: ").append(pinnedSpot).append("\n");
        if (!pinnedNote.isEmpty()) sb.append("Notes: ").append(pinnedNote).append("\n");
        sb.append("Coordinates: ").append(String.format(Locale.US, "%.6f, %.6f", pinnedLatitude, pinnedLongitude)).append("\n");
        sb.append("Maps Link: ").append(mapUrl);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My Parked Vehicle Location");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share ParkPin Spot"));
    }

    private void addMeterTime(int minutes) {
        long now = System.currentTimeMillis();
        if (meterExpiryTimeMillis < now) {
            meterExpiryTimeMillis = now + (minutes * 60L * 1000L);
        } else {
            meterExpiryTimeMillis += (minutes * 60L * 1000L);
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putLong(KEY_METER_EXPIRY, meterExpiryTimeMillis)
                .apply();

        startMeterCountdown();
    }

    private void cancelMeterTimer() {
        meterExpiryTimeMillis = 0L;
        if (meterCountDownTimer != null) {
            meterCountDownTimer.cancel();
            meterCountDownTimer = null;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove(KEY_METER_EXPIRY)
                .apply();

        tvMeterCountdown.setText("Meter: Inactive");
        Toast.makeText(this, "Meter Timer Cancelled", Toast.LENGTH_SHORT).show();
    }

    private void startMeterCountdown() {
        if (meterCountDownTimer != null) {
            meterCountDownTimer.cancel();
        }

        long remaining = meterExpiryTimeMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            tvMeterCountdown.setText("Meter: EXPIRED!");
            return;
        }

        meterCountDownTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSecs = millisUntilFinished / 1000;
                long hours = totalSecs / 3600;
                long minutes = (totalSecs % 3600) / 60;
                long seconds = totalSecs % 60;
                tvMeterCountdown.setText(String.format(Locale.US, "Meter: %02d:%02d:%02d Remaining", hours, minutes, seconds));
            }

            @Override
            public void onFinish() {
                tvMeterCountdown.setText("Meter: EXPIRED! ⚠️");
                notifyMeterExpiry();
            }
        }.start();
    }

    private void notifyMeterExpiry() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500}, -1));
            } else {
                v.vibrate(500);
            }
        }
        try {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), alert);
            if (r != null) {
                r.play();
            }
        } catch (Exception ignored) {
        }
    }

    private void saveToHistory(double lat, double lon, String level, String spot, String notes, long timestamp) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyRaw = prefs.getString(KEY_HISTORY_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(historyRaw);
            JSONObject obj = new JSONObject();
            obj.put("lat", lat);
            obj.put("lon", lon);
            obj.put("level", level);
            obj.put("spot", spot);
            obj.put("notes", notes);
            obj.put("time", timestamp);

            // Prepend new item
            JSONArray newArr = new JSONArray();
            newArr.put(obj);
            for (int i = 0; i < Math.min(arr.length(), 14); i++) {
                newArr.put(arr.get(i));
            }
            prefs.edit().putString(KEY_HISTORY_JSON, newArr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderHistoryList() {
        containerHistory.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyRaw = prefs.getString(KEY_HISTORY_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(historyRaw);
            if (arr.length() == 0) {
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText("No parking history recorded yet.");
                tvEmpty.setTextColor(getColor(R.color.m3_on_surface_variant));
                tvEmpty.setPadding(8, 16, 8, 16);
                containerHistory.addView(tvEmpty);
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                double lat = obj.getDouble("lat");
                double lon = obj.getDouble("lon");
                String level = obj.optString("level", "-");
                String spot = obj.optString("spot", "-");
                String notes = obj.optString("notes", "");
                long time = obj.optLong("time", 0);

                LinearLayout itemLayout = new LinearLayout(this);
                itemLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.setBackgroundResource(R.drawable.bg_surface_card);
                itemLayout.setPadding(24, 20, 24, 20);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(0, 0, 0, 16);
                itemLayout.setLayoutParams(lp);

                TextView tvTitle = new TextView(this);
                tvTitle.setText(String.format(Locale.US, "Level: %s | Spot: %s • %s",
                        level.isEmpty() ? "--" : level,
                        spot.isEmpty() ? "--" : spot,
                        sdf.format(new Date(time))));
                tvTitle.setTextSize(14f);
                tvTitle.setTextColor(getColor(R.color.m3_on_surface));

                TextView tvCoords = new TextView(this);
                tvCoords.setText(String.format(Locale.US, "Coords: %.6f, %.6f %s", lat, lon,
                        notes.isEmpty() ? "" : "| " + notes));
                tvCoords.setTextSize(12f);
                tvCoords.setTextColor(getColor(R.color.m3_on_surface_variant));

                Button btnRestore = new Button(this, null, android.R.attr.borderlessButtonStyle);
                btnRestore.setText("Restore Anchor");
                btnRestore.setTextColor(getColor(R.color.m3_primary));
                btnRestore.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    restoreAnchor(lat, lon, level, spot, notes, time);
                });

                itemLayout.addView(tvTitle);
                itemLayout.addView(tvCoords);
                itemLayout.addView(btnRestore);

                containerHistory.addView(itemLayout);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void restoreAnchor(double lat, double lon, String level, String spot, String notes, long time) {
        hasPinnedLocation = true;
        pinnedLatitude = lat;
        pinnedLongitude = lon;
        pinnedAltitude = 0.0;
        pinnedLevel = level;
        pinnedSpot = spot;
        pinnedNote = notes;
        pinnedTimestamp = time;

        etLevelNumber.setText(level);
        etSpotNumber.setText(spot);
        etParkingNotes.setText(notes);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_PIN_LAT, String.valueOf(pinnedLatitude))
                .putString(KEY_PIN_LON, String.valueOf(pinnedLongitude))
                .putString(KEY_PIN_ALT, "0.0")
                .putString(KEY_PIN_LEVEL, pinnedLevel)
                .putString(KEY_PIN_SPOT, pinnedSpot)
                .putString(KEY_PIN_NOTE, pinnedNote)
                .putLong(KEY_PIN_TIME, pinnedTimestamp)
                .apply();

        updateHud();
        Toast.makeText(this, "Restored selected anchor!", Toast.LENGTH_SHORT).show();
    }

    private void clearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all recorded parking spots?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .remove(KEY_HISTORY_JSON)
                            .apply();
                    renderHistoryList();
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadPersistedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.contains(KEY_PIN_LAT) && prefs.contains(KEY_PIN_LON)) {
            try {
                pinnedLatitude = Double.parseDouble(prefs.getString(KEY_PIN_LAT, "0"));
                pinnedLongitude = Double.parseDouble(prefs.getString(KEY_PIN_LON, "0"));
                pinnedAltitude = Double.parseDouble(prefs.getString(KEY_PIN_ALT, "0"));
                pinnedLevel = prefs.getString(KEY_PIN_LEVEL, "");
                pinnedSpot = prefs.getString(KEY_PIN_SPOT, "");
                pinnedNote = prefs.getString(KEY_PIN_NOTE, "");
                pinnedTimestamp = prefs.getLong(KEY_PIN_TIME, 0L);
                hasPinnedLocation = true;

                etLevelNumber.setText(pinnedLevel);
                etSpotNumber.setText(pinnedSpot);
                etParkingNotes.setText(pinnedNote);
            } catch (Exception e) {
                hasPinnedLocation = false;
            }
        }

        meterExpiryTimeMillis = prefs.getLong(KEY_METER_EXPIRY, 0L);
        if (meterExpiryTimeMillis > System.currentTimeMillis()) {
            startMeterCountdown();
        } else if (meterExpiryTimeMillis > 0) {
            tvMeterCountdown.setText("Meter: EXPIRED!");
        }

        renderHistoryList();
        updateHud();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                btnDropPin.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                if (meterExpiryTimeMillis > System.currentTimeMillis()) {
                    btnMeterClear.performClick();
                } else {
                    btnMeter30m.performClick();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                btnMeter15m.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                btnMeterClear.performClick();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Freeform windowing resizing maintains state without reloading activity
        updateHud();
    }
}