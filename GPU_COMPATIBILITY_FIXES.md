# GPU Compatibility Fixes Implementation Plan

**Created**: 2026-04-14
**Target**: High-impact compatibility improvements for Xbox 360 emulation on Android

---

## Overview

This document outlines the implementation strategy for fixing the five highest-impact compatibility issues identified in the Xbox 360 hardware vs. Android/Vulkan emulation analysis:

1. **eDRAM Bandwidth and Access Patterns** (Highest Impact)
2. **Memexport Operations** (Blocks Specific Engines)
3. **Shader Compilation Overhead** (User Experience Issue)
4. **Occlusion Query Synchronization** (Engine-Specific)
5. **Texture Format and Memory Management** (Stability Issue)

---

## 1. eDRAM Bandwidth and Access Patterns

### Problem Statement

**Xbox 360 Hardware**:
- 10MB dedicated eDRAM with 256 GB/s bandwidth
- Zero-latency framebuffer operations
- Hardware-accelerated resolve, blending, Z-test

**Current Emulation**:
- Storage buffer or Fragment Shader Interlock (FSI)
- Main memory bandwidth: 40-80 GB/s (3-5x slower)
- Software emulation of all operations

**Impact**:
- Performance bottlenecks in complex scenes
- Corruption in Frostbite/MT Framework games
- 40% performance penalty with FSI mode

### Solution: VK_EXT_shader_tile_image Integration (Phase 4B)

**Status**: Infrastructure completed (Phase 4A), shader implementation pending

**Implementation**:

#### Step 1: Enable Tile Images in Pipeline Cache
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_pipeline_cache.cc`

Add detection and enablement:
```cpp
// In VulkanPipelineCache::Initialize()
if (shader_translator_->CanUseTileImagesForEdram()) {
  const auto& device_props = command_processor_.GetVulkanDevice()->properties();

  // Check if running on Turnip driver (not stock Qualcomm)
  bool is_turnip = std::string(device_props.deviceName).find("Turnip") != std::string::npos;

  // Check Adreno generation (7xx/8xx support tile images)
  bool is_adreno_gen7_plus = device_props.vendorID == 0x5143 && // Qualcomm
                             device_props.deviceID >= 0x07000000;

  if (is_turnip && is_adreno_gen7_plus) {
    shader_translator_->set_edram_tile_image_enabled(true);
    XELOGI("VulkanPipelineCache: Enabled tile images for eDRAM optimization");
  }
}
```

#### Step 2: Implement Tile Image Shader Paths
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/spirv_shader_translator.cc`

**Location**: `StartFragmentShaderBeforeMain()` (~line 1784)

Add input attachment declarations:
```cpp
if (edram_fragment_shader_interlock_ && edram_tile_image_enabled_) {
  builder_->addExtension("SPV_EXT_shader_tile_image");
  builder_->addCapability(spv::CapabilityTileImageColorReadAccessEXT);

  // Declare input attachments for reading eDRAM
  spv::Id type_subpass_data = builder_->makeImageType(
      type_float4_, spv::DimSubpassData, false, false, false, 2,
      spv::ImageFormatUnknown);

  for (uint32_t i = 0; i < xenos::kMaxColorRenderTargets; ++i) {
    spv::Id input_attachment = builder_->createVariable(
        spv::NoPrecision, spv::StorageClassInput, type_subpass_data,
        fmt::format("xe_edram_input_attachment_{}", i).c_str());

    builder_->addDecoration(input_attachment, spv::DecorationInputAttachmentIndex, i);
    builder_->addDecoration(input_attachment, spv::DecorationDescriptorSet,
                           kDescriptorSetEdramAndSharedMemory);
    builder_->addDecoration(input_attachment, spv::DecorationBinding,
                           kEdramBindingStorageBuffer + 1 + i);

    fsi_input_attachments_[i] = input_attachment;
  }
}
```

