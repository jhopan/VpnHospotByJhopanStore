package com.jhopanstore.vpnhospot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 10, 12);
    private static final int CARD = Color.rgb(24, 24, 30);
    private static final int TEXT_PRIMARY = Color.rgb(245, 245, 247);
    private static final int TEXT_SECONDARY = Color.rgb(136, 136, 136);
    private static final int GREEN = Color.rgb(28, 184, 98);
    private static final int RED = Color.rgb(220, 60, 60);
    private static final int GRAY = Color.rgb(96, 102, 114);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProxyService service;
    private boolean bound;
    private EditText httpPortInput;
    private EditText socksPortInput;
    private Switch trafficSwitch;
    private TextView statusText;
    private TextView addressText;
    private TextView downloadValue;
    private TextView uploadValue;
    private Button toggleButton;
    private SharedPreferences prefs;

    private long lastAddressRefreshMs = 0;
    private boolean lastRunningState = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ProxyService.LocalBinder) binder).getService();
            bound = true;
            updateUi();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        requestNotificationPermission();
        setContentView(buildContent());
        updateUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, ProxyService.class), connection, Context.BIND_AUTO_CREATE);
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(tick);
        if (bound) unbindService(connection);
        bound = false;
        super.onStop();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateUi();
            handler.postDelayed(this, 1000);
        }
    };

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("VPN Hospot", 28, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        LinearLayout ports = row();
        httpPortInput = portInput("8080", prefs.getInt("http_port", 8080));
        socksPortInput = portInput("1080", prefs.getInt("socks_port", 1080));
        ports.addView(fieldBox("HTTP", httpPortInput), weight());
        ports.addView(space(dp(10)), new LinearLayout.LayoutParams(dp(10), 1));
        ports.addView(fieldBox("SOCKS5", socksPortInput), weight());
        root.addView(ports, marginTop(matchWrap(), 20));

        trafficSwitch = new Switch(this);
        trafficSwitch.setText("Hitung Download / Upload");
        trafficSwitch.setTextSize(15);
        trafficSwitch.setTextColor(TEXT_PRIMARY);
        trafficSwitch.setChecked(prefs.getBoolean("count_traffic", true));
        trafficSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean("count_traffic", isChecked).apply();
            if (bound && service != null) service.setTrafficCounting(isChecked);
            updateUi();
        });
        root.addView(trafficSwitch, marginTop(matchWrap(), 12));

        toggleButton = button("Start Proxy");
        toggleButton.setOnClickListener(v -> {
            boolean running = bound && service != null && service.isRunning();
            if (running) stopProxy();
            else startProxy();
        });
        root.addView(toggleButton, marginTop(matchWrap(), 14));

        Button guide = button("Cara Konek HTTP Proxy");
        guide.setBackgroundColor(Color.rgb(86, 96, 111));
        guide.setOnClickListener(v -> showConnectionGuide());
        root.addView(guide, marginTop(matchWrap(), 8));

        Button socks5Guide = button("Cara Konek SOCKS5");
        socks5Guide.setBackgroundColor(Color.rgb(70, 110, 130));
        socks5Guide.setOnClickListener(v -> showSocks5Guide());
        root.addView(socks5Guide, marginTop(matchWrap(), 8));

        Button developer = button("Info Developer");
        developer.setBackgroundColor(Color.rgb(32, 36, 46));
        developer.setOnClickListener(v -> showDeveloperInfo());
        root.addView(developer, marginTop(matchWrap(), 8));

        TextView footer = text("Powered by JhopanStore", 12, false);
        footer.setGravity(Gravity.CENTER);
        footer.setTextColor(TEXT_SECONDARY);
        root.addView(footer, marginTop(matchWrap(), 14));

        statusText = text("", 15, true);
        root.addView(statusText, marginTop(matchWrap(), 20));

        root.addView(buildRealtimePanel(), marginTop(matchWrap(), 12));

        addressText = text("", 14, false);
        addressText.setTextColor(TEXT_SECONDARY);
        root.addView(addressText, marginTop(matchWrap(), 12));

        return scroll;
    }

    private View buildRealtimePanel() {
        LinearLayout panel = row();
        panel.setBackgroundColor(CARD);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout downCol = metricColumn("DOWNLOAD");
        LinearLayout upCol = metricColumn("UPLOAD");

        downloadValue = metricValue();
        uploadValue = metricValue();
        downCol.addView(downloadValue, matchWrap());
        upCol.addView(uploadValue, matchWrap());

        panel.addView(downCol, new LinearLayout.LayoutParams(0, -2, 1f));
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(50, 50, 58));
        panel.addView(divider, new LinearLayout.LayoutParams(dp(1), -1));
        panel.addView(upCol, new LinearLayout.LayoutParams(0, -2, 1f));

        return panel;
    }

    private LinearLayout metricColumn(String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(2), dp(10), dp(2));

        TextView caption = text(label, 11, true);
        caption.setTextColor(TEXT_SECONDARY);
        caption.setAllCaps(true);
        caption.setLetterSpacing(0.1f);
        col.addView(caption, matchWrap());
        return col;
    }

    private TextView metricValue() {
        TextView view = new TextView(this);
        view.setTextColor(TEXT_PRIMARY);
        view.setSingleLine(true);
        view.setMaxLines(1);
        view.setGravity(Gravity.START);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextSize(20);
        view.setIncludeFontPadding(false);
        if (Build.VERSION.SDK_INT >= 21) view.setFontFeatureSettings("tnum");
        if (Build.VERSION.SDK_INT >= 26) {
            view.setAutoSizeTextTypeUniformWithConfiguration(12, 22, 1, TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        }
        return view;
    }

    private void startProxy() {
        int httpPort = parsePort(httpPortInput, 8080);
        int socksPort = parsePort(socksPortInput, 1080);
        prefs.edit()
                .putInt("http_port", httpPort)
                .putInt("socks_port", socksPort)
                .putBoolean("count_traffic", trafficSwitch.isChecked())
                .apply();

        Intent intent = new Intent(this, ProxyService.class)
                .setAction(ProxyService.ACTION_START)
                .putExtra(ProxyService.EXTRA_HTTP_PORT, httpPort)
                .putExtra(ProxyService.EXTRA_SOCKS_PORT, socksPort)
                .putExtra(ProxyService.EXTRA_COUNT_TRAFFIC, trafficSwitch.isChecked());
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
        bindService(new Intent(this, ProxyService.class), connection, Context.BIND_AUTO_CREATE);
        updateUi();
    }

    private void stopProxy() {
        startService(new Intent(this, ProxyService.class).setAction(ProxyService.ACTION_STOP));
        updateUi();
    }

    private void updateUi() {
        boolean running = bound && service != null && service.isRunning();
        
        // Status simpel
        statusText.setText("Status: " + (running ? "Running" : "Stop"));

        applyButtonStates(running);

        long downloadBytes = (running && trafficSwitch.isChecked() && bound && service != null)
                ? service.downloadBytes() : 0;
        long uploadBytes = (running && trafficSwitch.isChecked() && bound && service != null)
                ? service.uploadBytes() : 0;
        setTotalText(downloadValue, downloadBytes);
        setTotalText(uploadValue, uploadBytes);

        long now = System.currentTimeMillis();
        boolean shouldRefreshAddress = (now - lastAddressRefreshMs > 3000) || (running != lastRunningState);
        if (shouldRefreshAddress) {
            int activeHttpPort = running && service != null ? service.httpPort() : parsePort(httpPortInput, 8080);
            int activeSocksPort = running && service != null ? service.socksPort() : parsePort(socksPortInput, 1080);
            List<InterfaceDetector.HostEntry> hosts = InterfaceDetector.listProxyHosts();
            StringBuilder builder = new StringBuilder();
            builder.append("Proxy Aktif:\n");
            for (InterfaceDetector.HostEntry host : hosts) {
                if (host.ip.equals("0.0.0.0")) {
                    builder.append("- ").append(host.label).append('\n');
                    continue;
                }
                builder.append("- ").append(host.label).append(" HTTP  : ")
                        .append(host.ip).append(':').append(activeHttpPort).append('\n');
                builder.append("  ").append(host.label).append(" SOCKS5: ")
                        .append(host.ip).append(':').append(activeSocksPort).append('\n');
            }
            addressText.setText(builder.toString());
            lastAddressRefreshMs = now;
        }
        lastRunningState = running;
    }

    private void applyButtonStates(boolean running) {
        toggleButton.setText(running ? "Stop Proxy" : "Start Proxy");
        toggleButton.setBackgroundColor(running ? RED : GREEN);
        toggleButton.setEnabled(true);

        httpPortInput.setEnabled(!running);
        socksPortInput.setEnabled(!running);
    }

    private void setTotalText(TextView view, long bytes) {
        String[] parts = humanBytes(bytes);
        view.setText(parts[0] + " " + parts[1]);
    }

    private String[] humanBytes(long bytes) {
        double value = Math.max(0, bytes);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        String number = unit == 0 ? String.format(Locale.US, "%.0f", value) : String.format(Locale.US, "%.2f", value);
        return new String[]{number, units[unit]};
    }

    private void showConnectionGuide() {
        int httpPort = bound && service != null && service.isRunning() ? service.httpPort() : parsePort(httpPortInput, 8080);
        String guide = "1) Android\n"
                + "- Hubungkan ke hotspot/USB dari HP server.\n"
                + "- Buka Wi-Fi yang terhubung > Modify network > Advanced.\n"
                + "- Proxy: Manual.\n"
                + "- Hostname: isi IP server dari daftar di app.\n"
                + "- Port: " + httpPort + ". Simpan.\n\n"
                + "2) iPhone (iOS)\n"
                + "- Join ke Wi-Fi hotspot server.\n"
                + "- Settings > Wi-Fi > (i) pada jaringan.\n"
                + "- Scroll ke HTTP Proxy > Manual.\n"
                + "- Server: IP server, Port: " + httpPort + ".\n"
                + "- Save.\n\n"
                + "3) Windows 10/11\n"
                + "- Terhubung ke hotspot/USB tether server.\n"
                + "- Settings > Network & Internet > Proxy.\n"
                + "- Aktifkan Use a proxy server.\n"
                + "- Address: IP server, Port: " + httpPort + ". Save.\n\n"
                + "4) macOS\n"
                + "- System Settings > Network > koneksi aktif > Details.\n"
                + "- Proxies > centang Web Proxy (HTTP) dan Secure Web Proxy (HTTPS).\n"
                + "- Server: IP server, Port: " + httpPort + ".\n"
                + "- OK > Apply.\n\n"
                + "5) Linux (GNOME)\n"
                + "- Settings > Network > Network Proxy.\n"
                + "- Method: Manual.\n"
                + "- HTTP Proxy: IP server, Port: " + httpPort + ".\n"
                + "- Untuk terminal: export http_proxy=http://IP:" + httpPort + "\n\n"
                + "Catatan: jalankan Start Proxy dulu, lalu pakai IP yang bertanda Wi-Fi hotspot/USB tether.";

        new AlertDialog.Builder(this)
                .setTitle("Panduan Koneksi HTTP Proxy")
                .setMessage(guide)
                .setPositiveButton("Tutup", null)
                .show();
    }

    private void showSocks5Guide() {
        int socksPort = bound && service != null && service.isRunning() ? service.socksPort() : parsePort(socksPortInput, 1080);
        String guide = "SOCKS5 Proxy adalah protokol yang lebih canggih dari HTTP Proxy. "
                + "Support TCP + UDP, cocok untuk VPN, gaming, dan aplikasi yang butuh UDP.\n\n"
                + "═══ CARA KONEK ═══\n\n"
                + "📌 1) Pakai Socks Client App (RECOMMENDED)\n"
                + "- Install app \"Socks Client by JhopanStore\" di HP client.\n"
                + "- Hubungkan HP client ke hotspot/USB tether server.\n"
                + "- Buka Socks Client, isi:\n"
                + "  • Host: IP server (lihat di app ini)\n"
                + "  • Port: " + socksPort + "\n"
                + "  • Username & Password: kosongkan (kecuali server pakai auth)\n"
                + "- Tekan \"Connect Socks VPN\" → izinkan VPN Android.\n"
                + "- SEMUA trafik (TCP + UDP) otomatis lewat SOCKS5!\n\n"
                + "📌 2) Dari Aplikasi yang Support SOCKS5\n"
                + "Beberapa aplikasi punya setting SOCKS5 bawaan:\n"
                + "- Telegram: Settings > Data & Storage > Use Proxy > Add SOCKS5\n"
                + "- Firefox: Settings > Network Settings > Manual proxy > SOCKS Host\n"
                + "- Socks IP: IP server, Port: " + socksPort + "\n\n"
                + "📌 3) Dari PC/Laptop\n"
                + "- Windows: Gunakan app seperti Proxifier atau SocksCap64\n"
                + "- Linux: proxychains4 atau tsocks\n"
                + "  $ export ALL_PROXY=socks5://IP:" + socksPort + "\n"
                + "- macOS: sama seperti HTTP proxy tapi pilih SOCKS Proxy\n\n"
                + "═══ KEUNGGULAN SOCKS5 ═══\n\n"
                + "• TCP + UDP (HTTP proxy hanya TCP)\n"
                + "• DNS remote via tunnel (anti DNS leak)\n"
                + "• Support semua protokol (game, VoIP, streaming)\n"
                + "• Lebih cepat untuk UDP-heavy apps\n\n"
                + "═══ TROUBLESHOOTING ═══\n\n"
                + "❌ Failed connect → Cek HP client terhubung ke hotspot server\n"
                + "❌ Auth failed → Pastikan username/password benar\n"
                + "❌ UDP tidak jalan → Server harus support UDP Associate\n"
                + "❌ Internet lambat → Cek sinyal data HP server\n\n"
                + "💡 Tips: Untuk gaming, pastikan server punya sinyal data yang kuat!";

        new AlertDialog.Builder(this)
                .setTitle("Panduan Koneksi SOCKS5")
                .setMessage(guide)
                .setPositiveButton("Tutup", null)
                .show();
    }

    private void showDeveloperInfo() {
        String version = "v1.0.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        String message = "VPN Hotspot by JhopanStore\n"
                + "Version: " + version + "\n\n"
                + "Telegram: https://t.me/jhopan_05\n"
                + "Website: https://jhopanstore.my.id";
        new AlertDialog.Builder(this)
                .setTitle("Info Developer")
                .setMessage(message)
                .setPositiveButton("Telegram", (dialog, which) -> openUrl("https://t.me/jhopan_05"))
                .setNegativeButton("Website", (dialog, which) -> openUrl("https://jhopanstore.my.id"))
                .setNeutralButton("Tutup", null)
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
        }
    }

    private int parsePort(EditText input, int fallback) {
        try {
            int port = Integer.parseInt(input.getText().toString().trim());
            return port > 0 && port <= 65535 ? port : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(TEXT_PRIMARY);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private EditText portInput(String hint, int value) {
        EditText input = new EditText(this);
        input.setText(String.valueOf(value));
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_SECONDARY);
        input.setBackgroundColor(CARD);
        if (Build.VERSION.SDK_INT >= 21) input.setFontFeatureSettings("tnum");
        return input;
    }

    private LinearLayout fieldBox(String label, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(6));
        box.setBackgroundColor(CARD);
        TextView text = text(label, 13, true);
        text.setGravity(Gravity.CENTER);
        text.setTextColor(TEXT_SECONDARY);
        box.addView(text, matchWrap());
        box.addView(input, matchWrap());
        return box;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        return button;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private View space(int width) {
        View view = new View(this);
        view.setMinimumWidth(width);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private LinearLayout.LayoutParams marginTop(LinearLayout.LayoutParams params, int topDp) {
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
