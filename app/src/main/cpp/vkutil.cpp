
// SPDX-License-Identifier: WTFPL
// Created by aenu on 2025/5/31.
//

 #include "vkutil.h"

#include <algorithm>
#include <android/log.h>

#define LOG_TAG "vkutil"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
 #define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
std::optional<VkInstance> vk_create_instance(const char * name) {
        if (!vkCreateInstance_) {
            LOGE("vkCreateInstance function pointer is null - Vulkan library not loaded?");
            return std::nullopt;
        }

        VkApplicationInfo appinfo = {};
        appinfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        appinfo.pNext = nullptr;
        appinfo.pApplicationName = name;
        appinfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
        appinfo.pEngineName = name;
        appinfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
        appinfo.apiVersion = VK_API_VERSION_1_1;

        std::vector<const char*> extensions = {
            VK_KHR_SURFACE_EXTENSION_NAME,
            VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
            VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
        };

        // Check for portability enumeration extension
        uint32_t ext_count = 0;
        vkEnumerateInstanceExtensionProperties_(nullptr, &ext_count, nullptr);
        std::vector<VkExtensionProperties> available_exts(ext_count);
        vkEnumerateInstanceExtensionProperties_(nullptr, &ext_count, available_exts.data());

        bool portability_supported = false;
        for (const auto& ext : available_exts) {
            if (std::string(ext.extensionName) == "VK_KHR_portability_enumeration") {
                extensions.push_back("VK_KHR_portability_enumeration");
                portability_supported = true;
                break;
            }
        }

        VkInstanceCreateInfo inst_create_info = {};
        inst_create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        inst_create_info.pApplicationInfo = &appinfo;
        inst_create_info.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
        inst_create_info.ppEnabledExtensionNames = extensions.data();
        
        if (portability_supported) {
            inst_create_info.flags |= VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
        }

        VkInstance inst;
        VkResult result = vkCreateInstance_(&inst_create_info, nullptr, &inst);
        if (result != VK_SUCCESS) {
            LOGE("vkCreateInstance failed with result: %d", result);
            return std::nullopt;
        }
        return inst;
}

void vk_destroy_instance(VkInstance inst) {
    if (vkDestroyInstance_ && inst != VK_NULL_HANDLE) {
        vkDestroyInstance_(inst, nullptr);
    }
}

int vk_get_physical_device_count(VkInstance inst){
    if (!vkEnumeratePhysicalDevices_) return 0;
    uint32_t count = 0;
    if (vkEnumeratePhysicalDevices_(inst, &count, nullptr) != VK_SUCCESS) {
        LOGE("vkEnumeratePhysicalDevices failed");
        return 0;
    }
    return count;
}
std::optional<VkPhysicalDevice> vk_get_physical_device(VkInstance inst, int index){
    uint32_t count = vk_get_physical_device_count(inst);
    if (count == 0 || index < 0 || static_cast<uint32_t>(index) >= count)
        return std::nullopt;
    std::vector<VkPhysicalDevice> devices(count);
    if (vkEnumeratePhysicalDevices_(inst, &count, devices.data()) != VK_SUCCESS) {
        LOGE("vkEnumeratePhysicalDevices (fetch) failed");
        return std::nullopt;
    }
    if (static_cast<uint32_t>(index) >= count)
        return std::nullopt;
    return devices[index];
}

 VkPhysicalDeviceProperties vk_get_physical_device_properties(VkPhysicalDevice dev){
    VkPhysicalDeviceProperties props = {};
    if (vkGetPhysicalDeviceProperties_) {
        vkGetPhysicalDeviceProperties_(dev, &props);
    } else {
        LOGE("vkGetPhysicalDeviceProperties function pointer is null");
    }
    return props;
}

VkPhysicalDeviceLimits vk_get_physical_device_limits(VkPhysicalDevice dev){
    return  vk_get_physical_device_properties(dev).limits;
}

