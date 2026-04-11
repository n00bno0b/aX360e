package aenu.ax360e;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;

public class PerformanceMonitor {
    private static final String TAG = "PerformanceMonitor";

    /**
     * Performance state classification for native consumption.
     * Used to inform dynamic resolution and quality adjustments.
     */
    public enum PerformanceState {
        NORMAL(0),          // Everything running smoothly
        PRESSURED(1),       // Starting to show strain (low battery, high memory)
        THROTTLING(2),      // Thermal throttling active
        CRITICAL(3);        // Critical condition (very low battery + throttling + low memory)

        private final int value;

        PerformanceState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private final Context context;
    private long lastCpuTime = 0;
    private long lastAppCpuTime = 0;
    private long lastUpdateTime = 0;
    
    // Performance metrics
    private float currentFps = 0;
    private float averageFps = 0;
    private float cpuUsage = 0;
    private float gpuUsage = 0;  // Estimated
    private long memoryUsed = 0;
    private long memoryTotal = 0;
    private float batteryLevel = 100;
    private float deviceTemperature = 0;
    private boolean isThermalThrottling = false;
    
    // Shader compilation tracking
    private int shadersCompiled = 0;
    private int shaderCacheHits = 0;
    private long shaderCompilationTime = 0;

    // eDRAM/GMEM tracking
    private long gmemFlushes = 0;
    private long fsiOverhead = 0;

    // Metrics push tracking
    private long lastMetricsPushTime = 0;
    private static final long METRICS_PUSH_INTERVAL_MS = 2000; // Push every 2 seconds
    private PerformanceState lastPushedState = PerformanceState.NORMAL;

    public PerformanceMonitor(Context context) {
        this.context = context;
        this.lastUpdateTime = SystemClock.elapsedRealtime();
    }
    
    public void updateMetrics() {
        updateCpuUsage();
        updateMemoryUsage();
        updateBatteryLevel();
        updateThermalStatus();
    }
    
    private void updateCpuUsage() {
        try {
            long currentTime = SystemClock.elapsedRealtime();
            long elapsedTime = currentTime - lastUpdateTime;
            
            if (elapsedTime < 1000) return; // Update every second
            
            // Read /proc/stat for total CPU time
            String load;
            try (RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r")) {
                load = reader.readLine();
            }
            
            String[] toks = load.split(" +");
            long idle = Long.parseLong(toks[4]);
            long cpu = Long.parseLong(toks[1]) + Long.parseLong(toks[2]) + Long.parseLong(toks[3]);
            long total = idle + cpu;
            
            if (lastCpuTime > 0) {
                long cpuDiff = total - lastCpuTime;
                long appDiff = (long) (Debug.threadCpuTimeNanos() / 1000000);
                
                if (cpuDiff > 0) {
                    cpuUsage = (float) (appDiff - lastAppCpuTime) / cpuDiff * 100;
                    cpuUsage = Math.max(0, Math.min(100, cpuUsage));
                }
            }
            
            lastCpuTime = total;
            lastAppCpuTime = (long) (Debug.threadCpuTimeNanos() / 1000000);
            lastUpdateTime = currentTime;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read CPU usage", e);
        }
    }
    
    private void updateMemoryUsage() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return;
        activityManager.getMemoryInfo(mi);
        
        memoryTotal = mi.totalMem;
        memoryUsed = memoryTotal - mi.availMem;
        
        // Check for low memory condition
        if (mi.lowMemory) {
            Log.w(TAG, "Device is in low memory condition");
        }
    }
    
