// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk21.autocloseable;

import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.getAutoCloseableAndroidClassData;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DeterminismChecker;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class AutoCloseableRetargeterExecutorServiceSubtypeTwrTest extends TestBase {

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
      StringUtils.lines("close", "close", "close", "close", "close", "SUCCESS");

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

  @Test
  public void testD8Determinism() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Path logDirectory = temp.newFolder().toPath();
    Path ref = compileWithD8Determinism(logDirectory);
    Path next = compileWithD8Determinism(logDirectory);
    assertProgramsEqual(ref, next);
    // Check that setting the determinism checker wrote a log file.
    assertTrue(Files.exists(logDirectory.resolve("0.log")));
  }

  private Path compileWithD8Determinism(Path logDirectory) throws Exception {
    return testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
        .addLibraryClassFileData(getAutoCloseableAndroidClassData(parameters))
        .allowStdoutMessages()
        .addOptionsModification(
            options ->
                options
                    .getTestingOptions()
                    .setDeterminismChecker(DeterminismChecker.createWithFileBacking(logDirectory)))
        .compile()
        .writeToZip();
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

  public static class Main {

    public static void main(String[] args) throws Exception {
      raw();
      subtypes();
      System.out.println("SUCCESS");
    }

    private static void subtypes() throws Exception {
      try (PrintForkJoinPool forkJoinPool = new PrintForkJoinPool()) {}

      try (ForkJoinPool forkJoinPool = new PrintForkJoinPool()) {}

      try (ExecutorService executorService = new PrintForkJoinPool()) {}

      try (OverrideForkJoinPool overrideForkJoinPool = new OverrideForkJoinPool()) {}

      try (ForkJoinPool overrideForkJoinPool1 = new OverrideForkJoinPool()) {}

      try (ExecutorService executorService = new OverrideForkJoinPool()) {}

      try (OverrideForkJoinPool2 overrideForkJoinPool = new OverrideForkJoinPool2()) {}

      try (OverrideForkJoinPool overrideForkJoinPool = new OverrideForkJoinPool2()) {}

      try (ForkJoinPool overrideForkJoinPool1 = new OverrideForkJoinPool2()) {}

      try (ExecutorService executorService = new OverrideForkJoinPool2()) {}

      try (Executor1 executorService = new Executor1()) {}

      try (ExecutorService executorService = new Executor1()) {}

      try (Executor2 executorService = new Executor2()) {}

      try (ExecutorService executorService = new Executor2()) {}
    }

    private static void raw() throws Exception {
      try (ForkJoinPool forkJoinPool = new ForkJoinPool()) {}

      try (ExecutorService forkJoinPool = new ForkJoinPool()) {}
    }
  }
}
