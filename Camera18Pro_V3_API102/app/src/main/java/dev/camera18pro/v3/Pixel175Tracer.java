package dev.camera18pro.v3;

import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only Pixel175 diagnostic helper for Xiaomi 18 Pro camera 6.8.002090.2.
 * Companion to, NOT a replacement for, existing CameraAIMerged + XCamera v2.3.
 * No zoom, camera ID, capture output or stream parameters are changed.
 */
public final class Pixel175Tracer extends XposedModule {
    private static final String TARGET = "com.android.camera";
    private static final String TAG = "CAMERA18PRO_V3";
    private static final AtomicBoolean ATTACHED = new AtomicBoolean(false);
    private static final AtomicLong LAST_CAPTURE_NANOS = new AtomicLong(0L);

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName())) return;
        if (!ATTACHED.compareAndSet(false, true)) return;
        info("API=102, version=3.0.0-alpha1, mode=READ_ONLY");
        ClassLoader cl = param.getClassLoader();
        install(cl, "com.android.camera.features.mode.pixel.PixelModule",
                "onCaptureStart", 2, Event.CAPTURE_START);
        install(cl, "com.android.camera.features.mode.pixel.PixelModule",
                "onPictureTakenFinished", 3, Event.SHOT2SHOT_FINISH);
        install(cl, "com.android.camera.features.mode.pixel.PixelModule",
                "onEarlyImageAvailable", 5, Event.EARLY_IMAGE);
        install(cl, "com.android.camera.features.mode.pixel.PixelModule",
                "onFinalImageAvailable", 2, Event.FINAL_IMAGE);
        install(cl, "com.xiaomi.camera.mivi.qcom.ImageReceiverExecutor$2",
                "onSuccess", 2, Event.MIVI_SUCCESS);
        install(cl, "com.xiaomi.camera.mivi.qcom.ImageReceiverExecutor$2",
                "onError", 1, Event.MIVI_ERROR);
        install(cl, "com.xiaomi.camera.mivi.AidlBGServiceClient",
                "onHalFailed", 3, Event.HAL_FAILED);
        info("Hook installation finished; verify JPEG size via CAPTURE-END");
    }

    private enum Event {
        CAPTURE_START, SHOT2SHOT_FINISH, EARLY_IMAGE,
        FINAL_IMAGE, MIVI_SUCCESS, MIVI_ERROR, HAL_FAILED
    }

    private void install(ClassLoader loader, String className, String methodName,
                         int count, Event event) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            int installed = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)
                        || method.getParameterCount() != count) continue;
                hook(method).intercept(chain -> {
                    if (event == Event.CAPTURE_START) {
                        LAST_CAPTURE_NANOS.set(SystemClock.elapsedRealtimeNanos());
                        info("CAPTURE_START");
                    }
                    // Exactly one invocation; propagate camera exceptions unchanged.
                    Object result = chain.proceed();
                    long elapsed = elapsedMs();
                    switch (event) {
                        case SHOT2SHOT_FINISH:
                            info("SHOT2SHOT_FINISH preliminary=" + safe(chain.getArg(0))
                                    + " sinceStartMs=" + elapsed);
                            break;
                        case EARLY_IMAGE:
                            info("EARLY_IMAGE sinceStartMs=" + elapsed);
                            break;
                        case FINAL_IMAGE:
                            info("FINAL_IMAGE success=" + safe(chain.getArg(1))
                                    + " sinceStartMs=" + elapsed);
                            break;
                        case MIVI_SUCCESS:
                            info("MIVI_SUCCESS sinceStartMs=" + elapsed);
                            break;
                        case MIVI_ERROR:
                            info("MIVI_ERROR type=" + safe(chain.getArg(0))
                                    + " sinceStartMs=" + elapsed);
                            break;
                        case HAL_FAILED:
                            info("HAL_FAILED frame=" + safe(chain.getArg(1))
                                    + " sinceStartMs=" + elapsed);
                            break;
                        default: break;
                    }
                    return result;
                });
                installed++;
            }
            info("HOOK " + type.getSimpleName() + "." + methodName
                    + " installed=" + installed);
        } catch (Throwable t) {
            info("HOOK_UNAVAILABLE " + className + "." + methodName
                    + " reason=" + t.getClass().getSimpleName());
        }
    }

    private static long elapsedMs() {
        long start = LAST_CAPTURE_NANOS.get();
        return start == 0L ? -1L :
                (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
    }

    private static String safe(Object o) {
        if (o == null) return "null";
        try {
            String s = String.valueOf(o);
            return s.length() > 80 ? s.substring(0, 80) + "..." : s;
        } catch (Throwable t) {
            return "<toString-error>";
        }
    }

    private void info(String msg) {
        Log.i(TAG, msg);
        log(Log.INFO, TAG, msg);
    }
}
