# Xbox 360 Emulation Regression Matrix

**Version:** 1.0
**Last Updated:** 2026-04-11
**Purpose:** Track compatibility status of representative games across engine families

---

## Overview

This regression matrix tracks 20 representative Xbox 360 games organized by engine family. The games are selected based on the documented compatibility patterns in `game_engine_compatibility.md` and cover the five recurring failure classes:

1. **GPU Occlusion Queries** - Visibility culling and occlusion testing issues
2. **Kernel Unimplemented Features** - Missing Xbox 360 kernel/system functions
3. **GPU Readback/Memexport** - GPU-to-CPU memory operations
4. **Shader or Drawing Corruption** - Rendering pipeline errors
5. **CPU Instruction Gaps** - Missing or incorrectly emulated PowerPC instructions

---

## Testing Methodology

### Test Environment
- **Device**: Document specific device model (e.g., Samsung Galaxy S24 Ultra)
- **GPU**: Document GPU model and driver (e.g., Adreno 750, Turnip v13)
- **Driver Mode**: System driver vs Custom Turnip driver
- **Build**: Git commit hash or version number
- **Test Date**: Date of testing

### Status Definitions
- ✅ **PASS**: Boots, menu works, gameplay functional, 30+ FPS avg
- ⚠️ **PARTIAL**: Boots, has issues but playable with workarounds
- ❌ **FAIL**: Crashes, black screen, or unplayable (<15 FPS)
- ❓ **UNTESTED**: Not yet tested

### Save/Repro Points
Test from consistent save points to ensure reproducible results. Document:
- Specific level/mission name
- Approximate timestamp in game
- Actions to reproduce issues

---

## Regression Matrix

### 1. Unreal Engine 3/3.5

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **Gears of War** | 45410912 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Occlusion queries | UE3, heavy GPU usage |
| **Mass Effect** | 4D5307D1 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Occlusion queries, shader stutter | UE3, complex scenes |
| **BioShock** | 4D53082C | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Occlusion queries | UE2.5, water effects |
| **Murdered: Soul Suspect** | 53510826 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Occlusion queries, shader stutter | Documented in compat notes |

**Engine Profile Applied**: Occlusion query threshold adjustments, shader cache monitoring

**Critical Test Scenarios**:
- Outdoor environments with many objects (occlusion query stress)
- Scene transitions (shader compilation)
- Combat with particle effects

---

### 2. Frostbite Engine

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **Battlefield: Bad Company 2** | 45410869 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | GPU readback, memexport | Documented issue |
| **Need for Speed: The Run** | 545408FC | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Kernel gaps, XEX switching | Multi-XEX game |
| **Crysis 2** | 454109D5 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | GPU readback, kernel features | Advanced rendering |

**Engine Profile Applied**: GPU readback workarounds, resolution scale adjustments

**Critical Test Scenarios**:
- Multiplayer maps (streaming stress)
- Destructible environments (GPU compute)
- Level loading transitions (XEX switching)

---

### 3. MT Framework (Capcom)

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **Dragon's Dogma** | 4343082F | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Audio silent, GPU readback | Often playable despite issues |
| **Resident Evil 5** | 43430841 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Render corruption, audio | Documented issues |
| **Lost Planet 2** | 43430852 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Audio, readback, corruption | Mix of all MT Framework issues |

**Engine Profile Applied**: GPU readback duplicate pixel workaround

**Critical Test Scenarios**:
- Cutscenes (audio playback)
- Boss battles (GPU effects)
- Co-op mode if available (stress test)

---

### 4. Gamebryo/NetImmerse

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **Fallout: New Vegas** | 425307E1 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Shader errors, kernel gaps | Complex open world |
| **Fallout 3** | 425307D1 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Shader errors, kernel gaps | Similar to FNV |
| **The Elder Scrolls V: Skyrim** | 425307E3 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Drawing corruption, timing | Large open world |

**Engine Profile Applied**: VSync enabled, 50Hz mode disabled for timing stability

**Critical Test Scenarios**:
- Fast travel (loading system)
- Dense areas like cities (rendering stress)
- Save/load functionality (kernel interaction)

---

### 5. Source Engine (Valve)

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **Left 4 Dead** | 45410914 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Occlusion, readback, kernel | Full suite of issues |
| **Left 4 Dead 2** | 45410915 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Similar to L4D1 | Improved engine version |
| **Portal 2** | 454108CE | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Occlusion queries, GPU effects | Physics-heavy |

**Engine Profile Applied**: Occlusion query fixes, GPU readback workarounds

**Critical Test Scenarios**:
- Large open areas (occlusion queries)
- Portal rendering (GPU effects)
- Particle-heavy scenes (zombies, fire)

---

### 6. Unity (Xbox 360 Runtime)

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **Frogger: Hyper Arcade** | 4B4E0877 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | CPU instructions, protect_zero | Documented case |

**Engine Profile Applied**: None (Unity-specific fixes are in emulator core)

**Critical Test Scenarios**:
- Game startup (CPU instruction coverage)
- Multiple levels (memory protection)

---

### 7. id Tech 4/5

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **RAGE** | 425307F1 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | VSync sensitivity, texture streaming | MegaTexture system |
| **DOOM 3 BFG Edition** | 425307DB | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Drawing/shader issues | id Tech 4 |

**Engine Profile Applied**: VSync enabled for timing stability

**Critical Test Scenarios**:
- Outdoor wasteland (texture streaming)
- Fast camera movement (streaming stress)
- Different VSync settings (performance comparison)

---

