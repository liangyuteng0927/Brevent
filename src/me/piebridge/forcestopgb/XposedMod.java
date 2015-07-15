package me.piebridge.forcestopgb;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import me.piebridge.forcestopgb.common.CommonIntent;
import me.piebridge.forcestopgb.hook.Hook;
import me.piebridge.forcestopgb.hook.HookResult;
import me.piebridge.forcestopgb.hook.SystemHook;

public class XposedMod implements IXposedHookZygoteInit {

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Class<?> ActivityThread = Class.forName("android.app.ActivityThread"); // NOSONAR
            XposedBridge.hookAllMethods(ActivityThread, "systemMain", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    android.util.Log.d(CommonIntent.TAG, "hook ActivityThread.systemMain, uid: " + Process.myUid());
                    hookSystem(Thread.currentThread().getContextClassLoader());
                }
            });
        } else {
            hookSystem(null);
        }

        hookZygote();
    }

    private static void hookSystem(ClassLoader classLoader) throws Throwable {
        SystemHook.setClassLoader(classLoader);

        Class<?> ActivityManagerService = Class.forName("com.android.server.am.ActivityManagerService", false, classLoader);
        Class<?> ProcessRecord = Class.forName("com.android.server.am.ProcessRecord", false, classLoader);
        Method startProcessLocked;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            startProcessLocked = ActivityManagerService.getDeclaredMethod("startProcessLocked", ProcessRecord, String.class, String.class);
        } else {
            startProcessLocked = ActivityManagerService.getDeclaredMethod("startProcessLocked", ProcessRecord, String.class, String.class, String.class, String.class, String[].class);
        }
        XposedBridge.hookMethod(startProcessLocked, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!SystemHook.beforeActivityManagerService$startProcessLocked(param.args)) {
                    param.setResult(null);
                }
            }
        });

        Class<?> IntentFilter = Class.forName("android.content.IntentFilter", false, classLoader);
        Method match = IntentFilter.getMethod("match", String.class, String.class, String.class, Uri.class, Set.class, String.class);
        XposedBridge.hookMethod(match, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                HookResult result = SystemHook.hookIntentFilter$match(param.thisObject, param.args);
                if (!result.isNone()) {
                    param.setResult(result.getResult());
                }
            }
        });
    }

    private static void hookZygote() {
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Hook.beforeActivity$onCreate((Activity) param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Hook.afterActivity$onDestroy((Activity) param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(Process.class, "killProcess", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int pid = (Integer) param.args[0];
                if (Process.myPid() == pid && Hook.stopSelf(pid)) {
                    param.setResult(null);
                }
            }
        });

        XposedHelpers.findAndHookMethod(System.class, "exit", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Hook.stopSelf(-1);
                param.setResult(null);
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "moveTaskToBack", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Hook.afterActivity$moveTaskToBack((Activity) param.thisObject, (Boolean) param.getResult());
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent = (Intent) param.args[0];
                if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
                    Hook.beforeActivity$startHomeActivityForResult((Activity) param.thisObject);
                }
            }
        });
    }
}
