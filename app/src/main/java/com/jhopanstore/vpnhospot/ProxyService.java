package com.jhopanstore.vpnhospot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;

public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    static final String ACTION_START = "com.jhopanstore.vpnhospot.START";
    static final String ACTION_STOP = "com.jhopanstore.vpnhospot.STOP";
    static final String EXTRA_HTTP_PORT = "http_port";
    static final String EXTRA_SOCKS_PORT = "socks_port";
    static final String EXTRA_COUNT_TRAFFIC = "count_traffic";

    private static final String CHANNEL_ID = "proxy";
    private static final long NOTIFICATION_UPDATE_INTERVAL_MS = 2000;

    private final LocalBinder binder = new LocalBinder();
    private final TrafficCounter counter = new TrafficCounter();
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private ProxyServer httpServer;
    private ProxyServer socksServer;
    private volatile boolean running;
    private volatile String lastMessage = "Proxy belum berjalan";
    private volatile int httpPort = 8080;
    private volatile int socksPort = 1080;

    private final Runnable notificationUpdater = new Runnable() {
        @Override
        public void run() {
            if (running) {
                updateNotification();
                notificationHandler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS);
            }
        }
    };

    public final class LocalBinder extends Binder {
        ProxyService getService() {
            return ProxyService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopProxy();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            httpPort = intent.getIntExtra(EXTRA_HTTP_PORT, 8080);
            socksPort = intent.getIntExtra(EXTRA_SOCKS_PORT, 1080);
            counter.setEnabled(intent.getBooleanExtra(EXTRA_COUNT_TRAFFIC, true));
            startForeground(1, buildNotification());
            startProxy(httpPort, socksPort);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopProxy();
        super.onDestroy();
    }

    boolean isRunning() {
        return running;
    }

    String lastMessage() {
        return lastMessage;
    }

    long uploadBytes() {
        return counter.uploadBytes();
    }

    long downloadBytes() {
        return counter.downloadBytes();
    }

    void setTrafficCounting(boolean enabled) {
        counter.setEnabled(enabled);
        updateNotification();
    }

    boolean isTrafficCounting() {
        return counter.isEnabled();
    }

    int httpPort() {
        return httpPort;
    }

    int socksPort() {
        return socksPort;
    }

    private synchronized void startProxy(int httpPort, int socksPort) {
        Log.e(TAG, "TRACE SERVICE START_PROXY http=" + httpPort + " socks=" + socksPort);
        stopProxy();
        try {
            counter.reset();
            httpServer = new ProxyServer("http-proxy", httpPort, counter, new HttpProxyHandler(this));
            socksServer = new ProxyServer("socks5-proxy", socksPort, counter, new Socks5ProxyHandler(this));
            httpServer.start();
            socksServer.start();
            running = true;
            lastMessage = "HTTP :" + httpPort + " dan SOCKS5 :" + socksPort + " berjalan";
            updateNotification();
            notificationHandler.postDelayed(notificationUpdater, NOTIFICATION_UPDATE_INTERVAL_MS);
        } catch (Exception e) {
            stopProxy();
            lastMessage = "Gagal start proxy: " + e.getMessage();
        }
    }

    private synchronized void stopProxy() {
        running = false;
        notificationHandler.removeCallbacks(notificationUpdater);
        if (httpServer != null) httpServer.stop();
        if (socksServer != null) socksServer.stop();
        httpServer = null;
        socksServer = null;
        lastMessage = "Proxy berhenti";
    }

    private Notification buildNotification() {
        createChannel();
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, ProxyService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("VPN Hospot Proxy")
                .setContentText(buildNotificationText())
                .setContentIntent(contentIntent)
                .setOngoing(running)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
                .build();
    }

    private void updateNotification() {
        if (!running) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, buildNotification());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Proxy", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Proxy berjalan di background");
        manager.createNotificationChannel(channel);
    }

    private String buildNotificationText() {
        StringBuilder sb = new StringBuilder(lastMessage);
        if (running && counter.isEnabled()) {
            sb.append("\n\u2191 ").append(formatBytes(counter.uploadBytes()))
              .append("  \u2193 ").append(formatBytes(counter.downloadBytes()));
        }
        return sb.toString();
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
