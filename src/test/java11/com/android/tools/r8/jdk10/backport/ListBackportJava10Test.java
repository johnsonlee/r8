// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk10.backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ListBackportJava10Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK10)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public ListBackportJava10Test(TestParameters parameters) {
    super(parameters, List.class, ListBackportJava10Main.class);
    // Note: None of the methods in this test exist in the latest android.jar. If/when they ship in
    // an actual API level, migrate these tests to ListBackportTest.

    // Available since API 1 and used to test created lists.
    ignoreInvokes("add");
    ignoreInvokes("get");
    ignoreInvokes("set");
    ignoreInvokes("size");

    // List.copyOf added in API 31.
    registerTarget(AndroidApiLevel.S, 3);
  }

  public static class ListBackportJava10Main {

    public static void main(String[] args) {
      testCopyOf();
    }

    private static void testCopyOf() {
      Object anObject0 = new Object();
      Object anObject1 = new Object();
      List<Object> original = Arrays.asList(anObject0, anObject1);
      List<Object> copy = List.copyOf(original);
      assertEquals(2, copy.size());
      assertEquals(original, copy);
      assertSame(anObject0, copy.get(0));
      assertSame(anObject1, copy.get(1));
      assertMutationNotAllowed(copy);

      // Mutate the original backing collection and ensure it's not reflected in copy.
      original.set(0, new Object());
      assertSame(anObject0, copy.get(0));

      try {
        List.copyOf(null);
        throw new AssertionError();
      } catch (NullPointerException expected) {
      }
      try {
        List.copyOf(Arrays.asList(1, null, 2));
        throw new AssertionError();
      } catch (NullPointerException expected) {
      }
    }

    private static void assertMutationNotAllowed(List<Object> ofObject) {
      try {
        ofObject.add(new Object());
        throw new AssertionError();
      } catch (UnsupportedOperationException expected) {
      }
      try {
        ofObject.set(0, new Object());
        throw new AssertionError();
      } catch (UnsupportedOperationException expected) {
      }
    }

    private static void assertSame(Object expected, Object actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
      }
    }

    private static void assertEquals(Object expected, Object actual) {
      if (expected != actual && !expected.equals(actual)) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
      }
    }
  }
}
