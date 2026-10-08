package com.aistudio.omnistreamlivestudio;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;

public class FloatingCamManager {

    private static final String TAG = "FloatingCamManager";

    public interface StateListener {
        void onFacecamToggled(boolean isShowing);
        void onCameraFlipped(boolean isFront);
        default void onCameraRotated(int rotationDegrees) {}
    }

    private static FloatingCamManager instance;

    private Context appContext;
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams windowParams;

    private TextView tvPipBadge;
    private TextView btnPipRotate;
    private TextView btnPipFlip;
    private TextView btnPipSize;
    private TextView btnPipClose;
    private TextureView tvPipCamera;

    // Camera2 State
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private boolean isFrontCamera = true;
    private int sensorOrientation = 0;
    private Size previewSize;
    private boolean isOverlayShowing = false;
    private boolean isLiveBroadcasting = false;

    // Camera Rotation: 0 = 0° (Portrait), 90 = 90° (Landscape), 180 = 180° (Inverted), 270 = 270° (Rev. Landscape)
    private int currentRotationDegrees = 0;

    // Window Sizing: 0 = Compact, 1 = Studio Medium (Default), 2 = Large
    private int currentSizeIndex = 1;
    private static final int[][] SIZES_DP = {
            {140, 185}, // Compact
            {175, 230}, // Medium (Default)
            {220, 290}  // Large
    };

