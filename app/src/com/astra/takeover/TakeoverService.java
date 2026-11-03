package com.astra.takeover;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
public class TakeoverService extends Service {
  WindowManager wm; LinearLayout bar; TextView tv; Button btn; boolean shown=false, armed=false;
  Handler h = new Handler(Looper.getMainLooper());
  String text = "AI 正在接管此设备";
  static TakeoverService inst; Lock lock; boolean barWanted=false;
  final java.util.concurrent.ExecutorService rootq = java.util.concurrent.Executors.newSingleThreadExecutor();
  void root(String cmd){ rootq.execute(() -> Root.run(cmd)); }
  BroadcastReceiver scr = new BroadcastReceiver(){ public void onReceive(Context c, Intent i){
    String a=i.getAction();
    if (Intent.ACTION_SCREEN_OFF.equals(a)) lock.lock("screen off");
    else if (lock.locked){ lock.attach(); root("cmd statusbar collapse"); }
  }};
  void onLockChanged(boolean l){ if (l) removeBar(); else if (barWanted) addBar(); }
  public IBinder onBind(Intent i){return null;}
  int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
  public void onCreate(){
    super.onCreate();
    NotificationManager nm=getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("t","接管状态",NotificationManager.IMPORTANCE_MIN));
    startForeground(1,new Notification.Builder(this,"t").setContentTitle("AI 接管中").setSmallIcon(android.R.drawable.ic_menu_view).build());
    wm=(WindowManager)getSystemService(WINDOW_SERVICE);
    bar=new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL);
    GradientDrawable bg=new GradientDrawable(); bg.setColor(0xE0202020); bg.setCornerRadius(dp(16)); bg.setStroke(dp(1),0xFFFF9800);
    bar.setBackground(bg); bar.setPadding(dp(12),dp(2),dp(4),dp(2));
    TextView dot=new TextView(this); dot.setText("● "); dot.setTextColor(0xFFFF9800); dot.setTextSize(13); bar.addView(dot);
    tv=new TextView(this); tv.setTextColor(Color.WHITE); tv.setTextSize(13); tv.setSingleLine(true);
    tv.setEllipsize(android.text.TextUtils.TruncateAt.END); tv.setMaxWidth(dp(560)); bar.addView(tv);
    btn=new Button(this); btn.setText("退出接管"); btn.setTextSize(12); btn.setTextColor(Color.WHITE); btn.setAllCaps(false);
    GradientDrawable bb=new GradientDrawable(); bb.setColor(0xFFD32F2F); bb.setCornerRadius(dp(12)); btn.setBackground(bb);
    btn.setMinHeight(0); btn.setMinimumHeight(0); btn.setPadding(dp(10),dp(2),dp(10),dp(2));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(26)); lp.leftMargin=dp(10);
    bar.addView(btn,lp);
    btn.setOnClickListener(v->{
      if(!armed){armed=true; btn.setText("再点一次确认"); h.postDelayed(()->{armed=false;btn.setText("退出接管");},4000); return;}
      armed=false; tv.setText("正在断开 AI 连接…");
      new Thread(()->{ final String r=Root.exitTakeover(false); h.post(()->{ Toast.makeText(this,"已退出接管，无线ADB已关闭（密码锁仍生效）",Toast.LENGTH_LONG).show(); hide(); }); }).start();
    });
    tv.setText(text);
    IntentFilter f=new IntentFilter();
    for(String a:new String[]{"com.astra.takeover.STATUS","com.astra.takeover.SHOW","com.astra.takeover.HIDE","com.astra.takeover.DRYRUN"}) f.addAction(a);
    registerReceiver(dyn,f,Context.RECEIVER_EXPORTED);
    inst=this; lock=new Lock(this);
    IntentFilter sf=new IntentFilter(Intent.ACTION_SCREEN_OFF); sf.addAction(Intent.ACTION_SCREEN_ON); sf.addAction(Intent.ACTION_USER_PRESENT);
    registerReceiver(scr,sf);
    if (Lock.hasPin(this)) lock.lock("service start"); else root("cmd statusbar send-disable-flag none");
  }
  BroadcastReceiver dyn=new BroadcastReceiver(){ public void onReceive(Context c,Intent i){ onStartCommand(i,0,0);} };
  void show(){ barWanted=true; if(!lock.locked) addBar(); }
  void hide(){ barWanted=false; removeBar(); }
  void addBar(){
    if(shown) return;
    WindowManager.LayoutParams p=new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
    p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; p.y=dp(2);
    try{ wm.addView(bar,p); shown=true; }catch(Exception e){ android.util.Log.e("takeover","addView",e); }
  }
  void removeBar(){ if(shown){ try{wm.removeView(bar);}catch(Exception e){} shown=false; } }
  public int onStartCommand(Intent i,int f,int id){
    String a = i==null?null:i.getAction();
    String t = i==null?null:i.getStringExtra("text");
    if(t!=null){ text=t; tv.setText(t); }
    if("com.astra.takeover.I_LOCK".equals(a)) lock.lock("remote");
    else if("com.astra.takeover.I_UNLOCK".equals(a)){ Lock.sp(this).edit().putInt("fails",0).putLong("until",0).commit(); lock.unlock(); }
    else if("com.astra.takeover.I_CLEARPIN".equals(a)) lock.unlock();
    else if("com.astra.takeover.BOOTLOCK".equals(a)) { if(!lock.locked) lock.lock("boot"); }
    else if("com.astra.takeover.HIDE".equals(a)) hide();
    else if("com.astra.takeover.DRYRUN".equals(a)){ show(); new Thread(()->{ String r=Root.exitTakeover(true); h.post(()->tv.setText("DRYRUN: "+r.replace('\n',' '))); }).start(); }
    else show();
    return START_STICKY;
  }
  public void onDestroy(){ try{unregisterReceiver(dyn);unregisterReceiver(scr);}catch(Exception e){} removeBar(); lock.detach(); inst=null;
    Thread t=new Thread(()->Root.run("cmd statusbar send-disable-flag none")); t.start(); try{t.join(3000);}catch(Exception e){} super.onDestroy(); }
}
