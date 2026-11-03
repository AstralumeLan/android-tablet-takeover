import android.graphics.*; import java.io.*;
public class Shot { public static void main(String[] a) throws Exception {
  Bitmap b = BitmapFactory.decodeFile(a[0]);
  float s = Float.parseFloat(a[2]); int q = Integer.parseInt(a[3]);
  Bitmap o = Bitmap.createScaledBitmap(b, (int)(b.getWidth()*s), (int)(b.getHeight()*s), true);
  FileOutputStream f = new FileOutputStream(a[1]); o.compress(Bitmap.CompressFormat.JPEG, q, f); f.close();
  System.out.println("ok " + o.getWidth() + "x" + o.getHeight());
}}
