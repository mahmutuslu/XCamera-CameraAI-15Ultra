package dev.cameraai.override;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unified CameraAI + XCamera v2.4, modern Xposed API 102, Pixel175 diagnostics + OPT-IN UI.
 *
 * This module NEVER changes Camera ID, stream size, zoom, method arguments,
 * method returns, sensor RAW, or scheduling. UI hint is disabled by default. It is NOT a v2.3 replacement.
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
    private static final ConcurrentLinkedDeque<Shot> PENDING =
            new ConcurrentLinkedDeque<>();
    private static volatile Shot latest;
    private static volatile boolean PROCESSING_TIP_ENABLED;

    private static final class Shot {
        final long id;
        final long startedNs;
        final long startedWallMs;
        volatile long preliminaryMs = -1;
        Shot(long id) {
            this.id = id;
            this.startedNs = SystemClock.elapsedRealtimeNanos();
            this.startedWallMs = System.currentTimeMillis();
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
        PROCESSING_TIP_ENABLED = "1".equals(readDeviceProperty("debug.cameraai.processingtip"));
        info("API=102 version=2.9.0-UNIFIED-175-FOUR mode=DIAGNOSTIC uiOptIn="
                + PROCESSING_TIP_ENABLED + " correlation=FILENAME_TIME_APPROX");
        ProcessingHint.setOEMLook("1".equals(readDeviceProperty("debug.cameraai.oem175ui")));
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
        if (PROCESSING_TIP_ENABLED) {
            installCameraWindowHook(loader);
        }
        info("INSTALL_COMPLETE; UI tile-reveal waits for stable on-disk 200MP JPEG after MiVi completion");
        info("JPEG requires independent CAPTURE-END and 16320x12288 validation");
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
                        if (PROCESSING_TIP_ENABLED) ProcessingHint.update(true, "CAPTURE_START");
                        while (PENDING.size() > 16) {
                            Shot stale = PENDING.pollFirst();
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
                            // In the 18 Pro port this callback can run before
                            // PixelModule.onCaptureStart. Such events must stay unmatched.
                            Shot shot = PENDING.peekLast();
                            boolean matched = shot != null
                                    && shot.preliminaryMs < 0
                                    && shot.elapsedMs() <= 1800;
                            if (matched) shot.preliminaryMs = shot.elapsedMs();
                            info("SHOT2SHOT_FINISH id=" + (matched ? id(shot) : -1)
                                    + " match=" + (matched ? "RECENT_PENDING" : "UNMATCHED_PRESTART_OR_LATE")
                                    + " preliminary=" + safe(chain.getArg(0))
                                    + " elapsedMs=" + (matched ? elapsed(shot) : -1)
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
                            Match correlation = matchFilenameToShot(file);
                            Shot shot = correlation.shot;
                            info("MIVI_SUCCESS id=" + id(shot)
                                    + " match=" + correlation.label
                                    + " filenameDeltaMs=" + correlation.deltaMs
                                    + " file=" + file
                                    + " elapsedMs=" + elapsed(shot)
                                    + " preliminaryMs=" + prelim(shot)
                                    + " metadata=" + metadata);
                            if (PROCESSING_TIP_ENABLED && PENDING.isEmpty()) ProcessingHint.finalImage(file);
                            break;
                        }
                        case MIVI_ERROR: {
                            Shot shot = PENDING.pollFirst();
                            info("MIVI_ERROR id=" + id(shot)
                                    + " match=FIFO_HEURISTIC"
                                    + " elapsedMs=" + elapsed(shot)
                                    + " reason=" + safe(chain.getArg(0)));
                            if (PROCESSING_TIP_ENABLED && PENDING.isEmpty()) ProcessingHint.error("MIVI_ERROR");
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

    private void installCameraWindowHook(ClassLoader loader) {
        try {
            Class<?> camera = Class.forName("com.android.camera.Camera", false, loader);
            Method focus = camera.getDeclaredMethod("onWindowFocusChanged", boolean.class);
            hook(focus).intercept(chain -> {
                Object result = chain.proceed();
                // UI errors must never propagate into the camera event handler.
                try {
                    if (chain.getThisObject() instanceof Activity) {
                        Activity activity = (Activity) chain.getThisObject();
                        boolean hasFocus = Boolean.TRUE.equals(chain.getArg(0));
                        ProcessingHint.onCameraFocus(activity, hasFocus, !PENDING.isEmpty());
                    }
                } catch (Throwable t) {
                    info("UI_FOCUS_SKIPPED reason=" + t.getClass().getSimpleName());
                }
                return result;
            });
            info("HOOK Camera.onWindowFocusChanged installed=1; UI opt-in enabled");
        } catch (Throwable t) {
            info("UI_HOOK_UNAVAILABLE " + t.getClass().getSimpleName());
        }
    }

    private static String readDeviceProperty(String key) {
        try {
            Class<?> props = Class.forName("android.os.SystemProperties");
            Method method = props.getMethod("get", String.class, String.class);
            Object value = method.invoke(null, key, "0");
            return value == null ? "0" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "0";
        }
    }

    // Timestamp from IMG_yyyyMMdd_HHmmss.jpg has SECOND resolution only.
    // This is a heuristic, not a trusted capture/frame identity.
    private static final class Match {
        final Shot shot;
        final String label;
        final long deltaMs;
        Match(Shot shot, String label, long deltaMs) {
            this.shot = shot;
            this.label = label;
            this.deltaMs = deltaMs;
        }
    }

    private static Match matchFilenameToShot(String filename) {
        long filenameMs = parseFilenameMs(filename);
        synchronized (PENDING) {
            Shot best = null;
            long nearest = Long.MAX_VALUE;
            if (filenameMs != -1L) {
                for (Shot candidate : PENDING) {
                    long difference = Math.abs(candidate.startedWallMs - filenameMs);
                    if (difference <= 1800 && difference < nearest) {
                        nearest = difference;
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                PENDING.remove(best);
                return new Match(best, "FILENAME_TIME_APPROX", nearest);
            }
            Shot first = PENDING.pollFirst();
            return new Match(first, first == null ? "NO_PENDING" : "FIFO_HEURISTIC", -1);
        }
    }

    private static long parseFilenameMs(String filename) {
        if (filename == null) return -1;
        int idx = filename.indexOf("IMG_");
        if (idx < 0 || idx + 19 > filename.length()) return -1;
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            fmt.setLenient(false);
            Date parsed = fmt.parse(filename.substring(idx + 4, idx + 19));
            return parsed == null ? -1 : parsed.getTime();
        } catch (ParseException ignored) {
            return -1;
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
