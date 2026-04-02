// SPDX-License-Identifier: WTFPL

#ifndef AX360E_AX360E_EMU_H
#define AX360E_AX360E_EMU_H

#include <atomic>
#include "xenia/ui/windowed_app_context.h"

class AndroidWindowedAppContext final : public xe::ui::WindowedAppContext {
public:

    volatile int event=0;
    static const int EVENT_EXECUTE_PENDING_FUNCTIONS = 1;
    static const int EVENT_QUIT = 2;

    std::atomic<bool> paint_requested{false};

    pthread_mutex_t mutex;
    pthread_cond_t cond;

    void NotifyUILoopOfPendingFunctions() override;

    void PlatformQuitFromUIThread() override;

    void setup_ui_thr_id(std::thread::id id);

    void request_paint();

    void main_loop();

    AndroidWindowedAppContext();

    ~AndroidWindowedAppContext();

};

#endif //AX360E_AX360E_EMU_H
