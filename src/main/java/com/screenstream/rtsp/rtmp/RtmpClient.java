package com.screenstream.rtsp.rtmp;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class RtmpClient {

    private static final String TAG = "RtmpClient";
    public static final int CHUNK_SIZE = 4096;

    public interface RtmpListener {
        void onConnected();
        void onDisconnected();
        void onError(Exception e);
    }

    private static class PendingRtmpMessage {
        final int csid;
        final long timestamp;
        final int msgType;
        final int msgStreamId;
        final byte[] payload;
        final boolean isKeyframe;

        PendingRtmpMessage(int csid, long timestamp, int msgType, int msgStreamId, byte[] payload, boolean isKeyframe) {
            this.csid = csid;
            this.timestamp = timestamp;
            this.msgType = msgType;
            this.msgStreamId = msgStreamId;
            this.payload = payload;
            this.isKeyframe = isKeyframe;
        }
    }

    public static class RtmpPacket {
        public int csid;
        public long timestamp;
        public int messageType;
        public int messageStreamId;
        public byte[] data;
    }

    private static class ChunkStream {
        long timestamp = 0;
        long timestampDelta = 0;
        int messageLength = 0;
        int messageType = 0;
        int messageStreamId = 0;
        byte[] buffer = null;
        int bufferOffset = 0;
    }

    private final String url;
    private final String streamKey;
    private final int width;
    private final int height;
    private final int fps;
    private final int bitrate;
    private final boolean hasAudio;
    private final RtmpListener listener;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final Object sendLock = new Object();

    private final Map<Integer, ChunkStream> chunkStreams = new LinkedHashMap<>();
    private int serverChunkSize = 128;

    private volatile boolean isConnected = false;
    private int streamId = 1;

    private byte[] sps;
    private byte[] pps;
    private byte[] cachedAsc;
    private boolean videoHeaderSent = false;
    private boolean audioHeaderSent = false;
    private boolean firstKeyframeSent = false;

    private long baseStreamPtsUs = -1;
    private long baseVideoPtsUs = -1;
    private long baseAudioPtsUs = -1;
    private long lastVideoTimestamp = 0;
    private long lastAudioTimestamp = 0;
    private long videoFramesSent = 0;
    private long audioFramesSent = 0;
    private Thread readerThread;
    private final BlockingQueue<PendingRtmpMessage> outgoingRtmpQueue = new LinkedBlockingQueue<>(120);
    private Thread rtmpSenderThread;

    public RtmpClient(String url, String streamKey, int width, int height,
                      int fps, int bitrate, boolean hasAudio, RtmpListener listener) {
        this.url = url;
        this.streamKey = streamKey;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrate = bitrate;
        this.hasAudio = hasAudio;
        this.listener = listener;
    }

    public synchronized void setSpsAndPps(byte[] sps, byte[] pps) {
        this.sps = stripStartCode(sps);
        this.pps = stripStartCode(pps);
        Log.i(TAG, "setSpsAndPps: SPS len=" + (this.sps != null ? this.sps.length : 0)
                + ", PPS len=" + (this.pps != null ? this.pps.length : 0));
        if (isConnected && !videoHeaderSent && this.sps != null && this.pps != null) {
            try {
                sendVideoHeader();
            } catch (Exception e) {
                Log.e(TAG, "Failed to send video header: " + e.getMessage());
            }
        }
    }

    public void connect() throws Exception {
        URI uri = new URI(url);
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "rtmp";
        boolean isSsl = scheme.equals("rtmps");
        String host = uri.getHost();
        int port = uri.getPort();
        if (port <= 0) {
            port = isSsl ? 443 : 1935;
        }

        String path = uri.getPath();
        if (path == null) path = "";
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        String app = path;
        String tcUrl = (isSsl ? "rtmps://" : "rtmp://") + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + "/" + app;

        Log.i(TAG, "Connecting to " + (isSsl ? "RTMPS" : "RTMP") + " host=" + host + ", port=" + port + ", app=" + app + ", tcUrl=" + tcUrl);

        if (isSsl) {
            SSLSocket sslSocket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            sslSocket.connect(new InetSocketAddress(host, port), 10000);
            sslSocket.startHandshake();
            socket = sslSocket;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 10000);
        }
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(15000);

        inputStream = socket.getInputStream();
        outputStream = new BufferedOutputStream(socket.getOutputStream(), 65536);

        // 1. Handshake
        doHandshake();

        // 2. Set Client Chunk Size
        sendSetChunkSize(CHUNK_SIZE);

        // 3. Connect Command
        sendConnect(app, tcUrl);

        DataInputStream dis = new DataInputStream(inputStream);

        // Wait for connect response (_result 1.0)
        boolean connectSuccess = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            RtmpPacket pkt = readPacket(dis);
            if (pkt == null) break;
            if (pkt.messageType == 20) {
                ByteArrayInputStream bais = new ByteArrayInputStream(pkt.data);
                DataInputStream cmdDis = new DataInputStream(bais);
                String cmd = (String) AmfUtil.readValue(cmdDis);
                if ("_result".equals(cmd)) {
                    double transId = ((Number) AmfUtil.readValue(cmdDis)).doubleValue();
                    if (transId == 1.0) {
                        Log.i(TAG, "Connect succeeded (_result for transId 1.0 received)");
                        connectSuccess = true;
                        break;
                    }
                } else if ("_error".equals(cmd)) {
                    throw new IOException("Server returned _error for connect command");
                }
            }
        }
        if (!connectSuccess) {
            throw new IOException("Timeout or connection failed waiting for connect response from server");
        }

        // 4. FMLE Stream Setup Commands (Essential for YouTube/Facebook Live)
        sendReleaseStream(streamKey);
        sendFCPublish(streamKey);

        // 5. Create Stream Command (transId 4.0)
        sendCreateStream();

        // Wait for createStream response (_result 4.0)
        boolean createStreamSuccess = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            RtmpPacket pkt = readPacket(dis);
            if (pkt == null) break;
            if (pkt.messageType == 20) {
                ByteArrayInputStream bais = new ByteArrayInputStream(pkt.data);
                DataInputStream cmdDis = new DataInputStream(bais);
                String cmd = (String) AmfUtil.readValue(cmdDis);
                if ("_result".equals(cmd)) {
                    double transId = ((Number) AmfUtil.readValue(cmdDis)).doubleValue();
                    if (transId == 4.0) {
                        AmfUtil.readValue(cmdDis); // null command object
                        Object sidObj = AmfUtil.readValue(cmdDis);
                        if (sidObj instanceof Number) {
                            streamId = ((Number) sidObj).intValue();
                        }
                        Log.i(TAG, "createStream succeeded: streamId=" + streamId);
                        createStreamSuccess = true;
                        break;
                    }
                } else if ("_error".equals(cmd)) {
                    throw new IOException("Server rejected createStream command");
                }
            }
        }
        if (!createStreamSuccess) {
            throw new IOException("Timeout or error waiting for createStream response from server");
        }

        // 6. Publish Command (transId 5.0 on streamId)
        sendPublish(streamKey);

        // Wait for onStatus (NetStream.Publish.Start)
        boolean publishSuccess = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            RtmpPacket pkt = readPacket(dis);
            if (pkt == null) break;
            if (pkt.messageType == 20) {
                ByteArrayInputStream bais = new ByteArrayInputStream(pkt.data);
                DataInputStream cmdDis = new DataInputStream(bais);
                String cmd = (String) AmfUtil.readValue(cmdDis);
                if ("onStatus".equals(cmd)) {
                    AmfUtil.readValue(cmdDis); // transId 0.0
                    AmfUtil.readValue(cmdDis); // null
                    Object infoObj = AmfUtil.readValue(cmdDis);
                    if (infoObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> info = (Map<String, Object>) infoObj;
                        String code = (String) info.get("code");
                        String level = (String) info.get("level");
                        Log.i(TAG, "Publish status: code=" + code + ", level=" + level);
                        if ("error".equals(level) || "NetStream.Publish.BadName".equals(code) || "NetStream.Publish.Denied".equals(code)) {
                            throw new IOException("Server rejected stream key! Check your YouTube stream key (" + code + ")");
                        } else if ("NetStream.Publish.Start".equals(code)) {
                            publishSuccess = true;
                            break;
                        }
                    }
                }
            }
        }
        if (!publishSuccess) {
            throw new IOException("Timeout waiting for NetStream.Publish.Start from server");
        }

        isConnected = true;

        // 7. Metadata (@setDataFrame onMetaData)
        sendMetadata();

        // 8. Video Header (AVC Sequence Header)
        if (sps != null && pps != null) {
            sendVideoHeader();
        }

        // 9. Audio Header (AAC Sequence Header)
        if (hasAudio) {
            byte[] asc = (cachedAsc != null) ? cachedAsc : new byte[] { 0x11, (byte) 0x90 };
            sendAudioHeader(asc);
        }

        // Start background sender and reader threads
        rtmpSenderThread = new Thread(this::senderLoop, "RtmpSenderThread");
        rtmpSenderThread.start();

        readerThread = new Thread(this::readLoop, "RtmpReaderThread");
        readerThread.start();

        Log.i(TAG, ">>> SUCCESS: RTMP connected and publishing to " + host + "!");
        if (listener != null) {
            listener.onConnected();
        }
    }

    private void doHandshake() throws IOException {
        byte[] c0c1 = new byte[1537];
        c0c1[0] = 0x03;
        new Random().nextBytes(c0c1);
        c0c1[0] = 0x03;
        for (int i = 1; i <= 8; i++) c0c1[i] = 0;

        outputStream.write(c0c1);
        outputStream.flush();

        DataInputStream dis = new DataInputStream(inputStream);
        byte s0 = dis.readByte();
        if (s0 != 0x03) {
            throw new IOException("Invalid RTMP handshake version: " + s0);
        }
        byte[] s1 = new byte[1536];
        dis.readFully(s1);
        byte[] s2 = new byte[1536];
        dis.readFully(s2);

        outputStream.write(s1);
        outputStream.flush();
    }

    private void sendSetChunkSize(int chunkSize) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(chunkSize & 0x7FFFFFFF);

        sendChunk(2, 0, 1, 0, baos.toByteArray());
    }

    private void sendConnect(String app, String tcUrl) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        AmfUtil.writeString(dos, "connect");
        AmfUtil.writeNumber(dos, 1.0);

        Map<String, Object> commandObj = new LinkedHashMap<>();
        commandObj.put("app", app);
        commandObj.put("flashVer", "FMLE/3.0 (compatible; FMSc/1.0)");
        commandObj.put("tcUrl", tcUrl);
        commandObj.put("fpad", false);
        commandObj.put("capabilities", 15.0);
        commandObj.put("audioCodecs", 3191.0);
        commandObj.put("videoCodecs", 252.0);
        commandObj.put("videoFunction", 1.0);
        AmfUtil.writeObject(dos, commandObj);

        sendChunk(3, 0, 20, 0, baos.toByteArray());
    }

    private void sendReleaseStream(String streamKey) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        AmfUtil.writeString(dos, "releaseStream");
        AmfUtil.writeNumber(dos, 2.0);
        AmfUtil.writeNull(dos);
        AmfUtil.writeString(dos, streamKey);

        sendChunk(3, 0, 20, 0, baos.toByteArray());
    }

    private void sendFCPublish(String streamKey) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        AmfUtil.writeString(dos, "FCPublish");
        AmfUtil.writeNumber(dos, 3.0);
        AmfUtil.writeNull(dos);
        AmfUtil.writeString(dos, streamKey);

        sendChunk(3, 0, 20, 0, baos.toByteArray());
    }

    private void sendCreateStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        AmfUtil.writeString(dos, "createStream");
        AmfUtil.writeNumber(dos, 4.0);
        AmfUtil.writeNull(dos);

        sendChunk(3, 0, 20, 0, baos.toByteArray());
    }

    private void sendPublish(String streamKey) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        AmfUtil.writeString(dos, "publish");
        AmfUtil.writeNumber(dos, 5.0);
        AmfUtil.writeNull(dos);
        AmfUtil.writeString(dos, streamKey);
        AmfUtil.writeString(dos, "live");

        sendChunk(8, 0, 20, streamId, baos.toByteArray());
    }

    private void sendMetadata() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        AmfUtil.writeString(dos, "@setDataFrame");
        AmfUtil.writeString(dos, "onMetaData");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("duration", 0.0);
        metadata.put("width", (double) width);
        metadata.put("height", (double) height);
        metadata.put("videodatarate", (double) (bitrate / 1000));
        metadata.put("framerate", (double) fps);
        metadata.put("videocodecid", 7.0); // AVC
        if (hasAudio) {
            metadata.put("audiodatarate", 192.0);
            metadata.put("audiosamplerate", 48000.0);
            metadata.put("audiosamplesize", 16.0);
            metadata.put("stereo", true);
            metadata.put("audiocodecid", 10.0); // AAC
        }
        metadata.put("encoder", "ScreenStream");

        AmfUtil.writeEcmaArray(dos, metadata);
        sendChunk(4, 0, 18, streamId, baos.toByteArray());
        Log.i(TAG, "Sent metadata: " + width + "x" + height + "@" + fps + "fps");
    }

    private RtmpPacket readPacket(DataInputStream in) throws IOException {
        while (socket != null && !socket.isClosed()) {
            int b0 = in.readUnsignedByte();
            int fmt = (b0 >> 6) & 0x03;
            int csid = b0 & 0x3F;
            if (csid == 0) {
                csid = 64 + in.readUnsignedByte();
            } else if (csid == 1) {
                int b1 = in.readUnsignedByte();
                int b2 = in.readUnsignedByte();
                csid = 64 + b1 + (b2 << 8);
            }

            ChunkStream cs = chunkStreams.get(csid);
            if (cs == null) {
                cs = new ChunkStream();
                chunkStreams.put(csid, cs);
            }

            if (fmt == 0) {
                long ts = readUint24(in);
                int len = readUint24(in);
                int type = in.readUnsignedByte();
                int sid = readInt32LE(in);
                if (ts == 0xFFFFFFL) {
                    ts = in.readInt() & 0xFFFFFFFFL;
                }
                cs.timestamp = ts;
                cs.timestampDelta = 0;
                cs.messageLength = len;
                cs.messageType = type;
                cs.messageStreamId = sid;
                cs.buffer = new byte[len];
                cs.bufferOffset = 0;
            } else if (fmt == 1) {
                long delta = readUint24(in);
                int len = readUint24(in);
                int type = in.readUnsignedByte();
                if (delta == 0xFFFFFFL) {
                    delta = in.readInt() & 0xFFFFFFFFL;
                }
                cs.timestampDelta = delta;
                cs.timestamp += delta;
                cs.messageLength = len;
                cs.messageType = type;
                cs.buffer = new byte[len];
                cs.bufferOffset = 0;
            } else if (fmt == 2) {
                long delta = readUint24(in);
                if (delta == 0xFFFFFFL) {
                    delta = in.readInt() & 0xFFFFFFFFL;
                }
                cs.timestampDelta = delta;
                cs.timestamp += delta;
                cs.buffer = new byte[cs.messageLength];
                cs.bufferOffset = 0;
            } else { // fmt == 3
                if (cs.buffer == null) {
                    cs.buffer = new byte[cs.messageLength];
                    cs.bufferOffset = 0;
                }
            }

            int toRead = Math.min(cs.messageLength - cs.bufferOffset, serverChunkSize);
            in.readFully(cs.buffer, cs.bufferOffset, toRead);
            cs.bufferOffset += toRead;

            if (cs.bufferOffset == cs.messageLength) {
                RtmpPacket pkt = new RtmpPacket();
                pkt.csid = csid;
                pkt.timestamp = cs.timestamp;
                pkt.messageType = cs.messageType;
                pkt.messageStreamId = cs.messageStreamId;
                pkt.data = cs.buffer;

                cs.buffer = null;
                cs.bufferOffset = 0;

                // Handle protocol control messages
                if (pkt.messageType == 1) { // Set Chunk Size
                    if (pkt.data.length >= 4) {
                        int newChunkSize = ByteBuffer.wrap(pkt.data).getInt() & 0x7FFFFFFF;
                        if (newChunkSize > 0) {
                            serverChunkSize = newChunkSize;
                            Log.d(TAG, "Server chunk size updated to: " + serverChunkSize);
                        }
                    }
                } else if (pkt.messageType == 4) { // User Control
                    if (pkt.data.length >= 6) {
                        int eventType = ((pkt.data[0] & 0xFF) << 8) | (pkt.data[1] & 0xFF);
                        if (eventType == 6) { // Ping Request
                            byte[] pingResp = new byte[6];
                            pingResp[0] = 0x00;
                            pingResp[1] = 0x07; // Ping Response
                            System.arraycopy(pkt.data, 2, pingResp, 2, 4);
                            sendChunk(2, 0, 4, 0, pingResp);
                            Log.d(TAG, "Responded to server Ping Request");
                        }
                    }
                }

                return pkt;
            }
        }
        return null;
    }

    private static int readUint24(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        return (b0 << 16) | (b1 << 8) | b2;
    }

    private static int readInt32LE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private void readLoop() {
        DataInputStream dis = new DataInputStream(inputStream);
        try {
            socket.setSoTimeout(0); // Infinite read timeout
            while (isConnected && socket != null && !socket.isClosed()) {
                RtmpPacket pkt = readPacket(dis);
                if (pkt == null) break;

                if (pkt.messageType == 20) {
                    try {
                        ByteArrayInputStream bais = new ByteArrayInputStream(pkt.data);
                        DataInputStream cmdDis = new DataInputStream(bais);
                        String cmd = (String) AmfUtil.readValue(cmdDis);
                        if ("onStatus".equals(cmd)) {
                            AmfUtil.readValue(cmdDis); // transId
                            AmfUtil.readValue(cmdDis); // null
                            Object infoObj = AmfUtil.readValue(cmdDis);
                            if (infoObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> info = (Map<String, Object>) infoObj;
                                String code = (String) info.get("code");
                                if ("NetStream.Play.UnpublishNotify".equals(code) ||
                                    "NetStream.Publish.BadName".equals(code) ||
                                    "NetStream.Publish.Denied".equals(code)) {
                                    Log.w(TAG, "Server stream status: " + code);
                                    if (listener != null) {
                                        listener.onError(new IOException("Stream stopped by server: " + code));
                                    }
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            if (isConnected) {
                Log.w(TAG, "Reader thread ended: " + e.getMessage());
            }
        } finally {
            boolean wasConnected = isConnected;
            disconnect();
            if (wasConnected && listener != null) {
                listener.onDisconnected();
            }
        }
    }

    public synchronized void sendVideoHeader() throws IOException {
        if (sps == null || pps == null || !isConnected || videoHeaderSent) return;

        byte[] cleanSps = stripStartCode(sps);
        byte[] cleanPps = stripStartCode(pps);

        if (cleanSps == null || cleanSps.length < 4 || cleanPps == null || cleanPps.length < 1) {
            Log.e(TAG, "Cannot send video header: invalid SPS/PPS");
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(0x17); // Keyframe, AVC
        dos.writeByte(0x00); // Sequence header
        dos.writeByte(0x00); // Composition time
        dos.writeByte(0x00);
        dos.writeByte(0x00);

        // AVCDecoderConfigurationRecord (ISO/IEC 14496-15)
        dos.writeByte(0x01); // configurationVersion = 1
        dos.writeByte(cleanSps[1]); // AVCProfileIndication
        dos.writeByte(cleanSps[2]); // profile_compatibility
        dos.writeByte(cleanSps[3]); // AVCLevelIndication
        dos.writeByte(0xFF); // 111111b + lengthSizeMinusOne = 3 (4-byte NALU length prefix)

        dos.writeByte(0xE1); // 111b + numOfSequenceParameterSets = 1
        dos.writeShort(cleanSps.length);
        dos.write(cleanSps);

        dos.writeByte(0x01); // numOfPictureParameterSets = 1
        dos.writeShort(cleanPps.length);
        dos.write(cleanPps);

        // ISO/IEC 14496-15 Section 5.2.4.1.1: Extension for High Profile (100) and above
        int profileIdc = cleanSps[1] & 0xFF;
        if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 144) {
            dos.writeByte(0xFD); // 111111b + chroma_format_idc (1 = 4:2:0)
            dos.writeByte(0xF8); // 11111b + bit_depth_luma_minus8 (0 = 8-bit)
            dos.writeByte(0xF8); // 11111b + bit_depth_chroma_minus8 (0 = 8-bit)
            dos.writeByte(0x00); // numOfSequenceParameterSetExt = 0
        }

        sendChunk(6, 0, 9, streamId, baos.toByteArray());
        videoHeaderSent = true;
        Log.i(TAG, ">>> SUCCESS: Sent AVC Sequence Header (profile=" + profileIdc + ", clean SPS: " + cleanSps.length + " bytes, clean PPS: " + cleanPps.length + " bytes)");
    }

    public synchronized void sendAudioHeader(byte[] asc) throws IOException {
        if (audioHeaderSent) return;
        if (asc != null && asc.length >= 2) {
            this.cachedAsc = asc;
        }
        if (!isConnected) return; // Will be sent once connected

        if (asc == null || asc.length < 2) {
            asc = (this.cachedAsc != null) ? this.cachedAsc : new byte[] { 0x11, (byte) 0x90 }; // Default AAC-LC 48kHz Stereo
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(0xAF); // AAC, Stereo
        dos.writeByte(0x00); // AAC sequence header
        dos.write(asc);

        sendChunk(7, 0, 8, streamId, baos.toByteArray());
        audioHeaderSent = true;
        Log.i(TAG, "Sent AAC Sequence Header to RTMP server");
    }

    public void sendVideoFrame(ByteBuffer buffer, int offset, int size, long ptsUs, boolean isKeyframeHint) {
        if (!isConnected) return;

        try {
            byte[] data = new byte[size];
            int pos = buffer.position();
            buffer.position(offset);
            buffer.get(data);
            buffer.position(pos);

            List<int[]> nals = findNalUnits(data);
            if (nals.isEmpty()) return;

            // Extract SPS/PPS if present in this frame
            for (int[] nal : nals) {
                int nStart = nal[0];
                int nLen = nal[1];
                int nalType = data[nStart] & 0x1F;
                if (nalType == 7 && (!videoHeaderSent || this.sps == null)) {
                    byte[] s = new byte[nLen];
                    System.arraycopy(data, nStart, s, 0, nLen);
                    this.sps = stripStartCode(s);
                } else if (nalType == 8 && (!videoHeaderSent || this.pps == null)) {
                    byte[] p = new byte[nLen];
                    System.arraycopy(data, nStart, p, 0, nLen);
                    this.pps = stripStartCode(p);
                }
            }

            if (!videoHeaderSent && this.sps != null && this.pps != null) {
                sendVideoHeader();
            }

            if (!videoHeaderSent) return;

            // Check if this frame contains an IDR keyframe NAL (type 5)
            boolean hasIdr = false;
            for (int[] nal : nals) {
                int nStart = nal[0];
                int nalType = data[nStart] & 0x1F;
                if (nalType == 5) {
                    hasIdr = true;
                    break;
                }
            }

            boolean isKeyframe = hasIdr;

            // YouTube requires stream to begin on an IDR keyframe
            if (!firstKeyframeSent) {
                if (!isKeyframe) return;
                firstKeyframeSent = true;
                baseStreamPtsUs = ptsUs;
                Log.i(TAG, ">>> Starting video transmission with first IDR Keyframe! Base PTS: " + baseStreamPtsUs);
            }

            if (baseStreamPtsUs < 0) {
                baseStreamPtsUs = ptsUs;
            }

            long timestamp = Math.max(0, (ptsUs - baseStreamPtsUs) / 1000L);
            if (timestamp < lastVideoTimestamp) {
                timestamp = lastVideoTimestamp;
            }
            lastVideoTimestamp = timestamp;

            ByteArrayOutputStream baos = new ByteArrayOutputStream(size + 16);
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(isKeyframe ? 0x17 : 0x27);
            dos.writeByte(0x01); // AVC NALU
            dos.writeByte(0x00); // composition time offset
            dos.writeByte(0x00);
            dos.writeByte(0x00);

            boolean hasSlices = false;
            for (int[] nal : nals) {
                int nStart = nal[0];
                int nLen = nal[1];
                int nalType = data[nStart] & 0x1F;
                if (nalType == 7 || nalType == 8 || nalType == 9) continue; // Skip parameter sets and AUD

                dos.writeInt(nLen);
                dos.write(data, nStart, nLen);
                hasSlices = true;
            }

            if (!hasSlices) return;

            // Congestion control: if outgoing queue is congested (> 80 items), drop non-keyframes
            if (outgoingRtmpQueue.size() > 80 && !isKeyframe) {
                return;
            }

            byte[] payload = baos.toByteArray();
            outgoingRtmpQueue.offer(new PendingRtmpMessage(6, timestamp, 9, streamId, payload, isKeyframe));
            videoFramesSent++;

            if (videoFramesSent == 1 || isKeyframe || videoFramesSent % 120 == 0) {
                Log.i(TAG, "Queued video frame #" + videoFramesSent + " (ts=" + timestamp + "ms, " + (isKeyframe ? "KEYFRAME/IDR" : "P-frame") + ", qSize=" + outgoingRtmpQueue.size() + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error queuing video frame: " + e.getMessage());
            boolean wasConnected = isConnected;
            disconnect();
            if (wasConnected && listener != null) {
                listener.onError(e);
            }
        }
    }

    public void sendAudioFrame(ByteBuffer buffer, int offset, int size, long ptsUs) {
        if (!isConnected || !firstKeyframeSent || baseStreamPtsUs < 0) return;

        if (!audioHeaderSent && hasAudio) {
            try {
                byte[] asc = (cachedAsc != null) ? cachedAsc : new byte[] { 0x11, (byte) 0x90 };
                sendAudioHeader(asc);
            } catch (Exception e) {
                Log.w(TAG, "Failed to send AAC header before audio frame: " + e.getMessage());
            }
        }
        if (!audioHeaderSent) return;

        long timestamp = Math.max(0, (ptsUs - baseStreamPtsUs) / 1000L);
        if (timestamp < lastAudioTimestamp) {
            timestamp = lastAudioTimestamp;
        }
        lastAudioTimestamp = timestamp;

        try {
            int aacOffset = offset;
            int aacSize = size;
            if (size >= 7) {
                int b0 = buffer.get(offset) & 0xFF;
                int b1 = buffer.get(offset + 1) & 0xFF;
                if (b0 == 0xFF && (b1 & 0xF0) == 0xF0) {
                    boolean hasProt = (b1 & 0x01) == 0;
                    int hdr = hasProt ? 9 : 7;
                    aacOffset += hdr;
                    aacSize -= hdr;
                }
            }
            if (aacSize <= 0) return;

            byte[] payload = new byte[2 + aacSize];
            payload[0] = (byte) 0xAF; // AAC Stereo
            payload[1] = 0x01;         // AAC raw data

            int pos = buffer.position();
            buffer.position(aacOffset);
            buffer.get(payload, 2, aacSize);
            buffer.position(pos);

            outgoingRtmpQueue.offer(new PendingRtmpMessage(7, timestamp, 8, streamId, payload, false));
            audioFramesSent++;
            if (audioFramesSent == 1 || audioFramesSent % 200 == 0) {
                Log.i(TAG, "Queued audio frame #" + audioFramesSent + " (ts=" + timestamp + "ms, " + payload.length + " bytes, qSize=" + outgoingRtmpQueue.size() + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error queuing audio frame: " + e.getMessage());
        }
    }

    private void senderLoop() {
        while (isConnected) {
            try {
                PendingRtmpMessage msg = outgoingRtmpQueue.poll(500, TimeUnit.MILLISECONDS);
                if (msg == null) continue;
                if (!isConnected) break;

                sendChunk(msg.csid, msg.timestamp, msg.msgType, msg.msgStreamId, msg.payload);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in rtmp sender loop: " + e.getMessage());
                boolean wasConnected = isConnected;
                disconnect();
                if (wasConnected && listener != null) {
                    listener.onError(e);
                }
                break;
            }
        }
    }

    private void sendChunk(int csid, long timestamp, int msgType, int msgStreamId, byte[] payload) throws IOException {
        synchronized (sendLock) {
            if (outputStream == null || socket == null || !socket.isConnected()) return;

            int payloadLen = payload.length;
            int offset = 0;

            boolean hasExtTs = timestamp >= 0xFFFFFFL;
            long ts = hasExtTs ? 0xFFFFFFL : timestamp;

            // First Chunk Header: Format 0 (12 bytes)
            byte[] header = new byte[12];
            header[0] = (byte) (0x00 | (csid & 0x3F)); // fmt 0

            header[1] = (byte) ((ts >> 16) & 0xFF);
            header[2] = (byte) ((ts >> 8) & 0xFF);
            header[3] = (byte) (ts & 0xFF);

            header[4] = (byte) ((payloadLen >> 16) & 0xFF);
            header[5] = (byte) ((payloadLen >> 8) & 0xFF);
            header[6] = (byte) (payloadLen & 0xFF);

            header[7] = (byte) (msgType & 0xFF);

            header[8] = (byte) (msgStreamId & 0xFF);
            header[9] = (byte) ((msgStreamId >> 8) & 0xFF);
            header[10] = (byte) ((msgStreamId >> 16) & 0xFF);
            header[11] = (byte) ((msgStreamId >> 24) & 0xFF);

            outputStream.write(header);

            if (hasExtTs) {
                byte[] extTs = new byte[4];
                extTs[0] = (byte) ((timestamp >> 24) & 0xFF);
                extTs[1] = (byte) ((timestamp >> 16) & 0xFF);
                extTs[2] = (byte) ((timestamp >> 8) & 0xFF);
                extTs[3] = (byte) (timestamp & 0xFF);
                outputStream.write(extTs);
            }

            int firstChunkSize = Math.min(payloadLen, CHUNK_SIZE);
            outputStream.write(payload, 0, firstChunkSize);
            offset += firstChunkSize;

            // Subsequent chunks: Format 3 (1 byte chunk header)
            byte fmt3Header = (byte) (0xC0 | (csid & 0x3F));
            while (offset < payloadLen) {
                int chunkSize = Math.min(payloadLen - offset, CHUNK_SIZE);
                outputStream.write(fmt3Header);
                if (hasExtTs) {
                    byte[] extTs = new byte[4];
                    extTs[0] = (byte) ((timestamp >> 24) & 0xFF);
                    extTs[1] = (byte) ((timestamp >> 16) & 0xFF);
                    extTs[2] = (byte) ((timestamp >> 8) & 0xFF);
                    extTs[3] = (byte) (timestamp & 0xFF);
                    outputStream.write(extTs);
                }
                outputStream.write(payload, offset, chunkSize);
                offset += chunkSize;
            }

            outputStream.flush();
        }
    }

    private List<int[]> findNalUnits(byte[] data) {
        List<Integer> startPositions = new ArrayList<>();
        List<Integer> headerLengths = new ArrayList<>();
        int len = data.length;

        for (int i = 0; i < len - 3; i++) {
            if (data[i] == 0 && data[i + 1] == 0) {
                if (data[i + 2] == 1) {
                    startPositions.add(i + 3);
                    headerLengths.add(3);
                    i += 2;
                } else if (data[i + 2] == 0 && i < len - 4 && data[i + 3] == 1) {
                    startPositions.add(i + 4);
                    headerLengths.add(4);
                    i += 3;
                }
            }
        }

        List<int[]> nalUnits = new ArrayList<>();
        int count = startPositions.size();
        for (int i = 0; i < count; i++) {
            int start = startPositions.get(i);
            int end = (i < count - 1) ? (startPositions.get(i + 1) - headerLengths.get(i + 1)) : len;
            int length = end - start;
            if (length > 0) {
                nalUnits.add(new int[]{start, length});
            }
        }
        return nalUnits;
    }

    public synchronized void disconnect() {
        if (!isConnected && socket == null) return;
        isConnected = false;
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {}
        try {
            if (inputStream != null) inputStream.close();
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
        socket = null;
        inputStream = null;
        outputStream = null;

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        if (rtmpSenderThread != null) {
            rtmpSenderThread.interrupt();
            rtmpSenderThread = null;
        }
        outgoingRtmpQueue.clear();

        chunkStreams.clear();
        firstKeyframeSent = false;
        videoHeaderSent = false;
        audioHeaderSent = false;
        baseStreamPtsUs = -1;
        baseVideoPtsUs = -1;
        baseAudioPtsUs = -1;
        lastVideoTimestamp = 0;
        lastAudioTimestamp = 0;

        if (listener != null) {
            listener.onDisconnected();
        }
    }

    public static byte[] stripStartCode(byte[] data) {
        if (data == null || data.length < 4) return data;
        int offset = 0;
        if (data[0] == 0 && data[1] == 0) {
            if (data[2] == 1) {
                offset = 3;
            } else if (data[2] == 0 && data.length > 3 && data[3] == 1) {
                offset = 4;
            }
        }
        if (offset > 0) {
            byte[] out = new byte[data.length - offset];
            System.arraycopy(data, offset, out, 0, out.length);
            return out;
        }
        return data;
    }
}
