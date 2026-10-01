package com.screenstream.rtsp.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {

    public static String getPrimaryIpAddress(Context context) {
        // 1. Modern Android API 23+ ConnectivityManager LinkProperties lookup
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    LinkProperties lp = cm.getLinkProperties(activeNetwork);
                    if (lp != null) {
                        for (LinkAddress la : lp.getLinkAddresses()) {
                            InetAddress addr = la.getAddress();
                            if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                                String host = addr.getHostAddress();
                                if (host != null && !host.isEmpty()) {
                                    return host;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Legacy Wi-Fi IP fallback
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    int ipInt = wifiInfo.getIpAddress();
                    if (ipInt != 0) {
                        return (ipInt & 0xFF) + "." +
                                ((ipInt >> 8) & 0xFF) + "." +
                                ((ipInt >> 16) & 0xFF) + "." +
                                ((ipInt >> 24) & 0xFF);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback: search active network interfaces (wlan0, ap0, rndis0, eth0)
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            // Priority: wlan, ap (hotspot), rndis (usb tether), then any other non-loopback IPv4
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback()) continue;
                String name = intf.getName().toLowerCase();
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("rndis") || name.startsWith("eth")) {
                    for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }

            // General non-loopback IPv4 fallback
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback()) continue;
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return "127.0.0.1";
    }

    public static List<String> getAllIpAddresses() {
        List<String> ips = new ArrayList<>();
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback()) continue;
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        ips.add(addr.getHostAddress() + " (" + intf.getName() + ")");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ips;
    }
}
