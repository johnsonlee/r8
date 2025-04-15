// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidManifestWithApplicationAttributesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public String attributeName;

  @Parameters(name = "{0}, attributeName: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withAllApiLevels()
            .withPartialCompilation()
            .build(),
        ImmutableList.of("backupAgent", "appComponentFactory", "zygotePreloadName"));
  }

  private static final String INNER_CLASS_NAME =
      AndroidManifestWithApplicationAttributesTest.class.getSimpleName()
          + "$"
          + Bar.class.getSimpleName();

  public static String MANIFEST_WITH_MANIFEST_PACKAGE_USAGE_SUB_APPLICATION =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    xmlns:tools=\"http://schemas.android.com/tools\""
          + "    package=\""
          + AndroidManifestWithApplicationAttributesTest.class.getPackage().getName()
          + "\""
          + ">\n"
          + "    <application android:%s=\""
          + INNER_CLASS_NAME
          + "\"/>"
          + "</manifest>";

  public AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withManifest(
            String.format(MANIFEST_WITH_MANIFEST_PACKAGE_USAGE_SUB_APPLICATION, attributeName))
        .build(temp);
  }

  @Test
  public void testManifestReferences() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Bar.class)
        .addAndroidResources(getTestResources(temp))
        .enableOptimizedShrinking()
        .compile()
        .inspect(
            codeInspector -> {
              ClassSubject barClass = codeInspector.clazz(Bar.class);
              assertThat(barClass, isPresentAndNotRenamed());
              // We should have two and only two methods, the two constructors.
              assertEquals(barClass.allMethods(MethodSubject::isInstanceInitializer).size(), 2);
              if (!parameters.isRandomPartialCompilation()) {
                assertEquals(barClass.allMethods().size(), 2);
              }
            });
  }

  // Only referenced from Manifest file
  public static class Bar {
    // We should keep this since this is a provider
    public Bar() {
      System.out.println("init");
    }

    public Bar(String x) {
      System.out.println("init with string");
    }

    public void bar() {
      System.out.println("never kept");
    }
  }
}
