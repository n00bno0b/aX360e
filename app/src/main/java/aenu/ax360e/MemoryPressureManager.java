package aenu.ax360e;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

/**
 * Monitors system memory pressure and thermal status to dynamically adjust
 * GPU memory limits. Used to prevent OOM crashes and manage thermal throttling.
 */
public class MemoryPressureManager {
    private static final String TAG = "MemoryPressureManager";
    private static final long CHECK_INTERVAL_MS = 5000; // 5 seconds

    private final Context context;
    private final ActivityManager activityManager;
    private final PowerManager powerManager;
    private long lastCheckTime = 0;
    private MemoryPressure lastPressure;

    public enum PressureLevel {
        NONE(0),      // < 60% RAM used
        LOW(1),       // 60-70% RAM used
        MEDIUM(2),    // 70-80% RAM used
        HIGH(3),      // 80-90% RAM used or < 1GB available
        CRITICAL(4);  // > 90% RAM used or < 500MB available

        private final int value;

        PressureLevel(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static class MemoryPressure {
        public PressureLevel level;
        public long availableMB;
        public long totalMB;
        public int thermalLevel; // 0-6 from PowerManager.THERMAL_STATUS_*

        public MemoryPressure(PressureLevel level, long availableMB, long totalMB, int thermalLevel) {
            this.level = level;
            this.availableMB = availableMB;
            this.totalMB = totalMB;
            this.thermalLevel = thermalLevel;
        }

        @Override
        public String toString() {
            return String.format("MemoryPressure{level=%s, available=%dMB/%dMB, thermal=%d}",
                    level, availableMB, totalMB, thermalLevel);
        }
    }

    public MemoryPressureManager(Context context) {
        this.context = context;
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.lastPressure = new MemoryPressure(PressureLevel.NONE, 0, 0, 0);
    }

    /**
     * Get current memory pressure. Results are cached for CHECK_INTERVAL_MS
     * to avoid excessive system calls.
     */
    public MemoryPressure getMemoryPressure() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS && lastPressure != null) {
            return lastPressure;
        }

        lastCheckTime = now;
        lastPressure = calculateMemoryPressure();
        return lastPressure;
    }

    /**
     * Force immediate memory pressure check, bypassing cache.
     */
    public MemoryPressure getMemoryPressureImmediate() {
        lastCheckTime = System.currentTimeMillis();
        lastPressure = calculateMemoryPressure();
        return lastPressure;
    }

    private MemoryPressure calculateMemoryPressure() {
        if (activityManager == null) {
            Log.w(TAG, "ActivityManager is null, returning NONE pressure");
            return new MemoryPressure(PressureLevel.NONE, 0, 0, 0);
        }

        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);

        long availableMB = memInfo.availMem / (1024 * 1024);
        long totalMB = memInfo.totalMem / (1024 * 1024);
        double pressureRatio = 1.0 - ((double) memInfo.availMem / memInfo.totalMem);

        int thermalLevel = getThermalLevel();

        // Determine pressure level based on available memory and pressure ratio
        PressureLevel level;
        if (pressureRatio > 0.9 || availableMB < 500) {
            level = PressureLevel.CRITICAL;
        } else if (pressureRatio > 0.8 || availableMB < 1000) {
            level = PressureLevel.HIGH;
        } else if (pressureRatio > 0.7) {
            level = PressureLevel.MEDIUM;
        } else if (pressureRatio > 0.6) {
            level = PressureLevel.LOW;
        } else {
            level = PressureLevel.NONE;
        }

        MemoryPressure pressure = new MemoryPressure(level, availableMB, totalMB, thermalLevel);

        // Log significant changes
        if (lastPressure == null || lastPressure.level != level) {
            Log.i(TAG, "Memory pressure changed: " + pressure);
        }

        return pressure;
    }

    private int getThermalLevel() {
        if (powerManager == null) {
            return 0;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                int status = powerManager.getCurrentThermalStatus();
                // PowerManager.THERMAL_STATUS_NONE = 0
                // PowerManager.THERMAL_STATUS_LIGHT = 1
                // PowerManager.THERMAL_STATUS_MODERATE = 2
                // PowerManager.THERMAL_STATUS_SEVERE = 3
                // PowerManager.THERMAL_STATUS_CRITICAL = 4
                // PowerManager.THERMAL_STATUS_EMERGENCY = 5
                // PowerManager.THERMAL_STATUS_SHUTDOWN = 6
                return status;
            } catch (Exception e) {
                Log.w(TAG, "Failed to get thermal status: " + e.getMessage());
                return 0;
            }
        }

        return 0; // Pre-Q devices don't have thermal API
    }

    /**
     * Get recommended texture cache scale factor based on current pressure.
     * Returns value between 0.5 (critical) and 1.0 (none).
     */
    public double getTextureCacheScaleFactor() {
        MemoryPressure pressure = getMemoryPressure();

        double scaleFactor = 1.0;

        switch (pressure.level) {
            case CRITICAL:
                scaleFactor = 0.5;
                break;
            case HIGH:
                scaleFactor = 0.7;
                break;
            case MEDIUM:
                scaleFactor = 0.85;
                break;
            case LOW:
                scaleFactor = 0.95;
                break;
            case NONE:
            default:
                scaleFactor = 1.0;
                break;
        }

        // Apply thermal throttling factor
        if (pressure.thermalLevel >= 4) { // THERMAL_STATUS_CRITICAL or higher
            scaleFactor *= 0.8;
        } else if (pressure.thermalLevel >= 3) { // THERMAL_STATUS_SEVERE
            scaleFactor *= 0.9;
        }

        return scaleFactor;
    }

    /**
     * Check if aggressive texture eviction should be triggered.
     */
    public boolean shouldEvictTextures() {
        MemoryPressure pressure = getMemoryPressure();
        return pressure.level.getValue() >= PressureLevel.HIGH.getValue();
    }

    /**
     * Get memory pressure level as integer for JNI (0-4).
     */
    public int getPressureLevelValue() {
        return getMemoryPressure().level.getValue();
    }
}
