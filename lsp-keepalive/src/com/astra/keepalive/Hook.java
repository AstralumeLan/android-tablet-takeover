package com.astra.keepalive;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.*;
// system_server hooks: ZTE MindSync must not force-stop, and ZTE CpuFreezer must not freeze, the takeover-critical apps.
public class Hook implements IXposedHookLoadPackage {
  static final Set<String> PKGS = new HashSet<>(Arrays.asList("com.tailscale.ipn", "com.astra.takeover", "com.ai.assistance.operit"));
  // App IDs (uid % 100000) of the same apps. These are EXAMPLES: they differ per device/install.
  // Find yours with: adb shell dumpsys package <pkg> | grep userId=   then rebuild.
  static final Set<Integer> APPIDS = new HashSet<>(Arrays.asList(10354, 10357, 10342));
  static int blockedKills = 0, blockedFreezes = 0;
  public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
    if (!"android".equals(lp.packageName)) return;
    ClassLoader cl = lp.classLoader;
    try {
      Class<?> ams = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", cl);
      XposedBridge.hookAllMethods(ams, "zteEx_forceStopPackage", new XC_MethodHook() {
        protected void beforeHookedMethod(MethodHookParam p) {
          Object pkg = p.args.length > 0 ? p.args[0] : null;
          if (pkg instanceof String && PKGS.contains(pkg)) {
            p.setResult(null);
            if (blockedKills++ < 200) XposedBridge.log("AstraKeepAlive: blocked zteEx_forceStopPackage " + pkg + " reason=" + (p.args.length > 2 ? p.args[2] : "?"));
          }
        }
      });
      XposedBridge.log("AstraKeepAlive: hooked AMS.zteEx_forceStopPackage");
    } catch (Throwable t) { XposedBridge.log("AstraKeepAlive: AMS hook failed: " + t); }
    try {
      Class<?> fz = XposedHelpers.findClass("com.android.server.am.CpuFreezerManagerServiceV2", cl);
      XC_MethodHook noFreeze = new XC_MethodHook() {
        protected void beforeHookedMethod(MethodHookParam p) {
          // (int uid, String appName, boolean isToFreeze, String reason)
          if (p.args.length >= 3 && p.args[0] instanceof Integer && Boolean.TRUE.equals(p.args[2])) {
            int appId = ((Integer) p.args[0]) % 100000;
            if (APPIDS.contains(appId)) {
              p.setResult(Boolean.FALSE);
              if (blockedFreezes++ < 200) XposedBridge.log("AstraKeepAlive: blocked freeze uid=" + p.args[0] + " " + p.args[1] + " reason=" + (p.args.length > 3 ? p.args[3] : "?"));
            }
          }
        }
      };
      XposedBridge.hookAllMethods(fz, "doSetUidFreezeLocked", noFreeze);
      XposedBridge.hookAllMethods(fz, "doSetUidFreezer", noFreeze);
      XposedBridge.log("AstraKeepAlive: hooked CpuFreezerManagerServiceV2 freeze");
    } catch (Throwable t) { XposedBridge.log("AstraKeepAlive: freezer hook failed: " + t); }
  }
}
