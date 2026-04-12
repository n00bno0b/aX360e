// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import aenu.preference.CheckBoxPreference;
import aenu.preference.ListPreference;
import aenu.preference.SeekBarPreference;


import java.io.File;
import java.util.Set;

public class EmulatorSettings extends AppCompatActivity {

    static final String EXTRA_CONFIG_PATH="config_path";
    static final String EXTRA_CUSTOM_DRIVER_TYPE="extra_custom_driver_type";
    static final int REQUEST_CODE_CUSTOM_DRIVER_TYPE=6201;
    static final int REQUEST_CODE_CUSTOM_DRIVER_GPU=6202;

    static final String KEY_HID_DRIVER_TYPE="HID|hid";
    static final String KEY_CUSTOM_DRIVER_LOAD_TYPE="CustomDrivers|load_driver_type";
    static final String KEY_CUSTOM_DRIVER_GPU="CustomDrivers|gpu_driver";
    static final String KEY_CUSTOM_DRIVER_GPU_REMOVE="CustomDrivers|gpu_driver_remove";
    static final String KEY_MANAGE_PATCHES="Patches|manage_patches";

    // Advanced settings keys
    static final String KEY_TURNIP_DRIVER_INFO="TurnipAdvanced|driver_info";
    static final String KEY_PRESET_ULTRA_PERFORMANCE="Presets|ultra_performance";
    static final String KEY_PRESET_HIGH_QUALITY="Presets|high_quality";
    static final String KEY_PRESET_MAXIMUM_QUALITY="Presets|maximum_quality";
    static final String KEY_PRESET_BATTERY_OPTIMIZED="Presets|battery_optimized";

    static final int WARNING_COLOR=0xffff8000;

