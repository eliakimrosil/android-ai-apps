package com.screenstream.rtsp.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import com.screenstream.rtsp.utils.StreamConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class AudioStreamEncoder {

    private static final String TAG = "AudioStreamEncoder";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNEL_COUNT = 2;
    public static final int BITRATE = 192000; // 192 kbps studio quality

    public static final int SAMPLES_PER_FRAME = 1024; // AAC-LC standard
    public static final int FRAME_BYTES = SAMPLES_PER_FRAME * CHANNEL_COUNT * 2; // 4096 bytes (16-bit stereo)

    public interface AudioFrameCallback {
        void onAudioConfig(byte[] csd0);
        void onEncodedAudio(ByteBuffer buffer, int offset, int size, long ptsUs);
    }

    private final MediaProjection mediaProjection;
    private final int audioMode;
    private final AudioFrameCallback callback;

    private AudioRecord audioRecord;
    private AudioRecord audioRecordMic;
    private MediaCodec mediaCodec;
    private Thread captureThread;
    private Thread micThread;
    private Thread encodeThread;

    private final BlockingQueue<byte[]> micQueue = new LinkedBlockingQueue<>(4);

    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler echoCanceler;
    private AutomaticGainControl autoGain;

    private volatile boolean isRunning = false;
    private long encodedFrames = 0;

    public AudioStreamEncoder(MediaProjection mediaProjection, int audioMode, AudioFrameCallback callback) {
        this.mediaProjection = mediaProjection;
        this.audioMode = audioMode;
        this.callback = callback;
    }

    public synchronized void start() throws IOException {
        if (isRunning) return;

        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        // Robust buffer size: 8 frames = 32KB to eliminate any buffer overruns/underruns
        int bufferSize = Math.max(minBufferSize * 2, FRAME_BYTES * 8);

        if (audioMode == StreamConfig.AUDIO_MODE_GAME) {
            audioRecord = createGameAudioRecord(bufferSize);
        } else if (audioMode == StreamConfig.AUDIO_MODE_MIC) {
            audioRecord = createMicAudioRecord(bufferSize);
            if (audioRecord != null) {
                attachAudioEffects(audioRecord.getAudioSessionId());
            }
        } else if (audioMode == StreamConfig.AUDIO_MODE_GAME_AND_MIC) {
            audioRecord = createGameAudioRecord(bufferSize);
            audioRecordMic = createMicAudioRecord(bufferSize);
            if (audioRecordMic != null) {
                attachAudioEffects(audioRecordMic.getAudioSessionId());
            }
        } else {
            audioRecord = null;
            audioRecordMic = null;
        }

        MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_BYTES * 4);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
        audioFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            audioFormat.setInteger(MediaFormat.KEY_COMPLEXITY, 10);
        }

        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        if (audioRecord != null) {
            try {
                audioRecord.startRecording();
            } catch (Exception e) {
                Log.w(TAG, "Game startRecording failed: " + e.getMessage());
                audioRecord = null;
            }
        }
        if (audioRecordMic != null) {
            try {
                audioRecordMic.startRecording();
            } catch (Exception e) {
                Log.w(TAG, "Mic startRecording failed: " + e.getMessage());
                audioRecordMic = null;
            }
        }
        isRunning = true;

        if (audioRecordMic != null && audioMode == StreamConfig.AUDIO_MODE_GAME_AND_MIC) {
            micThread = new Thread(this::micCaptureLoop, "MicCaptureThread");
            micThread.start();
        }

        captureThread = new Thread(this::audioCaptureLoop, "AudioCaptureThread");
        encodeThread = new Thread(this::audioEncodeLoop, "AudioEncodeThread");

        captureThread.start();
        encodeThread.start();
        Log.i(TAG, "AudioStreamEncoder started at " + SAMPLE_RATE + "Hz, " + (BITRATE / 1000) + "kbps, mode=" + audioMode + " (gameRecord=" + (audioRecord != null) + ", micRecord=" + (audioRecordMic != null) + ")");
    }

    private AudioRecord createMicAudioRecord(int bufferSize) {
        AudioRecord record = null;
        try {
            record = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                record = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                );
            }
        } catch (Exception e) {
            Log.w(TAG, "Microphone init error: " + e.getMessage());
            record = null;
        }
        if (record != null && record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            record = null;
        }
        return record;
    }

    private AudioRecord createGameAudioRecord(int bufferSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
            try {
                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build();

                AudioFormat format = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build();

                AudioRecord record = new AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .build();
                if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                    return record;
                }
                record.release();
            } catch (Exception e) {
                Log.w(TAG, "AudioPlaybackCapture init error: " + e.getMessage());
            }
        }
        return null;
    }

    private void attachAudioEffects(int sessionId) {
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                    Log.i(TAG, "Hardware NoiseSuppressor enabled");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "NoiseSuppressor not enabled: " + e.getMessage());
        }

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId);
                if (echoCanceler != null) {
                    echoCanceler.setEnabled(true);
                    Log.i(TAG, "Hardware AcousticEchoCanceler enabled");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "AcousticEchoCanceler not enabled: " + e.getMessage());
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                autoGain = AutomaticGainControl.create(sessionId);
                if (autoGain != null) {
                    autoGain.setEnabled(true);
                    Log.i(TAG, "Hardware AutomaticGainControl enabled");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "AutomaticGainControl not enabled: " + e.getMessage());
        }
    }

    private void applySoftGain(byte[] pcm, int length, float gain) {
        if (gain == 1.0f) return;
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int val = Math.round(sample * gain);
            if (val > 32767) val = 32767;
            else if (val < -32768) val = -32768;
            pcm[i] = (byte) (val & 0xFF);
            pcm[i + 1] = (byte) ((val >> 8) & 0xFF);
        }
    }

    private void mixPcm(byte[] gameBuf, byte[] micBuf, byte[] outBuf, float gameGain, float micGain) {
        for (int i = 0; i < outBuf.length - 1; i += 2) {
            short g = (short) ((gameBuf[i] & 0xFF) | (gameBuf[i + 1] << 8));
            short m = (short) ((micBuf[i] & 0xFF) | (micBuf[i + 1] << 8));
            int sum = Math.round(g * gameGain + m * micGain);
            if (sum > 32767) sum = 32767;
            else if (sum < -32768) sum = -32768;
            outBuf[i] = (byte) (sum & 0xFF);
            outBuf[i + 1] = (byte) ((sum >> 8) & 0xFF);
        }
    }

    private void micCaptureLoop() {
        byte[] buffer = new byte[FRAME_BYTES];
        while (isRunning && audioRecordMic != null) {
            int offset = 0;
            while (isRunning && offset < FRAME_BYTES) {
                try {
                    int read = audioRecordMic.read(buffer, offset, FRAME_BYTES - offset, AudioRecord.READ_BLOCKING);
                    if (read > 0) {
                        offset += read;
                    } else if (read < 0) {
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }
            if (!isRunning) break;
            if (offset == FRAME_BYTES) {
                while (micQueue.size() >= 3) {
                    micQueue.poll();
                }
                byte[] frame = new byte[FRAME_BYTES];
                System.arraycopy(buffer, 0, frame, 0, FRAME_BYTES);
                micQueue.offer(frame);
            }
        }
    }

    private void audioCaptureLoop() {
        byte[] frameBuffer = new byte[FRAME_BYTES];
        long totalSamplesCaptured = 0;
        long baseSystemTimeUs = -1;

        while (isRunning) {
            int offset = 0;

            if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                while (isRunning && offset < FRAME_BYTES) {
                    try {
                        int read = audioRecord.read(frameBuffer, offset, FRAME_BYTES - offset, AudioRecord.READ_BLOCKING);
                        if (read > 0) {
                            offset += read;
                        } else if (read < 0) {
                            Log.w(TAG, "AudioRecord read error: " + read);
                            break;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "AudioRecord read exception: " + e.getMessage());
                        break;
                    }
                }
            }

            if (!isRunning) break;

            if (audioMode == StreamConfig.AUDIO_MODE_GAME_AND_MIC) {
                byte[] micFrame = micQueue.poll();
                if (offset == FRAME_BYTES && micFrame != null) {
                    mixPcm(frameBuffer, micFrame, frameBuffer, 0.85f, 1.15f);
                } else if (offset == FRAME_BYTES) {
                    applySoftGain(frameBuffer, FRAME_BYTES, 1.2f);
                } else if (micFrame != null) {
                    System.arraycopy(micFrame, 0, frameBuffer, 0, FRAME_BYTES);
                    applySoftGain(frameBuffer, FRAME_BYTES, 1.25f);
                    offset = FRAME_BYTES;
                } else {
                    Arrays.fill(frameBuffer, (byte) 0);
                    offset = FRAME_BYTES;
                    try {
                        Thread.sleep(21);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            } else if (offset < FRAME_BYTES) {
                // If audioRecord was null (Mute mode) or returned incomplete data/standby,
                // fill with clean silence and pace it so the encoder receives continuous clock.
                Arrays.fill(frameBuffer, offset, FRAME_BYTES, (byte) 0);
                try {
                    // Frame duration: 1024 / 48000 = ~21.33 ms
                    Thread.sleep(21);
                } catch (InterruptedException ignored) {
                    break;
                }
            } else if (audioMode == StreamConfig.AUDIO_MODE_GAME) {
                // Boost game audio slightly for rich, punchy stream volume
                applySoftGain(frameBuffer, FRAME_BYTES, 1.3f);
            } else if (audioMode == StreamConfig.AUDIO_MODE_MIC) {
                // Boost microphone audio slightly for crisp voice commentary
                applySoftGain(frameBuffer, FRAME_BYTES, 1.25f);
            }

            if (!isRunning) break;

            // Feed the complete 1024-sample frame into MediaCodec without dropping
            int inputIndex = -1;
            while (isRunning && inputIndex < 0) {
                try {
                    inputIndex = mediaCodec.dequeueInputBuffer(10000);
                    if (inputIndex < 0) {
                        Thread.sleep(2);
                    }
                } catch (Exception e) {
                    break;
                }
            }

            if (!isRunning || inputIndex < 0) break;

            try {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(frameBuffer, 0, FRAME_BYTES);

                    if (baseSystemTimeUs < 0) {
                        baseSystemTimeUs = System.nanoTime() / 1000L;
                    }
                    long ptsUs = baseSystemTimeUs + ((totalSamplesCaptured * 1000000L) / SAMPLE_RATE);
                    totalSamplesCaptured += SAMPLES_PER_FRAME;

                    mediaCodec.queueInputBuffer(inputIndex, 0, FRAME_BYTES, ptsUs, 0);
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Error queuing audio to MediaCodec: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void audioEncodeLoop() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (isRunning) {
            try {
                int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = mediaCodec.getOutputFormat();
                    ByteBuffer csd0 = format.getByteBuffer("csd-0");
                    if (csd0 != null && callback != null) {
                        byte[] csdBytes = new byte[csd0.remaining()];
                        csd0.get(csdBytes);
                        callback.onAudioConfig(csdBytes);
                    }
                } else if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && bufferInfo.size > 0 && callback != null) {
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            callback.onEncodedAudio(outputBuffer, bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs);
                            encodedFrames++;
                            if (encodedFrames == 1 || encodedFrames % 200 == 0) {
                                Log.i(TAG, "Encoded AAC audio frame #" + encodedFrames + " (" + bufferInfo.size + " bytes, pts=" + (bufferInfo.presentationTimeUs / 1000) + "ms)");
                            }
                        }
                    }
                    mediaCodec.releaseOutputBuffer(outputIndex, false);
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Error in audioEncodeLoop: " + e.getMessage());
                }
                break;
            }
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;

        if (captureThread != null) {
            captureThread.interrupt();
        }
        if (micThread != null) {
            micThread.interrupt();
            micThread = null;
        }
        if (encodeThread != null) {
            encodeThread.interrupt();
        }
        micQueue.clear();

        if (noiseSuppressor != null) {
            try { noiseSuppressor.release(); } catch (Exception ignored) {}
            noiseSuppressor = null;
        }
        if (echoCanceler != null) {
            try { echoCanceler.release(); } catch (Exception ignored) {}
            echoCanceler = null;
        }
        if (autoGain != null) {
            try { autoGain.release(); } catch (Exception ignored) {}
            autoGain = null;
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {}
            try {
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }

        if (audioRecordMic != null) {
            try {
                audioRecordMic.stop();
            } catch (Exception ignored) {}
            try {
                audioRecordMic.release();
            } catch (Exception ignored) {}
            audioRecordMic = null;
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

        Log.i(TAG, "AudioStreamEncoder stopped");
    }
}
