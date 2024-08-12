// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultFieldValueJoinerWithUnknownDynamicTypeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keepclassmembers class " + Main.class.getTypeName() + "{",
            "  *** emptyList();",
            "  *** emptySet(boolean);",
            "}")
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/358629308): Should succeed.
        .assertFailureWithErrorThatThrows(RuntimeException.class);
  }

  @NeverClassInline
  static class Main {

    static final Collection<Object> DEFAULT = emptyList();

    Collection<Object> cache;

    public static void main(String[] args) {
      Main main = new Main();
      Collection<Object> emptySet = main.test(false);
      Collection<Object> emptyList = main.test(true);
      checkNotIdentical(emptySet, emptyList);
      Collection<Object> cachedEmptyList = main.test(true);
      checkIdentical(emptyList, cachedEmptyList);
    }

    @NeverInline
    Collection<Object> test(boolean doThrow) {
      if (cache != null) {
        return cache;
      }
      try {
        return emptySet(doThrow);
      } catch (Throwable t) {
        cache = DEFAULT;
        return cache;
      }
    }

    // @Keep
    static Collection<Object> emptyList() {
      return new ArrayList<>();
    }

    // @Keep
    static Collection<Object> emptySet(boolean doThrow) {
      if (doThrow) {
        throw new RuntimeException();
      }
      return Collections.emptySet();
    }

    static void checkIdentical(Object o1, Object o2) {
      if (o1 != o2) {
        throw new RuntimeException();
      }
    }

    static void checkNotIdentical(Object o1, Object o2) {
      if (o1 == o2) {
        throw new RuntimeException();
      }
    }
  }
}
