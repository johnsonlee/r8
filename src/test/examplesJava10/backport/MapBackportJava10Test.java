// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.HashMap;
import java.util.Map;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapBackportJava10Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK10)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public MapBackportJava10Test(TestParameters parameters) {
    super(parameters, Map.class, MapBackportJava10Main.class);
    // Note: None of the methods in this test exist in the latest android.jar. If/when they ship in
    // an actual API level, migrate these tests to MapBackportTest.

    // Available since API 1 and used to test created maps.
    ignoreInvokes("entrySet");
    ignoreInvokes("get");
    ignoreInvokes("put");
    ignoreInvokes("size");

    // Map.copyOf added in API 31.
    registerTarget(AndroidApiLevel.S, 4);
  }

  public static class MapBackportJava10Main {

    public static void main(String[] args) {
      testCopyOf();
    }

    private static void testCopyOf() {
      Object key0 = new Object();
      Object value0 = new Object();
      Object key1 = new Object();
      Object value1 = new Object();
      Map<Object, Object> original = new HashMap<>();
      original.put(key0, value0);
      original.put(key1, value1);
      Map<Object, Object> copy = Map.copyOf(original);
      assertEquals(2, copy.size());
      assertEquals(original, copy);
      assertSame(value0, copy.get(key0));
      assertSame(value1, copy.get(key1));
      assertMutationNotAllowed(copy);

      // Mutate the original backing collection and ensure it's not reflected in copy.
      original.put(key0, new Object());
      assertSame(value0, copy.get(key0));

      try {
        Map.copyOf(null);
        throw new AssertionError();
      } catch (NullPointerException expected) {
      }
      try {
        Map<Object, Object> map = new HashMap<>();
        map.put(null, new Object());
        Map.copyOf(map);
        throw new AssertionError();
      } catch (NullPointerException expected) {
      }
      try {
        Map<Object, Object> map = new HashMap<>();
        map.put(new Object(), null);
        Map.copyOf(map);
        throw new AssertionError();
      } catch (NullPointerException expected) {
      }
    }

    private static void assertMutationNotAllowed(Map<Object, Object> ofObject) {
      try {
        ofObject.put(new Object(), new Object());
        throw new AssertionError();
      } catch (UnsupportedOperationException expected) {
      }
      for (Map.Entry<Object, Object> entry : ofObject.entrySet()) {
        try {
          entry.setValue(new Object());
          throw new AssertionError();
        } catch (UnsupportedOperationException expected) {
        }
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
