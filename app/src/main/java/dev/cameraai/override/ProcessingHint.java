package dev.cameraai.override;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.SurfaceView;
import android.view.PixelCopy;
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
    private static volatile WeakReference<View> shutterRef = new WeakReference<>(null);
    private static volatile float shutterPreviousAlpha = 1f;

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
                if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;

                FrameLayout existing = overlayRef.get();
                if (existing != null && existing.getParent() instanceof ViewGroup)
                    ((ViewGroup) existing.getParent()).removeView(existing);
                RevealCanvas previous = canvasRef.get();
                if (previous != null) previous.release();

                // A genuine preview snapshot before adding our overlay.
                Bitmap frozen = previewFrame(root);
                Rect viewport = findPreviewBounds(root);
                FrameLayout overlay = new FrameLayout(activity);
                overlay.setClickable(false);
                overlay.setFocusable(false);
                overlay.setBackgroundColor(Color.TRANSPARENT);

                FrameLayout previewLayer = new FrameLayout(activity);
                previewLayer.setClickable(false);
                previewLayer.setFocusable(false);
                previewLayer.setClipChildren(true);
                previewLayer.setClipToPadding(true);

                RevealCanvas render = new RevealCanvas(activity);
                render.setClickable(false);
                previewLayer.addView(render, new FrameLayout.LayoutParams(-1, -1));

                TextView title = new TextView(activity);
                title.setText("200MP görüntü işleniyor...");
                title.setTextColor(Color.WHITE);
                title.setTextSize(11.5f);
                title.setGravity(Gravity.CENTER);
                title.setPadding(dp(activity, 12), dp(activity, 5),
                                 dp(activity, 12), dp(activity, 5));
                title.setClickable(false);
                GradientDrawable bubble = new GradientDrawable();
                bubble.setColor(0x76000000);
                bubble.setCornerRadius(dp(activity, 14));
                title.setBackground(bubble);
                FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                    -2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                titleParams.topMargin = dp(activity, 10);
                previewLayer.addView(title, titleParams);

                FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(
                    viewport.width(), viewport.height(), Gravity.LEFT | Gravity.TOP);
                previewParams.leftMargin = viewport.left;
                previewParams.topMargin = viewport.top;
                overlay.addView(previewLayer, previewParams);
                root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

                overlayRef = new WeakReference<>(overlay);
                canvasRef = new WeakReference<>(render);
                render.reset();
                if (frozen != null) render.setPreview(soften(frozen));
                else requestSurfacePreview(root, render, generation);
                dimShutter(root);
                overlay.setVisibility(View.VISIBLE);
                overlay.setAlpha(1f);

                Log.i(TAG, "REVEAL_V26_START reason=" + reason
                    + " preview=" + viewport.toShortString()
                    + " frozen=" + (frozen != null)
                    + " controls=UNOBSCURED");
                overlay.postDelayed(() -> {
                    if (generation == token && processing) hide("TIMEOUT_20S");
                }, 20000L);
            } catch (Throwable t) {
                Log.w(TAG, "REVEAL_SHOW_ERROR: " + t.getClass().getSimpleName());
                hide("UI_ERROR");
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
            // Both layers depict the SAME saved JPEG: softened first, then
            // each of four quadrants becomes sharp. Avoid preview/JPEG drift.
            final Bitmap blurredStill;
            try {
                Bitmap scaled = Bitmap.createScaledBitmap(image,
                    Math.max(64, image.getWidth() / 2),
                    Math.max(64, image.getHeight() / 2), true);
                blurredStill = soften(scaled);
            } catch (Throwable t) {
                Log.w(TAG, "REVEAL_FOUR_BLUR_FALLBACK " + t.getClass().getSimpleName());
                blurredStill = null;
            }
            render.setResult(image, blurredStill);
            ValueAnimator tiles = ValueAnimator.ofFloat(0f, 1f);
            tiles.setDuration(3200L);
            tiles.addUpdateListener(a -> {
                if (generation == token) render.setProgress((Float)a.getAnimatedValue());
                else a.cancel();
            });
            tiles.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (generation != token) return;
                    // Do not crossfade the JPEG over the moving camera preview:
                    // the two frames differ and produce visible double images.
                    // At 100% tiled reveal the still is complete, so restore UI
                    // in one frame instead of transparency-blending two scenes.
                    hide("TILED_ANIMATION_END");
                }
            });
            tiles.start();
            Log.i(TAG, "REVEAL_JPEG_READY bitmap=" + image.getWidth() + "x" + image.getHeight()
                + " four_quadrants=true source=SAVED_JPEG blur=SAME_PHOTO");
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

    /** Fallback for GL/SurfaceView-based Xiaomi preview; no HAL interaction. */
    private static void requestSurfacePreview(View root, RevealCanvas render, long generation) {
        SurfaceView surface = findSurface(root, 0);
        if (surface == null || surface.getHolder() == null
                || surface.getHolder().getSurface() == null
                || !surface.getHolder().getSurface().isValid()) {
            Log.i(TAG, "REVEAL_SNAPSHOT_UNAVAILABLE no_valid_surface");
            return;
        }
        final Bitmap snapshot;
        try {
            snapshot = Bitmap.createBitmap(160, 284, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            Log.w(TAG, "REVEAL_SNAPSHOT_ALLOC_FAILED " + t.getClass().getSimpleName());
            return;
        }
        try {
            PixelCopy.request(surface, snapshot, result -> {
                if (generation == token && processing && result == PixelCopy.SUCCESS) {
                    render.setPreview(soften(snapshot));
                    Log.i(TAG, "REVEAL_SNAPSHOT_PIXELCOPY_OK");
                } else {
                    snapshot.recycle();
                    Log.i(TAG, "REVEAL_SNAPSHOT_PIXELCOPY_FAILED code=" + result);
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (Throwable t) {
            snapshot.recycle();
            Log.w(TAG, "REVEAL_SNAPSHOT_COPY_ERROR " + t.getClass().getSimpleName());
        }
    }

    private static SurfaceView findSurface(View view, int depth) {
        if (depth > 12) return null;
        if (view instanceof SurfaceView) {
            SurfaceView surface = (SurfaceView) view;
            if (surface.getWidth() > 100 && surface.getHeight() > 100) return surface;
        }
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup)view;
            SurfaceView largest = null;
            int largestArea = 0;
            for (int i=0; i<parent.getChildCount(); i++) {
                SurfaceView child = findSurface(parent.getChildAt(i), depth+1);
                if (child == null) continue;
                int area = child.getWidth() * child.getHeight();
                if (area > largestArea) { largestArea = area; largest = child; }
            }
            return largest;
        }
        return null;
    }


    /** Restrict render only to the real camera preview, NEVER the bottom controls. */
    private static Rect findPreviewBounds(FrameLayout root) {
        int width = root.getWidth(), height = root.getHeight();
        View target = findSurface(root, 0);
        if (target == null) target = findTexture(root, 0);
        Rect measured = new Rect();
        if (target != null && target.getWidth() > 0 && target.getHeight() > 0) {
            int[] origin = new int[2];
            int[] child = new int[2];
            root.getLocationOnScreen(origin);
            target.getLocationOnScreen(child);
            measured.set(child[0]-origin[0], child[1]-origin[1],
                child[0]-origin[0]+target.getWidth(),
                child[1]-origin[1]+target.getHeight());
            if (!measured.intersect(new Rect(0, 0, width, height)))
                measured.setEmpty();
        }
        if (height > width) {
            // Mode 175 is a portrait 200MP viewfinder. Some vendor SurfaceViews
            // intentionally occupy the whole activity behind the shutter.
            // Do not draw above the toolbar or over the bottom controls.
            int fallbackTop = Math.round(height * 0.180f);
            int fallbackBottom = Math.round(height * 0.775f);
            boolean valid = !measured.isEmpty()
                && measured.height() >= height * 0.38f
                && measured.height() <= height * 0.70f
                && measured.top >= height * 0.10f
                && measured.bottom <= height * 0.81f;
            Rect result = valid ? measured :
                new Rect(0, fallbackTop, width, fallbackBottom);
            result.intersect(new Rect(0, fallbackTop, width, fallbackBottom));
            return result;
        }
        if (!measured.isEmpty()) return measured;
        return new Rect(0, 0, width, height);
    }

    /**
     * True linear-time, two-pass RGB box blur of the preview snapshot.
     * The old downscale-by-11 + bilinear upscale created giant visible pixels
     * on 1440px screens. This path preserves the source resolution and blurs
     * spatial colors without any RAW, camera stream or HAL modifications.
     */
    private static Bitmap soften(Bitmap bitmap) {
        if (bitmap == null) return null;
        final int width = bitmap.getWidth(), height = bitmap.getHeight();
        if (width < 30 || height < 30) return bitmap;
        try {
            final int radius = Math.max(5, Math.min(16, Math.round(width * 0.055f)));
            final int window = radius * 2 + 1;
            final int[] pixels = new int[width * height];
            final int[] horizontal = new int[width * height];
            final int[] vertical = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            for (int y = 0; y < height; y++) {
                final int base = y * width;
                int sumR = 0, sumG = 0, sumB = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int x = Math.max(0, Math.min(width - 1, offset));
                    int pixel = pixels[base + x];
                    sumR += (pixel >> 16) & 255;
                    sumG += (pixel >> 8) & 255;
                    sumB += pixel & 255;
                }
                for (int x = 0; x < width; x++) {
                    horizontal[base + x] = 0xff000000
                        | ((sumR / window) << 16) | ((sumG / window) << 8)
                        | (sumB / window);
                    int removeIndex = Math.max(0, x - radius);
                    int addIndex = Math.min(width - 1, x + radius + 1);
                    int remove = pixels[base + removeIndex];
                    int add = pixels[base + addIndex];
                    sumR += ((add >> 16) & 255) - ((remove >> 16) & 255);
                    sumG += ((add >> 8) & 255) - ((remove >> 8) & 255);
                    sumB += (add & 255) - (remove & 255);
                }
            }
            for (int x = 0; x < width; x++) {
                int sumR = 0, sumG = 0, sumB = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int y = Math.max(0, Math.min(height - 1, offset));
                    int pixel = horizontal[y * width + x];
                    sumR += (pixel >> 16) & 255;
                    sumG += (pixel >> 8) & 255;
                    sumB += pixel & 255;
                }
                for (int y = 0; y < height; y++) {
                    vertical[y * width + x] = 0xff000000
                        | ((sumR / window) << 16) | ((sumG / window) << 8)
                        | (sumB / window);
                    int removeY = Math.max(0, y - radius);
                    int addY = Math.min(height - 1, y + radius + 1);
                    int remove = horizontal[removeY * width + x];
                    int add = horizontal[addY * width + x];
                    sumR += ((add >> 16) & 255) - ((remove >> 16) & 255);
                    sumG += ((add >> 8) & 255) - ((remove >> 8) & 255);
                    sumB += (add & 255) - (remove & 255);
                }
            }
            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            output.setPixels(vertical, 0, width, 0, 0, width, height);
            bitmap.recycle();
            Log.i(TAG, "REVEAL_BLUR_V28_REAL_BOX radius=" + radius
                + " size=" + width + "x" + height);
            return output;
        } catch (Throwable t) {
            Log.w(TAG, "REVEAL_BLUR_V28_FALLBACK " + t.getClass().getSimpleName());
            return bitmap;
        }
    }

    /** Dim only the genuine camera shutter View; restore its exact old alpha. */
    private static void dimShutter(View root) {
        restoreShutter();
        View shutter = findShutter(root, 0);
        if (shutter == null) {
            Log.i(TAG, "REVEAL_SHUTTER_UNAVAILABLE");
            return;
        }
        shutterPreviousAlpha = shutter.getAlpha();
        shutterRef = new WeakReference<>(shutter);
        shutter.setAlpha(Math.min(shutterPreviousAlpha, 0.52f));
        Log.i(TAG, "REVEAL_SHUTTER_DIM class=" + shutter.getClass().getSimpleName());
    }

    private static void restoreShutter() {
        View shutter = shutterRef.get();
        if (shutter != null) shutter.setAlpha(shutterPreviousAlpha);
        shutterRef = new WeakReference<>(null);
    }

    private static View findShutter(View view, int depth) {
        if (depth > 20) return null;
        String name = view.getClass().getName();
        if ((name.contains("CameraSnapView") || name.endsWith(".SnapView"))
                && view.getWidth() > 0 && view.getHeight() > 0
                && view.getVisibility() == View.VISIBLE) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) {
                View match = findShutter(group.getChildAt(i), depth+1);
                if (match != null) return match;
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
            restoreShutter();
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
        void setResult(Bitmap value, Bitmap blurredStill) {
            if (preview != null && !preview.isRecycled() && preview != blurredStill)
                preview.recycle();
            preview = blurredStill;
            result = value;
            progress = 0f;
            invalidate();
        }
        void setProgress(float value) { progress = value; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (preview != null && !preview.isRecycled()) {
                drawCrop(canvas, preview, new Rect(0,0,w,h), soft);
                // Do not darken the saved JPEG blur during tile restoration.
                if (result == null) canvas.drawColor(0x1B000000);
            } else {
                canvas.drawColor(0x441A1A1A);
            }
            if (result == null || result.isRecycled()) return;
            // OEM-like 2x2 (FOUR quadrant) reveal: one quadrant at a time.
            // The blurred base and sharp tiles are derived from the same JPEG.
            int columns = 2, rows = 2;
            float tw = (float)w / columns, th = (float)h / rows;
            for (int row=0;row<rows;row++) for(int col=0;col<columns;col++) {
                int quadrant = row * columns + col;
                float threshold = 0.04f + quadrant * 0.22f;
                float t = Math.max(0f, Math.min(1f,
                    (progress - threshold) / 0.22f));
                float alpha = t*t*(3f - 2f*t);
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
