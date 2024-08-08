// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.serviceloader;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceWithFeatureTest extends ServiceLoaderTestBase {

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
      ClassLoader classLoader = Service.class.getClassLoader();
      checkNotNull(classLoader);
      for (Service x : ServiceLoader.load(Service.class, classLoader)) {
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

  public ServiceWithFeatureTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testRewritings() throws Exception {
    serviceLoaderTestNoClasses(Service.class, ServiceImpl.class)
        .addFeatureSplit(MainRunner.class, Service.class, ServiceImpl.class)
        .enableInliningAnnotations()
        .addKeepMainRule(MainRunner.class)
        .compile()
        .inspect(
            inspector -> {},
            inspector -> {
              verifyNoServiceLoaderLoads(inspector.clazz(MainRunner.class));
              verifyServiceMetaInf(inspector, Service.class, ServiceImpl.class);
            });
  }
}
