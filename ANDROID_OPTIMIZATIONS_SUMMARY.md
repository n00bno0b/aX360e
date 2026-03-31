# Android-Specific Optimizations Summary

## Overview

This document describes the Android-specific optimizations implemented for the aX360e Xbox 360 emulator, focusing on Turnip driver integration, performance monitoring, and mobile GPU optimizations.

## New Components

### 1. TurnipDriverInfo (`TurnipDriverInfo.java`)

**Purpose**: Detects and provides information about installed custom Turnip drivers for Adreno GPUs.

**Key Features**:
- Automatically detects Turnip driver installation by reading `vk_icd.json` manifest
- Parses driver metadata: name, version, Mesa version
- Provides GPU-specific recommendations (A6xx/A7xx series optimization notes)
- Displays formatted driver information in settings UI

**Usage**:
```java
TurnipDriverInfo info = TurnipDriverInfo.detect(context);
if (info != null) {
    String driverName = info.getDriverName();
    String version = info.getDriverVersion();
    String formatted = info.getFormattedInfo();
}
```

**Integration Point**: Settings → Custom Drivers → Turnip Advanced Settings → Driver Info

---

### 2. PerformanceMonitor (`PerformanceMonitor.java`)

**Purpose**: Comprehensive real-time performance monitoring for Android devices.

**Tracked Metrics**:
- **CPU Usage**: Per-app CPU utilization via `/proc/stat`
- **Memory**: Total and available system memory via `ActivityManager`
- **Battery**: Level and charging status via `BatteryManager`
- **Thermal Status**: Device temperature and throttling state via `PowerManager` (Android Q+) with fallback to `thermal_zone0`
- **FPS**: Current and rolling average frame rate
- **Shader Compilation**: Cache hits, compilation count, total time
- **GMEM/eDRAM**: Flush count and FSI overhead tracking

**Key Methods**:
```java
PerformanceMonitor monitor = new PerformanceMonitor(context);
monitor.updateMetrics();  // Call periodically (e.g., every second)

// Update from emulator
monitor.updateFps(60.0f);
monitor.recordShaderCompilation(15, false);  // 15ms, cache miss
monitor.recordGmemFlush();
monitor.recordFsiOverhead(nanoTime);

// Query current state
boolean reduceQuality = monitor.shouldReduceQuality();
String memoryInfo = monitor.getFormattedMemoryUsage();
String thermalInfo = monitor.getFormattedThermalStatus();
```

**Auto-Quality Reduction**: The `shouldReduceQuality()` method returns `true` when:
- Thermal throttling is active (≥ THERMAL_STATUS_MODERATE)
- Battery is below 20% and not charging
- Memory usage exceeds 90% of total

**Integration**: Display in performance overlay or use for dynamic quality adjustments.

---

### 3. TurnipEnvManager (`TurnipEnvManager.java`)

**Purpose**: Manages Turnip driver environment variables and applies performance presets.

**Environment Variables**:
- `TU_DEBUG`: Controls debug output and special modes
  - `gmem`: Enable GMEM/eDRAM-specific logging
  - `noubwc`: Disable UBWC compression
  - `startup`, `nir`: Enable compilation logging
- `FD_DEV_FEATURES`: Enables development features
  - `ubwc_flag_hint`: Use UBWC flag hints for better compression
- `MESA_DEBUG`: General Mesa debugging

**Performance Presets**:

1. **Ultra Performance** (`ultra_performance`)
   - Resolution: 1x (640×480)
   - VSync: Disabled
   - Anti-aliasing: Disabled
   - Dynamic resolution: 50%-100%
   - Power mode: Performance

2. **High Quality** (`high_quality`)
   - Resolution: 2x (1280×960)
   - VSync: Enabled
   - Anti-aliasing: FXAA
   - Dynamic resolution: 75%-150%
   - Power mode: Balanced

