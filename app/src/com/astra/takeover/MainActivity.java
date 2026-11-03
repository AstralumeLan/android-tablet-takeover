package com.astra.takeover;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
public class MainActivity extends Activity {
  protected void onCreate(Bundle b){
    super.onCreate(b);
    if(getIntent().getBooleanExtra("bg",false)){
      Intent s=new Intent(this,TakeoverService.class); s.setAction("com.astra.takeover.SHOW");
      String t=getIntent().getStringExtra("text"); if(t!=null) s.putExtra("text",t);
      startForegroundService(s); finish(); overridePendingTransition(0,0); return;
    }
    LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48,48,48,48);
    TextView t=new TextView(this); t.setTextSize(16);
    t.setText("AI 接管控制\n\n「退出接管」会关闭无线 ADB，AI 将无法再操作本机（重启后也不会自动恢复）。\n「恢复接管」会重新开启无线 ADB (5555)。");
    l.addView(t);
    Button exit=new Button(this); exit.setText("退出接管（关闭无线ADB）"); l.addView(exit);
    Button res=new Button(this); res.setText("恢复接管（开启无线ADB）"); l.addView(res);
    TextView out=new TextView(this); l.addView(out);
    Handler h=new Handler(Looper.getMainLooper());
    exit.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("确定退出接管？").setPositiveButton("退出",(d,w)->new Thread(()->{String r=Root.exitTakeover(false);h.post(()->out.setText(r));Intent hd=new Intent(this,TakeoverService.class); hd.setAction("com.astra.takeover.HIDE"); startForegroundService(hd);}).start()).setNegativeButton("取消",null).show());
    res.setOnClickListener(v->new Thread(()->{String r=Root.resume();h.post(()->out.setText(r));}).start());
    setContentView(l);
  }
}
