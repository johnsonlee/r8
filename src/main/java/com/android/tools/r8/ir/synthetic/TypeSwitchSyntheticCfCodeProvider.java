// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfCmp;
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
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueNumber;
import com.android.tools.r8.ir.code.Cmp.Bias;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IntBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class TypeSwitchSyntheticCfCodeProvider extends SyntheticCfCodeProvider {

  private final List<DexValue> bootstrapArgs;
  private final DexType arg0Type;
  private final Dispatcher dispatcher;
  private final DexMethod intEq;
  private final Map<DexType, DexMethod> enumEqMethods;
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
      Map<DexType, DexMethod> enumEqMethods,
      DexField enumFieldCache) {
    super(appView, holder);
    this.arg0Type = arg0Type;
    this.bootstrapArgs = bootstrapArgs;
    this.dispatcher = dispatcher;
    this.intEq = intEq;
    this.enumEqMethods = enumEqMethods;
    this.enumFieldCache = enumFieldCache;
  }

  @Override
  public CfCode generateCfCode() {
    // arg 0: Object|primitive obj
    // arg 1: int restart
    boolean isPrimitiveSwitch = arg0Type.isPrimitiveType();
    DexItemFactory factory = appView.dexItemFactory();
    List<CfInstruction> instructions = new ArrayList<>();

    CfFrame frame = computeCfFrame();

    // Objects.checkIndex(restart, length + 1);
    instructions.add(loadArg1());
    instructions.add(new CfConstNumber(bootstrapArgs.size() + 1, ValueType.INT));
    DexMethod checkIndex =
        factory.createMethod(
            factory.objectsType,
            factory.createProto(factory.intType, factory.intType, factory.intType),
            "checkIndex");
    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, checkIndex, false));
    instructions.add(new CfStackInstruction(Opcode.Pop));

    if (!isPrimitiveSwitch) {
      // if (obj == null) { return -1; }
      instructions.add(loadArg0());
      CfLabel nonNull = new CfLabel();
      instructions.add(new CfIf(IfType.NE, ValueType.OBJECT, nonNull));
      instructions.add(new CfConstNumber(-1, ValueType.INT));
      instructions.add(new CfReturn(ValueType.INT));
      instructions.add(nonNull);
      instructions.add(frame);
    }

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
    instructions.add(loadArg1());
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
                  if (!isPrimitiveSwitch) {
                    instructions.add(loadArg0());
                    instructions.add(new CfInstanceOf(dexType));
                    instructions.add(
                        new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  } else {
                    // TODO(b/399808482): Investigate primitive downcast, i.e., an int being
                    //  instanceof a byte, short or char.
                  }
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                intValue -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(loadArg0());
                  if (isPrimitiveSwitch) {
                    instructions.add(new CfConstNumber(intValue, ValueType.INT));
                    instructions.add(
                        new CfIfCmp(IfType.NE, ValueType.INT, cfLabels.get(index.get() + 1)));
                  } else if (allowsInlinedIntegerEquality(arg0Type, factory)) {
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
                        new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  }
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                dexString -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(loadArg0());
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
                  DexMethod enumEq = enumEqMethods.get(type);
                  assert enumFieldCache != null;
                  assert enumEq != null;
                  instructions.add(loadArg0());
                  instructions.add(new CfStaticFieldRead(enumFieldCache));
                  instructions.add(new CfConstNumber(enumIndex.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfConstString(enumField));
                  instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, enumEq, false));
                  instructions.add(
                      new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                bool -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(loadArg0());
                  if (isPrimitiveSwitch) {
                    instructions.add(
                        new CfIf(
                            bool ? IfType.EQ : IfType.NE,
                            ValueType.INT,
                            cfLabels.get(index.get() + 1)));
                  } else {
                    instructions.add(new CfConstNumber(BooleanUtils.intValue(bool), ValueType.INT));
                    instructions.add(
                        new CfInvoke(Opcodes.INVOKESTATIC, factory.booleanMembers.valueOf, false));
                    instructions.add(
                        new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.equals, false));
                    instructions.add(
                        new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  }
                  instructions.add(new CfConstNumber(index.getAndIncrement(), ValueType.INT));
                  instructions.add(new CfReturn(ValueType.INT));
                },
                dexNumber -> {
                  instructions.add(cfLabels.get(index.get()));
                  instructions.add(frame);
                  instructions.add(loadArg0());
                  if (dexNumber.isDexValueFloat()) {
                    instructions.add(new CfConstNumber(dexNumber.getRawValue(), ValueType.FLOAT));
                  } else if (dexNumber.isDexValueDouble()) {
                    instructions.add(new CfConstNumber(dexNumber.getRawValue(), ValueType.DOUBLE));
                  } else if (dexNumber.isDexValueLong()) {
                    instructions.add(new CfConstNumber(dexNumber.getRawValue(), ValueType.LONG));
                  } else {
                    throw new CompilationError(
                        "Unexpected dexNumber in type switch desugaring " + dexNumber);
                  }
                  if (isPrimitiveSwitch) {
                    instructions.add(
                        new CfCmp(
                            arg0Type.isLongType() ? Bias.NONE : Bias.GT,
                            NumericType.fromDexType(arg0Type)));
                    instructions.add(
                        new CfIf(IfType.NE, ValueType.INT, cfLabels.get(index.get() + 1)));
                  } else {
                    if (dexNumber.isDexValueFloat()) {
                      instructions.add(
                          new CfInvoke(Opcodes.INVOKESTATIC, factory.floatMembers.valueOf, false));
                    } else if (dexNumber.isDexValueDouble()) {
                      instructions.add(
                          new CfInvoke(Opcodes.INVOKESTATIC, factory.doubleMembers.valueOf, false));
                    } else {
                      assert dexNumber.isDexValueLong();
                      instructions.add(
                          new CfInvoke(Opcodes.INVOKESTATIC, factory.longMembers.valueOf, false));
                    }
                    instructions.add(
                        new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.equals, false));
                    instructions.add(
                        new CfIf(IfType.EQ, ValueType.INT, cfLabels.get(index.get() + 1)));
                  }
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

  private CfLoad loadArg1() {
    return new CfLoad(ValueType.INT, arg0Type.isWideType() ? 2 : 1);
  }

  private CfLoad loadArg0() {
    return new CfLoad(ValueType.fromDexType(arg0Type), 0);
  }

  private CfFrame computeCfFrame() {
    DexType frameType =
        arg0Type.isByteType()
                || arg0Type.isShortType()
                || arg0Type.isCharType()
                || arg0Type.isBooleanType()
            ? appView.dexItemFactory().intType
            : arg0Type;
    CfFrame frame =
        CfFrame.builder()
            .appendLocal(FrameType.initialized(frameType))
            .appendLocal(FrameType.intType())
            .build();
    return frame;
  }

  public static boolean allowsInlinedIntegerEquality(DexType arg0Type, DexItemFactory factory) {
    return arg0Type.isIdenticalTo(factory.boxedByteType)
        || arg0Type.isIdenticalTo(factory.boxedCharType)
        || arg0Type.isIdenticalTo(factory.boxedShortType)
        || arg0Type.isIdenticalTo(factory.boxedIntType);
  }
}
