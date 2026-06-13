package com.jhopanstore.vpnhospot;

import android.util.Log;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class ProxyServer {
    private static final String TAG = "ProxyServer";

    interface Handler {
        void handle(Socket client, TrafficCounter counter);
    }

    private final int port;
    private final String name;
    private final TrafficCounter counter;
    private final Handler handler;
    // Bounded pool: max 128 koneksi bersamaan, queue 64, idle thread mati setelah 60s
    private final ExecutorService workers = new ThreadPoolExecutor(
            4, 128, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64));
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    ProxyServer(String name, int port, TrafficCounter counter, Handler handler) {
        this.name = name;
        this.port = port;
        this.counter = counter;
        this.handler = handler;
    }

    void start() throws Exception {
        if (running) return;
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        running = true;
        Log.i(TAG, name + " listening on port " + port);
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    client.setTcpNoDelay(true);
                    try {
                        workers.execute(() -> handler.handle(client, counter));
                    } catch (java.util.concurrent.RejectedExecutionException e) {
                        Log.w(TAG, name + " pool full, rejecting " + client.getInetAddress());
                        try { client.close(); } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    if (running) {
                        Log.e(TAG, name + " accept error: " + e.getMessage() + " — restarting in 1s");
                        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }, name + "-accept");
        acceptThread.start();
    }

    void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
        workers.shutdownNow();
        Log.i(TAG, name + " stopped");
    }
}
