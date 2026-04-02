// Created by aenu on 2025/7/21.
// SPDX-License-Identifier: WTFPL
#include <stdlib.h>
#include <jni.h>
#include <string>
#include <fstream>
#include <android/log.h>

#define HWTAG "ax360e_hwinfo"
#define HWLOG(...) __android_log_print(ANDROID_LOG_INFO, HWTAG, __VA_ARGS__)

// Read GPU name from sysfs (Qualcomm Adreno) without creating a VkInstance.
// Do NOT use Vulkan here: loading libvulkan.so triggers Adreno driver
// initialization that corrupts a static mutex (FORTIFY crash on debug builds).
extern "C" JNIEXPORT jstring JNICALL  Java_aenu_hardware_ProcessorInfo_gpu_1get_1physical_1device_1name_1vk(JNIEnv*  env,jclass cls){
    // Qualcomm Adreno: /sys/class/kgsl/kgsl-3d0/gpu_model
    std::ifstream f("/sys/class/kgsl/kgsl-3d0/gpu_model");
    if (f.is_open()) {
        std::string name;
        std::getline(f, name);
        if (!name.empty()) {
            HWLOG("GPU name from sysfs: %s", name.c_str());
            return env->NewStringUTF(name.c_str());
        }
    }
    // Fallback: try SoC ID
    std::ifstream f2("/sys/devices/soc0/soc_id");
    if (f2.is_open()) {
        std::string soc;
        std::getline(f2, soc);
        if (!soc.empty()) {
            HWLOG("SoC ID from sysfs: %s", soc.c_str());
            std::string name = "Adreno (SoC " + soc + ")";
            return env->NewStringUTF(name.c_str());
        }
    }
    HWLOG("sysfs failed, returning unknown");
    return env->NewStringUTF("Unknown GPU");
}
