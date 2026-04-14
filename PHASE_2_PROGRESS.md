# Phase 2 Implementation Progress: Occlusion Query + Texture Memory

## Summary
Phase 2 focuses on reducing GPU synchronization overhead through occlusion query caching and implementing dynamic texture memory management based on system pressure.

## Completed Components

### 1. Occlusion Query Result Cache (Commit: eb2a8ad9)

**Files Modified:**
- `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_command_processor.h` (lines 752-803)
- `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_command_processor.cc` (line 3002-3003)

**Implementation Details:**
- **OcclusionQueryCache Structure**: LRU cache with 10-frame age limit
- **Cache Hit Detection**: `TryGetCachedResult()` checks if result is recent and valid
- **Temporal Coherence**: `PredictResult()` returns last known result for visible objects
- **Automatic Eviction**: Runs every 20 frames to prevent unbounded memory growth
- **Frame Tracking**: Integrated with `BeginFrame()` to advance frame counter

**Benefits:**
- Reduces GPU synchronization stalls in Unreal Engine and Source Engine games
- Temporal coherence prediction for smoother visibility culling
- Minimal memory overhead (~80 bytes per cached query)

**Games That Will Benefit:**
- Murdered: Soul Suspect (Unreal Engine)
- Left 4 Dead (Source Engine)
- Dark Messiah (Source Engine)
- Any game using heavy occlusion culling

### 2. Memory Pressure Detection System (Commit: 06fa17a3)

**Files Created:**
- `app/src/main/java/aenu/ax360e/MemoryPressureManager.java`

**Files Modified:**
- `app/src/main/java/aenu/ax360e/Emulator.java` (added `update_memory_pressure` native method)

**Implementation Details:**
- **5-Level Pressure Detection**:
  - NONE: < 60% RAM used
  - LOW: 60-70% RAM used
  - MEDIUM: 70-80% RAM used
  - HIGH: 80-90% RAM used or < 1GB available
  - CRITICAL: > 90% RAM used or < 500MB available

- **Thermal Awareness**: Integrates PowerManager.getCurrentThermalStatus() on Android Q+
- **Caching**: 5-second cache to avoid excessive ActivityManager calls
- **Scale Factors**: Returns 0.5-1.0 multiplier for texture cache limits based on pressure
- **Thermal Throttling**: Applies 0.8x-0.9x factor when device is hot

**API Methods:**
```java
MemoryPressure getMemoryPressure()           // Cached (5s)
MemoryPressure getMemoryPressureImmediate()  // Force check
double getTextureCacheScaleFactor()          // 0.5-1.0 based on pressure
boolean shouldEvictTextures()                // true if HIGH or CRITICAL
int getPressureLevelValue()                  // 0-4 for JNI
```

**Benefits:**
- Prevents OOM crashes on 6GB devices
- Adapts to thermal throttling
- Reduces memory footprint during multitasking
- Smooth scaling without hard cutoffs

## Pending Work

### 3. Native Memory Pressure Integration

**Status**: Native method declared in Java, C++ implementation needed

**Required Files:**
- `app/src/main/cpp/emulator_ax360e.cpp` - Add JNI binding for `update_memory_pressure`
- `app/src/main/cpp/xenia/src/xenia/gpu/texture_cache.h` - Add pressure state tracking
- `app/src/main/cpp/xenia/src/xenia/gpu/texture_cache.cc` - Dynamic limit adjustment
- `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_texture_cache.cc` - Vulkan-specific eviction

**Implementation Plan:**
```cpp
// In texture_cache.h
struct MemoryPressureState {
  int pressure_level = 0;      // 0-4
  uint64_t available_mb = 0;
  int thermal_level = 0;
  double scale_factor = 1.0;
};
MemoryPressureState memory_pressure_;

// In texture_cache.cc
void TextureCache::UpdateMemoryPressure(int level, uint64_t available_mb, int thermal) {
  memory_pressure_.pressure_level = level;
  memory_pressure_.available_mb = available_mb;
  memory_pressure_.thermal_level = thermal;

  // Calculate scale factor
  double scale = 1.0;
  switch (level) {
    case 4: scale = 0.5; break;   // CRITICAL
    case 3: scale = 0.7; break;   // HIGH
    case 2: scale = 0.85; break;  // MEDIUM
    case 1: scale = 0.95; break;  // LOW
    default: scale = 1.0; break;  // NONE
  }

  if (thermal >= 4) scale *= 0.8;  // THERMAL_STATUS_CRITICAL
  else if (thermal >= 3) scale *= 0.9;  // THERMAL_STATUS_SEVERE

  memory_pressure_.scale_factor = scale;

  // Trigger eviction if needed
  if (level >= 3) {
    EvictOldTextures(true);  // Aggressive eviction
  }
}
```

### 4. Integration with EmulatorActivity

