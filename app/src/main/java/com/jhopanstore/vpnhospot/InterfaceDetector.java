package com.jhopanstore.vpnhospot;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class InterfaceDetector {
    static final class HostEntry {
        final String ip;
        final String label;

        HostEntry(String ip, String label) {
            this.ip = ip;
            this.label = label;
        }
    }

    private InterfaceDetector() {
    }

    static List<HostEntry> listProxyHosts() {
        ArrayList<HostEntry> result = new ArrayList<>();
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : interfaces) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase(Locale.US);
                String label = classify(name);
                if (!"Wi-Fi hotspot".equals(label) && !"USB tether".equals(label)) continue;
                for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    String ip = address.getHostAddress();
                    result.add(new HostEntry(ip, label));
                }
            }
        } catch (Exception e) {
            result.add(new HostEntry("0.0.0.0", "Gagal membaca interface: " + e.getMessage()));
        }
        if (result.isEmpty()) {
            result.add(new HostEntry("0.0.0.0", "Belum ada IP hotspot/USB tethering"));
        }
        return result;
    }

    private static String classify(String name) {
        if (name.contains("tun") || name.startsWith("rmnet") || name.contains("clat")) return "Ignore";
        if (name.contains("rndis") || name.contains("usb")) return "USB tether";
        if (name.contains("wlan") || name.contains("swlan") || name.contains("ap")) return "Wi-Fi hotspot";
        return "Ignore";
    }
}
