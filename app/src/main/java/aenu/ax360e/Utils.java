// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
            long fileSize = file.length();
            if (fileSize > 10 * 1024 * 1024) { // 10 MB safety limit for config/text files
                android.util.Log.e("Utils", "File too large to load as string: " + file.getPath() + " (" + fileSize + " bytes)");
                return null;
            }
            if (fileSize > Integer.MAX_VALUE) {
                android.util.Log.e("Utils", "File size exceeds maximum array size: " + file.getPath());
                return null;
            }

            // Read file in a loop to ensure complete read
            byte[] buf = new byte[(int) fileSize];
            int totalRead = 0;
            int bytesRead;
            while (totalRead < fileSize && (bytesRead = fis.read(buf, totalRead, (int) fileSize - totalRead)) != -1) {
                totalRead += bytesRead;
            }

            if (totalRead < fileSize) {
                android.util.Log.e("Utils", "Incomplete read: expected " + fileSize + " bytes, got " + totalRead + " bytes from " + file.getPath());
                return null;
            }

            return new String(buf, java.nio.charset.StandardCharsets.UTF_8);
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

    @FunctionalInterface
    public static interface InstallCallback {
        void onInstallComplete(String path);
    }

    static boolean install_custom_driver_from_zip(Activity ctx, Uri uri, InstallCallback cb) {
        try {
            ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) {
                Toast.makeText(ctx, "ERR: could not open file", Toast.LENGTH_SHORT).show();
                return false;
            }
            FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
            ZipInputStream zis = new ZipInputStream(fis);

            Map<String, byte[]> entries = new HashMap<>();
            for (ZipEntry ze = zis.getNextEntry(); ze != null; ze = zis.getNextEntry()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int n;
                while ((n = zis.read(buffer)) != -1)
                    baos.write(buffer, 0, n);
                entries.put(ze.getName(), baos.toByteArray());
                zis.closeEntry();
            }

            zis.close();
            fis.close();
            pfd.close();

            String libs_dir_name = Utils.getFileNameFromUri(uri);
            if (libs_dir_name == null || !libs_dir_name.contains(".")) {
                Toast.makeText(ctx, "ERR: invalid file name", Toast.LENGTH_SHORT).show();
                return false;
            }
            libs_dir_name = libs_dir_name.substring(0, libs_dir_name.lastIndexOf('.'));
            File libs_dir = new File(Application.get_custom_driver_dir(), libs_dir_name);

            String driver_lib_path = null;
            if (entries.containsKey("meta.json")) {
                JSONObject meta = new JSONObject(new String(entries.get("meta.json")));
                if (!meta.has("libraryName")) {
                    Toast.makeText(ctx, "ERR: invalid meta.json", Toast.LENGTH_SHORT).show();
                    return false;
                }
                String driver_lib_name = meta.getString("libraryName");
                if(!entries.containsKey( driver_lib_name)){
                    Toast.makeText(ctx, "ERR: not found "+driver_lib_name, Toast.LENGTH_SHORT).show();
                    return false;
                }

                driver_lib_path = new File(libs_dir, driver_lib_name).getAbsolutePath();
            }

            if(driver_lib_path==null){
                int lib_count=0;
                String lib_name = null;
                for(String entry_name : entries.keySet()){
                    if (entry_name.endsWith(".so")) {
                        lib_name = entry_name;
                        lib_count++;
                    }
                }
                if(lib_count!=1){
                    Toast.makeText(ctx, "ERR: invalid file", Toast.LENGTH_SHORT).show();
                    return false;
                }
                driver_lib_path = new File(libs_dir, lib_name).getAbsolutePath();
            }

            if (!libs_dir.exists()) libs_dir.mkdirs();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (entry.getKey().endsWith(".so")||entry.getKey().equals("meta.json")) {
                    String lib_path = new File(libs_dir, entry.getKey()).getAbsolutePath();
                    FileOutputStream lib_os = new FileOutputStream(lib_path);
                    lib_os.write(entry.getValue());
                    lib_os.close();
                }
            }
            cb.onInstallComplete(driver_lib_path);
            return true;
        } catch (Exception e) {
            Toast.makeText(ctx, e.toString(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}

