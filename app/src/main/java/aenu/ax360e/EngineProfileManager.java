package aenu.ax360e;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages engine profiles and applies them with proper precedence.
 * Config precedence: global defaults < engine profile < per-game user overrides < session overrides
 */
public class EngineProfileManager {
    private static final String TAG = "EngineProfileMgr";

    private final Context context;
    private final EngineDetector detector;
    private final Map<GameEngine, EngineProfile> profileCache;

    public EngineProfileManager(Context context) {
        this.context = context;
        this.detector = new EngineDetector(context);
        this.profileCache = new HashMap<>();

        // Pre-load detector mappings
        detector.loadMappings();
    }

    /**
     * Get engine profile for a specific engine.
     * Profiles are cached for performance.
     */
    public EngineProfile getProfile(GameEngine engine) {
        if (engine == null || engine == GameEngine.UNKNOWN) {
            return null;
        }

        EngineProfile profile = profileCache.get(engine);
        if (profile == null) {
            profile = new EngineProfile(engine);
            profileCache.put(engine, profile);
        }

        return profile;
    }

    /**
     * Detect engine for a game and return its profile.
     *
     * @param gameUri Game URI
     * @param manualOverride Manual engine override from GameProfile (takes precedence)
     * @return Engine profile or null if no engine detected
     */
    public EngineProfile detectAndGetProfile(String gameUri, String manualOverride) {
        GameEngine detectedEngine;

        // Manual override takes precedence
        if (manualOverride != null && !manualOverride.isEmpty()) {
            detectedEngine = GameEngine.fromString(manualOverride);
            if (detectedEngine != GameEngine.UNKNOWN) {
                Log.i(TAG, "Using manual engine override: " + detectedEngine.getDisplayName());
                return getProfile(detectedEngine);
            }
        }

        // Auto-detect from title ID
        detectedEngine = detector.detectEngineFromUri(gameUri);
        if (detectedEngine == GameEngine.UNKNOWN) {
            Log.d(TAG, "No engine detected for: " + gameUri);
            return null;
        }

        Log.i(TAG, "Auto-detected engine: " + detectedEngine.getDisplayName());
        return getProfile(detectedEngine);
    }

    /**
     * Apply engine profile configuration with proper precedence.
     * Engine profile settings act as a middle layer between global defaults and per-game overrides.
     *
     * @param baseConfig Base configuration (typically global defaults)
     * @param profile Engine profile to apply
     * @return Configuration with engine profile applied
     */
    public Map<String, String> applyEngineProfile(Map<String, String> baseConfig, EngineProfile profile) {
        if (profile == null) {
            return baseConfig;
        }

        Map<String, String> result = new HashMap<>(baseConfig);

        // Apply engine profile overrides (don't force - let existing settings win)
        profile.applyToConfig(result, false);

        Log.d(TAG, "Applied engine profile: " + profile.getEngine().getDisplayName() +
                " with " + profile.getConfigOverrides().size() + " overrides");

        return result;
    }

    /**
     * Get the engine detector for external use (e.g., UI).
     */
    public EngineDetector getDetector() {
        return detector;
    }
}
