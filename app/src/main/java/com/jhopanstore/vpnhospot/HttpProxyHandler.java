package com.jhopanstore.vpnhospot;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class HttpProxyHandler implements ProxyServer.Handler {
    @Override
    public void handle(Socket client, TrafficCounter counter) {
        Socket remote = null;
        try {
            BufferedInputStream clientInput = new BufferedInputStream(client.getInputStream());
            OutputStream clientOutput = client.getOutputStream();
            byte[] headerBytes = readHeader(clientInput);
            if (headerBytes.length == 0) return;

            String header = new String(headerBytes, StandardCharsets.ISO_8859_1);
            String[] lines = header.split("\\r?\\n");
            if (lines.length == 0) return;
            String[] request = lines[0].split(" ", 3);
            if (request.length < 2) return;

            String method = request[0].toUpperCase(Locale.US);
            if ("CONNECT".equals(method)) {
                String[] hostPort = request[1].split(":", 2);
                String host = hostPort[0];
                int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 443;
                remote = new Socket(host, port);
                remote.setTcpNoDelay(true);
                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                clientOutput.flush();
                tunnel(client, clientInput, remote, counter);
                return;
            }

            Target target = resolveTarget(request[1], lines);
            if (target == null) return;
            remote = new Socket(target.host, target.port);
            remote.setTcpNoDelay(true);
            OutputStream remoteOutput = remote.getOutputStream();
            byte[] rewritten = rewriteRequest(header, request, target.path);
            remoteOutput.write(rewritten);
            remoteOutput.flush();
            counter.addUpload(rewritten.length);
            tunnel(client, clientInput, remote, counter);
        } catch (Exception ignored) {
        } finally {
            BytePump.close(client);
            if (remote != null) BytePump.close(remote);
        }
    }

    private static void tunnel(Socket client, BufferedInputStream clientInput, Socket remote, TrafficCounter counter)
            throws InterruptedException {
        Thread up = new Thread(new BytePump(clientInput, safeOutput(remote), client, remote, counter, BytePump.Direction.UPLOAD));
        Thread down = new Thread(new BytePump(safeInput(remote), safeOutput(client), client, remote, counter, BytePump.Direction.DOWNLOAD));
        up.start();
        down.start();
        up.join();
        down.join();
    }

    private static java.io.InputStream safeInput(Socket socket) {
        try {
            return socket.getInputStream();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static OutputStream safeOutput(Socket socket) {
        try {
            return socket.getOutputStream();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readHeader(BufferedInputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int match = 0;
        int b;
        byte[] end = new byte[]{'\r', '\n', '\r', '\n'};
        while ((b = input.read()) != -1) {
            out.write(b);
            if (b == end[match]) {
                match++;
                if (match == end.length) break;
            } else {
                match = b == end[0] ? 1 : 0;
            }
            if (out.size() > 64 * 1024) throw new IllegalArgumentException("Header too large");
        }
        return out.toByteArray();
    }

    private static Target resolveTarget(String rawUri, String[] lines) {
        try {
            URI uri = URI.create(rawUri);
            if (uri.getHost() != null) {
                int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
                String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
                return new Target(uri.getHost(), port, path);
            }
        } catch (Exception ignored) {
        }
        String host = null;
        for (String line : lines) {
            if (line.toLowerCase(Locale.US).startsWith("host:")) {
                host = line.substring(5).trim();
                break;
            }
        }
        if (host == null || host.isEmpty()) return null;
        int port = 80;
        int colon = host.lastIndexOf(':');
        if (colon > 0) {
            port = Integer.parseInt(host.substring(colon + 1));
            host = host.substring(0, colon);
        }
        return new Target(host, port, rawUri.isEmpty() ? "/" : rawUri);
    }

    private static byte[] rewriteRequest(String header, String[] request, String path) {
        StringBuilder out = new StringBuilder();
        out.append(request[0]).append(' ').append(path).append(' ');
        out.append(request.length > 2 ? request[2] : "HTTP/1.1").append("\r\n");
        String[] lines = header.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String lower = lines[i].toLowerCase(Locale.US);
            if (lower.startsWith("proxy-connection:")) continue;
            out.append(lines[i]).append("\r\n");
        }
        out.append("\r\n");
        return out.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static final class Target {
        final String host;
        final int port;
        final String path;

        Target(String host, int port, String path) {
            this.host = host;
            this.port = port;
            this.path = path;
        }
    }
}
