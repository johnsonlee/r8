// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress326837090 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  public Regress326837090(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    runTest(false);
    runTest(true);
  }

  private void runTest(boolean sortBackwards)
      throws IOException, ExecutionException, CompilationFailedException {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .addOptionsModification(o -> o.testing.reverseClassSortingForDeterminism = sortBackwards)
        .compile()
        .run(TestClass.class)
        .assertSuccessWithOutputLines("foo", "foo")
        .inspect(
            codeInspector -> {
              List<FoundMethodSubject> makeCommand =
                  codeInspector
                      .clazz(C.class)
                      .allMethods(
                          p ->
                              p.getOriginalMethodName().contains("makeCommand")
                                  && !p.isSynthetic());
              assertEquals(1, makeCommand.size());
              assertEquals(makeCommand.get(0).isRenamed(), sortBackwards);
            });
  }

  public static class TestClass {
    public static void main(String[] args) {
      new A().makeCommand().foo();
      ArrayList<Foo> arrayList = new ArrayList<>();
      arrayList.add(new C());
      arrayList.get(0).makeCommand().foo();
    }
  }

  @KeepForApi
  public abstract static class Foo<S extends Foo> {
    abstract S makeCommand();

    public void foo() {
      System.out.println("foo");
    }
  }

  @KeepForApi
  public static class A extends Foo<A> {
    @Override
    protected A makeCommand() {
      return this;
    }
  }

  @KeepForApi
  public static class C extends Foo<C> {
    @Override
    @NeverInline
    C makeCommand() {
      return this;
    }
  }
}
