
// SPDX-License-Identifier: WTFPL
// Created by aenu on 2025/5/29.
//
#include <dlfcn.h>
#include <string>
#include <cstdlib>
#include <android/log.h>
#include <android/dlext.h>
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

    void set_turnip_environment_variables() {
        // Essential Mesa Turnip environment variables for Adreno performance
        // and correctness.
        setenv("TU_DEBUG", "sysmem,gmem", 1);
        setenv("FD_DEV_FEATURES", "tile_image", 1);
        setenv("MESA_VK_WSI_PRESENT_MODE", "mailbox", 1);
        setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 0); // fallback if needed
        LOGI("Mesa Turnip environment variables configured");
    }
}

void vk_load(const char* lib_path, bool is_adreno_custom) {
    if (lib_handle) {
        LOGW("Vulkan library already loaded, skipping...");
        return;
    }

    if (!lib_path) {
        LOGE("vk_load called with null lib_path");
        return;
    }

    LOGI("Loading Vulkan library from path: %s (custom=%d)", lib_path, is_adreno_custom);

    // Set environment variables before loading driver
    if (is_adreno_custom) {
        LOGI("Loading custom Adreno driver (likely Mesa Turnip)");
        set_turnip_environment_variables();
    }

    // On Android 12+, we need to satisfy system dependencies like libcutils.so
    // which are blocked in the default app namespace. By pre-loading our dummy
    // stubs, we satisfy the dynamic linker.
    if (is_adreno_custom) {
        const char* dummy_libs[] = {"libcutils.so", "libutils.so", "libhardware.so", "libhidlbase.so"};
        for (const char* lib : dummy_libs) {
            dlopen(lib, RTLD_NOW | RTLD_GLOBAL);
        }
    }

    lib_handle = dlopen(lib_path, RTLD_NOW | RTLD_GLOBAL);

    if (!lib_handle) {
        const char* err = dlerror();
        LOGE("Failed to load %s: %s", lib_path, err ? err : "unknown error");
        return;
    }

#define VKFN(func)                                                             \
    func##_ = (PFN_##func)dlsym(lib_handle, #func);                            \
    if (!func##_) {                                                            \
        LOGW("Failed to load Vulkan function: %s", #func);                     \
    }

#include "vksym.h"
#undef VKFN

    if (!vkCreateInstance_ || !vkDestroyInstance_ || !vkEnumeratePhysicalDevices_) {
        LOGE(
            "Critical Vulkan symbols missing after loading %s: "
            "vkCreateInstance=%p vkDestroyInstance=%p vkEnumeratePhysicalDevices=%p",
            lib_path, reinterpret_cast<void*>(vkCreateInstance_),
            reinterpret_cast<void*>(vkDestroyInstance_),
            reinterpret_cast<void*>(vkEnumeratePhysicalDevices_));
        dlclose(lib_handle);
        lib_handle = nullptr;
#define VKFN(func) func##_ = nullptr
#include "vksym.h"
#undef VKFN
        return;
    }

    LOGI("Successfully loaded Vulkan library and functions");
    
    if (is_adreno_custom) {
        LOGI("Custom Adreno driver loaded - TBDR optimizations should be active");
    }
}

void vk_unload() {
    if (!lib_handle) {
        return;
    }

    dlclose(lib_handle);
    lib_handle = nullptr;

#define VKFN(func) func##_=nullptr
#include "vksym.h"
#undef VKFN

    LOGI("Vulkan library unloaded successfully");
}

bool vk_is_loaded() {
    return lib_handle != nullptr;
}
