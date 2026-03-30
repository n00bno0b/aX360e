# Mesa Turnip Driver Research: Solutions for Game Engine Compatibility Issues

**Research Date:** March 30, 2026
**Project:** aX360e (Xenia Android Port)
**Focus:** Addressing game engine-specific compatibility issues through Mesa Turnip driver optimization

---

## Executive Summary

This document consolidates research findings on the Mesa Turnip driver development and its application to solving Xbox 360 emulation challenges in aX360e, particularly the game engine compatibility issues documented in `game_engine_compatibility.md`. The research identifies specific technical solutions for occlusion query problems, shader compilation stutter, GPU readback issues, and TBDR optimization on Adreno 840 hardware.

---

## 1. Mesa Turnip Driver: Current State (2024-2026)

### 1.1 Team and Development

**Mesa3D Freedreno/Turnip Project:**
- Open-source Vulkan driver for Qualcomm Adreno GPUs (6xx, 7xx, 8xx series)
- Volunteer-driven development with rapid iteration cycles
- Also known as "Lemonade" (rebranded in 2025)
- Part of the broader freedreno project family

**Key Milestones:**
- **January 2025**: Mesa 25.0 released with full Vulkan 1.3 conformance on Adreno 750
- **January 2025**: VK_KHR_ray_query support added for Adreno 740+
- **Late 2025/Early 2026**: Mesa 26.0 with Adreno Gen 8 (830/840) support
- **March 2026**: Ongoing optimization for Snapdragon 8 Elite emulation workloads

**Community Resources:**
- GitHub: Mesa project repository
- Releases: Regular pre-built binaries available
- Forums: AndroGaming.com, various Discord communities
- Technical docs: docs.mesa3d.org/drivers/freedreno.html

### 1.2 Advantages Over Stock Qualcomm Drivers

**For Emulation:**
1. **Better Extension Support**: Full implementation of VK_EXT_fragment_shader_interlock, VK_EXT_shader_tile_image
2. **Faster Bug Fixes**: Community-driven development responds quickly to emulator issues
3. **Advanced Feature Support**: Descriptor indexing, timeline semaphores, dynamic rendering
4. **No Vendor Restrictions**: Open implementation follows Vulkan spec strictly

**Performance Characteristics:**
- Improved frame pacing for emulators (GameHub, Eden, Winlator, RPCSX-UI)
- Better handling of complex shader programs
- Reduced graphical corruption in demanding workloads
- Sometimes outperforms proprietary drivers for specific emulator use cases

### 1.3 Adreno 840 TBDR Architecture Support

**Tile-Based Deferred Rendering Optimizations:**
- Native binning pass support
- GMEM (Graphics Memory) management optimized for on-chip tile processing
- Low Resolution Z-buffer (LRZ) integration
- Efficient tile DMA and memory transition handling

**Critical for Xenia:**
- Reduces GMEM thrashing when emulating Xbox 360's eDRAM
- Supports VK_DEPENDENCY_BY_REGION_BIT for localized synchronization
- Enables tile-aware rendering paths that keep data on-chip

---

## 2. Vulkan Extensions for Mobile Optimization

### 2.1 VK_EXT_shader_tile_image

**Purpose:**
- Allows fragment shaders to read color/depth/stencil data directly from the current tile
- Only works with VK_KHR_dynamic_rendering
- Critical for programmable blending and deferred shading on tile-based GPUs

**Benefits for Xenia:**
- Read back eDRAM data without flushing GMEM to system RAM
- Eliminates expensive round-trip to main memory
- Dramatically reduces memory bandwidth for post-processing effects

**Implementation Requirements:**
1. Check extension availability during device creation
2. Enable extension when creating logical device
3. Use dynamic rendering instead of traditional render passes
4. Access tile attachments through special shader variables

**Status in Turnip:**
- Supported in Mesa 26.0+ for Adreno 7xx and 8xx
- Well-tested with emulator workloads
- Better implementation than stock Qualcomm driver

### 2.2 VK_QCOM_tile_shading

**Purpose:**
- Qualcomm-specific extension for explicit tile-based rendering control
- Forces TBDR mode (disables FlexRender auto-selection)
- Provides per-tile dispatch and tile-aware shader access

**Key Features:**
1. **Explicit Tile Control**: Direct management of tile size and processing
2. **Tile Variables**: Shader access to attachment data within tile boundaries
3. **Visibility Pass**: Hardware-assisted primitive binning and culling
4. **GMEM Optimization**: Maximum on-chip memory utilization