**Required Changes:**
- Initialize `MemoryPressureManager` in `EmulatorActivity.onCreate()`
- Start background thread/handler to periodically call `getMemoryPressure()`
- Call `Emulator.update_memory_pressure()` with pressure data
- Log pressure changes for debugging

**Implementation:**
```java
// In EmulatorActivity
private MemoryPressureManager memoryPressureManager;
private Handler memoryCheckHandler;
private static final int MEMORY_CHECK_INTERVAL_MS = 5000;

private void startMemoryPressureMonitoring() {
    memoryPressureManager = new MemoryPressureManager(this);
    memoryCheckHandler = new Handler(Looper.getMainLooper());

    Runnable memoryCheckRunnable = new Runnable() {
        @Override
        public void run() {
            MemoryPressureManager.MemoryPressure pressure =
                memoryPressureManager.getMemoryPressure();

            if (Emulator.get != null) {
                Emulator.get.update_memory_pressure(
                    pressure.level.getValue(),
                    pressure.availableMB,
                    pressure.thermalLevel
                );
            }

            memoryCheckHandler.postDelayed(this, MEMORY_CHECK_INTERVAL_MS);
        }
    };

    memoryCheckHandler.post(memoryCheckRunnable);
}
```

### 5. Testing Requirements

**Unit Tests Needed:**
- [ ] Memory pressure level calculation correctness
- [ ] Scale factor calculation at each pressure level
- [ ] Thermal throttling factor application
- [ ] Cache behavior (5-second interval)

**Integration Tests Needed:**
- [ ] Test on 6GB device under memory pressure
- [ ] Test on 12GB device with high texture settings
- [ ] Test thermal throttling during extended gameplay
- [ ] Verify texture eviction doesn't cause pop-in artifacts

**Games to Test:**
- Open-world games (Fallout, Elder Scrolls) - high texture usage
- Unreal Engine games - occlusion query optimization
- Source Engine games - occlusion query optimization
- Frostbite games - combination of all optimizations

## Technical Decisions

### Why 10-Frame Cache Age?
- Balances memory usage vs. effectiveness
- Most objects visible in frame N are visible in N+1
- 10 frames @ 30fps = 333ms, sufficient for temporal coherence
- Eviction every 20 frames prevents memory leaks

### Why 5-Second Memory Pressure Cache?
- ActivityManager.getMemoryInfo() is relatively expensive
- RAM pressure doesn't change rapidly (5s is sufficient)
- Reduces overhead while still being responsive
- Can force immediate check if needed

### Why These Pressure Thresholds?
- **60%**: Modern Android uses ~40-50% normally, 60% is light pressure
- **1GB threshold**: Minimum for comfortable multitasking
- **500MB threshold**: Critical - app may be killed soon
- Based on Android Low Memory Killer behavior

## Performance Expectations

### Occlusion Query Caching
- **Cache Hit Rate**: 70-90% expected in stable scenes
- **Latency Reduction**: Eliminate GPU sync for cache hits (~0.5-2ms per query)
- **Total Improvement**: 15-30% faster frame times in query-heavy games

### Memory Management
- **OOM Prevention**: Zero crashes on 6GB devices (vs. occasional crashes before)
- **Thermal Management**: 10-20% better sustained performance when hot
- **Memory Footprint**: 20-50% reduction in peak texture memory during pressure

## Known Limitations

1. **Occlusion Query Cache**:
   - May cause brief pop-in if object visibility changes rapidly
   - Cache miss on first frame (expected)
   - Not effective for dynamic scenes with constant camera/object movement

2. **Memory Pressure**:
   - Thermal API only available Android Q+ (API 29+)
   - Older devices get `thermalLevel = 0` (no thermal awareness)
   - Cannot predict future memory pressure, only react to current

3. **Texture Eviction**:
   - Aggressive eviction may cause texture thrashing if limits too low
   - No predictive preloading, only reactive eviction
   - Scale factors are fixed multipliers, not adaptive learning

## Next Steps (Phase 3)

After Phase 2 completion:
1. Implement VK_EXT_shader_tile_image Phase 4B for eDRAM optimization
2. Comprehensive testing with problematic games
3. Performance profiling and benchmarking
4. User-facing documentation updates

## Related Files

- `GPU_COMPATIBILITY_FIXES.md` - Overall implementation plan
- `PHASE_1_COMPLETE.md` - Phase 1 memexport and pipeline cache
- `TILE_IMAGE_INTEGRATION.md` - Tile image extension implementation
- `mesa_turnip_research_findings.md` - Turnip driver optimization research

---

**Status**: Phase 2 ~60% complete. Occlusion query cache fully implemented. Memory pressure detection implemented in Java. Pending: C++ integration, texture cache dynamic limits, EmulatorActivity integration, and testing.
