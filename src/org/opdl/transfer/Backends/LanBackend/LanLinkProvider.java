/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.opdl.transfer.Backends.LanBackend;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Network;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.opdl.transfer.Backends.BaseLink;
import org.opdl.transfer.Backends.BaseLinkProvider;
import org.opdl.transfer.DeviceHost;
import org.opdl.transfer.DeviceInfo;
import org.opdl.transfer.Helpers.DeviceHelper;
import org.opdl.transfer.Helpers.SecurityHelpers.SslHelper;
import org.opdl.transfer.Helpers.ThreadHelper;
import org.opdl.transfer.Helpers.TrustedNetworkHelper;
import org.opdl.transfer.NetworkPacket;
import org.opdl.transfer.UserInterface.CustomDevicesActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

import kotlin.text.Charsets;

/**
 * This LanLinkProvider creates {@link LanLink}s to other devices on the same
 * WiFi network. The first packet sent over a socket must be an
 * {@link DeviceInfo#toIdentityPacket()}.
 *
 * @see #identityPacketReceived(NetworkPacket, Socket,
 *      LanLink.ConnectionStarted, boolean)
 */
public class LanLinkProvider extends BaseLinkProvider {

    final static int UDP_PORT = 1716;
    final static int MIN_PORT = 1716;
    final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    final static int MAX_IDENTITY_PACKET_SIZE = 1024 * 512;
    final static int MAX_UDP_PACKET_SIZE = 1024 * 512;

    final static long MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE = 1000L;

    private final Context context;

    final HashMap<String, LanLink> visibleDevices = new HashMap<>(); // Links by device id

    final static int MAX_RATE_LIMIT_ENTRIES = 255;
    final ConcurrentHashMap<String, Long> lastConnectionTimeByDeviceId = new ConcurrentHashMap<>();
    final ConcurrentHashMap<InetAddress, Long> lastConnectionTimeByIp = new ConcurrentHashMap<>();

    private ServerSocket tcpServer;
    private DatagramSocket udpServer;

    private final MdnsDiscovery mdnsDiscovery;

    private long lastBroadcast = 0;
    private final static long delayBetweenBroadcasts = 200;

    private boolean listening = false;

    public void onConnectionLost(BaseLink link) {
        String deviceId = link.getDeviceId();
        visibleDevices.remove(deviceId);
        super.onConnectionLost(link);
    }

    Pair<NetworkPacket, Boolean> unserializeReceivedIdentityPacket(String message) {
        Log.d("OPDL/Discovery", "=== Parsing Identity Packet ===");
        Log.d("OPDL/Discovery",
                "Raw message (first 300 chars): " + message.substring(0, Math.min(message.length(), 300)));

        NetworkPacket identityPacket;
        try {
            identityPacket = NetworkPacket.unserialize(message);
            Log.d("OPDL/Discovery", "✅ JSON parsed successfully, type=" + identityPacket.getType());
        } catch (JSONException e) {
            Log.e("OPDL/Discovery", "❌ JSON parse FAILED: " + e.getMessage());
            Log.e("OPDL/Discovery", "   This usually means the packet format is wrong (missing 'body' wrapper?)");
            return null;
        }

        if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
            String pktDeviceId = identityPacket.getString("deviceId", "<missing>");
            String pktDeviceName = identityPacket.getString("deviceName", "<missing>");
            Log.e("OPDL/Discovery", "❌ Identity packet VALIDATION FAILED");
            Log.e("OPDL/Discovery", "   deviceId='" + pktDeviceId + "' (length=" + pktDeviceId.length() + ")");
            Log.e("OPDL/Discovery", "   deviceName='" + pktDeviceName + "'");
            Log.e("OPDL/Discovery", "   type='" + identityPacket.getType() + "'");
            Log.e("OPDL/Discovery", "   deviceId must match regex ^[a-zA-Z0-9_-]{32,38}$");
            return null;
        }

