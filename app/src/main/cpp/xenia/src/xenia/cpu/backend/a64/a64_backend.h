/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2024 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#ifndef XENIA_CPU_BACKEND_A64_A64_BACKEND_H_
#define XENIA_CPU_BACKEND_A64_A64_BACKEND_H_

#include <memory>

#include "xenia/base/cvar.h"
#include "xenia/base/bit_map.h"
#include "xenia/cpu/backend/backend.h"

DECLARE_int32(a64_extension_mask);
DECLARE_int32(max_stackpoints);
DECLARE_bool(enable_host_guest_stack_synchronization);

namespace xe {
class Exception;
}  // namespace xe
namespace xe {
namespace cpu {
namespace backend {
namespace a64 {

    //static const uintptr_t kExecuteCodeAddrHigh = 0xaull<<32;

class A64CodeCache;

typedef void* (*HostToGuestThunk)(void* target, void* arg0, void* arg1);
typedef void* (*GuestToHostThunk)(void* target, void* arg0, void* arg1);
typedef void (*ResolveFunctionThunk)();

/*
    place guest trampolines in the memory range that the HV normally occupies.
    This way guests can call in via the indirection table and we don't have to
   clobber/reuse an existing memory range The xboxkrnl range is already used by
   export trampolines (see kernel/kernel_module.cc)
*/
    static constexpr uint32_t GUEST_TRAMPOLINE_BASE = 0x80000000;
    static constexpr uint32_t GUEST_TRAMPOLINE_END = 0x80040000;

    static constexpr uint32_t GUEST_TRAMPOLINE_MIN_LEN = 8;

    static constexpr uint32_t MAX_GUEST_TRAMPOLINES =
            (GUEST_TRAMPOLINE_END - GUEST_TRAMPOLINE_BASE) / GUEST_TRAMPOLINE_MIN_LEN;

#define RESERVE_BLOCK_SHIFT 16

#define RESERVE_NUM_ENTRIES \
  ((1024ULL * 1024ULL * 1024ULL * 4ULL) >> RESERVE_BLOCK_SHIFT)
// https://codalogic.com/blog/2022/12/06/Exploring-PowerPCs-read-modify-write-operations
        struct ReserveHelper {
            uint64_t blocks[RESERVE_NUM_ENTRIES / 64];

            ReserveHelper() { memset(blocks, 0, sizeof(blocks)); }
        };

        struct A64BackendStackpoint {
            uint64_t host_stack_;
            uint32_t guest_stack_;
            // pad to 16 bytes so we never end up having a 64 bit load/store for
            // host_stack_ straddling two lines. Consider this field reserved for future
            // use
            uint32_t guest_return_address_;
        };
        struct A64BackendContext {
            union {
                uint64x2_t helper_scratch_xmms[4];
                uint64_t helper_scratch_u64s[8];
                uint32_t helper_scratch_u32s[16];
            };
            ReserveHelper* reserve_helper_;
            uint64_t cached_reserve_value_;
            // guest_tick_count is used if inline_loadclock is used
            uint64_t* guest_tick_count;
            // records mapping of host_stack to guest_stack
            A64BackendStackpoint* stackpoints;
            uint64_t cached_reserve_offset;
            uint32_t cached_reserve_bit;
            uint32_t current_stackpoint_depth;
            uint32_t mxcsr_fpu;  // currently, the way we implement rounding mode
            // affects both vmx and the fpu
            uint32_t mxcsr_vmx;
            // bit 0 = 0 if mxcsr is fpu, else it is vmx
            // bit 1 = got reserve
            uint32_t flags;
            uint32_t Ox1000;  // constant 0x1000 so we can shrink each tail emitted
            // add of it by... 2 bytes lol
        };
class A64Backend : public Backend {
 public:
  //static const uint32_t kForceReturnAddress = 0x9FFF0000u;

  explicit A64Backend();
  ~A64Backend() override;

  A64CodeCache* code_cache() const { return code_cache_.get(); }
  uintptr_t emitter_data() const { return emitter_data_; }

  // Call a generated function, saving all stack parameters.
  HostToGuestThunk host_to_guest_thunk() const { return host_to_guest_thunk_; }
  // Function that guest code can call to transition into host code.
  GuestToHostThunk guest_to_host_thunk() const { return guest_to_host_thunk_; }
  // Function that thunks to the ResolveFunction in A64Emitter.
  ResolveFunctionThunk resolve_function_thunk() const {
    return resolve_function_thunk_;
  }

  bool Initialize(Processor* processor) override;

  void InitializeCodeCache(const std::filesystem::path& cache_root,
                           uint32_t title_id) override;

  void CommitExecutableRange(uint32_t guest_low, uint32_t guest_high) override;

  std::unique_ptr<Assembler> CreateAssembler() override;

  std::unique_ptr<GuestFunction> CreateGuestFunction(Module* module,
                                                     uint32_t address) override;

  uint64_t CalculateNextHostInstruction(ThreadDebugInfo* thread_info,
                                        uint64_t current_pc) override;

  void InstallBreakpoint(Breakpoint* breakpoint) override;
  void InstallBreakpoint(Breakpoint* breakpoint, Function* fn) override;
  void UninstallBreakpoint(Breakpoint* breakpoint) override;
    virtual void InitializeBackendContext(void* ctx) override;
    virtual void DeinitializeBackendContext(void* ctx) override;
    virtual void PrepareForReentry(void* ctx) override;

    A64BackendContext* BackendContextForGuestContext(void* ctx) {
        return reinterpret_cast<A64BackendContext*>(
                reinterpret_cast<intptr_t>(ctx) - sizeof(A64BackendContext));
    }

    virtual bool PopulatePseudoStacktrace(GuestPseudoStackTrace* st) override;
    virtual uint32_t CreateGuestTrampoline(GuestTrampolineProc proc,
                                           void* userdata1, void* userdata2,
                                           bool long_term) override;

    virtual void FreeGuestTrampoline(uint32_t trampoline_addr) override;
 private:
  static bool ExceptionCallbackThunk(Exception* ex, void* data);
  bool ExceptionCallback(Exception* ex);

  uintptr_t capstone_handle_ = 0;

  std::unique_ptr<A64CodeCache> code_cache_;
  uintptr_t emitter_data_ = 0;

  HostToGuestThunk host_to_guest_thunk_;
  GuestToHostThunk guest_to_host_thunk_;
  ResolveFunctionThunk resolve_function_thunk_;

  // Persistent code cache state
  std::filesystem::path code_cache_path_;
  uint32_t code_cache_title_id_ = 0;

private:

    //alignas(64) ReserveHelper reserve_helper_;
    // allocates 8-byte aligned addresses in a normally not executable guest
    // address
    // range that will be used to dispatch to host code
    BitMap guest_trampoline_address_bitmap_;
    uint8_t* guest_trampoline_memory_;
};

}  // namespace a64
}  // namespace backend
}  // namespace cpu
}  // namespace xe

#endif  // XENIA_CPU_BACKEND_A64_A64_BACKEND_H_
