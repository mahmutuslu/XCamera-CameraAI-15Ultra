package dev.cameraai.override;

import android.util.SparseArray;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * CameraAI v0.4 -> modern libxposed API 102 bridge.
 * Intended as a second entry alongside an UNMODIFIED XCamera 1.2 dex.
 *
 * MODE-175 zoom presets (property debug.cameraai.zoomprofile):
 *    0 -> original XCamera list [0.6, 1, 3, 4.3]
 *    1 -> verified 200MP single button [4.3] (fallback)
 *    2 -> user requested three buttons [1, 3, 4.3] (DEFAULT)
 * The mode 175 initial selection is managed by the app; it was 1x in
 * the user's original test. No forced 4.3x selection is applied.
 * Three-button list remains TEST ONLY until verified on device.
 * Existing AI hooks remain unchanged.
 * Actual sensor resolution and HAL are never modified.
 */
public final class ModernCameraAIHook extends XposedModule {
    private static final String TAG = "CameraAIMerged";
    private static final String TARGET_MODEL = "madrid";
    private static final long EXTENDED_MS = 15000L;
    private static boolean initialized = false;
    private static final AtomicInteger zoomTrace = new AtomicInteger();
    private static final AtomicInteger deviceZoomTrace = new AtomicInteger();
    private static volatile float lastRequestedZoom = 4.3f;
    private static final AtomicInteger hybridTransitions = new AtomicInteger();

    private void note(String text) {
        log(android.util.Log.INFO, TAG, text);
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam p) {
        if (!"com.android.camera".equals(p.getPackageName()) || initialized) return;
        initialized = true;
        ClassLoader loader = p.getClassLoader();
        note("modern API 102 entry; camera AI hooks starting");
        hookJson();
        hookGson(loader);
        hookTimeout(loader);
        hookZoomPresets(loader);
        hookRuntimeZoomDiagnostics(loader);
        hookZoomTranslationProbe(loader);
        hookHybridHighResolutionRouting(loader);
    }

    private void hookJson() {
        try {
            Method put = JSONObject.class.getDeclaredMethod("put", String.class, Object.class);
            hook(put).intercept(chain -> {
                Object key = chain.getArg(0);
                if ("phone_model".equals(key)) {
                    Object[] args = chain.getArgs().toArray();
                    note("phone_model " + args[1] + " -> madrid");
                    args[1] = TARGET_MODEL;
                    return chain.proceed(args);
                }
                if ("motion".equals(key) && chain.getArg(1) instanceof Number && isAiSceneBuilder()) {
                    Object[] args = chain.getArgs().toArray();
                    Object old = args[1];
                    args[1] = Integer.valueOf(0);
                    note("AI scene motion original=" + old + " -> 0 (JSON only)");
                    return chain.proceed(args);
                }
                return chain.proceed();
            });
            note("JSONObject.put hook installed");
        } catch (Throwable e) { note("JSONObject.put failed " + e); }
    }

