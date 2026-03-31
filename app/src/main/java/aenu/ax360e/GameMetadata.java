package aenu.ax360e;

import org.json.JSONException;
import org.json.JSONObject;

public class GameMetadata {
    public String gameUri;
    public long lastPlayed;
    public long totalPlayTime;
    public boolean isFavorite;
    public String compatibilityRating;
    
    public GameMetadata() {
        this.lastPlayed = 0;
        this.totalPlayTime = 0;
        this.isFavorite = false;
        this.compatibilityRating = "unknown";
    }
    
    public GameMetadata(String gameUri) {
        this();
        this.gameUri = gameUri;
    }
    
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("gameUri", gameUri);
        json.put("lastPlayed", lastPlayed);
        json.put("totalPlayTime", totalPlayTime);
        json.put("isFavorite", isFavorite);
        json.put("compatibilityRating", compatibilityRating);
        return json;
    }
    
    public static GameMetadata fromJson(JSONObject json) throws JSONException {
        GameMetadata metadata = new GameMetadata();
        metadata.gameUri = json.getString("gameUri");
        metadata.lastPlayed = json.optLong("lastPlayed", 0);
        metadata.totalPlayTime = json.optLong("totalPlayTime", 0);
        metadata.isFavorite = json.optBoolean("isFavorite", false);
        metadata.compatibilityRating = json.optString("compatibilityRating", "unknown");
        return metadata;
    }
    
    public String getFormattedPlayTime() {
        if (totalPlayTime == 0) return "Never played";
        
        long hours = totalPlayTime / 3600;
        long minutes = (totalPlayTime % 3600) / 60;
        
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }
    
    public String getFormattedLastPlayed() {
        if (lastPlayed == 0) return "Never";
        
        long now = System.currentTimeMillis();
        long diff = now - lastPlayed;
        long days = diff / (1000 * 60 * 60 * 24);
        
        if (days == 0) return "Today";
        if (days == 1) return "Yesterday";
        if (days < 7) return days + " days ago";
        if (days < 30) return (days / 7) + " weeks ago";
        if (days < 365) return (days / 30) + " months ago";
        return (days / 365) + " years ago";
    }
}
