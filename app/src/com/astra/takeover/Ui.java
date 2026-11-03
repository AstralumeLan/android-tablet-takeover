package com.astra.takeover;
import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.*;
import java.util.List;
// UI-tree reader. Does nothing on events; only answers root-only DUMP / CLICK requests forwarded by .Ctl.
// Never touches the takeover lock overlay (its windows are skipped) and refuses clicks while locked.
public class Ui extends AccessibilityService {
  static volatile Ui inst;
  protected void onServiceConnected(){ inst = this; }
  public void onAccessibilityEvent(AccessibilityEvent e){}
  public void onInterrupt(){}
  public boolean onUnbind(android.content.Intent i){ inst = null; return super.onUnbind(i); }
  public void onDestroy(){ inst = null; super.onDestroy(); }

  static boolean locked(){ TakeoverService t = TakeoverService.inst; return t != null && t.lock != null && t.lock.locked; }
  static String wtype(int t){ switch(t){ case 1: return "APP"; case 2: return "IME"; case 3: return "SYS"; case 4: return "OVERLAY"; case 5: return "MAG"; case 6: return "SPLIT"; default: return ""+t; } }
  static String esc(CharSequence c){ if(c==null) return null; String s=c.toString().replace('\n',' ').replace('\r',' ').replace('"','\''); return s.length()>150? s.substring(0,150)+"…" : s; }

  int count; boolean raw; StringBuilder out; final Rect r = new Rect();
  String dump(boolean all){ return dump(all,false); }
  // v2.4: the service only subscribes to announcement events, so the framework's AccessibilityCache never saw
  // scroll/content-changed events and kept returning stale nodes (e.g. a shopping app's list after a swipe).
  // Drop the whole cache before every DUMP/CLICK so roots and children are fetched fresh from the app.
  void fresh(){ if (android.os.Build.VERSION.SDK_INT >= 33) { try { clearCache(); } catch (Throwable t) {} } }
  String dump(boolean all, boolean rawMode){
    fresh();
    raw = rawMode;
    long t0 = System.currentTimeMillis(); out = new StringBuilder(32768); count = 0;
    out.append("LOCK locked=").append(locked()).append('\n');
    if (all) {
      List<AccessibilityWindowInfo> ws = getWindows(); int wi = 0;
      for (AccessibilityWindowInfo w : ws) {
        AccessibilityNodeInfo root = w.getRoot(); w.getBoundsInScreen(r);
        String pkg = root!=null && root.getPackageName()!=null ? root.getPackageName().toString() : "?";
        out.append("W").append(wi).append(' ').append(wtype(w.getType())).append(" pkg=").append(pkg)
           .append(" [").append(r.left).append(',').append(r.top).append(',').append(r.right).append(',').append(r.bottom).append(']')
           .append(w.isActive()?" active":"").append(w.isFocused()?" focused":"");
        CharSequence ti = w.getTitle(); if (ti!=null) out.append(" title=\"").append(esc(ti)).append('"');
        if ("com.astra.takeover".equals(pkg)) { out.append(locked() && w.getType()==4 ? " <TAKEOVER OVERLAY: content not dumped>\n" : " <takeover window skipped>\n"); wi++; continue; }
        out.append('\n');
        if (root != null) walk(root, wi+"", 1);
        wi++;
      }
    } else {
      AccessibilityNodeInfo root = getRootInActiveWindow();
      if (root != null && !"com.astra.takeover".contentEquals(root.getPackageName()==null?"":root.getPackageName())) { out.append("W0 active pkg=").append(root.getPackageName()).append('\n'); walk(root, "0", 1); }
      else out.append("W0 <no active window or takeover overlay>\n");
    }
    out.append("END nodes=").append(count).append(" ms=").append(System.currentTimeMillis()-t0).append('\n');
    return out.toString();
  }
  void walk(AccessibilityNodeInfo n, String path, int depth){
    if (n == null || count > 6000) return;
    if (!n.isVisibleToUser()) return;
    count++;
    String tx = esc(n.getText()), cd = esc(n.getContentDescription()), id = n.getViewIdResourceName();
    if (id != null) { int k = id.indexOf(":id/"); if (k >= 0) id = id.substring(k+4); }
    boolean cl = n.isClickable(), lc = n.isLongClickable(), ed = n.isEditable(), sc = n.isScrollable();
    CharSequence hint = android.os.Build.VERSION.SDK_INT>=26 ? n.getHintText() : null;
    CharSequence sd = android.os.Build.VERSION.SDK_INT>=30 ? n.getStateDescription() : null;
    CharSequence tt = android.os.Build.VERSION.SDK_INT>=28 ? n.getTooltipText() : null;
    if ((tx==null||tx.length()==0) && hint!=null) tx = "hint:"+esc(hint);
    if ((cd==null||cd.length()==0) && sd!=null) cd = "state:"+esc(sd);
    if ((cd==null||cd.length()==0) && tt!=null) cd = "tip:"+esc(tt);
    boolean show = raw || (tx!=null && tx.length()>0) || (cd!=null && cd.length()>0) || cl || ed || sc || n.isChecked() || n.isSelected() || (id!=null && depth<=3);
    if (show) {
      for (int i=0;i<depth && i<40;i++) out.append(' ');
      CharSequence cn = n.getClassName(); String c = cn==null?"?":cn.toString(); c = c.substring(c.lastIndexOf('.')+1);
      n.getBoundsInScreen(r);
      out.append(path).append(' ').append(c);
      if (tx!=null && tx.length()>0) out.append(" \"").append(tx).append('"');
      if (cd!=null && cd.length()>0) out.append(" d=\"").append(cd).append('"');
      if (id!=null) out.append(" #").append(id);
      out.append(" [").append(r.left).append(',').append(r.top).append(',').append(r.right).append(',').append(r.bottom).append(']');
      if (cl) out.append(" C"); if (lc) out.append(" L"); if (n.isCheckable()) out.append(n.isChecked()?" CHK+":" CHK-");
      if (n.isSelected()) out.append(" SEL"); if (ed) out.append(" E"); if (sc) out.append(" SCR"); if (!n.isEnabled()) out.append(" DIS");
      out.append('\n');
    }
    int k = n.getChildCount();
    for (int i=0;i<k;i++){ AccessibilityNodeInfo ch = n.getChild(i); if (ch!=null) walk(ch, path+"."+i, show?depth+1:depth); }
  }

