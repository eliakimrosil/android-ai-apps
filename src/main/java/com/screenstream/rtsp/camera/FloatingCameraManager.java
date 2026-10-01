package com.screenstream.rtsp.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.screenstream.rtsp.R;

import java.util.Collections;

public class FloatingCameraManager implements TextureView.SurfaceTextureListener {

    private static final String TAG = "FloatingCameraManager";

    public interface OnOverlayStateChangeListener {
        void onOverlayVisibilityChanged(boolean visible);
        void onOverlayParamsChanged(boolean front, boolean flipped, int userRotation, int sizeIndex);
    }

    private static FloatingCameraManager instance;

    public static synchronized FloatingCameraManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatingCameraManager(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final WindowManager windowManager;
    private final CameraManager cameraManager;
    private final Handler mainHandler;

    private View floatingView;
    private TextureView textureView;
    private LinearLayout layoutControls;
    private View btnClose;
    private WindowManager.LayoutParams windowParams;

    // Sizing presets (Small: 120dp, Medium: 160dp, Large: 200dp)
    private static final int[] PRESET_SIZES_DP = {120, 160, 200};
    private int sizeIndex = 1; // Default: Medium (160dp)
    private int currentSizePx;

    private boolean isFrontCamera = true;
    private boolean isFlippedHorizontal = true; // Front camera defaults to mirrored
    private int userRotation = 0; // Manual rotation offset: 0, 90, 180, 270 degrees
    private boolean isShowing = false;
    private volatile boolean initialTransformApplied = false;
    private int lastX = -1;
    private int lastY = -1;

    // Camera2 variables
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Size previewSize;
    private int sensorOrientation = 270;

    // Auto-hide controls
    private final Runnable autoHideControlsRunnable = () -> {
        hideControls();
    };

    private DisplayManager displayManager;
    private DisplayManager.DisplayListener displayListener;
    private OrientationEventListener orientationEventListener;
    private OnOverlayStateChangeListener stateChangeListener;

    private FloatingCameraManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.currentSizePx = dpToPx(PRESET_SIZES_DP[sizeIndex]);
    }

