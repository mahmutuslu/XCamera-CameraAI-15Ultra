package dev.camera18pro.v3;

import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Camera18Pro v3 alpha 2. Modern Xposed API 102, read-only diagnostic.
 *
 * This module NEVER changes Camera ID, stream size, zoom, method arguments,
 * method returns, capture UI, or scheduling. It is NOT a v2.3 replacement.
 *
 * CAPTURE_START -> preliminary shot -> MiVi result are distinct stages.
 * MiVi result is associated to the oldest pending capture as a *heuristic*;
 * native callbacks do not expose an unambiguous shared shot ID here.
 */
public final class Pixel175Tracer extends XposedModule {
    private static final String TARGET = "com.android.camera";
    private static final String TAG = "CAMERA18PRO_V3";
    private static final AtomicBoolean ATTACHED = new AtomicBoolean(false);
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final ConcurrentLinkedQueue<Shot> PENDING =
            new ConcurrentLinkedQueue<>();
    private static volatile Shot latest;

    private static final class Shot {
        final long id;
        final long startedNs;
        volatile long preliminaryMs = -1;
        Shot(long id) {
            this.id = id;
            this.startedNs = SystemClock.elapsedRealtimeNanos();
        }
        long elapsedMs() {
            return (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L;
        }
    }

    private enum Event {
        CAPTURE_START, SHOT2SHOT_FINISH, EARLY_IMAGE, FINAL_IMAGE,
        MIVI_SUCCESS, MIVI_ERROR, HAL_FAILED, BG_CAPTURE_FAILED
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName())) return;
        if (!ATTACHED.compareAndSet(false, true)) return;
        info("API=102 version=3.0.0-alpha2 mode=READ_ONLY correlation=FIFO_HEURISTIC");
        ClassLoader loader = param.getClassLoader();
        install(loader, "com.android.camera.features.mode.pixel.PixelModule",
                "onCaptureStart", 2, Event.CAPTURE_START);
        install(loader, "com.android.camera.features.mode.pixel.PixelModule",
                "onPictureTakenFinished", 3, Event.SHOT2SHOT_FINISH);
        install(loader, "com.android.camera.features.mode.pixel.PixelModule",
                "onEarlyImageAvailable", 5, Event.EARLY_IMAGE);
        install(loader, "com.android.camera.features.mode.pixel.PixelModule",
                "onFinalImageAvailable", 2, Event.FINAL_IMAGE);
        install(loader, "com.xiaomi.camera.mivi.qcom.ImageReceiverExecutor$2",
                "onSuccess", 2, Event.MIVI_SUCCESS);
        install(loader, "com.xiaomi.camera.mivi.qcom.ImageReceiverExecutor$2",
                "onError", 1, Event.MIVI_ERROR);
        install(loader, "com.xiaomi.camera.mivi.AidlBGServiceClient",
                "onHalFailed", 3, Event.HAL_FAILED);
        install(loader, "com.xiaomi.camera.mivi.AidlBGServiceClient$1",
                "onCaptureFailed", 3, Event.BG_CAPTURE_FAILED);
        info("INSTALL_COMPLETE; JPEG requires independent CAPTURE-END and 16320x12288 validation");
    }

    private void install(ClassLoader loader, String className, String methodName,
                         int argumentCount, Event event) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            int installed = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())
                        || method.getParameterCount() != argumentCount) continue;
                hook(method).intercept(chain -> {
                    // Only hook onCaptureStart records the event *before*
                    // the original method. All other logging is AFTER proceed().
                    if (event == Event.CAPTURE_START) {
                        Shot shot = new Shot(NEXT_ID.incrementAndGet());
                        latest = shot;
                        PENDING.add(shot);
                        while (PENDING.size() > 16) {
                            Shot stale = PENDING.poll();
                            if (stale != null) {
                                info("UNMATCHED_CAPTURE id=" + stale.id
                                        + " ageMs=" + stale.elapsedMs()
                                        + " reason=pending_queue_limit");
                            }
                        }
                        info("CAPTURE_START id=" + shot.id
                                + " pending=" + PENDING.size());
                    }

                    // Never skip, retry or swallow original Camera callbacks.
                    Object value = chain.proceed();

                    switch (event) {
                        case SHOT2SHOT_FINISH: {
                            Shot shot = latest;
                            if (shot != null) {
                                shot.preliminaryMs = shot.elapsedMs();
                            }
                            info("SHOT2SHOT_FINISH id=" + id(shot)
                                    + " preliminary=" + safe(chain.getArg(0))
                                    + " elapsedMs=" + elapsed(shot)
                                    + " isFinalJpeg=false");
                            break;
                        }
                        case EARLY_IMAGE: {
                            Shot shot = latest;
                            info("EARLY_IMAGE id=" + id(shot)
                                    + " file=" + safe(chain.getArg(0))
                                    + " elapsedMs=" + elapsed(shot));
                            break;
                        }
                        case FINAL_IMAGE: {
                            Shot shot = latest;
                            info("FINAL_IMAGE id=" + id(shot)
                                    + " file=" + safe(chain.getArg(0))
                                    + " success=" + safe(chain.getArg(1))
                                    + " elapsedMs=" + elapsed(shot));
                            break;
                        }
                        case MIVI_SUCCESS: {
                            Object output = chain.getArg(0);
                            String metadata = safe(output);
                            String file = getPictureName(metadata, chain.getArg(1));
                            Shot shot = PENDING.poll();
                            info("MIVI_SUCCESS id=" + id(shot)
                                    + " match=FIFO_HEURISTIC"
                                    + " file=" + file
                                    + " elapsedMs=" + elapsed(shot)
                                    + " preliminaryMs=" + prelim(shot)
                                    + " metadata=" + metadata);
                            break;
                        }
                        case MIVI_ERROR: {
                            Shot shot = PENDING.poll();
                            info("MIVI_ERROR id=" + id(shot)
                                    + " match=FIFO_HEURISTIC"
                                    + " elapsedMs=" + elapsed(shot)
                                    + " reason=" + safe(chain.getArg(0)));
                            break;
                        }
                        case HAL_FAILED: {
                            info("HAL_FAILED (NOT proof of JPEG failure)"
                                    + " key=" + safe(chain.getArg(0))
                                    + " frame=" + safe(chain.getArg(1))
                                    + " info=" + safe(chain.getArg(2)));
                            break;
                        }
                        case BG_CAPTURE_FAILED: {
                            info("BG_CAPTURE_FAILED id=" + id(latest)
                                    + " key=" + safe(chain.getArg(0))
                                    + " code=" + safe(chain.getArg(1))
                                    + " details=" + safe(chain.getArg(2)));
                            break;
                        }
                        default: break;
                    }
                    return value;
                });
                installed++;
            }
            info("HOOK " + className + "." + methodName
                    + " installed=" + installed);
        } catch (Throwable t) {
            info("HOOK_UNAVAILABLE " + className + "." + methodName
                    + " reason=" + t.getClass().getSimpleName());
        }
    }

    private static long id(Shot shot) {
        return shot == null ? -1 : shot.id;
    }

    private static long elapsed(Shot shot) {
        return shot == null ? -1 : shot.elapsedMs();
    }

    private static long prelim(Shot shot) {
        return shot == null ? -1 : shot.preliminaryMs;
    }

    private static String getPictureName(String metadata, Object fallback) {
        String key = "mPictureName=";
        int idx = metadata.indexOf(key);
        if (idx >= 0) {
            int start = idx + key.length();
            int end = metadata.indexOf(',', start);
            if (end < 0) end = metadata.indexOf('}', start);
            if (end < 0) end = metadata.length();
            return metadata.substring(start, end);
        }
        return safe(fallback);
    }

    private static String safe(Object object) {
        if (object == null) return "null";
        try {
            String value = String.valueOf(object);
            return value.length() > 240 ? value.substring(0, 240) + "..." : value;
        } catch (Throwable ignored) {
            return "<toString-error>";
        }
    }

    private void info(String message) {
        Log.i(TAG, message);
        log(Log.INFO, TAG, message);
    }
}