### 8. LithTech Jupiter

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **F.E.A.R.** | 57520852 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | CPU timing, GPU corruption | Documented issues |
| **F.E.A.R. 2** | 575208A3 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Similar to F.E.A.R. | Improved engine |
| **Murdered: Soul Suspect** | 53510826 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | Kernel gaps | (Note: Also in Unreal list, verify engine) |

**Engine Profile Applied**: None (LithTech-specific fixes are kernel-side)

**Critical Test Scenarios**:
- Combat sequences (timing sensitive)
- Slow-mo effects (frame rate manipulation)

---

### 9. EGO Engine (Codemasters)

| Title | Title ID | Device | GPU/Driver | Build | Boot | Menu | In-Game | Playable | Avg FPS | Major Issues | Notes |
|-------|----------|--------|------------|-------|------|------|---------|----------|---------|--------------|-------|
| **GRID 2** | 434D0834 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | GPU readback/memexport | Racing game stress test |
| **DiRT 3** | 434D0866 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | GPU readback/memexport | Similar to GRID |
| **F1 2012** | 434D0892 | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | GPU readback/memexport | Latest in series |

**Engine Profile Applied**: GPU readback duplicate pixel workaround

**Critical Test Scenarios**:
- Rain/weather effects (GPU compute)
- High-speed racing (frame pacing)
- Replays (readback stress)

---

## Issue Class Summary

### Issue Distribution by Engine

| Engine | Occlusion | Kernel | Readback | Shader/Draw | CPU Inst |
|--------|-----------|--------|----------|-------------|----------|
| Unreal | ✓✓✓ | - | - | ✓ | - |
| Frostbite | - | ✓✓✓ | ✓✓✓ | ✓ | - |
| MT Framework | - | - | ✓✓✓ | ✓✓ | - |
| Gamebryo | - | ✓✓ | - | ✓✓✓ | - |
| Source | ✓✓ | ✓✓ | ✓✓ | - | ✓ |
| Unity | - | - | - | ✓ | ✓✓✓ |
| id Tech | - | ✓ | - | ✓✓✓ | - |
| LithTech | - | ✓✓ | - | ✓✓ | - |
| EGO | - | - | ✓✓✓ | - | - |

**Legend**: ✓ = Minor impact, ✓✓ = Moderate impact, ✓✓✓ = Major impact

---

## Testing Guidelines

### Pre-Test Setup
1. **Clear shader cache** before each test run to measure shader compilation performance
2. **Document device thermal state** - test when device is cool (first run) and warm (sustained play)
3. **Use consistent driver settings** - document Turnip env vars (TU_DEBUG, etc.)
4. **Record baseline FPS** in menu screens for comparison

### During Testing
1. **Play for 15+ minutes** to catch issues that appear after warmup
2. **Test all major game systems** - combat, exploration, menus, saves
3. **Monitor thermal throttling** - note when/if performance degrades
4. **Save frequently** - test save/load functionality
5. **Document workarounds** - note any settings changes that improve compatibility

### Metrics to Collect
- **Boot time** - from launch to menu
- **Shader compilation count** - unique shaders compiled
- **Average FPS** - during representative gameplay
- **1% low FPS** - worst frame times
- **Memory usage** - peak and average
- **Thermal state** - max temperature reached
- **Battery drain** - for portable devices

---

## Regression Testing Process

### When to Run Regression Tests
1. **Before major releases** - full matrix on 2+ devices
2. **After core emulator changes** - affected engine subset
3. **After driver updates** - full matrix if driver behavior changed
4. **Weekly continuous testing** - automated subset on dev builds

### Pass/Fail Criteria
A game **passes** regression if:
- No new crashes introduced vs. baseline
- Performance within 10% of baseline
- No new visual corruption
- Existing workarounds still function

A game **fails** regression if:
- New crash or hang introduced
- Performance degraded >20%
- New visual corruption blocking gameplay
- Previously working features now broken

### Reporting Template
```markdown
## Regression Test: [Game Title]

**Build**: [commit hash]
**Device**: [model]
**GPU/Driver**: [GPU + driver version]
**Date**: [YYYY-MM-DD]

### Status: PASS/FAIL/PARTIAL

### Changes from Baseline
- Boot: [better/same/worse]
- Performance: [FPS change]
- New issues: [list]
- Fixed issues: [list]

### Thermal Behavior
- Max temp: [°C]
- Throttled: [yes/no]
- Sustained FPS: [FPS]

### Notes
[Additional observations]
```

---

## Machine-Readable Data

For automation, maintain a companion `regression_matrix.json` with structured data:

```json
{
  "version": "1.0",
  "last_updated": "2026-04-11",
  "games": [
    {
      "title": "Gears of War",
      "title_id": "45410912",
      "engine": "UNREAL",
      "issue_tags": ["gpu-occlusion-query", "gpu-shader-stutter"],
      "test_results": []
    }
  ]
}
```

---

## Maintenance

### Adding Games
When adding games to the matrix:
1. Choose representative titles from underrepresented engines
2. Prioritize popular games with known compatibility issues
3. Include at least one "gold standard" fully-working game per engine
4. Document issue tags from `game_engine_compatibility.md`

### Updating Results
- **Update timestamp** and **build hash** with each test run
- **Keep historical data** in version control for trend analysis
- **Note engine profile changes** that affect results
- **Link to related issues** in GitHub issue tracker

---

## Related Documentation
- `game_engine_compatibility.md` - Engine-specific compatibility patterns
- `engine_mappings.json` - Title ID to engine mappings
- `ANDROID_TURNIP_OPTIMIZATION_GUIDE.md` - Driver optimization guide
- `TURNIP_DRIVER_INTEGRATION_NOTES.md` - Custom driver setup

---

**Maintainers**: Update this matrix after each significant compatibility improvement or regression.
**Contributors**: Feel free to add test results via pull requests following the template above.
