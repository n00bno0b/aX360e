# Game Engine Compatibility Patterns in Xenia

This document catalogs the recurring compatibility issues observed across different game engines when running Xbox 360 titles on the Xenia emulator. Understanding these engine-specific patterns helps diagnose problems and develop targeted optimizations.

---

## Overview

Xbox 360 games were built using various commercial and proprietary engines, each with distinct rendering pipelines, resource management strategies, and low-level hardware access patterns. When emulated, these engine-specific approaches surface as characteristic compatibility issues that cluster by engine type rather than by individual game.

This documentation is based on observed patterns from Xenia's game compatibility reports and issue tracking.

---

## Unreal Engine

### Common Issues
- **GPU Occlusion Queries**: Unreal Engine titles frequently exhibit occlusion-query related problems
- **Shader Compilation Stutter**: Visible shader compilation hitching during gameplay, even after initial load
- **Performance**: Occlusion query handling can cause rendering bottlenecks

### Technical Background
Unreal Engine 3 heavily utilized the Xbox 360's hierarchical Z-buffer and occlusion query system for visibility culling. The engine performs aggressive occlusion queries to skip rendering of hidden objects. Xenia's emulation of these queries can introduce synchronization overhead that wasn't present on native hardware.

### Example Games
- **Murdered: Soul Suspect** - Tagged for `gpu-occlusion-query`; shader compilation stutter remains noticeable
- Other Unreal Engine 3 titles showing similar patterns

### Recommended Flags
- Adjust `query_occlusion_sample_lower_threshold` and `query_occlusion_sample_upper_threshold` in GPU configuration
- Monitor shader cache behavior with `dump_shaders` enabled for debugging

---

## Frostbite Engine

### Common Issues
- **Kernel Functions**: Missing or unimplemented Xbox 360 kernel features
- **XEX Switching**: Problems with executable switching and multi-XEX games
- **GPU Readback**: Memory export and GPU-to-CPU readback operations fail or hang
- **System Behavior**: Complex interactions between kernel, GPU, and memory systems

### Technical Background
Frostbite is known for pushing hardware limits and utilizing advanced Xbox 360 features like EDRAM tiling, compute shaders for post-processing, and tight CPU-GPU synchronization. The engine's sophisticated streaming and multi-threading systems interact heavily with kernel services.

### Example Games
- **Need for Speed: The Run** - `kernel-unimplemented-feature`, `kernel-xex-switching`
- **Battlefield: Bad Company 2** - `gpu-readback` issues
- **FIFA 17 Demo** - Kernel and GPU synchronization problems

### Known Tags
- `kernel-unimplemented-feature`
- `kernel-xex-switching`
- `gpu-readback`
- `gpu-memexport`

---

## MT Framework (Capcom)

### Common Issues
- **Silent Audio**: Audio systems frequently fail to initialize or produce output
- **GPU Readback**: Texture and buffer readback operations cause corruption or hangs
- **Render Corruption**: Graphical artifacts, missing textures, or incorrect rendering
- **Gameplay Depth**: Games often reach playable states despite above issues

### Technical Background
MT Framework uses a highly optimized rendering pipeline with extensive GPU compute shader usage for effects. The engine's audio middleware integration and complex material system can expose emulation gaps. Despite issues, the engine's robust fallback systems often allow games to remain somewhat playable.

### Example Games
- **Dragon's Dogma** - Audio and readback issues
- **Resident Evil 4 HD** - GPU readback problems
- **Resident Evil 5** - Render corruption with silent audio
- **Lost Planet 2** - Mix of audio, readback, and corruption issues

### Known Tags
- `audio-silent`
- `gpu-readback`
- `gpu-render-corrupt`

---

## Gamebryo (NetImmerse)

### Common Issues
- **Shader Corruption**: Incorrect shader compilation or execution
- **Drawing Corruption**: Vertex/index buffer problems, primitive rendering errors
- **Kernel Gaps**: Missing system functions for save games, timing, or file I/O
- **Timing Issues**: Frame pacing and VSync-related behavior
- **Save System Problems**: Save/load functionality failures

### Technical Background
Gamebryo is an older, highly modular engine that gives developers low-level control. Different licensees implemented rendering and system features differently, leading to varied compatibility issues. The engine's flexibility means each game may use different subsets of Xbox 360 features.

