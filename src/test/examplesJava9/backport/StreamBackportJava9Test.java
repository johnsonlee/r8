// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.stream.Stream;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class StreamBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .enableApiLevelsForCf()
        .build();
  }

  public StreamBackportJava9Test(TestParameters parameters) {
    super(parameters, Stream.class, StreamBackportJava9Main.class);
    // Note: The methods in this test exist from Android U. However, they are only available from
    // Java 9. When tests build with Java 9 migrate to StreamBackportTest and add insert
    // StreamBackportJava9Main as an inner class here.

    // Available since N as part of library desugaring.
    ignoreInvokes("of");
    ignoreInvokes("empty");
    ignoreInvokes("count");

    registerTarget(AndroidApiLevel.U, 2);
  }

  public static class StreamBackportJava9Main {

    public static void main(String[] args) {
      testOfNullable();
    }

    public static void testOfNullable() {
      Object guineaPig = new Object();
      Stream<Object> streamNonEmpty = Stream.ofNullable(guineaPig);
      assertTrue(streamNonEmpty.count() == 1);
      Stream<Object> streamEmpty = Stream.ofNullable(null);
      assertTrue(streamEmpty.count() == 0);
    }

    private static void assertTrue(boolean value) {
      if (!value) {
        throw new AssertionError("Expected <true> but was <false>");
      }
    }
  }
}