Add color read function:
```cpp
spv::Id SpirvShaderTranslator::FSI_LoadColorFromTileImage(
    uint32_t rt_index, spv::Id sample_index) {
  assert_true(edram_tile_image_enabled_);
  assert_true(rt_index < xenos::kMaxColorRenderTargets);

  spv::Id input_attachment = fsi_input_attachments_[rt_index];

  if (sample_index != spv::NoResult) {
    // MSAA: read specific sample
    std::vector<spv::Id> operands = {sample_index};
    return builder_->createOp(spv::OpImageRead, type_float4_,
                             {input_attachment, operands});
  } else {
    // Non-MSAA: simple tile image read
    return builder_->createUnaryOp(spv::OpColorAttachmentReadEXT,
                                  type_float4_, input_attachment);
  }
}
```

Modify all eDRAM color load sites to use tile images when enabled.

#### Step 3: Update Render Pass Creation
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_render_target_cache.cc`

Mark attachments for input:
```cpp
if (tile_image_enabled) {
  attachment_desc.flags |= VK_ATTACHMENT_DESCRIPTION_MAY_ALIAS_BIT;
  attachment_desc.loadOp = VK_ATTACHMENT_LOAD_OP_LOAD; // Keep loaded for reading
}

// Add input attachment references
VkAttachmentReference input_attachments[xenos::kMaxColorRenderTargets];
for (uint32_t i = 0; i < color_rt_count; ++i) {
  input_attachments[i].attachment = i;
  input_attachments[i].layout = VK_IMAGE_LAYOUT_GENERAL;
}

subpass_desc.inputAttachmentCount = color_rt_count;
subpass_desc.pInputAttachments = input_attachments;
```

**Expected Benefit**: 15-25% FPS improvement, reduced corruption in complex scenes

**Risk**: Medium - Requires extensive testing on real Turnip hardware

**Testing**: See `TILE_IMAGE_INTEGRATION.md` for comprehensive test plan

---

## 2. Memexport Operations

### Problem Statement

**Xbox 360 Feature**:
- Shaders can write directly to main memory
- Used for: GPU compute, physics, deferred rendering
- Zero overhead native hardware support

**Current Status**:
- Basic emulation exists but disabled by default
- Config flag: `GPU|readback_memexport`
- Incomplete testing with Frostbite/MT Framework games

**Impact**:
- Battlefield: Bad Company (Frostbite) - hangs waiting for memexport
- Dragon's Dogma (MT Framework) - readback corruption
- Source Engine - HDR effects broken

### Solution: Enable and Validate Memexport

#### Step 1: Enable Memexport Flag by Default
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/render_target_cache.cc`

Change default value:
```cpp
DEFINE_bool(readback_memexport, true,  // Changed from false
            "Read back memexported data to system memory for emulation accuracy.\n"
            "Required for Frostbite and MT Framework games.",
            "GPU");
```

#### Step 2: Add Memexport Diagnostics
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_command_processor.cc`

Add logging for memexport operations:
```cpp
void VulkanCommandProcessor::HandleMemexport(
    const std::vector<draw_util::MemExportRange>& ranges) {
  if (ranges.empty()) return;

  XELOGI("Memexport: {} ranges totaling {} bytes",
         ranges.size(),
         std::accumulate(ranges.begin(), ranges.end(), 0u,
                        [](uint32_t sum, const auto& r) { return sum + r.size_bytes; }));

  for (const auto& range : ranges) {
    XELOGI("  Range: base=0x{:08X}, size={} bytes",
           range.base_address_dwords * 4, range.size_bytes);
  }

  // Existing memexport handling code...
}
```

#### Step 3: Async Memexport via Compute Queue
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_command_processor.cc`

Use async compute queue to reduce blocking:
```cpp
// Check if async compute queue is available
if (GetVulkanDevice()->queue_family_compute_index() != UINT32_MAX) {
  // Submit memexport operations to compute queue
  // This prevents blocking the graphics pipeline
  VkCommandBuffer compute_cmd = AcquireComputeCommandBuffer();

  // Copy memexport results using compute shader
  // Signal semaphore when complete
  // Graphics queue waits on semaphore before reading results
}
```

**Expected Benefit**: Unblock Frostbite and MT Framework games

**Risk**: Low - Code exists, just needs enablement and testing

**Testing Games**:
- Battlefield: Bad Company 2
- Need for Speed: The Run
- Dragon's Dogma
- Resident Evil 5

---

## 3. Shader Compilation Overhead

### Problem Statement

**Issue**: Games encounter new pipeline combinations constantly, causing stuttering

