// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.serviceloader;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceWithFeatureNullClassLoaderTest extends ServiceLoaderTestBase {

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
    }

    @NeverInline
    public static void run1() {
      for (Service x : ServiceLoader.load(Service.class, null)) {
        x.print();
      }
    }

    @NeverInline
    public static void checkNotNull(ClassLoader classLoader) {
      if (classLoader == null) {
        throw new NullPointerException("ClassLoader should not be null");
      }
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ServiceWithFeatureNullClassLoaderTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testNoRewritings() throws Exception {
    R8TestCompileResult result =
        serviceLoaderTestNoClasses(Service.class, ServiceImpl.class)
            .addFeatureSplit(MainRunner.class, Service.class, ServiceImpl.class)
            .enableInliningAnnotations()
            .addKeepMainRule(MainRunner.class)
            .allowDiagnosticInfoMessages()
            .compileWithExpectedDiagnostics(expectedDiagnostics);

    CodeInspector inspector = result.featureInspector();
    assertEquals(getServiceLoaderLoads(inspector), 1);
    // Check that we have not removed the service configuration from META-INF/services.
    verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
  }
}
