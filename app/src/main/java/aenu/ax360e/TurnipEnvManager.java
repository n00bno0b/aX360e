package aenu.ax360e;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages Turnip driver environment variables based on user preferences.
 * These environment variables control Mesa Turnip driver behavior for Adreno GPUs.
 */
public class TurnipEnvManager {
    private static final String TAG = "TurnipEnvManager";

    private final Context context;

    public TurnipEnvManager(Context context) {
        this.context = context;
    }

    /**
     * Build environment variable configuration based on preferences.
     * Returns a map-like structure that can be applied to the native emulator process.
     */
    public List<EnvVar> buildEnvironmentVariables() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        List<EnvVar> envVars = new ArrayList<>();

        // TU_DEBUG flags
        List<String> tuDebugFlags = new ArrayList<>();

        if (prefs.getBoolean("TurnipAdvanced|gmem_mode", false)) {
            tuDebugFlags.add("gmem");
        }

        if (prefs.getBoolean("TurnipAdvanced|debug_logging", false)) {
            tuDebugFlags.add("startup");
            tuDebugFlags.add("nir");
        }

        if (prefs.getBoolean("TurnipAdvanced|noubwc", false)) {
            tuDebugFlags.add("noubwc");
        }

        if (!tuDebugFlags.isEmpty()) {
            envVars.add(new EnvVar("TU_DEBUG", String.join(",", tuDebugFlags)));
        }

        // FD_DEV_FEATURES flags
        if (prefs.getBoolean("TurnipAdvanced|ubwc_flag_hint", false)) {
            envVars.add(new EnvVar("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1"));
        }

        // MESA_DEBUG - general Mesa debugging
        if (prefs.getBoolean("TurnipAdvanced|debug_logging", false)) {
            envVars.add(new EnvVar("MESA_DEBUG", "1"));
        }

        return envVars;
    }

    /**
     * Apply performance preset configurations
     */
    public void applyPreset(String presetKey, Emulator.Config config) {
        switch (presetKey) {
            case "Presets|ultra_performance":
                applyUltraPerformancePreset(config);
                break;
            case "Presets|high_quality":
                applyHighQualityPreset(config);
                break;
            case "Presets|maximum_quality":
                applyMaximumQualityPreset(config);
                break;
            case "Presets|battery_optimized":
                applyBatteryOptimizedPreset(config);
                break;
        }
    }

    private void applyUltraPerformancePreset(Emulator.Config config) {
        // Minimize resolution for maximum FPS
        config.save_config_entry("Video|internal_display_resolution", "1x");
        config.save_config_entry("GPU|vsync", "false");
        config.save_config_entry("Display|postprocess_antialiasing", "disabled");
        config.save_config_entry("Display|postprocess_scaling_and_sharpening", "bilinear");

        // Enable dynamic resolution with low min
        config.save_config_entry("MobileGPU|dynamic_resolution", "true");
        config.save_config_entry("MobileGPU|resolution_scale_min", "50");
        config.save_config_entry("MobileGPU|resolution_scale_max", "100");

        // Power mode
        config.save_config_entry("PowerManagement|power_mode", "performance");
    }

    private void applyHighQualityPreset(Emulator.Config config) {
        // Balanced resolution and quality
        config.save_config_entry("Video|internal_display_resolution", "2x");
        config.save_config_entry("GPU|vsync", "true");
        config.save_config_entry("Display|postprocess_antialiasing", "fxaa");
        config.save_config_entry("Display|postprocess_scaling_and_sharpening", "cas");

        // Conservative dynamic resolution
        config.save_config_entry("MobileGPU|dynamic_resolution", "true");
        config.save_config_entry("MobileGPU|resolution_scale_min", "75");
        config.save_config_entry("MobileGPU|resolution_scale_max", "150");

        // Balanced power mode
        config.save_config_entry("PowerManagement|power_mode", "balanced");
    }

    private void applyMaximumQualityPreset(Emulator.Config config) {
        // Maximum resolution and quality
        config.save_config_entry("Video|internal_display_resolution", "3x");
        config.save_config_entry("GPU|vsync", "true");
        config.save_config_entry("Display|postprocess_antialiasing", "fxaa");
        config.save_config_entry("Display|postprocess_scaling_and_sharpening", "cas_sharpening_max");

        // Disable dynamic resolution
        config.save_config_entry("MobileGPU|dynamic_resolution", "false");
        config.save_config_entry("MobileGPU|resolution_scale_max", "200");

        // Performance power mode
        config.save_config_entry("PowerManagement|power_mode", "performance");
    }

    private void applyBatteryOptimizedPreset(Emulator.Config config) {
        // Minimum quality for battery saving
        config.save_config_entry("Video|internal_display_resolution", "1x");
        config.save_config_entry("GPU|vsync", "false");
        config.save_config_entry("Display|postprocess_antialiasing", "disabled");
        config.save_config_entry("Display|postprocess_scaling_and_sharpening", "bilinear");

        // Enable dynamic resolution with very low min
        config.save_config_entry("MobileGPU|dynamic_resolution", "true");
        config.save_config_entry("MobileGPU|resolution_scale_min", "50");
        config.save_config_entry("MobileGPU|resolution_scale_max", "75");

        // Enable all power saving features
        config.save_config_entry("PowerManagement|power_mode", "battery_saver");
        config.save_config_entry("PowerManagement|background_unload", "true");
        config.save_config_entry("PowerManagement|low_memory_mode", "true");
        config.save_config_entry("MobileGPU|thermal_aware", "true");
    }

    /**
     * Environment variable pair
     */
    public static class EnvVar {
        public final String name;
        public final String value;

        public EnvVar(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }
}
