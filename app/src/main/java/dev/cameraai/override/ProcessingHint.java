package dev.cameraai.override;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Mode 175 only: non-invasive visual re-creation of the OEM tiled 200MP reveal.
 * Underlying MiVi/ArcSoft/Harbo, lens selection and saving remain untouched.
 * The exact OEM renderengine classes cannot simply be copied between APK versions.
 */
final class ProcessingHint {
    private static volatile WeakReference<Activity> active = new WeakReference<>(null);
    private static volatile WeakReference<FrameLayout> overlayRef = new WeakReference<>(null);
    private static volatile WeakReference<RevealCanvas> canvasRef = new WeakReference<>(null);
    private static volatile boolean processing;
    private static volatile boolean oemLook;
    private static volatile long token;
    private static final String TAG = "CameraAI_175_REVEAL";

    private ProcessingHint() {}

    static void setOEMLook(boolean value) { oemLook = value; }

    static void onCameraFocus(Activity activity, boolean focused, boolean pending) {
        if (!focused) {
            if (active.get() == activity) {
                // Leave no overlay in the app switcher.
                hide("FOCUS_LOST");
            }
            return;
        }
        active = new WeakReference<>(activity);
        if (pending && !processing) update(true, "FOCUS_PENDING");
    }

    static void update(boolean start, String reason) {
        if (!start) { hide(reason); return; }
        final long generation = ++token;
        processing = true;
        Activity activity = active.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Log.w(TAG, "NO_ACTIVITY for " + reason);
            return;
        }
        activity.runOnUiThread(() -> {
            if (generation != token || !processing || activity.isFinishing() || activity.isDestroyed()) return;
            try {
                FrameLayout root = activity.findViewById(android.R.id.content);
                if (root == null) return;
                FrameLayout overlay = overlayRef.get();
                RevealCanvas render = canvasRef.get();
                if (overlay == null || overlay.getParent() != root || render == null) {
                    if (overlay != null && overlay.getParent() instanceof ViewGroup)
                        ((ViewGroup) overlay.getParent()).removeView(overlay);
                    overlay = new FrameLayout(activity);
                    overlay.setClickable(false);
                    overlay.setFocusable(false);
                    render = new RevealCanvas(activity);
                    render.setClickable(false);
                    overlay.addView(render, new FrameLayout.LayoutParams(-1, -1));
                    TextView title = new TextView(activity);
                    title.setText("200MP görüntü işleniyor…");
                    title.setTextColor(Color.WHITE);
                    title.setTextSize(16f);
                    title.setGravity(Gravity.CENTER);
                    title.setShadowLayer(5f, 0, 2f, 0xC0000000);
                    title.setClickable(false);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        -1, dp(activity, 48), Gravity.TOP);
                    lp.topMargin = dp(activity, 80);
                    overlay.addView(title, lp);
                    root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
                    overlayRef = new WeakReference<>(overlay);
                    canvasRef = new WeakReference<>(render);
                }
                render.reset();
                Bitmap freeze = previewFrame(root);
                render.setPreview(freeze);
                overlay.setAlpha(1f);
                overlay.setVisibility(View.VISIBLE);
                Log.i(TAG, "REVEAL_START reason=" + reason + " freeze=" + (freeze != null));
                final long capturedToken = generation;
                overlay.postDelayed(() -> {
                    if (capturedToken == token && processing) hide("TIMEOUT_20S");
                }, 20000L);
            } catch (Throwable t) {
                Log.w(TAG, "REVEAL_SHOW_ERROR: " + t.getClass().getSimpleName());
            }
        });
    }

    /** Called after MiVi success. Delay the tile reveal until the JPEG is truly on disk. */
    static void finalImage(String pictureName) {
        final long generation = token;
        if (!processing) return;
        final String basename = pictureName == null ? "" : new File(pictureName).getName();
        if (!basename.startsWith("IMG_") || !basename.endsWith(".jpg")) {
            Log.w(TAG, "REVEAL_INVALID_NAME " + basename);
            hide("INVALID_FILENAME");
            return;
        }
        new Thread(() -> {
            try {
                File jpeg = new File("/storage/emulated/0/DCIM/Camera", basename);
                long lastSize = -1L;
                int stable = 0;
                long began = SystemClock.uptimeMillis();
                while (SystemClock.uptimeMillis() - began < 5000L && generation == token) {
                    long size = jpeg.isFile() ? jpeg.length() : 0L;
                    if (size > 100_000L && size == lastSize) stable++; else stable = 0;
                    lastSize = size;
                    if (stable >= 2) {
                        Bitmap result = decodeSampled(jpeg.getAbsolutePath());
                        if (result != null) {
                            showFinal(result, generation);
                            return;
                        }
                    }
                    Thread.sleep(120L);
                }
                Log.w(TAG, "REVEAL_JPEG_UNAVAILABLE file=" + basename);
                if (generation == token) hide("JPEG_UNAVAILABLE");
            } catch (Throwable t) {
                Log.w(TAG, "REVEAL_IO_ERROR " + t.getClass().getSimpleName());
                if (generation == token) hide("FILE_ERROR");
            }
        }, "CameraAI-200MP-reveal").start();
    }

    static void error(String reason) { hide(reason); }

    private static void showFinal(Bitmap image, long generation) {
        Activity activity = active.get();
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            image.recycle();
            hide("ACTIVITY_GONE");
            return;
        }
        activity.runOnUiThread(() -> {
            if (!processing || generation != token) {
                image.recycle();
                return;
            }
            RevealCanvas render = canvasRef.get();
            FrameLayout overlay = overlayRef.get();
            if (render == null || overlay == null) {
                image.recycle();
                hide("VIEW_GONE");
                return;
            }
            render.setResult(image);
            ValueAnimator tiles = ValueAnimator.ofFloat(0f, 1f);
            tiles.setDuration(3600L);
            tiles.addUpdateListener(a -> {
                if (generation == token) render.setProgress((Float)a.getAnimatedValue());
                else a.cancel();
            });
            tiles.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (generation != token) return;
                    overlay.animate().alpha(0f).setDuration(850L)
                        .withEndAction(() -> {
                            if (generation == token) hide("TILED_ANIMATION_END");
                        }).start();
                }
            });
            tiles.start();
            Log.i(TAG, "REVEAL_JPEG_READY bitmap=" + image.getWidth() + "x" + image.getHeight()
                + " duration=3600+850ms");
        });
    }

    private static Bitmap decodeSampled(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth < 200 || bounds.outHeight < 200) return null;
        int sample = 1;
        while (bounds.outWidth / sample > 1500 || bounds.outHeight / sample > 1500) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap image = BitmapFactory.decodeFile(path, opts);
        if (image == null) return null;
        try {
            ExifInterface exif = new ExifInterface(path);
            int rotate = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int angle = rotate == ExifInterface.ORIENTATION_ROTATE_90 ? 90 :
                (rotate == ExifInterface.ORIENTATION_ROTATE_180 ? 180 :
                (rotate == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0));
            if (angle != 0) {
                Matrix m = new Matrix();
                m.postRotate(angle);
                Bitmap oriented = Bitmap.createBitmap(image, 0, 0,
                    image.getWidth(), image.getHeight(), m, true);
                if (oriented != image) { image.recycle(); image = oriented; }
            }
        } catch (Throwable ignored) { /* Keep decoded sample if EXIF unavailable. */ }
        return image;
    }

    private static Bitmap previewFrame(View root) {
        TextureView texture = findTexture(root, 0);
        if (texture == null || !texture.isAvailable()) return null;
        try {
            // Small preview avoids allocating a full-resolution camera framebuffer.
            return texture.getBitmap(360, 640);
        } catch (Throwable t) {
            Log.i(TAG, "PREVIEW_SNAPSHOT_UNAVAILABLE " + t.getClass().getSimpleName());
            return null;
        }
    }

    private static TextureView findTexture(View view, int depth) {
        if (depth > 10) return null;
        if (view instanceof TextureView) {
            TextureView t = (TextureView)view;
            if (t.isAvailable() && t.getWidth() > 0) return t;
        }
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup)view;
            for (int i = 0; i < parent.getChildCount(); i++) {
                TextureView found = findTexture(parent.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void hide(String reason) {
        processing = false;
        token++;
        Activity activity = active.get();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            FrameLayout overlay = overlayRef.get();
            RevealCanvas render = canvasRef.get();
            if (overlay != null) {
                overlay.animate().cancel();
                if (overlay.getParent() instanceof ViewGroup)
                    ((ViewGroup)overlay.getParent()).removeView(overlay);
            }
            if (render != null) render.release();
            overlayRef = new WeakReference<>(null);
            canvasRef = new WeakReference<>(null);
            Log.i(TAG, "REVEAL_END reason=" + reason);
        });
    }

    private static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    private static final class RevealCanvas extends View {
        private final Paint soft = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint full = new Paint(Paint.FILTER_BITMAP_FLAG);
        private Bitmap preview;
        private Bitmap result;
        private float progress;

        RevealCanvas(Activity a) { super(a); setLayerType(View.LAYER_TYPE_HARDWARE, null); }

        void reset() { release(); progress = 0f; invalidate(); }
        void setPreview(Bitmap value) { preview = value; invalidate(); }
        void setResult(Bitmap value) { result = value; progress = 0f; invalidate(); }
        void setProgress(float value) { progress = value; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (preview != null && !preview.isRecycled()) {
                drawCrop(canvas, preview, new Rect(0,0,w,h), soft);
                canvas.drawColor(0x88000000);
            } else {
                canvas.drawColor(0x990A0C10);
            }
            if (result == null || result.isRecycled()) return;
            int columns = 8, rows = 12;
            float tw = (float)w/columns, th = (float)h/rows;
            for (int row=0;row<rows;row++) for(int col=0;col<columns;col++) {
                // Diagonal staggered tiles, progressively revealing the genuine JPEG.
                float threshold = 0.72f * ((row + col) / (float)(rows + columns - 2));
                float alpha = Math.max(0f, Math.min(1f, (progress-threshold) / 0.28f));
                if (alpha <= 0f) continue;
                int left = Math.round(col*tw), top=Math.round(row*th);
                int right = col==columns-1?w:Math.round((col+1)*tw);
                int bottom = row==rows-1?h:Math.round((row+1)*th);
                canvas.save();
                canvas.clipRect(left,top,right,bottom);
                full.setAlpha(Math.round(alpha*255));
                drawCrop(canvas,result,new Rect(0,0,w,h),full);
                canvas.restore();
            }
            full.setAlpha(255);
        }

        private void drawCrop(Canvas c, Bitmap b, Rect dest, Paint paint) {
            float sourceRatio=(float)b.getWidth()/b.getHeight();
            float targetRatio=(float)dest.width()/dest.height();
            Rect src;
            if (sourceRatio>targetRatio) {
                int sw=Math.max(1,Math.round(b.getHeight()*targetRatio));
                int x=(b.getWidth()-sw)/2;
                src=new Rect(x,0,x+sw,b.getHeight());
            } else {
                int sh=Math.max(1,Math.round(b.getWidth()/targetRatio));
                int y=(b.getHeight()-sh)/2;
                src=new Rect(0,y,b.getWidth(),y+sh);
            }
            c.drawBitmap(b,src,dest,paint);
        }

        void release() {
            if (preview != null && !preview.isRecycled()) preview.recycle();
            if (result != null && !result.isRecycled()) result.recycle();
            preview = null; result = null;
            invalidate();
        }
    }
}
