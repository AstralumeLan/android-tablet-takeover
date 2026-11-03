package com.astra.takeover;
import java.io.*;
public class Root {
  // run a shell script via su, return combined output
  public static String run(String script) {
    StringBuilder sb = new StringBuilder();
    try {
      Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
      OutputStream os = p.getOutputStream();
      os.write((script + "\nexit\n").getBytes()); os.flush(); os.close();
      BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String l; while ((l = r.readLine()) != null) sb.append(l).append('\n');
      sb.append("exit=").append(p.waitFor());
    } catch (Exception e) { sb.append("ERR ").append(e); }
    return sb.toString();
  }
  static final String LOG = "/sdcard/AI/takeover/exit.log";
  static final String FLAG = "/data/adb/takeover.disabled";
  public static String exitTakeover(boolean dry) {
    String ts = "$(date '+%Y-%m-%d %H:%M:%S')";
    String cut = "setprop service.adb.tcp.port -1; stop adbd; start adbd";
    String s = "mkdir -p /sdcard/AI/takeover; "
      + (dry ? "echo \"" + ts + " DRYRUN exit (would run: " + cut + ") uid=$(id -u) port=$(getprop service.adb.tcp.port)\" >> " + LOG
             : "echo \"" + ts + " EXIT pressed, wireless adb disabled\" >> " + LOG + "; touch " + FLAG + "; " + cut)
      + "; tail -1 " + LOG;
    return run(s);
  }
  public static String resume() {
    return run("mkdir -p /sdcard/AI/takeover; rm -f " + FLAG + "; setprop service.adb.tcp.port 5555; stop adbd; start adbd; echo \"$(date '+%Y-%m-%d %H:%M:%S') RESUME wireless adb on 5555\" >> " + LOG + "; tail -1 " + LOG);
  }
}
