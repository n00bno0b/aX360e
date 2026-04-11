package aenu.ax360e;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Per-game profile containing launch preferences and settings.
 * This is separate from GameMetadata which contains descriptive information.
 * GameProfile stores user-configurable runtime settings per game.
 */
public class GameProfile {
    public enum DriverSelection {
        DEFAULT,           // Use global driver setting
        CUSTOM,           // Use specific custom Turnip driver
        FORCE_SYSTEM      // Force system libvulkan.so
    }

    public String gameUri;
    public DriverSelection driverSelection;
    public String customDriverName;  // Name/path of specific custom driver (if CUSTOM mode)
    public String engineOverride;     // Manual engine override (null = auto-detect)

    // Resolution/quality overrides (null = use global/engine defaults)
    public Integer resolutionScale;   // Percentage: 50-200
    public Boolean dynamicResolution;
    public Boolean vsync;

    public GameProfile() {
        this.driverSelection = DriverSelection.DEFAULT;
        this.customDriverName = null;
        this.engineOverride = null;
        this.resolutionScale = null;
        this.dynamicResolution = null;
        this.vsync = null;
    }

    public GameProfile(String gameUri) {
        this();
        this.gameUri = gameUri;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("gameUri", gameUri);
        json.put("driverSelection", driverSelection.name());
        if (customDriverName != null) {
            json.put("customDriverName", customDriverName);
        }
        if (engineOverride != null) {
            json.put("engineOverride", engineOverride);
        }
        if (resolutionScale != null) {
            json.put("resolutionScale", resolutionScale);
        }
        if (dynamicResolution != null) {
            json.put("dynamicResolution", dynamicResolution);
        }
        if (vsync != null) {
            json.put("vsync", vsync);
        }
        return json;
    }

    public static GameProfile fromJson(JSONObject json) throws JSONException {
        GameProfile profile = new GameProfile();
        profile.gameUri = json.getString("gameUri");

        String driverStr = json.optString("driverSelection", "DEFAULT");
        try {
            profile.driverSelection = DriverSelection.valueOf(driverStr);
        } catch (IllegalArgumentException e) {
            profile.driverSelection = DriverSelection.DEFAULT;
        }

        profile.customDriverName = json.optString("customDriverName", null);
        profile.engineOverride = json.optString("engineOverride", null);

        if (json.has("resolutionScale")) {
            profile.resolutionScale = json.getInt("resolutionScale");
        }
        if (json.has("dynamicResolution")) {
            profile.dynamicResolution = json.getBoolean("dynamicResolution");
        }
        if (json.has("vsync")) {
            profile.vsync = json.getBoolean("vsync");
        }

        return profile;
    }
}
