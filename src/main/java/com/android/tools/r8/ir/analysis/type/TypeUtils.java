// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.WorkList;
import java.util.Objects;
import java.util.function.BiFunction;

public class TypeUtils {

  private static class UserAndValuePair {

    final InstructionOrPhi user;
    final Value value;

    UserAndValuePair(InstructionOrPhi user, Value value) {
      this.user = user;
      this.value = value;
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      UserAndValuePair pair = (UserAndValuePair) obj;
      return user == pair.user && value == pair.value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(user, value);
    }
  }

  /**
   * Returns the "use type" of a given value {@link Value}, i.e., the weakest static type that this
   * value must have in order for the program to type check.
   */
  public static TypeElement computeUseType(AppView<?> appView, DexType returnType, Value value) {
    if (appView.hasClassHierarchy()) {
      return computeUseType(appView, returnType, value, (s, t) -> s.join(t, appView));
    } else {
      AppView<AppInfo> appViewWithoutClassHierarchy = appView.withoutClassHierarchy();
      AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
      return computeUseType(
          appView,
          returnType,
          value,
          (s, t) -> joinWithoutClassHierarchy(s, t, appViewWithoutClassHierarchy, appInfo));
    }
  }

  private static TypeElement computeUseType(
      AppView<?> appView,
      DexType returnType,
      Value value,
      BiFunction<TypeElement, TypeElement, TypeElement> joiner) {
    TypeElement staticType = value.getType();
    TypeElement useType = TypeElement.getBottom();
    WorkList<UserAndValuePair> users = WorkList.newEqualityWorkList();
    enqueueUsers(value, users);
    while (users.hasNext()) {
      UserAndValuePair item = users.next();
      InstructionOrPhi user = item.user;
      if (user.isPhi()) {
        enqueueUsers(user.asPhi(), users);
      } else {
        Instruction instruction = user.asInstruction();
        TypeElement instructionUseType =
            computeUseTypeForInstruction(
                appView, returnType, instruction, item.value, joiner, users);
        useType = joiner.apply(useType, instructionUseType);
        if (useType.isTop() || useType.equalUpToNullability(staticType)) {
          // Bail-out.
          return staticType;
        }
      }
    }
    return useType;
  }

  private static void enqueueUsers(Value value, WorkList<UserAndValuePair> users) {
    for (Instruction user : value.uniqueUsers()) {
      users.addIfNotSeen(new UserAndValuePair(user, value));
    }
    for (Phi user : value.uniquePhiUsers()) {
      users.addIfNotSeen(new UserAndValuePair(user, value));
    }
  }

  private static TypeElement computeUseTypeForInstruction(
      AppView<?> appView,
      DexType returnType,
      Instruction instruction,
      Value value,
      BiFunction<TypeElement, TypeElement, TypeElement> joiner,
      WorkList<UserAndValuePair> users) {
    switch (instruction.opcode()) {
      case ASSUME:
        return computeUseTypeForAssume(instruction.asAssume(), users);
      case CHECK_CAST:
      case IF:
        return TypeElement.getBottom();
      case INSTANCE_GET:
        return computeUseTypeForInstanceGet(appView, instruction.asInstanceGet());
      case INSTANCE_PUT:
        return computeUseTypeForInstancePut(appView, instruction.asInstancePut(), value, joiner);
      case INVOKE_DIRECT:
      case INVOKE_INTERFACE:
      case INVOKE_STATIC:
      case INVOKE_SUPER:
      case INVOKE_VIRTUAL:
        return computeUseTypeForInvoke(appView, instruction.asInvokeMethod(), value, joiner);
      case RETURN:
        return computeUseTypeForReturn(appView, returnType);
      case STATIC_PUT:
        return computeUseTypeForStaticPut(appView, instruction.asStaticPut());
      default:
        // Bail out for unhandled instructions.
        return TypeElement.getTop();
    }
  }

  private static TypeElement computeUseTypeForAssume(
      Assume assume, WorkList<UserAndValuePair> users) {
    enqueueUsers(assume.outValue(), users);
    return TypeElement.getBottom();
  }

  private static TypeElement computeUseTypeForInstanceGet(
      AppView<?> appView, InstanceGet instanceGet) {
    return instanceGet.getField().getHolderType().toTypeElement(appView);
  }

  private static TypeElement computeUseTypeForInstancePut(
      AppView<?> appView,
      InstancePut instancePut,
      Value value,
      BiFunction<TypeElement, TypeElement, TypeElement> joiner) {
    DexField field = instancePut.getField();
    TypeElement useType = TypeElement.getBottom();
    if (instancePut.object() == value) {
      useType = joiner.apply(useType, field.getHolderType().toTypeElement(appView));
    }
    if (instancePut.value() == value) {
      useType = joiner.apply(useType, field.getType().toTypeElement(appView));
    }
    return useType;
  }

  private static TypeElement computeUseTypeForInvoke(
      AppView<?> appView,
      InvokeMethod invoke,
      Value value,
      BiFunction<TypeElement, TypeElement, TypeElement> joiner) {
    TypeElement useType = TypeElement.getBottom();
    for (int argumentIndex = 0; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      Value argument = invoke.getArgument(argumentIndex);
      if (argument != value) {
        continue;
      }
      TypeElement useTypeForArgument =
          invoke
              .getInvokedMethod()
              .getArgumentType(argumentIndex, invoke.isInvokeStatic())
              .toTypeElement(appView);
      useType = joiner.apply(useType, useTypeForArgument);
    }
    assert !useType.isBottom();
    return useType;
  }

  private static TypeElement computeUseTypeForReturn(AppView<?> appView, DexType returnType) {
    return returnType.toTypeElement(appView);
  }

  private static TypeElement computeUseTypeForStaticPut(AppView<?> appView, StaticPut staticPut) {
    return staticPut.getField().getType().toTypeElement(appView);
  }

  private static TypeElement joinWithoutClassHierarchy(
      TypeElement type,
      TypeElement other,
      AppView<AppInfo> appView,
      AppInfoWithClassHierarchy appInfo) {
    assert !other.isBottom();
    if (type.isBottom()) {
      return other;
    }
    if (type.isTop() || other.isTop()) {
      return TypeElement.getTop();
    }
    if (type.equals(other)) {
      return type;
    }
    if (type.isPrimitiveType()) {
      return type.join(other, appView);
    }
    assert type.isReferenceType();
    assert other.isReferenceType();
    assert !type.isNullType();
    assert !other.isNullType();
    if (type.isArrayType() || other.isArrayType()) {
      return TypeElement.getTop();
    }
    assert type.isClassType();
    assert other.isClassType();
    DexType classType = type.asClassType().getClassType();
    DexType otherClassType = other.asClassType().getClassType();
    if (appInfo.isSubtype(classType, otherClassType)) {
      return ClassTypeElement.createForD8(classType, type.nullability().join(other.nullability()));
    }
    if (appInfo.isSubtype(otherClassType, classType)) {
      return ClassTypeElement.createForD8(
          otherClassType, type.nullability().join(other.nullability()));
    }
    return TypeElement.getTop();
  }
}
