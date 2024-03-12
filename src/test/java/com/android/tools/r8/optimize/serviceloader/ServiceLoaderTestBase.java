// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.serviceloader;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;

public class ServiceLoaderTestBase extends TestBase {

  public static long getServiceLoaderLoads(CodeInspector inspector) {
    return inspector.allClasses().stream()
        .mapToLong(ServiceLoaderTestBase::getServiceLoaderLoads)
        .reduce(0, Long::sum);
  }

  public static long getServiceLoaderLoads(CodeInspector inspector, Class<?> clazz) {
    return getServiceLoaderLoads(inspector.clazz(clazz));
  }

  public static long getServiceLoaderLoads(ClassSubject classSubject) {
    assertTrue(classSubject.isPresent());
    return classSubject.allMethods(MethodSubject::hasCode).stream()
        .mapToLong(
            method ->
                method
                    .streamInstructions()
                    .filter(ServiceLoaderTestBase::isServiceLoaderLoad)
                    .count())
        .sum();
  }

  private static boolean isServiceLoaderLoad(InstructionSubject instruction) {
    return instruction.isInvokeStatic()
        && instruction.getMethod().qualifiedName().contains("ServiceLoader.load");
  }

  public void verifyNoClassLoaders(CodeInspector inspector) {
    inspector.allClasses().forEach(this::verifyNoClassLoaders);
  }

  public void verifyNoClassLoaders(ClassSubject classSubject) {
    assertTrue(classSubject.isPresent());
    classSubject.forAllMethods(
        method -> assertThat(method, not(invokesMethodWithName("getClassLoader"))));
  }

  public void verifyNoServiceLoaderLoads(CodeInspector inspector) {
    inspector.allClasses().forEach(this::verifyNoServiceLoaderLoads);
  }

  public void verifyNoServiceLoaderLoads(ClassSubject classSubject) {
    assertTrue(classSubject.isPresent());
    classSubject.forAllMethods(method -> assertThat(method, not(invokesMethodWithName("load"))));
  }
}
