#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "DriverTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Declare the function from adreno_driver.cpp
namespace {
    bool load_custom_adreno_driver(const std::string& driver_dir,
                                   const std::string& driver_name,
                                   bool enable_redirection);
}

extern "C" {

// Test function to load the driver using libadrenotools (same path as the emulator)
JNIEXPORT jstring JNICALL
Java_aenu_ax360e_Emulator_nativeTestDriverLoad(JNIEnv* env, jclass clazz, jstring driverDir, jstring driverName) {
    const char* dir = env->GetStringUTFChars(driverDir, nullptr);
    const char* name = env->GetStringUTFChars(driverName, nullptr);

    LOGI("Testing driver load via libadrenotools: dir=%s name=%s", dir, name);

    bool loaded = load_custom_adreno_driver(dir, name, true);
    if (loaded) {
        LOGI("Driver loaded successfully via libadrenotools!");
        env->ReleaseStringUTFChars(driverDir, dir);
        env->ReleaseStringUTFChars(driverName, name);
        return env->NewStringUTF("SUCCESS: Driver loaded via libadrenotools");
    } else {
        LOGE("Driver load failed via libadrenotools");
        env->ReleaseStringUTFChars(driverDir, dir);
        env->ReleaseStringUTFChars(driverName, name);
        return env->NewStringUTF("FAILED: libadrenotools could not load driver");
    }
}

} // extern "C"
