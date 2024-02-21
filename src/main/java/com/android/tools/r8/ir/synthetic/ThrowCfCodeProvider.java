// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.synthetic;

import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a method that just throws an exception with empty <init> with *any* signature passed,
 * so the method can be inserted in a hierarchy and be called with normal virtual dispatch.
 */
public class ThrowCfCodeProvider extends SyntheticCfCodeProvider {

  private final DexType exceptionType;
  private final Position position;

  public ThrowCfCodeProvider(
      AppView<?> appView, DexType holder, DexType exceptionType, Position position) {
    super(appView, holder);
    this.exceptionType = exceptionType;
    this.position = position;
  }

  @Override
  public CfCode generateCfCode() {
    List<CfInstruction> instructions = new ArrayList<>();
    if (position != null) {
      CfLabel entryLabel = new CfLabel();
      instructions.add(entryLabel);
      instructions.add(new CfPosition(entryLabel, position));
    }
    instructions.add(new CfNew(exceptionType));
    instructions.add(new CfStackInstruction(Opcode.Dup));
    DexMethod init = appView.dexItemFactory().createInstanceInitializer(exceptionType);
    instructions.add(new CfInvoke(INVOKESPECIAL, init, false));
    instructions.add(new CfThrow());
    return standardCfCodeFromInstructions(instructions);
  }
}
