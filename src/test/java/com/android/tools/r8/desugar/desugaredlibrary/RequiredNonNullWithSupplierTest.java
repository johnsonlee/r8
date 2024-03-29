// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RequiredNonNullWithSupplierTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("SuppliedString2", "OK");

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameterized.Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RequiredNonNullWithSupplierTest(
      boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testRequiredNonNullWithSupplierTest() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(RequiredNonNullWithSupplierTest.class)
          .run(parameters.getRuntime(), Executor.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(RequiredNonNullWithSupplierTest.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Executor {

    public static void main(String[] args) {
      Object o = System.currentTimeMillis() > 10 ? new Object() : new Object();
      Objects.requireNonNull(o, () -> "SuppliedString");
      try {
        Objects.requireNonNull(null, () -> "SuppliedString2");
        throw new AssertionError("Unexpected");
      } catch (NullPointerException e) {
        System.out.println(e.getMessage());
      }
      try {
        Objects.requireNonNull(null, (Supplier<String>) null);
        throw new AssertionError("Unexpected");
      } catch (NullPointerException e) {
        // Normally we would want to print the exception message, but some ART versions have a bug
        // where they erroneously calls supplier.get() on a null reference which produces the NPE
        // but with an ART-defined message. See b/147419222.
        System.out.println("OK");
      }
    }
  }
}
