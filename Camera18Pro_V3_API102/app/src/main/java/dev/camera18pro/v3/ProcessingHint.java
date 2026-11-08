package dev.camera18pro.v3;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/**
 * Display-only experimental Pixel175 processing indicator.
 * Touch-transparent. No preview, ImageReader, camera HAL, or capture state edits.
 * The hint reflects MiVi completion, not on-disk JPEG save.
 */
final class ProcessingHint {
    private static volatile WeakReference<Activity> active =
            new WeakReference<>(null);
    private static volatile WeakReference<TextView> view =
            new WeakReference<>(null);
    private static volatile boolean processing;

    private ProcessingHint() { }

    static void onCameraFocus(Activity activity, boolean focus, boolean pending) {
        if (!focus) {
            Activity previous = active.get();
            if (previous == activity) update(false);
            return;
        }
        active = new WeakReference<>(activity);
        processing = pending;
        update(pending);
    }

    static void update(boolean value) {
        processing = value;
        Activity activity = active.get();
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) return;
            try {
                FrameLayout root = activity.findViewById(android.R.id.content);
                if (root == null) return;

                TextView label = view.get();
                if (label == null || label.getParent() != root) {
                    if (label != null && label.getParent() instanceof ViewGroup) {
                        ((ViewGroup) label.getParent()).removeView(label);
                    }
                    label = new TextView(activity);
                    label.setText("200 MP işleniyor…");
                    label.setTextColor(0xFFFFFFFF);
                    label.setTextSize(14f);
                    label.setGravity(Gravity.CENTER);
                    label.setClickable(false);
                    label.setFocusable(false);
                    label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                    label.setPadding(dp(activity,16), dp(activity,8),
                            dp(activity,16), dp(activity,8));
                    GradientDrawable background = new GradientDrawable();
                    background.setColor(0xDD20242A);
                    background.setCornerRadius(dp(activity,18));
                    label.setBackground(background);
                    FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
                    layout.bottomMargin = dp(activity, 110);
                    root.addView(label, layout);
                    view = new WeakReference<>(label);
                }
                label.setVisibility(processing ? View.VISIBLE : View.GONE);
            } catch (Throwable ignored) {
                // Never throw on camera UI thread.
            }
        });
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
