# Turnip Driver Integration Technical Notes

**Date:** March 31, 2026
**Component:** Custom Vulkan Driver Loading for Android
**Status:** Enhancement Recommendations

---

## Current Implementation

The aX360e project has a custom Vulkan loader in `vkapi.cpp` and `vkapi.h` that supports loading custom drivers:

```cpp
void vk_load(const char* lib_path, bool is_adreno_custom=false);
```

This allows loading Mesa Turnip drivers instead of stock Qualcomm drivers.

---

## Enhancement Recommendations

### 1. Environment Variable Support

Mesa Turnip drivers support several environment variables for tuning and debugging. These should be set before loading the driver.

**Recommended Environment Variables:**

```cpp
// In vk_load(), before dlopen():
if (is_adreno_custom) {
    // Force GMEM mode for better compatibility on mid-range Adreno (710/720)
    setenv("TU_DEBUG", "gmem", 1);

    // Enable specific Freedreno features for compatibility
    // setenv("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1", 1);

    // For debugging (disable in production)
    // setenv("FD_MESA_DEBUG", "1", 1);
    // setenv("MESA_DEBUG", "1", 1);
}
```

**Usage Pattern:**
```cpp
// From Java/Kotlin layer
String turnipDriverPath = getTurnipDriverPath();
if (turnipDriverPath != null) {
    // Set environment before loading
    setEnv("TU_DEBUG", "gmem");
    vk_load(turnipDriverPath, true);
} else {
    vk_load("/system/lib64/libvulkan.so", false);
}
```

### 2. Driver Version Detection

After loading, detect which driver was actually loaded:

```cpp
struct DriverInfo {
    bool is_turnip;
    bool is_adreno;
    int mesa_version_major;
    int mesa_version_minor;
    int mesa_version_patch;
    std::string driver_name;
    std::string device_name;
};

DriverInfo detect_driver_info(VkPhysicalDevice physical_device) {
    DriverInfo info = {};

    // Get device properties
    VkPhysicalDeviceProperties props;
    vkGetPhysicalDeviceProperties(physical_device, &props);

    info.is_adreno = (props.vendorID == 0x5143);
    info.device_name = props.deviceName;

    // Get driver properties (Vulkan 1.2+)
    VkPhysicalDeviceProperties2 props2 = {};
    props2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;

    VkPhysicalDeviceDriverProperties driver_props = {};
    driver_props.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES;
    props2.pNext = &driver_props;

    vkGetPhysicalDeviceProperties2(physical_device, &props2);

    info.driver_name = driver_props.driverName;
    info.is_turnip = (strstr(driver_props.driverName, "turnip") != nullptr ||
                      strstr(driver_props.driverName, "Turnip") != nullptr ||
                      strstr(driver_props.driverName, "Mesa") != nullptr);

    // Parse Mesa version from driverInfo if available
    // Format: "Mesa 26.0.0" or similar
    if (driver_props.driverInfo[0]) {
        sscanf(driver_props.driverInfo, "Mesa %d.%d.%d",
               &info.mesa_version_major,
               &info.mesa_version_minor,
               &info.mesa_version_patch);
    }

    return info;
}
```

### 3. Per-Game Driver Selection

Implement a system similar to Eden's per-game driver configuration:

**Java Layer Enhancement:**
```java
// In game configuration
public class GameSettings {
    // null = use global default driver
    private String customDriverPath;

    // Turnip-specific settings
    private boolean forceTurnipGmem = false;
    private boolean enableTurnipDebug = false;
    private String turnipRevision; // e.g., "R7", "R8"

    public void applyToNative() {
        if (customDriverPath != null) {
            // Set environment variables based on settings
            if (forceTurnipGmem) {
                NativeLib.setEnvironmentVariable("TU_DEBUG", "gmem");
            }
            if (enableTurnipDebug) {
                NativeLib.setEnvironmentVariable("FD_MESA_DEBUG", "1");
            }

            NativeLib.loadCustomDriver(customDriverPath);
        }
    }
}
```

**Native JNI Bridge:**
```cpp
// JNI function to set environment variables
JNIEXPORT void JNICALL
Java_aenu_ax360e_NativeLib_setEnvironmentVariable(
    JNIEnv* env, jclass clazz, jstring key, jstring value) {

    const char* key_str = env->GetStringUTFChars(key, nullptr);
    const char* value_str = env->GetStringUTFChars(value, nullptr);

    setenv(key_str, value_str, 1);

    env->ReleaseStringUTFChars(key, key_str);
    env->ReleaseStringUTFChars(value, value_str);
}
```

### 4. Fallback and Error Handling

Improve driver loading with fallback logic:

```cpp
bool vk_load_with_fallback(const char* custom_path) {
    // Try custom driver first
    if (custom_path && custom_path[0]) {
        try {
            vk_load(custom_path, true);

            // Validate that Vulkan actually works
            VkInstance test_instance;
            VkApplicationInfo app_info = {};
            app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
            app_info.apiVersion = VK_API_VERSION_1_1;

            VkInstanceCreateInfo instance_info = {};
            instance_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
            instance_info.pApplicationInfo = &app_info;

            VkResult result = vkCreateInstance(&instance_info, nullptr, &test_instance);
            if (result == VK_SUCCESS) {
                vkDestroyInstance(test_instance, nullptr);
                LOGI("Custom Turnip driver loaded successfully");
                return true;
            } else {
                LOGW("Custom driver failed validation, falling back to system driver");
                vk_unload();
            }
        } catch (...) {
            LOGW("Custom driver loading failed, falling back to system driver");
            vk_unload();
        }
    }

    // Fallback to system driver
    vk_load("/system/lib64/libvulkan.so", false);
    return false;
}
```

