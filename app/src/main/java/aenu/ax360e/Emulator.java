// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;

public class Emulator extends aenu.emulator.Emulator{
    public static Emulator get=null;
    public static void load_library(){
        if(get!=null)
            throw new RuntimeException("Emulator already loaded");
        get=new Emulator();
        System.loadLibrary("e");
    }

    /*public void key_event(int keycode,boolean pressed){
        throw new RuntimeException("Not implemented");
        final int unused=-1;
        super.key_event(keycode,pressed,unused);
    }*/

    public native void setup_context(Context ctx);
    public native void setup_document_file_tree(DocumentFile tree);
    public native void setup_launch_args(String[] args);
    public  native void setup_uri_info_list_file(String path);
    public native String simple_device_info();
    public native String generate_config_xml(String config_path);
    public static int nc_open_uri_fd(Context ctx,Uri uri) {
        try {
            ParcelFileDescriptor pfd_ = ctx.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd_ == null) {
                Log.e("ax360e", "openFileDescriptor returned null for: " + uri);
                return -1;
            }
            int game_fd=pfd_.detachFd();
            return game_fd;
        } catch (Exception e) {
            Log.e("ax360e",e.toString());
            return -1;
        }
    }


    public native GameInfo meta_info_from_god_game(Context ctx,String uri) throws RuntimeException;


    public static class GameInfo{

        public String uri;
        public String name;
        public int fd;
        public byte[] icon;


        static JSONObject to_json(GameInfo  info) throws JSONException {
            JSONObject json=new JSONObject();

            json.put("uri",info.uri);
            if(info.name!=null)
                json.put("name",info.name);

            if(info.icon!=null)
                json.put("icon", Base64.getEncoder().encodeToString(info.icon));
            return json;
        }

        static GameInfo from_json(JSONObject json) throws JSONException {
            GameInfo info=new GameInfo();
            info.uri=json.getString("uri");
            if(json.has("name"))
                info.name=json.getString("name");
            if(json.has("icon"))
                info.icon=Base64.getDecoder().decode(json.getString("icon"));

            return info;
        }
    }
}
