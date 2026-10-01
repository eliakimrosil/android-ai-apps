package com.devsys.monitor;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView tvDeviceSummary;
    private TextView tvBatteryPercent;
    private TextView tvBatteryStatus;
    private ProgressBar pbBattery;
    private TextView tvBatteryDetails;
    private TextView tvRamUsage;
    private ProgressBar pbRam;
    private TextView tvRamAvailable;
    private TextView tvHardwareInfo;
    private TextView tvNetworkInfo;
    private Button btnRefresh;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        displayHardwareInfo();
        refreshAll();

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshAll();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
        refreshAll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
        }
    }

    private void initViews() {
        tvDeviceSummary = findViewById(R.id.tv_device_summary);
        tvBatteryPercent = findViewById(R.id.tv_battery_percent);
        tvBatteryStatus = findViewById(R.id.tv_battery_status);
        pbBattery = findViewById(R.id.pb_battery);
        tvBatteryDetails = findViewById(R.id.tv_battery_details);
        tvRamUsage = findViewById(R.id.tv_ram_usage);
        pbRam = findViewById(R.id.pb_ram);
        tvRamAvailable = findViewById(R.id.tv_ram_available);
        tvHardwareInfo = findViewById(R.id.tv_hardware_info);
        tvNetworkInfo = findViewById(R.id.tv_network_info);
        btnRefresh = findViewById(R.id.btn_refresh);
    }

    private void refreshAll() {
        updateMemory();
        updateNetwork();
    }

    private void displayHardwareInfo() {
        String deviceName;
        if (Build.MODEL.toLowerCase(Locale.US).startsWith(Build.MANUFACTURER.toLowerCase(Locale.US))) {
            deviceName = Build.MODEL;
        } else {
            deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        }
        tvDeviceSummary.setText(deviceName + " (Snapdragon 8+ Gen 1)");

        StringBuilder sb = new StringBuilder();
        sb.append("• Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        sb.append("• Model: ").append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n");
        sb.append("• Hardware: ").append(Build.HARDWARE).append("\n");
        sb.append("• Board: ").append(Build.BOARD).append("\n");
        sb.append("• Android: ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("• Supported ABIs: ");
        if (Build.SUPPORTED_ABIS != null) {
            for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) {
                sb.append(Build.SUPPORTED_ABIS[i]);
                if (i < Build.SUPPORTED_ABIS.length - 1) sb.append(", ");
            }
        }
        tvHardwareInfo.setText(sb.toString());
    }

    private void updateBattery(Intent intent) {
        if (intent == null) return;

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = (level >= 0 && scale > 0) ? (level * 100) / scale : 0;

        tvBatteryPercent.setText(String.format(Locale.US, "%d%%", percent));
        pbBattery.setProgress(percent);

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL;

        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        String chargeSource = "";
        if (plugged == BatteryManager.BATTERY_PLUGGED_AC) chargeSource = " (AC)";
        else if (plugged == BatteryManager.BATTERY_PLUGGED_USB) chargeSource = " (USB)";
        else if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) chargeSource = " (Wireless)";

        if (status == BatteryManager.BATTERY_STATUS_FULL) {
            tvBatteryStatus.setText("Full");
        } else if (isCharging) {
            tvBatteryStatus.setText("Charging" + chargeSource);
        } else {
            tvBatteryStatus.setText("Discharging");
        }

        int tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        double tempC = tempTenths / 10.0;
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        String health;
        switch (healthInt) {
            case BatteryManager.BATTERY_HEALTH_GOOD: health = "Good"; break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: health = "Overheat"; break;
            case BatteryManager.BATTERY_HEALTH_DEAD: health = "Dead"; break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: health = "Over Voltage"; break;
            default: health = "Normal"; break;
        }

        String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        if (technology == null) technology = "Li-ion";

        String details = String.format(Locale.US,
                "Temp: %.1f °C  |  Voltage: %d mV  |  Health: %s  |  Type: %s",
                tempC, voltage, health, technology);
        tvBatteryDetails.setText(details);
    }

    private void updateMemory() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);

        double totalGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
        double availGb = mi.availMem / (1024.0 * 1024.0 * 1024.0);
        double usedGb = totalGb - availGb;
        int usedPercent = (int) Math.round((usedGb / totalGb) * 100);

        tvRamUsage.setText(String.format(Locale.US, "Used: %.2f GB / Total: %.2f GB (%d%%)", usedGb, totalGb, usedPercent));
        pbRam.setProgress(usedPercent);
        tvRamAvailable.setText(String.format(Locale.US, "Available RAM: %.2f GB (Low Memory Alert: %s)",
                availGb, mi.lowMemory ? "YES" : "No"));
    }

    private void updateNetwork() {
        StringBuilder sb = new StringBuilder();
        try {
            java.util.Enumeration<NetworkInterface> interfacesEnum = NetworkInterface.getNetworkInterfaces();
            if (interfacesEnum != null) {
                List<NetworkInterface> interfaces = Collections.list(interfacesEnum);
                for (NetworkInterface intf : interfaces) {
                    if (!intf.isUp() || intf.isLoopback()) continue;
                    List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addrs) {
                        if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                            String name = intf.getName();
                            if (name.startsWith("wlan")) name = "Wi-Fi (" + name + ")";
                            else if (name.startsWith("rmnet") || name.startsWith("ccmni")) name = "Cellular (" + name + ")";
                            sb.append("• ").append(name).append(": ")
                              .append(addr.getHostAddress()).append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("• Network query: ").append(e.getMessage()).append("\n");
        }

        if (sb.length() == 0) {
            sb.append("• Wi-Fi / Mobile: Disconnected\n");
        }
        sb.append("• Loopback: 127.0.0.1 (ADB Localhost Active)");
        tvNetworkInfo.setText(sb.toString());
    }
}
