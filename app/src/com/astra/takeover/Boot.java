package com.astra.takeover;
import android.content.*;
public class Boot extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    String a = i.getAction(); if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
    if (!Lock.hasPin(c)) return;
    Intent s = new Intent(c, TakeoverService.class); s.setAction("com.astra.takeover.BOOTLOCK");
    try { c.startForegroundService(s); } catch (Exception e) { android.util.Log.e("TOLock","boot start",e); }
  }
}
