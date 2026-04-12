// SPDX-License-Identifier: WTFPL

#include <jni.h>
#include <android/log.h>

#define LOGI(...) {}
#define LOGW(...) {}
#define LOGE(...) {}
#define PR {}
#define PRE(f) {}

int register_Emulator(JNIEnv* env);
int register_Emulator$Config(JNIEnv* env);

int register_ax360e_Emulator(JNIEnv* env);

JavaVM* g_jvm;

extern "C" __attribute__((visibility("default")))
jint JNI_OnLoad(JavaVM* vm, void* reserved){

    g_jvm = vm;

    JNIEnv* env = NULL;
    int result=-1;

    __android_log_print(ANDROID_LOG_INFO, "ax360e_native", "JNI_OnLoad starting");
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, "ax360e_native", "GetEnv failed");
        goto bail;
    }

    __android_log_print(ANDROID_LOG_INFO, "ax360e_native", "Registering Emulator");
    if(register_Emulator(env) != JNI_OK){
        __android_log_print(ANDROID_LOG_ERROR, "ax360e_native", "register_Emulator failed");
        goto bail;
    }

    __android_log_print(ANDROID_LOG_INFO, "ax360e_native", "Registering Emulator$Config");
    if(register_Emulator$Config(env) != JNI_OK){
        __android_log_print(ANDROID_LOG_ERROR, "ax360e_native", "register_Emulator$Config failed");
        goto bail;
    }

    __android_log_print(ANDROID_LOG_INFO, "ax360e_native", "Registering ax360e_Emulator");
    if(register_ax360e_Emulator(env) != JNI_OK){
        __android_log_print(ANDROID_LOG_ERROR, "ax360e_native", "register_ax360e_Emulator failed");
        goto bail;
    }
    result = JNI_VERSION_1_6;

    __android_log_print(ANDROID_LOG_INFO, "ax360e_native", "JNI_OnLoad OK");

    bail:
    return result;
}