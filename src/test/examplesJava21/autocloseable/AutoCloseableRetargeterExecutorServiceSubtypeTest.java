// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package autocloseable;

import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.getAutoCloseableAndroidClassData;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AutoCloseableRetargeterExecutorServiceSubtypeTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntime(CfVm.JDK21)
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines(
          "close",
          "close",
          "close",
          "close",
          "close",
          "close",
          "close",
          "close itf",
          "close itf",
          "close",
          "close",
          "close itf",
          "close itf",
          "close ac",
          "close ac direct",
          "SUCCESS");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
        .addLibraryClassFileData(getAutoCloseableAndroidClassData(parameters))
        .compile()
        .addRunClasspathClassFileData((getAutoCloseableAndroidClassData(parameters)))
        .inspect(this::assertCloseMethodsAndTags)
        .run(
            parameters.getRuntime(),
            Main.class,
            String.valueOf(parameters.getApiLevel().getLevel()))
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void assertCloseMethodsAndTags(CodeInspector inspector) {
    for (Class<? extends ExecutorService> clazz :
        ImmutableList.of(
            PrintForkJoinPool.class,
            OverrideForkJoinPool.class,
            Executor1.class,
            Executor2.class)) {
      ClassSubject subj = inspector.clazz(clazz);
      Assert.assertTrue(subj.isPresent());
      Assert.assertTrue(subj.allMethods().stream().anyMatch(m -> m.getFinalName().equals("close")));
      Assert.assertTrue(
          subj.getDexProgramClass()
              .getInterfaces()
              .contains(inspector.getFactory().autoCloseableType));
    }
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
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
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class PrintForkJoinPool extends ForkJoinPool {

    public void close() {
      super.close();
      System.out.println("close");
    }
  }

  public static class OverrideForkJoinPool extends ForkJoinPool {}

  public static class OverrideForkJoinPool2 extends OverrideForkJoinPool {}

  public static class Executor1 implements ExecutorService {

    boolean done = false;

    @Override
    public void shutdown() {
      done = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      done = true;
      return null;
    }

    @Override
    public boolean isShutdown() {
      return done;
    }

    @Override
    public boolean isTerminated() {
      return done;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    @Override
    public void execute(Runnable command) {}
  }

  public static class Executor2 implements ExecutorService {

    boolean done = false;

    @Override
    public void shutdown() {
      done = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      done = true;
      return null;
    }

    @Override
    public boolean isShutdown() {
      return done;
    }

    @Override
    public boolean isTerminated() {
      return done;
    }

    public void close() {
      ExecutorService.super.close();
      System.out.println("close");
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    @Override
    public void execute(Runnable command) {}
  }

  public interface ExecutorItfOverride extends ExecutorService {
    default void close() {
      ExecutorService.super.close();
      System.out.println("close itf");
    }
  }

  public interface ExecutorItfSub extends ExecutorService {}

  public static class ExecOverride1 implements ExecutorItfOverride {
    boolean done = false;

    @Override
    public void shutdown() {
      done = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      done = true;
      return null;
    }

    @Override
    public boolean isShutdown() {
      return done;
    }

    @Override
    public boolean isTerminated() {
      return done;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    @Override
    public void execute(Runnable command) {}
  }

  public static class ExecOverride2 implements ExecutorItfOverride {
    boolean done = false;

    @Override
    public void shutdown() {
      done = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      done = true;
      return null;
    }

    public void close() {
      ExecutorItfOverride.super.close();
      System.out.println("close");
    }

    @Override
    public boolean isShutdown() {
      return done;
    }

    @Override
    public boolean isTerminated() {
      return done;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    @Override
    public void execute(Runnable command) {}
  }

  public static class ExecSub1 implements ExecutorItfSub {
    boolean done = false;

    @Override
    public void shutdown() {
      done = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      done = true;
      return null;
    }

    @Override
    public boolean isShutdown() {
      return done;
    }

    @Override
    public boolean isTerminated() {
      return done;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    @Override
    public void execute(Runnable command) {}
  }

  public static class ExecSub2 implements ExecutorItfSub {
    boolean done = false;

    @Override
    public void shutdown() {
      done = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      done = true;
      return null;
    }

    public void close() {
      ExecutorItfSub.super.close();
      System.out.println("close");
    }

    @Override
    public boolean isShutdown() {
      return done;
    }

    @Override
    public boolean isTerminated() {
      return done;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    @Override
    public void execute(Runnable command) {}
  }

  public interface AutoCloseableOverride extends AutoCloseable {
    default void close() {
      System.out.println("close itf");
    }
  }

  public interface AutoCloseablesub extends AutoCloseable {}

  public static class AutoCloseableOverride1 implements AutoCloseableOverride {}

  public static class AutoCloseableOverride2 implements AutoCloseableOverride {
    public void close() {
      AutoCloseableOverride.super.close();
      System.out.println("close ac");
    }
  }

  public static class AutoCloseableOverride3 implements AutoCloseablesub {
    public void close() {
      System.out.println("close ac direct");
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      PrintForkJoinPool forkJoinPool = new PrintForkJoinPool();
      forkJoinPool.close();
      forkJoinPool = new PrintForkJoinPool();
      close(forkJoinPool);

      ForkJoinPool forkJoinPool2 = new PrintForkJoinPool();
      forkJoinPool2.close();

      ExecutorService executorService = new PrintForkJoinPool();
      executorService.close();
      executorService = new PrintForkJoinPool();
      closeExecutorService(executorService);

      OverrideForkJoinPool overrideForkJoinPool = new OverrideForkJoinPool();
      overrideForkJoinPool.close();
      overrideForkJoinPool = new OverrideForkJoinPool();
      close(overrideForkJoinPool);

      ForkJoinPool overrideForkJoinPool1 = new OverrideForkJoinPool();
      overrideForkJoinPool1.close();

      executorService = new OverrideForkJoinPool();
      executorService.close();
      executorService = new OverrideForkJoinPool();
      closeExecutorService(executorService);

      OverrideForkJoinPool2 overrideForkJoinPool2 = new OverrideForkJoinPool2();
      overrideForkJoinPool2.close();
      overrideForkJoinPool2 = new OverrideForkJoinPool2();
      close(overrideForkJoinPool2);

      ForkJoinPool overrideForkJoinPool22 = new OverrideForkJoinPool2();
      overrideForkJoinPool22.close();

      executorService = new OverrideForkJoinPool2();
      executorService.close();
      executorService = new OverrideForkJoinPool2();
      closeExecutorService(executorService);

      Executor1 executor1 = new Executor1();
      executor1.close();
      Executor2 executor2 = new Executor2();
      executor2.close();
      ExecutorService executor11 = new Executor1();
      close(executor11);
      ExecutorService executor22 = new Executor2();
      close(executor22);

      ExecOverride1 execOverride1 = new ExecOverride1();
      execOverride1.close();
      ExecOverride2 execOverride2 = new ExecOverride2();
      execOverride2.close();
      ExecSub1 execSub1 = new ExecSub1();
      execSub1.close();
      ExecSub2 execSub2 = new ExecSub2();
      execSub2.close();

      AutoCloseableOverride1 autoCloseableOverride1 = new AutoCloseableOverride1();
      autoCloseableOverride1.close();
      AutoCloseableOverride2 autoCloseableOverride2 = new AutoCloseableOverride2();
      autoCloseableOverride2.close();
      AutoCloseableOverride3 autoCloseableOverride3 = new AutoCloseableOverride3();
      autoCloseableOverride3.close();

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
