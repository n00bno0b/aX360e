/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2025 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include "xenia/ui/vulkan/vulkan_instance.h"

#include <sstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "xenia/base/cvar.h"
#include "xenia/base/logging.h"
#include "xenia/base/platform.h"
#include "xenia/ui/vulkan/vulkan_presenter.h"

#if XE_PLATFORM_LINUX
#include <dlfcn.h>
#if XE_PLATFORM_ANDROID || XE_PLATFORM_AX360E
#include <android/dlext.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
// android_get_exported_namespace is a bionic private API exported from
// libdl.so but NOT declared in public NDK headers. Declare it ourselves as a
// weak symbol so: (a) the compiler accepts the call, (b) if the dynamic linker
// can resolve it we get a real function pointer, and (c) if it can't we get
// NULL and fall back to an explicit dlopen("libdl.so") + dlsym lookup.
extern "C" __attribute__((weak))
struct android_namespace_t* android_get_exported_namespace(const char* name);
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif
#endif
#elif XE_PLATFORM_WIN32
#include "xenia/base/platform_win.h"
#endif

DEFINE_bool(
    vulkan_log_debug_messages, true,
    "Write Vulkan VK_EXT_debug_utils messages to the Xenia log, as opposed to "
    "the OS debug output.",
    "Vulkan");

