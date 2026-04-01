// Created by aenu on 2025/7/21.
// SPDX-License-Identifier: WTFPL
#include <stdlib.h>
#include <jni.h>
#include <vector>
#include <vulkan/vulkan.h>

extern "C" JNIEXPORT jstring JNICALL  Java_aenu_hardware_ProcessorInfo_gpu_1get_1physical_1device_1name_1vk(JNIEnv*  env,jclass cls){

    VkApplicationInfo appinfo = {};
    appinfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appinfo.pNext = nullptr;
    appinfo.pApplicationName = "aps3e-cfg-test";
    appinfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appinfo.pEngineName = "nul";
    appinfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appinfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo inst_create_info = {};
    inst_create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    inst_create_info.pApplicationInfo = &appinfo;

    VkInstance inst;
    if (vkCreateInstance(&inst_create_info, nullptr, &inst)!= VK_SUCCESS) {
        return nullptr;
    }

    uint32_t physicalDeviceCount = 0;
    if (vkEnumeratePhysicalDevices(inst, &physicalDeviceCount, nullptr) != VK_SUCCESS || physicalDeviceCount == 0) {
        vkDestroyInstance(inst, nullptr);
        return nullptr;
    }

    std::vector<VkPhysicalDevice> physicalDevices(physicalDeviceCount);
    if (vkEnumeratePhysicalDevices(inst, &physicalDeviceCount, physicalDevices.data()) != VK_SUCCESS) {
        vkDestroyInstance(inst, nullptr);
        return nullptr;
    }

    if(physicalDevices.empty()){
        vkDestroyInstance(inst, nullptr);
        return nullptr;
    }

    VkPhysicalDeviceProperties physicalDeviceProperties;
    vkGetPhysicalDeviceProperties(physicalDevices[0], &physicalDeviceProperties);

    // Copy deviceName into a Java string before destroying the instance.
    // VkPhysicalDeviceProperties is a value type; deviceName is stored directly
    // in the struct as a char array, not as a pointer to driver-internal memory.
    jstring result = env->NewStringUTF(physicalDeviceProperties.deviceName);

    vkDestroyInstance(inst, nullptr);

    return result;
}
