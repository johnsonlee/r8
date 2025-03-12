// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class CharSequenceBackportJava11Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public CharSequenceBackportJava11Test(TestParameters parameters) {
    super(parameters, Character.class, CharSequenceBackportJava11Main.class);
    // Note: None of the methods in this test exist in the latest android.jar. If/when they ship in
    // an actual API level, migrate these tests to CharacterBackportTest.
  }

  public static class CharSequenceBackportJava11Main {
    public static void main(String[] args) {
      testCompare();
    }

    private static void testCompare() {
      assertTrue(CharSequence.compare("Hello", "Hello") == 0);

      assertTrue(CharSequence.compare("Hey", "Hello") > 0);
      assertTrue(CharSequence.compare("Hello", "Hey") < 0);

      assertTrue(CharSequence.compare("Hel", "Hello") < 0);
      assertTrue(CharSequence.compare("Hello", "Hel") > 0);

      assertTrue(CharSequence.compare("", "") == 0);
      assertTrue(CharSequence.compare("", "Hello") < 0);
      assertTrue(CharSequence.compare("Hello", "") > 0);

      // Different CharSequence types:
      assertTrue(CharSequence.compare("Hello", new StringBuilder("Hello")) == 0);
      assertTrue(CharSequence.compare(new StringBuffer("hey"), "Hello") > 0);
      assertTrue(CharSequence.compare(new StringBuffer("Hello"), new StringBuilder("Hey")) < 0);

      try {
        throw new AssertionError(CharSequence.compare(null, "Hello"));
      } catch (NullPointerException expected) {
      }
      try {
        throw new AssertionError(CharSequence.compare("Hello", null));
      } catch (NullPointerException expected) {
      }
      try {
        // Ensure a == b fast path does not happen before null checks.
        throw new AssertionError(CharSequence.compare(null, null));
      } catch (NullPointerException expected) {
      }
    }

    private static void assertTrue(boolean value) {
      if (!value) {
        throw new AssertionError("Expected <true> but was <false>");
      }
    }
  }
}
