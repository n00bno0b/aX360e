/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2022 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include "xenia/kernel/util/shim_utils.h"
#include "xenia/kernel/xboxkrnl/xboxkrnl_private.h"

namespace xe {
namespace kernel {
namespace xboxkrnl {

dword_result_t HidReadKeys_entry(dword_t unk1, unknown_t unk2, unknown_t unk3) {
  /* TODO(gibbed):
   * Games check for the following errors:
   *   0xC000009D - translated to 0x48F  - ERROR_DEVICE_NOT_CONNECTED
   *   0x103      - translated to 0x10D2 - ERROR_EMPTY
   * Other errors appear to be ignored?
   *
   * unk1 is 0
   * unk2 is a pointer to &unk3[2], possibly a 6-byte buffer
   * unk3 is a pointer to a 20-byte buffer
   */
  return 0xC000009D;
}
DECLARE_XBOXKRNL_EXPORT1(HidReadKeys, kInput, kStub);

dword_result_t XInputdFFGetDeviceInfo_entry(dword_t user_index, pointer_t<void> info) {
  return X_ERROR_DEVICE_NOT_CONNECTED;
}
DECLARE_XBOXKRNL_EXPORT1(XInputdFFGetDeviceInfo, kInput, kStub);

dword_result_t XInputdFFSetEffect_entry(dword_t user_index, pointer_t<void> effect) {
  return X_ERROR_DEVICE_NOT_CONNECTED;
}
DECLARE_XBOXKRNL_EXPORT1(XInputdFFSetEffect, kInput, kStub);

dword_result_t XInputdFFUpdateEffect_entry(dword_t user_index, pointer_t<void> effect) {
  return X_ERROR_DEVICE_NOT_CONNECTED;
}
DECLARE_XBOXKRNL_EXPORT1(XInputdFFUpdateEffect, kInput, kStub);

dword_result_t XInputdFFEffectOperation_entry(dword_t user_index, dword_t op) {
  return X_ERROR_DEVICE_NOT_CONNECTED;
}
DECLARE_XBOXKRNL_EXPORT1(XInputdFFEffectOperation, kInput, kStub);

dword_result_t XInputdFFDeviceControl_entry(dword_t user_index, dword_t control, pointer_t<void> data) {
  return X_ERROR_DEVICE_NOT_CONNECTED;
}
DECLARE_XBOXKRNL_EXPORT1(XInputdFFDeviceControl, kInput, kStub);

dword_result_t XInputdFFSetDeviceGain_entry(dword_t user_index, dword_t gain) {
  return X_ERROR_DEVICE_NOT_CONNECTED;
}
DECLARE_XBOXKRNL_EXPORT1(XInputdFFSetDeviceGain, kInput, kStub);

dword_result_t XInputdFFCancelIo_entry(dword_t user_index) {
  return X_ERROR_DEVICE_NOT_CONNECTED;
}
DECLARE_XBOXKRNL_EXPORT1(XInputdFFCancelIo, kInput, kStub);

dword_result_t XInputdFFSetRumble_entry(dword_t user_index, pointer_t<void> rumble) {
  return X_ERROR_SUCCESS;
}
DECLARE_XBOXKRNL_EXPORT1(XInputdFFSetRumble, kInput, kStub);

}  // namespace xboxkrnl
}  // namespace kernel
}  // namespace xe

DECLARE_XBOXKRNL_EMPTY_REGISTER_EXPORTS(Hid);
