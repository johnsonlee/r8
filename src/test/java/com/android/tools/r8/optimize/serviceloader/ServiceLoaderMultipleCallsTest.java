// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.serviceloader;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceLoaderMultipleCallsTest extends ServiceLoaderTestBase {
  private final String EXPECTED_OUTPUT = StringUtils.lines("Hello World!", "Hello World!");

  public interface Service {

    void print();
  }

  public static class ServiceImpl implements Service {

    @Override
    public void print() {
      System.out.println("Hello World!");
    }
  }

  public static class MainRunner {

    public static void main(String[] args) {
      run1();
      run2();
    }

    @NeverInline
    public static void run1() {
      for (Service x : ServiceLoader.load(Service.class, Service.class.getClassLoader())) {
        x.print();
      }
    }

    @NeverInline
    public static void run2() {
      for (Service x : ServiceLoader.load(Service.class, Service.class.getClassLoader())) {
        x.print();
      }
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ServiceLoaderMultipleCallsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testRewritings() throws IOException, CompilationFailedException, ExecutionException {
    serviceLoaderTest(Service.class, ServiceImpl.class)
        .addKeepMainRule(MainRunner.class)
        .enableInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              // Check that we have actually rewritten the calls to ServiceLoader.load.
              assertEquals(0, getServiceLoaderLoads(inspector));
              // Check the synthesized service loader method is a single shared method.
              // Due to minification we just check there is only a single synthetic class with a
              // single static method.
              boolean found = false;
              for (FoundClassSubject clazz : inspector.allClasses()) {
                if (clazz.isSynthetic()) {
                  assertFalse(found);
                  found = true;
                  assertEquals(1, clazz.allMethods().size());
                  clazz.forAllMethods(m -> assertTrue(m.isStatic()));
                }
              }
            });
  }
}
