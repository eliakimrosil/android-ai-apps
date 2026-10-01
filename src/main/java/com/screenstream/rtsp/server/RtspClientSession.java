package com.screenstream.rtsp.server;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtspClientSession implements Runnable {

    private static final String TAG = "RtspClientSession";

    public interface SessionCallback {
        void onSessionPlay(RtspClientSession session);
        void onSessionClose(RtspClientSession session);
        String getSdp(String hostIp);
    }

    private static class QueuedPacket {
        final byte[] data;
        final boolean isKeyframe;
        final boolean isVideo;

        QueuedPacket(byte[] data, boolean isKeyframe, boolean isVideo) {
            this.data = data;
            this.isKeyframe = isKeyframe;
            this.isVideo = isVideo;
        }
    }

    private final Socket socket;
    private final SessionCallback callback;
    private final String sessionId;
    private final InetAddress clientAddress;

    private OutputStream outputStream;
    private volatile boolean isRunning = true;
    private volatile boolean isPlaying = false;

    private final BlockingQueue<QueuedPacket> outgoingQueue = new LinkedBlockingQueue<>(80);
    private Thread senderThread;

    // Video Transport
    private boolean isVideoInterleaved = true;
    private int videoRtpChannel = 0;
    private int videoRtcpChannel = 1;
    private int clientVideoRtpPort = 0;
    private DatagramSocket videoUdpSocket = null;

    // Audio Transport
    private boolean isAudioInterleaved = true;
    private int audioRtpChannel = 2;
    private int audioRtcpChannel = 3;
    private int clientAudioRtpPort = 0;
    private DatagramSocket audioUdpSocket = null;

    private final Object writeLock = new Object();

    public RtspClientSession(Socket socket, SessionCallback callback) {
        this.socket = socket;
        this.callback = callback;
        this.clientAddress = socket.getInetAddress();
        this.sessionId = String.format("%08d", new Random().nextInt(100000000));
        this.senderThread = new Thread(this::senderLoop, "RtspSender-" + sessionId);
        this.senderThread.start();
    }

    public boolean isPlaying() {
        return isPlaying && isRunning;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getClientIp() {
        return clientAddress.getHostAddress();
    }

    @Override
    public void run() {
        try {
            outputStream = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (isRunning && !socket.isClosed()) {
                String requestLine = reader.readLine();
                if (requestLine == null) break;
                if (requestLine.isEmpty()) continue;

                // Parse RTSP request headers
                Map<String, String> headers = new HashMap<>();
                String headerLine;
                while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                    int colonIndex = headerLine.indexOf(':');
                    if (colonIndex > 0) {
                        String key = headerLine.substring(0, colonIndex).trim().toLowerCase();
                        String val = headerLine.substring(colonIndex + 1).trim();
                        headers.put(key, val);
                    }
                }

                handleRequest(requestLine, headers);
            }
        } catch (Exception e) {
            Log.d(TAG, "Session ended for " + getClientIp() + ": " + e.getMessage());
        } finally {
            close();
        }
    }

    private void handleRequest(String requestLine, Map<String, String> headers) throws IOException {
        String[] parts = requestLine.split(" ");
        if (parts.length < 3) return;

        String method = parts[0].toUpperCase();
        String uri = parts[1];
        String cseq = headers.containsKey("cseq") ? headers.get("cseq") : "1";

        switch (method) {
            case "OPTIONS":
                sendResponse("RTSP/1.0 200 OK\r\n" +
                        "CSeq: " + cseq + "\r\n" +
                        "Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, OPTIONS\r\n" +
                        "\r\n");
                break;

            case "DESCRIBE":
                String sdp = callback.getSdp(socket.getLocalAddress().getHostAddress());
                byte[] sdpBytes = sdp.getBytes("UTF-8");
                sendResponse("RTSP/1.0 200 OK\r\n" +
                        "CSeq: " + cseq + "\r\n" +
                        "Content-Type: application/sdp\r\n" +
                        "Content-Base: " + uri + "/\r\n" +
                        "Content-Length: " + sdpBytes.length + "\r\n" +
                        "\r\n" + sdp);
                break;

            case "SETUP":
                handleSetup(uri, headers, cseq);
                break;

            case "PLAY":
                isPlaying = true;
                sendResponse("RTSP/1.0 200 OK\r\n" +
                        "CSeq: " + cseq + "\r\n" +
                        "Session: " + sessionId + "\r\n" +
                        "Range: npt=0.000-\r\n" +
                        "RTP-Info: url=" + uri + ";seq=0;rtptime=0\r\n" +
                        "\r\n");
                callback.onSessionPlay(this);
                break;

            case "PAUSE":
                isPlaying = false;
                sendResponse("RTSP/1.0 200 OK\r\n" +
                        "CSeq: " + cseq + "\r\n" +
                        "Session: " + sessionId + "\r\n" +
                        "\r\n");
                break;

            case "TEARDOWN":
                sendResponse("RTSP/1.0 200 OK\r\n" +
                        "CSeq: " + cseq + "\r\n" +
                        "Session: " + sessionId + "\r\n" +
                        "\r\n");
                close();
                break;

            case "GET_PARAMETER":
            case "SET_PARAMETER":
                // Keep-alive or ping
                sendResponse("RTSP/1.0 200 OK\r\n" +
                        "CSeq: " + cseq + "\r\n" +
                        "Session: " + sessionId + "\r\n" +
                        "\r\n");
                break;

            default:
                sendResponse("RTSP/1.0 501 Not Implemented\r\n" +
                        "CSeq: " + cseq + "\r\n" +
                        "\r\n");
                break;
        }
    }

    private void handleSetup(String uri, Map<String, String> headers, String cseq) throws IOException {
        String transport = headers.get("transport");
        if (transport == null) transport = "";

        boolean isAudio = uri.contains("trackID=1");
        String transportResponse;

        if (transport.contains("TCP") || transport.contains("interleaved")) {
            // TCP Interleaved mode
            Pattern pattern = Pattern.compile("interleaved=(\\d+)-(\\d+)");
            Matcher matcher = pattern.matcher(transport);
            int rtpChan = isAudio ? 2 : 0;
            int rtcpChan = isAudio ? 3 : 1;
            if (matcher.find()) {
                try {
                    rtpChan = Integer.parseInt(matcher.group(1));
                    rtcpChan = Integer.parseInt(matcher.group(2));
                } catch (Exception ignored) {
                }
            }

            if (isAudio) {
                isAudioInterleaved = true;
                audioRtpChannel = rtpChan;
                audioRtcpChannel = rtcpChan;
            } else {
                isVideoInterleaved = true;
                videoRtpChannel = rtpChan;
                videoRtcpChannel = rtcpChan;
            }

            transportResponse = "RTP/AVP/TCP;unicast;interleaved=" + rtpChan + "-" + rtcpChan;
        } else {
            // UDP unicast mode
            Pattern pattern = Pattern.compile("client_port=(\\d+)-(\\d+)");
            Matcher matcher = pattern.matcher(transport);
            int clientRtp = 5004;
            int clientRtcp = 5005;
            if (matcher.find()) {
                try {
                    clientRtp = Integer.parseInt(matcher.group(1));
                    clientRtcp = Integer.parseInt(matcher.group(2));
                } catch (Exception ignored) {
                }
            }

            DatagramSocket udpSocket = new DatagramSocket();
            int serverPort = udpSocket.getLocalPort();

            if (isAudio) {
                isAudioInterleaved = false;
                clientAudioRtpPort = clientRtp;
                audioUdpSocket = udpSocket;
            } else {
                isVideoInterleaved = false;
                clientVideoRtpPort = clientRtp;
                videoUdpSocket = udpSocket;
            }

            transportResponse = "RTP/AVP;unicast;client_port=" + clientRtp + "-" + clientRtcp +
                    ";server_port=" + serverPort + "-" + (serverPort + 1);
        }

        sendResponse("RTSP/1.0 200 OK\r\n" +
                "CSeq: " + cseq + "\r\n" +
                "Session: " + sessionId + ";timeout=60\r\n" +
                "Transport: " + transportResponse + "\r\n" +
                "\r\n");
    }

    public void sendVideoPacket(byte[] rtpPacket) {
        sendVideoPacket(rtpPacket, false);
    }

    public void sendVideoPacket(byte[] rtpPacket, boolean isKeyframe) {
        if (!isPlaying || !isRunning || rtpPacket == null) return;
        // Congestion control: if queue is > 70% full, drop non-keyframe packets
        if (outgoingQueue.size() > 56 && !isKeyframe) {
            return;
        }
        outgoingQueue.offer(new QueuedPacket(rtpPacket, isKeyframe, true));
    }

    public void sendAudioPacket(byte[] rtpPacket) {
        if (!isPlaying || !isRunning || rtpPacket == null) return;
        outgoingQueue.offer(new QueuedPacket(rtpPacket, false, false));
    }

    private void senderLoop() {
        while (isRunning) {
            try {
                QueuedPacket qp = outgoingQueue.poll(500, TimeUnit.MILLISECONDS);
                if (qp == null) continue;
                if (!isPlaying || !isRunning) continue;

                if (qp.isVideo) {
                    if (isVideoInterleaved) {
                        byte[] frame = RtpPacket.createInterleavedFrame(videoRtpChannel, qp.data);
                        synchronized (writeLock) {
                            if (outputStream != null) {
                                outputStream.write(frame);
                                if (outgoingQueue.isEmpty()) {
                                    outputStream.flush();
                                }
                            }
                        }
                    } else if (videoUdpSocket != null && clientVideoRtpPort > 0) {
                        DatagramPacket dp = new DatagramPacket(qp.data, qp.data.length, clientAddress, clientVideoRtpPort);
                        videoUdpSocket.send(dp);
                    }
                } else {
                    if (isAudioInterleaved) {
                        byte[] frame = RtpPacket.createInterleavedFrame(audioRtpChannel, qp.data);
                        synchronized (writeLock) {
                            if (outputStream != null) {
                                outputStream.write(frame);
                                if (outgoingQueue.isEmpty()) {
                                    outputStream.flush();
                                }
                            }
                        }
                    } else if (audioUdpSocket != null && clientAudioRtpPort > 0) {
                        DatagramPacket dp = new DatagramPacket(qp.data, qp.data.length, clientAddress, clientAudioRtpPort);
                        audioUdpSocket.send(dp);
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                close();
                break;
            }
        }
    }

    private void sendResponse(String response) throws IOException {
        synchronized (writeLock) {
            if (outputStream != null) {
                outputStream.write(response.getBytes("UTF-8"));
                outputStream.flush();
            }
        }
    }

    public void close() {
        if (!isRunning) return;
        isRunning = false;
        isPlaying = false;

        if (senderThread != null) {
            senderThread.interrupt();
            senderThread = null;
        }
        outgoingQueue.clear();

        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {}

        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}

        if (videoUdpSocket != null && !videoUdpSocket.isClosed()) {
            videoUdpSocket.close();
        }
        if (audioUdpSocket != null && !audioUdpSocket.isClosed()) {
            audioUdpSocket.close();
        }

        callback.onSessionClose(this);
    }
}
