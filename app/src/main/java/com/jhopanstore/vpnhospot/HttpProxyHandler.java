package com.jhopanstore.vpnhospot;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class HttpProxyHandler implements ProxyServer.Handler {
    private static final String TAG = "HttpProxy";
    private static final int HEADER_LIMIT = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int IDLE_TIMEOUT_MS = 120000;

    private final Context context;

    HttpProxyHandler(Context context) {
        this.context = context;
    }

    @Override
    public void handle(Socket client, TrafficCounter counter) {
        Socket remote = null;
        try {
            client.setSoTimeout(IDLE_TIMEOUT_MS);
            client.setTcpNoDelay(true);
            //             Log.i(TAG, "client accepted from " + client.getRemoteSocketAddress());
            //             Log.e(TAG, "TRACE HTTP HANDLE_START " + client.getRemoteSocketAddress());

            BufferedInputStream clientInput = new BufferedInputStream(client.getInputStream());
            OutputStream clientOutput = client.getOutputStream();
            byte[] headerBytes = readHeader(clientInput);
            if (headerBytes.length == 0) {
                //                 Log.w(TAG, "empty request from " + client.getRemoteSocketAddress());
                return;
            }

            String header = new String(headerBytes, StandardCharsets.ISO_8859_1);
            String firstLine = header.split("\\r?\\n", 2)[0];
            //             Log.i(TAG, "request: " + firstLine);
            //             Log.e(TAG, "TRACE HTTP REQUEST " + firstLine);

            String[] lines = header.split("\\r?\\n");
            String[] request = firstLine.split(" ", 3);
            if (request.length < 2) {
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            String method = request[0].toUpperCase(Locale.US);
            if ("CONNECT".equals(method)) {
                String[] hostPort = request[1].split(":", 2);
                String host = hostPort[0];
                int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 443;
                remote = connectRemote(host, port);

                clientOutput.write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: JhopanStore\r\n\r\n"
                        .getBytes(StandardCharsets.ISO_8859_1));
                clientOutput.flush();
                //                 Log.i(TAG, "CONNECT tunnel established: " + host + ":" + port);
                tunnel(client, clientInput, remote, counter);
                return;
            }

            Target target = resolveTarget(request[1], lines);
            if (target == null) {
                //                 Log.w(TAG, "cannot resolve target for: " + firstLine);
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            remote = connectRemote(target.host, target.port);
            OutputStream remoteOutput = remote.getOutputStream();
            byte[] rewritten = rewriteRequest(header, request, target.path);
            remoteOutput.write(rewritten);
            remoteOutput.flush();
            counter.addUpload(rewritten.length);
            //             Log.i(TAG, "HTTP tunnel established: " + target.host + ":" + target.port);
            tunnel(client, clientInput, remote, counter);
        } catch (Exception e) {
            //             Log.e(TAG, "handle failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
        } finally {
            BytePump.close(client);
            if (remote != null) BytePump.close(remote);
        }
    }

    private Socket connectRemote(String host, int port) throws Exception {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(IDLE_TIMEOUT_MS);
        bindToVpnOrActiveNetwork(socket);
        //         Log.i(TAG, "connecting remote " + host + ":" + port);
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        //         Log.i(TAG, "remote connected " + host + ":" + port);
        return socket;
    }

    private void bindToVpnOrActiveNetwork(Socket socket) {
        try {
            if (context == null) return;
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            Network chosen = null;

            // User needs all traffic through Dark Tunnel/VPN. Prefer VPN, not cellular.
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null) continue;
                //                 Log.e(TAG, "TRACE HTTP NETWORK " + describeNetwork(caps));
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    chosen = network;
                    //                     Log.e(TAG, "TRACE HTTP BIND_SELECT VPN");
                    break;
                }
            }

            if (chosen == null) {
                Network active = cm.getActiveNetwork();
                if (active != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        chosen = active;
                        //                         Log.e(TAG, "TRACE HTTP BIND_SELECT ACTIVE " + describeNetwork(caps));
                    }
                }
            }

            if (chosen != null) {
                chosen.bindSocket(socket);
                //                 Log.e(TAG, "TRACE HTTP BOUND_SOCKET");
            } else {
                //                 Log.e(TAG, "TRACE HTTP NO_NETWORK_BOUND");
            }
        } catch (Exception e) {
            //             Log.w(TAG, "bind network failed: " + e.getMessage());
        }
    }

    private static String describeNetwork(NetworkCapabilities caps) {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "CELLULAR";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WIFI";
        return caps.toString();
    }

    private static void tunnel(Socket client, BufferedInputStream clientInput, Socket remote, TrafficCounter counter)
            throws InterruptedException {
        Thread up = new Thread(new BytePump(clientInput, safeOutput(remote), client, remote, counter, BytePump.Direction.UPLOAD), "http-upload");
        Thread down = new Thread(new BytePump(safeInput(remote), safeOutput(client), client, remote, counter, BytePump.Direction.DOWNLOAD), "http-download");
        up.start();
        down.start();
        up.join();
        down.join();
    }

    private static java.io.InputStream safeInput(Socket socket) {
        try { return socket.getInputStream(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static OutputStream safeOutput(Socket socket) {
        try { return socket.getOutputStream(); }
        catch (Exception e) { throw new IllegalStateException(e); }
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
            if (out.size() > HEADER_LIMIT) throw new IllegalArgumentException("Header too large");
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
        } catch (Exception ignored) {}

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
            if (lower.startsWith("connection:")) continue;
            out.append(lines[i]).append("\r\n");
        }
        out.append("Connection: close\r\n\r\n");
        return out.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void sendError(OutputStream out, int code, String message) {
        try {
            String body = code + " " + message + "\n";
            String response = "HTTP/1.1 " + code + " " + message + "\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body;
            out.write(response.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        } catch (Exception ignored) {}
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
