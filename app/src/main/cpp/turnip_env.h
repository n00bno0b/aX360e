// SPDX-License-Identifier: WTFPL
// Turnip Driver Environment Variable Helpers
// Based on research from Eden emulator and Mesa Turnip documentation

#ifndef AX360E_TURNIP_ENV_H
#define AX360E_TURNIP_ENV_H

#include <cstdlib>
#include <string>
#include <android/log.h>

#define TURNIP_LOG_TAG "TurnipEnv"
#define TURNIP_LOGI(...) __android_log_print(ANDROID_LOG_INFO, TURNIP_LOG_TAG, __VA_ARGS__)

namespace turnip {

// Environment variable helpers for Turnip driver optimization
// Reference: https://github.com/eden-emulator/Issue-Reports/pulls/3205
// Reference: Mesa Turnip documentation

inline void SetGmemMode(bool enable) {
    if (enable) {
        const char* existing = std::getenv("TU_DEBUG");
        if (existing && std::string(existing).find("gmem") == std::string::npos) {
            std::string combined = std::string(existing) + ",gmem";
            setenv("TU_DEBUG", combined.c_str(), 1);
        } else if (!existing) {
            setenv("TU_DEBUG", "gmem", 1);
        }
        TURNIP_LOGI("Enabled GMEM mode (recommended for Adreno 710/720)");
    }
}

inline void SetUbwcFlagHint(bool enable) {
    if (enable) {
        setenv("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1", 1);
        TURNIP_LOGI("Enabled UBWC flag hint (fixes OneUI graphical bugs)");
    }
}

inline void SetDebugLogging(bool enable) {
    if (enable) {
        setenv("FD_MESA_DEBUG", "1", 1);
        setenv("MESA_DEBUG", "1", 1);
        TURNIP_LOGI("Enabled Turnip debug logging");
    }
}

inline void DisableUbwc(bool disable) {
    if (disable) {
        const char* existing = std::getenv("TU_DEBUG");
        if (existing && std::string(existing).find("noubwc") == std::string::npos) {
            std::string combined = std::string(existing) + ",noubwc";
            setenv("TU_DEBUG", combined.c_str(), 1);
        } else if (!existing) {
            setenv("TU_DEBUG", "noubwc", 1);
        }
        TURNIP_LOGI("Disabled UBWC compression (debug mode)");
    }
}

// Force system memory rendering instead of GMEM
// Required for Adreno 830 where GMEM is broken and causes GPU hangs
inline void SetSysmemMode(bool enable) {
    if (enable) {
        const char* existing = std::getenv("TU_DEBUG");
        if (existing && std::string(existing).find("sysmem") == std::string::npos) {
            std::string combined = std::string(existing) + ",sysmem";
            setenv("TU_DEBUG", combined.c_str(), 1);
        } else if (!existing) {
            setenv("TU_DEBUG", "sysmem", 1);
        }
        TURNIP_LOGI("Enabled sysmem mode (required for Adreno 830)");
    }
}

// Disable Low Resolution Z feature
// Fixes Z-fighting and GPU hangs on some Adreno GPUs (especially A830)
inline void DisableLrz(bool disable) {
    if (disable) {
        const char* existing = std::getenv("TU_DEBUG");
        if (existing && std::string(existing).find("nolrz") == std::string::npos) {
            std::string combined = std::string(existing) + ",nolrz";
            setenv("TU_DEBUG", combined.c_str(), 1);
        } else if (!existing) {
            setenv("TU_DEBUG", "nolrz", 1);
        }
        TURNIP_LOGI("Disabled Low Resolution Z (fixes Z-fighting on some GPUs)");
    }
}

// Get current TU_DEBUG setting
inline const char* GetTuDebug() {
    return std::getenv("TU_DEBUG");
}

// Check if Turnip environment is configured
inline bool IsTurnipConfigured() {
    return std::getenv("TU_DEBUG") != nullptr ||
           std::getenv("FD_DEV_FEATURES") != nullptr ||
           std::getenv("FD_MESA_DEBUG") != nullptr;
}

} // namespace turnip

#endif // AX360E_TURNIP_ENV_H