    public static class SettingsFragment extends PreferenceFragmentCompat implements
            Preference.OnPreferenceClickListener,Preference.OnPreferenceChangeListener{

        boolean is_global;
        String config_path;
        Emulator.Config original_config;
        Emulator.Config config;
        PreferenceScreen root_pref;

        public SettingsFragment(){
        }

        public static SettingsFragment newInstance(String config_path,boolean is_global){
            SettingsFragment fragment=new SettingsFragment();
            Bundle args=new Bundle();
            args.putString(EXTRA_CONFIG_PATH,config_path);
            args.putBoolean("is_global",is_global);
            fragment.setArguments(args);
            return fragment;
        }

        OnBackPressedCallback back_callback=new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                String current=SettingsFragment.this.getPreferenceScreen().getKey();
                if (current==null){
                    requireActivity().finish();
                    return;
                }
                int p=current.lastIndexOf('|');
                if (p==-1)
                    setPreferenceScreen(root_pref);
                else
                    setPreferenceScreen(root_pref.findPreference(current.substring(0,p)));
            }
        };

        final PreferenceDataStore data_store=new PreferenceDataStore(){

            public void putString(String key, @Nullable String value) {
                config.save_config_entry(key,value);
            }

            public void putStringSet(String key, @Nullable Set<String> values) {
                throw new UnsupportedOperationException("Not implemented on this data store");
            }

            public void putInt(String key, int value) {
                config.save_config_entry(key,Integer.toString(value));
            }

            public void putLong(String key, long value) {
                throw new UnsupportedOperationException("Not implemented on this data store");
            }

            public void putFloat(String key, float value) {
                throw new UnsupportedOperationException("Not implemented on this data store");
            }

            public void putBoolean(String key, boolean value) {
                config.save_config_entry(key,Boolean.toString(value));
            }

            @Nullable
            public String getString(String key, @Nullable String defValue) {
                return config.load_config_entry(key);
            }

            @Nullable
            public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
                //return defValues;
                throw new UnsupportedOperationException("Not implemented on this data store");
            }

            public int getInt(String key, int defValue) {
                String v=config.load_config_entry(key);
                return v!=null?Integer.parseInt(v):defValue;
            }

            public long getLong(String key, long defValue) {
                throw new UnsupportedOperationException("Not implemented on this data store");
            }

            public float getFloat(String key, float defValue) {
                throw new UnsupportedOperationException("Not implemented on this data store");
            }

            public boolean getBoolean(String key, boolean defValue) {
                String v=config.load_config_entry(key);
                return v!=null?Boolean.parseBoolean(v):defValue;
            }
        };

        Preference reset_as_default_pref(File _config_file){
            Preference p=new Preference(requireContext());
            p.setTitle(R.string.reset_as_default);
            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    new AlertDialog.Builder(requireContext())
                            .setMessage(getString(R.string.reset_as_default)+"?")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (config!=null) {
                                        config.close_config_file();
                                        config=null;
                                    }
                                    if(original_config!=null){
                                        original_config.close_config();
                                        original_config=null;
                                    }
                                    Utils.copy_file(Application.get_default_config_file(),_config_file);
                                    requireActivity().finish();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .create().show();
                    return true;
                }
            });
            return p;
        }

        Preference reset_as_global_pref(){
            Preference p=new Preference(requireContext());
            p.setTitle(R.string.use_global_config);
            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    new AlertDialog.Builder(requireContext())
                            .setMessage(getString(R.string.use_global_config)+"?")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (config!=null) {
                                        config.close_config_file();
                                        config=null;
                                    }
                                    if(original_config!=null){
                                        original_config.close_config();
                                        original_config=null;
                                    }

                                    new File(config_path).delete();
                                    requireActivity().finish();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .create().show();
                    return true;
                }
            });
            return p;
        }

        public void setPreferenceScreen(PreferenceScreen preferenceScreen){
            super.setPreferenceScreen(preferenceScreen);
            CharSequence title=preferenceScreen.getTitle();
            if(title==null)
                title=getString(R.string.settings);
            requireActivity().setTitle(title);
        }

        private void checkStoragePermission() {
            Log.d("EmulatorSettings", "checkStoragePermission called");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                boolean isManager = android.os.Environment.isExternalStorageManager();
                Log.d("EmulatorSettings", "isExternalStorageManager: " + isManager);
                if (!isManager) {
                    Log.d("EmulatorSettings", "Showing storage permission dialog");
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Storage Permission Required")
                            .setMessage("Settings require 'All Files Access' to manage configuration and drivers.\n\nPlease grant this permission.")
                            .setPositiveButton("Grant", (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    intent.addCategory("android.intent.category.DEFAULT");
                                    intent.setData(Uri.parse(String.format("package:%s", requireContext().getPackageName())));
                                    startActivity(intent);
                                } catch (Exception e) {
                                    Intent intent = new Intent();
                                    intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }
        }

        boolean ensure_default_config_exists(){
            try{
                File default_config_file=Application.get_default_config_file();
                if(default_config_file.exists())
                    return true;
                String default_config_str=Application.load_default_config_str(requireContext());
                if(default_config_str==null)
                    return false;
                Utils.save_string(default_config_file,default_config_str);
                return default_config_file.exists();
            }
            catch(Exception e){
                Log.e("EmulatorSettings","ensure_default_config_exists",e);
                return false;
            }
        }

        boolean restore_config_from_default(){
            try{
                if(!ensure_default_config_exists())
                    return false;
                File config_file=new File(config_path);
                File parent=config_file.getParentFile();
                if(parent!=null && !parent.exists())
                    parent.mkdirs();
                Utils.copy_file(Application.get_default_config_file(),config_file);
                return config_file.exists();
            }
            catch(Exception e){
                Log.e("EmulatorSettings","restore_config_from_default",e);
                return false;
            }
        }

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            checkStoragePermission();
            Bundle args=getArguments();
            if(args!=null){
                config_path=args.getString(EXTRA_CONFIG_PATH);
                is_global=args.getBoolean("is_global");
            }

            if(rootKey!=null) throw new RuntimeException();

            setPreferencesFromResource(R.xml.emulator_settings, rootKey);
            root_pref=getPreferenceScreen();

            if(is_global) {
                root_pref.addPreference(reset_as_default_pref(Application.get_global_config_file()));
            }
            else{
                root_pref.addPreference(reset_as_default_pref(new File(config_path)));
                root_pref.addPreference(reset_as_global_pref());
            }

            requireActivity().getOnBackPressedDispatcher().addCallback(back_callback);

            if(!new File(config_path).exists()){
                if(!restore_config_from_default()){
                    root_pref.setEnabled(false);
                    Toast.makeText(requireContext(), config_path, Toast.LENGTH_LONG).show();
                    return;
                }
            }

            try{
                config=Emulator.Config.open_config_file(config_path);
                original_config=Emulator.Config.open_config_from_string(Application.load_default_config_str(getContext()));
            }catch(Exception e){
                Log.e("EmulatorSettings","Failed to open config, trying to restore",e);
                if(!restore_config_from_default()){
                    root_pref.setEnabled(false);
                    return;
                }
                try{
                    config=Emulator.Config.open_config_file(config_path);
                    original_config=Emulator.Config.open_config_from_string(Application.load_default_config_str(getContext()));
                }catch(Exception e2){
                    Log.e("EmulatorSettings","Failed to open restored config",e2);
                    root_pref.setEnabled(false);
                    return;
                }
            }



            final String[] BOOL_KEYS={
                    "Vulkan|vulkan_sparse_shared_memory",
                    "Vulkan|vulkan_log_debug_messages",
                    "Vulkan|vulkan_validation",
                    "Vulkan|vulkan_allow_present_mode_immediate",
                    "Vulkan|vulkan_allow_present_mode_mailbox",
                    "Vulkan|vulkan_allow_present_mode_fifo_relaxed",
                    "Video|widescreen",
                    "Video|use_50Hz_mode",
                    "Video|interlaced",
                    "Video|enable_3d_mode",
                    "UI|show_profiler",
                    "UI|show_achievement_notification",
                    "UI|profiler_dpi_scaling",
                    "UI|storage_selection_dialog",
                    "UI|headless",
                    "UI|show_fps_counter",
                    "UI|show_frame_time",
                    "UI|show_cpu_usage",
                    "UI|show_gpu_usage",
                    "UI|show_memory_usage",
                    "Storage|mount_scratch",
                    "Storage|mount_cache",
                    "Kernel|staging_mode",
                    "Kernel|log_high_frequency_kernel_calls",
                    "Kernel|ignore_thread_affinities",
                    "Kernel|kernel_pix",
                    "Kernel|kernel_cert_monitor",
                    "Kernel|ignore_thread_priorities",
                    "Kernel|allow_incompatible_title_update",
                    "Kernel|apply_title_update",
                    "Kernel|kernel_debug_monitor",
                    "Kernel|Allow_nui_initialization",
                    "Memory|scribble_heap",
                    "Memory|protect_zero",
                    "Memory|writable_executable_memory",
                    "Memory|protect_on_release",
                    "Memory|ignore_offset_for_ranged_allocations",
                    "Display|present_letterbox",
                    "Display|postprocess_dither",
                    "Display|present_render_pass_clear",
                    "Display|host_present_from_non_ui_thread",
                    "Display|fullscreen",
                    "GPU|vsync",
                    "GPU|store_shaders",
                    "GPU|resolve_resolution_scale_fill_half_pixel_offset",
                    "GPU|readback_resolve",
                    "GPU|snorm16_render_target_full_range",
                    "GPU|native_2x_msaa",
                    "GPU|half_pixel_offset",
                    "GPU|log_ringbuffer_kickoff_initiator_bts",
                    "GPU|gpu_allow_invalid_fetch_constants",
                    "GPU|log_guest_driven_gpu_register_written_values",
                    "GPU|trace_gpu_stream",
                    "GPU|force_convert_quad_lists_to_triangle_lists",
                    "GPU|ignore_32bit_vertex_index_support",
                    "GPU|execute_unclipped_draw_vs_on_cpu_with_scissor",
                    "GPU|mrt_edram_used_range_clamp_to_min",
                    "GPU|gamma_render_target_as_srgb",
                    "GPU|execute_unclipped_draw_vs_on_cpu",
                    "GPU|readback_memexport",
                    "GPU|force_convert_triangle_fans_to_lists",
                    "GPU|disassemble_pm4",
                    "GPU|non_seamless_cube_map",
                    "GPU|depth_float24_round",
                    "GPU|clear_memory_page_state",
                    "GPU|depth_transfer_not_equal_test",
                    "GPU|native_stencil_value_output",
                    "GPU|force_convert_line_loops_to_strips",
                    "GPU|execute_unclipped_draw_vs_on_cpu_for_psi_render_backend",
                    "GPU|draw_resolution_scaled_texture_offsets",
                    "GPU|depth_float24_convert_in_pixel_shader",
                    "CPU|validate_hir",
                    "CPU|trace_function_references",
                    "CPU|trace_functions",
                    "CPU|trace_function_coverage",
                    "CPU|store_all_context_values",
                    "CPU|ignore_undefined_externs",
                    "CPU|debugprint_trap_log",
                    "CPU|break_condition_truncate",
                    "CPU|clock_source_raw",
                    "CPU|disassemble_functions",
                    "CPU|break_on_unimplemented_instructions",
                    "CPU|break_on_start",
                    "CPU|inline_mmio_access",
                    "CPU|disable_global_lock",
                    "CPU|break_on_debugbreak",
                    "CPU|clock_no_scaling",
                    "Logging|log_to_stdout",
                    "Logging|log_to_debugprint",
                    "Logging|log_string_format_kernel_calls",
                    "Logging|flush_log",
                    "General|allow_plugins",
                    "General|debug",
                    "General|discord",
                    "General|apply_patches",
                    "HID|vibration",
                    "APU|ffmpeg_verbose",
                    "APU|mute",
                    "APU|enable_xmp",
                    "APU|use_new_decoder",
                    "APU|use_dedicated_xma_thread",

                    // Advanced Turnip settings
                    "TurnipAdvanced|gmem_mode",
                    "TurnipAdvanced|ubwc_flag_hint",
                    "TurnipAdvanced|debug_logging",
                    "TurnipAdvanced|noubwc",

                    // Performance Monitoring
                    "PerfMonitoring|show_gmem_stats",
                    "PerfMonitoring|show_shader_stats",
                    "PerfMonitoring|show_edram_overhead",
                    "PerfMonitoring|show_thermal_status",
                    "PerfMonitoring|show_power_usage",

                    // Mobile GPU
                    "MobileGPU|dynamic_resolution",
                    "MobileGPU|thermal_aware",

                    // Power Management
                    "PowerManagement|background_unload",
                    "PowerManagement|low_memory_mode",
            };
            final String[] INT_KEYS={
                    "Memory|mmap_address_high",
                    "GPU|texture_cache_memory_limit_soft",
                    "GPU|texture_cache_memory_limit_hard",
                    "General|time_scalar",
                    "APU|xmp_default_volume",
                    "APU|apu_max_queued_frames",

                    // Mobile GPU resolution scales
                    "MobileGPU|resolution_scale_min",
                    "MobileGPU|resolution_scale_max",
            };
            final String[] STRING_ARR_KEYS={
                    "Video|video_standard",
                    "Video|internal_display_resolution",
                    "Video|avpack",
                    "Kernel|kernel_display_gamma_type",
                    "HID|hid",
                    "HID|left_stick_deadzone_percentage",
                    "HID|right_stick_deadzone_percentage",
                    "XConfig|user_language",
                    "XConfig|user_country",
                    "Display|postprocess_scaling_and_sharpening",
                    "Display|postprocess_antialiasing",
                    "GPU|render_target_path_vulkan",
                    "GPU|gpu",
                    "CPU|cpu",
                    "Logging|log_level",
                    "Content|license_mask",
                    "APU|apu",
                    "UI|profiler_position",

                    // Power Management
                    "PowerManagement|power_mode",
            };


            final String[] NODE_KEYS={
                    "Vulkan",
                    "UI",
                    "Storage",
                    "Kernel",
                    "HID",
                    "CustomDrivers",
                    "Memory",
                    "XConfig",
                    "Display",
                    "GPU",
                    "Logging",
                    "APU",
                    "Content",
                    "CPU",
                    "General",
                    "Video",
                    "Patches",
                    "TurnipAdvanced",
                    "PerfMonitoring",
                    "MobileGPU",
                    "PowerManagement",
                    "Presets"
            };


            for (String key:BOOL_KEYS){
                CheckBoxPreference pref=findPreference(key);
                if (pref == null) continue;
                String val_str=config.load_config_entry(key);
                if (val_str!=null) {
                    boolean val=Boolean.parseBoolean(val_str);
                    pref.setChecked(val);
                    setup_pref_title_color(pref,val_str);
                    //setup_config_dependency(pref,val_str);
                }
                pref.setOnPreferenceChangeListener(this);
                pref.setPreferenceDataStore(data_store);
            }

            for (String key:INT_KEYS){
                SeekBarPreference pref=findPreference(key);
                if (pref == null) continue;
                String val_str=config.load_config_entry(key);
                if (val_str!=null) {
                    //FIXME
                    try {
                        int val = Integer.parseInt(val_str);
                        pref.setValue(val);
                        setup_pref_title_color(pref,val_str);
                        //setup_config_dependency(pref,val_str);
                    } catch (NumberFormatException e) {
                        pref.setEnabled(false);
                    }
                }

                pref.setOnPreferenceChangeListener(this);
                pref.setPreferenceDataStore(data_store);
            }

            /* Preference.OnPreferenceChangeListener list_pref_change_listener=new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    ListPreference pref=(ListPreference) preference;
                    CharSequence value=(CharSequence) newValue;
                    CharSequence[] values=pref.getEntryValues();
                    CharSequence[] entries=pref.getEntries();
                    for (int i=0;i<values.length;i++){
                        if (values[i].equals(value)){
                            pref.setSummary(entries[i]);
                            break;
                        }
                    }
                    return true;
                }
            };*/
            for (String key:STRING_ARR_KEYS){
                ListPreference pref=findPreference(key);
                if (pref == null) continue;
                String val_str=config.load_config_entry(key);
                if (val_str!=null) {
                    pref.setValue(val_str);
                    pref.setSummary(pref.getEntry());
                    setup_pref_title_color(pref,val_str);
                    //setup_config_dependency(pref,val_str);
                }
                pref.setOnPreferenceChangeListener(this);
                pref.setPreferenceDataStore(data_store);
            }

            setup_custom_driver_type(null);
            setup_custom_driver_gpu(null);

            for (String key:NODE_KEYS){
                PreferenceScreen pref=findPreference(key);
                if (pref != null) pref.setOnPreferenceClickListener(this);
            }

            Preference custom_driver_load_pref=findPreference(KEY_CUSTOM_DRIVER_LOAD_TYPE);
            if(custom_driver_load_pref!=null)
                custom_driver_load_pref.setOnPreferenceClickListener(this);
            Preference custom_driver_gpu_pref=findPreference(KEY_CUSTOM_DRIVER_GPU);
            if(custom_driver_gpu_pref!=null)
                custom_driver_gpu_pref.setOnPreferenceClickListener(this);
            Preference custom_driver_gpu_remove_pref=findPreference(KEY_CUSTOM_DRIVER_GPU_REMOVE);
            if(custom_driver_gpu_remove_pref!=null)
                custom_driver_gpu_remove_pref.setOnPreferenceClickListener(this);
            Preference manage_patches_pref=findPreference(KEY_MANAGE_PATCHES);
            if(manage_patches_pref!=null)
                manage_patches_pref.setOnPreferenceClickListener(this);

            // Advanced settings click listeners
            Preference turnip_driver_info_pref=findPreference(KEY_TURNIP_DRIVER_INFO);
            if(turnip_driver_info_pref!=null)
                turnip_driver_info_pref.setOnPreferenceClickListener(this);

            Preference preset_ultra_perf_pref=findPreference(KEY_PRESET_ULTRA_PERFORMANCE);
            if(preset_ultra_perf_pref!=null)
                preset_ultra_perf_pref.setOnPreferenceClickListener(this);

            Preference preset_high_quality_pref=findPreference(KEY_PRESET_HIGH_QUALITY);
            if(preset_high_quality_pref!=null)
                preset_high_quality_pref.setOnPreferenceClickListener(this);

            Preference preset_max_quality_pref=findPreference(KEY_PRESET_MAXIMUM_QUALITY);
            if(preset_max_quality_pref!=null)
                preset_max_quality_pref.setOnPreferenceClickListener(this);

            Preference preset_battery_pref=findPreference(KEY_PRESET_BATTERY_OPTIMIZED);
            if(preset_battery_pref!=null)
                preset_battery_pref.setOnPreferenceClickListener(this);

        }


        @Override
        public void onDisplayPreferenceDialog( @NonNull Preference pref) {
            if (pref instanceof SeekBarPreference) {
                final DialogFragment f = SeekBarPreference.SeekBarPreferenceFragmentCompat.newInstance(pref.getKey());
                f.setTargetFragment(this, 0);
                f.show(getParentFragmentManager(), "DIALOG_FRAGMENT_TAG");
                return;
            }
            super.onDisplayPreferenceDialog(pref);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (config!=null)
                config.close_config_file();
            if (original_config!=null)
                original_config.close_config();
        }

        /*@Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            Log.i("onPreferenceChange",preference.getKey()+" "+newValue);
            if (preference instanceof CheckBoxPreference){
                config.save_config_entry(preference.getKey(),newValue.toString());
            }else if (preference instanceof ListPreference){
                config.save_config_entry(preference.getKey(),newValue.toString());
            }else if (preference instanceof SeekBarPreference){
                config.save_config_entry(preference.getKey(),newValue.toString());
            }
            return true;
        }*/


        @Override
        public boolean onPreferenceClick(@NonNull Preference preference) {

            if(KEY_CUSTOM_DRIVER_GPU.equals(preference.getKey())){
                open_custom_driver_gpu_picker();
                return true;
            }

            if(KEY_CUSTOM_DRIVER_GPU_REMOVE.equals(preference.getKey())){
                remove_custom_driver_gpu();
                return true;
            }

            if(KEY_CUSTOM_DRIVER_LOAD_TYPE.equals(preference.getKey())){
                open_custom_driver_type_editor();
                return true;
            }

            if(KEY_MANAGE_PATCHES.equals(preference.getKey())){
                Intent intent = new Intent(requireContext(), PatchManagerActivity.class);
                startActivity(intent);
                return true;
            }

            if(KEY_TURNIP_DRIVER_INFO.equals(preference.getKey())){
                show_turnip_driver_info();
                return true;
            }

            if(KEY_PRESET_ULTRA_PERFORMANCE.equals(preference.getKey())){
                apply_performance_preset(KEY_PRESET_ULTRA_PERFORMANCE);
                return true;
            }

            if(KEY_PRESET_HIGH_QUALITY.equals(preference.getKey())){
                apply_performance_preset(KEY_PRESET_HIGH_QUALITY);
                return true;
            }

            if(KEY_PRESET_MAXIMUM_QUALITY.equals(preference.getKey())){
                apply_performance_preset(KEY_PRESET_MAXIMUM_QUALITY);
                return true;
            }

            if(KEY_PRESET_BATTERY_OPTIMIZED.equals(preference.getKey())){
                apply_performance_preset(KEY_PRESET_BATTERY_OPTIMIZED);
                return true;
            }

            if(preference instanceof PreferenceScreen){
                setPreferenceScreen(root_pref.findPreference(preference.getKey()));
                return false;
            }
            return false;
        }

        void create_list_dialog(String title, String[] items, DialogInterface.OnClickListener listener){
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(title)
                    .setItems(items, listener)
                    .setNegativeButton(android.R.string.cancel, null);
            builder.create().show();
        }

        void setup_custom_driver_type(String new_type){
            Preference load_type_pref=findPreference(KEY_CUSTOM_DRIVER_LOAD_TYPE);
            ListPreference hidden_hid_type_pref=findPreference(KEY_HID_DRIVER_TYPE);

            if(new_type==null){
                String current=config.load_config_entry(KEY_HID_DRIVER_TYPE);
                if(current!=null && !current.trim().isEmpty()){
                    if(load_type_pref!=null)
                        load_type_pref.setSummary(current);
                }
                else if(load_type_pref!=null){
                    load_type_pref.setSummary(getString(R.string.es_hint_custom_drivers_load_type));
                }
                return;
            }

            String value=new_type.trim();
            if(value.isEmpty()){
                Toast.makeText(requireContext(),R.string.custom_driver_type_invalid,Toast.LENGTH_SHORT).show();
                return;
            }

            config.save_config_entry(KEY_HID_DRIVER_TYPE,value);

            if(load_type_pref!=null)
                load_type_pref.setSummary(value);

            if(hidden_hid_type_pref!=null){
                hidden_hid_type_pref.setValue(value);

                CharSequence[] values=hidden_hid_type_pref.getEntryValues();
                CharSequence[] entries=hidden_hid_type_pref.getEntries();
                boolean matched=false;
                for(int i=0;i<values.length;i++){
                    if(values[i].toString().equals(value)){
                        hidden_hid_type_pref.setSummary(entries[i]);
                        matched=true;
                        break;
                    }
                }
                if(!matched)
                    hidden_hid_type_pref.setSummary(value);

                setup_pref_title_color(hidden_hid_type_pref,value);
            }
        }

        void setup_custom_driver_gpu(android.net.Uri uri){
            Preference gpu_pref=findPreference(KEY_CUSTOM_DRIVER_GPU);
            if(uri == null){
                // Determine if driver is installed and show its name
                TurnipDriverInfo driverInfo = TurnipDriverInfo.detect(requireContext());
                if (driverInfo.isInstalled()) {
                    if (gpu_pref != null) {
                        gpu_pref.setSummary("Installed: " + driverInfo.getDriverName() + " (" + driverInfo.getDriverVersion() + ")");
                    }
                } else {
                    if (gpu_pref != null) gpu_pref.setSummary(getString(R.string.es_hint_custom_drivers_gpu));
                }
                return;
            }

            boolean success = CustomDriverUtils.installDriver(requireContext(), uri);
            if (success) {
                TurnipDriverInfo driverInfo = TurnipDriverInfo.detect(requireContext());
                if (gpu_pref != null) {
                    gpu_pref.setSummary("Installed: " + driverInfo.getDriverName() + " (" + driverInfo.getDriverVersion() + ")");
                }
                Toast.makeText(requireContext(), getString(R.string.custom_driver_installed_success), Toast.LENGTH_SHORT).show();
            } else {
                String failureReason = CustomDriverUtils.getLastDriverError();
                if (gpu_pref != null) {
                    gpu_pref.setSummary(failureReason != null && !failureReason.isEmpty()
                            ? "Driver import failed: " + failureReason
                            : getString(R.string.es_hint_custom_drivers_gpu));
                }
                String toastMessage = getString(R.string.custom_driver_installed_failed);
                if (failureReason != null && !failureReason.isEmpty()) {
                    toastMessage += ": " + failureReason;
                }
                Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG).show();
            }
        }

        void open_custom_driver_gpu_picker(){
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
            ((AppCompatActivity)requireActivity()).startActivityForResult(intent, REQUEST_CODE_CUSTOM_DRIVER_GPU);
        }

        void remove_custom_driver_gpu() {
            CustomDriverUtils.removeDriver(requireContext());
            Preference gpu_pref = findPreference(KEY_CUSTOM_DRIVER_GPU);
            if (gpu_pref != null) {
                gpu_pref.setSummary(getString(R.string.es_hint_custom_drivers_gpu));
            }
            Toast.makeText(requireContext(), getString(R.string.custom_driver_removed), Toast.LENGTH_SHORT).show();
        }

        void open_custom_driver_type_editor(){
            String current=config.load_config_entry(KEY_HID_DRIVER_TYPE);
            Intent intent=new Intent(requireContext(),CustomDriverTypeActivity.class);
            intent.putExtra(EXTRA_CUSTOM_DRIVER_TYPE,current);
            ((AppCompatActivity)requireActivity()).startActivityForResult(intent,REQUEST_CODE_CUSTOM_DRIVER_TYPE);
        }

        void show_turnip_driver_info() {
            TurnipDriverInfo driverInfo = TurnipDriverInfo.detect(requireContext());

            String message;
            if (driverInfo.isInstalled()) {
                message = "Turnip Driver Detected\n\n" +
                         "Driver: " + driverInfo.getDriverName() + "\n" +
                         "Version: " + driverInfo.getDriverVersion() + "\n" +
                         "Mesa Version: " + driverInfo.getMesaVersion() + "\n\n" +
                         driverInfo.getFormattedInfo();
            } else {
                message = "No custom Turnip driver detected.\n\n" +
                         "To install a custom Turnip driver:\n" +
                         "1. Go to Custom Drivers settings\n" +
                         "2. Select 'Install GPU Driver'\n" +
                         "3. Choose a Turnip driver ZIP file";
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Turnip Driver Information")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create().show();
        }

        void apply_performance_preset(String presetKey) {
            String presetName = "";
            switch (presetKey) {
                case KEY_PRESET_ULTRA_PERFORMANCE:
                    presetName = getString(R.string.preset_ultra_performance);
                    break;
                case KEY_PRESET_HIGH_QUALITY:
                    presetName = getString(R.string.preset_high_quality);
                    break;
                case KEY_PRESET_MAXIMUM_QUALITY:
                    presetName = getString(R.string.preset_maximum_quality);
                    break;
                case KEY_PRESET_BATTERY_OPTIMIZED:
                    presetName = getString(R.string.preset_battery_optimized);
                    break;
            }

            final String finalPresetName = presetName;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Apply Preset")
                    .setMessage("Apply \"" + finalPresetName + "\" preset?\n\nThis will modify multiple settings to optimize for this configuration.")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            TurnipEnvManager envManager = new TurnipEnvManager(requireContext());
                            envManager.applyPreset(presetKey, config);

                            Toast.makeText(requireContext(),
                                    "Applied " + finalPresetName + " preset",
                                    Toast.LENGTH_SHORT).show();

                            // Restart the settings activity to refresh all preference values
                            requireActivity().recreate();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create().show();
        }

        void setup_pref_title_color(Preference preference,String cur_val){
            String default_val = original_config.load_config_entry(preference.getKey());
            boolean modify = default_val == null || !default_val.equals(cur_val);

            if(preference instanceof CheckBoxPreference){
                ((CheckBoxPreference) preference).set_is_modify_color(modify);
            }
            else if(preference instanceof SeekBarPreference){
                ((SeekBarPreference) preference).set_is_modify_color(modify);
            }
            else if(preference instanceof ListPreference){
                ((ListPreference) preference).set_is_modify_color(modify);
            }
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
            if(preference instanceof CheckBoxPreference){
                CheckBoxPreference pref=(CheckBoxPreference) preference;
                String value=Boolean.toString((boolean)newValue);
                setup_pref_title_color(pref,value);
                //setup_config_dependency(pref,value);
                return true;
            }
            else if(preference instanceof SeekBarPreference){
                SeekBarPreference pref=(SeekBarPreference) preference;
                String value=Integer.toString((int)newValue);
                setup_pref_title_color(pref,value);
                //setup_config_dependency(pref,value);
                return true;
            }
            else if(preference instanceof ListPreference){
                ListPreference pref=(ListPreference) preference;
                CharSequence value=(CharSequence) newValue;
                CharSequence[] values=pref.getEntryValues();
                CharSequence[] entries=pref.getEntries();
                for (int i=0;i<values.length;i++){
                    if (values[i].equals(value)){
                        pref.setSummary(entries[i]);
                        break;
                    }
                }
                setup_pref_title_color(pref,value.toString());
                //setup_config_dependency(pref,value.toString());
                return true;
            }

            return false;
        }
    }

    SettingsFragment fragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String config_path=getIntent().getStringExtra(EXTRA_CONFIG_PATH);


        if(config_path!=null) {
            fragment=SettingsFragment.newInstance(config_path,false);
        }
        else{
            fragment=SettingsFragment.newInstance(Application.get_global_config_file().getAbsolutePath(),true);
        }

        getSupportFragmentManager().beginTransaction().replace(android.R.id.content,fragment).commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode!=RESULT_OK || data==null)
            return;

        if(requestCode==REQUEST_CODE_CUSTOM_DRIVER_TYPE){
            String type=data.getStringExtra(EXTRA_CUSTOM_DRIVER_TYPE);
            if(fragment!=null)
                fragment.setup_custom_driver_type(type);
        } else if (requestCode==REQUEST_CODE_CUSTOM_DRIVER_GPU) {
            Uri uri = data.getData();
            if (uri != null) {
                int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (SecurityException e) {
                    Log.w("EmulatorSettings", "Failed to persist custom driver URI permission: " + uri, e);
                }
            }
            if (fragment!=null && uri != null)
                fragment.setup_custom_driver_gpu(uri);
        }
    }
}