### Example Games
- **Defense Grid** - `gpu-drawing-corrupt`
- **Fallout: New Vegas** - `gpu-shader-errors`, `kernel-unimplemented-feature`
- **The Elder Scrolls IV: Oblivion** - Multiple GPU and kernel issues
- **Civilization Revolution** - `vsync-off-speedup` timing issues

### Known Tags
- `gpu-drawing-corrupt`
- `gpu-shader-errors`
- `kernel-unimplemented-feature`
- `vsync-off-speedup`
- `save-corrupt`

---

## Source Engine (Valve)

### Common Issues
- **Occlusion Queries**: Similar to Unreal Engine, heavy use of visibility queries
- **GPU Readback**: Readback operations for post-processing and effects
- **Kernel Gaps**: Missing Xbox 360 system functions
- **PowerPC Instructions**: Occasional missing or incorrectly emulated CPU instructions

### Technical Background
Source Engine on Xbox 360 was heavily modified from its PC origins, adding aggressive occlusion culling and optimized rendering paths. The engine uses GPU readback for certain HDR and post-processing effects, and relies on specific Xbox 360 kernel timing functions.

### Example Games
- **Dark Messiah of Might and Magic** - Full suite of occlusion, readback, and kernel issues
- **Counter-Strike: Global Offensive Beta** - PowerPC instruction gaps
- **Left 4 Dead** - Mix of GPU and kernel problems

### Known Tags
- `gpu-occlusion-query`
- `gpu-readback`
- `kernel-unimplemented-feature`
- `cpu-unimplemented-instruction`

---

## Unity (Xbox 360 Builds)

### Common Issues
- **PowerPC Instructions**: Frequent missing CPU instruction implementations
- **Memory Protection**: Requires `protect_zero_false` flag for null pointer handling
- **Graphics Glitches**: Missing or corrupted graphics elements
- **Save System Issues**: Save/load functionality problems

### Technical Background
Unity's Xbox 360 runtime uses specific PowerPC instruction sequences for its scripting VM and physics engine. The engine's memory management assumes certain Xbox 360-specific behaviors around null pointer access. Unity titles often used simplified graphics features compared to AAA engines, but exposed different CPU emulation gaps.

### Example Games
- **Frogger: Hyper Arcade Edition** - `cpu-unimplemented-instruction`, `requires_protect_zero_false`
- **Way of the Dogg** - CPU and memory protection issues
- **Max: The Curse of Brotherhood** - Missing graphics, CPU instructions
- **SpongeBob HeroPants** - Save and CPU instruction problems

### Known Tags
- `cpu-unimplemented-instruction`
- `requires_protect_zero_false`
- `gpu-missing-graphics`
- `save-corrupt`

---

## id Tech Engine

### Common Issues
- **Drawing/Shader Problems**: Rendering pipeline corruption
- **VSync Sensitivity**: Performance highly dependent on VSync settings
- **XEX Switching**: Multi-executable issues in some titles
- **Texture Streaming**: Problems with MegaTexture and virtual texturing systems

### Technical Background
id Tech engines (especially id Tech 4 and 5) use advanced rendering techniques like virtual texturing (MegaTexture in RAGE), deferred rendering, and heavy compute shader usage. The engines' precise frame timing requirements can conflict with emulator overhead, causing VSync-dependent speed issues.

### Example Games
- **RAGE** - VSync sensitivity, texture streaming issues
- **Brink** - Drawing and shader corruption
- **Prey (2006)** - Rendering pipeline problems
- **Quake 2** - XEX switching in ports
- **Quake 4** - Shader and drawing errors

### Known Tags
- `gpu-drawing-corrupt`
- `gpu-shader-errors`
- `vsync-off-speedup`
- `kernel-xex-switching`
- `texture-streaming-corrupt`

---

## LithTech Engine

### Common Issues
- **CPU Timing**: Frame rate and game logic timing problems
- **GPU Corruption**: Various rendering artifacts
- **Xbox Live Integration**: Sign-in and online service dependencies
- **Mixed Issues**: Combination of rendering, timing, and system problems

### Technical Background
LithTech titles often had tight coupling between game logic timing and frame rate, plus deep integration with Xbox Live services. The engine's modular architecture means different games exhibit different issue subsets. LithTech is notable for showing that not all engine problems are purely graphical.