**Current State**:
- VkPipelineCache is created and used
- Cache is saved to disk on shutdown
- Cache path: `{cache_root}/vulkan_pipelines/{title_id:08X}.cache`

**Impact**:
- Unreal Engine games: noticeable hitching during gameplay
- First-time runs: severe stuttering until cache warms up
- User frustration due to inconsistent performance

### Solution: Verify and Optimize Pipeline Caching

#### Step 1: Verify Cache Persistence
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_pipeline_cache.cc`

**Current Implementation**: Lines 165-254

```cpp
void VulkanPipelineCache::InitializePipelineCache(const std::filesystem::path& cache_root,
                                                   uint32_t title_id) {
  auto pipeline_cache_dir = cache_root / "vulkan_pipelines";
  std::filesystem::create_directories(pipeline_cache_dir);

  pipeline_cache_path_ = pipeline_cache_dir / fmt::format("{:08X}.cache", title_id);
  pipeline_cache_title_id_ = title_id;

  // Load existing cache
  FILE* cache_file = xe::filesystem::OpenFile(pipeline_cache_path_, "rb");
  // ... read cache data ...

  // Create VkPipelineCache with loaded data
  VkPipelineCacheCreateInfo cache_create_info = {};
  cache_create_info.initialDataSize = cache_size;
  cache_create_info.pInitialData = cache_data;

  vkCreatePipelineCache(device, &cache_create_info, nullptr, &vk_pipeline_cache_);
}
```

**Verification Needed**:
1. Check that cache is actually being loaded on startup
2. Verify cache is saved on shutdown
3. Ensure cache directory has write permissions

#### Step 2: Add Cache Statistics
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_pipeline_cache.cc`

Add metrics:
```cpp
struct PipelineCacheStats {
  size_t pipelines_created = 0;
  size_t pipelines_cached = 0;
  size_t cache_hits = 0;
  size_t cache_misses = 0;
  double avg_compile_time_ms = 0.0;
};

PipelineCacheStats stats_;

// In ConfigurePipeline():
auto start_time = std::chrono::high_resolution_clock::now();
VkResult result = vkCreateGraphicsPipelines(device, vk_pipeline_cache_, 1, &pipeline_create_info, nullptr, &pipeline);
auto end_time = std::chrono::high_resolution_clock::now();

double compile_time = std::chrono::duration<double, std::milli>(end_time - start_time).count();
stats_.avg_compile_time_ms = (stats_.avg_compile_time_ms * stats_.pipelines_created + compile_time) / (stats_.pipelines_created + 1);
stats_.pipelines_created++;

if (compile_time < 5.0) {
  stats_.cache_hits++;
  XELOGI("Pipeline compiled from cache in {:.2f}ms", compile_time);
} else {
  stats_.cache_misses++;
  XELOGI("Pipeline compiled fresh in {:.2f}ms", compile_time);
}
```

#### Step 3: Periodic Cache Flush
Save cache periodically, not just on shutdown:
```cpp
// In VulkanPipelineCache
std::chrono::steady_clock::time_point last_cache_save_;
constexpr auto kCacheSaveInterval = std::chrono::minutes(5);

void VulkanPipelineCache::MaybeFlushCache() {
  auto now = std::chrono::steady_clock::now();
  if (now - last_cache_save_ >= kCacheSaveInterval) {
    ShutdownPipelineCache();  // Saves cache
    last_cache_save_ = now;
    XELOGI("Pipeline cache auto-saved ({} KB)", GetCacheSize() / 1024);
  }
}
```

**Expected Benefit**: Eliminate stutter after first run, improve user experience

**Risk**: Very Low - System already implemented, just needs verification

---

## 4. Occlusion Query Synchronization

### Problem Statement

**Issue**: Unreal Engine and Source Engine use heavy occlusion queries for culling

**Current State**:
- Configurable thresholds exist:
  - `query_occlusion_sample_lower_threshold` = 80
  - `query_occlusion_sample_upper_threshold` = 100
- Each query causes GPU sync overhead
- Results cause objects to pop in/out if timing is wrong

**Impact**:
- Murdered: Soul Suspect (Unreal) - hitching during camera movement
- Left 4 Dead (Source) - performance drops in complex areas
- Object pop-in artifacts

### Solution: Query Result Prediction and Caching