3. **Maximum Quality** (`maximum_quality`)
   - Resolution: 3x (1920×1440)
   - VSync: Enabled
   - Anti-aliasing: FXAA + CAS sharpening
   - Dynamic resolution: Disabled, max 200%
   - Power mode: Performance

4. **Battery Optimized** (`battery_optimized`)
   - Resolution: 1x (640×480)
   - VSync: Disabled
   - Anti-aliasing: Disabled
   - Dynamic resolution: 50%-75%
   - Power mode: Battery saver
   - All power-saving features enabled

**Usage**:
```java
TurnipEnvManager envManager = new TurnipEnvManager(context);

// Get environment variables based on preferences
List<TurnipEnvManager.EnvVar> vars = envManager.buildEnvironmentVariables();
for (TurnipEnvManager.EnvVar var : vars) {
    // Apply to native process: setenv(var.name, var.value)
}

// Apply preset
envManager.applyPreset("Presets|ultra_performance", config);
```

---

## Settings UI Integration

### New Settings Categories

#### 1. Turnip Advanced Settings
**Location**: Settings → Turnip Advanced Settings

- **Driver Info**: Displays installed Turnip driver version and metadata
- **GMEM Mode** (`TurnipAdvanced|gmem_mode`): Enable GMEM/eDRAM debug logging
- **UBWC Flag Hint** (`TurnipAdvanced|ubwc_flag_hint`): Use UBWC compression hints
- **Debug Logging** (`TurnipAdvanced|debug_logging`): Enable Turnip debug output
- **No UBWC** (`TurnipAdvanced|noubwc`): Disable UBWC compression entirely

#### 2. Performance Monitoring
**Location**: Settings → Performance Monitoring

- **Show GMEM Stats** (`PerfMonitoring|show_gmem_stats`): Display GMEM flush counts
- **Show Shader Stats** (`PerfMonitoring|show_shader_stats`): Display shader cache hits and compilation time
- **Show eDRAM Overhead** (`PerfMonitoring|show_edram_overhead`): Display FSI overhead
- **Show Thermal Status** (`PerfMonitoring|show_thermal_status`): Display device temperature and throttling state
- **Show Power Usage** (`PerfMonitoring|show_power_usage`): Display battery level and charging status

#### 3. Mobile GPU Optimizations
**Location**: Settings → Mobile GPU Optimizations

- **Dynamic Resolution** (`MobileGPU|dynamic_resolution`): Automatically adjust resolution based on performance
- **Resolution Scale Min** (`MobileGPU|resolution_scale_min`): Minimum scale (50%-100%)
- **Resolution Scale Max** (`MobileGPU|resolution_scale_max`): Maximum scale (100%-200%)
- **Thermal Throttle Aware** (`MobileGPU|thermal_aware`): Reduce quality when throttling detected

#### 4. Power Management
**Location**: Settings → Power Management

- **Power Mode** (`PowerManagement|power_mode`): Performance / Balanced / Battery Saver
- **Background Resource Unload** (`PowerManagement|background_unload`): Free resources when app is backgrounded
- **Low Memory Mode** (`PowerManagement|low_memory_mode`): Aggressive memory management

#### 5. Performance Presets
**Location**: Settings → Performance Presets

- **Ultra Performance**: Maximum FPS, lowest quality
- **High Quality**: Balanced performance and visuals
- **Maximum Quality**: Best visuals, performance secondary
- **Battery Optimized**: Maximum battery life

---

## Implementation Status

### Completed ✓
- [x] TurnipDriverInfo class with driver detection
- [x] PerformanceMonitor class with comprehensive metrics
- [x] TurnipEnvManager class with preset system
- [x] Settings XML structure for all new categories
- [x] EmulatorSettings.java handlers for all new preferences
- [x] String resources for all new settings
- [x] Array resources for power modes

