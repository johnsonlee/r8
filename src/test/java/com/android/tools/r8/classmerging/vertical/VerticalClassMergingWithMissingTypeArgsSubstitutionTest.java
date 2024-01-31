// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.TypeVariable;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingWithMissingTypeArgsSubstitutionTest extends TestBase {

  @Parameter(0)
  public boolean enableBridgeAnalysis;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, bridge analysis: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassRules(A.class)
        .addKeepAttributeSignature()
        .addOptionsModification(
            options ->
                options
                    .getVerticalClassMergerOptions()
                    .setEnableBridgeAnalysis(enableBridgeAnalysis))
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(B.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableConstantArgumentAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("T", "Hello World")
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(C.class);
              assertThat(classSubject, isPresentAndRenamed());
              assertEquals(
                  "<T:Ljava/lang/Object;>L" + binaryName(A.class) + "<Ljava/lang/Object;>;",
                  classSubject.getFinalSignatureAttribute());

              MethodSubject bar = classSubject.uniqueMethodWithOriginalName("bar");
              assertThat(bar, isPresentAndRenamed());
              assertEquals("(TT;)V", bar.getFinalSignatureAttribute());

              // The NeverInline is transferred to the private vertically merged method but also
              // copied to the virtual bridge.
              MethodSubject fooMovedFromB =
                  classSubject.uniqueMethodThatMatches(
                      method ->
                          method.isFinal()
                              && method.isVirtual()
                              && !method.isSynthetic()
                              && method.getOriginalName(false).equals("foo"));
              assertThat(fooMovedFromB, isPresentAndRenamed());
              assertEquals(
                  "(Ljava/lang/Object;)Ljava/lang/Object;",
                  fooMovedFromB.getFinalSignatureAttribute());

              MethodSubject fooBridge =
                  classSubject.uniqueMethodThatMatches(
                      method ->
                          method.isFinal()
                              && method.isVirtual()
                              && method.isSynthetic()
                              && method.getOriginalName(false).equals("foo"));
              if (enableBridgeAnalysis) {
                assertThat(fooBridge, isAbsent());
              } else {
                assertThat(fooBridge, isPresentAndRenamed());
                assertEquals(
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    fooBridge.getFinalSignatureAttribute());
              }
            });
  }

  static class Main {

    @SuppressWarnings("rawtypes")
    public static void main(String[] args) {
      C<String> stringC = new C<>();
      for (TypeVariable<? extends Class<? extends C>> typeParameter :
          stringC.getClass().getTypeParameters()) {
        System.out.println(typeParameter.getName());
      }
      stringC.bar("Hello World");
    }
  }

  static class A<T> {}

  static class B<T> extends A<T> {

    @KeepConstantArguments
    @NoMethodStaticizing
    @NeverInline
    public T foo(T t) {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException("Foo");
      }
      return t;
    }
  }

  @SuppressWarnings("rawtypes")
  static class C<T> extends B {

    @NoMethodStaticizing
    @KeepConstantArguments
    @NeverInline
    @SuppressWarnings("unchecked")
    void bar(T t) {
      System.out.println(foo(t));
    }
  }
}
