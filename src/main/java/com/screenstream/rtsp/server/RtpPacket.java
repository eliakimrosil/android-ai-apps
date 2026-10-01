package com.screenstream.rtsp.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class RtpPacket {

    public static final int HEADER_SIZE = 12;

    public static byte[] createRtpPacket(boolean marker, int payloadType, int seqNum, long timestamp, int ssrc,
                                         byte[] payload, int payloadOffset, int payloadLength) {
        byte[] packet = new byte[HEADER_SIZE + payloadLength];

        // Byte 0: V=2, P=0, X=0, CC=0 -> 10000000 -> 0x80
        packet[0] = (byte) 0x80;

        // Byte 1: M (1 bit), PT (7 bits)
        packet[1] = (byte) ((marker ? 0x80 : 0x00) | (payloadType & 0x7F));

        // Bytes 2-3: Sequence Number (16 bits)
        packet[2] = (byte) ((seqNum >> 8) & 0xFF);
        packet[3] = (byte) (seqNum & 0xFF);

        // Bytes 4-7: Timestamp (32 bits)
        packet[4] = (byte) ((timestamp >> 24) & 0xFF);
        packet[5] = (byte) ((timestamp >> 16) & 0xFF);
        packet[6] = (byte) ((timestamp >> 8) & 0xFF);
        packet[7] = (byte) (timestamp & 0xFF);

        // Bytes 8-11: SSRC (32 bits)
        packet[8] = (byte) ((ssrc >> 24) & 0xFF);
        packet[9] = (byte) ((ssrc >> 16) & 0xFF);
        packet[10] = (byte) ((ssrc >> 8) & 0xFF);
        packet[11] = (byte) (ssrc & 0xFF);

        // Payload
        if (payloadLength > 0 && payload != null) {
            System.arraycopy(payload, payloadOffset, packet, HEADER_SIZE, payloadLength);
        }

        return packet;
    }

    public static byte[] createInterleavedFrame(int channel, byte[] rtpPacket) {
        int rtpLength = rtpPacket.length;
        byte[] frame = new byte[4 + rtpLength];
        frame[0] = '$'; // Magic '$'
        frame[1] = (byte) (channel & 0xFF);
        frame[2] = (byte) ((rtpLength >> 8) & 0xFF);
        frame[3] = (byte) (rtpLength & 0xFF);
        System.arraycopy(rtpPacket, 0, frame, 4, rtpLength);
        return frame;
    }
}
