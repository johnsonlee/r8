// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.reflectiveidentification;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.partial.R8PartialUseCollector;
import java.util.Collection;
import java.util.Set;

public class KeepAllReflectiveIdentificationEventConsumer
    implements ReflectiveIdentificationEventConsumer {

  private final R8PartialUseCollector useCollector;

  public KeepAllReflectiveIdentificationEventConsumer(R8PartialUseCollector useCollector) {
    this.useCollector = useCollector;
  }

  private void keep(Definition definition, ProgramMember<?, ?> context) {
    DefinitionContext referencedFrom = DefinitionContextUtils.create(context);
    useCollector.keep(definition, referencedFrom, false);
  }

  @Override
  public void onJavaLangClassForName(DexClass clazz, ProgramMethod context) {
    keep(clazz, context);
  }

  @Override
  public void onJavaLangClassGetField(ProgramField field, ProgramMethod context) {
    keep(field, context);
  }

  @Override
  public void onJavaLangClassGetMethod(ProgramMethod method, ProgramMethod context) {
    keep(method, context);
  }

  @Override
  public void onJavaLangClassNewInstance(DexProgramClass clazz, ProgramMethod context) {
    ProgramMethod defaultInitializer = clazz.getProgramDefaultInitializer();
    if (defaultInitializer != null) {
      keep(defaultInitializer, context);
    }
  }

  @Override
  public void onJavaLangReflectConstructorNewInstance(
      ProgramMethod initializer, ProgramMethod context) {
    keep(initializer, context);
  }

  @Override
  public void onJavaLangReflectProxyNewProxyInstance(
      Set<DexProgramClass> classes, ProgramMethod context) {
    for (DexProgramClass clazz : classes) {
      keep(clazz, context);
    }
  }

  @Override
  public void onJavaUtilConcurrentAtomicAtomicIntegerFieldUpdaterNewUpdater(
      ProgramField field, ProgramMethod context) {
    keep(field, context);
  }

  @Override
  public void onJavaUtilConcurrentAtomicAtomicLongFieldUpdaterNewUpdater(
      ProgramField field, ProgramMethod context) {
    keep(field, context);
  }

  @Override
  public void onJavaUtilConcurrentAtomicAtomicReferenceFieldUpdaterNewUpdater(
      ProgramField field, ProgramMethod context) {
    keep(field, context);
  }

  @Override
  public void onJavaUtilServiceLoaderLoad(
      DexProgramClass serviceClass,
      Collection<DexProgramClass> implementationClasses,
      ProgramMethod context) {
    if (serviceClass != null) {
      keep(serviceClass, context);
    }
    for (DexProgramClass implementationClass : implementationClasses) {
      keep(implementationClass, context);
      ProgramMethod defaultInitializer = implementationClass.getProgramDefaultInitializer();
      if (defaultInitializer != null) {
        keep(defaultInitializer, context);
      }
    }
  }

  @Override
  public void onIdentifierNameString(Definition definition, ProgramMember<?, ?> context) {
    keep(definition, context);
  }
}
