
// SPDX-License-Identifier: WTFPL
// Created by aenu on 2025/5/29.
//
#include <dlfcn.h>
#include <string>
#include <cstdlib>
#include <android/log.h>
#include "vkapi.h"

#define LOG_TAG "VulkanDriverLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define VKFN(func) PFN_##func func##_
#include "vksym.h"
#undef VKFN

namespace {
    void* lib_handle = nullptr;

    // Set Turnip-specific environment variables for optimal performance
    void set_turnip_environment_variables() {
        // Force GMEM mode for better compatibility on mid-range Adreno GPUs (710/720)
        // This can improve stability on devices that struggle with complex rendering
        // Note: Can be overridden by user preferences in the future
        const char* existing_tu_debug = std::getenv("TU_DEBUG");
        if (!existing_tu_debug) {
            // Only set if not already configured
            // setenv("TU_DEBUG", "gmem", 0);  // Disabled by default, enable via settings
            LOGI("TU_DEBUG not set, using driver defaults");
        } else {
            LOGI("TU_DEBUG already set to: %s", existing_tu_debug);
        }

        // Enable specific Freedreno features for compatibility (OneUI devices)
        // Uncomment if needed for specific devices
        // setenv("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1", 0);

        // For debugging (disable in production)
        // setenv("FD_MESA_DEBUG", "1", 0);
        // setenv("MESA_DEBUG", "1", 0);
    }
}

void vk_load(const char* lib_path, bool is_adreno_custom) {
    if (lib_handle) {
        LOGW("Vulkan library already loaded, ignoring duplicate load request");
        return;
    }

    // Set environment variables before loading driver
    if (is_adreno_custom) {
        LOGI("Loading custom Adreno driver (likely Mesa Turnip)");
        set_turnip_environment_variables();
    } else {
        LOGI("Loading system Vulkan driver: %s", lib_path);
    }

    lib_handle = dlopen(lib_path, RTLD_NOW);

    if (!lib_handle) {
        LOGE("Failed to load Vulkan library from %s: %s", lib_path, dlerror());
        return;
    }

    LOGI("Successfully loaded Vulkan library from: %s", lib_path);

    // Load function pointers
#define VKFN(func) func##_=reinterpret_cast<PFN_##func>(dlsym(lib_handle, #func))
#include "vksym.h"
#undef VKFN

    if (is_adreno_custom) {
        LOGI("Custom Adreno driver loaded - TBDR optimizations should be active");
    }
}

void vk_unload() {
    if (!lib_handle) {
        LOGW("No Vulkan library loaded, ignoring unload request");
        return;
    }

    LOGI("Unloading Vulkan library");
    dlclose(lib_handle);
    lib_handle = nullptr;

#define VKFN(func) func##_=nullptr
#include "vksym.h"
#undef VKFN

    LOGI("Vulkan library unloaded successfully");
}