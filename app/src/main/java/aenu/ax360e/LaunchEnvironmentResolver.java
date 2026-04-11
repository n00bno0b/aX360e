package aenu.ax360e;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves and applies the final launch environment for the emulator.
 * This is the single authoritative path for environment variable setup,
 * combining Turnip driver configuration, per-game driver selection, engine profiles, and global settings.
 */
public class LaunchEnvironmentResolver {
    private static final String TAG = "LaunchEnvResolver";

    private final Context context;
    private final EngineProfileManager engineProfileManager;

    public LaunchEnvironmentResolver(Context context) {
        this.context = context;
        this.engineProfileManager = new EngineProfileManager(context);
    }

    /**
     * Resolve and apply all environment variables for launching the game.
     * This must be called right before Emulator.get.boot().
     *
     * @param gameUri The URI of the game being launched
     */
    public void resolveAndApply(String gameUri) {
        Log.i(TAG, "Resolving launch environment for: " + gameUri);

        // Get per-game profile
        GameProfileManager profileManager = new GameProfileManager(context);
        GameProfile profile = profileManager.getProfile(gameUri);

        // Detect engine and get engine profile
        EngineProfile engineProfile = engineProfileManager.detectAndGetProfile(
                gameUri, profile.engineOverride);

        // Log engine detection
        if (engineProfile != null) {
            Log.i(TAG, "Engine detected: " + engineProfile.getEngine().getDisplayName());
            Log.i(TAG, "Engine profile: " + engineProfile.getDescription());
        } else {
            Log.i(TAG, "No engine profile applied (unknown or no detection)");
        }

        // Resolve driver selection
        applyDriverSelection(profile);

        // Apply Turnip environment variables
        applyTurnipEnvironment();

        // Note: Engine profile config overrides are applied in the native config system
        // This resolver only handles environment variables

        // Log final environment for debugging
        logFinalEnvironment();
    }

    /**
     * Apply driver selection based on per-game profile.
     */
    private void applyDriverSelection(GameProfile profile) {
        switch (profile.driverSelection) {
            case FORCE_SYSTEM:
                // Clear any custom driver env vars to force system driver
                try {
                    Os.unsetenv("CUSTOM_DRIVER_PATH");
                    Os.unsetenv("VK_ICD_FILENAMES");
                    Os.unsetenv("VK_DRIVER_FILES");
                    Log.i(TAG, "Driver selection: FORCE_SYSTEM (using system libvulkan.so)");
                } catch (ErrnoException e) {
                    Log.e(TAG, "Failed to unset custom driver env vars", e);
                }
                break;

            case CUSTOM:
                // Use specific custom driver (future: support multiple installed drivers)
                // For now, just use the globally installed custom driver
                CustomDriverUtils.setupDriverEnv(context);
                Log.i(TAG, "Driver selection: CUSTOM (custom driver name: " +
                      (profile.customDriverName != null ? profile.customDriverName : "global") + ")");
                break;

            case DEFAULT:
            default:
                // Use global driver setting (check if custom driver is installed)
                CustomDriverUtils.setupDriverEnv(context);
                Log.i(TAG, "Driver selection: DEFAULT (using global driver setting)");
                break;
        }
    }

    /**
     * Apply Turnip-specific environment variables based on global settings.
     */
    private void applyTurnipEnvironment() {
        TurnipEnvManager turnipManager = new TurnipEnvManager(context);
        List<TurnipEnvManager.EnvVar> envVars = turnipManager.buildEnvironmentVariables();

        for (TurnipEnvManager.EnvVar envVar : envVars) {
            try {
                Os.setenv(envVar.name, envVar.value, true);
                Log.d(TAG, "Set " + envVar.name + "=" + envVar.value);
            } catch (ErrnoException e) {
                Log.e(TAG, "Failed to set " + envVar.name, e);
            }
        }

        if (envVars.isEmpty()) {
            Log.i(TAG, "No Turnip environment variables configured");
        } else {
            Log.i(TAG, "Applied " + envVars.size() + " Turnip environment variables");
        }
    }

    /**
     * Log the final resolved environment for debugging.
     */
    private void logFinalEnvironment() {
        String[] envVarsToLog = {
            "CUSTOM_DRIVER_PATH",
            "VK_ICD_FILENAMES",
            "VK_DRIVER_FILES",
            "TU_DEBUG",
            "FD_DEV_FEATURES",
            "MESA_DEBUG"
        };

        Log.i(TAG, "=== Final Launch Environment ===");
        for (String varName : envVarsToLog) {
            String value = System.getenv(varName);
            if (value != null) {
                Log.i(TAG, "  " + varName + "=" + value);
            }
        }
        Log.i(TAG, "================================");
    }
}
