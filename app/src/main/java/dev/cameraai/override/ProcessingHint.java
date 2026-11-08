package dev.cameraai.override;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 200MP (mode 175) non-invasive status: caption + shutter progress spinner.
 * No preview snapshot, blur, mosaic, bitmap manipulation or HAL overrides.
 * Pixel175Tracer invokes start at PixelModule capture and finalImage when
 * MiVi finishes. The indicator ends after the final JPEG is saved on disk.
 */
final class ProcessingHint {
    private static final String TAG = "CameraAI_175_STATUS";
    private static final AtomicLong TOKEN = new AtomicLong();
    private static volatile WeakReference<Activity> active = new WeakReference<>(null);
    private static volatile WeakReference<FrameLayout> overlayRef = new WeakReference<>(null);
    private static volatile WeakReference<View> shutterRef = new WeakReference<>(null);
    private static volatile ValueAnimator spinnerAnimator;
    private static volatile boolean processing;
    private static volatile float shutterAlpha = 1f;

    private ProcessingHint() {}
    static void setOEMLook(boolean ignored) { /* minimal UI is always used */ }

    static void onCameraFocus(Activity activity, boolean focused, boolean pending) {
        if (!focused) {
            if (active.get() == activity) hide("FOCUS_LOST");
            return;
        }
        active = new WeakReference<>(activity);
        if (pending && !processing) update(true, "FOCUS_PENDING");
    }