**Benefits for Xenia:**
- Fine-grained control over how Xbox 360's tiled rendering is emulated
- Can match Xbox 360's eDRAM tiling behavior more precisely
- Reduces bandwidth by keeping render targets in GMEM across multiple operations

**Implementation Approach:**
- Use for games that heavily utilized Xbox 360's tiling (Frostbite, id Tech engines)
- Combine with VK_EXT_shader_tile_image for maximum efficiency
- Requires restructuring render passes to be tile-aware

### 2.3 VK_EXT_fragment_shader_interlock

**Purpose:**
- Ensures fragments affecting the same pixel are processed in defined order
- Critical for emulating Xbox 360's eDRAM blending and depth operations
- Prevents race conditions in programmable blending

**Xenia's Current Usage:**
- Already implemented in Xenia's Vulkan render target cache
- Required for accurate emulation of complex blending modes
- Causes shader compilation overhead (addressed below)

**Optimization Strategies:**
1. **Aggressive Shader Caching**: Store compiled pipelines persistently
2. **Selective Use**: Only enable for render passes that truly need it
3. **Cache Warming**: Pre-compile shaders during game load or in background
4. **Driver-Level Optimizations**: Turnip handles interlock more efficiently than stock drivers

---

## 3. Solving Engine-Specific Issues

### 3.1 Unreal Engine: Occlusion Queries and Shader Stutter

**Problem Analysis:**
- Unreal Engine 3 on Xbox 360 used aggressive hardware occlusion queries for visibility culling
- Xenia must translate these to Vulkan queries, introducing synchronization overhead
- Shader compilation stutter occurs when new visual effects trigger pipeline creation

**Research Findings:**

**Occlusion Query Emulation:**
- Xbox 360's Xenos GPU had dedicated occlusion query hardware
- Queries return sample counts from depth buffer tests
- Used for bounding box visibility checks on Actors

**Xenia's Implementation:**
- Maps Xbox 360 queries to Vulkan occlusion queries
- Challenges: Timing differences, eDRAM integration, result readback latency
- Can cause objects to pop in/out if emulation timing is imperfect

**Solutions:**

1. **Query Threshold Tuning** (Immediate):
   ```toml
   [GPU]
   query_occlusion_sample_lower_threshold = 80
   query_occlusion_sample_upper_threshold = 100
   ```
   - Adjust thresholds to match Unreal Engine's expectations
   - Lower threshold = more conservative culling (fewer pop-ins)
   - Higher threshold = more aggressive culling (better performance)

2. **Shader Cache Management** (High Priority):
   - Implement persistent pipeline cache with Vulkan's VkPipelineCache
   - Save cache to disk after every session
   - Load cache during startup to eliminate first-run stutter
   - Share community shader caches for popular games

3. **Pipeline Pre-compilation** (Future Work):
   - Analyze game's shader bytecode during load screen
   - Compile all likely pipelines in background thread
   - Use VK_EXT_graphics_pipeline_library for modular compilation
   - Reduces hitching during gameplay

4. **Turnip-Specific Optimizations**:
   - Mesa 26.0+ has faster pipeline compilation than stock drivers
   - Better handling of interlock-enabled shaders
   - Reduced driver overhead for query operations

### 3.2 Frostbite and MT Framework: GPU Readback and eDRAM

**Problem Analysis:**
- Both engines use advanced GPU techniques: memexport, GPGPU compute, render-to-vertex-buffer
- Heavy reliance on Xbox 360's 10MB eDRAM for high-bandwidth framebuffer operations
- Games tile their rendering to fit within eDRAM constraints

**Research Findings:**

**Xbox 360 Memexport:**
- Unique feature allowing shaders to write directly to main memory
- Used for physics, deferred rendering, post-processing
- CPU can read back GPU-computed data

**Xenia's eDRAM Emulation:**
- Two modes: RTV (fast, less accurate) and ROV (slow, pixel-perfect)
- ROV uses Rasterizer-Ordered Views for race-free blending
- Tiling emulated through render target cache system

**Solutions:**

1. **Enable Memexport Readback** (Configuration):
   ```toml
   [D3D12]  # Also applies to Vulkan backend conceptually
   d3d12_readback_memexport = true
   d3d12_readback_resolve = true
   ```

