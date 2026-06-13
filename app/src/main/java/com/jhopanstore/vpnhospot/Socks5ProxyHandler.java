package com.jhopanstore.vpnhospot;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SOCKS5 handler — mendukung TCP CONNECT (0x01) dan UDP ASSOCIATE (0x03).
 * RFC 1928 compliant untuk handshake, CONNECT, dan UDP relay.
 */
final class Socks5ProxyHandler implements ProxyServer.Handler {

    private static final String TAG = "Socks5Proxy";

    private static final int SOCKS5_VERSION = 0x05;
    private static final int CMD_CONNECT = 0x01;
    private static final int CMD_UDP_ASSOCIATE = 0x03;
    private static final int ATYP_IPV4 = 0x01;
    private static final int ATYP_DOMAIN = 0x03;
    private static final int ATYP_IPV6 = 0x04;
    private static final int METHOD_NO_AUTH = 0x00;
    private static final int METHOD_NO_ACCEPTABLE = 0xFF;
    private static final int TIMEOUT_MS = 120_000;
    private static final int UDP_BUF_SIZE = 65535;

    private final Context context;

    Socks5ProxyHandler(Context context) {
        this.context = context;
    }

    @Override
    public void handle(Socket client, TrafficCounter counter) {
        String clientId = client.getInetAddress().getHostAddress() + ":" + client.getPort();
        Socket remote = null;
        DatagramSocket udpRelay = null;
        try {
            client.setSoTimeout(TIMEOUT_MS);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            // ── SOCKS5 Handshake ──
            if (in.read() != SOCKS5_VERSION) return;
            int nMethods = in.read();
            if (nMethods <= 0) return;
            byte[] methods = readExact(in, nMethods);

            boolean hasNoAuth = false;
            for (byte m : methods) {
                if ((m & 0xFF) == METHOD_NO_AUTH) { hasNoAuth = true; break; }
            }
            out.write(new byte[]{SOCKS5_VERSION, (byte) (hasNoAuth ? METHOD_NO_AUTH : METHOD_NO_ACCEPTABLE)});
            out.flush();
            if (!hasNoAuth) return;

            // ── Request ──
            if (in.read() != SOCKS5_VERSION) return;
            int command = in.read();
            in.read(); // RSV
            int atyp = in.read();

            String host;
            byte[] rawAddr;
            if (atyp == ATYP_IPV4) {
                rawAddr = readExact(in, 4);
                host = InetAddress.getByAddress(rawAddr).getHostAddress();
            } else if (atyp == ATYP_DOMAIN) {
                int len = in.read();
                if (len < 0) return;
                rawAddr = readExact(in, len);
                host = new String(rawAddr, StandardCharsets.ISO_8859_1);
            } else if (atyp == ATYP_IPV6) {
                rawAddr = readExact(in, 16);
                host = InetAddress.getByAddress(rawAddr).getHostAddress();
            } else {
                sendError(out, 0x08);
                return;
            }

            int port = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);

            // ── Dispatch command ──
            if (command == CMD_CONNECT) {
                logInfo( "TCP CONNECT " + host + ":" + port + " from " + clientId);
                remote = handleConnect(client, out, host, port, counter);
            } else if (command == CMD_UDP_ASSOCIATE) {
                logInfo( "UDP ASSOCIATE request from " + clientId);
                udpRelay = handleUdpAssociate(client, out, counter, clientId);
            } else {
                Log.w(TAG, "Unsupported command 0x" + Integer.toHexString(command) + " from " + clientId);
                sendError(out, 0x07);
            }
        } catch (SocketTimeoutException e) {
            logDebug( "Timeout: " + clientId);
        } catch (Exception e) {
            Log.e(TAG, "Handler error: " + clientId + " — " + e.getMessage());
        } finally {
            BytePump.close(client);
            if (remote != null) BytePump.close(remote);
            if (udpRelay != null && !udpRelay.isClosed()) udpRelay.close();
            logDebug( "Connection closed: " + clientId);
        }
    }

    // ──────────────────────────────────────────────
    // TCP CONNECT
    // ──────────────────────────────────────────────

    private Socket handleConnect(Socket client, OutputStream out, String host, int port,
                                  TrafficCounter counter) throws Exception {
        Socket remote;
        try {
            remote = new Socket(host, port);
            remote.setTcpNoDelay(true);
            remote.setSoTimeout(TIMEOUT_MS);
        } catch (Exception e) {
            Log.w(TAG, "TCP CONNECT failed: " + host + ":" + port + " — " + e.getMessage());
            sendError(out, 0x05);
            return null;
        }

        out.write(new byte[]{SOCKS5_VERSION, 0x00, 0x00, ATYP_IPV4, 0, 0, 0, 0, 0, 0});
        out.flush();

        Thread up = new Thread(new BytePump(client.getInputStream(), remote.getOutputStream(),
                client, remote, counter, BytePump.Direction.UPLOAD));
        Thread down = new Thread(new BytePump(remote.getInputStream(), client.getOutputStream(),
                client, remote, counter, BytePump.Direction.DOWNLOAD));
        up.start();
        down.start();
        up.join();
        down.join();
        return null;
    }

    // ──────────────────────────────────────────────
    // UDP ASSOCIATE (RFC 1928 Section 7)
    // ──────────────────────────────────────────────

    private DatagramSocket handleUdpAssociate(Socket client, OutputStream out,
                                               TrafficCounter counter, String clientId) throws Exception {
        // ── TWO-SOCKET APPROACH ──
        // clientSocket: hotspot interface (menerima dari & kirim ke client)
        DatagramSocket clientSocket = new DatagramSocket();
        clientSocket.setSoTimeout(5000);
        int relayPort = clientSocket.getLocalPort();

        // internetSocket: cellular interface (kirim ke & terima dari internet)
        DatagramSocket internetSocket = new DatagramSocket();
        internetSocket.setSoTimeout(5000);
        bindToInternetNetwork(internetSocket);

        logInfo( "UDP ASSOCIATE two-socket: clientSock=0.0.0.0:" + relayPort
                + " internetSock=0.0.0.0:" + internetSocket.getLocalPort());

        // Dapatkan IP LAN/WiFi server yang bisa dijangkau client
        InetAddress serverAddr = getServerBindAddress(client);

        logInfo( "UDP ASSOCIATE relay=" + serverAddr.getHostAddress() + ":" + relayPort + " for " + clientId);

        // Reply: success + relay address/port
        byte[] addrBytes = serverAddr.getAddress();
        byte[] reply = new byte[4 + addrBytes.length + 2];
        reply[0] = SOCKS5_VERSION;
        reply[1] = 0x00; // success
        reply[2] = 0x00; // RSV
        reply[3] = (byte) (addrBytes.length == 4 ? ATYP_IPV4 : ATYP_IPV6);
        System.arraycopy(addrBytes, 0, reply, 4, addrBytes.length);
        reply[reply.length - 2] = (byte) ((relayPort >> 8) & 0xFF);
        reply[reply.length - 1] = (byte) (relayPort & 0xFF);
        out.write(reply);
        out.flush();

        // Shared state
        AtomicBoolean alive = new AtomicBoolean(true);
        InetSocketAddress[] clientUdp = new InetSocketAddress[1];
        ConcurrentHashMap<String, InetSocketAddress> destToClient = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, InetSocketAddress> portToClient = new ConcurrentHashMap<>();
        AtomicLong fwdCount = new AtomicLong(0);
        AtomicLong bwdCount = new AtomicLong(0);

        // ── Thread 1: TCP monitor — detect when client disconnects ──
        Thread tcpMonitor = new Thread(() -> {
            try {
                while (alive.get() && !client.isClosed()) {
                    try {
                        if (client.getInputStream().read() == -1) break;
                    } catch (SocketTimeoutException e) {
                        // normal
                    }
                }
            } catch (Exception ignored) {
            } finally {
                alive.set(false);
                clientSocket.close();
                internetSocket.close();
                logInfo( "UDP ASSOCIATE ended: fwd=" + fwdCount.get() + " bwd=" + bwdCount.get()
                        + " for " + clientId);
            }
        }, "udp-tcp-mon");
        tcpMonitor.setDaemon(true);
        tcpMonitor.start();

        // ── Thread 2: FORWARD — client → parse SOCKS5 frame → send to internet ──
        Thread fwdThread = new Thread(() -> {
            byte[] buf = new byte[UDP_BUF_SIZE];
            while (alive.get() && !clientSocket.isClosed()) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    clientSocket.receive(pkt);

                    // Learn client address
                    if (clientUdp[0] == null) {
                        clientUdp[0] = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                        logInfo( "UDP client learned: " + clientUdp[0]);
                    }

                    UdpFrame frame = parseUdpFrame(pkt.getData(), pkt.getLength());
                    if (frame == null || frame.frag != 0) continue;

                    InetAddress dstAddr;
                    try {
                        dstAddr = InetAddress.getByName(frame.host);
                    } catch (Exception e) {
                        Log.w(TAG, "UDP DNS resolve failed: " + frame.host);
                        continue;
                    }

                    // Skip IPv6
                    if (!(dstAddr instanceof Inet4Address)) {
                        try {
                            InetAddress[] all = InetAddress.getAllByName(frame.host);
                            Inet4Address ipv4 = null;
                            for (InetAddress a : all) {
                                if (a instanceof Inet4Address) { ipv4 = (Inet4Address) a; break; }
                            }
                            if (ipv4 != null) {
                                dstAddr = ipv4;
                            } else {
                                Log.w(TAG, "UDP SKIP IPv6: " + frame.host + " (no IPv4 fallback)");
                                continue;
                            }
                        } catch (Exception e2) {
                            Log.w(TAG, "UDP SKIP IPv6: " + frame.host);
                            continue;
                        }
                    }

                    DatagramPacket outPkt = new DatagramPacket(
                            frame.data, frame.data.length, dstAddr, frame.port);
                    try {
                        internetSocket.send(outPkt);
                    } catch (Exception sendErr) {
                        Log.e(TAG, "UDP FWD FAIL → " + frame.host + "(" + dstAddr.getHostAddress()
                                + "):" + frame.port + " — " + sendErr.getMessage());
                        continue;
                    }
                    counter.addUpload(frame.data.length);
                    long fwd = fwdCount.incrementAndGet();

                    String dstKey = dstAddr.getHostAddress() + ":" + frame.port;
                    destToClient.put(dstKey, clientUdp[0]);
                    portToClient.put(frame.port, clientUdp[0]);

                    if (fwd <= 5 || fwd % 50 == 0) {
                        logInfo( "UDP FWD #" + fwd + " → " + frame.host + "(" + dstAddr.getHostAddress()
                                + "):" + frame.port + " len=" + frame.data.length);
                    }
                } catch (SocketTimeoutException e) {
                    // normal
                } catch (Exception e) {
                    if (alive.get()) Log.w(TAG, "UDP FWD error: " + e.getMessage());
                }
            }
        }, "udp-fwd");
        fwdThread.setDaemon(true);
        fwdThread.start();

        // ── Thread 3: BACKWARD — internet response → wrap SOCKS5 frame → send to client ──
        Thread bwdThread = new Thread(() -> {
            byte[] buf = new byte[UDP_BUF_SIZE];
            while (alive.get() && !internetSocket.isClosed()) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    internetSocket.receive(pkt);

                    String srcKey = pkt.getAddress().getHostAddress() + ":" + pkt.getPort();

                    InetSocketAddress targetClient = destToClient.get(srcKey);
                    if (targetClient == null) {
                        targetClient = portToClient.get(pkt.getPort());
                    }

                    if (targetClient != null) {
                        byte[] framed = buildUdpFrame(pkt.getAddress(), pkt.getPort(),
                                pkt.getData(), pkt.getLength());
                        DatagramPacket outPkt = new DatagramPacket(framed, framed.length, targetClient);
                        clientSocket.send(outPkt);
                        counter.addDownload(pkt.getLength());
                        long bwd = bwdCount.incrementAndGet();

                        if (bwd <= 5 || bwd % 50 == 0) {
                            logInfo( "UDP BWD #" + bwd + " ← " + srcKey + " len=" + pkt.getLength());
                        }
                    } else {
                        Log.w(TAG, "UDP BWD dropped: no mapping for " + srcKey);
                    }
                } catch (SocketTimeoutException e) {
                    // normal
                } catch (Exception e) {
                    if (alive.get()) Log.w(TAG, "UDP BWD error: " + e.getMessage());
                }
            }
        }, "udp-bwd");
        bwdThread.setDaemon(true);
        bwdThread.start();

        // Block until ASSOCIATE ends (tcpMonitor will set alive=false and close sockets)
        try {
            tcpMonitor.join();
            fwdThread.join(3000);
            bwdThread.join(3000);
        } catch (InterruptedException ignored) {
        }

        // Cleanup (already closed by tcpMonitor, but just in case)
        if (!clientSocket.isClosed()) clientSocket.close();
        if (!internetSocket.isClosed()) internetSocket.close();

        return null; // sockets already closed
    }

    // ──────────────────────────────────────────────
    // SOCKS5 UDP Frame parsing & building
    // ──────────────────────────────────────────────

    private static class UdpFrame {
        int frag;
        String host;
        int port;
        byte[] data;
    }

    private static UdpFrame parseUdpFrame(byte[] buf, int len) {
        if (len < 10) return null;
        try {
            int frag = buf[2] & 0xFF;
            int atyp = buf[3] & 0xFF;
            int offset;
            String host;

            if (atyp == ATYP_IPV4) {
                if (len < 10) return null;
                host = InetAddress.getByAddress(new byte[]{buf[4], buf[5], buf[6], buf[7]}).getHostAddress();
                offset = 8;
            } else if (atyp == ATYP_DOMAIN) {
                int dlen = buf[4] & 0xFF;
                if (len < 7 + dlen) return null;
                host = new String(buf, 5, dlen, StandardCharsets.ISO_8859_1);
                offset = 5 + dlen;
            } else if (atyp == ATYP_IPV6) {
                if (len < 22) return null;
                byte[] addr = new byte[16];
                System.arraycopy(buf, 4, addr, 0, 16);
                host = InetAddress.getByAddress(addr).getHostAddress();
                offset = 20;
            } else {
                return null;
            }

            int port = ((buf[offset] & 0xFF) << 8) | (buf[offset + 1] & 0xFF);
            offset += 2;

            int dataLen = len - offset;
            byte[] data = new byte[dataLen];
            System.arraycopy(buf, offset, data, 0, dataLen);

            UdpFrame f = new UdpFrame();
            f.frag = frag;
            f.host = host;
            f.port = port;
            f.data = data;
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] buildUdpFrame(InetAddress addr, int port, byte[] data, int dataLen) {
        byte[] addrBytes = addr.getAddress();
        int atyp = (addrBytes.length == 4) ? ATYP_IPV4 : ATYP_IPV6;
        int headerLen = 4 + addrBytes.length + 2;
        byte[] frame = new byte[headerLen + dataLen];
        frame[0] = 0; frame[1] = 0;
        frame[2] = 0;
        frame[3] = (byte) atyp;
        System.arraycopy(addrBytes, 0, frame, 4, addrBytes.length);
        frame[headerLen - 2] = (byte) ((port >> 8) & 0xFF);
        frame[headerLen - 1] = (byte) (port & 0xFF);
        System.arraycopy(data, 0, frame, headerLen, dataLen);
        return frame;
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    /** Debug-level log — only emitted in debug builds. */
    private static void logDebug(String msg) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg);
    }

    /** Info-level log — only emitted in debug builds (verbose traffic info). */
    private static void logInfo(String msg) {
        if (BuildConfig.DEBUG) Log.i(TAG, msg);
    }

    /**
     * Dapatkan IP LAN/WiFi server yang bisa dijangkau oleh client.
     * Prioritas: IP dari interface yang sama dengan client, fallback ke IP non-loopback pertama.
     */
    private static InetAddress getServerBindAddress(Socket client) {
        InetAddress clientAddr = client.getInetAddress();
        InetAddress localAddr = client.getLocalAddress();

        // Jika localAddress bukan 0.0.0.0 dan bukan loopback, pakai langsung
        if (localAddr != null
                && !localAddr.isAnyLocalAddress()
                && !localAddr.isLoopbackAddress()
                && localAddr instanceof Inet4Address) {
            return localAddr;
        }

        // Cari IP dari interface yang sama subnet dengan client
        try {
            byte[] clientBytes = clientAddr.getAddress();
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        byte[] myBytes = addr.getAddress();
                        // Cek apakah sama subnet (/24)
                        if (myBytes[0] == clientBytes[0]
                                && myBytes[1] == clientBytes[1]
                                && myBytes[2] == clientBytes[2]) {
                            logDebug( "Found matching LAN IP: " + addr.getHostAddress()
                                    + " on " + iface.getDisplayName());
                            return addr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getServerBindAddress subnet scan failed: " + e.getMessage());
        }

        // Fallback: IP non-loopback pertama
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        logDebug( "Fallback IP: " + addr.getHostAddress()
                                + " on " + iface.getDisplayName());
                        return addr;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getServerBindAddress fallback failed: " + e.getMessage());
        }

        // Ultimate fallback
        Log.w(TAG, "No suitable IP found, using loopback");
        return InetAddress.getLoopbackAddress();
    }

    /**
     * Bind DatagramSocket ke active internet network (mobile data).
     * Tanpa ini, pada Android hotspot, UDP bisa di-route lewat interface yang salah → EHOSTUNREACH.
     */
    private void bindToInternetNetwork(DatagramSocket socket) {
        try {
            if (context == null) {
                Log.w(TAG, "bindToInternetNetwork: no context, skipping");
                return;
            }
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                Log.w(TAG, "bindToInternetNetwork: no ConnectivityManager");
                return;
            }

            // Cari network yang punya internet (bukan hotspot/wlan yang serve client)
            Network bestNetwork = null;
            for (Network network : cm.getAllNetworks()) {
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null) continue;

                boolean hasInternet = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
                boolean isValidated = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                boolean notSuspended = true; // skip suspended check (API 28+)

                // Skip hotspot tethering network
                boolean isTethered = false;
                try {
                    android.net.LinkProperties lp = cm.getLinkProperties(network);
                    if (lp != null) {
                        String iface = lp.getInterfaceName();
                        // Hotspot interface biasanya wlan0 (tapi bisa juga jadi tethering)
                        // Mobile data biasanya rmnet_data0 atau similar
                        logDebug( "Network " + network + ": iface=" + iface
                                + " internet=" + hasInternet + " validated=" + isValidated);
                    }
                } catch (Exception ignored) {}

                if (hasInternet && isValidated && notSuspended) {
                    bestNetwork = network;
                    // Prefer mobile data (TRANSPORT_CELLULAR) over WiFi if both available
                    if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        logInfo( "bindToInternetNetwork: using cellular network " + network);
                        break;
                    }
                }
            }

            if (bestNetwork != null) {
                bestNetwork.bindSocket(socket);
                logInfo( "bindToInternetNetwork: bound to network " + bestNetwork);
            } else {
                Log.w(TAG, "bindToInternetNetwork: no suitable internet network found");
            }
        } catch (Exception e) {
            Log.w(TAG, "bindToInternetNetwork failed: " + e.getMessage());
        }
    }

    private static void sendError(OutputStream out, int repCode) {
        try {
            out.write(new byte[]{SOCKS5_VERSION, (byte) repCode, 0x00, ATYP_IPV4, 0, 0, 0, 0, 0, 0});
            out.flush();
        } catch (Exception ignored) {
        }
    }

    private static byte[] readExact(InputStream in, int length) throws Exception {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read < 0) throw new IllegalStateException("Unexpected EOF");
            offset += read;
        }
        return data;
    }
}