#### Step 1: Implement Query Result Cache
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_command_processor.h`

Add cache structure:
```cpp
struct OcclusionQueryCache {
  struct Entry {
    uint64_t last_frame_tested;
    uint32_t last_result;
    bool result_valid;
  };

  std::unordered_map<uint32_t, Entry> cache;
  uint64_t current_frame = 0;

  static constexpr uint32_t kMaxCacheAge = 10; // frames

  bool TryGetCachedResult(uint32_t query_address, uint32_t& result_out) {
    auto it = cache.find(query_address);
    if (it != cache.end() && it->second.result_valid &&
        (current_frame - it->second.last_frame_tested) < kMaxCacheAge) {
      result_out = it->second.last_result;
      return true;
    }
    return false;
  }

  void StoreResult(uint32_t query_address, uint32_t result) {
    cache[query_address] = {current_frame, result, true};
  }

  void AdvanceFrame() {
    current_frame++;
    // Evict old entries
    for (auto it = cache.begin(); it != cache.end();) {
      if (current_frame - it->second.last_frame_tested > kMaxCacheAge * 2) {
        it = cache.erase(it);
      } else {
        ++it;
      }
    }
  }
};

OcclusionQueryCache occlusion_query_cache_;
```

#### Step 2: Use Cache in Query Processing
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_command_processor.cc`

```cpp
void VulkanCommandProcessor::ProcessOcclusionQuery(uint32_t query_address) {
  uint32_t cached_result;
  if (occlusion_query_cache_.TryGetCachedResult(query_address, cached_result)) {
    // Use cached result, skip GPU query
    WriteQueryResult(query_address, cached_result);
    return;
  }

  // Execute actual GPU query
  // ...

  // Cache the result
  occlusion_query_cache_.StoreResult(query_address, actual_result);
}
```

#### Step 3: Temporal Coherence Prediction
```cpp
// Predict query result based on temporal coherence
uint32_t PredictQueryResult(uint32_t query_address) {
  auto it = occlusion_query_cache_.cache.find(query_address);
  if (it != occlusion_query_cache_.cache.end()) {
    // Object visible last frame? Likely visible this frame
    return it->second.last_result;
  }
  // Conservative: assume visible
  return query_occlusion_sample_upper_threshold;
}
```

**Expected Benefit**: Reduce GPU sync overhead, smoother frame times

**Risk**: Medium - May cause incorrect culling if prediction is wrong

**Fallback**: Make cache aggressive ness configurable

---

## 5. Texture Format and Memory Management

### Problem Statement

**Issue**: Mobile devices have limited RAM, games can exceed texture budget

**Current State**:
- Configurable limits:
  - Soft limit: 384MB - 3GB
  - Hard limit: 512MB - 4GB
- No dynamic adjustment
- No thermal awareness

**Impact**:
- Out-of-memory crashes on 6GB devices
- Thermal throttling not accounted for
- Texture thrashing when approaching limit

### Solution: Dynamic Texture Memory Management

#### Step 1: Implement Memory Pressure Detection
**File**: `app/src/main/java/aenu/ax360e/TurnipEnvManager.java`

Add memory monitoring:
```java
public class MemoryManager {
    private static final long CHECK_INTERVAL_MS = 5000; // 5 seconds

    public static class MemoryPressure {
        public enum Level { NONE, LOW, MEDIUM, HIGH, CRITICAL }

        public Level level;
        public long available_mb;
        public long total_mb;
        public int thermal_level; // 0-6, from ThermalManager
    }

    public MemoryPressure getMemoryPressure() {
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);

        long available_mb = memInfo.availMem / (1024 * 1024);
        long total_mb = memInfo.totalMem / (1024 * 1024);
        double pressure_ratio = 1.0 - (double)memInfo.availMem / memInfo.totalMem;

        MemoryPressure result = new MemoryPressure();
        result.available_mb = available_mb;
        result.total_mb = total_mb;
        result.thermal_level = getThermalLevel();

        if (pressure_ratio > 0.9 || available_mb < 500) {
            result.level = MemoryPressure.Level.CRITICAL;
        } else if (pressure_ratio > 0.8 || available_mb < 1000) {
            result.level = MemoryPressure.Level.HIGH;
        } else if (pressure_ratio > 0.7) {
            result.level = MemoryPressure.Level.MEDIUM;
        } else if (pressure_ratio > 0.6) {
            result.level = MemoryPressure.Level.LOW;
        } else {
            result.level = MemoryPressure.Level.NONE;
        }

        return result;
    }

    private int getThermalLevel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm.getCurrentThermalStatus();
        }
        return 0;
    }
}
```

