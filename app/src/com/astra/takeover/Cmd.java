package com.astra.takeover;
import android.content.*;
public class Cmd extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    String a = i.getAction();
    // explicit intents bypass intent-filters, so whitelist: this public receiver can only touch the status bar, never the lock
    if (!("com.astra.takeover.STATUS".equals(a) || "com.astra.takeover.SHOW".equals(a) || "com.astra.takeover.HIDE".equals(a) || "com.astra.takeover.DRYRUN".equals(a))) return;
    Intent s = new Intent(c, TakeoverService.class);
    s.setAction(a);
    String t = i.getStringExtra("text"); if (t != null) s.putExtra("text", t);
    c.startForegroundService(s);
  }
}
