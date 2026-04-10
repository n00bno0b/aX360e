package aenu.ax360e;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class GameMetadataManager {
    private static final String PREFS_NAME = "game_metadata";
    private static final String KEY_METADATA_PREFIX = "meta_";
    
    private final SharedPreferences prefs;
    private final Map<String, GameMetadata> cache;
    
    public GameMetadataManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cache = new HashMap<>();
        loadAllMetadata();
    }
    
    private void loadAllMetadata() {
        Map<String, ?> allPrefs = prefs.getAll();
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().startsWith(KEY_METADATA_PREFIX)) {
                try {
                    String gameUri = entry.getKey().substring(KEY_METADATA_PREFIX.length());
                    JSONObject json = new JSONObject((String) entry.getValue());
                    GameMetadata metadata = GameMetadata.fromJson(json);
                    cache.put(gameUri, metadata);
                } catch (JSONException e) {
                    android.util.Log.e("GameMetadata", "Failed to parse metadata for: " + entry.getKey(), e);
                }
            }
        }
    }
    
    public GameMetadata getMetadata(String gameUri) {
        if (cache.containsKey(gameUri)) {
            return cache.get(gameUri);
        }
        
        GameMetadata metadata = new GameMetadata(gameUri);
        cache.put(gameUri, metadata);
        return metadata;
    }
    
    public void updateMetadata(String gameUri, GameMetadata metadata) {
        cache.put(gameUri, metadata);
        saveMetadata(gameUri, metadata);
    }
    
    public void setFavorite(String gameUri, boolean isFavorite) {
        GameMetadata metadata = getMetadata(gameUri);
        metadata.isFavorite = isFavorite;
        updateMetadata(gameUri, metadata);
    }
    
    public void updatePlayTime(String gameUri, long sessionTime) {
        GameMetadata metadata = getMetadata(gameUri);
        metadata.totalPlayTime += sessionTime;
        metadata.lastPlayed = System.currentTimeMillis();
        updateMetadata(gameUri, metadata);
    }
    
    public void setCompatibilityRating(String gameUri, String rating) {
        GameMetadata metadata = getMetadata(gameUri);
        metadata.compatibilityRating = rating;
        updateMetadata(gameUri, metadata);
    }

    public void setCoverArtPath(String gameUri, String coverArtPath) {
        GameMetadata metadata = getMetadata(gameUri);
        metadata.coverArtPath = coverArtPath;
        updateMetadata(gameUri, metadata);
    }
    
    private void saveMetadata(String gameUri, GameMetadata metadata) {
        try {
            JSONObject json = metadata.toJson();
            prefs.edit()
                .putString(KEY_METADATA_PREFIX + gameUri, json.toString())
                .apply();
        } catch (JSONException e) {
            android.util.Log.e("GameMetadata", "Failed to save metadata for: " + gameUri, e);
        }
    }
    
    public void deleteMetadata(String gameUri) {
        cache.remove(gameUri);
        prefs.edit()
            .remove(KEY_METADATA_PREFIX + gameUri)
            .apply();
    }
    
    public void clearAllMetadata() {
        cache.clear();
        prefs.edit().clear().apply();
    }
}
