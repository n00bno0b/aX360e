package aenu.ax360e;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Detects game engine based on title ID mapping.
 * This is the Java-side detector for UI purposes.
 * Native-side detection (at XEX load) is authoritative for runtime behavior.
 */
public class EngineDetector {
    private static final String TAG = "EngineDetector";
    private static final String MAPPINGS_FILE = "engine_mappings.json";

    private final Context context;
    private Map<String, GameEngine> titleIdToEngine;
    private boolean initialized = false;

    public EngineDetector(Context context) {
        this.context = context;
        this.titleIdToEngine = new HashMap<>();
    }

    /**
     * Load engine mappings from assets.
     * Should be called once at startup or on-demand.
     */
    public synchronized void loadMappings() {
        if (initialized) {
            return;
        }

        try {
            String json = Application.load_assets_file(MAPPINGS_FILE);
            if (json == null) {
                Log.e(TAG, "Failed to load engine mappings file");
                initialized = true;
                return;
            }

            JSONObject root = new JSONObject(json);
            JSONObject mappings = root.getJSONObject("mappings");

            // Parse each engine category
            for (Iterator<String> it = mappings.keys(); it.hasNext(); ) {
                String engineName = it.next();

                // Skip comments
                if (engineName.startsWith("comment")) {
                    continue;
                }

                GameEngine engine = GameEngine.fromString(engineName);
                if (engine == GameEngine.UNKNOWN) {
                    Log.w(TAG, "Unknown engine in mappings: " + engineName);
                    continue;
                }

                JSONObject engineMappings = mappings.getJSONObject(engineName);
                for (Iterator<String> titleIt = engineMappings.keys(); it.hasNext(); ) {
                    String key = titleIt.next();

                    // Skip comments in engine sections
                    if (key.startsWith("comment")) {
                        continue;
                    }

                    // Title ID is the key, game name is the value (we only need the ID)
                    String titleId = key.toUpperCase();
                    titleIdToEngine.put(titleId, engine);
                }
            }

            Log.i(TAG, "Loaded " + titleIdToEngine.size() + " title ID to engine mappings");
            initialized = true;

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse engine mappings", e);
            initialized = true;
        }
    }

    /**
     * Detect engine for a game based on its title ID.
     * Returns UNKNOWN if no mapping exists.
     *
     * @param titleId Title ID in hex format (8 characters, case-insensitive)
     * @return Detected engine or UNKNOWN
     */
    public GameEngine detectEngine(String titleId) {
        if (!initialized) {
            loadMappings();
        }

        if (titleId == null || titleId.isEmpty()) {
            return GameEngine.UNKNOWN;
        }

        String normalizedId = titleId.toUpperCase().trim();
        GameEngine engine = titleIdToEngine.get(normalizedId);

        if (engine != null) {
            Log.i(TAG, "Detected engine for " + titleId + ": " + engine.getDisplayName());
            return engine;
        }

        return GameEngine.UNKNOWN;
    }

    /**
     * Extract title ID from game URI.
     * Xbox 360 title IDs are typically embedded in the game path or metadata.
     *
     * @param gameUri Game URI
     * @return Title ID or null if not found
     */
    public String extractTitleIdFromUri(String gameUri) {
        if (gameUri == null) {
            return null;
        }

        // Try to extract from URI path
        // Title IDs are 8-character hex strings
        // Common patterns: /titleid/game.xex, /TITLEID/default.xex, etc.

        // Simple extraction: look for 8 consecutive hex characters
        String[] parts = gameUri.split("[/\\\\]");
        for (String part : parts) {
            if (part.length() == 8 && part.matches("[0-9A-Fa-f]{8}")) {
                return part.toUpperCase();
            }
        }

        return null;
    }

    /**
     * Detect engine for a game URI.
     * Convenience method that extracts title ID and detects engine.
     */
    public GameEngine detectEngineFromUri(String gameUri) {
        String titleId = extractTitleIdFromUri(gameUri);
        if (titleId == null) {
            return GameEngine.UNKNOWN;
        }
        return detectEngine(titleId);
    }
}
