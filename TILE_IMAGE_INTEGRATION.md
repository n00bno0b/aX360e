# VK_EXT_shader_tile_image Integration Guide

## Phase 4A: Infrastructure (✅ COMPLETED)

Phase 4A added feature detection and infrastructure without modifying shader generation:

### Changes Made
1. **Feature Detection** (`spirv_shader_translator.h/.cc`):
   - Added `shader_tile_image_color_read_access` to Features struct
   - Added `shader_tile_image_depth_read_access` to Features struct
   - Added `shader_tile_image_stencil_read_access` to Features struct
   - Features initialized from VulkanDevice properties (Phase 1)

2. **Runtime Flag** (`spirv_shader_translator.h`):
   - Added `edram_tile_image_enabled_` boolean member
   - Initialized to `false` in constructor
   - Reserved for Phase 4B to enable actual tile image usage

3. **Helper Function** (`spirv_shader_translator.h`):
   - Added `CanUseTileImagesForEdram()` const function
   - Returns true when: FSI enabled + tile image support + pixel shader
   - Phase 4B will use this to conditionally emit tile image operations

---

## Phase 4B: Shader Implementation (🔄 TODO - Requires Hardware Testing)

### Overview

Phase 4B will modify shader generation to use tile images instead of storage buffer loads for eDRAM color reads on Adreno GPUs with Turnip driver.

**Goal**: Replace storage buffer loads with `OpColorAttachmentReadEXT` to keep data in GMEM.

**Benefits**:
- Eliminates GMEM ↔ main memory transfers on TBDR GPUs
- Reduces memory bandwidth by up to 80% for complex rendering
- Particularly beneficial for Frostbite/Source engines with heavy eDRAM usage

---

## Implementation Strategy

### Step 1: Enable Tile Images in Constructor

**File**: `vulkan_pipeline_cache.cc` (or shader translator initialization)

```cpp
// In VulkanPipelineCache::Initialize() or similar
shader_translator_ = std::make_unique<SpirvShaderTranslator>(
    SpirvShaderTranslator::Features(vulkan_device),
    render_target_cache_.msaa_2x_attachments_supported(),
    render_target_cache_.msaa_2x_no_attachments_supported(),
    edram_fragment_shader_interlock);

// Phase 4B: Enable tile images if supported
if (shader_translator_->CanUseTileImagesForEdram() &&
    vulkan_device->properties().isTurnipDriver) {
  // Set the flag to enable tile image path
  // shader_translator_->set_edram_tile_image_enabled(true);
  XELOGI("VulkanPipelineCache: Enabling tile images for eDRAM optimization");
}
```

### Step 2: Declare Input Attachments in Fragment Shader

**File**: `spirv_shader_translator.cc`

**Location**: `StartFragmentShaderBeforeMain()` function (~line 1784)

**Current Code** (FSI storage buffer):
```cpp
if (edram_fragment_shader_interlock_) {
  builder_->addExtension("SPV_EXT_fragment_shader_interlock");

  // EDRAM buffer uint[]
  id_vector_temp_.clear();
  id_vector_temp_.push_back(builder_->makeRuntimeArray(type_uint_));
  // Storage buffers have std430 packing
  // ...
}
```

**Phase 4B Addition** (tile image input attachments):
```cpp
if (edram_fragment_shader_interlock_) {
  builder_->addExtension("SPV_EXT_fragment_shader_interlock");

  if (edram_tile_image_enabled_) {
    // Phase 4B: Use tile images for color attachments
    builder_->addExtension("SPV_EXT_shader_tile_image");
    builder_->addCapability(spv::CapabilityTileImageColorReadAccessEXT);

    // Declare input attachments for each render target
    // SubpassData type for reading from current pixel location
    spv::Id type_subpass_data = builder_->makeImageType(
        type_float4_,
        spv::DimSubpassData,
        false,  // not depth
        false,  // not array
        false,  // not multisampled (handle MSAA separately)
        2,      // read (sampled=2 for SubpassData)
        spv::ImageFormatUnknown);

    for (uint32_t i = 0; i < xenos::kMaxColorRenderTargets; ++i) {
      spv::Id var_input_attachment = builder_->createVariable(
          spv::NoPrecision,
          spv::StorageClassInput,
          type_subpass_data,
          fmt::format("xe_input_attachment_{}", i).c_str());

      builder_->addDecoration(var_input_attachment,
                             spv::DecorationInputAttachmentIndex, i);
      builder_->addDecoration(var_input_attachment,
                             spv::DecorationDescriptorSet,
                             kDescriptorSetEdramAndSharedMemory);
      builder_->addDecoration(var_input_attachment,
                             spv::DecorationBinding,
                             kEdramInputAttachmentBindingStart + i);

      // Store for later use in FSI_LoadEdramColor()
      fsi_input_attachments_[i] = var_input_attachment;
    }
  } else {
    // Original FSI storage buffer path
    // ... existing code ...
  }
}
```

