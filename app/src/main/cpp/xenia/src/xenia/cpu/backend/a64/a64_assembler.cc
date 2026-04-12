/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2024 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include "xenia/cpu/backend/a64/a64_assembler.h"

#include <climits>

#include "third_party/capstone/include/capstone/arm64.h"
#include "third_party/capstone/include/capstone/capstone.h"
#include "xenia/base/logging.h"
#include "xenia/base/profiling.h"
#include "xenia/base/reset_scope.h"
#include "xenia/base/string.h"
#include "xenia/cpu/backend/a64/a64_backend.h"
#include "xenia/cpu/backend/a64/a64_code_cache.h"
#include "xenia/cpu/backend/a64/a64_emitter.h"
#include "xenia/cpu/backend/a64/a64_function.h"
#include "xenia/cpu/cpu_flags.h"
#include "xenia/cpu/hir/hir_builder.h"
#include "xenia/cpu/hir/label.h"
#include "xenia/cpu/hir/opcodes.h"
#include "xenia/cpu/processor.h"
#include "oaknut/oaknut_exception.hpp"

namespace xe {
namespace cpu {
namespace backend {
namespace a64 {

using xe::cpu::hir::HIRBuilder;

A64Assembler::A64Assembler(A64Backend* backend)
    : Assembler(backend), a64_backend_(backend), capstone_handle_(0) {
  if (cs_open(CS_ARCH_AARCH64, CS_MODE_LITTLE_ENDIAN, &capstone_handle_) !=
      CS_ERR_OK) {
    assert_always("Failed to initialize capstone");
  }
  cs_option(capstone_handle_, CS_OPT_SYNTAX, CS_OPT_SYNTAX_INTEL);
  cs_option(capstone_handle_, CS_OPT_DETAIL, CS_OPT_OFF);
}

A64Assembler::~A64Assembler() {
  // Emitter must be freed before the allocator.
  emitter_.reset();

  if (capstone_handle_) {
    cs_close(&capstone_handle_);
  }
}

bool A64Assembler::Initialize() {
  if (!Assembler::Initialize()) {
    return false;
  }

  emitter_.reset(new A64Emitter(a64_backend_));

  return true;
}

void A64Assembler::Reset() {
  string_buffer_.Reset();
  Assembler::Reset();
}

bool A64Assembler::Assemble(GuestFunction* function, HIRBuilder* builder,
                            uint32_t debug_info_flags,
                            std::unique_ptr<FunctionDebugInfo> debug_info) {
  SCOPE_profile_cpu_f("cpu");

  // Reset when we leave.
  xe::make_reset_scope(this);

  // Lower HIR -> a64.
  void* machine_code = nullptr;
  size_t code_size = 0;
  const auto reset_emitter = [this]() {
    emitter_.reset(new A64Emitter(a64_backend_));
  };
  const auto log_emit_failure = [this, function, builder](const char* reason) {
    const uint32_t address = function ? function->address() : 0;
    const uint32_t end_address =
        function && function->has_end_address() ? function->end_address() : 0;
    const char* name =
        function && !function->name().empty() ? function->name().c_str()
                                              : "<unnamed>";
    XELOGE(
        "A64Assembler: {} while assembling guest function {:08X}-{:08X} {}",
        reason, address, end_address, name);

    if (emitter_) {
      const auto* instr = emitter_->current_instr();
      if (instr && instr->opcode) {
        XELOGE(
            "A64Assembler: active HIR opcode={} block={} ordinal={} flags={:04X}",
            instr->opcode->name, instr->block ? instr->block->ordinal : 0,
            instr->ordinal, instr->flags);
      }

      const uint32_t last_guest_address = emitter_->last_guest_address();
      const uint32_t last_hir_offset = emitter_->last_hir_offset();
      if (last_guest_address || last_hir_offset) {
        XELOGE(
            "A64Assembler: last source guest={:08X} hir_offset={:08X}",
            last_guest_address, last_hir_offset);
      }
    }

    const auto* instr = emitter_ ? emitter_->current_instr() : nullptr;
    if (!instr || !instr->block) {
      return;
    }

    string_buffer_.Reset();
    string_buffer_.AppendFormat("A64Assembler: HIR window around failure for {:08X}:\n",
                                address);

    const hir::Instr* window_start = instr;
    for (int i = 0; i < 3 && window_start->prev; ++i) {
      window_start = window_start->prev;
    }

    const hir::Instr* cursor = window_start;
    for (int i = 0; cursor && i < 7; ++i, cursor = cursor->next) {
      if (cursor->opcode->flags & hir::OPCODE_FLAG_HIDE) {
        continue;
      }

      const bool is_current = cursor == instr;
      string_buffer_.AppendFormat(
          "  {} block={} ordinal={} opcode={}",
          is_current ? ">>" : "  ",
          cursor->block ? cursor->block->ordinal : 0,
          cursor->ordinal,
          cursor->opcode ? cursor->opcode->name : "<null>");

      if (cursor->opcode == &hir::OPCODE_SOURCE_OFFSET_info) {
        string_buffer_.AppendFormat(" guest={:08X}",
                                    static_cast<uint32_t>(cursor->src1.offset));
      }
      if (cursor->flags) {
        string_buffer_.AppendFormat(" flags={:04X}", cursor->flags);
      }
      string_buffer_.Append('\n');
    }

    XELOGE("{}", string_buffer_.to_string_view());
  };

  try {
    if (!emitter_->Emit(function, builder, debug_info_flags, debug_info.get(),
                        &machine_code, &code_size, &function->source_map())) {
      reset_emitter();
      return false;
    }
  } catch (const oaknut::OaknutException& e) {
    log_emit_failure(e.what());
    reset_emitter();
    return false;
  } catch (const std::exception& e) {
    log_emit_failure(e.what());
    reset_emitter();
    return false;
  } catch (...) {
    log_emit_failure("unknown exception");
    reset_emitter();
    return false;
  }

  // Stash generated machine code.
  if (debug_info_flags & DebugInfoFlags::kDebugInfoDisasmMachineCode) {
    DumpMachineCode(machine_code, code_size, function->source_map(),
                    &string_buffer_);
    debug_info->set_machine_code_disasm(xe_strdup(string_buffer_.buffer()));
    string_buffer_.Reset();
  }

  function->set_debug_info(std::move(debug_info));

    //flush_icache(machine_code, code_size);
  static_cast<A64Function*>(function)->Setup(
      reinterpret_cast<uint8_t*>(machine_code), code_size);

  // Install into indirection table.
  const uint64_t host_address = reinterpret_cast<uint64_t>(machine_code);
  //assert_true((host_address >> 32) == 0);
    assert((host_address&0xffffffff00000000ull)==reinterpret_cast<A64CodeCache*>(backend_->code_cache())->execute_address_high());

  reinterpret_cast<A64CodeCache*>(backend_->code_cache())
      ->AddIndirection(function->address(),
                       static_cast<uint32_t>(host_address));

  return true;
}

void A64Assembler::DumpMachineCode(
    void* machine_code, size_t code_size,
    const std::vector<SourceMapEntry>& source_map, StringBuffer* str) {
  if (source_map.empty()) {
    return;
  }
  auto source_map_index = 0;
  uint32_t next_code_offset = source_map[0].code_offset;

  const uint8_t* code_ptr = reinterpret_cast<uint8_t*>(machine_code);
  size_t remaining_code_size = code_size;
  uint64_t address = uint64_t(machine_code);
  cs_insn insn = {0};
  while (remaining_code_size &&
         cs_disasm_iter(capstone_handle_, &code_ptr, &remaining_code_size,
                        &address, &insn)) {
    // Look up source offset.
    auto code_offset =
        uint32_t(code_ptr - reinterpret_cast<uint8_t*>(machine_code));
    if (code_offset >= next_code_offset &&
        source_map_index < source_map.size()) {
      auto& source_map_entry = source_map[source_map_index];
      str->AppendFormat("{:08X} ", source_map_entry.guest_address);
      ++source_map_index;
      next_code_offset = source_map_index < source_map.size()
                             ? source_map[source_map_index].code_offset
                             : UINT_MAX;
    } else {
      str->Append("         ");
    }

    str->AppendFormat("{:08X}      {:<6} {}\n", uint32_t(insn.address),
                      insn.mnemonic, insn.op_str);
  }
}

}  // namespace a64
}  // namespace backend
}  // namespace cpu
}  // namespace xe
