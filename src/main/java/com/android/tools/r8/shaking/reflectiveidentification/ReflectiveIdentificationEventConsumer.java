// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.reflectiveidentification;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Collection;
import java.util.Set;

public interface ReflectiveIdentificationEventConsumer {

  void onJavaLangClassForName(DexClass clazz, ProgramMethod context);

  void onJavaLangClassGetField(ProgramField field, ProgramMethod context);

  void onJavaLangClassGetMethod(ProgramMethod method, ProgramMethod context);

  void onJavaLangClassNewInstance(DexProgramClass clazz, ProgramMethod context);

  void onJavaLangReflectConstructorNewInstance(ProgramMethod initializer, ProgramMethod context);

  void onJavaLangReflectProxyNewProxyInstance(Set<DexProgramClass> classes, ProgramMethod context);

  void onJavaUtilConcurrentAtomicAtomicIntegerFieldUpdaterNewUpdater(
      ProgramField field, ProgramMethod context);

  void onJavaUtilConcurrentAtomicAtomicLongFieldUpdaterNewUpdater(
      ProgramField field, ProgramMethod context);

  void onJavaUtilConcurrentAtomicAtomicReferenceFieldUpdaterNewUpdater(
      ProgramField field, ProgramMethod context);

  void onJavaUtilServiceLoaderLoad(
      DexProgramClass serviceClass,
      Collection<DexProgramClass> implementationClasses,
      ProgramMethod context);
}