namespace xe {
namespace ui {
namespace vulkan {

#if XE_PLATFORM_ANDROID || XE_PLATFORM_AX360E
namespace {

using GetAndroidNamespaceFn = android_namespace_t* (*)(const char* name);

struct AndroidHwModule;
struct AndroidHwDevice;

using AndroidHwOpenFn = int (*)(const AndroidHwModule* module, const char* id,
                                AndroidHwDevice** device);

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
  // On LP64 (AArch64), the reserved array is uint64_t[32-7] = uint64_t[25]
  // to pad the struct to 32*8=256 bytes.  On ILP32 it would be uint32_t[25].
  // We always target LP64 here so use uint64_t.
  uint64_t reserved[32 - 7];
};

struct AndroidHwDevice {
  uint32_t tag;
  uint32_t version;
  AndroidHwModule* module;
  // On LP64 the reserved field comes BEFORE close, and is uint64_t[12] = 96 B.
  // Total hw_device_t size: 4+4+8 + 96 + 8 = 120 bytes.
  uint64_t reserved[12];
  int (*close)(AndroidHwDevice* device);
};

struct AndroidHwVulkanModule {
  AndroidHwModule common;
};

struct AndroidHwVulkanDevice {
  AndroidHwDevice common;
  PFN_vkEnumerateInstanceExtensionProperties
      EnumerateInstanceExtensionProperties;
  PFN_vkCreateInstance CreateInstance;
  PFN_vkGetInstanceProcAddr GetInstanceProcAddr;
};

struct AndroidHalVulkanFunctions {
  PFN_vkEnumerateInstanceExtensionProperties
      EnumerateInstanceExtensionProperties = nullptr;
  PFN_vkCreateInstance CreateInstance = nullptr;
  PFN_vkGetInstanceProcAddr GetInstanceProcAddr = nullptr;
};

GetAndroidNamespaceFn ResolveAndroidGetExportedNamespace() {
  // Check the compile-time weak symbol first (resolved by the NDK linker).
  GetAndroidNamespaceFn fn_get_ns = &android_get_exported_namespace;
  if (fn_get_ns) {
    return fn_get_ns;
  }
  // On Android 12+ the public symbol lives in libdl_android.so rather than
  // libdl.so.  On Android 12+ the adrenotools approach also finds a
  // __loader_-prefixed version in ld-android.so (the actual dynamic linker).
  // Try all three, with RTLD_NOLOAD first to avoid unnecessary library loads.
  struct {
    const char* lib;
    const char* sym;
  } kCandidates[] = {
      {"libdl.so",         "android_get_exported_namespace"},
      {"libdl_android.so", "android_get_exported_namespace"},
      // bionic/linker exports __loader_ prefixed versions that apps can reach
      // even when the public wrappers are stripped or namespaced away.
      {"ld-android.so",    "__loader_android_get_exported_namespace"},
      {nullptr,            nullptr},
  };
  for (int i = 0; kCandidates[i].lib; i++) {
    void* lib = dlopen(kCandidates[i].lib, RTLD_NOW | RTLD_NOLOAD);
    if (!lib) {
      lib = dlopen(kCandidates[i].lib, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!lib) {
      continue;
    }
    auto fn = reinterpret_cast<GetAndroidNamespaceFn>(
        dlsym(lib, kCandidates[i].sym));
    if (fn) {
      XELOGI("Resolved android_get_exported_namespace via {}:{}",
             kCandidates[i].lib, kCandidates[i].sym);
      return fn;
    }
  }
  return nullptr;
}

int OpenReadOnlyNoInterrupt(const char* path) {
  int fd;
  do {
    fd = open(path, O_RDONLY | O_CLOEXEC);
  } while (fd == -1 && errno == EINTR);
  return fd;
}

bool WriteAllNoInterrupt(int fd, const uint8_t* data, size_t size) {
  while (size > 0) {
    ssize_t written;
    do {
      written = write(fd, data, size);
    } while (written == -1 && errno == EINTR);
    if (written <= 0) {
      return false;
    }
    data += written;
    size -= size_t(written);
  }
  return true;
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
      "libcutils.so",
      "libhardware.so",
      "libsync.so",
      "libnativewindow.so",
      "libandroid.so",
      "libutils.so",
      "libbacktrace.so",
      "libunwindstack.so",
      "libhidlbase.so",
      "liblog.so",
  };

  std::string stub_dir = data_root + "/android_stub";
  for (const char* library_name : kStubLibraries) {
    std::string stub_path = stub_dir + "/" + library_name;
    XELOGI("Attempting Android stub preload from explicit path: {}", stub_path);
    dlerror();
    void* handle = dlopen(stub_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle) {
      XELOGI("Preloaded Android stub library from explicit path: {}",
             stub_path);
      continue;
    }
    const char* stub_error = dlerror();

    XELOGW(
        "Explicit-path preload failed for '{}' (error: {}), attempting "
        "packaged soname fallback",
        stub_path, stub_error ? stub_error : "unknown error");
    dlerror();
    handle = dlopen(library_name, RTLD_NOW | RTLD_GLOBAL);
    if (handle) {
      XELOGI("Preloaded Android stub library from packaged soname fallback: {}",
             library_name);
    } else {
      const char* packaged_error = dlerror();
      XELOGW(
          "Failed to preload Android stub library '{}' (packaged soname "
          "error: {}, file-path error: {})",
          stub_path, packaged_error ? packaged_error : "unknown error",
          stub_error ? stub_error : "unknown error");
    }
  }
}

int CreateMemFdCopy(const char* source_path) {
#ifdef __NR_memfd_create
  int source_fd = OpenReadOnlyNoInterrupt(source_path);
  if (source_fd == -1) {
    XELOGW("Failed to open custom driver '{}' for memfd copy: {}",
           source_path, strerror(errno));
    return -1;
  }

  struct stat source_stat = {};
  if (fstat(source_fd, &source_stat) != 0) {
    XELOGW("Failed to stat custom driver '{}' for memfd copy: {}", source_path,
           strerror(errno));
    close(source_fd);
    return -1;
  }

  int memfd = int(syscall(__NR_memfd_create, "ax360e-turnip", MFD_CLOEXEC));
  if (memfd == -1) {
    XELOGW("memfd_create failed for custom driver '{}': {}", source_path,
           strerror(errno));
    close(source_fd);
    return -1;
  }

  std::vector<uint8_t> buffer(64 * 1024);
  bool copied = true;
  while (copied) {
    ssize_t bytes_read;
    do {
      bytes_read = read(source_fd, buffer.data(), buffer.size());
    } while (bytes_read == -1 && errno == EINTR);

    if (bytes_read == 0) {
      break;
    }
    if (bytes_read < 0 ||
        !WriteAllNoInterrupt(memfd, buffer.data(), size_t(bytes_read))) {
      XELOGW("Failed to copy custom driver '{}' into memfd: {}", source_path,
             strerror(errno));
      copied = false;
      break;
    }
  }

  close(source_fd);

  if (!copied || lseek(memfd, 0, SEEK_SET) == -1) {
    if (copied) {
      XELOGW("Failed to rewind memfd for custom driver '{}': {}", source_path,
             strerror(errno));
    }
    close(memfd);
    return -1;
  }

  if (source_stat.st_size > 0 && ftruncate(memfd, source_stat.st_size) != 0) {
    XELOGW("Failed to size memfd for custom driver '{}': {}", source_path,
           strerror(errno));
    close(memfd);
    return -1;
  }

  return memfd;
#else
  XELOGW("memfd_create is unavailable on this build; cannot retry custom "
         "driver '{}' from memfd",
         source_path);
  return -1;
#endif
}

void* TryLoadCustomDriverFromMemFd(const char* source_path,
                                   android_namespace_t* ns,
                                   const char* ns_name) {
  int memfd = CreateMemFdCopy(source_path);
  if (memfd == -1) {
    return nullptr;
  }

  void* handle = nullptr;

  if (ns) {
    // Namespace-backed path: use android_dlopen_ext so the linker resolves
    // the driver's dependencies from the sphal/vndk system namespace.
    android_dlextinfo extinfo = {};
    extinfo.flags = ANDROID_DLEXT_USE_LIBRARY_FD |
                    ANDROID_DLEXT_USE_NAMESPACE |
                    ANDROID_DLEXT_FORCE_LOAD;
    extinfo.library_fd = memfd;
    extinfo.library_namespace = ns;

    dlerror();
    handle = android_dlopen_ext(source_path, RTLD_NOW | RTLD_GLOBAL, &extinfo);
    if (handle) {
      XELOGI("Custom driver loaded from memfd via '{}' namespace", ns_name);
    } else {
      XELOGW("android_dlopen_ext from memfd via '{}' namespace failed: {}",
             ns_name, dlerror());
    }
  } else {
    // No namespace available. android_dlopen_ext without USE_NAMESPACE crashes
    // on Android 12+ (Adreno 830) when the .so has unresolvable HAL deps.
    // Use plain dlopen via the /proc/self/fd/<fd> path which is safe and
    // works if the stub libraries were already pre-loaded into RTLD_GLOBAL.
    std::string fd_path = "/proc/self/fd/" + std::to_string(memfd);
    dlerror();
    handle = dlopen(fd_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle) {
      XELOGI("Custom driver loaded from memfd via /proc/self/fd path");
    } else {
      XELOGW("dlopen via /proc/self/fd failed: {}", dlerror());
    }
  }

  close(memfd);
  return handle;
}

bool TryLoadAndroidHalVulkanFunctions(void* loader, void** hal_device_out,
                                      AndroidHalVulkanFunctions* functions_out) {
  if (hal_device_out) {
    *hal_device_out = nullptr;
  }
  if (functions_out) {
    *functions_out = {};
  }

  auto* hal_module = reinterpret_cast<AndroidHwVulkanModule*>(
      dlsym(loader, "HMI"));
  if (!hal_module) {
    return false;
  }

  if (!hal_module->common.methods || !hal_module->common.methods->open) {
    XELOGW("Android HAL Vulkan module exported HMI but has no open method");
    return false;
  }

  AndroidHwDevice* opened_device = nullptr;
  int open_result =
      hal_module->common.methods->open(&hal_module->common, "vk0",
                                       &opened_device);
  if (open_result != 0 || !opened_device) {
    XELOGW("Android HAL Vulkan module open(vk0) failed with {}",
           open_result);
    return false;
  }

  auto* hal_vulkan_device =
      reinterpret_cast<AndroidHwVulkanDevice*>(opened_device);
  if (!hal_vulkan_device->CreateInstance ||
      !hal_vulkan_device->EnumerateInstanceExtensionProperties ||
      !hal_vulkan_device->GetInstanceProcAddr) {
    XELOGW(
        "Android HAL Vulkan device missing required callbacks "
        "(CreateInstance={}, EnumerateInstanceExtensionProperties={}, "
        "GetInstanceProcAddr={})",
        hal_vulkan_device->CreateInstance != nullptr,
        hal_vulkan_device->EnumerateInstanceExtensionProperties != nullptr,
        hal_vulkan_device->GetInstanceProcAddr != nullptr);
    if (opened_device->close) {
      opened_device->close(opened_device);
    }
    return false;
  }

  if (hal_device_out) {
    *hal_device_out = opened_device;
  }
  if (functions_out) {
    functions_out->CreateInstance = hal_vulkan_device->CreateInstance;
    functions_out->EnumerateInstanceExtensionProperties =
        hal_vulkan_device->EnumerateInstanceExtensionProperties;
    functions_out->GetInstanceProcAddr = hal_vulkan_device->GetInstanceProcAddr;
  }

  XELOGI(
      "Using Android HAL Vulkan entry points via HMI/vk0 "
      "(CreateInstance + EnumerateInstanceExtensionProperties + "
      "GetInstanceProcAddr)");
  return true;
}

void CloseAndroidHalDevice(void* hal_device) {
  if (!hal_device) {
    return;
  }

  auto* device = reinterpret_cast<AndroidHwDevice*>(hal_device);
  if (device->close) {
    device->close(device);
  }
}

}  // namespace
#endif

std::unique_ptr<VulkanInstance> VulkanInstance::Create(
    const bool with_surface, const bool try_enable_validation) {
  std::unique_ptr<VulkanInstance> vulkan_instance(new VulkanInstance());
  bool enable_validation = try_enable_validation;

  // Load the RenderDoc API if connected.

  vulkan_instance->renderdoc_api_ = nullptr;//::CreateIfConnected();

  // Load the loader library.

  Functions& ifn = vulkan_instance->functions_;
  std::string requested_loader_name;
  std::string active_loader_name;
  std::string custom_loader_error;
  bool custom_loader_requested = false;
  bool loaded_system_fallback = false;
  bool using_android_hal_bridge = false;
  bool android_namespace_lookup_unavailable = false;
  AndroidHalVulkanFunctions android_hal_functions;

  bool functions_loaded = true;
#if XE_PLATFORM_LINUX
#if XE_PLATFORM_ANDROID||XE_PLATFORM_AX360E
  // On Android, check for a custom Turnip driver installed via CustomDriverUtils.
  const char* custom_driver_path = std::getenv("CUSTOM_DRIVER_PATH");
  const char* loader_library_name;
  bool is_custom = (custom_driver_path && custom_driver_path[0] != '\0');
  custom_loader_requested = is_custom;
  
  if (is_custom) {
    loader_library_name = custom_driver_path;
    XELOGI("Using custom Vulkan driver: {}", loader_library_name);
  } else {
    loader_library_name = "libvulkan.so";
  }
  requested_loader_name = loader_library_name;
  active_loader_name = loader_library_name;
#else
  const char* const loader_library_name = "libvulkan.so.1";
  requested_loader_name = loader_library_name;
  active_loader_name = loader_library_name;
#endif
  // Load the Vulkan library. On Android with a custom driver, use the sphal
  // namespace (System Private HAL) which has access to system-private libraries
  // like libcutils.so that Turnip depends on — these are blocked in the app's
  // classloader namespace (clns-N) on Android 12+.
#if XE_PLATFORM_ANDROID || XE_PLATFORM_AX360E
  if (is_custom) {
    XELOGI("Preparing Android stub preload for custom driver: {}",
           loader_library_name);
    PreloadAndroidStubLibraries(loader_library_name);
    // android_get_exported_namespace is declared in <android/dlext.h> since API
    // 24 and exported by libdl.so. We can't reach it via dlsym(RTLD_DEFAULT)
    // from within a dlopen'd library on Android (visibility is scoped to the
    // caller's linker namespace), so we resolve it two ways:
    //   1. Directly (strong link through libdl.so at load time).
    //   2. Via an explicit dlopen("libdl.so") + dlsym fallback, in case the
    //      NDK we built against had the symbol stripped for our target API.
    XELOGI("Resolving android_get_exported_namespace for custom driver load");
    GetAndroidNamespaceFn fn_get_ns = ResolveAndroidGetExportedNamespace();
    const char* ns_names[] = {"sphal", "vndk", "default", nullptr};
    if (fn_get_ns) {
      for (int i = 0; ns_names[i] && !vulkan_instance->loader_; i++) {
        android_namespace_t* ns = fn_get_ns(ns_names[i]);
        if (!ns) {
          XELOGW("Namespace '{}' not found", ns_names[i]);
          continue;
        }
        android_dlextinfo extinfo = {};
        extinfo.flags = ANDROID_DLEXT_USE_NAMESPACE;
        extinfo.library_namespace = ns;
        XELOGI("Attempting android_dlopen_ext for custom driver via '{}' namespace",
               ns_names[i]);
        vulkan_instance->loader_ =
            android_dlopen_ext(loader_library_name, RTLD_NOW | RTLD_GLOBAL, &extinfo);
        if (vulkan_instance->loader_) {
          XELOGI("Custom driver loaded via '{}' namespace", ns_names[i]);
        } else {
          XELOGW("android_dlopen_ext with '{}' namespace failed: {}",
                 ns_names[i], dlerror());
        }
      }
    } else {
      XELOGW(
          "android_get_exported_namespace unavailable; skipping namespace "
          "loading and falling back to memfd/direct custom-driver load "
          "attempts");
      android_namespace_lookup_unavailable = true;
    }
    if (!vulkan_instance->loader_) {
      // memfd is only useful when combined with a namespace (so the driver's
      // HAL deps resolve from sphal). Without a namespace, plain dlopen of the
      // install path is both simpler and avoids potential linker issues with
      // tmpfs-backed fds on Android 15+.
      if (fn_get_ns) {
        for (int i = 0; ns_names[i] && !vulkan_instance->loader_; i++) {
          android_namespace_t* ns = fn_get_ns(ns_names[i]);
          if (!ns) {
            continue;
          }
          XELOGI("Attempting memfd-backed custom driver load via '{}' namespace",
                 ns_names[i]);
          vulkan_instance->loader_ =
              TryLoadCustomDriverFromMemFd(loader_library_name, ns, ns_names[i]);
        }
      }
      if (vulkan_instance->loader_) {
        active_loader_name += " [memfd]";
      }
    }
    if (!vulkan_instance->loader_) {
      // Direct dlopen: stubs are already in RTLD_GLOBAL so libcutils/libhardware
      // deps resolve from there. Try this before memfd-without-namespace since
      // it is simpler and more reliable on Android 15+.
      XELOGI("Attempting direct dlopen for custom driver after stub preload: {}",
             loader_library_name);
      dlerror();
      vulkan_instance->loader_ =
          dlopen(loader_library_name, RTLD_NOW | RTLD_GLOBAL);
      if (vulkan_instance->loader_) {
        XELOGI("Custom driver loaded via direct dlopen after stub preload");
      } else {
        const char* direct_load_error = dlerror();
        custom_loader_error =
            direct_load_error ? direct_load_error : "unknown error";
        XELOGW("Direct dlopen for custom driver failed after stub preload: {}",
               custom_loader_error);
        // Last resort: memfd without namespace. This path previously caused
        // SIGSEGV on Android 15 (tmpfs execute restriction) so we try it last.
        XELOGI("Attempting memfd-backed custom driver load without explicit namespace");
        vulkan_instance->loader_ =
            TryLoadCustomDriverFromMemFd(loader_library_name, nullptr, nullptr);
        if (vulkan_instance->loader_) {
          active_loader_name += " [memfd]";
          XELOGI("Custom driver loaded via memfd fallback (no namespace)");
        } else {
          XELOGW("All custom driver load paths failed");
        }
      }
    }
    if (!vulkan_instance->loader_ && custom_loader_error.empty() &&
        android_namespace_lookup_unavailable) {
      custom_loader_error =
          "android_get_exported_namespace unavailable and memfd/direct custom "
          "driver load paths failed";
    }
  } else {
    vulkan_instance->loader_ = dlopen(loader_library_name, RTLD_NOW | RTLD_GLOBAL);
  }
#else
  vulkan_instance->loader_ = dlopen(loader_library_name, RTLD_NOW | RTLD_GLOBAL);
#endif
  if (!vulkan_instance->loader_) {
    const char* error = dlerror();
    XELOGE("Failed to load {}: {}", loader_library_name,
           error ? error : "unknown error");
    
    // Fallback to system driver if custom failed
    if (custom_loader_requested) {
        if (custom_loader_error.empty()) {
          custom_loader_error = error ? error : "unknown error";
        }
        XELOGW("Fallback: attempting to load system libvulkan.so");
        vulkan_instance->loader_ =
            dlopen("libvulkan.so", RTLD_NOW | RTLD_GLOBAL);
        if (vulkan_instance->loader_) {
            loaded_system_fallback = true;
            active_loader_name = "libvulkan.so";
            XELOGI("Successfully loaded system Vulkan as fallback");
        } else {
            const char* fallback_error = dlerror();
            XELOGE("Failed to load system libvulkan.so during fallback: {}",
                   fallback_error ? fallback_error : "unknown error");
        }
    }
    
    if (!vulkan_instance->loader_) {
        return nullptr;
    }
  }
  if (loaded_system_fallback) {
    XELOGW(
        "Active Vulkan loader library: {} (requested custom loader {} failed: {})",
        active_loader_name, requested_loader_name, custom_loader_error);
  } else {
    XELOGI("Active Vulkan loader library: {}", active_loader_name);
  }
#define XE_VULKAN_LOAD_LOADER_FUNCTION(name)                             \
  functions_loaded &=                                                    \
      (ifn.name = PFN_##name(dlsym(vulkan_instance->loader_, #name))) != \
       nullptr;
#elif XE_PLATFORM_WIN32
  vulkan_instance->loader_ = LoadLibraryW(L"vulkan-1.dll");
  requested_loader_name = "vulkan-1.dll";
  active_loader_name = requested_loader_name;
  if (!vulkan_instance->loader_) {
    XELOGE("Failed to load vulkan-1.dll");
    return nullptr;
  }
  XELOGI("Active Vulkan loader library: {}", active_loader_name);
#define XE_VULKAN_LOAD_LOADER_FUNCTION(name)                 \
  functions_loaded &= (ifn.name = PFN_##name(GetProcAddress( \
                            vulkan_instance->loader_, #name))) != nullptr;
#else
#error No Vulkan loader library loading provided for the target platform.
#endif
  // Try the standard loader entry point first. If the loaded library is a
  // Vulkan ICD (e.g. Mesa Turnip loaded directly, not via libvulkan.so), it
  // exports vk_icdGetInstanceProcAddr instead and we must dispatch through
  // that. vkDestroyInstance is NOT exported as a symbol by ICDs, so we defer
  // loading it until after vkCreateInstance succeeds and we have a valid
  // VkInstance handle (see below, after vkCreateInstance).
  XE_VULKAN_LOAD_LOADER_FUNCTION(vkGetInstanceProcAddr);
  // Try to load vkDestroyInstance eagerly; it's needed only at shutdown, and
  // we'll lazily resolve it via vkGetInstanceProcAddr after instance creation
  // if the eager dlsym didn't work (ICDs don't export it as a symbol).
#if XE_PLATFORM_LINUX
  ifn.vkDestroyInstance = PFN_vkDestroyInstance(
      dlsym(vulkan_instance->loader_, "vkDestroyInstance"));
#else
  ifn.vkDestroyInstance = nullptr;
#endif
#if XE_PLATFORM_ANDROID || XE_PLATFORM_AX360E
  if (!ifn.vkGetInstanceProcAddr) {
    // ICD interface: optionally negotiate version, then use vk_icd*.
    typedef VkResult (*PFN_vk_icdNegotiate)(uint32_t*);
    auto icd_negotiate = reinterpret_cast<PFN_vk_icdNegotiate>(
        dlsym(vulkan_instance->loader_,
              "vk_icdNegotiateLoaderICDInterfaceVersion"));
    if (icd_negotiate) {
      uint32_t icd_version = 6;  // highest loader version we claim support for
      VkResult nr = icd_negotiate(&icd_version);
      XELOGI("ICD negotiate returned {}, version {}",
             static_cast<int>(nr), icd_version);
    }
    ifn.vkGetInstanceProcAddr = PFN_vkGetInstanceProcAddr(
        dlsym(vulkan_instance->loader_, "vk_icdGetInstanceProcAddr"));
    if (ifn.vkGetInstanceProcAddr) {
      XELOGI("Using ICD entry point vk_icdGetInstanceProcAddr");
      functions_loaded = true;
    }
  }
  if (!ifn.vkGetInstanceProcAddr && custom_loader_requested) {
    if (TryLoadAndroidHalVulkanFunctions(vulkan_instance->loader_,
                                         &vulkan_instance->android_hal_device_,
                                         &android_hal_functions)) {
      ifn.vkGetInstanceProcAddr = android_hal_functions.GetInstanceProcAddr;
      using_android_hal_bridge = true;
      XELOGI("Using Android HAL GetInstanceProcAddr bridge");
      functions_loaded = true;
    }
  }

  // If a surface-capable instance is needed, quickly probe whether the
  // custom driver advertises VK_KHR_android_surface. Some Turnip builds
  // compiled without Android WSI support won't enumerate it.
  if (functions_loaded && custom_loader_requested && with_surface) {
    PFN_vkEnumerateInstanceExtensionProperties enumerate_extensions = nullptr;
    if (using_android_hal_bridge) {
      enumerate_extensions = android_hal_functions.EnumerateInstanceExtensionProperties;
    } else {
      enumerate_extensions = PFN_vkEnumerateInstanceExtensionProperties(
          ifn.vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceExtensionProperties"));
    }

    if (enumerate_extensions) {
      uint32_t ext_count = 0;
      enumerate_extensions(nullptr, &ext_count, nullptr);
      bool has_surface = false;
      if (ext_count > 0) {
        std::vector<VkExtensionProperties> exts(ext_count);
        enumerate_extensions(nullptr, &ext_count, exts.data());
        for (uint32_t ei = 0; ei < ext_count && !has_surface; ++ei) {
          if (std::string_view(exts[ei].extensionName) == "VK_KHR_android_surface") {
            has_surface = true;
          }
        }
      }

      if (has_surface) {
        XELOGI("Custom driver '{}' successfully advertises VK_KHR_android_surface", active_loader_name);
      } else {
        XELOGW(
            "Custom driver '{}' does NOT advertise VK_KHR_android_surface. "
            "Proceeding anyway as requested — surface creation may fail if the "
            "driver lacks internal WSI support.",
            active_loader_name);
      }
    }
  }
#endif
  if (!ifn.vkGetInstanceProcAddr) {
    functions_loaded = false;
  }
#undef XE_VULKAN_LOAD_LOADER_FUNCTION
  if (!functions_loaded) {
    XELOGE("Failed to get Vulkan loader function pointers");
    return nullptr;
  }

  // Load global functions.
  if (using_android_hal_bridge) {
    XELOGI(
        "Resolving Vulkan global entry points via Android HAL bridge "
        "(validation layer probing disabled)");
    enable_validation = false;
    ifn.vkCreateInstance = android_hal_functions.CreateInstance;
    ifn.vkEnumerateInstanceExtensionProperties =
        android_hal_functions.EnumerateInstanceExtensionProperties;
  }

  if (!using_android_hal_bridge) {
    XELOGI("Resolving vkCreateInstance");
    functions_loaded &=
        (ifn.vkCreateInstance = PFN_vkCreateInstance(
             ifn.vkGetInstanceProcAddr(nullptr, "vkCreateInstance"))) !=
        nullptr;
    XELOGI("Resolving vkEnumerateInstanceExtensionProperties");
    functions_loaded &=
        (ifn.vkEnumerateInstanceExtensionProperties =
             PFN_vkEnumerateInstanceExtensionProperties(
                 ifn.vkGetInstanceProcAddr(
                     nullptr, "vkEnumerateInstanceExtensionProperties"))) !=
        nullptr;
  } else {
    XELOGI("Using Android HAL direct CreateInstance callback");
    XELOGI(
        "Using Android HAL direct EnumerateInstanceExtensionProperties "
        "callback");
    functions_loaded &= ifn.vkCreateInstance != nullptr;
    functions_loaded &= ifn.vkEnumerateInstanceExtensionProperties != nullptr;
  }
  if (!using_android_hal_bridge) {
    XELOGI("Resolving vkEnumerateInstanceLayerProperties");
    functions_loaded &=
        (ifn.vkEnumerateInstanceLayerProperties =
             PFN_vkEnumerateInstanceLayerProperties(
                 ifn.vkGetInstanceProcAddr(
                     nullptr, "vkEnumerateInstanceLayerProperties"))) !=
        nullptr;
  } else {
    ifn.vkEnumerateInstanceLayerProperties = nullptr;
  }
  if (!functions_loaded) {
    XELOGE(
        "Failed to get Vulkan global function pointers via "
        "vkGetInstanceProcAddr");
    return nullptr;
  }
  // Available since Vulkan 1.1. On the Android HAL path, avoid any additional
  // null-instance GetInstanceProcAddr calls before vkCreateInstance.
  if (!using_android_hal_bridge) {
    ifn.vkEnumerateInstanceVersion = PFN_vkEnumerateInstanceVersion(
        ifn.vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceVersion"));
  } else {
    ifn.vkEnumerateInstanceVersion = nullptr;
    XELOGI(
        "Skipping vkEnumerateInstanceVersion on Android HAL path and using "
        "conservative Vulkan 1.0 instance version until vkCreateInstance");
  }

  // Get the API version.

  if (ifn.vkEnumerateInstanceVersion) {
    ifn.vkEnumerateInstanceVersion(&vulkan_instance->api_version_);
  }

  // Enable extensions and layers.

  // Name pointers from `requested_extensions` will be used in the enabled
  // extensions vector.
  std::unordered_map<std::string, bool*> requested_extensions;
  if (vulkan_instance->api_version_ >= VK_MAKE_API_VERSION(0, 1, 1, 0)) {
    vulkan_instance->extensions_.ext_1_1_KHR_get_physical_device_properties2 =
        true;
  } else {
    // #60.
    requested_extensions.emplace(
        "VK_KHR_get_physical_device_properties2",
        &vulkan_instance->extensions_
             .ext_1_1_KHR_get_physical_device_properties2);
  }
  // #129.
  requested_extensions.emplace(
      "VK_EXT_debug_utils", &vulkan_instance->extensions_.ext_EXT_debug_utils);
  // #395.
  requested_extensions.emplace(
      "VK_KHR_portability_enumeration",
      &vulkan_instance->extensions_.ext_KHR_portability_enumeration);
  if (with_surface) {
    // #1.
    requested_extensions.emplace("VK_KHR_surface",
                                 &vulkan_instance->extensions_.ext_KHR_surface);
#ifdef VK_USE_PLATFORM_XCB_KHR
    // #6.
    requested_extensions.emplace(
        "VK_KHR_xcb_surface",
        &vulkan_instance->extensions_.ext_KHR_xcb_surface);
#endif
#ifdef VK_USE_PLATFORM_ANDROID_KHR
    // #9.
    requested_extensions.emplace(
        "VK_KHR_android_surface",
        &vulkan_instance->extensions_.ext_KHR_android_surface);
#endif
    requested_extensions.emplace(
        "VK_EXT_headless_surface",
        &vulkan_instance->extensions_.ext_EXT_headless_surface);
#ifdef VK_USE_PLATFORM_WIN32_KHR
    // #10.
    requested_extensions.emplace(
        "VK_KHR_win32_surface",
        &vulkan_instance->extensions_.ext_KHR_win32_surface);
#endif
  }

  std::vector<const char*> enabled_extensions;

  std::vector<VkExtensionProperties> supported_implementation_extensions;
  XELOGI("Enumerating Vulkan instance extensions");
  while (true) {
    uint32_t supported_implementation_extension_count = 0;
    const VkResult get_supported_implementation_extension_count_result =
        ifn.vkEnumerateInstanceExtensionProperties(
            nullptr, &supported_implementation_extension_count, nullptr);
    if (get_supported_implementation_extension_count_result != VK_SUCCESS &&
        get_supported_implementation_extension_count_result != VK_INCOMPLETE) {
      XELOGW("Failed to get the Vulkan instance extension count");
      return nullptr;
    }
    if (supported_implementation_extension_count) {
      supported_implementation_extensions.resize(
          supported_implementation_extension_count);
      const VkResult get_supported_implementation_extensions_result =
          ifn.vkEnumerateInstanceExtensionProperties(
              nullptr, &supported_implementation_extension_count,
              supported_implementation_extensions.data());
      if (get_supported_implementation_extensions_result == VK_INCOMPLETE) {
        continue;
      }
      if (get_supported_implementation_extensions_result != VK_SUCCESS) {
        XELOGW("Failed to get the Vulkan instance extensions");
        return nullptr;
      }
    }
    supported_implementation_extensions.resize(
        supported_implementation_extension_count);
    break;
  }

  XELOGI("Driver advertises {} instance extensions:",
         supported_implementation_extensions.size());
  for (const VkExtensionProperties& ext : supported_implementation_extensions) {
    XELOGI("  {}", ext.extensionName);
  }

  for (const VkExtensionProperties& supported_extension :
       supported_implementation_extensions) {
    const auto requested_extension_it =
        requested_extensions.find(supported_extension.extensionName);
    if (requested_extension_it == requested_extensions.cend()) {
      continue;
    }
    assert_not_null(requested_extension_it->second);
    if (!*requested_extension_it->second) {
      enabled_extensions.emplace_back(requested_extension_it->first.c_str());
      *requested_extension_it->second = true;
    }
  }

  // If enabled layers are not present, will disable all extensions provided by
  // the layers by truncating the enabled extension vector to this size.
  const size_t enabled_implementation_extension_count =
      enabled_extensions.size();
  std::vector<bool*> enabled_layer_extension_enablement_bools;

  // Name pointers from `requested_layers` will be used in the enabled layer
  // vector.
  std::unordered_map<std::string, bool*> requested_layers;
  bool layer_khronos_validation = false;
  if (enable_validation) {
    requested_layers.emplace("VK_LAYER_KHRONOS_validation",
                             &layer_khronos_validation);
  }

  std::vector<const char*> enabled_layers;

  if (!requested_layers.empty()) {
    std::vector<VkLayerProperties> available_layers;
    // "The list of available layers may change at any time due to actions
    // outside of the Vulkan implementation"
    while (true) {
      available_layers.clear();
      uint32_t available_layer_count = 0;
      const VkResult get_available_layer_count_result =
          ifn.vkEnumerateInstanceLayerProperties(&available_layer_count,
                                                 nullptr);
      if (get_available_layer_count_result != VK_SUCCESS &&
          get_available_layer_count_result != VK_INCOMPLETE) {
        break;
      }
      if (available_layer_count) {
        available_layers.resize(available_layer_count);
        const VkResult get_available_layers_result =
            ifn.vkEnumerateInstanceLayerProperties(&available_layer_count,
                                                   available_layers.data());
        if (get_available_layers_result == VK_INCOMPLETE) {
          // New layers were added.
          continue;
        }
        if (get_available_layers_result != VK_SUCCESS) {
          available_layers.clear();
          break;
        }
        // In case the second enumeration returned fewer layers.
        available_layers.resize(available_layer_count);
      }
      break;
    }

    if (!available_layers.empty()) {
      std::vector<VkExtensionProperties> supported_layer_extensions;

      for (const VkLayerProperties& available_layer : available_layers) {
        auto requested_layer_it =
            requested_layers.find(available_layer.layerName);
        if (requested_layer_it == requested_layers.cend()) {
          continue;
        }

        bool got_layer_extensions = true;
        // "Because the list of available layers may change externally between
        // calls to vkEnumerateInstanceExtensionProperties, two calls may
        // retrieve different results if a pLayerName is available in one call
        // but not in another."
        while (true) {
          uint32_t supported_layer_extension_count = 0;
          const VkResult get_supported_layer_extension_count_result =
              ifn.vkEnumerateInstanceExtensionProperties(
                  nullptr, &supported_layer_extension_count, nullptr);
          if (get_supported_layer_extension_count_result != VK_SUCCESS &&
              get_supported_layer_extension_count_result != VK_INCOMPLETE) {
            got_layer_extensions = false;
            break;
          }
          if (supported_layer_extension_count) {
            supported_layer_extensions.resize(supported_layer_extension_count);
            const VkResult get_supported_layer_extensions_result =
                ifn.vkEnumerateInstanceExtensionProperties(
                    available_layer.layerName, &supported_layer_extension_count,
                    supported_layer_extensions.data());
            if (get_supported_layer_extensions_result == VK_INCOMPLETE) {
              continue;
            }
            if (get_supported_layer_extensions_result != VK_SUCCESS) {
              got_layer_extensions = false;
              break;
            }
          }
          supported_layer_extensions.resize(supported_layer_extension_count);
          break;
        }
        if (!got_layer_extensions) {
          // The layer was possibly removed.
          continue;
        }

        for (const VkExtensionProperties& supported_extension :
             supported_layer_extensions) {
          const auto requested_extension_it =
              requested_extensions.find(supported_extension.extensionName);
          if (requested_extension_it == requested_extensions.cend()) {
            continue;
          }
          assert_not_null(requested_extension_it->second);
          // Don't add the extension to the enabled vector multiple times if
          // provided by the implementation itself or by another layer.
          if (!*requested_extension_it->second) {
            enabled_extensions.emplace_back(
                requested_extension_it->first.c_str());
            enabled_layer_extension_enablement_bools.push_back(
                requested_layer_it->second);
            *requested_extension_it->second = true;
          }
        }

        assert_not_null(requested_layer_it->second);
        if (!*requested_layer_it->second) {
          enabled_layers.emplace_back(requested_layer_it->first.c_str());
          *requested_layer_it->second = true;
        }
      }
    }
  }

  // Create the instance.

  VkApplicationInfo application_info;
  application_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  application_info.pNext = nullptr;
  application_info.pApplicationName = "Xenia";
  application_info.applicationVersion = 1;
  application_info.pEngineName = nullptr;
  application_info.engineVersion = 0;
  // "The patch version number specified in apiVersion is ignored when creating
  // an instance object."
  // "Vulkan 1.0 implementations were required to return
  // VK_ERROR_INCOMPATIBLE_DRIVER if apiVersion was larger than 1.0."
  application_info.apiVersion =
      vulkan_instance->api_version_ >= VK_MAKE_API_VERSION(0, 1, 1, 0)
          ? VulkanDevice::kHighestUsedApiMinorVersion
          : VK_MAKE_API_VERSION(0, 1, 0, 0);

  VkInstanceCreateInfo instance_create_info;
  instance_create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  instance_create_info.pNext = nullptr;
  instance_create_info.flags = 0;
  // VK_KHR_get_physical_device_properties2 is needed to get the portability
  // subset features.
  if (vulkan_instance->extensions_.ext_KHR_portability_enumeration &&
      vulkan_instance->extensions_
          .ext_1_1_KHR_get_physical_device_properties2) {
    instance_create_info.flags |=
        VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
  }
  instance_create_info.pApplicationInfo = &application_info;
  instance_create_info.enabledLayerCount = uint32_t(enabled_layers.size());
  instance_create_info.ppEnabledLayerNames = enabled_layers.data();
  instance_create_info.enabledExtensionCount =
      uint32_t(enabled_extensions.size());
  instance_create_info.ppEnabledExtensionNames = enabled_extensions.data();
  XELOGI("Creating Vulkan instance with {} extensions and {} layers",
         instance_create_info.enabledExtensionCount,
         instance_create_info.enabledLayerCount);
  VkResult instance_create_result = ifn.vkCreateInstance(
      &instance_create_info, nullptr, &vulkan_instance->instance_);

  if (instance_create_result == VK_ERROR_LAYER_NOT_PRESENT ||
      instance_create_result == VK_ERROR_EXTENSION_NOT_PRESENT) {
    // A layer was possibly removed. Try without layers.
    for (bool* const extension_enablement :
         enabled_layer_extension_enablement_bools) {
      *extension_enablement = false;
    }
    for (const std::pair<std::string, bool*>& requested_layer :
         requested_layers) {
      *requested_layer.second = false;
    }
    instance_create_info.enabledLayerCount = 0;
    instance_create_info.enabledExtensionCount =
        uint32_t(enabled_implementation_extension_count);
    instance_create_result = ifn.vkCreateInstance(
        &instance_create_info, nullptr, &vulkan_instance->instance_);
  }

  if (instance_create_result != VK_SUCCESS) {
    XELOGE("Failed to create a Vulkan instance: {}",
           vk::to_string(vk::Result(instance_create_result)));
    return nullptr;
  }
  XELOGI("Created Vulkan instance successfully");

  // Lazily resolve vkDestroyInstance if it wasn't available as a raw symbol
  // (the ICD case — the function is only reachable via vkGetInstanceProcAddr
  // with a valid VkInstance, per the Vulkan spec).
  if (!ifn.vkDestroyInstance) {
    ifn.vkDestroyInstance = PFN_vkDestroyInstance(
        ifn.vkGetInstanceProcAddr(vulkan_instance->instance_,
                                  "vkDestroyInstance"));
    if (!ifn.vkDestroyInstance) {
      XELOGE("Failed to resolve vkDestroyInstance via vkGetInstanceProcAddr");
      return nullptr;
    }
  }

  // Load instance functions.

#define XE_UI_VULKAN_FUNCTION(name)                                     \
  functions_loaded &= (ifn.name = PFN_##name(ifn.vkGetInstanceProcAddr( \
                           vulkan_instance->instance_, #name))) != nullptr;

  // Vulkan 1.0.
#include "xenia/ui/vulkan/functions/instance_1_0.inc"

  // Extensions promoted to a Vulkan version supported by the instance.
#define XE_UI_VULKAN_FUNCTION_PROMOTED(extension_name, core_name) \
  functions_loaded &=                                             \
      (ifn.core_name = PFN_##core_name(ifn.vkGetInstanceProcAddr( \
           vulkan_instance->instance_, #core_name))) != nullptr;
  if (vulkan_instance->api_version_ >= VK_MAKE_API_VERSION(0, 1, 1, 0)) {
#include "xenia/ui/vulkan/functions/instance_1_1_khr_get_physical_device_properties2.inc"
  }
#undef XE_UI_VULKAN_FUNCTION_PROMOTED

  // Non-promoted extensions, and extensions promoted to a Vulkan version not
  // supported by the instance.
#define XE_UI_VULKAN_FUNCTION_PROMOTED(extension_name, core_name) \
  functions_loaded &=                                             \
      (ifn.core_name = PFN_##core_name(ifn.vkGetInstanceProcAddr( \
           vulkan_instance->instance_, #extension_name))) != nullptr;
  if (vulkan_instance->api_version_ < VK_MAKE_API_VERSION(0, 1, 1, 0)) {
    if (vulkan_instance->extensions_
            .ext_1_1_KHR_get_physical_device_properties2) {
#include "xenia/ui/vulkan/functions/instance_1_1_khr_get_physical_device_properties2.inc"
    }
  }
#ifdef VK_USE_PLATFORM_XCB_KHR
  if (vulkan_instance->extensions_.ext_KHR_xcb_surface) {
#include "xenia/ui/vulkan/functions/instance_khr_xcb_surface.inc"
  }
#endif
#ifdef VK_USE_PLATFORM_ANDROID_KHR
  if (vulkan_instance->extensions_.ext_KHR_android_surface) {
#include "xenia/ui/vulkan/functions/instance_khr_android_surface.inc"
  }
#endif
#ifdef VK_USE_PLATFORM_WIN32_KHR
  if (vulkan_instance->extensions_.ext_KHR_win32_surface) {
#include "xenia/ui/vulkan/functions/instance_khr_win32_surface.inc"
  }
#endif
  if (vulkan_instance->extensions_.ext_EXT_headless_surface) {
#include "xenia/ui/vulkan/functions/instance_ext_headless_surface.inc"
  }
  if (vulkan_instance->extensions_.ext_KHR_surface) {
#include "xenia/ui/vulkan/functions/instance_khr_surface.inc"
  }
  if (vulkan_instance->extensions_.ext_EXT_debug_utils) {
#include "xenia/ui/vulkan/functions/instance_ext_debug_utils.inc"
  }
#undef XE_UI_VULKAN_FUNCTION_PROMOTED

#undef XE_UI_VULKAN_FUNCTION

  if (!functions_loaded) {
    XELOGE("Failed to get all Vulkan instance function pointers");
    return nullptr;
  }

  // Check whether a surface can be created.

  if (with_surface && !VulkanPresenter::GetSurfaceTypesSupportedByInstance(
                          vulkan_instance->extensions_)) {
    XELOGE("The Vulkan instance doesn't support surface types used by Xenia");
    return nullptr;
  }

  // Log instance properties.

  XELOGI("Vulkan instance API version {}.{}.{}. Enabled layers and extensions:",
         VK_VERSION_MAJOR(vulkan_instance->api_version_),
         VK_VERSION_MINOR(vulkan_instance->api_version_),
         VK_VERSION_PATCH(vulkan_instance->api_version_));
  for (uint32_t enabled_layer_index = 0;
       enabled_layer_index < instance_create_info.enabledLayerCount;
       ++enabled_layer_index) {
    XELOGI("* {}",
           instance_create_info.ppEnabledLayerNames[enabled_layer_index]);
  }
  for (uint32_t enabled_extension_index = 0;
       enabled_extension_index < instance_create_info.enabledExtensionCount;
       ++enabled_extension_index) {
    XELOGI(
        "* {}",
        instance_create_info.ppEnabledExtensionNames[enabled_extension_index]);
  }

  // Create the debug messenger if requested and available.
#if 1
  if (vulkan_instance->extensions_.ext_EXT_debug_utils &&
      cvars::vulkan_log_debug_messages) {
    VkDebugUtilsMessengerCreateInfoEXT debug_utils_messenger_create_info = {
        VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT};
    if (xe::logging::ShouldLog(xe::LogLevel::Debug)) {
      debug_utils_messenger_create_info.messageSeverity |=
          VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT;
    }
    if (xe::logging::ShouldLog(xe::LogLevel::Info)) {
      debug_utils_messenger_create_info.messageSeverity |=
          VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT;
    }
    if (xe::logging::ShouldLog(xe::LogLevel::Warning)) {
      debug_utils_messenger_create_info.messageSeverity |=
          VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
    }
    if (xe::logging::ShouldLog(xe::LogLevel::Error)) {
      debug_utils_messenger_create_info.messageSeverity |=
          VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
    }
    // VUID-VkDebugUtilsMessengerCreateInfoEXT-messageSeverity-requiredbitmask:
    // "messageSeverity must not be 0"
    if (debug_utils_messenger_create_info.messageSeverity) {
      debug_utils_messenger_create_info.messageType =
          VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
          VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
          VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
      debug_utils_messenger_create_info.pfnUserCallback =
          DebugUtilsMessengerCallback;
      debug_utils_messenger_create_info.pUserData = vulkan_instance.get();
      const VkResult debug_utils_messenger_create_result =
          ifn.vkCreateDebugUtilsMessengerEXT(
              vulkan_instance->instance_, &debug_utils_messenger_create_info,
              nullptr, &vulkan_instance->debug_utils_messenger_);
      if (debug_utils_messenger_create_result != VK_SUCCESS) {
        XELOGW("Failed to create the Vulkan debug utils messenger: {}",
               vk::to_string(vk::Result(debug_utils_messenger_create_result)));
      }
    }
  }
#endif

  return vulkan_instance;
}

VulkanInstance::~VulkanInstance() {
  if (instance_) {
    if (debug_utils_messenger_ != VK_NULL_HANDLE) {
      functions_.vkDestroyDebugUtilsMessengerEXT(
          instance_, debug_utils_messenger_, nullptr);
    }

    functions_.vkDestroyInstance(instance_, nullptr);
  }

#if XE_PLATFORM_ANDROID || XE_PLATFORM_AX360E
  if (android_hal_device_) {
    CloseAndroidHalDevice(android_hal_device_);
  }
#endif
#if XE_PLATFORM_LINUX
  if (loader_) {
    dlclose(loader_);
  }
#elif XE_PLATFORM_WIN32
  if (loader_) {
    FreeLibrary(loader_);
  }
#endif
}

void VulkanInstance::EnumeratePhysicalDevices(
    std::vector<VkPhysicalDevice>& physical_devices_out) const {
  physical_devices_out.clear();
  while (true) {
    uint32_t physical_device_count = 0;
    const VkResult get_physical_device_count_result =
        functions_.vkEnumeratePhysicalDevices(instance_, &physical_device_count,
                                              nullptr);
    if ((get_physical_device_count_result != VK_SUCCESS &&
         get_physical_device_count_result != VK_INCOMPLETE) ||
        !physical_device_count) {
      return;
    }
    physical_devices_out.resize(physical_device_count);
    const VkResult get_physical_devices_result =
        functions_.vkEnumeratePhysicalDevices(instance_, &physical_device_count,
                                              physical_devices_out.data());
    if (get_physical_devices_result == VK_INCOMPLETE) {
      continue;
    }
    physical_devices_out.resize(
        get_physical_devices_result == VK_SUCCESS ? physical_device_count : 0);
    return;
  }
}

VkBool32 VulkanInstance::DebugUtilsMessengerCallback(
    VkDebugUtilsMessageSeverityFlagBitsEXT message_severity,
    VkDebugUtilsMessageTypeFlagsEXT message_types,
    const VkDebugUtilsMessengerCallbackDataEXT* callback_data,
    [[maybe_unused]] void* user_data) {
  xe::LogLevel log_level;
  char log_prefix_char;
  if (message_severity >= VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) {
    log_level = xe::LogLevel::Error;
    log_prefix_char = xe::logging::kPrefixCharError;
  } else if (message_severity >=
             VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) {
    log_level = xe::LogLevel::Warning;
    log_prefix_char = xe::logging::kPrefixCharWarning;
  } else if (message_severity >= VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) {
    log_level = xe::LogLevel::Info;
    log_prefix_char = xe::logging::kPrefixCharInfo;
  } else {
    log_level = xe::LogLevel::Debug;
    log_prefix_char = xe::logging::kPrefixCharDebug;
  }

  std::ostringstream log_str;

  log_str << "Vulkan "
          << vk::to_string(
                 vk::DebugUtilsMessageSeverityFlagBitsEXT(message_severity))
          << " ("
          << vk::to_string(vk::DebugUtilsMessageTypeFlagsEXT(message_types))
          << ", ID " << callback_data->messageIdNumber;
  if (callback_data->pMessageIdName) {
    log_str << ": " << callback_data->pMessageIdName;
  }
  log_str << ')';

  if (callback_data->pMessage) {
    log_str << ": " << callback_data->pMessage;
  }

  bool annotations_begun = false;
  const auto begin_annotation = [&log_str, &annotations_begun]() {
    log_str << (annotations_begun ? ", " : " (");
    annotations_begun = true;
  };

  for (uint32_t queue_label_index = 0;
       queue_label_index < callback_data->queueLabelCount;
       ++queue_label_index) {
    begin_annotation();
    log_str << "queue label " << queue_label_index << ": "
            << callback_data->pQueueLabels[queue_label_index].pLabelName;
  }

  for (uint32_t cmd_buf_label_index = 0;
       cmd_buf_label_index < callback_data->cmdBufLabelCount;
       ++cmd_buf_label_index) {
    begin_annotation();
    log_str << "command buffer label " << cmd_buf_label_index << ": "
            << callback_data->pCmdBufLabels[cmd_buf_label_index].pLabelName;
  }

  for (uint32_t object_index = 0; object_index < callback_data->objectCount;
       ++object_index) {
    begin_annotation();
    const VkDebugUtilsObjectNameInfoEXT& object_info =
        callback_data->pObjects[object_index];
    // Lowercase hexadecimal digits in the handle to match the default Vulkan
    // debug utils messenger.
    log_str << "object " << object_index << ": "
            << vk::to_string(vk::ObjectType(object_info.objectType)) << " 0x"
            << std::hex << object_info.objectHandle << std::dec;
    if (object_info.pObjectName) {
      log_str << " '" << object_info.pObjectName << '\'';
    }
  }

  if (annotations_begun) {
    log_str << ')';
  }

  xe::logging::AppendLogLine(log_level, log_prefix_char, log_str.str());

  return VK_FALSE;
}

}  // namespace vulkan
}  // namespace ui
}  // namespace xe
