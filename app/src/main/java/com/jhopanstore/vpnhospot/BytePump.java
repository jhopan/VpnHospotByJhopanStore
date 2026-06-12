package com.jhopanstore.vpnhospot;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

final class BytePump implements Runnable {
    enum Direction { UPLOAD, DOWNLOAD }

    private final InputStream input;
    private final OutputStream output;
    private final Socket client;
    private final Socket remote;
    private final TrafficCounter counter;
    private final Direction direction;

    BytePump(InputStream input, OutputStream output, Socket client, Socket remote,
             TrafficCounter counter, Direction direction) {
        this.input = input;
        this.output = output;
        this.client = client;
        this.remote = remote;
        this.counter = counter;
        this.direction = direction;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[16 * 1024];
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                output.flush();
                if (direction == Direction.UPLOAD) counter.addUpload(read);
                else counter.addDownload(read);
            }
        } catch (Exception ignored) {
        } finally {
            close(client);
            close(remote);
        }
    }

    static void close(Socket socket) {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
