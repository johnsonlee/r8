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
}
