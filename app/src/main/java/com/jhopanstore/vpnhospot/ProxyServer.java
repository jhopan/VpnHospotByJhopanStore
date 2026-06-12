package com.jhopanstore.vpnhospot;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ProxyServer {
    interface Handler {
        void handle(Socket client, TrafficCounter counter);
    }

    private final int port;
    private final String name;
    private final TrafficCounter counter;
    private final Handler handler;
    private final ExecutorService workers = Executors.newCachedThreadPool();
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
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    client.setTcpNoDelay(true);
                    workers.execute(() -> handler.handle(client, counter));
                } catch (Exception ignored) {
                    if (running) stop();
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
    }
}
