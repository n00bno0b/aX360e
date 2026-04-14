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

/**
 * Memory pressure state for dynamic texture cache management.
 * Updated from Java MemoryPressureManager via JNI.
 */
struct MemoryPressureState {
    std::atomic<int> pressure_level;      // 0=NONE, 1=LOW, 2=MEDIUM, 3=HIGH, 4=CRITICAL
    std::atomic<uint64_t> available_mb;
    std::atomic<int> thermal_level;       // 0-6 from PowerManager.THERMAL_STATUS_*
    std::atomic<uint64_t> last_update_time_ms;

    MemoryPressureState()
        : pressure_level(0)
        , available_mb(0)
        , thermal_level(0)
        , last_update_time_ms(0)
    {}

    double GetScaleFactor() const {
        double scale = 1.0;
        int level = pressure_level.load(std::memory_order_relaxed);
        int thermal = thermal_level.load(std::memory_order_relaxed);

        switch (level) {
            case 4: scale = 0.5; break;   // CRITICAL
            case 3: scale = 0.7; break;   // HIGH
            case 2: scale = 0.85; break;  // MEDIUM
            case 1: scale = 0.95; break;  // LOW
            default: scale = 1.0; break;  // NONE
        }

        // Apply thermal throttling
        if (thermal >= 4) {
            scale *= 0.8;  // THERMAL_STATUS_CRITICAL
        } else if (thermal >= 3) {
            scale *= 0.9;  // THERMAL_STATUS_SEVERE
        }

        return scale;
    }

    bool ShouldEvictTextures() const {
        return pressure_level.load(std::memory_order_relaxed) >= 3; // HIGH or CRITICAL
    }
};

extern MemoryPressureState g_memory_pressure;

#endif //AX360E_EMULATOR_AX360E_H
