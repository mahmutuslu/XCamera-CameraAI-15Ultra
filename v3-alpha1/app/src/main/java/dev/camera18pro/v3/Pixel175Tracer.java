package dev.camera18pro.v3;

import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only Pixel175 trace helper. Does not replace CameraAIMerged + XCamera. */
public final class Pixel175Tracer extends XposedModule {
    private static final String TARGET = "com.android.camera";
    private static final String TAG = "CAMERA18PRO_V3";
    private static final AtomicBoolean ATTACHED = new AtomicBoolean(false);
    private static final AtomicLong LAST_CAPTURE_NANOS = new AtomicLong(0L);

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName())) return;
        if (!ATTACHED.compareAndSet(false, true)) return;
        info("API=102; module=3.0.0-alpha1; package=" + param.getPackageName()
                + "; mode=read-only; no camera values changed");
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
        info("installation completed; compare with CAPTURE-END and image dimensions");
    }

    private enum Event {
        CAPTURE_START, SHOT2SHOT_FINISH, EARLY_IMAGE, FINAL_IMAGE,
        MIVI_SUCCESS, MIVI_ERROR, HAL_FAILED
    }

    private void install(ClassLoader loader, String className,
                         String methodName, int numberOfArgs, Event event) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            int installed = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())
                        || method.getParameterCount() != numberOfArgs) continue;
                hook(method).intercept(chain -> {
                    if (event == Event.CAPTURE_START) {
                        LAST_CAPTURE_NANOS.set(SystemClock.elapsedRealtimeNanos());
                        info("CAPTURE_START; pixel-module; native camera unchanged");
                    }
                    Object result = chain.proceed();
                    long elapsed = elapsedMs();
                    switch (event) {
                        case SHOT2SHOT_FINISH:
                            info("SHOT2SHOT_FINISH; preliminary=" + safe(chain.getArg(0))
                                    + "; sinceStartMs=" + elapsed
                                    + "; NOT proof of final JPEG save");
                            break;
                        case EARLY_IMAGE:
                            info("EARLY_IMAGE; file=" + safe(chain.getArg(0))
                                    + "; sinceStartMs=" + elapsed);
                            break;
                        case FINAL_IMAGE:
                            info("FINAL_IMAGE; file=" + safe(chain.getArg(0))
                                    + "; success=" + safe(chain.getArg(1))
                                    + "; sinceStartMs=" + elapsed);
                            break;
                        case MIVI_SUCCESS:
                            info("MIVI_SUCCESS; result=" + safe(chain.getArg(0))
                                    + "; file=" + safe(chain.getArg(1))
                                    + "; sinceStartMs=" + elapsed);
                            break;
                        case MIVI_ERROR:
                            info("MIVI_ERROR; reason=" + safe(chain.getArg(0))
                                    + "; sinceStartMs=" + elapsed);
                            break;
                        case HAL_FAILED:
                            info("HAL_FAILED; key=" + safe(chain.getArg(0))
                                    + "; frame=" + safe(chain.getArg(1))
                                    + "; detail=" + safe(chain.getArg(2))
                                    + "; sinceStartMs=" + elapsed);
                            break;
                        default: break;
                    }
                    return result;
                });
                installed++;
            }
            info("hook " + type.getSimpleName() + "." + methodName
                    + " installed=" + installed);
        } catch (Throwable t) {
            info("hook unavailable " + className + "." + methodName + ": "
                    + t.getClass().getSimpleName());
        }
    }

    private static long elapsedMs() {
        long start = LAST_CAPTURE_NANOS.get();
        if (start == 0L) return -1L;
        return (SystemClock.elapsedRealtimeNanos() - start) / 1000000L;
    }

    private static String safe(Object o) {
        if (o == null) return "null";
        String s;
        try { s = String.valueOf(o); }
        catch (Throwable t) { s = "<toString-error>"; }
        return s.length() > 220 ? s.substring(0, 220) + "..." : s;
    }

    private void info(String message) { log(Log.INFO, TAG, message); }
}
