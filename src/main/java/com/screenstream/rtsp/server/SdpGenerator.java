package com.screenstream.rtsp.server;

public class SdpGenerator {

    public static String generateSdp(String hostIp, String streamName,
                                     String profileLevelId, String spropParameterSets,
                                     boolean hasAudio, String aacConfigHex) {
        StringBuilder sdp = new StringBuilder();
        sdp.append("v=0\r\n");
        sdp.append("o=- 0 0 IN IP4 ").append(hostIp).append("\r\n");
        sdp.append("s=").append(streamName).append("\r\n");
        sdp.append("c=IN IP4 0.0.0.0\r\n");
        sdp.append("t=0 0\r\n");
        sdp.append("a=recvonly\r\n");
        sdp.append("a=range:npt=now-\r\n");

        // Video Track (trackID=0)
        sdp.append("m=video 0 RTP/AVP 96\r\n");
        sdp.append("b=AS:8000\r\n");
        sdp.append("a=rtpmap:96 H264/90000\r\n");

        StringBuilder fmtp = new StringBuilder("a=fmtp:96 packetization-mode=1");
        if (profileLevelId != null && !profileLevelId.isEmpty()) {
            fmtp.append(";profile-level-id=").append(profileLevelId);
        }
        if (spropParameterSets != null && !spropParameterSets.isEmpty()) {
            fmtp.append(";sprop-parameter-sets=").append(spropParameterSets);
        }
        fmtp.append("\r\n");
        sdp.append(fmtp.toString());
        sdp.append("a=control:trackID=0\r\n");

        // Audio Track (trackID=1)
        if (hasAudio) {
            sdp.append("m=audio 0 RTP/AVP 97\r\n");
            sdp.append("b=AS:192\r\n");
            sdp.append("a=rtpmap:97 MPEG4-GENERIC/48000/2\r\n");
            String config = (aacConfigHex != null && !aacConfigHex.isEmpty()) ? aacConfigHex : "1190";
            sdp.append("a=fmtp:97 streamtype=5;profile-level-id=1;mode=AAC-hbr;config=")
               .append(config)
               .append(";sizelength=13;indexlength=3;indexdeltalength=3\r\n");
            sdp.append("a=control:trackID=1\r\n");
        }

        return sdp.toString();
    }
}