### Step 3: Implement Color Read Function

**File**: `spirv_shader_translator.cc`

Add new function for reading from tile images:

```cpp
// Phase 4B: Load color data from tile image instead of storage buffer
spv::Id SpirvShaderTranslator::FSI_LoadColorFromTileImage(
    uint32_t rt_index, spv::Id sample_index) {
  assert_true(edram_tile_image_enabled_);
  assert_true(rt_index < xenos::kMaxColorRenderTargets);

  // OpColorAttachmentReadEXT reads from the current fragment location
  // No coordinate needed - implicitly reads from current pixel
  spv::Id input_attachment = fsi_input_attachments_[rt_index];

  // For MSAA, use OpImageRead with sample index
  // For non-MSAA, OpColorAttachmentReadEXT without sample
  if (sample_index != spv::NoResult) {
    // MSAA path: need to read specific sample
    std::vector<spv::Id> operands;
    operands.push_back(sample_index);
    return builder_->createOp(spv::OpImageRead, type_float4_,
                             {input_attachment, operands});
  } else {
    // Non-MSAA path: simple tile image read
    return builder_->createUnaryOp(spv::OpColorAttachmentReadEXT,
                                  type_float4_, input_attachment);
  }
}
```

### Step 4: Modify FSI Color Load Paths

**File**: `spirv_shader_translator.cc`

**Location**: All places where eDRAM color is loaded via storage buffer

Find code like:
```cpp
// Current FSI storage buffer load
spv::Id edram_address = /* calculate address */;
spv::Id edram_data = builder_->createLoad(
    builder_->createAccessChain(spv::StorageClassStorageBuffer,
                                buffers_edram_, {edram_address}));
```

Replace with conditional:
```cpp
spv::Id edram_data;
if (edram_tile_image_enabled_) {
  // Phase 4B: Use tile image read
  edram_data = FSI_LoadColorFromTileImage(rt_index, sample_index);
} else {
  // Original FSI storage buffer path
  edram_data = builder_->createLoad(
      builder_->createAccessChain(spv::StorageClassStorageBuffer,
                                  buffers_edram_, {edram_address}));
}
```

### Step 5: Update Render Pass Creation

**File**: `vulkan_render_target_cache.cc`

**Changes Needed**:

1. Mark color attachments as BOTH output AND input:
```cpp
// In render pass creation
if (tile_image_enabled) {
  attachment_desc.flags |= VK_ATTACHMENT_DESCRIPTION_MAY_ALIAS_BIT;
  // Keep attachment loaded for reading
  attachment_desc.loadOp = VK_ATTACHMENT_LOAD_OP_LOAD;
}
```

2. Add input attachment references to subpass:
```cpp
VkAttachmentReference input_attachments[xenos::kMaxColorRenderTargets];
for (uint32_t i = 0; i < color_rt_count; ++i) {
  input_attachments[i].attachment = i;
  input_attachments[i].layout = VK_IMAGE_LAYOUT_GENERAL;
}

subpass_desc.inputAttachmentCount = color_rt_count;
subpass_desc.pInputAttachments = input_attachments;
```

3. Ensure `VK_DEPENDENCY_BY_REGION_BIT` is set (✅ already done in Phase 2)

---

## Testing Requirements

### Hardware Requirements
- **GPU**: Qualcomm Adreno 840 (Snapdragon 8 Elite)
- **Driver**: Mesa Turnip 25.0+ (not stock Qualcomm driver)
- **OS**: Android 14+ or Linux with Turnip build

