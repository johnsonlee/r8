// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.serviceloader;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderConstClassFromCalleeTest extends ServiceLoaderTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(ServiceImpl.class.getTypeName(), ServiceImpl2.class.getTypeName())
                    .getBytes(),
                "META-INF/services/" + Service.class.getTypeName(),
                Origin.unknown()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!")
        // Check that the call to ServiceLoader.load is removed.
        .inspect(inspector -> assertEquals(0, getServiceLoaderLoads(inspector)));
  }

  public static class Main {

    public static void main(String[] args) {
      for (Service service : ServiceLoader.load(getServiceClass(), null)) {
        service.print();
      }
    }

    private static Class<Service> getServiceClass() {
      return Service.class;
    }
  }

  public interface Service {

    void print();
  }

  public static class ServiceImpl implements Service {

    @Override
    public void print() {
      System.out.print("Hello");
    }
  }

  public static class ServiceImpl2 implements Service {

    @Override
    public void print() {
      System.out.println(", world!");
    }
  }
}