        final String deviceId = identityPacket.getString("deviceId");
        String myId = DeviceHelper.getDeviceId(context);
        if (deviceId.equals(myId)) {
            Log.d("OPDL/Discovery", "⏭️ Ignoring own broadcast (id=" + deviceId + ")");
            return null;
        }

        if (rateLimitByDeviceId(deviceId)) {
            Log.i("OPDL/Discovery",
                    "⏳ Rate-limited: discarding packet from device " + deviceId);
            return null;
        }

        boolean deviceTrusted = isDeviceTrusted(deviceId);
        Log.i("OPDL/Discovery", "✅ Valid identity received: id=" + deviceId
                + " name=" + identityPacket.getString("deviceName", "?")
                + " protocol=" + identityPacket.getInt("protocolVersion", -1)
                + " trusted=" + deviceTrusted);

        return new Pair<>(identityPacket, deviceTrusted);
    }

    // They received my UDP broadcast and are connecting to me. The first thing they
    // send should be their identity packet.
    @WorkerThread
    private void tcpPacketReceived(Socket socket) throws IOException, JSONException {

        InetAddress address = socket.getInetAddress();
        Log.d("OPDL/Discovery", "[TCP] 📥 Incoming TCP connection from " + address + ":" + socket.getPort());

        if (rateLimitByIp(address)) {
            Log.i("OPDL/Discovery", "[TCP] ⏳ Rate-limited TCP from " + address);
            return;
        }

        String message;
        try {
            message = readSingleLine(socket);
            Log.d("OPDL/Discovery", "[TCP] 📨 Read identity line (" + message.length() + " bytes)");
        } catch (Exception e) {
            Log.e("OPDL/Discovery", "[TCP] ❌ Failed to read from socket: " + e.getMessage(), e);
            return;
        }

        final Pair<NetworkPacket, Boolean> pair = unserializeReceivedIdentityPacket(message);
        if (pair == null) {
            Log.e("OPDL/Discovery", "[TCP] ❌ Identity packet from " + address + " was rejected (see logs above)");
            return;
        }
        final NetworkPacket identityPacket = pair.first;
        final boolean deviceTrusted = pair.second;

        Log.i("OPDL/Discovery",
                "[TCP] ✅ Valid identity from TCP: " + identityPacket.getString("deviceName")
                        + " (" + address + ")");

        identityPacketReceived(identityPacket, socket, LanLink.ConnectionStarted.Locally, deviceTrusted);
    }

    /**
     * Read a single line from a socket without consuming anything else from the
     * input.
     */
    private String readSingleLine(Socket socket) throws IOException {
        InputStream stream = socket.getInputStream();
        StringBuilder line = new StringBuilder(MAX_IDENTITY_PACKET_SIZE);
        int ch;
        while ((ch = stream.read()) != -1) {
            line.append((char) ch);
            if (ch == '\n') {
                return line.toString();
            }
            if (line.length() >= MAX_IDENTITY_PACKET_SIZE) {
                break;
            }
        }
        throw new IOException("Couldn't read a line from the socket");
    }

    boolean rateLimitByIp(InetAddress address) {
        long now = System.currentTimeMillis();
        Long last = lastConnectionTimeByIp.get(address);
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            return true;
        }
        lastConnectionTimeByIp.put(address, now);
        if (lastConnectionTimeByIp.size() > MAX_RATE_LIMIT_ENTRIES) {
            lastConnectionTimeByIp.entrySet()
                    .removeIf(e -> e.getValue() + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE < now);
        }
        return false;
    }

    boolean rateLimitByDeviceId(String deviceId) {
        long now = System.currentTimeMillis();
        Long last = lastConnectionTimeByDeviceId.get(deviceId);
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            return true;
        }
        lastConnectionTimeByDeviceId.put(deviceId, now);
        if (lastConnectionTimeByDeviceId.size() > MAX_RATE_LIMIT_ENTRIES) {
            lastConnectionTimeByDeviceId.entrySet()
                    .removeIf(e -> e.getValue() + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE < now);
        }
        return false;
    }

    // I've received their broadcast and should connect to their TCP socket and send
    // my identity.
    @WorkerThread
    private void udpPacketReceived(DatagramPacket packet) throws JSONException, IOException {

        final InetAddress address = packet.getAddress();
        Log.d("OPDL/Discovery", "[UDP] 📥 Received UDP packet from " + address + " (" + packet.getLength() + " bytes)");

        if (rateLimitByIp(address)) {
            Log.i("OPDL/Discovery", "[UDP] ⏳ Rate-limited UDP from " + address);
            return;
        }

        String message = new String(packet.getData(), 0, packet.getLength(), Charsets.UTF_8);
        Log.d("OPDL/Discovery", "[UDP] 📨 Raw message: " + message.substring(0, Math.min(message.length(), 200)));

        final Pair<NetworkPacket, Boolean> pair = unserializeReceivedIdentityPacket(message);
        if (pair == null) {
            Log.e("OPDL/Discovery", "[UDP] ❌ Identity packet from " + address + " was rejected (see logs above)");
            return;
        }
        final NetworkPacket identityPacket = pair.first;
        final boolean deviceTrusted = pair.second;
        final String deviceId = identityPacket.getString("deviceId");

        if (visibleDevices.containsKey(deviceId)) {
            Log.d("OPDL/Discovery",
                    "[UDP] ⏭️ Already have a link for " + deviceId + ", skipping redundant TCP connection");
            return;
        }

        Log.i("OPDL/Discovery",
                "[UDP] ✅ Valid broadcast identity from " + identityPacket.getString("deviceName") + " (" + address
                        + ")");

        int tcpPort = identityPacket.getInt("tcpPort", MIN_PORT);
        Log.d("OPDL/Discovery", "[UDP] TCP port from identity: " + tcpPort);
        if (tcpPort < MIN_PORT || tcpPort > MAX_PORT) {
            Log.e("OPDL/Discovery", "[UDP] ❌ TCP port " + tcpPort + " outside range " + MIN_PORT + "-" + MAX_PORT);
            return;
        }

        Log.d("OPDL/Discovery", "[UDP] 🔌 Connecting TCP to " + address + ":" + tcpPort);
        SocketFactory socketFactory = SocketFactory.getDefault();
        Socket socket = socketFactory.createSocket(address, tcpPort);
        configureSocket(socket);

        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
        NetworkPacket myIdentity = myDeviceInfo.toIdentityPacket();

        OutputStream out = socket.getOutputStream();
        out.write(myIdentity.serialize().getBytes());
        out.flush();
        Log.d("OPDL/Discovery", "[UDP] 📤 Sent our identity to " + address + ":" + tcpPort + " over TCP");

        identityPacketReceived(identityPacket, socket, LanLink.ConnectionStarted.Remotely, deviceTrusted);
    }

    private void configureSocket(Socket socket) {
        try {
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            socket.setSendBufferSize(256 * 1024);
            socket.setReceiveBufferSize(256 * 1024);
        } catch (SocketException e) {
            Log.e("LanLink", "Exception", e);
        }
    }

    private boolean isDeviceTrusted(String deviceId) {
        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        return preferences.getBoolean(deviceId, false);
    }

    /**
     * Called when a new 'identity' packet is received. Those are passed here by
     * {@link #tcpPacketReceived(Socket)} and
     * {@link #udpPacketReceived(DatagramPacket)}.
     * Should be called on a new thread since it blocks until the handshake is
     * completed.
     *
     * @param identityPacket    identity of a remote device
     * @param socket            a new Socket, which should be used to receive
     *                          packets from the remote device
     * @param connectionStarted which side started this connection
     * @param deviceTrusted     whether the packet comes from a trusted device
     */
    @WorkerThread
    private void identityPacketReceived(final NetworkPacket identityPacket, final Socket socket,
            final LanLink.ConnectionStarted connectionStarted, final boolean deviceTrusted)
            throws IOException, JSONException {
        final String deviceId = identityPacket.getString("deviceId");

        Log.d("OPDL/Discovery", "[HANDSHAKE] identityPacketReceived for " + deviceId);

        // ⚡ OPDL FAST-PATH BYPASS
        // If the device supports OPDL fast-path, we skip the complex SSL certificate
        // handshake
        // to ensure immediate discovery and connectivity.
        if (identityPacket.getBoolean("opdlFastPathV1", false)) {
            Log.i("OPDL/Discovery", "⚡ OPDL Fast Path device detected (" + deviceId + "), bypassing SSL handshake");

            // In Fast Path bypass, we must still ensure the identity exchange is completed
            // unencrypted.
            // If we are the TCP receiver, we haven't sent our identity yet.
            // If we are the TCP initiator, we sent ours but haven't read theirs from the
            // TCP stream yet.
            if (connectionStarted == LanLink.ConnectionStarted.Locally) {
                // We are the TCP Server: read identity -> send ours back
                DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
                NetworkPacket myIdentity = myDeviceInfo.toIdentityPacket();
                OutputStream out = socket.getOutputStream();
                out.write(myIdentity.serialize().getBytes(Charsets.UTF_8));
                out.flush();
                Log.d("OPDL/Discovery", "[HANDSHAKE] Sent our identity back to " + deviceId);
            } else {
                // We are the TCP Client: send identity -> read theirs back
                // Note: udpPacketReceived already sent ours. We just need to consume theirs
                // from TCP.
                try {
                    String line = readSingleLine(socket);
                    NetworkPacket tcpIdentity = NetworkPacket.unserialize(line);
                    Log.d("OPDL/Discovery",
                            "[HANDSHAKE] Consumed peer identity from TCP: " + tcpIdentity.getString("deviceId"));
                    // Use the TCP identity as the authoritative one if they differ (unlikely)
                    if (tcpIdentity.has("deviceId") && !tcpIdentity.getString("deviceId").equals(deviceId)) {
                        Log.w("OPDL/Discovery", "Device ID mismatch between UDP and TCP identity!");
                    }
                } catch (Exception e) {
                    Log.w("OPDL/Discovery", "Failed to read identity response from " + deviceId + " over TCP", e);
                }
            }

            // Use our own certificate as a placeholder since we won't verify it for
            // fast-path
            DeviceInfo deviceInfo = DeviceInfo.fromIdentityPacketAndCert(identityPacket,
                    SslHelper.INSTANCE.getCertificate());
            addOrUpdateLink(socket, deviceInfo);
            return;
        }

        int protocolVersion = identityPacket.getInt("protocolVersion");
        if (deviceTrusted && isProtocolDowngrade(deviceId, protocolVersion)) {
            Log.w("KDE/LanLinkProvider",
                    "Refusing to connect to a device using an older protocol version:" + protocolVersion);
            return;
        }

        if (deviceTrusted && !SslHelper.isCertificateStored(context, deviceId)) {
            Log.e("KDE/LanLinkProvider", "Device trusted but no cert stored. This should not happen.");
            return;
        }

        String deviceName = identityPacket.getString("deviceName", "unknown");
        Log.i("KDE/LanLinkProvider", "Starting SSL handshake with " + deviceName + " trusted:" + deviceTrusted);

        // If I'm the TCP server I will be the SSL client and vice-versa.
        final boolean clientMode = (connectionStarted == LanLink.ConnectionStarted.Locally);
        final SSLSocket sslSocket = SslHelper.convertToSslSocket(context, socket, deviceId, deviceTrusted, clientMode);
        sslSocket.addHandshakeCompletedListener(event -> {
            // Start a new thread because some Android versions don't allow calling
            // sslSocket.getOutputStream() from the callback
            ThreadHelper.execute(() -> {
                String mode = clientMode ? "client" : "server";
                try {
                    NetworkPacket secureIdentityPacket;
                    if (protocolVersion >= 8) {
                        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
                        NetworkPacket myIdentity = myDeviceInfo.toIdentityPacket();
                        OutputStream writer = sslSocket.getOutputStream();
                        writer.write(myIdentity.serialize().getBytes(Charsets.UTF_8));
                        writer.flush();
                        String line = readSingleLine(sslSocket);
                        // Do not trust the identity packet we received unencrypted
                        secureIdentityPacket = NetworkPacket.unserialize(line);
                        if (!DeviceInfo.isValidIdentityPacket(secureIdentityPacket)) {
                            throw new JSONException("Invalid identity packet");
                        }
                        int newProtocolVersion = secureIdentityPacket.getInt("protocolVersion");
                        if (newProtocolVersion != protocolVersion) {
                            Log.e("KDE/LanLinkProvider", "Protocol version changed half-way through the handshake: "
                                    + protocolVersion + " ->" + newProtocolVersion);
                            return;
                        }
                        String newDeviceId = secureIdentityPacket.getString("deviceId");
                        if (!newDeviceId.equals(deviceId)) {
                            Log.e("KDE/LanLinkProvider", "Device ID changed half-way through the handshake: "
                                    + protocolVersion + " ->" + newProtocolVersion);
                            return;
                        }
                    } else {
                        secureIdentityPacket = identityPacket;
                    }
                    Certificate certificate = event.getPeerCertificates()[0];
                    DeviceInfo deviceInfo = DeviceInfo.fromIdentityPacketAndCert(secureIdentityPacket, certificate);
                    Log.i("KDE/LanLinkProvider", "Handshake as " + mode + " successful with " + deviceName
                            + " secured with " + event.getCipherSuite());
                    addOrUpdateLink(sslSocket, deviceInfo);
                } catch (JSONException e) {
                    Log.e("KDE/LanLinkProvider", "Remote device doesn't correctly implement protocol version 8", e);
                } catch (IOException e) {
                    Log.e("KDE/LanLinkProvider", "Handshake as " + mode + " failed with " + deviceName, e);
                }
            });
        });

        // Handshake is blocking, so do it on another thread and free this thread to
        // keep receiving new connection
        Log.d("LanLinkProvider", "Starting handshake");
        sslSocket.startHandshake();
        Log.d("LanLinkProvider", "Handshake done");
    }

    private boolean isProtocolDowngrade(String deviceId, int protocolVersion) {
        SharedPreferences devicePrefs = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
        int lastKnownProtocolVersion = devicePrefs.getInt("protocolVersion", 0);
        return lastKnownProtocolVersion > protocolVersion;
    }

    /**
     * Add or update a link in the {@link #visibleDevices} map.
     *
     * @param socket     a new Socket, which should be used to send and receive
     *                   packets from the remote device
     * @param deviceInfo remote device info
     * @throws IOException if an exception is thrown by
     *                     {@link LanLink#reset(SSLSocket, DeviceInfo)}
     */
    @WorkerThread
    private void addOrUpdateLink(Socket socket, DeviceInfo deviceInfo) throws IOException {
        LanLink link = visibleDevices.get(deviceInfo.id);
        if (link != null) {
            if (!link.getDeviceInfo().certificate.equals(deviceInfo.certificate)) {
                Log.e("LanLinkProvider",
                        "LanLink was asked to replace a socket but the certificate doesn't match, aborting");
                return;
            }
            // Update existing link
            Log.d("KDE/LanLinkProvider", "Reusing same link for device " + deviceInfo.id);
            link.reset(socket, deviceInfo);
            onDeviceInfoUpdated(deviceInfo);
        } else {
            // Create a new link
            Log.d("KDE/LanLinkProvider", "Creating a new link for device " + deviceInfo.id);
            link = new LanLink(context, deviceInfo, this, socket);
            visibleDevices.put(deviceInfo.id, link);
            onConnectionReceived(link);
        }
    }

    public LanLinkProvider(Context context) {
        this.context = context;
        this.mdnsDiscovery = new MdnsDiscovery(context, this);
    }

    private void setupUdpListener() {
        try {
            udpServer = new DatagramSocket(null);
            udpServer.setReuseAddress(true);
            udpServer.setBroadcast(true);
            Log.d("OPDL/Discovery", "[SETUP] UDP socket created");
        } catch (SocketException e) {
            Log.e("OPDL/Discovery", "[SETUP] ❌ Error creating UDP socket", e);
            throw new RuntimeException(e);
        }
        try {
            udpServer.bind(new InetSocketAddress(UDP_PORT));
            Log.i("OPDL/Discovery", "[SETUP] ✅ UDP server bound to port " + UDP_PORT);
        } catch (SocketException e) {
            Log.e("OPDL/Discovery", "[SETUP] ❌ Failed to bind UDP to port " + UDP_PORT + ": " + e.getMessage(), e);
        }
        ThreadHelper.execute(() -> {
            Log.i("OPDL/Discovery", "[UDP] ✅ UDP listener thread started, waiting for packets on port " + UDP_PORT);
            while (listening) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[MAX_UDP_PACKET_SIZE], MAX_UDP_PACKET_SIZE);
                    udpServer.receive(packet);
                    ThreadHelper.execute(() -> {
                        try {
                            udpPacketReceived(packet);
                        } catch (JSONException | IOException e) {
                            Log.e("OPDL/Discovery", "[UDP] ❌ Exception handling UDP packet", e);
                        }
                    });
                } catch (IOException e) {
                    Log.e("OPDL/Discovery", "[UDP] ⚠️ UdpReceive exception, triggering network change", e);
                    onNetworkChange(null);
                }
            }
            Log.w("OPDL/Discovery", "[UDP] 🛑 UDP listener stopped");
        });
    }

    private void setupTcpListener() {
        try {
            tcpServer = openServerSocketOnFreePort(MIN_PORT);
        } catch (IOException e) {
            Log.e("LanLinkProvider", "Error creating tcp server", e);
            throw new RuntimeException(e);
        }
        ThreadHelper.execute(() -> {
            while (listening) {
                try {
                    Socket socket = tcpServer.accept();
                    configureSocket(socket);
                    ThreadHelper.execute(() -> {
                        try {
                            tcpPacketReceived(socket);
                        } catch (IOException | JSONException e) {
                            Log.e("LanLinkProvider", "Exception receiving incoming TCP connection", e);
                        }
                    });
                } catch (Exception e) {
                    Log.e("LanLinkProvider", "TcpReceive exception", e);
                }
            }
            Log.w("TcpListener", "Stopping TCP listener");
        });

    }

    static ServerSocket openServerSocketOnFreePort(int minPort) throws IOException {
        int tcpPort = minPort;
        while (tcpPort <= MAX_PORT) {
            try {
                ServerSocket candidateServer = new ServerSocket(tcpPort);
                Log.i("KDE/LanLink", "Using port " + tcpPort);
                return candidateServer;
            } catch (IOException e) {
                tcpPort++;
                if (tcpPort == MAX_PORT) {
                    Log.e("KDE/LanLink", "No ports available");
                    throw e; // Propagate exception
                }
            }
        }
        throw new RuntimeException("This should not be reachable");
    }

    private void broadcastUdpIdentityPacket(@Nullable Network network) {
        ThreadHelper.execute(() -> {
            List<DeviceHost> hostList = CustomDevicesActivity
                    .getCustomDeviceList(context);

            hostList.add(DeviceHost.BROADCAST);

            ArrayList<InetAddress> ipList = new ArrayList<>();
            for (DeviceHost host : hostList) {
                try {
                    ipList.add(InetAddress.getByName(host.toString()));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }

            if (ipList.isEmpty()) {
                Log.w("OPDL/Discovery", "[BROADCAST] ⚠️ No broadcast addresses found!");
                return;
            }

            Log.d("OPDL/Discovery", "[BROADCAST] 📡 Broadcasting to " + ipList.size() + " addresses");
            sendUdpIdentityPacket(ipList, network);
        });
    }

    @WorkerThread
    public void sendUdpIdentityPacket(List<InetAddress> ipList, @Nullable Network network) {
        if (tcpServer == null || !tcpServer.isBound()) {
            Log.i("OPDL/Discovery", "[BROADCAST] ⏳ TCP socket not ready, skipping broadcast");
            return;
        }

        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
        NetworkPacket identity = myDeviceInfo.toIdentityPacket();
        identity.set("tcpPort", tcpServer.getLocalPort());

        byte[] bytes;
        try {
            bytes = identity.serialize().getBytes(Charsets.UTF_8);
            Log.d("OPDL/Discovery", "[BROADCAST] Identity packet serialized (" + bytes.length + " bytes)");
            Log.d("OPDL/Discovery", "[BROADCAST] Our deviceId=" + myDeviceInfo.id
                    + " name=" + myDeviceInfo.name + " tcpPort=" + tcpServer.getLocalPort());
        } catch (JSONException e) {
            Log.e("OPDL/Discovery", "[BROADCAST] ❌ Failed to serialize identity packet", e);
            return;
        }

        DatagramSocket socket;
        try {
            socket = new DatagramSocket();
            if (network != null) {
                try {
                    network.bindSocket(socket);
                } catch (IOException e) {
                    Log.w("OPDL/Discovery", "[BROADCAST] ⚠️ Couldn't bind socket to network");
                    e.printStackTrace();
                }
            }
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            Log.e("OPDL/Discovery", "[BROADCAST] ❌ Failed to create DatagramSocket", e);
            return;
        }

        for (InetAddress ip : ipList) {
            try {
                socket.send(new DatagramPacket(bytes, bytes.length, ip, MIN_PORT));
                Log.d("OPDL/Discovery", "[BROADCAST] 📤 Sent identity to " + ip + ":" + MIN_PORT);
            } catch (IOException e) {
                Log.e("OPDL/Discovery",
                        "[BROADCAST] ❌ Send failed to " + ip + ": " + e.getMessage(), e);
            }
        }

        socket.close();
    }

    @Override
    public void onStart() {
        Log.i("OPDL/Discovery", "===================================================");
        Log.i("OPDL/Discovery", "  LanLinkProvider onStart()");
        Log.i("OPDL/Discovery", "  DeviceId: " + DeviceHelper.getDeviceId(context));
        Log.i("OPDL/Discovery", "  DeviceName: " + DeviceHelper.getDeviceName(context));
        Log.i("OPDL/Discovery", "  UDP Port: " + UDP_PORT);
        Log.i("OPDL/Discovery", "===================================================");
        if (!listening) {

            listening = true;

            setupUdpListener();
            setupTcpListener();

            mdnsDiscovery.startDiscovering();
            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                mdnsDiscovery.startAnnouncing();
            }

            broadcastUdpIdentityPacket(null);
        } else {
            Log.d("OPDL/Discovery", "Already listening, skipping onStart");
        }
    }

    @Override
    public void onNetworkChange(@Nullable Network network) {
        if (System.currentTimeMillis() < lastBroadcast + delayBetweenBroadcasts) {
            Log.i("LanLinkProvider", "onNetworkChange: relax cowboy");
            return;
        }
        lastBroadcast = System.currentTimeMillis();

        broadcastUdpIdentityPacket(network);
        mdnsDiscovery.stopDiscovering();
        mdnsDiscovery.startDiscovering();
    }

    @Override
    public void onStop() {
        // Log.i("KDE/LanLinkProvider", "onStop");
        listening = false;
        mdnsDiscovery.stopAnnouncing();
        mdnsDiscovery.stopDiscovering();
        try {
            tcpServer.close();
        } catch (Exception e) {
            Log.e("LanLink", "Exception", e);
        }
        try {
            udpServer.close();
        } catch (Exception e) {
            Log.e("LanLink", "Exception", e);
        }
    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }

    @Override
    public int getPriority() {
        return 20;
    }

}
