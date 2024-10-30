// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress366412380 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withAllApiLevels().build();
  }

  public Regress366412380(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(Regress366412380.class)
        .release()
        .compile()
        .inspect(
            codeInspector -> {
              // Ensure that we don't redundant field load eliminate the instance get of foo.
              // In D8 we are not allowed to make assumptions about side effects of new instance.
              assertTrue(
                  codeInspector
                      .clazz(TestClassNoSuper.class)
                      .uniqueMethodWithOriginalName("useFoo")
                      .iterateInstructions(InstructionSubject::isInstanceGet)
                      .hasNext());
              if (parameters.isDexRuntime()) {
                assertFalse(
                    codeInspector
                        .clazz(TestClassExtendsBar.class)
                        .uniqueMethodWithOriginalName("useFoo")
                        .iterateInstructions(InstructionSubject::isInstanceGet)
                        .hasNext());
              }
            });
  }

  public static class Foo {}

  public static class Bar {
    public Bar() {}

    public Bar(Foo foo) {}
  }

  public static class TestClassExtendsBar extends Bar {
    protected Foo foo;

    public void useFoo() {
      foo = new Foo();
      new Bar(foo);
    }
  }

  public static class TestClassNoSuper {
    protected Foo foo;

    public void useFoo() {
      foo = new Foo();
      new Bar(foo);
    }
  }
}
