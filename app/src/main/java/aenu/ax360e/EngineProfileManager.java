package aenu.ax360e;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class EngineProfileManager {
    private static final String TAG = "EngineProfileManager";

    // Title ID -> Engine Name static mapping
    private final Map<String, String> titleEngineMap = new HashMap<>();

    public EngineProfileManager() {
        // Load static mapping (e.g. from assets or embedded)
        // Known Title-ID mappings from compatibility doc:
        titleEngineMap.put("4D530A24", "Unreal"); // Murdered: Soul Suspect
        titleEngineMap.put("454108A8", "Frostbite"); // Battlefield Bad Company 2
        titleEngineMap.put("43430814", "MT Framework"); // Dragon's Dogma
        titleEngineMap.put("434307E4", "MT Framework"); // Resident Evil 5
        titleEngineMap.put("425307E0", "Gamebryo"); // Fallout New Vegas
        titleEngineMap.put("545407DE", "Gamebryo"); // Civilization Revolution
        titleEngineMap.put("45410830", "Source"); // Left 4 Dead
        titleEngineMap.put("58410943", "Source"); // Portal: Still Alive
        titleEngineMap.put("58411244", "Unity"); // Frogger: Hyper Arcade Edition
        titleEngineMap.put("425307EC", "id Tech"); // RAGE
        titleEngineMap.put("565507D3", "LithTech"); // F.E.A.R.
        titleEngineMap.put("5454082B", "RenderWare"); // Grand Theft Auto: San Andreas (Xbox Originals)
        titleEngineMap.put("434D0846", "EGO Engine"); // DiRT 3
        titleEngineMap.put("54540842", "RAGE Engine"); // Grand Theft Auto V
        // And more can be added...
    }

    public String detectEngine(String titleId) {
        return titleEngineMap.get(titleId); // Returns null if not found
    }

    public void applyEngineProfile(String engine, Emulator.Config config) {
        if (engine == null) return;

        Log.i(TAG, "Applying profile for engine: " + engine);

        // This acts as the Engine Profile layer (global defaults < engine profile < user overrides).
        // It's applied immediately before boot in EmulatorActivity.
        switch (engine) {
            case "Unreal":
            case "Source":
                // Optimize occlusion queries
                config.save_config_entry("GPU|query_occlusion_sample_lower_threshold", "500");
                break;
            case "MT Framework":
            case "Frostbite":
            case "EGO Engine":
                // GPU readback intensive
                config.save_config_entry("GPU|readback_resolve", "true");
                config.save_config_entry("GPU|readback_memexport", "true");
                break;
            case "Gamebryo":
                // Fix timing / shader corruption
                config.save_config_entry("GPU|vsync", "true");
                break;
            case "Unity":
                // CPU / memory protection needs
                config.save_config_entry("Memory|protect_zero", "false");
                break;
            case "id Tech":
                // VSync sensitive
                config.save_config_entry("GPU|vsync", "true");
                break;
            case "RenderWare":
                break;
            case "RAGE Engine":
                break;
            case "LithTech":
                break;
        }
    }
}
