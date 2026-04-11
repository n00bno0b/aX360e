// SPDX-License-Identifier: WTFPL

#ifndef AX360E_EMULATOR_AX360E_H
#define AX360E_EMULATOR_AX360E_H

#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>

extern jclass g_class_DocumentFile;
extern jclass g_class_Emulator;

extern jobject g_context;
extern jobject g_doocument_file_tree;

extern jmethodID mid_open_uri_fd;

extern std::vector<std::string> g_launch_args;
extern std::string g_uri_info_list_file_path;

/**
 * Performance metrics snapshot pushed from Java.
 * Cached in native code for consumption by render/frame pacing systems.
 */
struct PerformanceMetrics {
    std::atomic<float> fps;
    std::atomic<float> frame_time_ms;
    std::atomic<int> perf_state;        // 0=NORMAL, 1=PRESSURED, 2=THROTTLING, 3=CRITICAL
    std::atomic<float> memory_used_mb;
    std::atomic<float> memory_total_mb;
    std::atomic<float> temperature;
    std::atomic<uint64_t> last_update_time_ms;

    PerformanceMetrics()
        : fps(0.0f)
        , frame_time_ms(0.0f)
        , perf_state(0)
        , memory_used_mb(0.0f)
        , memory_total_mb(0.0f)
        , temperature(0.0f)
        , last_update_time_ms(0)
    {}
};

extern PerformanceMetrics g_performance_metrics;

#endif //AX360E_EMULATOR_AX360E_H
