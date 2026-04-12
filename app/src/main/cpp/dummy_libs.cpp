#include <stdint.h>
#include <string.h>

extern "C" {
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
        if (default_value) {
            strcpy(value, default_value);
            return strlen(value);
        }
        value[0] = '\0';
        return 0;
    }
    int property_set(const char* key, const char* value) { return 0; }
    int property_list(void (*fn)(const char* key, const char* value, void* cookie), void* cookie) { return 0; }

    // libutils stubs
    void _ZN7android11SystemClock13uptimeMillisEv() {} // android::SystemClock::uptimeMillis()
    void _ZN7android7Thread6runEPKciPj() {} // android::Thread::run(...)

    // libhardware stubs
    int hw_get_module(const char *id, const struct hw_module_t **module) { return -1; }
    int hw_get_module_by_class(const char *class_id, const char *inst, const struct hw_module_t **module) { return -1; }
}
