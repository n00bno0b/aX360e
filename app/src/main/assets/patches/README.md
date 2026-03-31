# Game Patches for aX360e

This directory contains game patches sourced from the [xenia-canary/game-patches](https://github.com/xenia-canary/game-patches) repository.

## About

These patches are community-contributed fixes and enhancements for Xbox 360 games running in the Xenia emulator. They include:

- **Performance patches**: FPS unlocks, optimization tweaks
- **Bug fixes**: Fixes for game-specific issues
- **Enhancements**: Aspect ratio corrections, visual improvements
- **Quality of life**: Gameplay tweaks and improvements

## Patch Format

Each patch file follows the TOML format with this structure:

```toml
title_name = "Game Name"
title_id = "XXXXXXXX"  # 8-digit hex title ID
hash = "XXXXXXXXXXXX"  # Module hash for version matching

[[patch]]
    name = "Patch Name"
    desc = "Description of what the patch does"
    author = "Author Name"
    is_enabled = false  # Set to true to enable by default

    # Supported data types:
    [[patch.be8]]   # 8-bit big-endian integer
    [[patch.be16]]  # 16-bit big-endian integer
    [[patch.be32]]  # 32-bit big-endian integer
    [[patch.be64]]  # 64-bit big-endian integer
    [[patch.f32]]   # 32-bit float
    [[patch.f64]]   # 64-bit float
    [[patch.string]] # ASCII string
    [[patch.u16string]] # UTF-16 string
    [[patch.array]] # Byte array (hex string)
```

## How Patches Work

1. **Automatic Extraction**: On first launch, patches are extracted from app assets to `/data/data/aenu.ax360e/files/ax360e/patches/`
2. **Automatic Loading**: The patcher system automatically loads all `.patch.toml` files at startup
3. **Game Matching**: When a game loads, patches are matched by:
   - Title ID (must match exactly)
   - Module hash (for version-specific patches)
4. **Application**: Enabled patches modify game memory at specified addresses

## Enabling/Disabling Patches

Currently, patches must be enabled in the TOML files by setting `is_enabled = true`. A future update may add a GUI for patch management.

To manually enable a patch:
1. Navigate to `/data/data/aenu.ax360e/files/ax360e/patches/`
2. Edit the desired `.patch.toml` file
3. Find the patch you want and change `is_enabled = false` to `is_enabled = true`
4. Restart the emulator and load the game

## Configuration

The patcher system can be disabled entirely by setting `apply_patches = false` in the emulator configuration file.

## Updates

This directory contains a snapshot of patches from xenia-canary/game-patches. Future app updates may include newer patch versions.

## Credits

- **xenia-canary community**: For creating and maintaining these patches
- **Individual patch authors**: Listed in each patch's `author` field
- **Xenia project**: For the base patcher system

## License

Patches are community-contributed and available under the same terms as the xenia-canary project. See individual patch files for authorship information.

## Total Patches

This directory contains **473** patch files covering hundreds of Xbox 360 games.
