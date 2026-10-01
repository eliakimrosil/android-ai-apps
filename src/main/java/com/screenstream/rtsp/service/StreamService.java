package com.screenstream.rtsp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.screenstream.rtsp.R;
import com.screenstream.rtsp.audio.AudioStreamEncoder;
import com.screenstream.rtsp.encoder.ScreenVideoEncoder;
import com.screenstream.rtsp.rtmp.RtmpClient;
import com.screenstream.rtsp.server.RtspServer;
import com.screenstream.rtsp.ui.MainActivity;
import com.screenstream.rtsp.utils.NetworkUtils;
import com.screenstream.rtsp.utils.StreamConfig;

import java.util.concurrent.CopyOnWriteArrayList;

public class StreamService extends Service {

    private static final String TAG = "StreamService";
    private static final String CHANNEL_ID = "screen_stream_channel";
    private static final int NOTIFICATION_ID = 101;

    public static final String ACTION_START = "com.screenstream.rtsp.ACTION_START";
    public static final String ACTION_STOP = "com.screenstream.rtsp.ACTION_STOP";
    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";

    public interface StreamListener {
        void onStreamStarted(String targetInfo);
        void onStreamStopped();
        void onStatsUpdate(int activeClients, float fps, float bitrateMbps, long uptimeSeconds);
        void onStreamError(String message);
    }

    private static final CopyOnWriteArrayList<StreamListener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(StreamListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(StreamListener l) {
        listeners.remove(l);
    }

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public StreamService getService() {
            return StreamService.this;
        }
    }

    private MediaProjection mediaProjection;
    private ScreenVideoEncoder videoEncoder;
    private AudioStreamEncoder audioEncoder;
    private RtspServer rtspServer;
    private RtmpClient rtmpClient;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    private boolean isStreaming = false;
    private long streamStartTime = 0;
    private String currentTargetInfo = "";
    private int currentClients = 0;

    private Handler mainHandler;

