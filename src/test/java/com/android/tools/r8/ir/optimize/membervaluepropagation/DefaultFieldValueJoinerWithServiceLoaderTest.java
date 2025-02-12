// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultFieldValueJoinerWithServiceLoaderTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    List<String> serviceImplementations = Lists.newArrayList();
    serviceImplementations.add(B.class.getTypeName());
    serviceImplementations.add(C.class.getTypeName());

    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addDataResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(serviceImplementations).getBytes(),
                "META-INF/services/" + A.class.getTypeName(),
                Origin.unknown()))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "1", "0", "2");
  }

  public interface A {

    void init();

    void print();
  }

  public static class B implements A {

    int f;

    @Override
    public void init() {
      f = 1;
    }

    @Override
    public void print() {
      System.out.println(f);
    }
  }

  public static class C implements A {

    int g;

    @Override
    public void init() {
      g = 2;
    }

    @Override
    public void print() {
      System.out.println(g);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      for (A a : ServiceLoader.load(A.class)) {
        a.print();
        a.init();
        a.print();
      }
    }
  }
}