std::vector<VkExtensionProperties> vk_get_physical_device_extension_properties(VkPhysicalDevice dev){
    if (!vkEnumerateDeviceExtensionProperties_) return {};
    uint32_t count = 0;
    if (vkEnumerateDeviceExtensionProperties_(dev, nullptr, &count, nullptr) != VK_SUCCESS) {
        LOGE("vkEnumerateDeviceExtensionProperties (count) failed");
        return {};
    }
    if (count == 0) return {};
    std::vector<VkExtensionProperties> props(count);
    if (vkEnumerateDeviceExtensionProperties_(dev, nullptr, &count, props.data()) != VK_SUCCESS) {
        LOGE("vkEnumerateDeviceExtensionProperties (fetch) failed");
        return {};
    }
    std::sort(props.begin(), props.end(), [](const VkExtensionProperties& a, const VkExtensionProperties& b) {
         return strcmp(a.extensionName, b.extensionName) < 0;
    });
    return props;
}

int vk_get_queue_family_properties_count(VkPhysicalDevice dev) {
    if (!vkGetPhysicalDeviceQueueFamilyProperties_) return 0;
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties_(dev, &count, nullptr);
    return count;
}

std::optional<VkQueueFamilyProperties> vk_get_queue_family_properties(VkPhysicalDevice dev,int index) {
    uint32_t count= vk_get_queue_family_properties_count( dev);
    if (count == 0 || index < 0 || static_cast<uint32_t>(index) >= count)
        return std::nullopt;
    std::vector<VkQueueFamilyProperties> props(count);
    vkGetPhysicalDeviceQueueFamilyProperties_(dev, &count, props.data());
    return props[index];
}

std::optional<VkDevice> vk_create_device(VkPhysicalDevice pdev,uint32_t queueFamilyIndex,VkQueueFamilyProperties  props) {
    if (!vkCreateDevice_) {
        LOGE("vkCreateDevice function pointer is null");
        return std::nullopt;
    }
    
    // We only need 1 queue for this test
    uint32_t queueCount = 1;
    float queuePriorities[1] = {1.0f};

    VkDeviceQueueCreateInfo queue_create_info = {
            .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .queueFamilyIndex = queueFamilyIndex,
            .queueCount = queueCount,
            .pQueuePriorities = queuePriorities
    };

    const char* extensions[] = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME
    };

    VkDeviceCreateInfo device_create_info = {
            .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .queueCreateInfoCount = 1,
            .pQueueCreateInfos = &queue_create_info,
            .enabledLayerCount = 0,
            .ppEnabledLayerNames = nullptr,
            .enabledExtensionCount = 1,
            .ppEnabledExtensionNames = extensions,
            .pEnabledFeatures = nullptr
    };
    VkDevice dev;
    VkResult res = vkCreateDevice_(pdev, &device_create_info, nullptr, &dev);
    if(res != VK_SUCCESS){
         LOGE("vkCreateDevice failed: %d", res);
         return std::nullopt;
    }
    return dev;
}

void vk_destroy_device(VkDevice dev) {
    if (vkDestroyDevice_ && dev != VK_NULL_HANDLE) {
        vkDestroyDevice_(dev, nullptr);
    }
}

std::optional<VkDescriptorSetLayout> vk_create_descriptor_set_layout(VkDevice dev,const std::vector<VkDescriptorSetLayoutBinding>& binds) {
    if (!vkCreateDescriptorSetLayout_) {
        LOGE("vkCreateDescriptorSetLayout function pointer is null");
        return std::nullopt;
    }
    VkDescriptorSetLayoutCreateInfo descriptor_set_layout_create_info = {
            .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .bindingCount = static_cast<uint32_t>(binds.size()),
            .pBindings = binds.data()
    };
    VkDescriptorSetLayout layout;
    if(vkCreateDescriptorSetLayout_( dev, &descriptor_set_layout_create_info, nullptr, &layout)!= VK_SUCCESS){
        LOGE( "create descriptor set layout failed");
        return std::nullopt;
    }
     return layout;
 }

  void vk_destroy_descriptor_set_layout(VkDevice dev,VkDescriptorSetLayout layout) {
     if (vkDestroyDescriptorSetLayout_ && layout != VK_NULL_HANDLE) {
         vkDestroyDescriptorSetLayout_(dev,layout, nullptr);
     }
 }

