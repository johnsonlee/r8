// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SwitchHelperGenerator {

  private final DexItemFactory factory;
  private final DexType type;
  private final DexField cacheField;
  private final DexMethod getter;

  SwitchHelperGenerator(
      SyntheticProgramClassBuilder builder,
      AppView<?> appView,
      Consumer<List<CfInstruction>> generator) {
    this.factory = appView.dexItemFactory();
    this.type = builder.getType();
    this.cacheField =
        factory.createField(type, factory.objectArrayType, factory.createString("switchCases"));
    this.getter =
        factory.createMethod(
            type,
            factory.createProto(factory.objectArrayType),
            factory.createString("getSwitchCases"));
    synthesizeStaticField(builder);
    synthesizeStaticMethod(builder, generator);
  }

  private void synthesizeStaticField(SyntheticProgramClassBuilder builder) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(cacheField)
                .setAccessFlags(FieldAccessFlags.createPublicStaticSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
  }

  /**
   * Generates the following code:
   *
   * <pre>
   *   if (switchCases != null) {
   *     return switchCases;
   *   }
   *   switchCases = <generate array from bootstrap method>;
   *   return switchCases;
   * </pre>
   *
   * We don't lock since the array generated is always the same and is never used in identity
   * checks.
   */
  private void synthesizeStaticMethod(
      SyntheticProgramClassBuilder builder, Consumer<List<CfInstruction>> generator) {
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfStaticFieldRead(cacheField));
    CfLabel target = new CfLabel();
    instructions.add(new CfIf(IfType.EQ, ValueType.OBJECT, target));
    instructions.add(new CfStaticFieldRead(cacheField));
    instructions.add(new CfReturn(ValueType.OBJECT));
    instructions.add(target);
    instructions.add(new CfFrame());
    generator.accept(instructions);
    instructions.add(new CfStackInstruction(Opcode.Dup));
    instructions.add(new CfStaticFieldWrite(cacheField));
    instructions.add(new CfReturn(ValueType.OBJECT));

    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(getter)
                .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                .setCode(new CfCode(type, 7, 3, instructions))
                .disableAndroidApiLevelCheck()
                .build()));
  }
}
