// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;


import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableSet;
import java.util.List;

class EnumUnboxingCandidateAnalysis {

  // Each time we unbox an enum with instance fields, we need to generate a method with a
  // switch case to dispatch from the enum to the instance field value. We introduce this heuristic
  // to avoid unboxing enums with too many instance fields.
  private static final int MAX_INSTANCE_FIELDS_FOR_UNBOXING = 7;

  private final AppView<AppInfoWithLiveness> appView;
  private final EnumUnboxerImpl enumUnboxer;
  private final DexItemFactory factory;
  private final EnumUnboxingCandidateInfoCollection enumToUnboxCandidates =
      new EnumUnboxingCandidateInfoCollection();

  EnumUnboxingCandidateAnalysis(AppView<AppInfoWithLiveness> appView, EnumUnboxerImpl enumUnboxer) {
    this.appView = appView;
    this.enumUnboxer = enumUnboxer;
    factory = appView.dexItemFactory();
  }

  EnumUnboxingCandidateInfoCollection findCandidates(
      GraphLens graphLensForPrimaryOptimizationPass) {
    if (enumUnboxer.getOrdinalField() == null || enumUnboxer.getOrdinalField().isProgramField()) {
      // This can happen when compiling for non standard libraries, in that case, this effectively
      // disables the enum unboxer.
      return enumToUnboxCandidates;
    }
    ImmediateProgramSubtypingInfo subtypingInfo = ImmediateProgramSubtypingInfo.create(appView);
    // In recent Kotlin enum subclasses are not flagged as enum, so we cannot rely on the flag.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isEnum() && clazz.superType.isIdenticalTo(factory.enumType)) {
        analyzeEnum(graphLensForPrimaryOptimizationPass, clazz, subtypingInfo);
      }
    }
    removeEnumsInAnnotations();
    removePinnedCandidates();
    if (appView.options().protoShrinking().isProtoShrinkingEnabled()) {
      enumToUnboxCandidates.removeCandidate(appView.protoShrinker().references.methodToInvokeType);
    }
    assert enumToUnboxCandidates.verifyAllSubtypesAreSet();
    return enumToUnboxCandidates;
  }

  private void analyzeEnum(
      GraphLens graphLensForPrimaryOptimizationPass,
      DexProgramClass clazz,
      ImmediateProgramSubtypingInfo subtypingInfo) {
    if (appView.getVerticallyMergedClasses().hasBeenMergedIntoSubtype(clazz.getType())) {
      return;
    }
    if (!isSuperEnumUnboxingCandidate(clazz)) {
      return;
    }
    List<DexProgramClass> subtypes = subtypingInfo.getSubclasses(clazz);
    for (DexProgramClass subEnum : subtypes) {
      if (!isSubEnumUnboxingCandidate(subEnum)) {
        return;
      }
    }
    enumToUnboxCandidates.addCandidate(
        appView, clazz, ImmutableSet.copyOf(subtypes), graphLensForPrimaryOptimizationPass);
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isSubEnumUnboxingCandidate(DexProgramClass clazz) {
    boolean result = true;
    // Javac does not seem to generate enums with more than a single subtype level.
    // TODO(b/273910479): Stop using isEffectivelyFinal.
    if (!clazz.isEffectivelyFinal(appView)) {
      if (!enumUnboxer.reportFailure(clazz.superType, Reason.SUBENUM_SUBTYPES)) {
        return false;
      }
      result = false;
    }
    DexClass superEnum = appView.definitionFor(clazz.superType);
    if (superEnum == null || !superEnum.isEnum() || superEnum.superType != factory.enumType) {
      if (!enumUnboxer.reportFailure(clazz.superType, Reason.SUBENUM_INVALID_HIERARCHY)) {
        return false;
      }
      result = false;
    }
    // TODO(b/271385332): Support subEnums with instance fields.
    if (!clazz.instanceFields().isEmpty()) {
      if (!enumUnboxer.reportFailure(clazz.superType, Reason.SUBENUM_INSTANCE_FIELDS)) {
        return false;
      }
      result = false;
    }
    return result;
  }

  private boolean isSuperEnumUnboxingCandidate(DexProgramClass clazz) {
    assert clazz.isEnum();
    assert clazz.superType.isIdenticalTo(factory.enumType);
    if (clazz.instanceFields().size() > MAX_INSTANCE_FIELDS_FOR_UNBOXING) {
      enumUnboxer.reportFailure(clazz, Reason.MANY_INSTANCE_FIELDS);
      return false;
    }
    return true;
  }

  private void removeEnumsInAnnotations() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isAnnotation()) {
        assert clazz.interfaces.contains(appView.dexItemFactory().annotationType);
        removeEnumsInAnnotation(clazz);
      }
    }
  }

  private void removeEnumsInAnnotation(DexProgramClass clazz) {
    // Browse annotation values types in search for enum.
    // Each annotation value is represented by a virtual method.
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      assert method.getParameters().isEmpty()
          || appView.options().testing.allowInjectedAnnotationMethods;
      DexType valueType = method.returnType().toBaseType(appView.dexItemFactory());
      if (enumToUnboxCandidates.isCandidate(valueType)) {
        if (!enumUnboxer.reportFailure(valueType, Reason.ANNOTATION)) {
          enumToUnboxCandidates.removeCandidate(valueType);
        }
      }
    }
  }

  private void removePinnedCandidates() {
    // A holder type, for field or method, should block enum unboxing only if the enum type is
    // also kept. This is to allow the keep rule -keepclassmembers to be used on enums while
    // enum unboxing can still be performed.
    KeepInfoCollection keepInfo = appView.appInfo().getKeepInfo();
    InternalOptions options = appView.options();
    keepInfo.forEachPinnedType(this::removePinnedCandidate, options);
    keepInfo.forEachPinnedField(field -> removePinnedIfNotHolder(field, field.type), options);
    keepInfo.forEachPinnedMethod(
        method -> {
          DexProto proto = method.proto;
          removePinnedIfNotHolder(method, proto.returnType);
          for (DexType parameterType : proto.parameters.values) {
            removePinnedIfNotHolder(method, parameterType);
          }
        },
        options);
  }

  @SuppressWarnings("ReferenceEquality")
  private void removePinnedIfNotHolder(DexMember<?, ?> member, DexType type) {
    DexType baseType = type.toBaseType(factory);
    if (baseType != member.holder) {
      removePinnedCandidate(baseType);
    }
  }

  private void removePinnedCandidate(DexType type) {
    if (enumToUnboxCandidates.isCandidate(type)) {
      enumUnboxer.reportFailure(type, Reason.PINNED);
      enumToUnboxCandidates.removeCandidate(type);
    }
  }
}
