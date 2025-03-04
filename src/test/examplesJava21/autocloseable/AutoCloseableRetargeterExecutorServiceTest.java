// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package autocloseable;

import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.getAutoCloseableAndroidClassData;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AutoCloseableRetargeterExecutorServiceTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .build();
  }

  private static String EXPECTED_OUTPUT = StringUtils.lines("false", "true", "SUCCESS");
  private static String EXPECTED_OUTPUT_24 =
      StringUtils.lines("false", "true", "false", "false", "SUCCESS");

  private String getExpectedOutput() {
    return parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.M)
        ? EXPECTED_OUTPUT
        : EXPECTED_OUTPUT_24;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
        .addLibraryClassFileData(getAutoCloseableAndroidClassData(parameters))
        .compile()
        .addRunClasspathClassFileData((getAutoCloseableAndroidClassData(parameters)))
        .run(
            parameters.getRuntime(),
            Main.class,
            String.valueOf(parameters.getApiLevel().getLevel()))
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .addInnerClassesAndStrippedOuter(getClass())
        .addInliningAnnotations()
        .setMinApi(parameters)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
        .addLibraryClassFileData(getAutoCloseableAndroidClassData(parameters))
        .compile()
        .addRunClasspathClassFileData((getAutoCloseableAndroidClassData(parameters)))
        .run(
            parameters.getRuntime(),
            Main.class,
            String.valueOf(parameters.getApiLevel().getLevel()))
        .assertSuccessWithOutput(getExpectedOutput());
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      int api = Integer.parseInt(args[0]);

      ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
      System.out.println(pool.isTerminated());
      pool.close();
      System.out.println(pool.isTerminated());

      if (api >= 21) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPool.close();
        forkJoinPool = new ForkJoinPool();
        close(forkJoinPool);

        ExecutorService executorService = new ForkJoinPool();
        executorService.close();
        executorService = new ForkJoinPool();
        closeExecutorService(executorService);
      }

      if (api >= 24) {
        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        System.out.println(commonPool.isTerminated());
        commonPool.close();
        // Common pool should never terminate.
        System.out.println(commonPool.isTerminated());
      }

      System.out.println("SUCCESS");
    }

    @NeverInline
    public static void closeExecutorService(ExecutorService ac) throws Exception {
      ac.close();
    }

    @NeverInline
    public static void close(AutoCloseable ac) throws Exception {
      ac.close();
    }
  }
}
