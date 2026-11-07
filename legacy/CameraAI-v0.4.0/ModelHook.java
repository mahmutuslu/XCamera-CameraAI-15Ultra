package dev.cameraai.override;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ModelHook implements IXposedHookLoadPackage {
    private static final String TARGET = "madrid";
    private static final long EXTENDED_MS = 15000L;

    private static void log(String message) {
        XposedBridge.log("CameraAIOverride: " + message);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.camera".equals(lpparam.packageName)) return;
        log("v0.4 motion experiment loaded in " + lpparam.processName);
        try {
            XposedHelpers.findAndHookMethod("org.json.JSONObject", lpparam.classLoader,
                "put", String.class, Object.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if ("phone_model".equals(p.args[0])) {
                            log("JSONObject phone_model " + p.args[1] + " -> " + TARGET);
                            p.args[1] = TARGET;
                            return;
                        }
                        // Experimental A/B diagnosis: camera AI scene JSON only.
                        // DEX-confirmed scene constructors in Camera 6.8.002090.2:
                        //   rn.b.Q and un.a.a call JSONObject.put("motion", value).
                        if ("motion".equals(p.args[0]) && p.args[1] instanceof Number) {
                            boolean sceneContext = false;
                            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                                if (("rn.b".equals(frame.getClassName()) && "Q".equals(frame.getMethodName()))
                                    || ("un.a".equals(frame.getClassName()) && "a".equals(frame.getMethodName()))) {
                                    sceneContext = true;
                                    break;
                                }
                            }
                            if (!sceneContext) return;
                            Object original = p.args[1];
                            p.args[1] = Integer.valueOf(0);
                            log("AI scene motion original=" + original + " -> 0 (JSON only)");
                        }
                    }
                });
            log("JSONObject.put hook installed");
        } catch (Throwable t) { log("JSONObject hook failed: " + t); }

        try {
            XposedHelpers.findAndHookMethod("com.google.gson.JsonObject", lpparam.classLoader,
                "addProperty", String.class, String.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if ("phone_model".equals(p.args[0])) {
                            log("Gson phone_model " + p.args[1] + " -> " + TARGET);
                            p.args[1] = TARGET;
                        }
                    }
                });
            log("Gson.addProperty hook installed");
        } catch (Throwable t) { log("Gson hook unavailable: " + t); }

        // Xiaomi Camera 6.8.002090.2: Lpn/j;->n and
        // Lwn/b$a;->invokeSuspend call Lwd/i5;->f(J,Lvv/p;,Lmv/c;).
        // The two callers pass 3000ms and 3500ms respectively.
        // Only alter timeouts issued from those exact AI call sites.
        try {
            Class<?> timeoutClass = XposedHelpers.findClass("wd.i5", lpparam.classLoader);
            int count = 0;
            for (Method method : timeoutClass.getDeclaredMethods()) {
                if (!"f".equals(method.getName()) || !Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 3 || params[0] != long.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        long original = (Long) p.args[0];
                        if (original != 3000L && original != 3500L) return;

                        String caller = null;
                        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                            String cls = frame.getClassName();
                            String name = frame.getMethodName();
                            if (original == 3000L && "pn.j".equals(cls) && "n".equals(name)) {
                                caller = "pollResult";
                                break;
                            }
                            if (original == 3500L && "wn.b$a".equals(cls) && "invokeSuspend".equals(name)) {
                                caller = "pipeline";
                                break;
                            }
                        }
                        if (caller == null) return;
                        p.args[0] = EXTENDED_MS;
                        log("AI timeout " + caller + " " + original + " -> " + EXTENDED_MS + "ms");
                    }
                });
                count++;
            }
            log("wd.i5.f timeout hooks installed=" + count);
        } catch (Throwable t) {
            log("wd.i5.f timeout hook failed: " + t);
        }
    }
}
