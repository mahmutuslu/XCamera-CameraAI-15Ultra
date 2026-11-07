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
 * On the real 15 Ultra, mode 175 starts at 4.3x.
 * The three-button [1,3,4.3] UI is confirmed NOT to change physical sensors.
 * Real 1x/3x high-resolution routing remains unresolved.
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
    private static volatile float pixelRequestedZoom = 4.3f;
    private static final AtomicInteger pixelZoomTrace = new AtomicInteger();
    private static final AtomicInteger pixelSwitchTrace = new AtomicInteger();
    private static final AtomicInteger pixelRouteTrace = new AtomicInteger();
    private static final AtomicInteger pixelSelectorTrace = new AtomicInteger();
    private static final AtomicInteger pixelDecisionTrace = new AtomicInteger();
    private static final AtomicInteger pixelStreamTrace = new AtomicInteger();

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
        hookPixelModeRouting(loader);
        hookPixelLensSelectorProbe(loader);
        hookPixelZoomDecisionTrace(loader);
        hookPixelOutputStreamProbe(loader);
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


    /**
     * Xiaomi Camera 6.8 real Ultra Pixel module is PixelModule (175);
     * mode 167 is ProModule, NOT a 50MP Ultra Pixel module.
     * Previous hybrid route was invalid and is intentionally removed.
     *
     * PixelModule declares isZoomEnabled(), unlike CaptureModule which runs
     * in mode 163 only. The native zoom-switch permission is inherited from
     * Camera2Module. Apply both only to PixelModule and only with opt-in.
     *
     * PixelModule remains mode 175. We do not alter StartControl, camera ID,
     * output resolution, or the Camera HAL.
     */
    private void hookPixelModeRouting(ClassLoader loader) {
        final boolean enabled = "1".equals(systemFlag("debug.cameraai.pixelzoom", "0"));
        note("PixelModule native zoom gate test=" + enabled + " (mode175, no cameraId override)");
        try {
            Class<?> pixel = Class.forName(
                "com.android.camera.features.mode.pixel.PixelModule", false, loader);
            Method zoomEnabled = pixel.getDeclaredMethod("isZoomEnabled");
            hook(zoomEnabled).intercept(chain -> {
                Object result = chain.proceed();
                int count = pixelZoomTrace.getAndIncrement();
                if (count < 35) {
                    note("PIXEL175 actual isZoomEnabled=" + result
                        + ", force=" + enabled);
                }
                if (enabled && Boolean.FALSE.equals(result)) return Boolean.TRUE;
                return result;
            });
            note("PixelModule.isZoomEnabled hook installed");
            Class<?> camera2 = Class.forName("com.android.camera.module.Camera2Module", false, loader);
            Method canSwitch = camera2.getDeclaredMethod("isCameraSwitchingDuringZoomingAllowed");
            hook(canSwitch).intercept(chain -> {
                Object result = chain.proceed();
                if (pixel.isInstance(chain.getThisObject())) {
                    int count = pixelSwitchTrace.getAndIncrement();
                    if (count < 35) {
                        note("PIXEL175 camera lens switching allowed=" + result
                            + ", force=" + enabled);
                    }
                    if (enabled && Boolean.FALSE.equals(result)) return Boolean.TRUE;
                }
                return result;
            });
            note("PixelModule Camera2Module native lens-switch permission hook installed");
        } catch (Throwable e) {
            note("PixelModule native zoom gate test unavailable: " + e);
        }
    }


    /**
     * Read-only investigation of Xiaomi Camera 6.8's physical sensor chooser.
     * The mode-175 special branch in B2.c.c(...) calls optical lens-role
     * flags (l9.r0.e/f/g/h) and Pe.b.x1(). Stock 6.7's C2.c.c(...) shares
     * the equivalent branch. This code makes ONE original selector call and
     * NEVER changes the requested mode, camera ID or HAL pipeline.
     *
     * Property: debug.cameraai.pixelrouteprobe=1. Default OFF.
     * This is NOT a working 1x/3x high-resolution optical switch yet.
     */
    private void hookPixelLensSelectorProbe(ClassLoader loader) {
        if (!"1".equals(systemFlag("debug.cameraai.pixelrouteprobe", "0"))) {
            note("PixelModule native physical selector probe OFF");
            return;
        }
        try {
            Class<?> manager = Class.forName("i9.g", false, loader);
            Method setter = manager.getDeclaredMethod("setZoomRatio", float.class);
            Field modeField = manager.getDeclaredField("c");
            modeField.setAccessible(true);
            hook(setter).intercept(chain -> {
                Object current = chain.getThisObject();
                if (current != null && modeField.getInt(current) == 175) {
                    float ratio = (Float) chain.getArg(0);
                    pixelRequestedZoom = ratio;
                    if (pixelRouteTrace.getAndIncrement() < 20) {
                        note("PIXEL175 requested physical-lens zoom=" + ratio);
                    }
                }
                return chain.proceed();
            });
            Class<?> physicalSelector = Class.forName("B2.c", false, loader);
            Method select = physicalSelector.getDeclaredMethod(
                "c", int.class, int.class, boolean.class);
            hook(select).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                Object selected = chain.proceed(); // IMPORTANT: call original ONCE
                int captureMode = (Integer) args[1];
                if (captureMode == 175 && pixelSelectorTrace.getAndIncrement() < 30) {
                    note("PIXEL175 physical selector: inputId=" + args[0]
                        + ", mode=" + captureMode + ", flag=" + args[2]
                        + ", zoom=" + pixelRequestedZoom + ", nativeCameraId=" + selected
                        + ", roleFlags=" + readPixelRoleFlags(loader));
                }
                return selected;
            });
            note("PIXEL175 B2.c.c physical selector tracing installed (read-only)");
        } catch (Throwable e) {
            note("PIXEL175 physical selector tracing unavailable: " + e);
        }
    }

    private String readPixelRoleFlags(ClassLoader loader) {
        StringBuilder result = new StringBuilder();
        try {
            Class<?> role = Class.forName("l9.r0", false, loader);
            for (String name : new String[]{"e", "f", "g", "h"}) {
                try {
                    Method flag = role.getDeclaredMethod(name);
                    flag.setAccessible(true);
                    result.append(name).append('=').append(flag.invoke(null)).append(' ');
                } catch (Throwable ignored) {
                    result.append(name).append("=? ");
                }
            }
        } catch (Throwable e) {
            result.append("roleUnavailable=").append(e.getClass().getSimpleName());
        }
        try {
            Class<?> capability = Class.forName("Pe.b", false, loader);
            Method get = capability.getDeclaredMethod("D");
            get.setAccessible(true);
            Object instance = get.invoke(null);
            Method optical = capability.getDeclaredMethod("x1");
            optical.setAccessible(true);
            result.append(" x1=").append(optical.invoke(instance));
        } catch (Throwable e) {
            result.append(" x1Unavailable=").append(e.getClass().getSimpleName());
        }
        return result.toString();
    }
    /**
     * Read-only tracing of native 175 zoom preference (source: 6.8 classes.dex):
     *   CameraSettings i.n(int mode,int lens) -> float
     *   CameraSettings i.O(int mode) -> float
     *
     * These are called from the native zoom initialization and physical
     * camera selector, respectively. Capture return values unmodified.
     *
     * The actual first 175 startup value was 4.3 on the user's phone.
     * Record which getter returns it before attempting any "1x default" patch.
     */
    private void hookPixelZoomDecisionTrace(ClassLoader loader) {
        if (!"1".equals(systemFlag("debug.cameraai.zoomdecisionprobe", "0"))) {
            note("PIXEL175 startup/selector zoom decision probe OFF");
            return;
        }
        try {
            Class<?> settings = Class.forName(
                "com.android.camera.data.data.i", false, loader);
            Method initial = settings.getDeclaredMethod("n", int.class, int.class);
            hook(initial).intercept(chain -> {
                Object value = chain.proceed();
                if (Integer.valueOf(175).equals(chain.getArg(0))
                    && pixelDecisionTrace.getAndIncrement() < 40) {
                    note("PIXEL175 zoom preference i.n: mode=" + chain.getArg(0)
                        + ", lens=" + chain.getArg(1)
                        + ", unmodifiedValue=" + value);
                }
                return value;
            });
            Method lensPreference = settings.getDeclaredMethod("O", int.class);
            hook(lensPreference).intercept(chain -> {
                Object value = chain.proceed();
                if (Integer.valueOf(175).equals(chain.getArg(0))
                    && pixelDecisionTrace.getAndIncrement() < 55) {
                    note("PIXEL175 camera selector i.O: mode=" + chain.getArg(0)
                        + ", unmodifiedValue=" + value
                        + ", requestedZoom=" + pixelRequestedZoom);
                }
                return value;
            });
            note("PIXEL175 native default zoom + lens selection input traces installed");
        } catch (Throwable e) {
            note("PIXEL175 zoom decision traces unavailable: " + e);
        }
        try {
            Class<?> zoom = Class.forName("i9.g", false, loader);
            Method initialize = zoom.getDeclaredMethod("B7");
            Field mode = zoom.getDeclaredField("c");
            Field ratio = zoom.getDeclaredField("l");
            mode.setAccessible(true);
            ratio.setAccessible(true);
            hook(initialize).intercept(chain -> {
                Object instance = chain.getThisObject();
                boolean target = instance != null && mode.getInt(instance) == 175;
                if (target && pixelDecisionTrace.getAndIncrement() < 55) {
                    note("PIXEL175 ZoomManager.B7 initialize before=" + ratio.getFloat(instance));
                }
                Object result = chain.proceed();
                if (target && pixelDecisionTrace.getAndIncrement() < 55) {
                    note("PIXEL175 ZoomManager.B7 initialize after=" + ratio.getFloat(instance));
                }
                return result;
            });
            note("PIXEL175 ZoomManager.B7 native initialization trace installed");
        } catch (Throwable e) {
            note("PIXEL175 ZoomManager.B7 trace unavailable: " + e);
        }
    }

    /**
     * v2.2: Capture the REAL high-resolution stream decisions, without ever
     * mutating them. DEX-verified 6.8 targets:
     *
     * Camera2Module.updatePictureAndPreviewSize() : void  (private)
     * Camera2Module.updateConfigQcfa() : void  (private)
     * m6.o.f(int,int,List,float,Size,boolean) : Size (public static)
     *
     * In updatePictureAndPreviewSize, the image/preview/algorithm size and
     * actual physical camera are stored in mLoadStreamSizeBase (m6.o);
     * the snapshot records both before and after normal OEM calculation.
     *
     * Property debug.cameraai.streamprobe=1 (OFF unless opted in).
     * This does NOT switch sensors, overwrite sizes or call methods twice.
     */
    private void hookPixelOutputStreamProbe(ClassLoader loader) {
        if (!"1".equals(systemFlag("debug.cameraai.streamprobe", "0"))) {
            note("PIXEL175 output-size/QCFA probe OFF");
            return;
        }
        try {
            Class<?> pixel = Class.forName("com.android.camera.features.mode.pixel.PixelModule", false, loader);
            Class<?> camera2 = Class.forName("com.android.camera.module.Camera2Module", false, loader);
            Field sizeField = camera2.getDeclaredField("mLoadStreamSizeBase");
            sizeField.setAccessible(true);
            Method updateSizes = camera2.getDeclaredMethod("updatePictureAndPreviewSize");
            hook(updateSizes).intercept(chain -> {
                Object target = chain.getThisObject();
                if (!pixel.isInstance(target)) return chain.proceed();
                int seq = pixelStreamTrace.getAndIncrement();
                if (seq < 20) {
                    note("PIXEL175 output-size BEFORE " + safePixelSizeFields(sizeField, target));
                }
                Object result = chain.proceed();
                if (seq < 20) {
                    note("PIXEL175 output-size AFTER " + safePixelSizeFields(sizeField, target));
                }
                return result;
            });
            Method qcfa = camera2.getDeclaredMethod("updateConfigQcfa");
            hook(qcfa).intercept(chain -> {
                Object target = chain.getThisObject();
                if (!pixel.isInstance(target)) return chain.proceed();
                int seq = pixelStreamTrace.getAndIncrement();
                if (seq < 24) {
                    note("PIXEL175 updateConfigQcfa BEFORE " + safePixelSizeFields(sizeField, target));
                }
                Object result = chain.proceed();
                if (seq < 24) {
                    note("PIXEL175 updateConfigQcfa AFTER " + safePixelSizeFields(sizeField, target));
                }
                return result;
            });
            note("PIXEL175 OEM picture/preview-size and QCFA tracing installed (read-only)");
        } catch (Throwable e) {
            note("PIXEL175 output-size/QCFA tracing unavailable: " + e);
        }
        try {
            Class<?> selector = Class.forName("m6.o", false, loader);
            Method size = selector.getDeclaredMethod(
                "f", int.class, int.class, java.util.List.class,
                float.class, android.util.Size.class, boolean.class);
            hook(size).intercept(chain -> {
                Object selected = chain.proceed();
                if (inPixelSizeDecision() && pixelStreamTrace.getAndIncrement() < 32) {
                    note("PIXEL175 m6.o.f size decision: modeOrCamera=" + chain.getArg(0)
                        + ", cameraOrMode=" + chain.getArg(1) + ", ratio=" + chain.getArg(3)
                        + ", sizeHint=" + chain.getArg(4) + ", switch=" + chain.getArg(5)
                        + ", OEMselected=" + selected);
                }
                return selected;
            });
            note("PIXEL175 m6.o.f size result tracing installed (read-only)");
        } catch (Throwable e) {
            note("PIXEL175 m6.o.f size tracing unavailable: " + e);
        }
    }

    private boolean inPixelSizeDecision() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if ("com.android.camera.module.Camera2Module".equals(frame.getClassName())
                && "updatePictureAndPreviewSize".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private String safePixelSizeFields(Field baseField, Object module) {
        try {
            Object base = baseField.get(module);
            if (base == null) return "mLoadStreamSizeBase=null";
            Class<?> klass = base.getClass();
            StringBuilder result = new StringBuilder();
            for (String name : new String[]{"y", "z", "v"}) {
                try {
                    Field size = klass.getDeclaredField(name);
                    size.setAccessible(true);
                    result.append(name).append('=').append(size.get(base)).append(' ');
                } catch (Throwable error) {
                    result.append(name).append("=? ");
                }
            }
            try {
                Field context = klass.getDeclaredField("E");
                context.setAccessible(true);
                Object metadata = context.get(base);
                if (metadata != null) {
                    for (String name : new String[]{"d", "u", "l"}) {
                        try {
                            Field fld = metadata.getClass().getDeclaredField(name);
                            fld.setAccessible(true);
                            result.append("ctx.").append(name).append('=')
                                .append(fld.get(metadata)).append(' ');
                        } catch (Throwable ignored) {
                            result.append("ctx.").append(name).append("=? ");
                        }
                    }
                }
            } catch (Throwable ignored) { result.append("ctx=?"); }
            return result.toString();
        } catch (Throwable e) {
            return "sizeFieldsUnavailable=" + e.getClass().getSimpleName();
        }
    }

}
