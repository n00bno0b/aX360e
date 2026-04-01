package aenu.ax360e;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class TurnipDriverInfo {
    private static final String TAG = "TurnipDriverInfo";
    
    private String driverName = "Unknown";
    private String driverVersion = "Unknown";
    private String mesaVersion = "Unknown";
    private boolean isTurnip = false;
    private boolean isInstalled = false;
    
    public static TurnipDriverInfo detect(Context context) {
        TurnipDriverInfo info = new TurnipDriverInfo();
        
        File driverDir = CustomDriverUtils.getDriverDirectory(context);
        File icdFile = new File(driverDir, "vk_icd.json");
        
        if (icdFile.exists()) {
            info.isInstalled = true;
            try (BufferedReader reader = new BufferedReader(new FileReader(icdFile))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                
                JSONObject json = new JSONObject(sb.toString());
                JSONObject icd = json.getJSONObject("ICD");
                String libraryPath = icd.getString("library_path");
                
                // Check if it's Turnip driver
                if (libraryPath.contains("turnip") || libraryPath.contains("Turnip")) {
                    info.isTurnip = true;
                    info.driverName = "Mesa Turnip";
                    
                    // Try to extract version from filename
                    File soFile = new File(libraryPath);
                    String filename = soFile.getName();
                    info.parseVersionFromFilename(filename);
                }
                
                info.driverVersion = icd.optString("api_version", "Unknown");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Failed to read driver info", e);
            }
        }
        
        return info;
    }
    
    private void parseVersionFromFilename(String filename) {
        // Common pattern: libvulkan_freedreno.so or turnip_26.0.0.so
        if (filename.contains("26.0") || filename.contains("26_0")) {
            mesaVersion = "Mesa 26.0.0";
        } else if (filename.contains("25.2") || filename.contains("25_2")) {
            mesaVersion = "Mesa 25.2.0";
        } else if (filename.contains("25.0") || filename.contains("25_0")) {
            mesaVersion = "Mesa 25.0.0";
        }
        
        // Try to extract R version (R7, R8, etc.)
        if (filename.toLowerCase().contains("r8")) {
            mesaVersion += " R8";
        } else if (filename.toLowerCase().contains("r7")) {
            mesaVersion += " R7";
        } else if (filename.toLowerCase().contains("r6")) {
            mesaVersion += " R6";
        }
    }
    
    public String getDriverName() {
        return driverName;
    }
    
    public String getDriverVersion() {
        return driverVersion;
    }
    
    public String getMesaVersion() {
        return mesaVersion;
    }
    
    public boolean isTurnip() {
        return isTurnip;
    }
    
    public boolean isInstalled() {
        return isInstalled;
    }
    
    public String getFormattedInfo() {
        if (!isInstalled) {
            return "No custom driver installed";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(driverName);
        if (!mesaVersion.equals("Unknown")) {
            sb.append("\n").append(mesaVersion);
        }
        sb.append("\nVulkan ").append(driverVersion);
        
        return sb.toString();
    }
    
    public String getRecommendedGpu() {
        if (!isTurnip || mesaVersion.equals("Unknown")) {
            return "Unknown";
        }
        
        if (mesaVersion.contains("26.0")) {
            return "Recommended for Adreno 840, 750";
        } else if (mesaVersion.contains("25.2")) {
            return "Recommended for Adreno 750";
        } else if (mesaVersion.contains("25.0")) {
            return "Recommended for Adreno 740";
        }
        
        return "Check compatibility";
    }
}
