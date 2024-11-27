// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SetBackportJava10Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK10)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public SetBackportJava10Test(TestParameters parameters) {
    super(parameters, Set.class, SetBackportJava10Main.class);
    // Note: None of the methods in this test exist in the latest android.jar. If/when they ship in
    // an actual API level, migrate these tests to SetBackportTest.

    // Available since API 1 and used to test created sets.
    ignoreInvokes("add");
    ignoreInvokes("contains");
    ignoreInvokes("size");

    // Set.of added in API 30
    registerTarget(AndroidApiLevel.R, 1);
    // Set.copyOf added in API 31
    registerTarget(AndroidApiLevel.S, 5);
  }

  public static class SetBackportJava10Main {

    public static void main(String[] args) {
      testCopyOf();
    }

    private static void testCopyOf() {
      Object anObject0 = new Object();
      Object anObject1 = new Object();
      List<Object> original = Arrays.asList(anObject0, anObject1);
      Set<Object> copy = Set.copyOf(original);
      assertEquals(2, copy.size());
      assertEquals(new HashSet<>(original), copy);
      assertTrue(copy.contains(anObject0));
      assertTrue(copy.contains(anObject1));
      assertMutationNotAllowed(copy);

      // Mutate the original backing collection and ensure it's not reflected in copy.
      Object newObject = new Object();
      original.set(0, newObject);
      assertFalse(copy.contains(newObject));

      // Ensure duplicates are allowed and are de-duped.
      assertEquals(Set.of(1, 2), Set.copyOf(List.of(1, 2, 1, 2)));

      try {
        Set.copyOf(null);
        throw new AssertionError();
      } catch (NullPointerException expected) {
      }
      try {
        Set.copyOf(Arrays.asList(1, null, 2));
        throw new AssertionError();
      } catch (NullPointerException expected) {
      }
    }

    private static void assertMutationNotAllowed(Set<Object> ofObject) {
      try {
        ofObject.add(new Object());
        throw new AssertionError();
      } catch (UnsupportedOperationException expected) {
      }
    }

    private static void assertTrue(boolean value) {
      if (!value) {
        throw new AssertionError("Expected <true> but was <false>");
      }
    }

    private static void assertFalse(boolean value) {
      if (value) {
        throw new AssertionError("Expected <false> but was <true>");
      }
    }

    private static void assertEquals(Object expected, Object actual) {
      if (expected != actual && !expected.equals(actual)) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
      }
    }
  }
}
