package aenu.ax360e;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import aenu.hardware.ProcessorInfo;

// Created by aenu on 2025/7/31.
// SPDX-License-Identifier: WTFPL
public class Application extends android.app.Application{
    static File get_app_data_dir(){
        return ctx.getExternalFilesDir("ax360e");
    }
    public static File get_default_config_file(){
        return new File(Application.get_app_data_dir(),"default_config.toml");
    }

    public static File get_default_profile_file(){
        final String XUID="E0300000A360E000";
        final String sub_path=String.format("%s/%s/%s/%s/%s/%s","content",XUID,"FFFE07D1","00010000",XUID,"Account");
        return new File(Application.get_app_data_dir(),sub_path);
    }
    public static File get_global_config_file(){
        return new File(Application.get_app_data_dir(),"xenia.config.toml");
    }

    public  static byte[] load_assets_file(Context ctx,String asset_file_path) {
        try {
            InputStream in = ctx.getAssets().open(asset_file_path);
            int size = in.available();
            byte[] buffer = new byte[size];
            in.read(buffer);
            in.close();
            return buffer;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    static String load_default_config_str(Context ctx){
        byte[] data = Application.load_assets_file(ctx,"config/default_config.toml");
        if (data == null) {
            throw new RuntimeException("Failed to load default config from assets");
        }
        return new String(data);
    }

    public static File get_uri_info_list_file(){
        return new File(Application.get_app_data_dir(),"uri_info_list.json");
    }

    public static File get_virtual_control_config_file(){
        return new File(Application.get_app_data_dir(),"virtual_control_config.json");
    }
    static boolean device_support_vulkan() {
        return gpu_device_name_vk!=null;
    }

    static boolean should_delay_load() {
        if(gpu_device_name_vk==null)
            throw new RuntimeException("gpu_device_name_vk==null");
        return gpu_device_name_vk.contains("Adreno (TM) 5")
                || gpu_device_name_vk.contains("Adreno (TM) 6");
    }

    public  static Context ctx;
    public static String gpu_device_name_vk;
    @Override
    public void onCreate()
    {
        super.onCreate();

        Application.ctx=this;
        try {
            gpu_device_name_vk = ProcessorInfo.gpu_get_physical_device_name_vk();
        } catch (Exception e) {
            e.printStackTrace();
            gpu_device_name_vk = null;
        }

        // Check if Vulkan is supported
        if (!device_support_vulkan()) {
            // App will show error dialog in MainActivity
            return;
        }

        String[] entry={"cache","cache0","cache1",};
        for(String e:entry){
            File f=new File(get_app_data_dir(),e);
            f.mkdirs();
        }
        File default_config_file=get_default_config_file();
        if(!default_config_file.exists())
            Utils.save_string(default_config_file,load_default_config_str(this));

        File global_config_file=get_global_config_file();
        if(!global_config_file.exists())
            Utils.copy_file(default_config_file,global_config_file);

        if(!get_default_profile_file().exists()){
            File default_profile_dir=get_default_profile_file().getParentFile();
            if (default_profile_dir != null) {
                default_profile_dir.mkdirs();
                Utils.extractAssetsDir(this,"content/E0300000A360E000/FFFE07D1/00010000/E0300000A360E000",default_profile_dir);
            }
        }

        // Extract game patches from assets if needed
        extractGamePatches();

        if(!should_delay_load())
            Emulator.load_library();

    }

    private void extractGamePatches() {
        File patchesDir = new File(get_app_data_dir(), "patches");
        if (!patchesDir.exists()) {
            patchesDir.mkdirs();
            Utils.extractAssetsDir(this, "patches", patchesDir);
        }
    }

}
