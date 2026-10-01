package com.screenstream.rtsp.server;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RtspServer {

    private static final String TAG = "RtspServer";

    public interface ServerListener {
        void onClientCountChanged(int count);
        void onRequestKeyframe();
        void onStatsUpdate(int activeClients, float fps, float bitrateMbps);
    }

    private final int port;
    private final boolean hasAudio;
    private final ServerListener listener;
    private final H264Packetizer videoPacketizer;
    private final AacPacketizer audioPacketizer;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService sessionThreadPool;
    private final List<RtspClientSession> sessions = new CopyOnWriteArrayList<>();

    private volatile boolean isRunning = false;

    // Statistics tracking
    private long totalBytesSent = 0;
    private long lastStatsCheckTime = 0;
    private long bytesSinceLastCheck = 0;
    private int framesSinceLastCheck = 0;
    private float currentFps = 0.0f;
    private float currentBitrateMbps = 0.0f;

    public RtspServer(int port, boolean hasAudio, ServerListener listener) {
        this.port = port;
        this.hasAudio = hasAudio;
        this.listener = listener;

        Random random = new Random();
        this.videoPacketizer = new H264Packetizer(random.nextInt());
        this.audioPacketizer = new AacPacketizer(random.nextInt());
    }

    public H264Packetizer getVideoPacketizer() {
        return videoPacketizer;
    }

    public AacPacketizer getAudioPacketizer() {
        return audioPacketizer;
    }

    public synchronized void start() throws IOException {
        if (isRunning) return;

        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        isRunning = true;
        sessionThreadPool = Executors.newCachedThreadPool();

        lastStatsCheckTime = System.currentTimeMillis();

        acceptThread = new Thread(() -> {
            Log.i(TAG, "RTSP Server started on port " + port);
            while (isRunning && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    RtspClientSession session = new RtspClientSession(clientSocket, new RtspClientSession.SessionCallback() {
                        @Override
                        public void onSessionPlay(RtspClientSession s) {
                            notifyClientsChanged();
                            if (listener != null) {
                                listener.onRequestKeyframe();
                            }
                        }

                        @Override
                        public void onSessionClose(RtspClientSession s) {
                            sessions.remove(s);
                            notifyClientsChanged();
                        }

                        @Override
                        public String getSdp(String hostIp) {
                            return SdpGenerator.generateSdp(
                                    hostIp,
                                    "ScreenStream RTSP",
                                    videoPacketizer.getProfileLevelId(),
                                    videoPacketizer.getSpropParameterSets(),
                                    hasAudio,
                                    audioPacketizer.getConfigHex()
                            );
                        }
                    });

                    sessions.add(session);
                    sessionThreadPool.execute(session);
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "Accept error: " + e.getMessage());
                    }
                }
            }
        }, "RtspAcceptThread");
        acceptThread.start();
    }

    public void dispatchVideoPacket(byte[] packet, boolean isKeyframe) {
        if (!isRunning || sessions.isEmpty()) return;

        int activePlaying = 0;
        for (RtspClientSession session : sessions) {
            if (session.isPlaying()) {
                session.sendVideoPacket(packet, isKeyframe);
                activePlaying++;
            }
        }

        if (activePlaying > 0) {
            long packetBytes = (long) packet.length * activePlaying;
            bytesSinceLastCheck += packetBytes;
            totalBytesSent += packetBytes;
        }

        framesSinceLastCheck++;
        updateStats();
    }

    public void dispatchAudioPacket(byte[] packet) {
        if (!isRunning || !hasAudio || sessions.isEmpty()) return;

        int activePlaying = 0;
        for (RtspClientSession session : sessions) {
            if (session.isPlaying()) {
                session.sendAudioPacket(packet);
                activePlaying++;
            }
        }

        if (activePlaying > 0) {
            long packetBytes = (long) packet.length * activePlaying;
            bytesSinceLastCheck += packetBytes;
            totalBytesSent += packetBytes;
        }
    }

    private void updateStats() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastStatsCheckTime;
        if (elapsed >= 1000) {
            currentFps = (framesSinceLastCheck * 1000.0f) / elapsed;
            currentBitrateMbps = (bytesSinceLastCheck * 8.0f) / (elapsed * 1000.0f);

            framesSinceLastCheck = 0;
            bytesSinceLastCheck = 0;
            lastStatsCheckTime = now;

            if (listener != null) {
                listener.onStatsUpdate(getPlayingClientCount(), currentFps, currentBitrateMbps);
            }
        }
    }

    public int getPlayingClientCount() {
        int count = 0;
        for (RtspClientSession session : sessions) {
            if (session.isPlaying()) count++;
        }
        return count;
    }

    private void notifyClientsChanged() {
        if (listener != null) {
            listener.onClientCountChanged(getPlayingClientCount());
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        for (RtspClientSession session : sessions) {
            session.close();
        }
        sessions.clear();

        if (sessionThreadPool != null) {
            sessionThreadPool.shutdownNow();
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        notifyClientsChanged();
    }
}
