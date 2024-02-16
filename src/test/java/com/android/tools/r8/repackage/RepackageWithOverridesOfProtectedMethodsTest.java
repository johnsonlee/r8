// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithOverridesOfProtectedMethodsTest extends RepackageTestBase {

  public RepackageWithOverridesOfProtectedMethodsTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    Box<ClassReference> helloGreeterAfterRepackaging = new Box<>();
    Box<ClassReference> worldGreeterAfterRepackaging = new Box<>();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(HelloGreeterBase.class, WorldGreeterBase.class)
                    .assertIsCompleteMergeGroup(
                        helloGreeterAfterRepackaging.get(), worldGreeterAfterRepackaging.get())
                    .assertNoOtherClassesMerged())
        .addRepackagingInspector(
            inspector -> {
              helloGreeterAfterRepackaging.set(
                  inspector.getTarget(Reference.classFromClass(HelloGreeter.class)));
              worldGreeterAfterRepackaging.set(
                  inspector.getTarget(Reference.classFromClass(WorldGreeter.class)));
            })
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(HelloGreeter.class, isRepackaged(inspector));
    assertThat(inspector.clazz(WorldGreeter.class), isAbsent());
  }

  public static class TestClass {

    public static void main(String[] args) {
      greet(new HelloGreeter());
      greet(new WorldGreeter());
    }

    @NeverInline
    @NoParameterTypeStrengthening
    static void greet(HelloGreeterBase greeter) {
      greeter.greet();
    }

    @NeverInline
    @NoParameterTypeStrengthening
    static void greet(WorldGreeterBase greeter) {
      greeter.greet();
    }
  }

  @NoVerticalClassMerging
  public abstract static class HelloGreeterBase {

    protected abstract void greet();
  }

  @NeverClassInline
  public static class HelloGreeter extends HelloGreeterBase {

    @NeverInline
    @Override
    protected void greet() {
      System.out.print("Hello");
    }
  }

  @NoVerticalClassMerging
  public abstract static class WorldGreeterBase {

    protected abstract void greet();
  }

  @NeverClassInline
  public static class WorldGreeter extends WorldGreeterBase {

    @NeverInline
    @Override
    public void greet() {
      System.out.println(" world!");
    }
  }
}