#### Step 2: Dynamic Texture Cache Limits
**File**: `app/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_texture_cache.cc`

Add adaptive limits:
```cpp
void VulkanTextureCache::UpdateMemoryLimits() {
  // Called periodically from main loop
  MemoryPressure pressure = GetMemoryPressure(); // From Java via JNI

  uint64_t base_soft_limit = cvars::texture_cache_memory_limit_soft * 1024 * 1024;
  uint64_t base_hard_limit = cvars::texture_cache_memory_limit_hard * 1024 * 1024;

  double scale_factor = 1.0;

  switch (pressure.level) {
    case MemoryPressure::CRITICAL:
      scale_factor = 0.5; // Reduce to 50%
      break;
    case MemoryPressure::HIGH:
      scale_factor = 0.7; // Reduce to 70%
      break;
    case MemoryPressure::MEDIUM:
      scale_factor = 0.85;
      break;
    case MemoryPressure::LOW:
      scale_factor = 0.95;
      break;
    default:
      scale_factor = 1.0;
  }

  // Apply thermal throttling factor
  if (pressure.thermal_level >= 4) { // THERMAL_STATUS_SEVERE
    scale_factor *= 0.8;
  }

  current_soft_limit_ = static_cast<uint64_t>(base_soft_limit * scale_factor);
  current_hard_limit_ = static_cast<uint64_t>(base_hard_limit * scale_factor);

  XELOGI("Texture cache limits: soft={}MB, hard={}MB (pressure={}, thermal={})",
         current_soft_limit_ / (1024 * 1024),
         current_hard_limit_ / (1024 * 1024),
         static_cast<int>(pressure.level),
         pressure.thermal_level);

  // Trigger aggressive eviction if needed
  if (pressure.level >= MemoryPressure::HIGH) {
    EvictTexturesToLimit(current_soft_limit_);
  }
}
```

#### Step 3: Aggressive Texture Eviction
```cpp
void VulkanTextureCache::EvictTexturesToLimit(uint64_t target_bytes) {
  // Sort textures by last access time
  std::vector<Texture*> eviction_candidates;
  uint64_t current_usage = CalculateTotalMemoryUsage();

  if (current_usage <= target_bytes) return;

  uint64_t bytes_to_free = current_usage - target_bytes;
  uint64_t bytes_freed = 0;

  // Collect candidates (LRU order)
  for (auto& [key, texture] : textures_) {
    if (!texture->in_use) {
      eviction_candidates.push_back(texture.get());
    }
  }

  std::sort(eviction_candidates.begin(), eviction_candidates.end(),
            [](const Texture* a, const Texture* b) {
              return a->last_access_frame < b->last_access_frame;
            });

  // Evict oldest textures first
  for (Texture* texture : eviction_candidates) {
    if (bytes_freed >= bytes_to_free) break;

    bytes_freed += texture->memory_size;
    EvictTexture(texture);
  }

  XELOGI("Evicted {} textures, freed {} MB",
         eviction_candidates.size(),
         bytes_freed / (1024 * 1024));
}
```

**Expected Benefit**: Prevent OOM crashes, better thermal performance

**Risk**: Low - Conservative eviction policy

---

## Implementation Priority and Timeline

### Phase 1: Immediate (Week 1)
1. ✅ **Documentation** - Create this comprehensive plan
2. **Memexport** - Enable flag, add diagnostics, test with 3 games
3. **Pipeline Cache** - Verify persistence, add statistics logging

### Phase 2: Short Term (Weeks 2-3)
4. **Occlusion Queries** - Implement result cache and prediction
5. **Texture Memory** - Add memory pressure detection, dynamic limits

### Phase 3: Medium Term (Weeks 4-6)
6. **Tile Images Phase 4B** - Complete shader implementation
7. **Testing** - Comprehensive testing with problematic games

### Phase 4: Validation (Weeks 7-8)
8. **Performance Profiling** - Measure improvements
9. **Regression Testing** - Ensure no new issues introduced
10. **Documentation** - Update user-facing docs with new features

