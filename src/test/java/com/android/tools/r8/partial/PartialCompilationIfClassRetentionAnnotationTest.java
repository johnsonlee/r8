// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationIfClassRetentionAnnotationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8Partial(parameters)
        .addR8ExcludedClasses(ClassRetentionAnnotation.class, Main.class)
        .addR8IncludedClasses(Unused.class)
        .addKeepRules(
            "-if @" + ClassRetentionAnnotation.class.getTypeName() + " class *",
            "-keep class " + Unused.class.getTypeName())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              assertTrue(mainClassSubject.annotations().isEmpty());

              ClassSubject unusedClassSubject = inspector.clazz(Unused.class);
              assertThat(unusedClassSubject, isPresent());
            });
  }

  @Retention(RetentionPolicy.CLASS)
  @interface ClassRetentionAnnotation {}

  @ClassRetentionAnnotation
  static class Main {

    public static void main(String[] args) {}
  }

  static class Unused {}
}
