package com.astra.takeover;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Lock {
  static final String TAG = "TOLock";
  // ---------- storage: app-private SharedPreferences, salted PBKDF2 hash only ----------
  static SharedPreferences sp(Context c){ return c.getSharedPreferences("lock", Context.MODE_PRIVATE); }
  static boolean hasPin(Context c){ return sp(c).getString("hash", null) != null; }
  static void setPin(Context c, String salt, String hash, int iter){
    sp(c).edit().putString("salt", salt.toLowerCase()).putString("hash", hash.toLowerCase()).putInt("iter", iter).putInt("fails",0).putLong("until",0).commit();
  }
  static void clearPin(Context c){ sp(c).edit().clear().commit(); }
  static void setFails(Context c, int n){ sp(c).edit().putInt("fails", n).putLong("until", 0).commit(); }
  static String state(Context c){
    SharedPreferences s = sp(c); long left = Math.max(0, s.getLong("until",0) - System.currentTimeMillis());
    TakeoverService ts = TakeoverService.inst;
    return "pin=" + (s.getString("hash",null)!=null) + " iter=" + s.getInt("iter",0) + " fails=" + s.getInt("fails",0)
      + " cooldown_ms=" + left + " service=" + (ts!=null) + " locked=" + (ts!=null && ts.lock!=null && ts.lock.locked)
      + " overlay=" + (ts!=null && ts.lock!=null && ts.lock.attached);
  }
  static byte[] hex(String h){ byte[] b=new byte[h.length()/2]; for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(h.substring(2*i,2*i+2),16); return b; }
  static String toHex(byte[] b){ StringBuilder sb=new StringBuilder(); for(byte x:b) sb.append(String.format("%02x",x)); return sb.toString(); }
  static boolean verify(Context c, char[] pin){
    SharedPreferences s = sp(c); String salt=s.getString("salt",null), hash=s.getString("hash",null); int it=s.getInt("iter",0);
    if (salt==null||hash==null) return false;
    try {
      PBEKeySpec ks = new PBEKeySpec(pin, hex(salt), it, 256);
      byte[] d = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(ks).getEncoded(); ks.clearPassword();
      byte[] e = hex(hash); int diff = d.length ^ e.length; for (int i=0;i<Math.min(d.length,e.length);i++) diff |= d[i]^e[i];
      return diff==0;
    } catch (Exception ex){ android.util.Log.e(TAG,"verify", ex); return false; }
  }
  // cooldown after 5 wrong: 30s, then doubles on every further wrong attempt (max 32 min)
  static long cooldownFor(int fails){ if (fails < 5) return 0; return 30000L << Math.min(fails-5, 6); }

  // ---------- overlay ----------
  final TakeoverService svc; final WindowManager wm; final Handler h = new Handler(Looper.getMainLooper());
  boolean locked=false, attached=false, checking=false;
  FrameLayout root; TextView dots, msg; final StringBuilder entry = new StringBuilder();
  Lock(TakeoverService s){ svc=s; wm=(WindowManager)s.getSystemService(Context.WINDOW_SERVICE); build(); }
  int dp(int v){ return (int)(v*svc.getResources().getDisplayMetrics().density+0.5f); }

  void build(){
    root = new FrameLayout(svc){
      public boolean dispatchKeyEvent(KeyEvent e){
        int k=e.getKeyCode();
        if (e.getAction()==KeyEvent.ACTION_UP && k>=KeyEvent.KEYCODE_0 && k<=KeyEvent.KEYCODE_9) press((char)('0'+k-KeyEvent.KEYCODE_0));
        else if (e.getAction()==KeyEvent.ACTION_UP && k>=KeyEvent.KEYCODE_NUMPAD_0 && k<=KeyEvent.KEYCODE_NUMPAD_9) press((char)('0'+k-KeyEvent.KEYCODE_NUMPAD_0));
        else if (e.getAction()==KeyEvent.ACTION_UP && k==KeyEvent.KEYCODE_DEL) press('<');
        else if (e.getAction()==KeyEvent.ACTION_UP && (k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_NUMPAD_ENTER)) press('#');
        if (k==KeyEvent.KEYCODE_VOLUME_UP||k==KeyEvent.KEYCODE_VOLUME_DOWN||k==KeyEvent.KEYCODE_POWER) return super.dispatchKeyEvent(e);
        return true; // swallow BACK, MENU, etc.
      }
      public void onWindowFocusChanged(boolean f){ super.onWindowFocusChanged(f); if(!f && locked){ guardN=0; h.removeCallbacks(guard); h.post(guard); } }
      public boolean onTouchEvent(MotionEvent e){ return true; }
    };
    root.setBackgroundColor(0xFF000000); root.setClickable(true); root.setFocusable(true); root.setFocusableInTouchMode(true);
    LinearLayout col = new LinearLayout(svc); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_HORIZONTAL);
    TextView title = new TextView(svc); title.setText("设备已锁定"); title.setTextColor(Color.WHITE); title.setTextSize(24); title.setGravity(Gravity.CENTER);
    col.addView(title);
    dots = new TextView(svc); dots.setTextColor(Color.WHITE); dots.setTextSize(28); dots.setGravity(Gravity.CENTER); dots.setMinHeight(dp(48)); dots.setText(" ");
    col.addView(dots);
    msg = new TextView(svc); msg.setTextColor(0xFFFF9800); msg.setTextSize(15); msg.setGravity(Gravity.CENTER); msg.setMinHeight(dp(28)); msg.setText("请输入密码");
    col.addView(msg);
    String[] keys = {"1","2","3","4","5","6","7","8","9","删除","0","确定"};
    GridLayout g = new GridLayout(svc); g.setColumnCount(3);
    for (final String k: keys){
      Button b = new Button(svc); b.setText(k); b.setTextSize(k.length()==1?26:18); b.setTextColor(Color.WHITE); b.setAllCaps(false);
      android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
      bg.setColor(k.equals("确定")?0xFF2E7D32:0xFF303030); b.setBackground(bg);
      GridLayout.LayoutParams lp = new GridLayout.LayoutParams(); lp.width=dp(84); lp.height=dp(84); lp.setMargins(dp(10),dp(8),dp(10),dp(8));
      b.setTag("key_"+k);
      b.setOnClickListener(v -> press(k.equals("删除")?'<':k.equals("确定")?'#':k.charAt(0)));
      g.addView(b, lp);
    }
    LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); glp.topMargin=dp(8);
    col.addView(g, glp);
    root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    // log key centers (coordinates only) so automation can tap them
    root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
      StringBuilder sb = new StringBuilder("keys");
      for (int i=0;i<g.getChildCount();i++){ View c=g.getChildAt(i); int[] p=new int[2]; c.getLocationOnScreen(p);
        sb.append(' ').append(keys[i].equals("删除")?"DEL":keys[i].equals("确定")?"OK":keys[i]).append('=').append(p[0]+c.getWidth()/2).append(',').append(p[1]+c.getHeight()/2); }
      android.util.Log.i(TAG, sb.toString());
    });
  }

  // While locked and not focused: collapse the shade, and close system dialogs that hide app overlays
  // (home-app chooser when no default launcher, package installer, permission dialogs). Re-check every 1s, up to 20s.
  static final String GUARD = "cmd statusbar collapse; sleep 0.4; f=$(dumpsys window | grep -m1 mCurrentFocus); case \"$f\" in *TakeoverLock*) ;; *ResolverActivity*|*ChooserActivity*|*packageinstaller*|*permissioncontroller*) input keyevent KEYCODE_BACK;; esac";
  int guardN=0;
  final Runnable guard = new Runnable(){ public void run(){
    if (!locked || !attached || root.hasWindowFocus() || guardN++ > 20) return;
    svc.root(GUARD); h.postDelayed(this, 1000);
  }};
  void press(char k){
    if (!locked || checking) return;
    long left = sp(svc).getLong("until",0) - System.currentTimeMillis();
    if (left > 0){ entry.setLength(0); dots.setText(" "); showCooldown(); return; }
    if (k=='<'){ if(entry.length()>0) entry.setLength(entry.length()-1); }
    else if (k=='#'){ submit(); return; }
    else if (entry.length() < 16) entry.append(k);
    StringBuilder d=new StringBuilder(); for(int i=0;i<entry.length();i++) d.append("● "); dots.setText(d.length()==0?" ":d.toString().trim());
    msg.setText("请输入密码");
  }
  void submit(){
    if (entry.length()==0) return;
    final char[] pin = new char[entry.length()]; entry.getChars(0, entry.length(), pin, 0);
    for (int i=0;i<entry.length();i++) entry.setCharAt(i,'0'); entry.setLength(0);
    checking = true; msg.setText("验证中…");
    new Thread(() -> {
      boolean ok = verify(svc, pin); java.util.Arrays.fill(pin, '\0');
      h.post(() -> {
        checking = false; dots.setText(" ");
        SharedPreferences s = sp(svc);
        if (ok){ s.edit().putInt("fails",0).putLong("until",0).commit(); android.util.Log.i(TAG,"unlock by pin"); unlock(); return; }
        int f = s.getInt("fails",0)+1; long cd = cooldownFor(f);
        s.edit().putInt("fails",f).putLong("until", cd>0? System.currentTimeMillis()+cd : 0).commit();
        android.util.Log.i(TAG,"wrong pin, fails="+f+" cooldown_ms="+cd);
        if (cd>0) showCooldown(); else msg.setText("密码错误（" + f + "/5）");
      });
    }).start();
  }
  final Runnable tick = new Runnable(){ public void run(){ showCooldown(); } };
  void showCooldown(){
    h.removeCallbacks(tick);
    long left = sp(svc).getLong("until",0) - System.currentTimeMillis();
    if (left <= 0){ msg.setText("请输入密码"); return; }
    msg.setText("错误次数过多，请 " + ((left+999)/1000) + " 秒后再试"); h.postDelayed(tick, 1000);
  }

  void lock(String why){
    if (!hasPin(svc)) { android.util.Log.w(TAG,"lock ignored (no pin): "+why); return; }
    locked = true; entry.setLength(0); dots.setText(" "); showCooldown(); if (sp(svc).getLong("until",0) <= System.currentTimeMillis()) msg.setText("请输入密码");
    attach(); svc.onLockChanged(true);
    svc.root("cmd statusbar collapse; cmd statusbar send-disable-flag statusbar-expansion notification-peek home recents");
    android.util.Log.i(TAG,"locked: "+why);
  }
  void unlock(){
    locked = false; entry.setLength(0); h.removeCallbacks(tick); h.removeCallbacks(guard);
    detach(); svc.root("cmd statusbar send-disable-flag none"); svc.onLockChanged(false);
    android.util.Log.i(TAG,"unlocked");
  }
  void attach(){
    if (attached) return;
    WindowManager.LayoutParams p = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.OPAQUE);
    p.gravity = Gravity.TOP|Gravity.LEFT; p.setTitle("TakeoverLock");
    if (Build.VERSION.SDK_INT >= 30) { p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS; p.setFitInsetsTypes(0); }
    else p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    try { wm.addView(root, p); attached = true; } catch (Exception e){ android.util.Log.e(TAG,"addView",e); return; }
    root.requestFocus();
    if (Build.VERSION.SDK_INT >= 30) h.post(() -> { try { WindowInsetsController c = root.getWindowInsetsController(); if (c!=null){ c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); c.hide(WindowInsets.Type.systemBars()); } } catch (Exception e){} });
  }
  void detach(){ if (attached){ try { wm.removeView(root); } catch (Exception e){} attached=false; } }
}