    static void update(boolean start, String reason) {
        if (!start) { hide(reason); return; }
        final long generation = TOKEN.incrementAndGet();
        processing = true;
        final Activity activity = active.get();
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            processing = false;
            Log.w(TAG, "NO_ACTIVITY reason=" + reason);
            return;
        }
        activity.runOnUiThread(() -> {
            if (generation != TOKEN.get() || !processing ||
                activity.isDestroyed() || activity.isFinishing()) return;
            try {
                FrameLayout root = activity.findViewById(android.R.id.content);
                if (root == null || root.getWidth() == 0 || root.getHeight() == 0) {
                    Log.w(TAG, "STATUS_ROOT_UNAVAILABLE");
                    processing = false;
                    return;
                }
                removeOldUI();
                View shutter = findShutter(root, 0);
                Rect shutterArea = boundsInRoot(root, shutter);
                int diameter = dp(activity, 74);
                int cx = shutterArea != null ? shutterArea.centerX() : root.getWidth() / 2;
                int cy = shutterArea != null ? shutterArea.centerY() :
                    Math.round(root.getHeight() * 0.87f);

                FrameLayout overlay = new FrameLayout(activity);
                overlay.setBackgroundColor(Color.TRANSPARENT);
                overlay.setClickable(false);
                overlay.setFocusable(false);
                overlay.setClipChildren(false);

                TextView caption = new TextView(activity);
                caption.setText("200MP görüntü işleniyor…");
                caption.setTextSize(12.5f);
                caption.setTextColor(Color.WHITE);
                caption.setGravity(Gravity.CENTER);
                caption.setPadding(dp(activity, 14), dp(activity, 7),
                    dp(activity, 14), dp(activity, 7));
                caption.setClickable(false);
                GradientDrawable background = new GradientDrawable();
                background.setColor(0x99000000);
                background.setCornerRadius(dp(activity, 14));
                caption.setBackground(background);
                FrameLayout.LayoutParams textParams =
                    new FrameLayout.LayoutParams(-2, -2,
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                textParams.topMargin = Math.round(root.getHeight() * 0.22f);
                overlay.addView(caption, textParams);

                SpinRing ring = new SpinRing(activity);
                ring.setClickable(false);
                ring.setFocusable(false);
                FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(
                    diameter, diameter, Gravity.LEFT | Gravity.TOP);
                ringParams.leftMargin = Math.max(0, Math.min(root.getWidth()-diameter,
                    cx - diameter/2));
                ringParams.topMargin = Math.max(0, Math.min(root.getHeight()-diameter,
                    cy - diameter/2));
                overlay.addView(ring, ringParams);
                root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
                overlayRef = new WeakReference<>(overlay);

                if (shutter != null) {
                    shutterAlpha = shutter.getAlpha();
                    shutterRef = new WeakReference<>(shutter);
                    shutter.setAlpha(Math.min(0.55f, shutterAlpha));
                }

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 360f);
                animator.setDuration(1050L);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(a -> {
                    if (generation == TOKEN.get() && processing)
                        ring.setRotation((Float)a.getAnimatedValue());
                });
                spinnerAnimator = animator;
                animator.start();

                Log.i(TAG, "STATUS_V210_START reason=" + reason +
                    " shutterFound=" + (shutter != null) +
                    " previewUnchanged=true animation=SPINNER");
                overlay.postDelayed(() -> {
                    if (processing && TOKEN.get() == generation) hide("TIMEOUT_20S");
                }, 20000L);
            } catch (Throwable t) {
                Log.e(TAG, "STATUS_SHOW_FAILED " + t.getClass().getSimpleName(), t);
                hide("SHOW_FAILED");
            }
        });
    }

    /**
     * MiVi completion precedes the final JPEG write. Keep the spinner until
     * on-disk size stabilizes. Timeout always restores the normal camera UI.
     */
    static void finalImage(String pictureName) {
        final long generation = TOKEN.get();
        if (!processing) return;
        final String filename = pictureName == null ? "" : new File(pictureName).getName();
        if (!filename.startsWith("IMG_") || !filename.endsWith(".jpg")) {
            Log.w(TAG, "STATUS_BAD_FILENAME " + filename);
            hide("BAD_FILENAME");
            return;
        }
        new Thread(() -> {
            final File jpeg = new File("/storage/emulated/0/DCIM/Camera", filename);
            final long begin = SystemClock.uptimeMillis();
            long previousSize = -1;
            int stable = 0;
            try {
                while (generation == TOKEN.get() &&
                        SystemClock.uptimeMillis() - begin < 8000L) {
                    long size = jpeg.isFile() ? jpeg.length() : 0L;
                    stable = (size > 100000 && size == previousSize) ? stable + 1 : 0;
                    previousSize = size;
                    if (stable >= 3) {
                        Log.i(TAG, "STATUS_JPEG_SAVED file=" + filename +
                            " size=" + size);
                        if (generation == TOKEN.get()) hide("JPEG_SAVED");
                        return;
                    }
                    Thread.sleep(140L);
                }
                if (generation == TOKEN.get()) hide("JPEG_NOT_OBSERVED");
            } catch (Throwable t) {
                Log.w(TAG, "STATUS_FILE_CHECK_FAILED " +
                    t.getClass().getSimpleName());
                if (generation == TOKEN.get()) hide("FILE_CHECK_ERROR");
            }
        }, "CameraAI-200MP-status").start();
    }

    static void error(String reason) { hide(reason); }

    private static void hide(String reason) {
        processing = false;
        TOKEN.incrementAndGet();
        Activity activity = active.get();
        if (activity == null) {
            Log.i(TAG, "STATUS_END_NO_ACTIVITY reason=" + reason);
            return;
        }
        activity.runOnUiThread(() -> {
            removeOldUI();
            Log.i(TAG, "STATUS_END reason=" + reason);
        });
    }

    private static void removeOldUI() {
        ValueAnimator animator = spinnerAnimator;
        spinnerAnimator = null;
        if (animator != null) animator.cancel();

        FrameLayout overlay = overlayRef.get();
        overlayRef = new WeakReference<>(null);
        if (overlay != null && overlay.getParent() instanceof ViewGroup)
            ((ViewGroup) overlay.getParent()).removeView(overlay);

        View shutter = shutterRef.get();
        shutterRef = new WeakReference<>(null);
        if (shutter != null) shutter.setAlpha(shutterAlpha);
    }

    private static View findShutter(View candidate, int depth) {
        if (candidate == null || depth > 20) return null;
        String name = candidate.getClass().getName();
        if ((name.contains("CameraSnapView") || name.endsWith(".SnapView")) &&
            candidate.getVisibility() == View.VISIBLE &&
            candidate.getWidth() > 0 && candidate.getHeight() > 0)
            return candidate;
        if (candidate instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)candidate;
            for (int i=0; i<group.getChildCount(); i++) {
                View found = findShutter(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Rect boundsInRoot(FrameLayout root, View child) {
        if (child == null) return null;
        int[] origin = new int[2];
        int[] position = new int[2];
        root.getLocationOnScreen(origin);
        child.getLocationOnScreen(position);
        return new Rect(position[0] - origin[0], position[1] - origin[1],
            position[0] - origin[0] + child.getWidth(),
            position[1] - origin[1] + child.getHeight());
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** Non-clickable spinner over the existing shutter (not a replacement button). */
    private static final class SpinRing extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        SpinRing(Activity activity) {
            super(activity);
            float density = activity.getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(2.5f * density);
            track.setColor(0x55FFFFFF);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeWidth(3.2f * density);
            stroke.setColor(Color.WHITE);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float padding = Math.max(stroke.getStrokeWidth()*1.5f, 6f);
            bounds.set(padding, padding, getWidth()-padding, getHeight()-padding);
            canvas.drawArc(bounds, 0f, 360f, false, track);
            canvas.drawArc(bounds, -85f, 225f, false, stroke);
        }
    }
}
