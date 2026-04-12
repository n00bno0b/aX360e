# aX360e - Xbox 360 Emulator for Android

aX360e is an Xbox 360 emulator for Android devices, based on the Xenia emulator project. It brings Xbox 360 gaming to ARM64 Android devices with Vulkan support.

## ⚠️ Important Notice

**This is an experimental emulator in active development.** Performance and compatibility vary significantly depending on your device hardware and the game being played.

## System Requirements

### Minimum Requirements
- **CPU**: ARM64-v8a architecture (64-bit ARM processor)
- **RAM**: 4 GB minimum (required for Xbox 360's virtual address space)
- **GPU**: Vulkan 1.1+ capable GPU (Adreno, Mali, or other Android GPUs)
- **OS**: Android 8.0 (API 26) or higher
- **Storage**: Several GB for game files and cache

### Recommended Requirements
- **RAM**: 6-8 GB or more
- **GPU**: Modern Adreno (710+, 730, 830) or flagship Mali GPU
- **CPU**: Snapdragon 8 Gen series or equivalent flagship SoC
- **Storage**: Fast UFS 3.1+ storage for best performance

### GPU Compatibility Notes
- **Adreno 830**: Requires sysmem mode (GMEM is broken in Mesa Turnip)
- **Adreno 710/720**: Benefits from GMEM mode for better performance
- **Samsung OneUI devices**: May need UBWC workarounds
- Custom Turnip drivers can improve performance on Qualcomm Adreno GPUs

## Key Features

### Emulation Core
- **Xenia-based**: Built on the Xenia Xbox 360 emulator with ARM64 optimizations
- **ARM64 backend**: Native ARM64 CPU emulation using AArch64 JIT compilation
- **Vulkan renderer**: Hardware-accelerated graphics via Vulkan API
- **Fragment Shader Interlock (FSI)**: High-accuracy render target emulation on supported GPUs
- **XMA audio decoding**: Both legacy and new experimental XMA audio decoders
- **In-game save support**: Supports normal game-provided save and load progress

### Graphics & Performance
- **Resolution scaling**: 1x, 2x, 3x internal resolution options
- **Post-processing**: FXAA, AMD FidelityFX CAS (sharpening), FSR 1.0 (upscaling)
- **Frame rate control**: VSync toggle, custom FPS limits
- **Custom Vulkan drivers**: Install custom Turnip drivers for Adreno GPUs
- **Advanced GPU settings**: MSAA, texture cache tuning, shader compilation options

### Input & Controls
- **Physical controllers**: Full support for Bluetooth/USB game controllers
- **Virtual touchscreen controls**: Customizable on-screen gamepad with editing mode
- **Key mapping**: Remap physical keyboard/controller inputs
- **Vibration support**: Haptic feedback when using compatible controllers

### Game Management
- **Automatic game detection**: Scans directories for Xbox 360 game files
- **Multiple formats**: ISO, XEX, STFS, and extracted game folders
- **Game metadata**: Displays title, icon, and game information
- **Search functionality**: Search and filter your game library
- **Shortcuts**: Create home screen shortcuts for games

### Customization & Advanced Features
- **Per-game configuration**: Individual settings for each game
- **Game patches**: Community patches from xenia-canary (FPS unlocks, bug fixes, enhancements)
- **Patch manager**: Built-in GUI to enable/disable patches
- **Extensive settings**: 100+ emulator configuration options across all subsystems
- **Performance presets**: Quick configurations for performance vs. quality
- **Debug tools**: Logging, profiler, shader dumps, trace capture

### Mobile-Specific Optimizations
- **Turnip environment variables**: Fine-tuned Mesa Turnip driver settings
- **Power management**: Battery-aware performance adjustments
- **Thermal awareness**: Settings to reduce load when device heats up
- **Low memory mode**: Aggressive memory management for RAM-constrained devices
- **Background resource unloading**: Free resources when app is backgrounded

## Supported File Formats

- **ISO**: Xbox 360 disc images (`.iso`)
- **XEX**: Xbox executables (`.xex`, `default.xex`)
- **STFS**: Xbox Live Arcade packages and DLC (auto-detected, no extension required)
- **GOD**: Games on Demand packages
- **Extracted folders**: Unpacked game directories

## Building from Source

### Prerequisites
- Android Studio (latest stable version)
- CMake 3.22.1+
- Android NDK (installed via Android Studio)
- Git

### Compilation Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/n00bno0b/aX360e.git
   cd aX360e
   ```

2. **Configure signing** (if building release):
   - The repository includes `app/build.gradle` with debug signing by default
   - For release builds, update the `signingConfigs` section in `app/build.gradle`

3. **Build with Android Studio**:
   - Open the project in Android Studio
   - Let Gradle sync complete
   - Build → Make Project
   - Build → Build Bundle(s) / APK(s) → Build APK(s)

4. **Or build via command line**:
   ```bash
   ./gradlew assembleDebug
   # Output: app/build/outputs/apk/debug/app-debug.apk
   ```

### Build Configuration
- **Target SDK**: Android 15 (API 35)
- **Min SDK**: Android 8.0 (API 26)
- **Architecture**: ARM64-v8a only
- **C++ Standard**: C++20
- **NDK**: c++_shared STL

## Installation & Setup

1. **Install the APK** on your Android device
2. **Grant permissions** when prompted (storage access required)
3. **Set game directory**: On first launch, select a folder containing your Xbox 360 games
4. **Wait for scanning**: The app will scan and index your games
5. **Select a game** from the library to launch

## Performance Tips

### For Best Performance
1. **Use custom Turnip drivers** on Adreno GPUs (Settings → Custom Drivers)
2. **Enable GPU-specific optimizations**:
   - Adreno 830: Enable "Force Sysmem Mode" and "Disable Low Resolution Z"
   - Adreno 710/720: Enable "Force GMEM Mode"
3. **Reduce resolution scale** if experiencing lag (Settings → GPU → Draw Resolution Scale)
4. **Disable VSync** for higher frame rates (Settings → GPU → VSYNC)
5. **Lower internal display resolution** (Settings → Video → Internal Display Resolution)
6. **Enable shader caching** (Settings → GPU → Store Shaders)

### For Better Battery Life
1. **Enable VSync** to cap frame rate
2. **Use lower resolution scales**
3. **Enable "Background Resource Unload"**
4. **Select "Battery Optimized" performance preset**

## Known Issues & Limitations

- **Not all games are playable**: Compatibility varies greatly by title
- **Performance intensive**: Even flagship devices may struggle with demanding games
- **High RAM usage**: Some games may crash on devices with less than 6GB RAM
- **Shader compilation stuttering**: First-time gameplay may have stutters
- **Audio sync issues**: Some games may experience audio desync
- **Graphics glitches**: Render target emulation isn't perfect on all games

## Advanced Configuration

The emulator provides extensive configuration options organized into categories:

- **APU**: Audio system, XMA decoder, music player settings
- **CPU**: Backend selection, debugging, thread management
- **GPU**: Renderer, resolution scaling, render target paths, texture cache
- **Display**: Post-processing, aspect ratio, safe areas
- **HID**: Input system, controller deadzones, vibration
- **Kernel**: NUI, title updates, debug monitors
- **Memory**: Virtual memory mapping, heap options
- **Storage**: Paths for saves, cache, and content
- **Video**: Display resolution, video standard, widescreen
- **Vulkan**: Device selection, validation, present modes
- **Patches**: Enable/disable community game patches

Settings can be configured globally or per-game via the game context menu.

## Game Patches

aX360e includes **473 community-created patches** from the [xenia-canary/game-patches](https://github.com/xenia-canary/game-patches) repository:

- **FPS unlocks**: Remove frame rate caps in games
- **Bug fixes**: Game-specific crash and glitch fixes
- **Visual enhancements**: Resolution fixes, aspect ratio corrections
- **Quality of life**: Gameplay tweaks and improvements

Patches can be managed via Settings → Patches → Patch Manager.

## Project Credits

### Based On
- **Xenia**: The original Xbox 360 emulator ([xenia-project/xenia](https://github.com/xenia-project/xenia))
- **Xenia Canary**: Enhanced Xenia fork with modern features ([xenia-canary](https://github.com/xenia-canary/xenia-canary))

### Third-Party Libraries
- **Mesa Turnip**: Vulkan driver for Qualcomm Adreno GPUs
- **FFmpeg**: Audio/video decoding
- **SPIRV-Tools**: Shader compilation
- **AMD FidelityFX**: CAS sharpening and FSR upscaling
- **SDL2**: Input handling
- **imgui**: Debug UI
- See individual library directories for full license information

### Contributors
- Thanks to all Xenia and Xenia Canary developers
- Ruban (UI icons and graphics)
- SRC267/Sting (community management)
- Community patch authors (473 patches)
- Testing and feedback contributors

## License

This project incorporates code from multiple sources with different licenses:

- **Xenia core**: BSD 3-Clause License
- **Third-party libraries**: See `LICENSE` file and individual library headers
- **Application code**: See file headers for specific licensing

Please check the LICENSE file and individual source file headers for detailed licensing information.

## Support & Community

- **Issues**: Report bugs on the [GitHub Issues](https://github.com/n00bno0b/aX360e/issues) page
- **Compatibility**: Check the Xenia compatibility database for game status
- **Documentation**: See Settings → About for update log and device info

## Disclaimer

This emulator is for educational and preservation purposes. You must own legal copies of any games you play. Xbox 360 and related trademarks are property of Microsoft Corporation.

---

**Current Version**: 1.0
**Last Updated**: 2026-04-12
**Minimum Android**: 8.0 (API 26)
**Target Android**: 15 (API 35)