    private void updateBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batteryLevel = level * 100 / (float) scale;
        }
    }
    
    private void updateThermalStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return;
            int thermalStatus = powerManager.getCurrentThermalStatus();
            
            // THERMAL_STATUS_NONE = 0, THERMAL_STATUS_LIGHT = 1, 
            // THERMAL_STATUS_MODERATE = 2, THERMAL_STATUS_SEVERE = 3, etc.
            isThermalThrottling = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE;
            
            // Estimate temperature (rough approximation)
            deviceTemperature = 25 + (thermalStatus * 10); // 25°C + 10°C per level
        } else {
            // Fallback for older Android versions - try reading thermal zone
            try (BufferedReader reader = new BufferedReader(new FileReader("/sys/class/thermal/thermal_zone0/temp"))) {
                String temp = reader.readLine();
                deviceTemperature = Integer.parseInt(temp) / 1000.0f; // Convert from millidegrees
                isThermalThrottling = deviceTemperature > 60; // Threshold for throttling
            } catch (IOException | NumberFormatException e) {
                // Couldn't read temperature
                deviceTemperature = 0;
            }
        }
    }
    
    public void updateFps(float fps) {
        this.currentFps = fps;
        // Calculate rolling average
        this.averageFps = (averageFps * 0.9f) + (fps * 0.1f);
    }
    
    public void recordShaderCompilation(long timeMs, boolean cacheHit) {
        if (cacheHit) {
            shaderCacheHits++;
        } else {
            shadersCompiled++;
            shaderCompilationTime += timeMs;
        }
    }
    
    public void recordGmemFlush() {
        gmemFlushes++;
    }
    
    public void recordFsiOverhead(long timeNs) {
        fsiOverhead += timeNs;
    }
    
    // Getters
    public float getCurrentFps() { return currentFps; }
    public float getAverageFps() { return averageFps; }
    public float getCpuUsage() { return cpuUsage; }
    public float getGpuUsage() { return gpuUsage; }
    public long getMemoryUsed() { return memoryUsed; }
    public long getMemoryTotal() { return memoryTotal; }
    public float getBatteryLevel() { return batteryLevel; }
    public float getDeviceTemperature() { return deviceTemperature; }
    public boolean isThermalThrottling() { return isThermalThrottling; }
    
    public int getShadersCompiled() { return shadersCompiled; }
    public int getShaderCacheHits() { return shaderCacheHits; }
    public long getShaderCompilationTime() { return shaderCompilationTime; }
    public float getShaderCacheHitRate() {
        int total = shadersCompiled + shaderCacheHits;
        return total > 0 ? (shaderCacheHits * 100.0f / total) : 0;
    }
    
    public long getGmemFlushes() { return gmemFlushes; }
    public long getFsiOverhead() { return fsiOverhead; }
    
    public String getFormattedMemoryUsage() {
        float usedMB = memoryUsed / (1024.0f * 1024.0f);
        float totalMB = memoryTotal / (1024.0f * 1024.0f);
        return String.format("%.0f / %.0f MB", usedMB, totalMB);
    }
    
    public String getFormattedThermalStatus() {
        if (deviceTemperature > 0) {
            return String.format("%.1f°C%s", deviceTemperature, 
                isThermalThrottling ? " (Throttling)" : "");
        }
        return "Unknown";
    }
    
    public boolean shouldReduceQuality() {
        // Suggest quality reduction if:
        // - Thermal throttling is active
        // - Battery is low and we're not charging
        // - Memory is critically low
        return isThermalThrottling ||
               (batteryLevel < 20 && !isCharging()) ||
               (memoryUsed > memoryTotal * 0.9);
    }

    public PerformanceState getPerformanceState() {
        boolean lowBattery = batteryLevel < 20 && !isCharging();
        boolean highMemory = memoryUsed > memoryTotal * 0.85;

        // CRITICAL: multiple severe conditions
        if (isThermalThrottling && (lowBattery || highMemory)) {
            return PerformanceState.CRITICAL;
        }

        // THROTTLING: thermal issues
        if (isThermalThrottling) {
            return PerformanceState.THROTTLING;
        }

        // PRESSURED: starting to show strain
        if (lowBattery || highMemory || (memoryUsed > memoryTotal * 0.75)) {
            return PerformanceState.PRESSURED;
        }

        // NORMAL: everything OK
        return PerformanceState.NORMAL;
    }

    /**
     * Push metrics snapshot to native code.
     * Should be called after updateMetrics().
     * Pushes on fixed cadence (2s) and on thermal state transitions.
     */
    public void pushMetricsToNative() {
        if (Emulator.get == null) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        PerformanceState currentState = getPerformanceState();

        // Push on state change or at regular intervals
        boolean stateChanged = currentState != lastPushedState;
        boolean intervalElapsed = (now - lastMetricsPushTime) >= METRICS_PUSH_INTERVAL_MS;

        if (stateChanged || intervalElapsed) {
            float memUsedMB = memoryUsed / (1024.0f * 1024.0f);
            float memTotalMB = memoryTotal / (1024.0f * 1024.0f);
            float frameTimeMs = currentFps > 0 ? (1000.0f / currentFps) : 0;

            try {
                Emulator.get.push_performance_metrics(
                        currentFps,
                        frameTimeMs,
                        currentState.getValue(),
                        memUsedMB,
                        memTotalMB,
                        deviceTemperature
                );

                lastMetricsPushTime = now;
                lastPushedState = currentState;

                if (stateChanged) {
                    Log.i(TAG, "Performance state changed to: " + currentState);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to push metrics to native", e);
            }
        }
    }

    private boolean isCharging() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                   status == BatteryManager.BATTERY_STATUS_FULL;
        }
        return false;
    }

}
