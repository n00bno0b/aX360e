#include <android/log.h>
#include <fcntl.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

struct hw_module_t;
struct sync_file_info;

#ifndef PROP_VALUE_MAX
#define PROP_VALUE_MAX 92
#endif

extern "C" {
    static constexpr char kStubLogTag[] = "AX360EStub";

    void dummy_lib_func() {}

    // libcutils atrace stubs
    uint64_t atrace_get_enabled_tags() { return 0; }
    void atrace_setup() {}
    void atrace_begin_body(const char*) {}
    void atrace_end_body() {}
    void atrace_async_begin_body(const char*, int32_t) {}
    void atrace_async_end_body(const char*, int32_t) {}
    void atrace_int_body(const char*, int32_t) {}
    void atrace_int64_body(const char*, int64_t) {}
    void atrace_init() {}

    // libcutils property stubs
    int property_get(const char* key, char* value, const char* default_value) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "property_get(key=%s, default=%s)",
                            key ? key : "<null>",
                            default_value ? default_value : "<null>");
        if (!value) {
            __android_log_print(ANDROID_LOG_WARN, kStubLogTag,
                                "property_get called with null value buffer");
            return 0;
        }
        constexpr size_t kPropertyBufferCapacity = PROP_VALUE_MAX + 1;
        size_t copied_length = 0;
        if (default_value) {
            copied_length = strnlen(default_value, kPropertyBufferCapacity);
            if (copied_length >= kPropertyBufferCapacity) {
                copied_length = PROP_VALUE_MAX;
            }
            memcpy(value, default_value, copied_length);
        }
        value[copied_length] = '\0';
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "property_get returning '%s' (%d bytes)", value,
                            static_cast<int>(copied_length));
        return static_cast<int>(copied_length);
    }
    int property_set(const char* key, const char* value) { return 0; }
    int property_list(void (*fn)(const char* key, const char* value, void* cookie), void* cookie) { return 0; }

    // libutils stubs
    int64_t _ZN7android11SystemClock13uptimeMillisEv() {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "android::SystemClock::uptimeMillis()");
        return 0LL;
    } // android::SystemClock::uptimeMillis()
    int _ZN7android7Thread6runEPKciPj(const char* name, int priority,
                                      uint32_t*) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "android::Thread::run(name=%s, priority=%d)",
                            name ? name : "<null>", priority);
        return 0;
    } // android::Thread::run(...)

    // libhardware stubs
    int hw_get_module(const char *id, const struct hw_module_t **module) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "hw_get_module(id=%s)", id ? id : "<null>");
        if (module) {
            *module = nullptr;
        }
        __android_log_print(ANDROID_LOG_WARN, kStubLogTag,
                            "hw_get_module returning -1 for id=%s",
                            id ? id : "<null>");
        return -1;
    }
    int hw_get_module_by_class(const char *class_id, const char *inst, const struct hw_module_t **module) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "hw_get_module_by_class(class_id=%s, inst=%s)",
                            class_id ? class_id : "<null>",
                            inst ? inst : "<null>");
        if (module) {
            *module = nullptr;
        }
        __android_log_print(ANDROID_LOG_WARN, kStubLogTag,
                            "hw_get_module_by_class returning -1 for class_id=%s, inst=%s",
                            class_id ? class_id : "<null>",
                            inst ? inst : "<null>");
        return -1;
    }

    // libsync stubs
    int sync_wait(int fd, int timeout) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "sync_wait(fd=%d, timeout=%d)", fd, timeout);
        return 0;
    }
    int sync_merge(const char* name, int fd, int fd2) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "sync_merge(name=%s, fd=%d, fd2=%d)",
                            name ? name : "<null>", fd, fd2);
        int source_fd = fd >= 0 ? fd : fd2;
        if (source_fd < 0) {
            __android_log_print(ANDROID_LOG_WARN, kStubLogTag,
                                "sync_merge returning -1 because both fds are invalid");
            return -1;
        }
        int merged_fd = fcntl(source_fd, F_DUPFD_CLOEXEC, 0);
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "sync_merge duplicated fd %d -> %d",
                            source_fd, merged_fd);
        return merged_fd;
    }
    struct sync_file_info* sync_file_info(int32_t fd) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "sync_file_info(fd=%d)", fd);
        return nullptr;
    }
    void sync_file_info_free(struct sync_file_info* info) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "sync_file_info_free(info=%p)", info);
    }

    // libnativewindow stubs
    void* AHardwareBuffer_getNativeHandle(const void* buffer) {
        __android_log_print(ANDROID_LOG_INFO, kStubLogTag,
                            "AHardwareBuffer_getNativeHandle(buffer=%p)", buffer);
        return nullptr;
    }
    int AHardwareBuffer_allocate(const void* desc, void** outBuffer) { return -1; }
    void AHardwareBuffer_release(void* buffer) {}
    void AHardwareBuffer_acquire(void* buffer) {}
    void AHardwareBuffer_describe(const void* buffer, void* outDesc) {}
    int AHardwareBuffer_lock(void* buffer, uint64_t usage, int32_t fence, const void* rect, void** out_virtual_address) { return -1; }
    int AHardwareBuffer_unlock(void* buffer, int32_t* fence) { return -1; }
    int AHardwareBuffer_sendHandleToUnixSocket(const void* buffer, int socketFd) { return -1; }
    int AHardwareBuffer_recvHandleFromUnixSocket(int socketFd, void** outBuffer) { return -1; }
    int AHardwareBuffer_isSupported(const void* desc) { return 0; }

    // ANativeWindow stubs
    void ANativeWindow_acquire(void* window) {}
    void ANativeWindow_release(void* window) {}
    int32_t ANativeWindow_getWidth(void* window) { return 0; }
    int32_t ANativeWindow_getHeight(void* window) { return 0; }
    int32_t ANativeWindow_getFormat(void* window) { return 0; }
    int32_t ANativeWindow_setBuffersGeometry(void* window, int32_t w, int32_t h, int32_t f) { return 0; }
    int32_t ANativeWindow_lock(void* window, void* outBuffer, void* inOutDirtyBounds) { return -1; }
    int32_t ANativeWindow_unlockAndPost(void* window) { return -1; }

    // liblog stubs
    int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
        // This is a bit recursive if we use __android_log_print here, 
        // but we're usually pre-loading this so it's okay.
        return 0;
    }
    void __android_log_assert(const char* cond, const char* tag, const char* fmt, ...) {}
}
