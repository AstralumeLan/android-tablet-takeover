package com.astra.takeover;
import android.content.*;
// Protected by com.astra.takeover.permission.CONTROL (signature). Root (uid 0) and system bypass the check; shell/3rd-party apps cannot send.
public class Ctl extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    String a = i.getAction(); if (a == null) return;
    if (a.equals("com.astra.takeover.SETPIN")) {
      String salt = i.getStringExtra("salt"), hash = i.getStringExtra("hash"); int it = i.getIntExtra("iter", 0);
      if (salt == null || hash == null || it < 1000 || salt.length() < 16 || hash.length() != 64) { setResultData("ERR bad args"); return; }
      Lock.setPin(c, salt, hash, it); setResultData("OK pin set"); return;
    }
    if (a.equals("com.astra.takeover.DUMP") || a.equals("com.astra.takeover.CLICK")) {
      final Ui u = Ui.inst; if (u == null) { setResultData("ERR accessibility service not connected"); return; }
      final PendingResult pr = goAsync(); final boolean click = a.equals("com.astra.takeover.CLICK");
      final boolean all = !"active".equals(i.getStringExtra("mode")); final boolean inline = i.getBooleanExtra("inline", true);
      final String tx = i.getStringExtra("text"), id = i.getStringExtra("id"); final int nth = i.getIntExtra("n", 0);
      new Thread(() -> {
        String res;
        try {
          if (click) res = u.click(tx, id, nth);
          else {
            String d = u.dump(all, i.getBooleanExtra("raw", false));
            try (java.io.FileOutputStream fo = new java.io.FileOutputStream(new java.io.File(c.getFilesDir(), "ui.txt"))) { fo.write(d.getBytes("UTF-8")); }
            res = inline ? d : ("OK file=" + c.getFilesDir() + "/ui.txt " + d.substring(d.lastIndexOf("END ")).trim());
          }
        } catch (Throwable t) { res = "ERR " + t; }
        pr.setResultData(res); pr.finish();
      }).start();
      return;
    }
    if (a.equals("com.astra.takeover.CLEARPIN")) { Lock.clearPin(c); setResultData("OK pin cleared, lock disabled"); }
    else if (a.equals("com.astra.takeover.TESTFAILS")) { Lock.setFails(c, i.getIntExtra("n", 0)); setResultData("OK fails=" + i.getIntExtra("n", 0)); return; }
    else if (a.equals("com.astra.takeover.STATE")) { setResultData(Lock.state(c)); return; }
    else if (a.equals("com.astra.takeover.LOCK") && !Lock.hasPin(c)) { setResultData("ERR no pin set"); return; }
    else setResultData("OK " + a.substring(a.lastIndexOf('.') + 1));
    Intent s = new Intent(c, TakeoverService.class); s.setAction("com.astra.takeover.I_" + a.substring(a.lastIndexOf('.') + 1));
    c.startForegroundService(s);
  }
}
