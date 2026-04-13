// SPDX-License-Identifier: WTFPL
// Created by aenu on 2025/5/29.
//
#include "vkapi.h"

#include <android/dlext.h>
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <string>
#include <vector>

#define LOG_TAG "VulkanDriverLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define VKFN(func) PFN_##func func##_
#include "vksym.h"
#undef VKFN

extern "C" __attribute__((weak))
struct android_namespace_t* android_get_exported_namespace(const char* name);

#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

namespace {

static void* lib_handle = nullptr;

using GetAndroidNamespaceFn = android_namespace_t* (*)(const char* name);

struct AndroidHwModule;
struct AndroidHwDevice;
using AndroidHwOpenFn =
    int (*)(const AndroidHwModule* module, const char* id, AndroidHwDevice** device);
struct AndroidHwModuleMethods {
  AndroidHwOpenFn open;
};
struct AndroidHwModule {
  uint32_t tag;
  uint16_t module_api_version;
  uint16_t hal_api_version;
  const char* id;
  const char* name;
  const char* author;
  AndroidHwModuleMethods* methods;
  void* dso;
  uint64_t reserved[25];
};
struct AndroidHwDevice {
  uint32_t tag;
  uint32_t version;
  AndroidHwModule* module;
  uint64_t reserved[12];
  int (*close)(AndroidHwDevice* device);
};
struct AndroidHwVulkanDevice {
  AndroidHwDevice common;
  PFN_vkEnumerateInstanceExtensionProperties EnumerateInstanceExtensionProperties;
  PFN_vkCreateInstance CreateInstance;
  PFN_vkGetInstanceProcAddr GetInstanceProcAddr;
};

static AndroidHwDevice* hal_device = nullptr;

GetAndroidNamespaceFn ResolveAndroidGetExportedNamespace() {
  GetAndroidNamespaceFn fn_get_ns = &android_get_exported_namespace;
  if (fn_get_ns) {
    return fn_get_ns;
  }

  struct Candidate {
    const char* library;
    const char* symbol;
  };
  const Candidate kCandidates[] = {
      {"libdl.so", "android_get_exported_namespace"},
      {"libdl_android.so", "android_get_exported_namespace"},
      {"ld-android.so", "__loader_android_get_exported_namespace"},
      {nullptr, nullptr},
  };

  for (int i = 0; kCandidates[i].library; ++i) {
    void* lib = dlopen(kCandidates[i].library, RTLD_NOW | RTLD_NOLOAD);
    if (!lib) {
      lib = dlopen(kCandidates[i].library, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!lib) {
      continue;
    }
    auto fn =
        reinterpret_cast<GetAndroidNamespaceFn>(dlsym(lib, kCandidates[i].symbol));
    if (fn) {
      return fn;
    }
  }

  return nullptr;
}

std::string GetDirectoryName(const char* path) {
  if (!path || !path[0]) {
    return {};
  }
  std::string path_string(path);
  size_t separator = path_string.find_last_of('/');
  if (separator == std::string::npos) {
    return {};
  }
  return path_string.substr(0, separator);
}

void PreloadAndroidStubLibraries(const char* source_path) {
  std::string driver_dir = GetDirectoryName(source_path);
  if (driver_dir.empty()) {
    return;
  }
  std::string data_root = GetDirectoryName(driver_dir.c_str());
  if (data_root.empty()) {
    return;
  }

  const char* kStubLibraries[] = {
      "libcutils.so",      "libhardware.so", "libsync.so",   "libnativewindow.so",
      "libandroid.so",     "libutils.so",    "libbacktrace.so",
      "libunwindstack.so", "libhidlbase.so", "liblog.so",
  };

  std::string stub_dir = data_root + "/android_stub";
  for (const char* library_name : kStubLibraries) {
    std::string stub_path = stub_dir + "/" + library_name;
    dlerror();
    void* handle = dlopen(stub_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle) {
      continue;
    }
    dlopen(library_name, RTLD_NOW | RTLD_GLOBAL);
  }
}

int CreateMemFdCopy(const char* source_path) {
#ifdef __NR_memfd_create
  int source_fd = open(source_path, O_RDONLY | O_CLOEXEC);
  if (source_fd == -1) {
    return -1;
  }

  struct stat source_stat = {};
  fstat(source_fd, &source_stat);

  int memfd = int(syscall(__NR_memfd_create, "ax360e-vkapi", MFD_CLOEXEC));
  if (memfd == -1) {
    close(source_fd);
    return -1;
  }

  std::vector<uint8_t> buffer(64 * 1024);
  while (true) {
    ssize_t bytes_read = read(source_fd, buffer.data(), buffer.size());
    if (bytes_read <= 0) {
      break;
    }
    write(memfd, buffer.data(), size_t(bytes_read));
  }

  close(source_fd);
  lseek(memfd, 0, SEEK_SET);
  if (source_stat.st_size > 0) {
    ftruncate(memfd, source_stat.st_size);
  }
  return memfd;
#else
  return -1;
#endif
}

void* TryLoadCustomDriverFromMemFd(const char* source_path, android_namespace_t* ns,
                                   int dl_flags) {
  int memfd = CreateMemFdCopy(source_path);
  if (memfd == -1) {
    return nullptr;
  }

  void* handle = nullptr;
  if (ns) {
    android_dlextinfo extinfo = {};
    extinfo.flags = ANDROID_DLEXT_USE_LIBRARY_FD |
                    ANDROID_DLEXT_USE_NAMESPACE |
                    ANDROID_DLEXT_FORCE_LOAD;
    extinfo.library_fd = memfd;
    extinfo.library_namespace = ns;
    handle = android_dlopen_ext(source_path, dl_flags, &extinfo);
  } else {
    std::string fd_path = "/proc/self/fd/" + std::to_string(memfd);
    handle = dlopen(fd_path.c_str(), dl_flags);
  }

  close(memfd);
  return handle;
}

void ResetResolvedFunctions() {
#define VKFN(func) func##_ = nullptr
#include "vksym.h"
#undef VKFN
}

bool ValidateCriticalSymbols(const char* lib_path) {
  if (vkCreateInstance_ && vkDestroyInstance_ && vkEnumeratePhysicalDevices_) {
    return true;
  }

  LOGE(
      "Critical Vulkan symbols missing after loading %s: "
      "vkCreateInstance=%p vkDestroyInstance=%p vkEnumeratePhysicalDevices=%p",
      lib_path ? lib_path : "<null>", reinterpret_cast<void*>(vkCreateInstance_),
      reinterpret_cast<void*>(vkDestroyInstance_),
      reinterpret_cast<void*>(vkEnumeratePhysicalDevices_));
  return false;
}

}  // namespace

void vk_load(const char* lib_path, bool is_adreno_custom) {
  if (lib_handle) {
    LOGW("Vulkan library already loaded, skipping...");
    return;
  }

  if (!lib_path) {
    LOGE("vk_load called with null lib_path");
    return;
  }

  LOGI("vk_load: path=%s, custom=%d", lib_path, is_adreno_custom);

  int dl_flags = RTLD_NOW | RTLD_GLOBAL;
  if (is_adreno_custom) {
    PreloadAndroidStubLibraries(lib_path);
    lib_handle = TryLoadCustomDriverFromMemFd(lib_path, nullptr, dl_flags);
    if (!lib_handle) {
      GetAndroidNamespaceFn fn_get_ns = ResolveAndroidGetExportedNamespace();
      const char* ns_names[] = {"sphal", "vndk", "default", nullptr};
      if (fn_get_ns) {
        for (int i = 0; ns_names[i] && !lib_handle; ++i) {
          android_namespace_t* ns = fn_get_ns(ns_names[i]);
          if (!ns) {
            continue;
          }
          lib_handle = TryLoadCustomDriverFromMemFd(lib_path, ns, dl_flags);
          if (!lib_handle) {
            android_dlextinfo extinfo = {};
            extinfo.flags = ANDROID_DLEXT_USE_NAMESPACE;
            extinfo.library_namespace = ns;
            lib_handle = android_dlopen_ext(lib_path, dl_flags, &extinfo);
          }
        }
      }
    }
    if (!lib_handle) {
      lib_handle = dlopen(lib_path, dl_flags);
    }
  } else {
    lib_handle = dlopen(lib_path, dl_flags | RTLD_NODELETE);
  }

  if (!lib_handle ||
      reinterpret_cast<uintptr_t>(lib_handle) == static_cast<uintptr_t>(-1)) {
    LOGE("Failed to load Vulkan library: %s", dlerror());
    lib_handle = nullptr;
    ResetResolvedFunctions();
    return;
  }

  LOGI("Library loaded at %p. Resolving entry points.", lib_handle);

  PFN_vkGetInstanceProcAddr get_proc_addr =
      reinterpret_cast<PFN_vkGetInstanceProcAddr>(
          dlsym(lib_handle, "vkGetInstanceProcAddr"));
  if (!get_proc_addr) {
    get_proc_addr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
        dlsym(lib_handle, "vk_icdGetInstanceProcAddr"));
  }