2. **GMEM-Aware Rendering** (Code Changes):
   - Implement VK_EXT_shader_tile_image support
   - Read attachment data within tile instead of flushing to RAM
   - Use VK_DEPENDENCY_BY_REGION_BIT in subpass dependencies
   - Keep eDRAM emulation buffer in GMEM as long as possible

3. **Render Pass Restructuring**:
   - Combine multiple Xbox 360 render passes into single Vulkan render pass
   - Use subpasses for dependent operations
   - Minimize vkCmdEndRenderPass calls (each forces GMEM flush)
   - Example: Deferred rendering G-buffer fill + lighting in same pass

4. **Turnip Driver Benefits**:
   - Better tile memory management than stock drivers
   - More accurate VK_QCOM_tile_shading implementation
   - Handles complex render pass dependencies correctly

### 3.3 Source Engine: Combined Occlusion and Readback Issues

**Problem Analysis:**
- Source engine combines both occlusion queries AND GPU readback for HDR/post-processing
- Double whammy of synchronization overhead

**Solutions:**
- Apply both occlusion query optimizations (section 3.1) and readback optimizations (section 3.2)
- Prioritize shader caching for Source engine games
- Consider per-engine configuration profiles

### 3.4 Unity and Gamebryo: CPU Instruction Coverage

**Problem Analysis:**
- These issues are CPU emulation, not GPU, so Turnip driver doesn't directly help
- However, reducing GPU overhead can improve overall performance

**Indirect Benefits:**
- Faster GPU emulation leaves more CPU time for instruction emulation
- Better frame pacing makes timing-dependent CPU code more stable

---

## 4. Implementation Roadmap for aX360e

### Phase 1: Detection and Configuration (Immediate)

**1. Turnip Driver Detection:**
```cpp
// In VulkanGraphicsSystem::Setup()
bool is_turnip_driver = false;
bool is_adreno_gpu = false;

VkPhysicalDeviceProperties properties;
vkGetPhysicalDeviceProperties(physical_device_, &properties);

if (properties.vendorID == 0x5143) {  // Qualcomm
    is_adreno_gpu = true;
    XELOGI("Detected Qualcomm Adreno GPU (ID: 0x%X)", properties.deviceID);
}

// Check device name for Turnip/Mesa indicators
const char* device_name = properties.deviceName;
if (strstr(device_name, "Turnip") || strstr(device_name, "Mesa") ||
    strstr(device_name, "freedreno")) {
    is_turnip_driver = true;
    XELOGI("Detected Mesa Turnip driver: %s", device_name);
}

// Store flags for optimization paths
config_.use_turnip_optimizations = is_adreno_gpu && is_turnip_driver;
```

**2. Extension Availability Checks:**
```cpp
// Check for tile image support
std::vector<VkExtensionProperties> available_extensions;
// ... populate available_extensions ...

bool has_shader_tile_image = false;
bool has_qcom_tile_shading = false;

for (const auto& ext : available_extensions) {
    if (strcmp(ext.extensionName, "VK_EXT_shader_tile_image") == 0) {
        has_shader_tile_image = true;
        enabled_extensions.push_back("VK_EXT_shader_tile_image");
        XELOGI("Enabling VK_EXT_shader_tile_image for GMEM optimization");
    }
    else if (strcmp(ext.extensionName, "VK_QCOM_tile_shading") == 0) {
        has_qcom_tile_shading = true;
        enabled_extensions.push_back("VK_QCOM_tile_shading");
        XELOGI("Enabling VK_QCOM_tile_shading for explicit tile control");
    }
}

config_.supports_tile_image = has_shader_tile_image;
config_.supports_tile_shading = has_qcom_tile_shading;
```

### Phase 2: Render Pass Optimization (High Priority)

**1. Subpass Dependency Improvements:**
```cpp
// In vulkan_render_target_cache.cc
VkSubpassDependency dependencies[2];

// External to Subpass 0: Use BY_REGION for tile locality
dependencies[0].srcSubpass = VK_SUBPASS_EXTERNAL;
dependencies[0].dstSubpass = 0;
dependencies[0].srcStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                               VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
dependencies[0].dstStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                               VK_PIPELINE_STAGE_COLOR_ATTACHMENT_READ_BIT;
dependencies[0].srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT |
                                VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
dependencies[0].dstAccessMask = VK_ACCESS_SHADER_READ_BIT |
                                VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;

// CRITICAL: VK_DEPENDENCY_BY_REGION_BIT prevents GMEM flush
if (config_.use_turnip_optimizations || is_mobile_gpu) {
    dependencies[0].dependencyFlags = VK_DEPENDENCY_BY_REGION_BIT;
} else {
    dependencies[0].dependencyFlags = 0;
}

// Subpass 0 to External
dependencies[1].srcSubpass = 0;
dependencies[1].dstSubpass = VK_SUBPASS_EXTERNAL;
dependencies[1].srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
dependencies[1].dstStageMask = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
dependencies[1].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
dependencies[1].dstAccessMask = VK_ACCESS_MEMORY_READ_BIT;
dependencies[1].dependencyFlags = VK_DEPENDENCY_BY_REGION_BIT;
```

