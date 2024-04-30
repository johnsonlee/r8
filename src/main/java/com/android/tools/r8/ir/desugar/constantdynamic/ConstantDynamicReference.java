// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.constantdynamic;

import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.objectweb.asm.ConstantDynamic;

public class ConstantDynamicReference implements StructuralItem<ConstantDynamicReference> {
  private final int symbolicReferenceId;
  private final DexString name;
  private final DexType type;
  private final DexMethodHandle bootstrapMethod;
  private final List<DexValue> bootstrapMethodArguments;

  private static void specify(StructuralSpecification<ConstantDynamicReference, ?> spec) {
    spec.withInt(c -> c.symbolicReferenceId);
  }

  public static ConstantDynamicReference fromAsmConstantDynamic(
      ConstantDynamic cst,
      JarApplicationReader application,
      DexType clazz,
      Supplier<Reference2IntMap<ConstantDynamic>> constantDynamicSymbolicReferencesSupplier) {

    Reference2IntMap<ConstantDynamic> constantDynamicSymbolicReferences =
        constantDynamicSymbolicReferencesSupplier.get();
    int symbolicReferenceId = constantDynamicSymbolicReferences.getOrDefault(cst, -1);
    if (symbolicReferenceId == -1) {
      symbolicReferenceId = constantDynamicSymbolicReferences.size();
      constantDynamicSymbolicReferences.put(cst, symbolicReferenceId);
    }

    String constantName = cst.getName();
    String constantDescriptor = cst.getDescriptor();
    DexMethodHandle bootstrapMethodHandle =
        DexMethodHandle.fromAsmHandle(cst.getBootstrapMethod(), application, clazz);
    int argumentCount = cst.getBootstrapMethodArgumentCount();
    List<DexValue> bootstrapMethodArguments1 = new ArrayList<>(argumentCount);
    for (int i = 0; i < argumentCount; i++) {
      Object argument = cst.getBootstrapMethodArgument(i);
      DexValue dexValue =
          DexValue.fromAsmBootstrapArgument(
              argument, application, clazz, constantDynamicSymbolicReferencesSupplier);
      bootstrapMethodArguments1.add(dexValue);
    }

    return new ConstantDynamicReference(
        symbolicReferenceId,
        application.getString(constantName),
        application.getTypeFromDescriptor(constantDescriptor),
        bootstrapMethodHandle,
        bootstrapMethodArguments1);
  }

  private ConstantDynamicReference(
      int symbolicReferenceId,
      DexString name,
      DexType type,
      DexMethodHandle bootstrapMethod,
      List<DexValue> bootstrapMethodArguments) {
    assert symbolicReferenceId >= 0;
    assert name != null;
    assert type != null;
    assert bootstrapMethod != null;
    assert bootstrapMethodArguments != null;
    this.symbolicReferenceId = symbolicReferenceId;
    this.name = name;
    this.type = type;
    this.bootstrapMethod = bootstrapMethod;
    this.bootstrapMethodArguments = bootstrapMethodArguments;
  }

  @Override
  public ConstantDynamicReference self() {
    return this;
  }

  @Override
  public StructuralMapping<ConstantDynamicReference> getStructuralMapping() {
    return ConstantDynamicReference::specify;
  }

  public DexString getName() {
    return name;
  }

  public DexType getType() {
    return type;
  }

  public DexMethodHandle getBootstrapMethod() {
    return bootstrapMethod;
  }

  public List<DexValue> getBootstrapMethodArguments() {
    return bootstrapMethodArguments;
  }

  @Override
  public boolean equals(Object obj) {
    return Equatable.equalsImpl(this, obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, bootstrapMethod, bootstrapMethodArguments);
  }
}
