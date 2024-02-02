// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.sync;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.containsConstString;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineWithMonitorInConstructorInline extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineWithMonitorInConstructorInline.class)
        .addKeepMainRule(TestClass.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(Foo.class, Bar.class)
                    .assertNoOtherClassesMerged())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspect)
        .assertSuccessWithOutputLines("foo", "monitor", "bar", "monitor2");
  }

  // Check we at most inline one method with monitor instructions into synthetic constructors
  // created by class merging. See also b/238399429.
  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    ClassSubject fooClassSubject = inspector.clazz(Bar.class);
    assertThat(fooClassSubject, isPresent());

    ClassSubject utilClassSubject = inspector.clazz(Util.class);
    assertThat(utilClassSubject, isAbsent());

    // Verify that the two monitor instructions are not inlined into the same method.
    if (parameters.isCfRuntime()
        || parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.M)) {
      // Find the synthetic constructor with an added `int classId` parameter. Verify that only
      // Foo.<init> has been inlined this constructor.
      MethodSubject syntheticInit = fooClassSubject.init("int");
      assertThat(syntheticInit, isPresent());
      assertThat(syntheticInit, containsConstString("foo"));
      assertThat(syntheticInit, not(containsConstString("bar")));
      assertEquals(1, numberOfMonitorEnterInstructions(syntheticInit));

      // Find the non-synthetic constructor corresponding to Bar.<init>.
      MethodSubject barInit = fooClassSubject.init();
      assertThat(barInit, isPresent());
      assertThat(barInit, containsConstString("bar"));
      assertThat(barInit, not(containsConstString("foo")));
      assertEquals(1, numberOfMonitorEnterInstructions(barInit));

      // Finally verify that the synthetic constructor calls the non-synthetic constructor, due to
      // inlining being prohibited.
      assertThat(syntheticInit, invokesMethod(barInit));
    } else {
      MethodSubject syntheticInit = fooClassSubject.uniqueInstanceInitializer();
      assertThat(syntheticInit, isPresent());
      assertThat(syntheticInit, containsConstString("bar"));
      assertThat(syntheticInit, containsConstString("foo"));
      assertEquals(2, numberOfMonitorEnterInstructions(syntheticInit));
    }
  }

  private static long numberOfMonitorEnterInstructions(MethodSubject methodSubject) {
    return methodSubject.streamInstructions().filter(InstructionSubject::isMonitorEnter).count();
  }

  static class Foo {
    public Foo() {
      System.out.println("foo");
      Util.useMonitor(this);
    }
  }

  static class Bar {
    public Bar() {
      System.out.println("bar");
      Util.useMonitor2(this);
    }
  }

  static class Util {
    public static void useMonitor(Object object) {
      synchronized (object) {
        System.out.println("monitor");
      }
    }

    public static void useMonitor2(Object object) {
      synchronized (object) {
        System.out.println("monitor2");
      }
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      new Foo();
      new Bar();
    }
  }
}