  if (!get_proc_addr) {
    LOGI("Trying Android HAL (HMI) resolution...");
    auto* hmi = static_cast<AndroidHwModule*>(dlsym(lib_handle, "HMI"));
    if (hmi && hmi->methods && hmi->methods->open) {
      if (hmi->methods->open(hmi, "vk0", &hal_device) == 0 && hal_device) {
        auto* vdev = reinterpret_cast<AndroidHwVulkanDevice*>(hal_device);
        get_proc_addr = vdev->GetInstanceProcAddr;
        LOGI("Found GPA via HMI at %p", reinterpret_cast<void*>(get_proc_addr));
      }
    }
  }

  if (!get_proc_addr) {
    LOGE("No GetInstanceProcAddr found.");
  } else {
    LOGI("Using GetInstanceProcAddr at %p", reinterpret_cast<void*>(get_proc_addr));
  }

#define VKFN(func)                                                            \
  func##_ = reinterpret_cast<PFN_##func>(dlsym(lib_handle, #func));           \
  if (!func##_ && get_proc_addr) {                                            \
    func##_ = reinterpret_cast<PFN_##func>(get_proc_addr(nullptr, #func));    \
  }                                                                           \
  if (!func##_) {                                                             \
    LOGW("Could not resolve %s", #func);                                      \
  }
#include "vksym.h"
#undef VKFN

  if (!ValidateCriticalSymbols(lib_path)) {
    if (hal_device) {
      hal_device->close(hal_device);
      hal_device = nullptr;
    }
    dlclose(lib_handle);
    lib_handle = nullptr;
    ResetResolvedFunctions();
    return;
  }

  LOGI("Successfully loaded Vulkan library and functions");
}

void vk_unload() {
  if (hal_device) {
    hal_device->close(hal_device);
    hal_device = nullptr;
  }
  if (lib_handle) {
    dlclose(lib_handle);
    lib_handle = nullptr;
  }
  ResetResolvedFunctions();
  LOGI("Vulkan library unloaded");
}

bool vk_is_loaded() { return lib_handle != nullptr; }