---

## Testing Strategy

### Test Games by Issue
- **eDRAM/Tile Images**: Battlefield Bad Company, Mirror's Edge (Frostbite)
- **Memexport**: Need for Speed: The Run, Dragon's Dogma
- **Shader Cache**: Murdered: Soul Suspect (Unreal Engine)
- **Occlusion Queries**: Left 4 Dead, Dark Messiah (Source)
- **Texture Memory**: Open-world games (Fallout, Elder Scrolls)

### Hardware Coverage
- **Adreno 710/720**: Test GMEM optimization
- **Adreno 830**: Verify sysmem workaround still needed
- **Adreno 840**: Test tile image extension
- **6GB RAM devices**: Test memory pressure handling
- **12GB RAM devices**: Test with high texture limits

### Metrics to Track
- Frame times (min/avg/max/99th percentile)
- Pipeline compilation stutter frequency
- Texture cache hit rate
- Memory usage over time
- Thermal throttling events
- GPU utilization percentage

---

## Risk Mitigation

### High Risk Items
1. **Tile Images (Phase 4B)**: Requires real Adreno 840 hardware for testing
   - **Mitigation**: Implement feature flags, thorough validation layers

2. **Memexport Changes**: May introduce new game-breaking bugs
   - **Mitigation**: Add extensive logging, easy rollback flag

### Medium Risk Items
3. **Query Prediction**: May cause incorrect culling
   - **Mitigation**: Conservative defaults, configurable aggressiveness

4. **Dynamic Memory Limits**: May cause texture pop-in
   - **Mitigation**: Smooth transitions, minimum guaranteed limits

### Low Risk Items
5. **Pipeline Cache Stats**: Pure instrumentation
6. **Memory Pressure Detection**: Read-only monitoring

---

## Success Criteria

### Quantitative
- ✅ 15-25% FPS improvement in Frostbite games (tile images)
- ✅ 90% reduction in shader stutter after cache warm-up
- ✅ 50% reduction in occlusion query stalls (Unreal/Source)
- ✅ Zero OOM crashes on 6GB devices over 1-hour sessions
- ✅ Memexport-dependent games become playable

### Qualitative
- ✅ Smoother gameplay experience (user feedback)
- ✅ Faster load times after first run
- ✅ Better thermal management
- ✅ Reduced corruption in complex scenes

---

## Related Documentation

- `TILE_IMAGE_INTEGRATION.md` - Detailed tile image implementation guide
- `mesa_turnip_research_findings.md` - Turnip driver optimization research
- `game_engine_compatibility.md` - Engine-specific compatibility patterns
- `ANDROID_TURNIP_OPTIMIZATION_GUIDE.md` - Turnip configuration guide

---

## Notes for Future Work

### Longer-Term Optimizations
1. **VK_EXT_graphics_pipeline_library** - Modular pipeline compilation
2. **Async Compute for Memexport** - Parallel GPU work queues
3. **GPU Texture Untiling** - Offload work from CPU
4. **Hardware Tessellation** - For specific games using patches
5. **Conditional Rendering** - Hardware-accelerated query usage

### Research Needed
1. Adreno 850 tile image support (next generation)
2. Vulkan 1.4 features for optimization
3. Ray tracing extension usage (if available)

---

## Appendix: Configuration Flags

### New/Modified Flags
- `GPU|readback_memexport` - Default changed to `true`
- `GPU|enable_tile_images` - Auto-detect Turnip + Adreno 7xx/8xx
- `GPU|occlusion_query_cache_aggressive` - Query prediction aggressiveness (0-100)
- `GPU|texture_memory_adaptive` - Enable dynamic memory limits

### Existing Flags (No Changes)
- `GPU|query_occlusion_sample_lower_threshold` = 80
- `GPU|query_occlusion_sample_upper_threshold` = 100
- `GPU|texture_cache_memory_limit_soft` = 384-3072 MB
- `GPU|texture_cache_memory_limit_hard` = 512-4096 MB
- `GPU|store_shaders` = true (already enabled)

---

**Document Status**: ✅ Ready for Implementation
**Last Updated**: 2026-04-14
**Next Review**: After Phase 1 completion
