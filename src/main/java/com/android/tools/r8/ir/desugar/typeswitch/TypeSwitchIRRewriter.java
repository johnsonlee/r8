// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistryWithResult;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues.EnumStaticFieldValues;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import com.google.common.collect.ImmutableList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TypeSwitchIRRewriter {
  private final AppView<AppInfoWithLiveness> appView;
  private final BidirectionalManyToOneMap<DexMethod, DexType> enumEqMethods;

  private final Map<DexType, EnumStaticFieldValues> staticFieldValuesMap =
      new ConcurrentHashMap<>();

  public static TypeSwitchIRRewriter create(AppView<AppInfoWithLiveness> appView) {
    BidirectionalManyToOneMap<DexMethod, DexType> enumEqMethods = buildEnumEqMethods(appView);
    if (enumEqMethods.isEmpty()) {
      return null;
    }
    return new TypeSwitchIRRewriter(appView, enumEqMethods);
  }

  private TypeSwitchIRRewriter(
      AppView<AppInfoWithLiveness> appView,
      BidirectionalManyToOneMap<DexMethod, DexType> enumEqMethods) {
    this.appView = appView;
    this.enumEqMethods = enumEqMethods;
  }

  private static BidirectionalManyToOneMap<DexMethod, DexType> buildEnumEqMethods(
      AppView<AppInfoWithLiveness> appView) {
    BidirectionalManyToOneHashMap<DexMethod, DexType> res =
        BidirectionalManyToOneHashMap.newIdentityHashMap();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (appView
          .getSyntheticItems()
          .isSyntheticOfKind(clazz.getType(), kinds -> kinds.TYPE_SWITCH_HELPER_ENUM)) {
        if (!clazz.hasMethods()) {
          continue;
        }
        ProgramMethod uniqueStaticMethod = clazz.directProgramMethods().iterator().next();
        DexMethod uniqueMethod = uniqueStaticMethod.getReference();
        UseRegistryWithResult<DexType, ProgramMethod> registry =
            new DefaultUseRegistryWithResult<>(appView, uniqueStaticMethod) {
              @Override
              public void registerInvokeStatic(DexMethod method) {
                assert getResult() == null;
                if (method.getName().isIdenticalTo(dexItemFactory().valueOfMethodName)
                    && method.getArity() == 1
                    && method.getParameter(0).isIdenticalTo(dexItemFactory().stringType)) {
                  setResult(method.getHolderType());
                }
              }
            };
        DexType enumType = uniqueStaticMethod.registerCodeReferencesWithResult(registry);
        if (enumType != null) {
          res.put(uniqueMethod, enumType);
        }
      }
    }
    return res;
  }

  public void recordEnumState(DexProgramClass clazz, StaticFieldValues staticFieldValues) {
    if (staticFieldValues == null || !staticFieldValues.isEnumStaticFieldValues()) {
      return;
    }
    assert clazz.isEnum();
    EnumStaticFieldValues enumStaticFieldValues = staticFieldValues.asEnumStaticFieldValues();
    if (enumEqMethods.containsValue(clazz.type)) {
      staticFieldValuesMap.put(clazz.type, enumStaticFieldValues);
    }
  }

  public void run(IRCode code) {
    if (shouldRewriteCode(code)) {
      rewriteCode(code);
    }
  }

  protected boolean shouldRewriteCode(IRCode code) {
    return !code.context().getDefinition().isClassInitializer()
        && appView
            .getSyntheticItems()
            .isSyntheticOfKind(code.context().getHolderType(), k -> k.TYPE_SWITCH_CLASS);
  }

  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean change = false;
    Map<If, If> replacement = new IdentityHashMap<>();
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction next = iterator.next();
      if (next.isIf()) {
        If anIf = next.asIf();
        If replace = replacement.get(anIf);
        if (replace != null) {
          replacement.remove(anIf);
          BasicBlock fallThrough = anIf.fallthroughBlock();
          BasicBlock jumpTarget = anIf.getTrueTarget();
          iterator.replaceCurrentInstruction(replace);
          replace.setFallthroughBlock(fallThrough);
          replace.setTrueTarget(jumpTarget);
        }
      } else if (next.isInvokeStatic()
          && appView
              .getSyntheticItems()
              .isSyntheticOfKind(
                  next.asInvokeStatic().getInvokedMethod().getHolderType(),
                  kinds -> kinds.TYPE_SWITCH_HELPER_ENUM)) {
        if (!next.hasOutValue()) {
          // This happens if the enum is missing from the compilation, the result is always false.
          iterator.removeOrReplaceByDebugLocalRead();
        } else if (next.outValue().hasSingleUniqueUserAndNoOtherUsers()) {
          InvokeStatic invokeStatic = next.asInvokeStatic();
          DexString fieldName = invokeStatic.getLastArgument().getConstStringOrNull();
          if (fieldName == null) {
            continue;
          }
          DexType enumType = enumEqMethods.get(invokeStatic.getInvokedMethod());
          if (enumType == null) {
            continue;
          }
          EnumStaticFieldValues enumStaticFieldValues = staticFieldValuesMap.get(enumType);
          if (enumStaticFieldValues == null) {
            continue;
          }
          Pair<DexField, Integer> fieldAccessor =
              enumStaticFieldValues.getFieldAccessorForName(fieldName, appView.dexItemFactory());
          if (fieldAccessor == null) {
            continue;
          }
          if (!invokeStatic.outValue().hasSingleUniqueUserAndNoOtherUsers()) {
            continue;
          }
          Instruction instruction = invokeStatic.outValue().singleUniqueUser();
          if (!instruction.isIf()) {
            continue;
          }
          // The enum instance has been resolved. We can replace
          // if (enumEq(val, cache, cacheIndex, name))
          // by
          // if (val == fieldAccessor)
          Value newValue;
          if (fieldAccessor.getSecond() == null) {
            newValue =
                code.createValue(
                    TypeElement.fromDexType(
                        fieldAccessor.getFirst().getType(), Nullability.maybeNull(), appView));
            iterator.replaceCurrentInstruction(new StaticGet(newValue, fieldAccessor.getFirst()));
          } else {
            newValue =
                code.createValue(
                    TypeElement.fromDexType(
                        fieldAccessor.getFirst().getType().toBaseType(appView.dexItemFactory()),
                        Nullability.maybeNull(),
                        appView));
            Value arrayValue =
                code.createValue(
                    TypeElement.fromDexType(
                        fieldAccessor.getFirst().getType(), Nullability.maybeNull(), appView));
            iterator.previous();
            Value intValue =
                iterator.insertConstIntInstruction(
                    code, appView.options(), fieldAccessor.getSecond());
            StaticGet fieldGet = new StaticGet(arrayValue, fieldAccessor.getFirst());
            iterator.add(fieldGet);
            iterator.next();
            ArrayGet arrayGet = new ArrayGet(MemberType.OBJECT, newValue, arrayValue, intValue);
            iterator.replaceCurrentInstruction(arrayGet);
            fieldGet.setPosition(arrayGet.getPosition());
          }
          If anIf = instruction.asIf();
          // The method enumEq answered true/false based on if the values are equal, which is
          // inverted when one compares directly the values.
          If newIf =
              new If(
                  anIf.getType().inverted(),
                  ImmutableList.of(newValue, invokeStatic.getFirstArgument()));
          replacement.put(anIf, newIf);
          change = true;
        }
      }
    }
    assert replacement.isEmpty();
    return CodeRewriterResult.hasChanged(change);
  }
}
