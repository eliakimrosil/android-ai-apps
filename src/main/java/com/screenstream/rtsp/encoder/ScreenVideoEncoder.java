package com.screenstream.rtsp.encoder;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ScreenVideoEncoder {

    private static final String TAG = "ScreenVideoEncoder";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    public interface VideoFrameCallback {
        void onSpsPps(byte[] sps, byte[] pps);
        void onEncodedFrame(ByteBuffer buffer, int offset, int size, long ptsUs, boolean isKeyframe);
    }

    private final MediaProjection mediaProjection;
    private final int width;
    private final int height;
    private final int dpi;
    private final int fps;
    private final int bitrate;
    private final VideoFrameCallback callback;

    private MediaCodec mediaCodec;
    private Surface inputSurface;
    private VirtualDisplay virtualDisplay;
    private Thread encoderThread;

    private volatile boolean isRunning = false;

    public ScreenVideoEncoder(MediaProjection mediaProjection, int width, int height, int dpi,
                              int fps, int bitrate, VideoFrameCallback callback) {
        this.mediaProjection = mediaProjection;
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.fps = fps;
        this.bitrate = bitrate;
        this.callback = callback;
    }

    public synchronized void start() throws IOException {
        if (isRunning) return;

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(MediaFormat.KEY_LATENCY, 0);
        }
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100000L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1);
        }

        int targetProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
        int targetLevel = (fps >= 60 || width >= 1920)
                ? MediaCodecInfo.CodecProfileLevel.AVCLevel51
                : MediaCodecInfo.CodecProfileLevel.AVCLevel41;

        format.setInteger(MediaFormat.KEY_PROFILE, targetProfile);
        format.setInteger(MediaFormat.KEY_LEVEL, targetLevel);

        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        try {
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Exception e) {
            Log.w(TAG, "Failed to configure AVC High Profile, falling back to Baseline Profile: " + e.getMessage());
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }
        inputSurface = mediaCodec.createInputSurface();
        mediaCodec.start();

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenStreamDisplay",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
        );

        isRunning = true;
        encoderThread = new Thread(this::encodeLoop, "ScreenEncoderLoop");
        encoderThread.start();
        Log.i(TAG, "ScreenVideoEncoder started: " + width + "x" + height + "@" + fps + "fps");
    }

    private void encodeLoop() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (isRunning) {
            try {
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mediaCodec.getOutputFormat();
                    ByteBuffer csd0Buf = newFormat.getByteBuffer("csd-0");
                    ByteBuffer csd1Buf = newFormat.getByteBuffer("csd-1");
                    byte[] sps = null;
                    byte[] pps = null;

                    if (csd0Buf != null) {
                        byte[] csd0 = new byte[csd0Buf.remaining()];
                        csd0Buf.get(csd0);
                        List<byte[]> nals0 = extractNalUnits(csd0);
                        for (byte[] nal : nals0) {
                            int type = nal[0] & 0x1F;
                            if (type == 7) sps = nal;
                            else if (type == 8) pps = nal;
                        }
                    }
                    if (csd1Buf != null) {
                        byte[] csd1 = new byte[csd1Buf.remaining()];
                        csd1Buf.get(csd1);
                        List<byte[]> nals1 = extractNalUnits(csd1);
                        for (byte[] nal : nals1) {
                            int type = nal[0] & 0x1F;
                            if (type == 7 && sps == null) sps = nal;
                            else if (type == 8) pps = nal;
                        }
                    }

                    if (sps != null && pps != null && callback != null) {
                        callback.onSpsPps(sps, pps);
                    }
                } else if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null && bufferInfo.size > 0 && callback != null) {
                        boolean isKeyframe = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                        callback.onEncodedFrame(outputBuffer, bufferInfo.offset, bufferInfo.size,
                                bufferInfo.presentationTimeUs, isKeyframe);
                    }

                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Error in encodeLoop: " + e.getMessage());
                }
                break;
            }
        }
    }

    public void requestSyncFrame() {
        if (!isRunning || mediaCodec == null) return;
        try {
            Bundle bundle = new Bundle();
            bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            mediaCodec.setParameters(bundle);
        } catch (Exception ignored) {
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;

        if (encoderThread != null) {
            encoderThread.interrupt();
            try {
                encoderThread.join(500);
            } catch (InterruptedException ignored) {}
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }

        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
            } catch (Exception ignored) {}
            try {
                mediaCodec.release();
            } catch (Exception ignored) {}
            mediaCodec = null;
        }

        Log.i(TAG, "ScreenVideoEncoder stopped");
    }

    public static List<byte[]> extractNalUnits(byte[] data) {
        List<byte[]> result = new ArrayList<>();
        if (data == null || data.length < 4) return result;
        List<Integer> starts = new ArrayList<>();
        List<Integer> hdrLens = new ArrayList<>();
        int len = data.length;
        for (int i = 0; i < len - 3; i++) {
            if (data[i] == 0 && data[i + 1] == 0) {
                if (data[i + 2] == 1) {
                    starts.add(i + 3);
                    hdrLens.add(3);
                    i += 2;
                } else if (data[i + 2] == 0 && i < len - 4 && data[i + 3] == 1) {
                    starts.add(i + 4);
                    hdrLens.add(4);
                    i += 3;
                }
            }
        }
        int count = starts.size();
        if (count == 0) {
            result.add(data);
            return result;
        }
        for (int i = 0; i < count; i++) {
            int start = starts.get(i);
            int end = (i < count - 1) ? (starts.get(i + 1) - hdrLens.get(i + 1)) : len;
            int nLen = end - start;
            if (nLen > 0) {
                byte[] nal = new byte[nLen];
                System.arraycopy(data, start, nal, 0, nLen);
                result.add(nal);
            }
        }
        return result;
    }
}
