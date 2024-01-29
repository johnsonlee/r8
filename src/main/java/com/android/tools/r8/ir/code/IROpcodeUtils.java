// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import static com.android.tools.r8.lightir.LirOpcodes.INVOKEDIRECT;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKEDIRECT_ITF;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKEINTERFACE;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKESTATIC;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKESTATIC_ITF;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKESUPER;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKESUPER_ITF;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKEVIRTUAL;

import com.android.tools.r8.errors.Unreachable;

public class IROpcodeUtils {

  public static int fromLirInvokeOpcode(int opcode) {
    switch (opcode) {
      case INVOKEDIRECT:
      case INVOKEDIRECT_ITF:
        return Opcodes.INVOKE_DIRECT;
      case INVOKEINTERFACE:
        return Opcodes.INVOKE_INTERFACE;
      case INVOKESTATIC:
      case INVOKESTATIC_ITF:
        return Opcodes.INVOKE_STATIC;
      case INVOKESUPER:
      case INVOKESUPER_ITF:
        return Opcodes.INVOKE_SUPER;
      case INVOKEVIRTUAL:
        return Opcodes.INVOKE_VIRTUAL;
      default:
        throw new Unreachable();
    }
  }
}
