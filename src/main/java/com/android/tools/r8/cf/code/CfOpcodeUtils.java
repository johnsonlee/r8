// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.CfCompareHelper;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class CfOpcodeUtils {

  public static void acceptCfFieldInstructionOpcodes(IntConsumer consumer) {
    consumer.accept(Opcodes.GETSTATIC);
    consumer.accept(Opcodes.PUTSTATIC);
    consumer.accept(Opcodes.GETFIELD);
    consumer.accept(Opcodes.PUTFIELD);
  }

  public static void acceptCfInvokeOpcodes(IntConsumer consumer) {
    consumer.accept(Opcodes.INVOKEVIRTUAL);
    consumer.accept(Opcodes.INVOKESPECIAL);
    consumer.accept(Opcodes.INVOKESTATIC);
    consumer.accept(Opcodes.INVOKEINTERFACE);
  }

  public static void acceptCfTypeInstructionOpcodes(
      IntConsumer consumer, IntConsumer compareToIdConsumer) {
    consumer.accept(Opcodes.CHECKCAST);
    consumer.accept(Opcodes.INSTANCEOF);
    consumer.accept(Opcodes.MULTIANEWARRAY);
    consumer.accept(Opcodes.NEW);
    consumer.accept(Opcodes.NEWARRAY);
    consumer.accept(Opcodes.ANEWARRAY);
    compareToIdConsumer.accept(CfCompareHelper.CONST_DYNAMIC_COMPARE_ID);
  }
}
