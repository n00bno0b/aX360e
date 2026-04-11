package aenu.ax360e;

import java.util.HashMap;
import java.util.Map;

/**
 * Engine-specific profile with recommended settings.
 * Acts as a configuration layer between global defaults and per-game overrides.
 */
public class EngineProfile {
    private final GameEngine engine;
    private final Map<String, String> configOverrides;
    private final String description;

    public EngineProfile(GameEngine engine) {
        this.engine = engine;
        this.configOverrides = new HashMap<>();
        this.description = engine.getDescription();
        applyEngineDefaults();
    }

    /**
     * Apply engine-specific default configurations based on known compatibility issues.
     */
    private void applyEngineDefaults() {
        switch (engine) {
            case UNREAL:
                // Unreal Engine 3/3.5 - occlusion query issues
                configOverrides.put("GPU|query_occlusion_fake_sample_count", "1000");
                configOverrides.put("GPU|query_occlusion_enable", "true");
                break;

            case FROSTBITE:
                // Frostbite - GPU readback, memexport, kernel gaps
                configOverrides.put("GPU|resolve_resolution_scale_duplicate_second_pixel", "true");
                configOverrides.put("GPU|draw_resolution_scale_y", "1");
                break;

            case MT_FRAMEWORK:
                // MT Framework - GPU readback/memexport
                configOverrides.put("GPU|resolve_resolution_scale_duplicate_second_pixel", "true");
                break;

            case GAMEBRYO:
                // Gamebryo - kernel gaps, timing/VSync sensitivity
                configOverrides.put("GPU|vsync", "true");
                configOverrides.put("Video|use_50Hz_mode", "false");
                break;

            case SOURCE:
                // Source Engine - multiple issues: occlusion queries, readback, CPU instructions
                configOverrides.put("GPU|query_occlusion_fake_sample_count", "1000");
                configOverrides.put("GPU|resolve_resolution_scale_duplicate_second_pixel", "true");
                break;

            case UNITY:
                // Unity - CPU instruction coverage
                // Most CPU fixes are in emulator core, no specific config overrides needed
                break;

            case ID_TECH:
                // id Tech 5 - timing/VSync sensitivity
                configOverrides.put("GPU|vsync", "true");
                configOverrides.put("Video|use_50Hz_mode", "false");
                break;

            case LITHTECH:
                // LithTech Jupiter - kernel gaps
                // Most fixes are kernel-side, minimal config changes needed
                break;

            case EGO:
                // EGO Engine - GPU readback/memexport
                configOverrides.put("GPU|resolve_resolution_scale_duplicate_second_pixel", "true");
                break;

            case CRYENGINE:
                // CryEngine 3 - generally well-behaved on Xbox 360
                break;

            case PROPRIETARY:
            case UNKNOWN:
            default:
                // No specific overrides for unknown/proprietary engines
                break;
        }
    }

    /**
     * Get configuration overrides for this engine.
     * These act as defaults that sit between global config and per-game overrides.
     */
    public Map<String, String> getConfigOverrides() {
        return new HashMap<>(configOverrides);
    }

    public GameEngine getEngine() {
        return engine;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Apply this profile's settings to a config object.
     * Does not overwrite existing user settings, only fills in defaults.
     */
    public void applyToConfig(Map<String, String> config, boolean forceOverride) {
        for (Map.Entry<String, String> entry : configOverrides.entrySet()) {
            if (forceOverride || !config.containsKey(entry.getKey())) {
                config.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
