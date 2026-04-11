package aenu.ax360e;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages per-game profiles (launch preferences and overrides).
 * Profiles are stored separately from GameMetadata.
 */
public class GameProfileManager {
    private static final String TAG = "GameProfileManager";
    private static final String PROFILES_FILE = "game_profiles.json";

    private final Context context;
    private final Map<String, GameProfile> profiles;
    private final File profilesFile;

    public GameProfileManager(Context context) {
        this.context = context;
        this.profiles = new HashMap<>();
        this.profilesFile = new File(Application.get_app_data_dir(), PROFILES_FILE);
        loadProfiles();
    }

    /**
     * Get profile for a game. Returns a default profile if none exists.
     */
    public GameProfile getProfile(String gameUri) {
        if (gameUri == null) {
            return new GameProfile();
        }

        synchronized (profiles) {
            GameProfile profile = profiles.get(gameUri);
            if (profile == null) {
                profile = new GameProfile(gameUri);
                profiles.put(gameUri, profile);
            }
            return profile;
        }
    }

    /**
     * Save profile for a game.
     */
    public void saveProfile(GameProfile profile) {
        if (profile == null || profile.gameUri == null) {
            return;
        }

        synchronized (profiles) {
            profiles.put(profile.gameUri, profile);
            saveProfiles();
        }
    }

    /**
     * Load all profiles from disk.
     */
    private void loadProfiles() {
        if (!profilesFile.exists()) {
            Log.i(TAG, "No profiles file found, starting fresh");
            return;
        }

        try (FileInputStream fis = new FileInputStream(profilesFile)) {
            byte[] data = new byte[(int) profilesFile.length()];
            if (data.length > 10 * 1024 * 1024) { // 10MB safety limit
                Log.e(TAG, "Profiles file too large: " + data.length + " bytes");
                return;
            }

            fis.read(data);
            JSONObject root = new JSONObject(new String(data));
            JSONArray profileArray = root.getJSONArray("profiles");

            synchronized (profiles) {
                profiles.clear();
                for (int i = 0; i < profileArray.length(); i++) {
                    try {
                        JSONObject profileJson = profileArray.getJSONObject(i);
                        GameProfile profile = GameProfile.fromJson(profileJson);
                        profiles.put(profile.gameUri, profile);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse profile at index " + i, e);
                    }
                }
            }

            Log.i(TAG, "Loaded " + profiles.size() + " game profiles");
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load profiles", e);
        }
    }

    /**
     * Save all profiles to disk.
     */
    private void saveProfiles() {
        try {
            JSONArray profileArray = new JSONArray();

            synchronized (profiles) {
                for (GameProfile profile : profiles.values()) {
                    try {
                        profileArray.put(profile.toJson());
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to serialize profile for " + profile.gameUri, e);
                    }
                }
            }

            JSONObject root = new JSONObject();
            root.put("profiles", profileArray);

            try (FileOutputStream fos = new FileOutputStream(profilesFile)) {
                fos.write(root.toString(2).getBytes());
            }

            Log.i(TAG, "Saved " + profileArray.length() + " game profiles");
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to save profiles", e);
        }
    }
}