### Test Games
1. **Unreal Engine** (heavy occlusion queries):
   - Murdered: Soul Suspect
   - BioShock

2. **Frostbite** (heavy eDRAM usage):
   - Battlefield: Bad Company
   - Mirror's Edge

3. **Source Engine** (combined issues):
   - Left 4 Dead
   - Portal 2

### Validation Steps
1. Enable tile images, verify no crashes
2. Compare rendering output: tile images vs. storage buffer (pixel-perfect match required)
3. Measure frame times (should see 10-20% improvement in complex scenes)
4. Run for extended period (30+ min) to catch edge cases
5. Test with validation layers enabled

### Performance Metrics
Expected improvements with tile images vs. storage buffer:
- **Bandwidth**: -60% to -80% GMEM traffic
- **Frame time**: -10% to -25% in eDRAM-heavy games
- **Power**: -5% to -15% overall system power

---

## SPIRV Extensions Reference

### VK_EXT_shader_tile_image

**Capability**: `CapabilityTileImageColorReadAccessEXT`

**Operations**:
- `OpColorAttachmentReadEXT` - Read color from current fragment location
- For depth/stencil: `OpDepthAttachmentReadEXT`, `OpStencilAttachmentReadEXT`

**Constraints**:
- Only in fragment shaders
- Reads from current pixel location (no arbitrary coordinates)
- Works with MSAA (specify sample index)
- Attachment must be both input and output in render pass

**Vulkan Spec**:
https://registry.khronos.org/vulkan/specs/1.3-extensions/man/html/VK_EXT_shader_tile_image.html

---

## Debugging Tips

### Enable Logging
```cpp
#define XENIA_TILE_IMAGE_DEBUG 1
```

### Common Issues

1. **Black Screen**: Input attachment not properly bound
   - Check descriptor set bindings
   - Verify render pass has input attachment references

2. **Crashes on OpColorAttachmentReadEXT**: Extension not enabled
   - Verify `SPV_EXT_shader_tile_image` is in SPIR-V module
   - Check capability is declared

3. **Wrong Colors**: Layout mismatch
   - Ensure attachment layout is GENERAL or COLOR_ATTACHMENT_OPTIMAL
   - Check format matches between attachment and shader type

4. **Performance Worse**: Not actually using tile memory
   - Verify Turnip driver (not stock Qualcomm)
   - Check `VK_DEPENDENCY_BY_REGION_BIT` is set
   - Ensure no render pass breaks between read and write

### Validation Layers
Enable for Phase 4B development:
```bash
export VK_INSTANCE_LAYERS=VK_LAYER_KHRONOS_validation
export VK_LAYER_ENABLES=VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT
```

---

## Community Contribution

If you have access to Adreno 840 + Turnip hardware:

1. **Test Phase 4A**: Verify feature detection works
   ```
   adb logcat | grep "Detected Qualcomm Adreno GPU"
   adb logcat | grep "Detected Mesa Turnip driver"
   adb logcat | grep "shaderTileImageColorReadAccess"
   ```

2. **Implement Phase 4B**: Follow this guide to add shader changes

3. **Submit Results**: Open GitHub issue with:
   - Test game and scene
   - Frame time comparison (before/after)
   - Screenshots or video capture
   - Any rendering artifacts

4. **Report Issues**:
   - aX360e: https://github.com/n00bno0b/aX360e/issues
   - Mesa Turnip: https://gitlab.freedesktop.org/mesa/mesa/-/issues

---

## References

- **Phase 1**: Driver detection and extension enablement
- **Phase 2**: VK_DEPENDENCY_BY_REGION_BIT optimization
- **Phase 3**: Persistent VkPipelineCache
- **Phase 4A**: Infrastructure (this document)
- **Phase 4B**: Shader implementation (TODO)

**Research Document**: `mesa_turnip_research_findings.md`
**Engine Issues**: `game_engine_compatibility.md`

---

**Document Version**: 1.0
**Last Updated**: March 30, 2026
**Status**: Phase 4A Complete, Phase 4B Ready for Implementation