**2. Minimize Render Pass Breaks:**
- Analyze where Xbox 360 games change render targets
- Batch multiple Xbox 360 operations into single Vulkan render pass
- Use subpasses for sequential dependencies within same framebuffer

### Phase 3: Shader Compilation Optimization (Medium Priority)

**1. Persistent Pipeline Cache:**
```cpp
// On startup
VkPipelineCacheCreateInfo cache_info = {};
cache_info.sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO;

// Try to load existing cache
std::vector<uint8_t> cache_data = LoadCacheFromDisk("xenia_pipeline_cache.bin");
if (!cache_data.empty()) {
    cache_info.initialDataSize = cache_data.size();
    cache_info.pInitialData = cache_data.data();
}

VkPipelineCache pipeline_cache;
vkCreatePipelineCache(device_, &cache_info, nullptr, &pipeline_cache);

// Use cache for all pipeline creations
// ...

// On shutdown
size_t cache_size = 0;
vkGetPipelineCacheData(device_, pipeline_cache, &cache_size, nullptr);
std::vector<uint8_t> updated_cache(cache_size);
vkGetPipelineCacheData(device_, pipeline_cache, &cache_size, updated_cache.data());
SaveCacheToDisk("xenia_pipeline_cache.bin", updated_cache);
```

**2. Background Compilation:**
- Create worker thread pool for shader compilation
- Queue likely pipelines during game load screens
- Use VK_PIPELINE_CREATE_ALLOW_DERIVATIVES_BIT for variants

### Phase 4: Tile Image Integration (Future Work)

**1. eDRAM Readback via Tile Images:**
- When Xbox 360 game reads from eDRAM in same pass
- Use VK_EXT_shader_tile_image instead of image load
- Requires shader rewrite but eliminates GMEM flush

**2. QCOM Tile Shading for Complex Scenes:**
- Detect heavy tiling games (Frostbite titles)
- Enable VK_QCOM_tile_shading mode
- Restructure render passes for explicit tile dispatch

---

## 5. Per-Engine Configuration Profiles

### Recommended Default Configurations

**Unreal Engine Games:**
```toml
[GPU]
query_occlusion_sample_lower_threshold = 60
query_occlusion_sample_upper_threshold = 120
[Vulkan]
aggressive_pipeline_caching = true
precompile_common_shaders = true
```

**Frostbite Games:**
```toml
[GPU]
enable_memexport_readback = true
rov_mode = true  # If accuracy needed
[Vulkan]
use_tile_image_extension = true
minimize_render_pass_breaks = true
```

**MT Framework Games:**
```toml
[GPU]
enable_memexport_readback = true
gpu_readback_optimization = true
[Vulkan]
use_subpasses_aggressive = true
```

**Source Engine Games:**
```toml
[GPU]
query_occlusion_sample_lower_threshold = 70
enable_gpu_readback = true
[Vulkan]
aggressive_pipeline_caching = true
use_tile_image_extension = true
```

---

## 6. Testing and Validation

### Recommended Test Games

**Occlusion Query Issues:**
- Murdered: Soul Suspect (Unreal Engine)
- Left 4 Dead (Source Engine)
- Dark Messiah (Source Engine)

**GPU Readback Issues:**
- Battlefield: Bad Company 2 (Frostbite)
- Need for Speed: The Run (Frostbite)
- Resident Evil 5 (MT Framework)
- Dragon's Dogma (MT Framework)

**Combined Issues:**
- CS:GO Beta (Source - both occlusion and readback)

### Success Metrics

1. **Reduced Texture Corruption**: Visual artifacts from GMEM thrashing
2. **Improved Frame Pacing**: More consistent frame times
3. **Eliminated Shader Stutter**: After cache warm-up, no hitching
4. **Correct Occlusion**: Objects appear/disappear correctly
5. **Working GPU Effects**: Post-processing and advanced rendering work

