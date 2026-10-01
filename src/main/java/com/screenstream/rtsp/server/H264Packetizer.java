package com.screenstream.rtsp.server;

import android.util.Base64;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class H264Packetizer {

    public static final int MAX_PAYLOAD_SIZE = 1360;
    public static final int RTP_PAYLOAD_TYPE = 96;

    private byte[] sps = null;
    private byte[] pps = null;
    private int seqNum = 0;
    private final int ssrc;

    public interface PacketListener {
        void onRtpPacket(byte[] packet, boolean isKeyframe);
    }

    public H264Packetizer(int ssrc) {
        this.ssrc = ssrc;
    }

    public synchronized void setSpsAndPps(byte[] sps, byte[] pps) {
        this.sps = sps;
        this.pps = pps;
    }

    public synchronized byte[] getSps() {
        return sps;
    }

    public synchronized byte[] getPps() {
        return pps;
    }

    public synchronized String getProfileLevelId() {
        if (sps != null && sps.length >= 4) {
            return String.format("%02x%02x%02x", sps[1] & 0xFF, sps[2] & 0xFF, sps[3] & 0xFF);
        }
        return "42e01f"; // Baseline Profile fallback
    }

    public synchronized String getSpropParameterSets() {
        if (sps != null && pps != null) {
            String spsB64 = Base64.encodeToString(sps, Base64.NO_WRAP);
            String ppsB64 = Base64.encodeToString(pps, Base64.NO_WRAP);
            return spsB64 + "," + ppsB64;
        }
        return null;
    }

    /**
     * Process encoded H.264 Annex-B buffer from MediaCodec.
     */
    public synchronized void processFrame(ByteBuffer buffer, int offset, int size, long ptsUs, PacketListener listener) {
        if (buffer == null || size <= 4) return;

        byte[] data = new byte[size];
        int originalPos = buffer.position();
        buffer.position(offset);
        buffer.get(data);
        buffer.position(originalPos);

        long rtpTimestamp = (ptsUs * 90) / 1000L; // 90 kHz clock

        // Find all NAL units in this buffer
        List<int[]> nalIndices = findNalUnits(data);
        int totalNals = nalIndices.size();

        for (int i = 0; i < totalNals; i++) {
            int[] nal = nalIndices.get(i);
            int nalStart = nal[0];
            int nalLen = nal[1];
            if (nalLen <= 0) continue;

            byte nalHeader = data[nalStart];
            int nalType = nalHeader & 0x1F;
            boolean isLastNal = (i == totalNals - 1);
            boolean isKeyframe = (nalType == 5);

            if (nalType == 7) { // SPS
                sps = new byte[nalLen];
                System.arraycopy(data, nalStart, sps, 0, nalLen);
            } else if (nalType == 8) { // PPS
                pps = new byte[nalLen];
                System.arraycopy(data, nalStart, pps, 0, nalLen);
            }

            if (nalLen <= MAX_PAYLOAD_SIZE) {
                // Single NAL unit packet
                seqNum = (seqNum + 1) & 0xFFFF;
                byte[] packet = RtpPacket.createRtpPacket(
                        isLastNal, RTP_PAYLOAD_TYPE, seqNum, rtpTimestamp, ssrc,
                        data, nalStart, nalLen
                );
                if (listener != null) {
                    listener.onRtpPacket(packet, isKeyframe);
                }
            } else {
                // FU-A Fragmentation (RFC 6184)
                byte fuIndicator = (byte) ((nalHeader & 0xE0) | 28); // FU-A type 28
                byte fuHeaderBase = (byte) (nalHeader & 0x1F);

                int payloadOffset = nalStart + 1; // skip NAL header byte
                int remaining = nalLen - 1;
                byte[] fuPayload = new byte[MAX_PAYLOAD_SIZE + 2];

                while (remaining > 0) {
                    int chunkSize = Math.min(remaining, MAX_PAYLOAD_SIZE);
                    boolean isFirst = (payloadOffset == nalStart + 1);
                    boolean isLastChunk = (chunkSize == remaining);

                    byte fuHeader = fuHeaderBase;
                    if (isFirst) fuHeader |= 0x80;       // Start bit S=1
                    if (isLastChunk) fuHeader |= 0x40;   // End bit E=1

                    fuPayload[0] = fuIndicator;
                    fuPayload[1] = fuHeader;
                    System.arraycopy(data, payloadOffset, fuPayload, 2, chunkSize);

                    seqNum = (seqNum + 1) & 0xFFFF;
                    boolean marker = isLastChunk && isLastNal;

                    byte[] packet = RtpPacket.createRtpPacket(
                            marker, RTP_PAYLOAD_TYPE, seqNum, rtpTimestamp, ssrc,
                            fuPayload, 0, chunkSize + 2
                    );

                    if (listener != null) {
                        listener.onRtpPacket(packet, isKeyframe);
                    }

                    payloadOffset += chunkSize;
                    remaining -= chunkSize;
                }
            }
        }
    }

    private List<int[]> findNalUnits(byte[] data) {
        List<Integer> startPositions = new ArrayList<>();
        List<Integer> headerLengths = new ArrayList<>();
        int len = data.length;

        for (int i = 0; i < len - 3; i++) {
            if (data[i] == 0 && data[i + 1] == 0) {
                if (data[i + 2] == 1) { // 3-byte start code 00 00 01
                    startPositions.add(i + 3);
                    headerLengths.add(3);
                    i += 2;
                } else if (data[i + 2] == 0 && i < len - 4 && data[i + 3] == 1) { // 4-byte start code 00 00 00 01
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
}