std::optional<VkPipelineLayout> vk_create_pipeline_layout(VkDevice dev,VkDescriptorSetLayout descriptor_set_layout) {
    if (!vkCreatePipelineLayout_) {
        LOGE("vkCreatePipelineLayout function pointer is null");
        return std::nullopt;
    }
    VkPipelineLayoutCreateInfo pipeline_layout_create_info = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .setLayoutCount = 1,
            .pSetLayouts = &descriptor_set_layout,
            .pushConstantRangeCount = 0,
            .pPushConstantRanges = nullptr
    };
     VkPipelineLayout layout;
     if(vkCreatePipelineLayout_(dev, &pipeline_layout_create_info, nullptr, &layout)!= VK_SUCCESS){
          LOGE( "create pipeline layout failed");
         return std::nullopt;
     }
     return layout;
}

std::optional<VkShaderModule> vk_create_shader_module(VkDevice dev,const std::vector<uint32_t>& code) {
    if (!vkCreateShaderModule_) {
        LOGE("vkCreateShaderModule function pointer is null");
        return std::nullopt;
    }
    if (code.empty()) {
        LOGE("Shader code is empty");
        return std::nullopt;
    }
     VkShaderModuleCreateInfo shader_module_create_info = {
            .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .codeSize = code.size() * sizeof(uint32_t),
            .pCode = code.data()
    };
     VkShaderModule module;
     if(vkCreateShaderModule_(dev, &shader_module_create_info, nullptr, &module)!= VK_SUCCESS){
          LOGE( "create shader module failed");
         return std::nullopt;
     }
     return module;
}

void vk_destroy_shader_module(VkDevice dev,VkShaderModule module){
    if (vkDestroyShaderModule_ && module != VK_NULL_HANDLE) {
        vkDestroyShaderModule_(dev, module, nullptr);
    }
}

void vk_destroy_pipeline_layout(VkDevice dev,VkPipelineLayout layout) {
    if (vkDestroyPipelineLayout_ && layout != VK_NULL_HANDLE) {
        vkDestroyPipelineLayout_(dev, layout, nullptr);
    }
}

std::optional <VkPipeline> vk_create_compute_pipeline(VkDevice dev,VkPipelineLayout layout,VkShaderModule module) {
    if (!vkCreateComputePipelines_) {
        LOGE("vkCreateComputePipelines function pointer is null");
        return std::nullopt;
    }
    VkComputePipelineCreateInfo compute_pipeline_create_info = {
            .sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .stage = {
                    .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                    .pNext = nullptr,
                    .flags = 0,
                    .stage = VK_SHADER_STAGE_COMPUTE_BIT,
                    .module = module,
                    .pName = "main",
            },
            .layout = layout,
            .basePipelineHandle= VK_NULL_HANDLE,
            .basePipelineIndex = -1,
     };
    VkPipeline pipeline;
     if(vkCreateComputePipelines_(dev, VK_NULL_HANDLE, 1, &compute_pipeline_create_info, nullptr, &pipeline)!= VK_SUCCESS){
          LOGE( "create pipeline failed");
        return std::nullopt;
    }
     return pipeline;
}

void  vk_destroy_pipeline(VkDevice dev,VkPipeline pipeline) {
    if (vkDestroyPipeline_ && pipeline != VK_NULL_HANDLE) {
        vkDestroyPipeline_(dev, pipeline, nullptr);
    }
}