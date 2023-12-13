// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InitializerNumberUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InitializerNumberUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addOptionsModification(opt -> opt.testing.enableNumberUnboxer = true)
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "Main[-1;-1]", "Main[-2;-2]", "Main[1;1]", "Main[2;2]", "Main[3;4]");
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    ClassSubject mainClass = codeInspector.clazz(Main.class);
    assertThat(mainClass, isPresent());

    List<FoundMethodSubject> inits =
        mainClass.allMethods(FoundMethodSubject::isInstanceInitializer);
    assertEquals(3, inits.size());
    inits.forEach(
        m ->
            assertTrue(
                m.getParameters().stream().allMatch(p -> p.getTypeReference().isPrimitive())));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new Main(-1));
      System.out.println(new Main(-2L));
      System.out.println(new Main(1));
      System.out.println(new Main(2L));
      System.out.println(new Main(3, 4L));
    }

    private final int i;
    private final long l;

    @NeverInline
    Main(Long l) {
      this(l.intValue(), l);
    }

    @NeverInline
    Main(Integer i) {
      this(i, Long.valueOf(i));
    }

    @NeverInline
    Main(Integer i, Long l) {
      this.i = i;
      this.l = l;
    }

    @Override
    public String toString() {
      return "Main[" + i + ";" + l + "]";
    }
  }
}