    private boolean isAiSceneBuilder() {
        for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            if (("rn.b".equals(f.getClassName()) && "Q".equals(f.getMethodName()))
                || ("un.a".equals(f.getClassName()) && "a".equals(f.getMethodName())))
                return true;
        }
        return false;
    }

    private void hookGson(ClassLoader loader) {
        try {
            Class<?> gson = Class.forName("com.google.gson.JsonObject", false, loader);
            Method addProperty = gson.getDeclaredMethod("addProperty", String.class, String.class);
            hook(addProperty).intercept(chain -> {
                if ("phone_model".equals(chain.getArg(0))) {
                    Object[] args = chain.getArgs().toArray();
                    note("Gson phone_model " + args[1] + " -> madrid");
                    args[1] = TARGET_MODEL;
                    return chain.proceed(args);
                }
                return chain.proceed();
            });
            note("Gson.addProperty hook installed");
        } catch (Throwable e) { note("Gson hook unavailable " + e); }
    }

    private void hookTimeout(ClassLoader loader) {
        try {
            Class<?> target = Class.forName("wd.i5", false, loader);
            int count = 0;
            for (Method m : target.getDeclaredMethods()) {
                if (!"f".equals(m.getName()) || !Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] types = m.getParameterTypes();
                if (types.length != 3 || types[0] != long.class) continue;
                hook(m).intercept(chain -> {
                    long original = (Long) chain.getArg(0);
                    if (original != 3000L && original != 3500L) return chain.proceed();
                    String kind = null;
                    for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
                        if (original == 3000L && "pn.j".equals(f.getClassName()) && "n".equals(f.getMethodName())) {
                            kind = "pollResult";
                            break;
                        }
                        if (original == 3500L && "wn.b$a".equals(f.getClassName()) && "invokeSuspend".equals(f.getMethodName())) {
                            kind = "pipeline";
                            break;
                        }
                    }
                    if (kind == null) return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    args[0] = Long.valueOf(EXTENDED_MS);
                    note("AI timeout " + kind + " " + original + " -> " + EXTENDED_MS + "ms");
                    return chain.proceed(args);
                });
                count++;
            }
            note("wd.i5.f hooks installed=" + count);
        } catch (Throwable e) { note("AI timeout hook failed " + e); }
    }

    private String zoomProfile() {
        try {
            return String.valueOf(Class.forName("android.os.SystemProperties")
                .getMethod("get", String.class, String.class)
                .invoke(null, "debug.cameraai.zoomprofile", "2"));
        } catch (Throwable e) {
            note("zoomprofile property unreadable; using safe three-preset mode: " + e);
            return "2";
        }
    }

    private void hookZoomPresets(ClassLoader loader) {
        final String profile = zoomProfile();
        if ("0".equals(profile)) {
            note("200MP zoom profile 0: original XCamera defaults");
            return;
        }
        if (!"1".equals(profile) && !"2".equals(profile)) {
            note("200MP zoom profile invalid (" + profile + "); original XCamera kept");
            return;
        }
        try {
            Class<?> values = Class.forName("com.leekinn.xcamera.XCameraModule$Values",
                false, getClass().getClassLoader());
            Method buildZoom = values.getDeclaredMethod("lambda$build$18");
            hook(buildZoom).intercept(chain -> {
                Object result = chain.proceed();
                if (!(result instanceof SparseArray)) return result;
                @SuppressWarnings("unchecked")
                SparseArray<Object> map = (SparseArray<Object>) result;
                Object original = map.get(175);
                if (!(original instanceof Float[])) {
                    note("mode175 zoom preset type unexpected: " + original);
                    return result;
                }
                Float[] replacement = "1".equals(profile)
                    ? new Float[]{4.3f}
                    : new Float[]{1.0f, 3.0f, 4.3f};
                map.put(175, replacement);
                if ("1".equals(systemFlag("debug.cameraai.hybridroute", "0"))) {
                    Object fiftyMpPresets = map.get(167);
                    map.put(167, new Float[]{1.0f, 3.0f, 4.3f});
                    note("HYBRID: mode167 50MP lens presets -> [1,3,4.3], old="
                        + (fiftyMpPresets instanceof Float[]
                            ? Arrays.toString((Float[]) fiftyMpPresets)
                            : String.valueOf(fiftyMpPresets)));
                }
                note("mode175 presets=" + Arrays.toString(replacement)
                    + "; original=" + Arrays.toString((Float[])original)
                    + "; profile=" + profile);
                return result;
            });
            note("200MP zoom-list hook installed, profile=" + profile
                + "; initial zoom managed by Xiaomi Camera (previously 1x)");
        } catch (Throwable e) {
            note("mode175 zoom preset hook unavailable: " + e);
        }
    }


    private String systemFlag(String name, String fallback) {
        try {
            return String.valueOf(Class.forName("android.os.SystemProperties")
                .getMethod("get", String.class, String.class)
                .invoke(null, name, fallback));
        } catch (Throwable e) {
            note("property " + name + " unavailable: " + e);
            return fallback;
        }
    }

    private int currentMode(Object module) {
        if (module == null) return -1;
        try {
            Class<?> base = module.getClass();
            while (base != null) {
                try {
                    Method m = base.getDeclaredMethod("getModuleIndex");
                    m.setAccessible(true);
                    return (Integer) m.invoke(module);
                } catch (NoSuchMethodException ignored) {
                    base = base.getSuperclass();
                }
            }
        } catch (Throwable e) {
            note("currentMode lookup failed: " + e);
        }
        return -1;
    }

    /**
     * The 1x/3x/4.3x list is ONLY a UI preset array; Xiaomi can still disable
     * zoom in UltraPixel mode. This probe logs the *actual* zoom gating and UI
     * calls. Override is opt-in: debug.cameraai.realzoom=1, default OFF.
     *
     * Only the isZoomEnabled result for CaptureModule in mode 175 can change.
     * This does not change sensors, JPEG/HEIC output, HAL or optical zoom.
     */
    private void hookRuntimeZoomDiagnostics(ClassLoader loader) {
        final boolean tryEnable = "1".equals(
            systemFlag("debug.cameraai.realzoom", "0"));
        note("200MP actual-zoom probe start; enable zoom gate test=" + tryEnable);
        try {
            Class<?> capture = Class.forName(
                "com.android.camera.features.mode.capture.CaptureModule", false, loader);
            Method enabled = capture.getDeclaredMethod("isZoomEnabled");
            enabled.setAccessible(true);
            hook(enabled).intercept(chain -> {
                Object result = chain.proceed();
                int mode = currentMode(chain.getThisObject());
                if (mode == 175) {
                    if (zoomTrace.getAndIncrement() < 24) {
                        note("MODE175 isZoomEnabled original=" + result
                            + ", override=" + tryEnable);
                    }
                    if (tryEnable && Boolean.FALSE.equals(result)) return Boolean.TRUE;
                }
                return result;
            });
            note("CaptureModule.isZoomEnabled diagnostic hook installed");
        } catch (Throwable e) {
            note("CaptureModule.isZoomEnabled diagnostic unavailable: " + e);
        }
        try {
            Class<?> zoom = Class.forName(
                "com.xiaomi.camera.features.zoom.ui.view.toggle.ZoomRatioToggleView",
                false, loader);
            Method ratio = zoom.getDeclaredMethod("setZoomRatio", float.class);
            hook(ratio).intercept(chain -> {
                if (zoomTrace.getAndIncrement() < 100) {
                    note("zoom toggle setZoomRatio=" + chain.getArg(0));
                }
                return chain.proceed();
            });
            Method arr = zoom.getDeclaredMethod("setZoomArray", float[].class);
            hook(arr).intercept(chain -> {
                Object value = chain.getArg(0);
                note("zoom toggle setZoomArray="
                    + (value instanceof float[] ? Arrays.toString((float[]) value)
                       : String.valueOf(value)));
                return chain.proceed();
            });
            note("zoom toggle update hooks installed");
        } catch (Throwable e) {
            note("zoom toggle probe unavailable: " + e);
        }
        try {
            Class<?> camera2 = Class.forName(
                "com.android.camera.module.Camera2Module", false, loader);
            Method sat = camera2.getDeclaredMethod("updateSATZooming", int.class);
            hook(sat).intercept(chain -> {
                if (zoomTrace.getAndIncrement() < 100) {
                    note("updateSATZooming(value=" + chain.getArg(0)
                        + ", mode=" + currentMode(chain.getThisObject()) + ")");
                }
                return chain.proceed();
            });
            note("SAT zoom diagnostic hook installed");
        } catch (Throwable e) {
            note("SAT zoom diagnostic unavailable: " + e);
        }
    }


    /**
     * Native 200MP zoom investigation on Xiaomi 15 Ultra:
     * UI sends 1.0/3.0/4.3, but cameraId 5 always receives 1.1219512.
     * Observe the exact obfuscated conversion i9.g.nh(float) in Camera 6.8,
     * without touching its return value or changing camera/HAL routing.
     */
    private void hookZoomTranslationProbe(ClassLoader loader) {
        try {
            Class<?> zoomManager = Class.forName("i9.g", false, loader);
            Method convert = zoomManager.getDeclaredMethod("nh", float.class);
            Field modeField = zoomManager.getDeclaredField("c");
            modeField.setAccessible(true);
            hook(convert).intercept(chain -> {
                Object value = chain.proceed();
                try {
                    Object instance = chain.getThisObject();
                    int mode = instance != null ? modeField.getInt(instance) : -1;
                    if (mode == 175 && deviceZoomTrace.getAndIncrement() < 60) {
                        note("MODE175 ZoomManager device translation "
                            + chain.getArg(0) + " -> " + value
                            + " (OBSERVE ONLY, original conversion preserved)");
                    }
                } catch (Throwable e) {
                    note("zoom translation observation skipped: " + e);
                }
                return value;
            });
            note("200MP ZoomManager.nh(float) conversion probe installed (no override)");
        } catch (Throwable e) {
            note("200MP ZoomManager conversion probe unavailable: " + e);
        }
    }


    private boolean calledFromZoomLensReopen() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            // Xiaomi Camera 6.8: Ph.b.e(Y, boolean, int)
            if ("Ph.b".equals(frame.getClassName()) && "e".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * EXPERIMENTAL 50MP/200MP hybrid switching for Xiaomi 15 Ultra only.
     *
     * Native Camera 6.8 mode 175 is permanently routed to the 200MP periscope,
     * even when its UI requests 1x or 3x (observed cameraId=5, fixed HAL 1.121951).
     * Camera 6.7 and 6.8 share the same logic: their 175 selector branches
     * inspect lens roles and capabilities, not just the zoom preset table.
     *
     * Rather than spoofing a 200MP output on a 50MP sensor, opt-in changes the
     * StartControl mode selected by Camera's own zoom-lens reopen path:
     * 175 -> 167 (50MP UltraPixel) for 1x / 3x.
     * 167 -> 175 (200MP periscope) for 4.3x.
     *
     * The real physical camera and output size remain decided by Camera's own
     * high-resolution mode. If the reopen mechanism does not follow this
     * route on this device, no forced cameraId/crop is applied.
     *
     * Property: debug.cameraai.hybridroute=1. DEFAULT OFF.
     * This is an UNVERIFIED functional experiment, not a proven fix.
     */
    private void hookHybridHighResolutionRouting(ClassLoader loader) {
        boolean enabled = "1".equals(
            systemFlag("debug.cameraai.hybridroute", "0"));
        if (!enabled) {
            note("HYBRID mode175/mode167 high-resolution lens router OFF");
            return;
        }
        try {
            Class<?> manager = Class.forName("i9.g", false, loader);
            Method setter = manager.getDeclaredMethod("setZoomRatio", float.class);
            Field module = manager.getDeclaredField("c");
            module.setAccessible(true);
            hook(setter).intercept(chain -> {
                Object obj = chain.getThisObject();
                int mode = obj == null ? -1 : module.getInt(obj);
                float ratio = (Float) chain.getArg(0);
                if (mode == 175 || mode == 167) {
                    lastRequestedZoom = ratio;
                    note("HYBRID: zoom requested, mode=" + mode + ", zoom=" + ratio);
                }
                return chain.proceed();
            });
            Class<?> start = Class.forName(
                "com.android.camera.module.loader.base.StartControl", false, loader);
            Method create = start.getDeclaredMethod("create", int.class);
            hook(create).intercept(chain -> {
                int target = (Integer) chain.getArg(0);
                // Only change module reopens explicitly initiated by the native
                // optical lens switching path. Never touch normal mode-menu taps.
                if (!calledFromZoomLensReopen()) return chain.proceed();
                int replacement = target;
                float requested = lastRequestedZoom;
                if (target == 175 && requested <= 3.05f && requested >= 0.95f) {
                    replacement = 167;
                } else if (target == 167 && requested >= 4.25f) {
                    replacement = 175;
                }
                if (replacement == target) return chain.proceed();
                int n = hybridTransitions.incrementAndGet();
                if (n > 18) {
                    note("HYBRID: transition safety limit reached; normal routing");
                    return chain.proceed();
                }
                note("HYBRID: physical lens route via Camera's own high-res modes "
                    + target + " -> " + replacement + " for " + requested + "x");
                Object[] args = chain.getArgs().toArray();
                args[0] = Integer.valueOf(replacement);
                return chain.proceed(args);
            });
            note("HYBRID 50MP/200MP StartControl routing installed (OPT-IN TEST)");
        } catch (Throwable e) {
            note("HYBRID 50MP/200MP routing unavailable; normal Camera behavior: " + e);
        }
    }

}
