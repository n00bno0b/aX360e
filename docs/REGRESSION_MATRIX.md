# Engine Regression Testing Matrix

This matrix tracks the top recurring issue classes across the most critical engine families in the codebase. Tests should be run regularly against these baseline points to ensure compatibility and identify regressions.

## Columns Key
* **Title**: Game Name
* **Title ID**: Hex ID
* **Engine**: Engine Family
* **Test Scene / Save**: Where to test
* **Device**: Test device (e.g. Adreno 840)
* **GPU / Driver**: e.g., Mesa Turnip v24.1
* **Build / Commit**: Xenia build tested
* **Boot Status**: Pass/Fail
* **Menu Status**: Pass/Fail
* **In-game Status**: Pass/Fail
* **Playable**: Yes/No
* **Avg FPS / Frame-time note**: e.g., 30fps (VSync sensitive)
* **Thermal State**: Normal/Pressured/Throttling
* **Major Issue Tags**: (From compatibility list)
* **Notes / Workaround**: Any manual profile overrides needed
* **Regression**: +/- vs previous baseline

| Title | Title ID | Engine | Test Scene / Save | Device | GPU / Driver | Build / Commit | Boot Status | Menu Status | In-game Status | Playable | Avg FPS / Frame-time note | Thermal State | Major Issue Tags | Notes / Workaround | Regression |
|-------|----------|--------|-------------------|--------|--------------|----------------|-------------|-------------|----------------|----------|---------------------------|---------------|------------------|--------------------|------------|
| Murdered: Soul Suspect | 4D530A24 | Unreal | Prologue Alley | | | | | | | | | | `gpu-occlusion-query` | | |
| BioShock Infinite | 5454085D | Unreal | Lighthouse | | | | | | | | | | `gpu-occlusion-query` | | |
| Batman: Arkham City | 57520802 | Unreal | Courthouse Roof | | | | | | | | | | `gpu-occlusion-query` | | |
| Battlefield Bad Company 2 | 454108A8 | Frostbite | First mission drop | | | | | | | | | | `gpu-readback`, `kernel-unimplemented-feature` | | |
| Mirror's Edge | 45410850 | Unreal | Tutorial Roof | | | | | | | | | | `gpu-occlusion-query` | | |
| Dragon's Dogma | 43430814 | MT Framework | Cassardis start | | | | | | | | | | `gpu-readback`, `audio-silent` | | |
| Resident Evil 5 | 434307E4 | MT Framework | Assembly sequence | | | | | | | | | | `gpu-render-corrupt` | | |
| Lost Planet 2 | 434307ED | MT Framework | Jungle level | | | | | | | | | | `gpu-readback`, `audio-silent` | | |
| Fallout: New Vegas | 425307E0 | Gamebryo | Goodsprings exit | | | | | | | | | | `gpu-shader-errors`, `kernel-unimplemented-feature` | | |
| Civilization Revolution | 545407DE | Gamebryo | Turn 1 map | | | | | | | | | | `vsync-off-speedup` | | |
| The Elder Scrolls IV: Oblivion | 425307D1 | Gamebryo | Imperial City sewers | | | | | | | | | | `gpu-drawing-corrupt` | | |
| Left 4 Dead | 45410830 | Source | No Mercy roof | | | | | | | | | | `gpu-occlusion-query`, `kernel-unimplemented-feature` | | |
| Portal: Still Alive | 58410943 | Source | Test Chamber 10 | | | | | | | | | | `gpu-occlusion-query` | | |
| Dark Messiah of Might and Magic | 55530803 | Source | First combat area | | | | | | | | | | `gpu-readback` | | |
| Frogger: Hyper Arcade Edition | 58411244 | Unity | Level 1 | | | | | | | | | | `cpu-unimplemented-instruction`, `requires_protect_zero_false` | | |
| RAGE | 425307EC | id Tech | Wasteland drive | | | | | | | | | | `vsync-off-speedup`, `texture-streaming-corrupt` | | |
| F.E.A.R. | 565507D3 | LithTech | Office shootout | | | | | | | | | | `cpu-timing`, `gpu-corrupt` | | |
| DiRT 3 | 434D0846 | EGO Engine | Gymkhana event | | | | | | | | | | `gpu-readback` | | |
| Grand Theft Auto: San Andreas | 5454082B | RenderWare | Grove Street | | | | | | | | | | `gpu-drawing-corrupt` | | |
| Grand Theft Auto V | 54540842 | RAGE Engine | Prologue | | | | | | | | | | `audio-corrupt`, `kernel-unimplemented-feature` | | |
