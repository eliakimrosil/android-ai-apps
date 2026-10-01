package com.screenstream.rtsp.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class StreamConfig {

    public static final String PREFS_NAME = "screen_stream_prefs";

    public static final int TARGET_RTSP = 0;
    public static final int TARGET_YOUTUBE = 1;
    public static final int TARGET_FACEBOOK = 2;
    public static final int TARGET_CUSTOM = 3;

    public static final int AUDIO_MODE_GAME = 0;
    public static final int AUDIO_MODE_MIC = 1;
    public static final int AUDIO_MODE_NONE = 2;
    public static final int AUDIO_MODE_GAME_AND_MIC = 3;

    public static final int ORIENTATION_LANDSCAPE = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_AUTO = 2;

    public static final String YOUTUBE_LIVE_URL = "rtmp://a.rtmp.youtube.com/live2";
    public static final String FB_LIVE_URL = "rtmps://live-api-s.facebook.com:443/rtmp/";

    public int streamTarget = TARGET_RTSP;
    public String youtubeServerUrl = YOUTUBE_LIVE_URL;
    public String youtubeStreamKey = "";
    public String fbServerUrl = FB_LIVE_URL;
    public String fbStreamKey = "";
    public String customRtmpUrl = "rtmp://live.twitch.tv/app/";
    public String customStreamKey = "";

    public int orientation = ORIENTATION_LANDSCAPE;
    public int targetDimension = 720; // 1080, 720, 0 (Native), 480
    public int fps = 60;
    public int bitrate = 6000000; // 6 Mbps in bps
    public int audioMode = AUDIO_MODE_GAME;
    public int port = 8554;

    public boolean floatingCameraEnabled = false;
    public boolean floatingCameraFacingFront = true;
    public boolean floatingCameraFlipped = true;
    public int floatingCameraRotation = 0; // 0, 90, 180, 270 degrees
    public int floatingCameraSizeIndex = 1; // 0=Small, 1=Medium, 2=Large
    public boolean floatingCameraCircular = false;

    public StreamConfig() {
    }

    public static StreamConfig load(Context context) {
        StreamConfig config = new StreamConfig();
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        config.streamTarget = sp.getInt("stream_target", TARGET_RTSP);

        config.youtubeServerUrl = sp.getString("yt_url", YOUTUBE_LIVE_URL);
        config.youtubeStreamKey = sp.getString("yt_key", "");

        config.fbServerUrl = sp.getString("fb_url", FB_LIVE_URL);
        config.fbStreamKey = sp.getString("fb_key", "");

        config.customRtmpUrl = sp.getString("custom_url", "rtmp://live.twitch.tv/app/");
        config.customStreamKey = sp.getString("custom_key", "");

        config.orientation = sp.getInt("orientation", ORIENTATION_LANDSCAPE);
        config.targetDimension = sp.getInt("target_dim", 720);
        config.fps = sp.getInt("fps", 60);
        config.bitrate = sp.getInt("bitrate", 6000000);
        config.audioMode = sp.getInt("audio_mode", AUDIO_MODE_GAME);
        config.port = sp.getInt("port", 8554);
        config.floatingCameraEnabled = sp.getBoolean("float_cam_enabled", false);
        config.floatingCameraFacingFront = sp.getBoolean("float_cam_front", true);
        config.floatingCameraFlipped = sp.getBoolean("float_cam_flipped", config.floatingCameraFacingFront);
        config.floatingCameraRotation = sp.getInt("float_cam_rotation", 0);
        config.floatingCameraSizeIndex = sp.getInt("float_cam_size", 1);
        config.floatingCameraCircular = sp.getBoolean("float_cam_circular", false);
        return config;
    }

    public void save(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putInt("stream_target", streamTarget)
                .putString("yt_url", youtubeServerUrl)
                .putString("yt_key", youtubeStreamKey)
                .putString("fb_url", fbServerUrl)
                .putString("fb_key", fbStreamKey)
                .putString("custom_url", customRtmpUrl)
                .putString("custom_key", customStreamKey)
                .putInt("orientation", orientation)
                .putInt("target_dim", targetDimension)
                .putInt("fps", fps)
                .putInt("bitrate", bitrate)
                .putInt("audio_mode", audioMode)
                .putInt("port", port)
                .putBoolean("float_cam_enabled", floatingCameraEnabled)
                .putBoolean("float_cam_front", floatingCameraFacingFront)
                .putBoolean("float_cam_flipped", floatingCameraFlipped)
                .putInt("float_cam_rotation", floatingCameraRotation)
                .putInt("float_cam_size", floatingCameraSizeIndex)
                .putBoolean("float_cam_circular", floatingCameraCircular)
                .apply();
    }

    public String getActiveRtmpUrl() {
        switch (streamTarget) {
            case TARGET_YOUTUBE:
                return (youtubeServerUrl != null && !youtubeServerUrl.isEmpty()) ? youtubeServerUrl : YOUTUBE_LIVE_URL;
            case TARGET_FACEBOOK:
                return (fbServerUrl != null && !fbServerUrl.isEmpty()) ? fbServerUrl : FB_LIVE_URL;
            case TARGET_CUSTOM:
                return customRtmpUrl;
            default:
                return "";
        }
    }

    public void setActiveRtmpUrl(String url) {
        switch (streamTarget) {
            case TARGET_YOUTUBE:
                youtubeServerUrl = url;
                break;
            case TARGET_FACEBOOK:
                fbServerUrl = url;
                break;
            case TARGET_CUSTOM:
                customRtmpUrl = url;
                break;
        }
    }

    public String getActiveStreamKey() {
        switch (streamTarget) {
            case TARGET_YOUTUBE:
                return youtubeStreamKey;
            case TARGET_FACEBOOK:
                return fbStreamKey;
            case TARGET_CUSTOM:
                return customStreamKey;
            default:
                return "";
        }
    }

    public void setActiveStreamKey(String key) {
        switch (streamTarget) {
            case TARGET_YOUTUBE:
                youtubeStreamKey = key;
                break;
            case TARGET_FACEBOOK:
                fbStreamKey = key;
                break;
            case TARGET_CUSTOM:
                customStreamKey = key;
                break;
        }
    }

    /**
     * Calculates width and height according to chosen orientation (Landscape, Portrait, or Auto).
     */
    public int[] calculateOutputDimensions(int screenWidth, int screenHeight) {
        if (orientation == ORIENTATION_PORTRAIT) {
            if (targetDimension == 1080) {
                return new int[]{1080, 1920};
            } else if (targetDimension == 720) {
                return new int[]{720, 1280};
            } else if (targetDimension == 480) {
                return new int[]{480, 854};
            } else {
                // Native Portrait (min dimension as width, max as height, aligned to 16)
                int minDim = Math.min(screenWidth, screenHeight);
                int maxDim = Math.max(screenWidth, screenHeight);
                int w = (minDim / 16) * 16;
                int h = (maxDim / 16) * 16;
                return new int[]{w, h};
            }
        } else if (orientation == ORIENTATION_AUTO) {
            boolean isLandscapeScreen = screenWidth >= screenHeight;
            if (isLandscapeScreen) {
                if (targetDimension == 1080) {
                    return new int[]{1920, 1080};
                } else if (targetDimension == 720) {
                    return new int[]{1280, 720};
                } else if (targetDimension == 480) {
                    return new int[]{854, 480};
                } else {
                    int w = (screenWidth / 16) * 16;
                    int h = (screenHeight / 16) * 16;
                    return new int[]{w, h};
                }
            } else {
                if (targetDimension == 1080) {
                    return new int[]{1080, 1920};
                } else if (targetDimension == 720) {
                    return new int[]{720, 1280};
                } else if (targetDimension == 480) {
                    return new int[]{480, 854};
                } else {
                    int w = (screenWidth / 16) * 16;
                    int h = (screenHeight / 16) * 16;
                    return new int[]{w, h};
                }
            }
        } else {
            // ORIENTATION_LANDSCAPE (default)
            if (targetDimension == 1080) {
                return new int[]{1920, 1080};
            } else if (targetDimension == 720) {
                return new int[]{1280, 720};
            } else if (targetDimension == 480) {
                return new int[]{854, 480};
            } else {
                // Native Landscape (max dimension as width, min as height, aligned to 16)
                int maxDim = Math.max(screenWidth, screenHeight);
                int minDim = Math.min(screenWidth, screenHeight);
                int w = (maxDim / 16) * 16;
                int h = (minDim / 16) * 16;
                return new int[]{w, h};
            }
        }
    }
}