    private StateListener stateListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FloatingCamManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized FloatingCamManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatingCamManager(context);
        }
        return instance;
    }

    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    public boolean isShowing() {
        return isOverlayShowing;
    }

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    public void setLiveState(boolean isLive) {
        this.isLiveBroadcasting = isLive;
        mainHandler.post(() -> {
            if (overlayView != null && tvPipBadge != null) {
                if (isLive) {
                    overlayView.setBackgroundResource(R.drawable.bg_floating_cam_live);
                    tvPipBadge.setText("● LIVE");
                    tvPipBadge.setTextColor(0xFFEF4444); // Studio Ruby
                } else {
                    overlayView.setBackgroundResource(R.drawable.bg_floating_cam_standby);
                    tvPipBadge.setText("●");
                    tvPipBadge.setTextColor(0xFF00E5FF); // Studio Cyan
                }
            }
        });
    }

    public synchronized void showOverlay() {
        if (isOverlayShowing) return;

        if (!Settings.canDrawOverlays(appContext)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW not granted");
            return;
        }

        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(appContext, "Camera permission required for floating facecam", Toast.LENGTH_SHORT).show();
            return;
        }

        mainHandler.post(() -> {
            try {
                initOverlayView();
                windowManager.addView(overlayView, windowParams);
                isOverlayShowing = true;
                setLiveState(isLiveBroadcasting);

                startCameraThread();
                if (tvPipCamera.isAvailable()) {
                    openCamera(tvPipCamera.getSurfaceTexture(), tvPipCamera.getWidth(), tvPipCamera.getHeight());
                }

                if (stateListener != null) {
                    stateListener.onFacecamToggled(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error displaying floating camera overlay", e);
            }
        });
    }

    public synchronized void hideOverlay() {
        if (!isOverlayShowing) return;

        mainHandler.post(() -> {
            closeCamera();
            stopCameraThread();

            if (overlayView != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception ignored) {}
                overlayView = null;
            }
            isOverlayShowing = false;

            if (stateListener != null) {
                stateListener.onFacecamToggled(false);
            }
        });
    }

    public void flipCamera() {
        isFrontCamera = !isFrontCamera;
        if (isOverlayShowing && tvPipCamera != null && tvPipCamera.isAvailable()) {
            if (cameraHandler != null) {
                cameraHandler.post(() -> {
                    closeCamera();
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException ignored) {}
                    mainHandler.post(() -> {
                        if (isOverlayShowing && tvPipCamera != null && tvPipCamera.isAvailable()) {
                            openCamera(tvPipCamera.getSurfaceTexture(), tvPipCamera.getWidth(), tvPipCamera.getHeight());
                        }
                    });
                });
            }
        }
        if (stateListener != null) {
            mainHandler.post(() -> stateListener.onCameraFlipped(isFrontCamera));
        }
    }

    public void rotateCamera() {
        currentRotationDegrees = (currentRotationDegrees + 90) % 360;
        updateWindowDimensionsAndTransform();
        String rotLabel = currentRotationDegrees == 0 ? "0° (Portrait)" :
                          currentRotationDegrees == 90 ? "90° (Landscape)" :
                          currentRotationDegrees == 180 ? "180° (Inverted)" : "270° (Rev. Landscape)";
        Toast.makeText(appContext, "Floating Cam: " + rotLabel, Toast.LENGTH_SHORT).show();
        if (stateListener != null) {
            mainHandler.post(() -> stateListener.onCameraRotated(currentRotationDegrees));
        }
    }

    public int getCameraRotationDegrees() {
        return currentRotationDegrees;
    }

    public void cycleSize() {
        currentSizeIndex = (currentSizeIndex + 1) % SIZES_DP.length;
        updateWindowDimensionsAndTransform();
    }

    private void updateWindowDimensionsAndTransform() {
        if (isOverlayShowing && overlayView != null && windowParams != null && windowManager != null) {
            float density = appContext.getResources().getDisplayMetrics().density;
            boolean isLandscape = (currentRotationDegrees == 90 || currentRotationDegrees == 270);
            int baseW = isLandscape ? SIZES_DP[currentSizeIndex][1] : SIZES_DP[currentSizeIndex][0];
            int baseH = isLandscape ? SIZES_DP[currentSizeIndex][0] : SIZES_DP[currentSizeIndex][1];

            windowParams.width = (int) (baseW * density);
            windowParams.height = (int) (baseH * density);
            clampPosition(windowParams);
            try {
                windowManager.updateViewLayout(overlayView, windowParams);
            } catch (Exception ignored) {}

            overlayView.post(() -> {
                if (tvPipCamera != null && tvPipCamera.getWidth() > 0 && tvPipCamera.getHeight() > 0) {
                    configureTransform(tvPipCamera.getWidth(), tvPipCamera.getHeight());
                }
            });
        }
    }

    private void initOverlayView() {
        LayoutInflater inflater = LayoutInflater.from(appContext);
        overlayView = inflater.inflate(R.layout.layout_floating_cam, null);

        tvPipBadge = overlayView.findViewById(R.id.pipBadge);
        btnPipRotate = overlayView.findViewById(R.id.pipBtnRotate);
        btnPipFlip = overlayView.findViewById(R.id.pipBtnFlip);
        btnPipSize = overlayView.findViewById(R.id.pipBtnSize);
        btnPipClose = overlayView.findViewById(R.id.pipBtnClose);
        tvPipCamera = overlayView.findViewById(R.id.pipCameraView);

        float density = appContext.getResources().getDisplayMetrics().density;
        boolean isLandscape = (currentRotationDegrees == 90 || currentRotationDegrees == 270);
        int baseW = isLandscape ? SIZES_DP[currentSizeIndex][1] : SIZES_DP[currentSizeIndex][0];
        int baseH = isLandscape ? SIZES_DP[currentSizeIndex][0] : SIZES_DP[currentSizeIndex][1];

        int widthPx = (int) (baseW * density);
        int heightPx = (int) (baseH * density);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        windowParams = new WindowManager.LayoutParams(
                widthPx, heightPx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = (int) (16 * density);
        windowParams.y = (int) (80 * density);

        setupOverlayListeners();
    }

    private void setupOverlayListeners() {
        if (btnPipRotate != null) {
            btnPipRotate.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                rotateCamera();
            });
        }

        btnPipFlip.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            flipCamera();
        });

        btnPipSize.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleSize();
        });

        btnPipClose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            hideOverlay();
        });

        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowParams.x;
                        initialY = windowParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoving = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6 || isMoving) {
                            isMoving = true;
                            windowParams.x = initialX + dx;
                            windowParams.y = initialY + dy;
                            clampPosition(windowParams);
                            try {
                                windowManager.updateViewLayout(overlayView, windowParams);
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        return true;
                }
                return false;
            }
        };

        tvPipCamera.setOnTouchListener(dragListener);
        tvPipBadge.setOnTouchListener(dragListener);
        View spacer = overlayView.findViewById(R.id.pipHeaderSpacer);
        if (spacer != null) {
            spacer.setOnTouchListener(dragListener);
        }

        tvPipCamera.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera(surface, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    private void clampPosition(WindowManager.LayoutParams params) {
        DisplayMetrics metrics = appContext.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        if (params.x < 0) params.x = 0;
        if (params.y < 50) params.y = 50; // Keep below notification status bar
        if (params.x + params.width > screenWidth) params.x = screenWidth - params.width;
        if (params.y + params.height > screenHeight - 60) params.y = screenHeight - params.height - 60;
    }

    // =========================================================================
    // Camera2 Subsystem
    // =========================================================================
    private void startCameraThread() {
        if (cameraThread == null) {
            cameraThread = new HandlerThread("KimLive-CameraThread");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
        }
    }

    private void stopCameraThread() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try {
                cameraThread.join(500);
            } catch (InterruptedException ignored) {}
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void openCamera(SurfaceTexture surfaceTexture, int viewWidth, int viewHeight) {
        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        CameraManager cm = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String selectedId = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (isFrontCamera && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    selectedId = id;
                    Integer orientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    sensorOrientation = orientation != null ? orientation : 270;
                    break;
                } else if (!isFrontCamera && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    selectedId = id;
                    Integer orientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    sensorOrientation = orientation != null ? orientation : 90;
                    break;
                }
            }

            if (selectedId == null && cm.getCameraIdList().length > 0) {
                selectedId = cm.getCameraIdList()[0];
            }
            if (selectedId == null) return;

            CameraCharacteristics cc = cm.getCameraCharacteristics(selectedId);
            StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight);
                surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            }

            configureTransform(viewWidth, viewHeight);

            cm.openCamera(selectedId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview(surfaceTexture);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    closeCamera();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "Camera device error: " + error);
                    closeCamera();
                }
            }, cameraHandler);

        } catch (Exception e) {
            Log.e(TAG, "Failed to open camera: ", e);
        }
    }

    private void startPreview(SurfaceTexture surfaceTexture) {
        if (cameraDevice == null) return;
        try {
            Surface surface = new Surface(surfaceTexture);
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        session.setRepeatingRequest(builder.build(), null, cameraHandler);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start camera repeating preview request", e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Camera capture session configuration failed");
                }
            }, cameraHandler);

        } catch (Exception e) {
            Log.e(TAG, "Error starting camera preview", e);
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (tvPipCamera == null || previewSize == null || viewWidth <= 0 || viewHeight <= 0) return;

        mainHandler.post(() -> {
            try {
                Matrix matrix = new Matrix();
                RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
                float centerX = viewRect.centerX();
                float centerY = viewRect.centerY();

                boolean isLandscape = (currentRotationDegrees == 90 || currentRotationDegrees == 270);
                float bufW = isLandscape ? previewSize.getWidth() : previewSize.getHeight();
                float bufH = isLandscape ? previewSize.getHeight() : previewSize.getWidth();

                RectF bufferRect = new RectF(0, 0, bufW, bufH);
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

                float scale = Math.max(
                        (float) viewWidth / bufW,
                        (float) viewHeight / bufH
                );
                matrix.postScale(scale, scale, centerX, centerY);

                if (currentRotationDegrees != 0) {
                    matrix.postRotate(currentRotationDegrees, centerX, centerY);
                }

                if (isFrontCamera) {
                    // Mirror horizontally for selfie orientation
                    matrix.postScale(-1, 1, centerX, centerY);
                }

                tvPipCamera.setTransform(matrix);
            } catch (Exception ignored) {}
        });
    }

    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        if (choices == null || choices.length == 0) {
            return new Size(640, 480);
        }
        // Prefer standard 640x480 or 1280x720 for optimal performance & heat management
        for (Size size : choices) {
            if (size.getWidth() == 640 && size.getHeight() == 480) {
                return size;
            }
        }
        for (Size size : choices) {
            if (size.getWidth() == 1280 && size.getHeight() == 720) {
                return size;
            }
        }
        return choices[0];
    }

    private void closeCamera() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                captureSession.close();
            } catch (Exception ignored) {}
            captureSession = null;
        }
        if (cameraDevice != null) {
            try {
                cameraDevice.close();
            } catch (Exception ignored) {}
            cameraDevice = null;
        }
    }
}