### Example Games
- **F.E.A.R.** - CPU timing and GPU corruption
- **Middle-earth: Shadow of Mordor** - (Note: This may be a different engine; verify)
- **Gotham City Impostors** - Xbox Live sign-in gating, mixed issues

### Known Tags
- `cpu-timing`
- `gpu-corrupt`
- `xbox-live-signin-required`
- `kernel-unimplemented-feature`

---

## Other Engines

### RenderWare
**Issues**: Audio/XMP codec problems, drawing corruption
**Background**: Older cross-platform engine with custom audio middleware

**Known Tags**:
- `audio-xmp-codec`
- `gpu-drawing-corrupt`

### EGO Engine (Codemasters)
**Issues**: GPU readback operations, kernel function gaps
**Background**: Racing-focused engine with complex physics-graphics synchronization

**Known Tags**:
- `gpu-readback`
- `kernel-unimplemented-feature`

### RAGE Engine (Rockstar)
**Issues**: Audio system problems, kernel gaps, crashes
**Background**: Proprietary Rockstar engine with deep system integration (Note: Not to be confused with id Software's RAGE game)

**Known Tags**:
- `audio-corrupt`
- `kernel-unimplemented-feature`
- `crash-frequent`

---

## Cross-Engine Patterns

### Most Common Issues
1. **GPU Occlusion Queries** - Unreal, Source engines
2. **Kernel Unimplemented Features** - Frostbite, Gamebryo, Source, LithTech
3. **GPU Readback/Memexport** - Frostbite, MT Framework, Source, EGO
4. **Shader/Drawing Corruption** - Gamebryo, id Tech, RenderWare
5. **CPU Instruction Gaps** - Unity, Source

### Engine Sophistication vs. Issues
- **High-end engines** (Frostbite, id Tech): Tend to expose kernel and advanced GPU features
- **Mid-tier engines** (MT Framework, Gamebryo): Mix of GPU corruption and system issues
- **Scripting-heavy engines** (Unity): CPU emulation gaps more common
- **Older engines** (RenderWare, LithTech): Varied issues based on specific implementations

### VSync-Dependent Behavior
Some engines (Gamebryo, id Tech) show `vsync-off-speedup` tags, indicating that game logic timing is incorrectly tied to frame rendering when VSync is disabled. This is an emulation timing issue rather than an engine bug.

---

## Using This Information

### For Developers
- When encountering a new game issue, check its engine type first
- Apply known workarounds for that engine pattern
- Focus debugging efforts on the characteristic weak points of each engine
- Consider engine-specific configuration profiles

### For Users
- Consult this guide when experiencing issues with a game
- Try engine-specific configuration adjustments
- Report new patterns that don't match documented behavior
- Understand that some engine issues require emulator improvements rather than per-game fixes

### For Optimization
- Implement engine-detection heuristics in Xenia
- Create engine-specific rendering and kernel paths
- Prioritize fixes that benefit entire engine families
- Consider per-engine default configuration profiles

---

## Future Work

### Needed Improvements
1. **Occlusion Query Optimization**: Reduce synchronization overhead for Unreal/Source engines
2. **Kernel Function Implementation**: Fill gaps affecting Frostbite and Gamebryo games
3. **GPU Readback Efficiency**: Improve handling for MT Framework and Frostbite
4. **PowerPC Coverage**: Complete instruction set for Unity games
5. **Timing Accuracy**: Fix VSync-dependent speed issues in id Tech and Gamebryo

### Engine Detection System
Consider implementing automatic engine detection based on:
- Binary signatures in game executables
- Rendering API usage patterns
- Memory allocation patterns
- Known title ID to engine mappings

### Per-Engine Configuration
Create configuration profiles that automatically apply optimal settings based on detected engine type, reducing need for per-game tweaking.

---

## References

- Xenia Game Compatibility Database (compatibility reports)
- Xenia Issue Tracker (engine-specific tags)
- Individual game compatibility pages on Xenia website
- Community testing and reports

---

## Contributing

When reporting new engine-specific patterns:
1. Identify the game engine (check game credits or research)
2. Note specific error tags and symptoms
3. Test multiple games using the same engine
4. Document which Xenia versions were tested
5. Include any successful workarounds

This document will be updated as new patterns emerge and as Xenia's engine compatibility improves.
