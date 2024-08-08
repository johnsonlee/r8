// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.serviceloader;

import static com.android.tools.r8.ToolHelper.DexVm.Version.V7_0_0;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceLoaderRewritingTest extends ServiceLoaderTestBase {

  private final String EXPECTED_OUTPUT =
      StringUtils.lines("Hello World!", "Hello World!", "Hello World!");

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

  public static class ServiceImplNoDefaultConstructor extends ServiceImpl {
    public ServiceImplNoDefaultConstructor(int unused) {}
  }

  public static class ServiceImplNonPublicConstructor extends ServiceImpl {
    ServiceImplNonPublicConstructor() {}
  }

  public static class MainRunner {

    public static void main(String[] args) {
      ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator().next().print();
      ServiceLoader.load(Service.class, null).iterator().next().print();
      for (Service x : ServiceLoader.load(Service.class, Service.class.getClassLoader())) {
        x.print();
      }
      // TODO(b/120436373) The stream API for ServiceLoader was added in Java 9. When we can model
      //   streams correctly, uncomment the lines below and adjust EXPECTED_OUTPUT.
      // ServiceLoader.load(Service.class, Service.class.getClassLoader())
      //   .stream().forEach(x -> x.get().print());
    }
  }

  public static class MainWithTryCatchRunner {

    public static void main(String[] args) {
      try {
        ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator().next().print();
      } catch (Throwable e) {
        System.out.println(e);
        throw e;
      }
    }
  }

  public static class OtherRunner {

    public static void main(String[] args) {
      ServiceLoader.load(Service.class).iterator().next().print();
      ServiceLoader.load(Service.class, Thread.currentThread().getContextClassLoader())
          .iterator()
          .next()
          .print();
      ServiceLoader.load(Service.class, OtherRunner.class.getClassLoader())
          .iterator()
          .next()
          .print();
    }
  }

  public static class EscapingRunner {

    public ServiceLoader<Service> serviceImplementations;

    @NeverInline
    public ServiceLoader<Service> getServices() {
      return ServiceLoader.load(Service.class, Thread.currentThread().getContextClassLoader());
    }

    @NeverInline
    public void printServices() {
      print(ServiceLoader.load(Service.class, Thread.currentThread().getContextClassLoader()));
    }

    @NeverInline
    public void print(ServiceLoader<Service> loader) {
      loader.iterator().next().print();
    }

    @NeverInline
    public void assignServicesField() {
      serviceImplementations =
          ServiceLoader.load(Service.class, Thread.currentThread().getContextClassLoader());
    }

    public static void main(String[] args) {
      EscapingRunner escapingRunner = new EscapingRunner();
      escapingRunner.getServices().iterator().next().print();
      escapingRunner.printServices();
      escapingRunner.assignServicesField();
      escapingRunner.print(escapingRunner.serviceImplementations);
    }
  }

  public static class LoadWhereClassLoaderIsPhi {

    public static void main(String[] args) {
      ServiceLoader.load(
              Service.class,
              System.currentTimeMillis() > 0
                  ? Thread.currentThread().getContextClassLoader()
                  : null)
          .iterator()
          .next()
          .print();
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ServiceLoaderRewritingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testRewritingWithNoImpls()
      throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(null)
        .addKeepMainRule(MainRunner.class)
        .compile()
        .run(parameters.getRuntime(), MainRunner.class)
        .assertFailureWithErrorThatThrows(NoSuchElementException.class)
        .inspectFailure(inspector -> assertEquals(0, getServiceLoaderLoads(inspector)));
  }

  @Test
  public void testRewritings() throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, ServiceImpl.class)
        .addKeepMainRule(MainRunner.class)
        .compile()
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              List<String> lines = dataResourceConsumer.get("META-INF/services/com.example.Foo");
              assertEquals(0, getServiceLoaderLoads(inspector));
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testRewritingWithMultiple()
      throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, ServiceImpl.class, ServiceImpl2.class)
        .addKeepMainRule(MainRunner.class)
        .compile()
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT + StringUtils.lines("Hello World 2!"))
        .inspect(
            inspector -> {
              assertEquals(0, getServiceLoaderLoads(inspector));
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class, ServiceImpl2.class);
            });
  }

  @Test
  public void testRewritingsWithCatchHandlers()
      throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, ServiceImpl.class, ServiceImpl2.class)
        .addKeepMainRule(MainWithTryCatchRunner.class)
        .compile()
        .run(parameters.getRuntime(), MainWithTryCatchRunner.class)
        .assertSuccessWithOutput(StringUtils.lines("Hello World!"))
        .inspect(
            inspector -> {
              assertEquals(0, getServiceLoaderLoads(inspector));
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class, ServiceImpl2.class);
            });
  }

  @Test
  public void testDoNoRewrite() throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, ServiceImpl.class)
        .addKeepMainRule(OtherRunner.class)
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(REWRITER_DIAGNOSTICS)
        .run(parameters.getRuntime(), OtherRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertEquals(3, getServiceLoaderLoads(inspector));
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testDoNoRewriteNoDefaultConstructor()
      throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, ServiceImplNoDefaultConstructor.class)
        .addKeepMainRule(MainRunner.class)
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(REWRITER_DIAGNOSTICS)
        .run(parameters.getRuntime(), MainRunner.class)
        .assertFailureWithErrorThatThrows(ServiceConfigurationError.class);
  }

  @Test
  public void testDoNoRewriteNonSubclass()
      throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, MainRunner.class)
        .addKeepMainRule(MainRunner.class)
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(REWRITER_DIAGNOSTICS)
        .run(parameters.getRuntime(), MainRunner.class)
        .assertFailureWithErrorThatThrows(ServiceConfigurationError.class);
  }

  @Test
  public void testDoNoRewriteNonPublicConstructor()
      throws IOException, CompilationFailedException, ExecutionException {
    // This throws a ServiceConfigurationError only on Android 7.
    serviceLoaderTest(Service.class, ServiceImplNonPublicConstructor.class)
        .addKeepMainRule(MainRunner.class)
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(REWRITER_DIAGNOSTICS)
        .run(parameters.getRuntime(), MainRunner.class)
        .applyIf(
            parameters.isCfRuntime() || parameters.getDexRuntimeVersion() != V7_0_0,
            runResult -> runResult.assertSuccessWithOutput(EXPECTED_OUTPUT),
            runResult ->
                runResult.assertFailureWithErrorThatThrows(ServiceConfigurationError.class));
  }

  @Test
  public void testDoNoRewriteWhenEscaping()
      throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, ServiceImpl.class)
        .addKeepMainRule(EscapingRunner.class)
        .enableInliningAnnotations()
        .addDontObfuscate()
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(REWRITER_DIAGNOSTICS)
        .run(parameters.getRuntime(), EscapingRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertEquals(3, getServiceLoaderLoads(inspector));
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testDoNoRewriteWhenClassLoaderIsPhi()
      throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, ServiceImpl.class)
        .addKeepMainRule(LoadWhereClassLoaderIsPhi.class)
        .enableInliningAnnotations()
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(REWRITER_DIAGNOSTICS)
        .run(parameters.getRuntime(), LoadWhereClassLoaderIsPhi.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              assertEquals(1, getServiceLoaderLoads(inspector));
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }

  @Test
  public void testKeepAsOriginal()
      throws IOException, CompilationFailedException, ExecutionException {
    // The CL that changed behaviour after Nougat is:
    // https://android-review.googlesource.com/c/platform/libcore/+/273135
    assumeTrue(
        parameters.getRuntime().isCf()
            || !parameters.getRuntime().asDex().getVm().getVersion().equals(V7_0_0));
    serviceLoaderTest(Service.class, ServiceImpl.class)
        .addKeepMainRule(MainRunner.class)
        .addKeepClassRules(Service.class)
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(REWRITER_DIAGNOSTICS)
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertEquals(3, getServiceLoaderLoads(inspector));
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }
}
