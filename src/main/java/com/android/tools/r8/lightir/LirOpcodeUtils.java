// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import static com.android.tools.r8.lightir.LirOpcodes.INVOKEDIRECT;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKEDIRECT_ITF;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKEINTERFACE;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKESTATIC;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKESTATIC_ITF;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKESUPER;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKESUPER_ITF;
import static com.android.tools.r8.lightir.LirOpcodes.INVOKEVIRTUAL;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.InvokeType;

public class LirOpcodeUtils {

  public static boolean getInterfaceBitFromInvokeOpcode(int opcode) {
    switch (opcode) {
      case INVOKEDIRECT_ITF:
      case INVOKEINTERFACE:
      case INVOKESTATIC_ITF:
      case INVOKESUPER_ITF:
        return true;
      default:
        assert opcode == INVOKEDIRECT
            || opcode == INVOKESTATIC
            || opcode == INVOKESUPER
            || opcode == INVOKEVIRTUAL;
        return false;
    }
  }

  public static InvokeType getInvokeType(int opcode) {
    assert isInvokeMethod(opcode);
    switch (opcode) {
      case INVOKEDIRECT:
      case INVOKEDIRECT_ITF:
        return InvokeType.DIRECT;
      case INVOKEINTERFACE:
        return InvokeType.INTERFACE;
      case INVOKESTATIC:
      case INVOKESTATIC_ITF:
        return InvokeType.STATIC;
      case INVOKESUPER:
      case INVOKESUPER_ITF:
        return InvokeType.SUPER;
      case INVOKEVIRTUAL:
        return InvokeType.VIRTUAL;
      default:
        throw new Unreachable();
    }
  }

  public static boolean isInvokeDirect(int opcode) {
    return opcode == INVOKEDIRECT || opcode == INVOKEDIRECT_ITF;
  }

  public static boolean isInvokeInterface(int opcode) {
    return opcode == INVOKEINTERFACE;
  }

  public static boolean isInvokeMethod(int opcode) {
    switch (opcode) {
      case INVOKEDIRECT:
      case INVOKEDIRECT_ITF:
      case INVOKEINTERFACE:
      case INVOKESTATIC:
      case INVOKESTATIC_ITF:
      case INVOKESUPER:
      case INVOKESUPER_ITF:
      case INVOKEVIRTUAL:
        return true;
      default:
        return false;
    }
  }

  public static boolean isInvokeSuper(int opcode) {
    return opcode == INVOKESUPER || opcode == INVOKESUPER_ITF;
  }

  public static boolean isInvokeVirtual(int opcode) {
    return opcode == INVOKEVIRTUAL;
  }
}