---

## Integration with Xenia Vulkan Backend

The Xenia Vulkan backend already has excellent Turnip detection in `vulkan_device.cc:715-747`. The custom driver loader should be initialized **before** Xenia's Vulkan initialization:

**Initialization Order:**
```
1. Application startup
2. Load user preferences (which driver to use)
3. Call vk_load() with custom Turnip driver
4. Initialize Xenia's VulkanInstance
5. Xenia's VulkanDevice detects Turnip and enables optimizations
6. Game emulation begins
```

**Code Flow:**
```cpp
// In Android activity onCreate or native init
void initialize_vulkan_for_xenia() {
    // Step 1: Load custom driver if user selected one
    std::string custom_driver = get_user_selected_turnip_driver();
    if (!custom_driver.empty()) {
        // Set environment variables before loading
        if (should_force_gmem()) {
            setenv("TU_DEBUG", "gmem", 1);
        }

        vk_load_with_fallback(custom_driver.c_str());
    } else {
        vk_load("/system/lib64/libvulkan.so", false);
    }

    // Step 2: Initialize Xenia's Vulkan systems
    // Xenia will automatically detect Turnip and enable optimizations
    xenia_initialize();
}
```

---

## Recommended Turnip Driver Versions

Based on research findings from Eden emulator community:

### Adreno 840 (Snapdragon 8 Elite)
- **Recommended:** Mesa Turnip 26.0.0 R7 or R8
- **Download:** https://github.com/K11MCH1/AdrenoToolsDrivers/releases
- **Features:** Full Gen 8 support, VK_EXT_shader_tile_image

### Adreno 750 (Snapdragon 8 Gen 3)
- **Recommended:** Mesa Turnip 25.2.0 or 26.0.0
- **Features:** Vulkan 1.3 conformance, excellent FSI support

### Adreno 740 (Snapdragon 8 Gen 2)
- **Recommended:** Mesa Turnip 25.0.0 or 26.0.0
- **Features:** Solid compatibility, good performance

### Adreno 710/720 (Mid-range)
- **Recommended:** Mesa Turnip 24.3.0 or 25.3.0
- **Note:** Use `TU_DEBUG=gmem` environment variable
- **Features:** Basic support, may need fallback for complex games

---

## UI Recommendations

### Settings Screen Mockup

```
Settings → Graphics → Driver

(•) System Driver (Stock Qualcomm)
( ) Custom Turnip Driver

[If Custom Selected]
  Selected Driver: Mesa Turnip 26.0.0 R7
  [Choose Driver ZIP...]
  [Clear Custom Driver]

  Advanced Settings:
    ☑ Force GMEM Mode (for Adreno 710/720)
    ☐ Enable Debug Logging

  Driver Info:
    - Mesa Version: 26.0.0
    - Vulkan Version: 1.3.290
    - Extensions: 247 supported
```

### Per-Game Override

```
Game Properties → Performance

Driver Override:
  ( ) Use Global Setting
  (•) Custom Driver for This Game

  [If Custom]
    Driver: Mesa Turnip 25.0.0 R5 ▼

    Game-Specific Tuning:
      ☑ Force GMEM Mode
      ☐ Disable UBWC Compression
```

---

## Testing Checklist

Before releasing driver loading enhancements:

- [ ] Test loading Mesa Turnip 26.0.0 on Adreno 840
- [ ] Test loading Mesa Turnip 25.0.0 on Adreno 750
- [ ] Test fallback to system driver on load failure
- [ ] Verify environment variables are set before dlopen()
- [ ] Test per-game driver switching (reload required)
- [ ] Validate driver info detection
- [ ] Test on devices without Adreno (should gracefully handle)
- [ ] Verify no crashes when custom driver is corrupted/invalid
- [ ] Test driver unload/reload cycle
- [ ] Measure performance delta: system vs. Turnip

---

## Security Considerations

1. **ZIP Validation**: Verify Turnip driver ZIP integrity before extraction
   - Check file signatures
   - Validate library ELF headers
   - Scan for obvious malware patterns

2. **Path Validation**: Ensure custom driver paths are safe
   - No path traversal (..)
   - Limited to app's private storage
   - Use Android scoped storage APIs

3. **Permission Requirements**:
   - READ_EXTERNAL_STORAGE (for selecting driver ZIP)
   - App private storage for extracted driver

4. **User Warning**: Display warning when loading custom drivers:
   ```
   "Custom drivers are provided by the community and not officially
   supported. Use at your own risk. Malicious drivers could compromise
   your device security."

   [I Understand] [Cancel]
   ```

---

## References

- Eden Emulator Driver Support: https://edenemulator.com/best-gpu-drivers/
- Mesa Turnip Releases: https://github.com/K11MCH1/AdrenoToolsDrivers
- Android Custom Driver Loading: Android NDK Vulkan documentation
- Freedreno Debug Variables: Mesa source code comments

---

**Document Version:** 1.0
**Last Updated:** March 31, 2026
**Status:** Enhancement recommendations for future implementation
