// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.serviceloader;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceLoaderRewritingWithAssumeNoSideEffectsTest extends ServiceLoaderTestBase {
  private final String EXPECTED_OUTPUT = StringUtils.lines("Hello World!");

  public interface Service {

    void print();
  }

  public static class ServiceImpl implements Service {

    @Override
    public void print() {
      System.out.println("Hello World!");
    }
  }

  public static class ServiceImpl2 implements Service {

    @Override
    public void print() {
      System.out.println("Hello World 2!");
    }
  }

  public static class MainRunner {
    public static void main(String[] args) {
      ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator().next().print();
    }
  }

  public static class HasNextRunner {
    public static void main(String[] args) {
      Iterator<Service> iterator =
          ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator();
      if (iterator.hasNext()) {
        iterator.next().print();
      }
    }
  }

  public static class MultipleCallsRunner {
    public static void main(String[] args) {
      Iterator<Service> iterator =
          ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator();
      if (iterator.hasNext() && iterator.hasNext()) {
        iterator.next().print();
      }
    }
  }

  public static class HasNextAfterNextRunner {
    public static void main(String[] args) {
      Iterator<Service> iterator =
          ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator();

      iterator.next().print();
      if (iterator.hasNext()) {
        System.out.println("not reached");
      }
    }
  }

  public static class HasNextAfterNextWithTryCatch {
    public static void main(String[] args) {
      try {
        Iterator<Service> iterator =
            ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator();

        iterator.next().print();
        if (iterator.hasNext()) {
          System.out.println("not reached");
        }
      } catch (Throwable t) {
        System.out.println("unreachable");
      }
    }
  }

  public static class LoopingRunner {
    public static void main(String[] args) {
      Iterator<Service> iterator =
          ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator();
      // Loop without a call to hasNext().
      for (int i = System.currentTimeMillis() > 0 ? 0 : 1; i < 1; ++i) {
        iterator.next().print();
      }
    }
  }

  public static class PhiRunner {
    public static void main(String[] args) {
      Iterator<Service> iterator =
          System.currentTimeMillis() > 0
              ? ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator()
              : null;
      iterator.next().print();
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ServiceLoaderRewritingWithAssumeNoSideEffectsTest(TestParameters parameters) {
    super(parameters);
  }

  private static void assertIteratorPresent(CodeInspector inspector, boolean expected) {
    assertEquals(0, getServiceLoaderLoads(inspector));

    boolean hasIteratorCall =
        inspector
            .streamInstructions()
            .anyMatch(ins -> ins.isInvoke() && ins.getMethod().name.toString().equals("iterator"));
    assertEquals(expected, hasIteratorCall);
  }

  private R8FullTestBuilder doTest(Class<?>... implClasses) throws IOException {
    return serviceLoaderTest(Service.class, implClasses)
        .addKeepRules("-assumenosideeffects class java.util.ServiceLoader { *** load(...); }");
  }

  @Test
  public void testRewritingWithNoImplsHasNext()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest()
        .addKeepMainRule(HasNextRunner.class)
        .compile()
        .run(parameters.getRuntime(), HasNextRunner.class)
        .assertSuccessWithOutput("")
        .inspect(inspector -> assertIteratorPresent(inspector, false));
  }

  @Test
  public void testRewritingWithMultiple()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest(ServiceImpl.class, ServiceImpl2.class)
        .addKeepMainRule(MainRunner.class)
        .compile()
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertIteratorPresent(inspector, false);
              // ServiceImpl2 gets removed since next() is called only once.
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testRewritingWithHasNext()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest(ServiceImpl.class)
        .addKeepMainRule(HasNextRunner.class)
        .compile()
        .run(parameters.getRuntime(), HasNextRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertIteratorPresent(inspector, false);
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testDoNotRewriteMultipleCalls()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest(ServiceImpl.class)
        .addKeepMainRule(MultipleCallsRunner.class)
        .compile()
        .run(parameters.getRuntime(), MultipleCallsRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertIteratorPresent(inspector, true);
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testDoNotRewriteHasNextAfterNext()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest(ServiceImpl.class)
        .addKeepMainRule(HasNextAfterNextRunner.class)
        .compile()
        .run(parameters.getRuntime(), HasNextAfterNextRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertIteratorPresent(inspector, true);
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testDoNotRewriteHasNextAfterNextWithTryCatch()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest(ServiceImpl.class)
        .addKeepMainRule(HasNextAfterNextWithTryCatch.class)
        .compile()
        .run(parameters.getRuntime(), HasNextAfterNextWithTryCatch.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertIteratorPresent(inspector, true);
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testDoNotRewriteHasNextAfterNextBlocks()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest(ServiceImpl.class)
        .addKeepMainRule(HasNextAfterNextRunner.class)
        .compile()
        .run(parameters.getRuntime(), HasNextAfterNextRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertIteratorPresent(inspector, true);
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testDoNotRewriteLoop()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest(ServiceImpl.class)
        .addKeepMainRule(LoopingRunner.class)
        .compile()
        .run(parameters.getRuntime(), LoopingRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertIteratorPresent(inspector, true);
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testDoNotRewritePhiUser()
      throws IOException, CompilationFailedException, ExecutionException {
    doTest(ServiceImpl.class)
        .addKeepMainRule(PhiRunner.class)
        .compile()
        .run(parameters.getRuntime(), PhiRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertIteratorPresent(inspector, true);
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }
}