    // Stats for RTMP mode
    private long rtmpBytes = 0;
    private int rtmpFrames = 0;
    private long lastStatsCheck = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public String getCurrentTargetInfo() {
        return currentTargetInfo;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            startStreaming(resultCode, resultData);
        } else if (ACTION_STOP.equals(action)) {
            stopStreaming();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startStreaming(int resultCode, Intent resultData) {
        if (isStreaming || resultData == null) return;

        StreamConfig config = StreamConfig.load(this);
        boolean isRtsp = (config.streamTarget == StreamConfig.TARGET_RTSP);

        if (isRtsp) {
            String hostIp = NetworkUtils.getPrimaryIpAddress(this);
            currentTargetInfo = "rtsp://" + hostIp + ":" + config.port + "/live";
        } else if (config.streamTarget == StreamConfig.TARGET_YOUTUBE) {
            currentTargetInfo = "YouTube Live";
        } else if (config.streamTarget == StreamConfig.TARGET_FACEBOOK) {
            currentTargetInfo = "Facebook Live";
        } else {
            currentTargetInfo = "Custom RTMP Live";
        }

        // Acquire Locks
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenStream:WakeLock");
                wakeLock.acquire(12 * 60 * 60 * 1000L);
            }
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ScreenStream:WifiLock");
                wifiLock.acquire();
            }
        } catch (Exception ignored) {
        }

        // Foreground Service
        boolean hasAudio = (config.audioMode != StreamConfig.AUDIO_MODE_NONE);
        Notification notification = buildNotification("Starting live stream...", currentTargetInfo);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (hasAudio) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            if (config.floatingCameraEnabled) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            }
            startForeground(NOTIFICATION_ID, notification, serviceType);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (hasAudio) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NOTIFICATION_ID, notification, serviceType);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Initialize MediaProjection
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection");
            stopStreaming();
            return;
        }

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopStreaming();
            }
        }, mainHandler);

        // Display Metrics
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = wm.getDefaultDisplay();
        display.getRealMetrics(metrics);

        int[] dims = config.calculateOutputDimensions(metrics.widthPixels, metrics.heightPixels);
        int encWidth = dims[0];
        int encHeight = dims[1];
        int dpi = metrics.densityDpi;
        hasAudio = (config.audioMode != StreamConfig.AUDIO_MODE_NONE);

        if (isRtsp) {
            // RTSP Mode
            try {
                rtspServer = new RtspServer(config.port, hasAudio, new RtspServer.ServerListener() {
                    @Override
                    public void onClientCountChanged(int count) {
                        currentClients = count;
                        updateNotification(currentTargetInfo + " (" + count + " viewers)");
                    }

                    @Override
                    public void onRequestKeyframe() {
                        if (videoEncoder != null) {
                            videoEncoder.requestSyncFrame();
                        }
                    }

                    @Override
                    public void onStatsUpdate(int activeClients, float fps, float bitrateMbps) {
                        currentClients = activeClients;
                        long uptime = (System.currentTimeMillis() - streamStartTime) / 1000L;
                        for (StreamListener l : listeners) {
                            l.onStatsUpdate(activeClients, fps, bitrateMbps, uptime);
                        }
                    }
                });
                rtspServer.start();
            } catch (Exception e) {
                notifyError("Failed to start RTSP server: " + e.getMessage());
                stopStreaming();
                return;
            }

            setupEncoders(encWidth, encHeight, dpi, config, hasAudio,
                    new ScreenVideoEncoder.VideoFrameCallback() {
                        @Override
                        public void onSpsPps(byte[] sps, byte[] pps) {
                            if (rtspServer != null) {
                                rtspServer.getVideoPacketizer().setSpsAndPps(sps, pps);
                            }
                        }

                        @Override
                        public void onEncodedFrame(java.nio.ByteBuffer buffer, int offset, int size, long ptsUs, boolean isKeyframe) {
                            if (rtspServer != null) {
                                rtspServer.getVideoPacketizer().processFrame(buffer, offset, size, ptsUs,
                                        (packet, key) -> rtspServer.dispatchVideoPacket(packet, key));
                            }
                        }
                    },
                    new AudioStreamEncoder.AudioFrameCallback() {
                        @Override
                        public void onAudioConfig(byte[] csd0) {
                            if (rtspServer != null) {
                                rtspServer.getAudioPacketizer().setAudioSpecificConfig(csd0);
                            }
                        }

                        @Override
                        public void onEncodedAudio(java.nio.ByteBuffer buffer, int offset, int size, long ptsUs) {
                            if (rtspServer != null) {
                                rtspServer.getAudioPacketizer().processFrame(buffer, offset, size, ptsUs,
                                        packet -> rtspServer.dispatchAudioPacket(packet));
                            }
                        }
                    });

        } else {
            // RTMP / YouTube / Facebook Live Mode
            String streamKey = config.getActiveStreamKey();
            String rtmpUrl = config.getActiveRtmpUrl();
            final String platformName = (config.streamTarget == StreamConfig.TARGET_YOUTUBE) ? "YouTube Live" :
                                        (config.streamTarget == StreamConfig.TARGET_FACEBOOK) ? "Facebook Live" : "RTMP Live";

            if (streamKey == null || streamKey.trim().isEmpty()) {
                notifyError(platformName + " Stream Key cannot be empty! Please paste your stream key in the app.");
                stopStreaming();
                return;
            }

            // RTMP platforms (YouTube, Facebook, Twitch) mandate an interleaved audio track.
            // Even if muted, AudioStreamEncoder will supply clean silent AAC frames so YouTube never shows "No data".
            boolean rtmpAudio = true;

            rtmpClient = new RtmpClient(
                    rtmpUrl,
                    streamKey.trim(),
                    encWidth,
                    encHeight,
                    config.fps,
                    config.bitrate,
                    rtmpAudio,
                    new RtmpClient.RtmpListener() {
                        @Override
                        public void onConnected() {
                            updateNotification("Streaming Live to " + platformName + "!");
                            if (videoEncoder != null) {
                                videoEncoder.requestSyncFrame();
                            }
                        }

                        @Override
                        public void onDisconnected() {
                            Log.i(TAG, "RTMP disconnected");
                            if (isStreaming) {
                                notifyError(platformName + " stream disconnected.");
                                mainHandler.post(StreamService.this::stopStreaming);
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e(TAG, "RTMP error: " + e.getMessage());
                            if (isStreaming) {
                                notifyError(platformName + " error: " + e.getMessage());
                                mainHandler.post(StreamService.this::stopStreaming);
                            }
                        }
                    }
            );

            new Thread(() -> {
                try {
                    rtmpClient.connect();
                } catch (Exception e) {
                    notifyError("Connection to " + platformName + " failed: " + e.getMessage());
                    stopStreaming();
                }
            }).start();

            lastStatsCheck = System.currentTimeMillis();

            setupEncoders(encWidth, encHeight, dpi, config, rtmpAudio,
                    new ScreenVideoEncoder.VideoFrameCallback() {
                        @Override
                        public void onSpsPps(byte[] sps, byte[] pps) {
                            if (rtmpClient != null) {
                                rtmpClient.setSpsAndPps(sps, pps);
                            }
                        }

                        @Override
                        public void onEncodedFrame(java.nio.ByteBuffer buffer, int offset, int size, long ptsUs, boolean isKeyframe) {
                            if (rtmpClient != null) {
                                rtmpClient.sendVideoFrame(buffer, offset, size, ptsUs, isKeyframe);
                                rtmpBytes += size;
                                rtmpFrames++;

                                long now = System.currentTimeMillis();
                                long elapsed = now - lastStatsCheck;
                                if (elapsed >= 1000) {
                                    float fps = (rtmpFrames * 1000.0f) / elapsed;
                                    float mbps = (rtmpBytes * 8.0f) / (elapsed * 1000.0f);
                                    rtmpFrames = 0;
                                    rtmpBytes = 0;
                                    lastStatsCheck = now;
                                    long uptime = (now - streamStartTime) / 1000L;
                                    for (StreamListener l : listeners) {
                                        l.onStatsUpdate(1, fps, mbps, uptime);
                                    }
                                }
                            }
                        }
                    },
                    new AudioStreamEncoder.AudioFrameCallback() {
                        @Override
                        public void onAudioConfig(byte[] csd0) {
                            if (rtmpClient != null) {
                                try {
                                    rtmpClient.sendAudioHeader(csd0);
                                } catch (Exception ignored) {}
                            }
                        }

                        @Override
                        public void onEncodedAudio(java.nio.ByteBuffer buffer, int offset, int size, long ptsUs) {
                            if (rtmpClient != null) {
                                rtmpClient.sendAudioFrame(buffer, offset, size, ptsUs);
                                rtmpBytes += size;
                            }
                        }
                    });
        }

        isStreaming = true;
        streamStartTime = System.currentTimeMillis();

        for (StreamListener l : listeners) {
            l.onStreamStarted(currentTargetInfo);
        }
    }

    private void setupEncoders(int encWidth, int encHeight, int dpi, StreamConfig config, boolean hasAudio,
                               ScreenVideoEncoder.VideoFrameCallback videoCb,
                               AudioStreamEncoder.AudioFrameCallback audioCb) {
        try {
            videoEncoder = new ScreenVideoEncoder(
                    mediaProjection, encWidth, encHeight, dpi, config.fps, config.bitrate, videoCb
            );
            videoEncoder.start();
        } catch (Exception e) {
            notifyError("Failed to start video encoder: " + e.getMessage());
            stopStreaming();
            return;
        }

        if (hasAudio) {
            try {
                audioEncoder = new AudioStreamEncoder(mediaProjection, config.audioMode, audioCb);
                audioEncoder.start();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start audio encoder: " + e.getMessage());
            }
        }
    }

    private void notifyError(String msg) {
        mainHandler.post(() -> {
            for (StreamListener l : listeners) {
                l.onStreamError(msg);
            }
        });
    }

    public void stopStreaming() {
        if (!isStreaming) return;
        isStreaming = false;

        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder = null;
        }

        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder = null;
        }

        if (rtspServer != null) {
            rtspServer.stop();
            rtspServer = null;
        }

        if (rtmpClient != null) {
            rtmpClient.disconnect();
            rtmpClient = null;
        }

        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception ignored) {}
            mediaProjection = null;
        }

        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {}

        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {}

        // Auto-dismiss floating facecam overlay if open
        try {
            com.screenstream.rtsp.camera.FloatingCameraManager fcm = com.screenstream.rtsp.camera.FloatingCameraManager.getInstance(this);
            if (fcm.isShowing()) {
                fcm.hide();
            }
        } catch (Exception ignored) {}

        stopForeground(true);

        for (StreamListener l : listeners) {
            l.onStreamStopped();
        }

        Log.i(TAG, "Streaming stopped cleanly");
    }

    private void updateNotification(String content) {
        String title = "ScreenStream • LIVE";
        Notification notification = buildNotification(title, content);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String title, String content) {
        Intent appIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent stopIntent = new Intent(this, StreamService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        null,
                        getString(R.string.notification_stop),
                        stopPendingIntent
                ).build());

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_desc));
            channel.setLightColor(Color.CYAN);
            channel.enableVibration(false);

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopStreaming();
        super.onDestroy();
    }
}
