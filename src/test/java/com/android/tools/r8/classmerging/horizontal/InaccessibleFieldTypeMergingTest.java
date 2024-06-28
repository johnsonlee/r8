// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.classmerging.horizontal.testclasses.InaccessibleFieldTypeMergingTestClasses;
import com.android.tools.r8.classmerging.horizontal.testclasses.InaccessibleFieldTypeMergingTestClasses.BarGreeterContainer;
import com.android.tools.r8.classmerging.horizontal.testclasses.InaccessibleFieldTypeMergingTestClasses.FooGreeterContainer;
import com.android.tools.r8.classmerging.horizontal.testclasses.InaccessibleFieldTypeMergingTestClasses.Greeter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InaccessibleFieldTypeMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass(), InaccessibleFieldTypeMergingTestClasses.class)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(
                        FooGreeterContainer.class, BarGreeterContainer.class)
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/347676160): Should succeed.
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  static class Main {

    public static void main(String[] args) {
      FooGreeterContainer fooGreeterContainer = createFooGreeterContainer();
      Greeter fooGreeter = fooGreeterContainer.f;
      fooGreeter.greet();
      BarGreeterContainer barGreeterContainer = createBarGreeterContainer();
      Greeter barGreeter = barGreeterContainer.f;
      barGreeter.greet();
    }

    @NeverInline
    static FooGreeterContainer createFooGreeterContainer() {
      return new FooGreeterContainer();
    }

    @NeverInline
    static BarGreeterContainer createBarGreeterContainer() {
      return new BarGreeterContainer();
    }
  }
}