  // CLICK: find nth node whose text/desc contains `text` (or viewId == id), climb to clickable ancestor, ACTION_CLICK.
  String click(String text, String id, int nth){
    if (locked()) return "ERR locked";
    fresh();
    int[] seen = {0};
    for (AccessibilityWindowInfo w : getWindows()){
      AccessibilityNodeInfo root = w.getRoot(); if (root==null) continue;
      if ("com.astra.takeover".contentEquals(root.getPackageName()==null?"":root.getPackageName())) continue;
      AccessibilityNodeInfo f = find(root, text, id, nth, seen);
      if (f != null){
        AccessibilityNodeInfo c = f; while (c != null && !c.isClickable()) c = c.getParent();
        if (c == null) { f.getBoundsInScreen(r); return "ERR not clickable " + r.toShortString(); }
        c.getBoundsInScreen(r); boolean ok = c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return (ok?"OK clicked ":"ERR click failed ") + r.toShortString();
      }
    }
    return "ERR not found";
  }
  AccessibilityNodeInfo find(AccessibilityNodeInfo n, String text, String id, int nth, int[] seen){
    if (n == null || !n.isVisibleToUser()) return null;
    boolean m = false;
    if (text != null){ CharSequence a = n.getText(), b = n.getContentDescription(); m = (a!=null && a.toString().contains(text)) || (b!=null && b.toString().contains(text)); }
    if (id != null){ String v = n.getViewIdResourceName(); m = v != null && (v.equals(id) || v.endsWith(":id/"+id)); }
    if (m && seen[0]++ == nth) return n;
    for (int i=0;i<n.getChildCount();i++){ AccessibilityNodeInfo r2 = find(n.getChild(i), text, id, nth, seen); if (r2 != null) return r2; }
    return null;
  }
}
