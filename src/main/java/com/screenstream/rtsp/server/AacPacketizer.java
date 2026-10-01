package com.screenstream.rtsp.server;

import java.nio.ByteBuffer;

public class AacPacketizer {

    public static final int RTP_PAYLOAD_TYPE = 97;
    public static final int SAMPLE_RATE = 48000;

    private int seqNum = 0;
    private final int ssrc;
    private String configHex = "1190"; // Default 48kHz Stereo AAC-LC

    public interface PacketListener {
        void onRtpPacket(byte[] packet);
    }

    public AacPacketizer(int ssrc) {
        this.ssrc = ssrc;
    }

    public synchronized void setAudioSpecificConfig(byte[] csd0) {
        if (csd0 != null && csd0.length >= 2) {
            StringBuilder sb = new StringBuilder();
            for (byte b : csd0) {
                sb.append(String.format("%02X", b));
            }
            this.configHex = sb.toString();
        }
    }

    public synchronized String getConfigHex() {
        return configHex;
    }

    public synchronized void processFrame(ByteBuffer buffer, int offset, int size, long ptsUs, PacketListener listener) {
        if (buffer == null || size <= 0) return;

        // Skip ADTS header if present (7 or 9 bytes starting with 0xFFF)
        int aacOffset = offset;
        int aacSize = size;
        if (size >= 7) {
            int b0 = buffer.get(offset) & 0xFF;
            int b1 = buffer.get(offset + 1) & 0xFF;
            if (b0 == 0xFF && (b1 & 0xF0) == 0xF0) {
                boolean hasProtection = (b1 & 0x01) == 0;
                int adtsHeaderLen = hasProtection ? 9 : 7;
                aacOffset += adtsHeaderLen;
                aacSize -= adtsHeaderLen;
            }
        }

        if (aacSize <= 0) return;

        byte[] payload = new byte[4 + aacSize];
        // RFC 3640 AU Header Section:
        // AU-headers-length: 16 bits -> 0x00, 0x10
        payload[0] = 0x00;
        payload[1] = 0x10;
        // AU-header: 13 bits aac size, 3 bits index
        int auHeader = (aacSize << 3) & 0xFFF8;
        payload[2] = (byte) ((auHeader >> 8) & 0xFF);
        payload[3] = (byte) (auHeader & 0xFF);

        int originalPos = buffer.position();
        buffer.position(aacOffset);
        buffer.get(payload, 4, aacSize);
        buffer.position(originalPos);

        long rtpTimestamp = (ptsUs * SAMPLE_RATE) / 1000000L;
        seqNum = (seqNum + 1) & 0xFFFF;

        byte[] packet = RtpPacket.createRtpPacket(
                true, RTP_PAYLOAD_TYPE, seqNum, rtpTimestamp, ssrc,
                payload, 0, payload.length
        );

        if (listener != null) {
            listener.onRtpPacket(packet);
        }
    }
}