### Pending Integration
- [ ] Connect PerformanceMonitor to native emulator code via JNI
- [ ] Apply TurnipEnvManager environment variables to emulator process
- [ ] Implement dynamic resolution scaling in native code
- [ ] Add performance overlay display using PerformanceMonitor data
- [ ] Implement thermal throttling response logic
- [ ] Per-game driver selection UI and metadata storage
- [ ] Test on actual Android devices with various Adreno GPUs

---

## Technical Details

### Thermal Monitoring

The `PerformanceMonitor` uses Android's `PowerManager` thermal API (Android Q+):
- `THERMAL_STATUS_NONE` (0): Normal operation
- `THERMAL_STATUS_LIGHT` (1): Minor throttling possible
- `THERMAL_STATUS_MODERATE` (2): Throttling likely - **triggers quality reduction**
- `THERMAL_STATUS_SEVERE` (3+): Severe throttling

Fallback for Android P and earlier: Reads `/sys/class/thermal/thermal_zone0/temp`

### GMEM/eDRAM Background

**GMEM** (Graphics Memory) is Adreno's tile-based memory system:
- Small on-chip cache (2-4MB) for render targets
- Eliminates DRAM bandwidth for on-tile operations
- Requires "flushes" when switching render targets or exceeding capacity
- Xbox 360 eDRAM (10MB) requires emulation via FSI (Fragment Shader Interlock)

**Performance Impact**:
- Excessive GMEM flushes → poor performance
- FSI overhead → additional shader execution time
- Monitoring these metrics helps identify bottlenecks

### Turnip Driver Installation

Custom Turnip drivers are installed as ZIP files containing:
```
driver_name/
├── vk_icd.json          # Vulkan ICD manifest
└── libvulkan_freedreno.so  # Turnip driver library
```

The `vk_icd.json` specifies the driver path and version:
```json
{
  "ICD": {
    "api_version": "1.3.0",
    "library_path": "libvulkan_freedreno.so"
  }
}
```

Driver filename format: `turnip-<version>-<gpu_gen>-<mesa_version>.zip`
- Example: `turnip-24.1.0-a6xx-mesa-24.1.0.zip`

---

## Future Enhancements

### Short Term
1. **JNI Bindings**: Expose PerformanceMonitor to native code
2. **Environment Variable Application**: Pass Turnip env vars to emulator process
3. **Overlay Integration**: Display performance metrics in real-time

### Medium Term
1. **Dynamic Resolution**: Implement automatic scaling based on FPS and thermal state
2. **Per-Game Profiles**: Save/load settings per game title
3. **Advanced Driver Selection**: Per-game custom driver assignment

### Long Term
1. **Machine Learning**: Auto-optimize settings based on device capabilities
2. **Telemetry**: Aggregate anonymous performance data for driver/setting recommendations
3. **VK_EXT_shader_tile_image**: Hardware-accelerated eDRAM emulation (requires GPU support)

---

## Testing Recommendations

### Required Test Scenarios
1. **Driver Detection**: Install custom Turnip driver and verify detection
2. **Preset Application**: Apply each preset and verify all settings change
3. **Thermal Monitoring**: Run demanding game until thermal throttling occurs
4. **Low Memory**: Test with <1GB available RAM
5. **Battery Drain**: Test with battery optimization features on/off

### Recommended Test Devices
- **High-end**: Snapdragon 8 Gen 2/3 (Adreno 740/750)
- **Mid-range**: Snapdragon 7 series (Adreno 6xx)
- **Low-end**: Snapdragon 6 series (Adreno 6xx)

### Performance Metrics to Track
- Frame rate stability
- Frame time variance
- Shader compilation stutter
- Battery drain rate
- Device temperature over time

---

## References

- [Mesa Turnip Documentation](https://docs.mesa3d.org/drivers/freedreno.html)
- [Adreno GPU Architecture](https://developer.qualcomm.com/software/adreno-gpu-sdk)
- [Android PowerManager API](https://developer.android.com/reference/android/os/PowerManager)
- [Vulkan on Android](https://developer.android.com/ndk/guides/graphics/getting-started)