    public void setStateChangeListener(OnOverlayStateChangeListener listener) {
        this.stateChangeListener = listener;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    public void setFrontCamera(boolean front) {
        if (this.isFrontCamera != front) {
            this.isFrontCamera = front;
            this.isFlippedHorizontal = front; // Front defaults to mirrored, back to normal
        }
    }

    public boolean isFlipped() {
        return isFlippedHorizontal;
    }

    public void setFlipped(boolean flipped) {
        this.isFlippedHorizontal = flipped;
    }

    public int getUserRotation() {
        return userRotation;
    }

    public void setUserRotation(int rotation) {
        this.userRotation = ((rotation % 360) + 360) % 360;
    }

    public int getSizeIndex() {
        return sizeIndex;
    }

    public void setSizeIndex(int index) {
        if (index >= 0 && index < PRESET_SIZES_DP.length) {
            this.sizeIndex = index;
            this.currentSizePx = dpToPx(PRESET_SIZES_DP[sizeIndex]);
        }
    }

    private boolean isCircular = false;

    public boolean isCircular() {
        return isCircular;
    }

    public void setCircular(boolean circular) {
        this.isCircular = circular;
        applyMaskShape();
    }

    public void toggleCircular() {
        this.isCircular = !this.isCircular;
        applyMaskShape();
    }

    public void applyMaskShape() {
        if (floatingView != null) {
            floatingView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    if (isCircular) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    } else {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(18));
                    }
                }
            });
            floatingView.setClipToOutline(true);
            floatingView.invalidateOutline();
        }
    }

    public void toggleFlip() {
        isFlippedHorizontal = !isFlippedHorizontal;
        configureTransform();
        notifyParamsChanged();
    }

    public void cycleRotation() {
        userRotation = (userRotation + 90) % 360;
        configureTransform();
        notifyParamsChanged();
    }

    public void cycleSize() {
        sizeIndex = (sizeIndex + 1) % PRESET_SIZES_DP.length;
        currentSizePx = dpToPx(PRESET_SIZES_DP[sizeIndex]);

        if (windowParams != null) {
            windowParams.width = currentSizePx;
            windowParams.height = currentSizePx;
            clampPosition(windowParams, getDisplayMetrics());

            if (floatingView != null && floatingView.isAttachedToWindow()) {
                windowManager.updateViewLayout(floatingView, windowParams);
            }
        }
        configureTransform(currentSizePx, currentSizePx);
        notifyParamsChanged();
    }

    private void notifyParamsChanged() {
        if (stateChangeListener != null) {
            stateChangeListener.onOverlayParamsChanged(isFrontCamera, isFlippedHorizontal, userRotation, sizeIndex);
        }
    }

    /**
     * Show the floating facecam overlay window.
     */
    public boolean show() {
        if (isShowing) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Please enable 'Display over other apps' permission", Toast.LENGTH_LONG).show();
            return false;
        }

        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Camera permission is required", Toast.LENGTH_LONG).show();
            return false;
        }

        initialTransformApplied = false;
        startCameraThread();
        initOverlayView();

        try {
            windowManager.addView(floatingView, windowParams);
            isShowing = true;
            startOrientationListener();
            if (stateChangeListener != null) {
                stateChangeListener.onOverlayVisibilityChanged(true);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to add floating view to WindowManager", e);
            hide();
            return false;
        }
    }

    /**
     * Hide and clean up the floating facecam overlay.
     */
    public void hide() {
        if (!isShowing && floatingView == null) return;
        isShowing = false;
        initialTransformApplied = false;

        stopOrientationListener();
        closeCamera();
        stopCameraThread();

        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {}
            floatingView = null;
        }

        if (stateChangeListener != null) {
            stateChangeListener.onOverlayVisibilityChanged(false);
        }
    }

    private void initOverlayView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        floatingView = inflater.inflate(R.layout.layout_floating_camera, null);
        textureView = floatingView.findViewById(R.id.camera_texture_view);
        layoutControls = floatingView.findViewById(R.id.layout_camera_controls);
        btnClose = floatingView.findViewById(R.id.btn_close_camera);

        textureView.setSurfaceTextureListener(this);

        // Control Pill Buttons
        View btnSwitch = floatingView.findViewById(R.id.btn_switch_camera);
        View btnFlip = floatingView.findViewById(R.id.btn_flip_camera);
        View btnRotate = floatingView.findViewById(R.id.btn_rotate_camera);
        View btnResize = floatingView.findViewById(R.id.btn_resize_camera);

        if (btnSwitch != null) {
            btnSwitch.setOnClickListener(v -> {
                isFrontCamera = !isFrontCamera;
                isFlippedHorizontal = isFrontCamera; // sensible default
                resetAutoHideTimer();
                reopenCamera();
                notifyParamsChanged();
                Toast.makeText(context, isFrontCamera ? "Front Camera" : "Back Camera", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnFlip != null) {
            btnFlip.setOnClickListener(v -> {
                toggleFlip();
                resetAutoHideTimer();
                Toast.makeText(context, isFlippedHorizontal ? "Flip: Mirrored" : "Flip: Normal", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnRotate != null) {
            btnRotate.setOnClickListener(v -> {
                cycleRotation();
                resetAutoHideTimer();
                Toast.makeText(context, "Rotation: " + userRotation + "°", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnResize != null) {
            btnResize.setOnClickListener(v -> {
                cycleSize();
                resetAutoHideTimer();
                String[] names = {"Small (120dp)", "Medium (160dp)", "Large (200dp)"};
                Toast.makeText(context, "Size: " + names[sizeIndex], Toast.LENGTH_SHORT).show();
            });
            btnResize.setOnLongClickListener(v -> {
                toggleCircular();
                resetAutoHideTimer();
                Toast.makeText(context, isCircular ? "Shape: Circle ⚪" : "Shape: Rounded Square ⬛", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> hide());
        }

        floatingView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> applyMaskShape());
        setupTouchAndDrag();

        // Calculate Window Parameters
        DisplayMetrics dm = getDisplayMetrics();
        currentSizePx = dpToPx(PRESET_SIZES_DP[sizeIndex]);

        if (lastX < 0 || lastY < 0) {
            lastX = dm.widthPixels - currentSizePx - dpToPx(16);
            lastY = dpToPx(60);
        }

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        windowParams = new WindowManager.LayoutParams(
                currentSizePx,
                currentSizePx,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        windowParams.gravity = Gravity.TOP | Gravity.START;
        clampPosition(windowParams, dm);

        resetAutoHideTimer();
    }

    private void setupTouchAndDrag() {
        textureView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowParams.x;
                        initialY = windowParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            windowParams.x = (int) (initialX + dx);
                            windowParams.y = (int) (initialY + dy);
                            clampPosition(windowParams, getDisplayMetrics());
                            if (floatingView != null && floatingView.isAttachedToWindow()) {
                                windowManager.updateViewLayout(floatingView, windowParams);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            // Tap: Toggle / show controls
                            boolean controlsVisible = (layoutControls != null && layoutControls.getVisibility() == View.VISIBLE && layoutControls.getAlpha() > 0.2f)
                                    || (btnClose != null && btnClose.getVisibility() == View.VISIBLE && btnClose.getAlpha() > 0.2f);
                            if (controlsVisible) {
                                hideControls();
                            } else {
                                showControls();
                            }
                        } else {
                            lastX = windowParams.x;
                            lastY = windowParams.y;
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showControls() {
        mainHandler.removeCallbacks(autoHideControlsRunnable);
        if (layoutControls != null) {
            layoutControls.setVisibility(View.VISIBLE);
            layoutControls.animate().alpha(1.0f).setDuration(200).start();
        }
        if (btnClose != null) {
            btnClose.setVisibility(View.VISIBLE);
            btnClose.animate().alpha(1.0f).setDuration(200).start();
        }
        mainHandler.postDelayed(autoHideControlsRunnable, 3500);
    }

    private void hideControls() {
        mainHandler.removeCallbacks(autoHideControlsRunnable);
        if (layoutControls != null) {
            layoutControls.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
                if (layoutControls != null && layoutControls.getAlpha() == 0.0f) {
                    layoutControls.setVisibility(View.GONE);
                }
            }).start();
        }
        if (btnClose != null) {
            btnClose.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
                if (btnClose != null && btnClose.getAlpha() == 0.0f) {
                    btnClose.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    private void resetAutoHideTimer() {
        showControls();
    }

    private void clampPosition(WindowManager.LayoutParams params, DisplayMetrics dm) {
        int maxX = Math.max(0, dm.widthPixels - params.width);
        int maxY = Math.max(0, dm.heightPixels - params.height);
        params.x = Math.max(0, Math.min(params.x, maxX));
        params.y = Math.max(0, Math.min(params.y, maxY));
    }

    // ==========================================
    // Camera2 Pipeline
    // ==========================================

    private void startCameraThread() {
        if (cameraThread == null) {
            cameraThread = new HandlerThread("FloatingCameraThread");
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

    public void reopenCamera() {
        if (!isShowing) return;
        initialTransformApplied = false;
        if (cameraHandler != null) {
            cameraHandler.post(() -> {
                closeCamera();
                openCamera();
            });
        }
    }

    private void openCamera() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            String targetFacing = isFrontCamera
                    ? String.valueOf(CameraCharacteristics.LENS_FACING_FRONT)
                    : String.valueOf(CameraCharacteristics.LENS_FACING_BACK);

            String selectedId = null;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cc = cameraManager.getCameraCharacteristics(id);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && String.valueOf(facing).equals(targetFacing)) {
                    selectedId = id;
                    Integer orient = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    sensorOrientation = (orient != null) ? orient : 270;
                    chooseOptimalPreviewSize(cc);
                    break;
                }
            }

            if (selectedId == null && cameraManager.getCameraIdList().length > 0) {
                selectedId = cameraManager.getCameraIdList()[0];
            }

            if (selectedId != null) {
                cameraManager.openCamera(selectedId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        cameraDevice = camera;
                        createCameraPreviewSession();
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        camera.close();
                        cameraDevice = null;
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        camera.close();
                        cameraDevice = null;
                        Log.e(TAG, "CameraDevice error: " + error);
                    }
                }, cameraHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access camera", e);
        }
    }

    private void chooseOptimalPreviewSize(CameraCharacteristics characteristics) {
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return;

        Size[] choices = map.getOutputSizes(SurfaceTexture.class);
        if (choices == null || choices.length == 0) {
            previewSize = new Size(1280, 720);
            return;
        }

        // Check if there is a native 1:1 square output (e.g. 1080x1080)
        Size bestSquare = null;
        for (Size s : choices) {
            if (s.getWidth() == s.getHeight() && s.getWidth() <= 1080 && s.getWidth() >= 480) {
                if (bestSquare == null || s.getWidth() > bestSquare.getWidth()) {
                    bestSquare = s;
                }
            }
        }
        if (bestSquare != null) {
            previewSize = bestSquare;
            Log.d(TAG, "chooseOptimalPreviewSize: using native 1:1 size " + previewSize);
            return;
        }

        // Prefer 1280x720 (720p 16:9) for optimal performance and thermal efficiency
        for (Size s : choices) {
            if (s.getWidth() == 1280 && s.getHeight() == 720) {
                previewSize = s;
                return;
            }
        }

        // Otherwise pick the closest 16:9 resolution under 1920
        for (Size s : choices) {
            float aspect = (float) s.getWidth() / (float) s.getHeight();
            if (Math.abs(aspect - (16.0f / 9.0f)) < 0.05f && s.getWidth() <= 1920 && s.getWidth() >= 640) {
                previewSize = s;
                return;
            }
        }

        previewSize = choices[0];
    }

    private void createCameraPreviewSession() {
        if (cameraDevice == null || textureView == null || !textureView.isAvailable()) {
            return;
        }

        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture == null) return;

            if (previewSize != null) {
                surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            }
            Surface surface = new Surface(surfaceTexture);

            final CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(surface);
            requestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
                        mainHandler.post(() -> configureTransform());
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Failed to start camera preview", e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "CameraCaptureSession configuration failed");
                }
            }, cameraHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create preview session", e);
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            try {
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

    // ==========================================
    // Isotropic Center-Crop Matrix (Zero Stretch)
    // ==========================================

    public void configureTransform() {
        if (textureView == null) return;
        int w = textureView.getWidth();
        int h = textureView.getHeight();
        if (w <= 0 || h <= 0) {
            w = currentSizePx;
            h = currentSizePx;
        }
        configureTransform(w, h);
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || previewSize == null || !textureView.isAvailable()) {
            return;
        }

        Display display = windowManager.getDefaultDisplay();
        int displayRotation = (display != null) ? display.getRotation() : Surface.ROTATION_0;
        int displayDegrees = 0;
        switch (displayRotation) {
            case Surface.ROTATION_0: displayDegrees = 0; break;
            case Surface.ROTATION_90: displayDegrees = 90; break;
            case Surface.ROTATION_180: displayDegrees = 180; break;
            case Surface.ROTATION_270: displayDegrees = 270; break;
        }

        int baseRotation;
        if (isFrontCamera) {
            baseRotation = (sensorOrientation + displayDegrees) % 360;
            baseRotation = (360 - baseRotation) % 360;
        } else {
            baseRotation = (sensorOrientation - displayDegrees + 360) % 360;
        }

        int totalRotation = (baseRotation + userRotation) % 360;
        if (totalRotation < 0) totalRotation += 360;

        float bufW = previewSize.getWidth();
        float bufH = previewSize.getHeight();
        float viewW = viewWidth;
        float viewH = viewHeight;

        // Effective dimensions of camera buffer on screen after rotation
        float effectiveBufW = (totalRotation == 90 || totalRotation == 270) ? bufH : bufW;
        float effectiveBufH = (totalRotation == 90 || totalRotation == 270) ? bufW : bufH;

        // Strict 1:1 Center-Crop: isotropic scaling factor that completely covers the 1:1 square
        float scale = Math.max(viewW / effectiveBufW, viewH / effectiveBufH);

        // Normalize TextureView's default buffer-to-view quad stretch back to uniform 1:1
        float sx = (bufW / viewW) * scale;
        float sy = (bufH / viewH) * scale;

        Log.d(TAG, "configureTransform: isFront=" + isFrontCamera + " isFlipped=" + isFlippedHorizontal +
                " userRot=" + userRotation + " baseRot=" + baseRotation + " totalRot=" + totalRotation +
                " view=" + viewW + "x" + viewH + " buf=" + bufW + "x" + bufH + " sx=" + sx + " sy=" + sy);

        Matrix matrix = new Matrix();
        float cx = viewW / 2.0f;
        float cy = viewH / 2.0f;

        // 1. Isotropic scale (zero stretch, strictly 1:1)
        matrix.setScale(sx, sy, cx, cy);

        // 2. Rotate to upright orientation + user rotation
        matrix.postRotate(totalRotation, cx, cy);

        // 3. Flip horizontally if enabled (mirror mode)
        if (isFlippedHorizontal) {
            matrix.postScale(-1.0f, 1.0f, cx, cy);
        }

        Runnable applyTransform = () -> {
            if (textureView != null && textureView.isAvailable()) {
                textureView.setTransform(matrix);
                textureView.invalidate();
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyTransform.run();
        } else {
            mainHandler.post(applyTransform);
        }
    }

    // ==========================================
    // TextureView & Orientation Callbacks
    // ==========================================

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (cameraHandler != null) {
            cameraHandler.post(this::openCamera);
        }
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
        if (!initialTransformApplied) {
            initialTransformApplied = true;
            mainHandler.post(this::configureTransform);
        }
    }

    private void startOrientationListener() {
        if (displayManager == null) {
            displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        }
        if (displayListener == null && displayManager != null) {
            displayListener = new DisplayManager.DisplayListener() {
                private int lastRotation = -1;

                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    if (windowManager == null) return;
                    Display disp = windowManager.getDefaultDisplay();
                    if (disp == null) return;
                    int curRotation = disp.getRotation();
                    if (curRotation != lastRotation) {
                        lastRotation = curRotation;
                        Log.d(TAG, "DisplayListener.onDisplayChanged: rotation=" + curRotation);
                        clampPosition(windowParams, getDisplayMetrics());
                        if (floatingView != null && floatingView.isAttachedToWindow()) {
                            windowManager.updateViewLayout(floatingView, windowParams);
                        }
                        configureTransform();
                    }
                }
            };
            displayManager.registerDisplayListener(displayListener, mainHandler);
        }

        if (orientationEventListener == null) {
            orientationEventListener = new OrientationEventListener(context) {
                private int lastRotation = -1;

                @Override
                public void onOrientationChanged(int orientation) {
                    if (windowManager == null) return;
                    Display disp = windowManager.getDefaultDisplay();
                    if (disp == null) return;
                    int curRotation = disp.getRotation();
                    if (curRotation != lastRotation) {
                        lastRotation = curRotation;
                        Log.d(TAG, "OrientationEventListener.onOrientationChanged: rotation=" + curRotation);
                        clampPosition(windowParams, getDisplayMetrics());
                        if (floatingView != null && floatingView.isAttachedToWindow()) {
                            windowManager.updateViewLayout(floatingView, windowParams);
                        }
                        configureTransform();
                    }
                }
            };
        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    private void stopOrientationListener() {
        if (displayManager != null && displayListener != null) {
            displayManager.unregisterDisplayListener(displayListener);
            displayListener = null;
        }
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
    }

    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        return dm;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()
        );
    }
}
