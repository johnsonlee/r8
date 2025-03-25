// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfSwitch;
import com.android.tools.r8.cf.code.CfSwitch.Kind;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueNumber;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IntBox;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class TypeSwitchSyntheticCfCodeProvider extends SyntheticCfCodeProvider {

  private final List<DexValue> bootstrapArgs;
  private final DexType arg0Type;
  private final Dispatcher dispatcher;
  private final DexMethod intEq;
  private final DexMethod enumEq;
  private final DexField enumFieldCache;

  @FunctionalInterface
  public interface Dispatcher {
    void generate(
        DexValue dexValue,
        Consumer<DexType> dexTypeConsumer,
        IntConsumer intValueConsumer,
        Consumer<DexString> dexStringConsumer,
        BiConsumer<DexType, DexString> enumConsumer,
        Consumer<Boolean> booleanConsumer,
        Consumer<DexValueNumber> numberConsumer);
  }

  public TypeSwitchSyntheticCfCodeProvider(
      AppView<?> appView,
      DexType holder,
      DexType arg0Type,
      List<DexValue> bootstrapArgs,
      Dispatcher dispatcher,
      DexMethod intEq,
      DexMethod enumEq,
      DexField enumFieldCache) {
    super(appView, holder);
    this.arg0Type = arg0Type;
    this.bootstrapArgs = bootstrapArgs;
    this.dispatcher = dispatcher;
    this.intEq = intEq;
    this.enumEq = enumEq;
    this.enumFieldCache = enumFieldCache;
  }

  @Override
  public CfCode generateCfCode() {
    // arg 0: Object obj
    // arg 1: int restart
    DexItemFactory factory = appView.dexItemFactory();
    List<CfInstruction> instructions = new ArrayList<>();

    CfFrame frame =
        CfFrame.builder()
            .appendLocal(FrameType.initialized(arg0Type))
            .appendLocal(FrameType.intType())
            .build();

    // Objects.checkIndex(restart, length + 1);
    instructions.add(new CfLoad(ValueType.INT, 1));
    instructions.add(new CfConstNumber(bootstrapArgs.size() + 1, ValueType.INT));
    DexMethod checkIndex =
        factory.createMethod(
            factory.objectsType,
            factory.createProto(factory.intType, factory.intType, factory.intType),
            "checkIndex");
    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, checkIndex, false));
    instructions.add(new CfStackInstruction(Opcode.Pop));

    // if (obj == null) { return -1; }
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    CfLabel nonNull = new CfLabel();
    instructions.add(new CfIf(IfType.NE, ValueType.OBJECT, nonNull));
    instructions.add(new CfConstNumber(-1, ValueType.INT));
    instructions.add(new CfReturn(ValueType.INT));
    instructions.add(nonNull);
    instructions.add(frame);

    // If no cases, return 0;
    if (bootstrapArgs.isEmpty()) {
      instructions.add(new CfConstNumber(0, ValueType.INT));
      instructions.add(new CfReturn(ValueType.INT));
    }

    // The tableSwitch for the restart dispatch.
    CfLabel defaultLabel = new CfLabel();
    List<CfLabel> cfLabels = new ArrayList<>();
    for (int i = 0; i < bootstrapArgs.size(); i++) {
      cfLabels.add(new CfLabel());
    }
    cfLabels.add(defaultLabel);
    instructions.add(new CfLoad(ValueType.INT, 1));
    instructions.add(new CfSwitch(Kind.TABLE, defaultLabel, new int[] {0}, cfLabels));

    IntBox index = new IntBox(0);
    IntBox enumIndex = new IntBox(0);
    bootstrapArgs.forEach(
        dexValue ->
            dispatcher.generate(
                dexValue,
                dexType -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(new CfLoad(ValueType.OBJECT, 0));
                  instructions.add(new CfInstanceOf(dexType));
                  instructions.add(
                      new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                intValue -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(new CfLoad(ValueType.OBJECT, 0));
                  if (allowsInlinedIntegerEquality(arg0Type, factory)) {
                    instructions.add(
                        new CfInvoke(
                            Opcodes.INVOKEVIRTUAL,
                            factory.unboxPrimitiveMethod.get(arg0Type),
                            false));
                    instructions.add(new CfConstNumber(intValue, ValueType.INT));
                    instructions.add(
                        new CfIfCmp(IfType.NE, ValueType.INT, cfLabels.get(index.get() + 1)));
                  } else {
                    instructions.add(new CfConstNumber(intValue, ValueType.INT));
                    assert intEq != null;
                    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, intEq, false));
                    instructions.add(
                        new CfIf(IfType.NE, ValueType.INT, cfLabels.get(index.get() + 1)));
                  }
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                dexString -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(new CfLoad(ValueType.OBJECT, 0));
                  instructions.add(new CfConstString(dexString));
                  instructions.add(
                      new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.equals, false));
                  instructions.add(
                      new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                (type, enumField) -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  // TODO(b/399808482): In R8 release, we can analyze at compile-time program enum
                  //  and generate a fast check based on the field. But these information are not
                  //  available in Cf instructions.
                  instructions.add(new CfLoad(ValueType.OBJECT, 0));
                  // TODO(b/399808482): Temporary work-around so we can roll to google3.
                  DexField field = getEnumField(enumField, type, appView);
                  if (field == null) {
                    instructions.add(new CfConstNull());
                  } else {
                    instructions.add(new CfStaticFieldRead(field));
                  }
                  instructions.add(
                      new CfIfCmp(IfType.NE, ValueType.OBJECT, cfLabels.get(index.get() + 1)));
                  assert enumFieldCache != null;
                  assert enumEq != null;
                  enumIndex.getAndIncrement();
                  // instructions.add(new CfStaticFieldRead(enumFieldCache));
                  // instructions.add(new CfConstNumber(enumIndex.getAndIncrement(),
                  // ValueType.INT));
                  // if (appView.enableWholeProgramOptimizations()
                  //     || appView.options().partialSubCompilationConfiguration != null) {
                  //   instructions.add(
                  //       new CfDexItemBasedConstString(
                  //           type,
                  //           ClassNameComputationInfo.create(NAME,
                  // type.getArrayTypeDimensions())));
                  // } else {
                  //   DexString typeString =
                  //       factory.createString(
                  //           DescriptorUtils.descriptorToJavaType(type.toDescriptorString()));
                  //   instructions.add(new CfConstString(typeString));
                  // }
                  // instructions.add(new CfConstString(enumField));
                  // instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, enumEq, false));
                  // instructions.add(
                  //     new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                bool -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(new CfLoad(ValueType.OBJECT, 0));
                  instructions.add(new CfConstNumber(BooleanUtils.intValue(bool), ValueType.INT));
                  instructions.add(
                      new CfInvoke(Opcodes.INVOKESTATIC, factory.booleanMembers.valueOf, false));
                  instructions.add(
                      new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.equals, false));
                  instructions.add(
                      new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                dexNumber -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(new CfLoad(ValueType.OBJECT, 0));
                  if (dexNumber.isDexValueFloat()) {
                    instructions.add(new CfConstNumber(dexNumber.getRawValue(), ValueType.FLOAT));
                    instructions.add(
                        new CfInvoke(Opcodes.INVOKESTATIC, factory.floatMembers.valueOf, false));
                  } else if (dexNumber.isDexValueDouble()) {
                    instructions.add(new CfConstNumber(dexNumber.getRawValue(), ValueType.DOUBLE));
                    instructions.add(
                        new CfInvoke(Opcodes.INVOKESTATIC, factory.doubleMembers.valueOf, false));
                  } else if (dexNumber.isDexValueLong()) {
                    instructions.add(new CfConstNumber(dexNumber.getRawValue(), ValueType.LONG));
                    instructions.add(
                        new CfInvoke(Opcodes.INVOKESTATIC, factory.longMembers.valueOf, false));
                  } else {
                    throw new CompilationError(
                        "Unexpected dexNumber in type switch desugaring " + dexNumber);
                  }
                  instructions.add(
                      new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.equals, false));
                  instructions.add(
                      new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                }));

    assert index.get() == bootstrapArgs.size();
    instructions.add(defaultLabel);
    instructions.add(frame);
    instructions.add(new CfConstNumber(-2, ValueType.INT));
    instructions.add(new CfReturn(ValueType.INT));
    return standardCfCodeFromInstructions(instructions);
  }

  public static DexField getEnumField(DexString fieldName, DexType enumType, AppView<?> appView) {
    DexClass enumClass = appView.appInfo().definitionForWithoutExistenceAssert(enumType);
    if (enumClass == null) {
      // If the enum class is missing, the case is (interestingly) considered unreachable and
      // effectively removed from the switch (base on jdk 21 behavior).
      return null;
    }
    DexEncodedField dexEncodedField = enumClass.lookupUniqueStaticFieldWithName(fieldName);
    if (dexEncodedField == null) {
      // If the field is missing, but the class is there, the case is considered unreachable and
      // effectively removed from the switch.
      return null;
    }
    return dexEncodedField.getReference();
  }

  public static boolean allowsInlinedIntegerEquality(DexType arg0Type, DexItemFactory factory) {
    return arg0Type.isIdenticalTo(factory.boxedByteType)
        || arg0Type.isIdenticalTo(factory.boxedCharType)
        || arg0Type.isIdenticalTo(factory.boxedShortType)
        || arg0Type.isIdenticalTo(factory.boxedIntType);
  }
}
