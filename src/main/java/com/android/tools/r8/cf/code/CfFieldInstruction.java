// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;


import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class CfFieldInstruction extends CfInstruction {

  private final DexField field;

  private static void specify(StructuralSpecification<CfFieldInstruction, ?> spec) {
    spec.withInt(CfFieldInstruction::getAsmOpcode).withItem(CfFieldInstruction::getField);
  }

  public CfFieldInstruction(DexField field) {
    this.field = field;
  }

  public static CfFieldInstruction create(int opcode, DexField field) {
    switch (opcode) {
      case Opcodes.GETSTATIC:
        return new CfStaticFieldRead(field);
      case Opcodes.PUTSTATIC:
        return new CfStaticFieldWrite(field);
      case Opcodes.GETFIELD:
        return new CfInstanceFieldRead(field);
      case Opcodes.PUTFIELD:
        return new CfInstanceFieldWrite(field);
      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }
  }

  public DexField getField() {
    return field;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return visitor.visit(this, other.asFieldInstruction(), CfFieldInstruction::specify);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, CfFieldInstruction::specify);
  }

  public abstract CfFieldInstruction createWithField(DexField field);

  @Override
  public CfFieldInstruction asFieldInstruction() {
    return this;
  }

  @Override
  public boolean isFieldInstruction() {
    return true;
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      GraphLens codeLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    DexField rewrittenField = graphLens.lookupField(field, codeLens);
    String owner = namingLens.lookupInternalName(rewrittenField.holder);
    String name = namingLens.lookupName(field).toString();
    String desc = namingLens.lookupDescriptor(rewrittenField.type).toString();
    visitor.visitFieldInsn(getAsmOpcode(), owner, name, desc);
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 3;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
