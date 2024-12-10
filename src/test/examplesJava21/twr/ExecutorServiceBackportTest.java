// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package twr;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ExecutorServiceBackportTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withDexRuntimesStartingFromExcluding(Version.V4_4_4)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public ExecutorServiceBackportTest(TestParameters parameters) {
    super(parameters, ExecutorService.class, Main.class);
    registerTarget(AndroidApiLevel.BAKLAVA, 1);
    ignoreInvokes("isTerminated");
  }

  public static class Main {

    public static void main(String[] args) {
      ExecutorService executorService = new ForkJoinPool();
      System.out.println(executorService.isTerminated());
      executorService.close();
      System.out.println(executorService.isTerminated());
    }
  }
}
