package com.jhopanstore.vpnhospot;

import android.util.Log;

import java.net.ServerSocket;
import java.net.Socket;

final class ProxyServer {
    private static final String TAG = "ProxyServer";

    interface Handler {
        void handle(Socket client, TrafficCounter counter);
    }

    private final int port;
    private final String name;
    private final TrafficCounter counter;
    private final Handler handler;
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
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new java.net.InetSocketAddress("0.0.0.0", port));
        running = true;
        Log.e(TAG, "TRACE " + name + " LISTEN " + serverSocket.getLocalSocketAddress());

        acceptThread = new Thread(() -> {
            Log.e(TAG, "TRACE " + name + " ACCEPT_THREAD_START");
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    client.setTcpNoDelay(true);
                    Log.e(TAG, "TRACE " + name + " ACCEPT " + client.getRemoteSocketAddress());

                    Thread worker = new Thread(() -> {
                        Log.e(TAG, "TRACE " + name + " WORKER_START " + client.getRemoteSocketAddress());
                        try {
                            handler.handle(client, counter);
                            Log.e(TAG, "TRACE " + name + " WORKER_DONE " + client.getRemoteSocketAddress());
                        } catch (Throwable t) {
                            Log.e(TAG, "TRACE " + name + " WORKER_CRASH " + t.getClass().getSimpleName() + " " + t.getMessage(), t);
                            try { client.close(); } catch (Exception ignored) {}
                        }
                    }, name + "-worker");
                    worker.start();
                } catch (Exception e) {
                    if (running) {
                        Log.e(TAG, "TRACE " + name + " ACCEPT_ERROR " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
                    }
                }
            }
            Log.e(TAG, "TRACE " + name + " ACCEPT_THREAD_STOP");
        }, name + "-accept");
        acceptThread.start();
    }

    void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        Log.e(TAG, "TRACE " + name + " STOPPED");
    }
}
