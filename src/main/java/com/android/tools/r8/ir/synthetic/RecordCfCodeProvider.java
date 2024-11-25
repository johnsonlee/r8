// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArithmeticBinop.Opcode;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfCmp;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfRecordFieldValues;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Cmp.Bias;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.records.RecordInstructionDesugaring;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public abstract class RecordCfCodeProvider extends SyntheticCfCodeProvider {

  protected RecordCfCodeProvider(AppView<?> appView, DexType holder) {
    super(appView, holder);
  }

  /**
   * Generates a method which hashes all the fields. If outline, the method has all fields as
   * parameters, else it's a local public method in the record class.
   */
  public static class RecordHashCfCodeProvider extends RecordCfCodeProvider {

    private final List<DexField> fieldsToHash;
    private final boolean outline;

    public RecordHashCfCodeProvider(
        AppView<?> appView, DexType holder, List<DexField> fieldsToHash, boolean outline) {
      super(appView, holder);
      this.fieldsToHash = fieldsToHash;
      this.outline = outline;
    }

    public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
      factory.createSynthesizedType("Ljava/lang/Objects;");
      factory.createSynthesizedType("Ljava/lang/Double;");
      factory.createSynthesizedType("Ljava/lang/Float;");
      factory.createSynthesizedType("Ljava/lang/Boolean;");
      factory.createSynthesizedType("Ljava/lang/Long;");
    }

    private void addInvokeStatic(List<CfInstruction> instructions, DexMethod method) {
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method, false));
    }

    private void pushHashCode(List<CfInstruction> instructions, DexField field, int index) {
      DexItemFactory factory = appView.dexItemFactory();
      ValueType valueType = ValueType.fromDexType(field.getType());
      if (outline) {
        instructions.add(new CfLoad(valueType, index));
      } else {
        instructions.add(new CfLoad(ValueType.OBJECT, 0));
        instructions.add(new CfInstanceFieldRead(field));
      }
      if (valueType == ValueType.DOUBLE) {
        addInvokeStatic(instructions, factory.doubleMembers.staticHashCode);
      } else if (valueType == ValueType.FLOAT) {
        addInvokeStatic(instructions, factory.floatMembers.staticHashCode);
      } else if (field.getType().isBooleanType()) {
        addInvokeStatic(instructions, factory.booleanMembers.staticHashCode);
      } else if (valueType == ValueType.LONG) {
        addInvokeStatic(instructions, factory.longMembers.staticHashCode);
      } else if (valueType.isObject()) {
        addInvokeStatic(instructions, factory.objectsMethods.hashCode);
      } else {
        assert valueType == ValueType.INT;
      }
    }

    @Override
    public CfCode generateCfCode() {
      int stackUsed = 0;
      int localsUsed = 0;
      List<CfInstruction> instructions = new ArrayList<>();
      if (fieldsToHash.isEmpty()) {
        stackUsed++;
        instructions.add(
            new CfConstNumber(
                RecordInstructionDesugaring.fixedHashCodeForEmptyRecord(), ValueType.INT));
      } else {
        pushHashCode(instructions, fieldsToHash.get(0), 0);
        boolean seenWide = fieldsToHash.get(0).getType().isWideType();
        int argIndex = fieldsToHash.get(0).getType().getRequiredRegisters();
        for (int i = 1; i < fieldsToHash.size(); i++) {
          instructions.add(new CfConstNumber(31, ValueType.INT));
          instructions.add(new CfArithmeticBinop(Opcode.Mul, NumericType.INT));
          pushHashCode(instructions, fieldsToHash.get(i), argIndex);
          seenWide |= fieldsToHash.get(i).getType().isWideType();
          instructions.add(new CfArithmeticBinop(Opcode.Add, NumericType.INT));
          argIndex += fieldsToHash.get(i).getType().getRequiredRegisters();
        }
        stackUsed += seenWide ? 3 : 2;
        localsUsed += argIndex;
      }
      instructions.add(new CfReturn(ValueType.INT));
      return new CfCode(getHolder(), stackUsed, localsUsed, instructions);
    }
  }

  public static class RecordEqCfCodeProvider extends RecordCfCodeProvider {

    private final List<DexField> fieldsToCompare;

    public RecordEqCfCodeProvider(
        AppView<?> appView, DexType holder, List<DexField> fieldsToCompare) {
      super(appView, holder);
      this.fieldsToCompare = fieldsToCompare;
    }

    public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
      factory.createSynthesizedType("Ljava/lang/Objects;");
    }

    private void addInvokeStatic(List<CfInstruction> instructions, DexMethod method) {
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method, false));
    }

    private void pushComparison(
        List<CfInstruction> instructions, DexField field, CfLabel falseLabel) {
      ValueType valueType = ValueType.fromDexType(field.getType());
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(new CfInstanceFieldRead(field));
      instructions.add(new CfLoad(ValueType.OBJECT, 2));
      instructions.add(new CfInstanceFieldRead(field));
      if (valueType == ValueType.DOUBLE) {
        instructions.add(new CfCmp(Bias.LT, NumericType.DOUBLE));
        instructions.add(new CfIf(IfType.NE, ValueType.INT, falseLabel));
      } else if (valueType == ValueType.FLOAT) {
        instructions.add(new CfCmp(Bias.LT, NumericType.FLOAT));
        instructions.add(new CfIf(IfType.NE, ValueType.INT, falseLabel));
      } else if (valueType == ValueType.LONG) {
        instructions.add(new CfCmp(Bias.NONE, NumericType.LONG));
        instructions.add(new CfIf(IfType.NE, ValueType.INT, falseLabel));
      } else if (valueType.isObject()) {
        addInvokeStatic(instructions, appView.dexItemFactory().objectsMethods.equals);
        instructions.add(new CfIf(IfType.EQ, ValueType.INT, falseLabel));
      } else {
        assert valueType == ValueType.INT;
        instructions.add(new CfIfCmp(IfType.NE, ValueType.INT, falseLabel));
      }
    }

    @Override
    public CfCode generateCfCode() {
      CfFrame frame = buildFrame();
      List<CfInstruction> instructions = new ArrayList<>();
      CfLabel falseLabel = new CfLabel();
      instructions.add(new CfLoad(ValueType.OBJECT, 1));
      instructions.add(new CfInstanceOf(getHolder()));
      instructions.add(new CfIf(IfType.EQ, ValueType.INT, falseLabel));
      instructions.add(new CfLoad(ValueType.OBJECT, 1));
      instructions.add(new CfCheckCast(getHolder()));
      instructions.add(new CfStore(ValueType.OBJECT, 2));
      for (int i = 0; i < fieldsToCompare.size(); i++) {
        pushComparison(instructions, fieldsToCompare.get(i), falseLabel);
      }
      instructions.add(new CfConstNumber(1, ValueType.INT));
      instructions.add(new CfReturn(ValueType.INT));
      instructions.add(falseLabel);
      instructions.add(frame);
      instructions.add(new CfConstNumber(0, ValueType.INT));
      instructions.add(new CfReturn(ValueType.INT));
      return standardCfCodeFromInstructions(instructions);
    }

    private CfFrame buildFrame() {
      return CfFrame.builder()
          .appendLocal(FrameType.initialized(getHolder()))
          .appendLocal(FrameType.initialized(appView.dexItemFactory().objectType))
          .build();
    }
  }

  /**
   * Generates a method which answers all field values as an array of objects. If the field value is
   * a primitive type, it uses the primitive wrapper to wrap it.
   *
   * <p>The fields in parameters are in the order where they should be in the array generated by the
   * method, which is not necessarily the class instanceFields order.
   *
   * <p>Example: <code>record Person{ int age; String name;}</code>
   *
   * <p><code>Object[] getFieldsAsObjects() {
   * Object[] fields = new Object[2];
   * fields[0] = name;
   * fields[1] = Integer.valueOf(age);
   * return fields;</code>
   */
  public static class RecordGetFieldsAsObjectsCfCodeProvider extends SyntheticCfCodeProvider {

    public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
      factory.createSynthesizedType("[Ljava/lang/Object;");
      factory.primitiveToBoxed.forEach(
          (primitiveType, boxedType) -> {
            factory.createSynthesizedType(primitiveType.toDescriptorString());
            factory.createSynthesizedType(boxedType.toDescriptorString());
          });
    }

    @Override
    protected int defaultMaxStack() {
      return fields.length + 3;
    }

    private final DexField[] fields;

    public RecordGetFieldsAsObjectsCfCodeProvider(
        AppView<?> appView, DexType holder, DexField[] fields) {
      super(appView, holder);
      this.fields = fields;
    }

    @Override
    public CfCode generateCfCode() {
      // Stack layout:
      // 0 : receiver (the record instance)
      // 1 : the array to return
      // 2+: spills
      return appView.enableWholeProgramOptimizations()
          ? generateCfCodeWithRecordModeling()
          : generateCfCodeWithArray();
    }

    private CfCode generateCfCodeWithArray() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      // Object[] fields = new Object[*length*];
      instructions.add(new CfConstNumber(fields.length, ValueType.INT));
      instructions.add(new CfNewArray(factory.objectArrayType));
      instructions.add(new CfStore(ValueType.OBJECT, 1));
      // fields[*i*] = this.*field* || *PrimitiveWrapper*.valueOf(this.*field*);
      for (int i = 0; i < fields.length; i++) {
        DexField field = fields[i];
        instructions.add(new CfLoad(ValueType.OBJECT, 1));
        instructions.add(new CfConstNumber(i, ValueType.INT));
        loadFieldAsObject(instructions, field);
        instructions.add(new CfArrayStore(MemberType.OBJECT));
      }
      // return fields;
      instructions.add(new CfLoad(ValueType.OBJECT, 1));
      instructions.add(new CfReturn(ValueType.OBJECT));
      return standardCfCodeFromInstructions(instructions);
    }

    private CfCode generateCfCodeWithRecordModeling() {
      List<CfInstruction> instructions = new ArrayList<>();
      // fields[*i*] = this.*field* || *PrimitiveWrapper*.valueOf(this.*field*);
      for (DexField field : fields) {
        loadFieldAsObject(instructions, field);
      }
      // return recordFieldValues(fields);
      instructions.add(new CfRecordFieldValues(fields));
      instructions.add(new CfReturn(ValueType.OBJECT));
      return standardCfCodeFromInstructions(instructions);
    }

    @SuppressWarnings("ReferenceEquality")
    private void loadFieldAsObject(List<CfInstruction> instructions, DexField field) {
      DexItemFactory factory = appView.dexItemFactory();
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(new CfInstanceFieldRead(field));
      if (field.type.isPrimitiveType()) {
        factory.primitiveToBoxed.forEach(
            (primitiveType, boxedType) -> {
              if (primitiveType == field.type) {
                instructions.add(
                    new CfInvoke(
                        Opcodes.INVOKESTATIC,
                        factory.createMethod(
                            boxedType,
                            factory.createProto(boxedType, primitiveType),
                            factory.valueOfMethodName),
                        false));
              }
            });
      }
    }
  }
}