### Debugging Tools

**Xenia Built-in:**
- `dump_shaders` flag for shader analysis
- `vulkan_dump_disasm` for pipeline inspection
- Frame capture with RenderDoc support

**External:**
- RenderDoc for Vulkan pipeline analysis
- Android GPU Inspector for mobile profiling
- Qualcomm Snapdragon Profiler for GMEM analysis

---

## 7. Community Engagement

### Where to Get Mesa Turnip

**Pre-built Binaries:**
- https://github.com/whitebelyash/freedreno_turnip-CI/releases
- https://github.com/v3kt0r-87/Mesa-Turnip-Builder/releases
- AndroGaming.com compilation threads

**Build From Source:**
- Official Mesa GitLab: https://gitlab.freedreno.org/mesa/mesa
- Building guide: https://docs.mesa3d.org/drivers/freedreno.html

### Recommended Versions

**For Adreno 840:**
- Mesa 26.0 or later (has Gen 8 support)
- Use latest nightly builds for cutting-edge fixes
- Minimum: Mesa 25.0 for baseline Vulkan 1.3 compliance

**Installation:**
- aX360e should bundle Turnip driver
- Allow user override with custom driver
- Auto-detect and prefer Turnip when available

### Reporting Issues

**Mesa Turnip Issues:**
- GitLab: https://gitlab.freedreno.org/mesa/mesa/-/issues
- Tag with "turnip" and "emulation" labels
- Provide trace files and shader dumps

**aX360e Issues:**
- GitHub: https://github.com/n00bno0b/aX360e/issues
- Reference engine type from `game_engine_compatibility.md`
- Include Turnip version and GPU model

---

## 8. Conclusion

The Mesa Turnip driver provides significant advantages for Xbox 360 emulation on Adreno 840 hardware, particularly for the engine-specific compatibility issues documented in this project. Key takeaways:

1. **Turnip Superiority**: Open-source driver handles complex emulator workloads better than stock Qualcomm drivers
2. **Extension Support**: VK_EXT_shader_tile_image and VK_QCOM_tile_shading are game-changers for TBDR optimization
3. **Actionable Solutions**: Specific code changes and configurations can address each engine's issues
4. **Community Support**: Active development and rapid bug fixes from Mesa team

**Immediate Next Steps:**
1. Implement Turnip detection and extension checks
2. Apply VK_DEPENDENCY_BY_REGION_BIT to subpass dependencies
3. Set up persistent pipeline cache system
4. Test with representative games from each engine category

**Long-term Goals:**
1. Full VK_EXT_shader_tile_image integration for eDRAM emulation
2. Per-engine automatic configuration profiles
3. Contribute findings and fixes back to Mesa Turnip project
4. Build comprehensive shader cache library for popular games

This research provides a clear path forward to dramatically improve aX360e's compatibility and performance on Adreno 840 hardware.

---

## References

### Mesa Turnip Documentation
- Official Freedreno Docs: https://docs.mesa3d.org/drivers/freedreno.html
- DeepWiki Turnip Architecture: Multiple sources indexed
- Phoronix Mesa Coverage: Regular updates on Turnip development

### Vulkan Extensions
- VK_EXT_shader_tile_image: https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_shader_tile_image.html
- VK_QCOM_tile_shading: https://docs.vulkan.org/refpages/latest/refpages/source/VK_QCOM_tile_shading.html
- VK_EXT_fragment_shader_interlock: https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_fragment_shader_interlock.html

### Xenia Emulation
- Xenia GPU Docs: https://github.com/xenia-project/xenia/blob/master/docs/gpu.md
- Xenia Render Target Cache: https://xenia.jp/updates/2021/04/27/leaving-no-pixel-behind-new-render-target-cache-3x3-resolution-scaling.html
- Xbox 360 Xenos GPU: https://datacrystal.tcrf.net/wiki/Xbox_360/Hardware_information/Xenos_(GPU)

### Community Resources
- AndroGaming Turnip Builds: https://androgaming.com/precompiled-turnip-driver-compilation/
- Shader Cache Guide: https://emulation.gametechwiki.com/index.php/Shader_caches
- Shader Stutter Fixes: Multiple gaming optimization guides

---

**Document Version:** 1.0
**Last Updated:** March 30, 2026
**Next Review:** After Phase 1-2 implementation and testing
