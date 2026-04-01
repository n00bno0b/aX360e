// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Utils {
    public static void enable_fullscreen(Window w){
        WindowCompat.setDecorFitsSystemWindows(w,false);
        WindowInsetsControllerCompat wic=WindowCompat.getInsetsController(w,w.getDecorView());
        wic.hide(WindowInsetsCompat.Type.systemBars());
        wic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        WindowManager.LayoutParams lp=w.getAttributes();
        lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);
    }
    static String getFileNameFromUri(Uri uri) {
        String fileName = null;
        try (Cursor cursor = Application.ctx.getContentResolver().query(
                uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                fileName = cursor.getString(cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ));
            }
        } catch (Exception e) {
            android.util.Log.e("Utils", "Failed to get file name from URI", e);
        }
        return fileName;
    }

    static void save_string(File file, String str){
        try(FileOutputStream fos=new FileOutputStream(file)){
            fos.write(str.getBytes());
        }catch(Exception e){
            android.util.Log.e("Utils", "Failed to save string to " + file.getPath(), e);
        }
    }

    static String load_string(File file){
        try(FileInputStream fis=new FileInputStream(file)){
            byte[] buf=new byte[fis.available()];
            fis.read(buf);
            return new String(buf);
        }catch(Exception e){
            android.util.Log.e("Utils", "Failed to load string from " + file.getPath(), e);
            return null;
        }
    }

    static void copy_file(File src_file,File dst_file){
        try(FileInputStream in=new FileInputStream(src_file);
            FileOutputStream out=new FileOutputStream(dst_file)){
            byte[] buf=new byte[16384];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
        }catch(Exception e){
            android.util.Log.e("Utils", "Failed to copy " + src_file.getPath() + " to " + dst_file.getPath(), e);
        }
    }

    static Bitmap gen_pressed_bitmap(Bitmap bmp){
        int width=bmp.getWidth();
        int height=bmp.getHeight();
        Bitmap gray_bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        for(int i=0;i<height;i++){
            for(int j=0;j<width;j++){
                int color=bmp.getPixel(j,i);
                int a=color&0xff000000;
                int r=(color>>16)&0xff;
                int g=(color>>8)&0xff;
                int b=color&0xff;
                int gray=(r+g+b)/3;
                int gray_color=a|(gray<<16)|(gray<<8)|0;
                gray_bmp.setPixel(j,i,gray_color);
            }
        }
        return gray_bmp;
    }
    public static void extractAssetsDir(Context context, String assertDir, File outputDir) {
        AssetManager assetManager = context.getAssets();
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            String[] filesToExtract = assetManager.list(assertDir);
            if (filesToExtract!= null) {
                for (String file : filesToExtract) {
                    File outputFile = new File(outputDir, file);
                    if(outputFile.exists())continue;

                    try(InputStream in = assetManager.open(assertDir + "/" + file);
                        FileOutputStream out = new FileOutputStream(outputFile)){
                        byte[] buffer = new byte[16384];
                        int read;
                        while ((read = in.read(buffer))!= -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
        } catch (IOException e) {
            android.util.Log.e("Utils", "Failed to extract assets dir: " + assertDir, e);
        }
    }
}

