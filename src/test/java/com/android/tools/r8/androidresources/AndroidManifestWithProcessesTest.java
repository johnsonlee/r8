// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidManifestWithProcessesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters()
        .withDefaultDexRuntime()
        .withAllApiLevels()
        .withPartialCompilation()
        .build();
  }

  private static final String INNER_CLASS_NAME =
      AndroidManifestWithProcessesTest.class.getSimpleName() + "$" + Bar.class.getSimpleName();

  public static String MANIFEST_WITH_PROCESS =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    xmlns:tools=\"http://schemas.android.com/tools\""
          + "    package=\""
          + AndroidManifestWithProcessesTest.class.getPackage().getName()
          + "\""
          + ">\n"
          + "    <application>\n"
          + "        <processes>\n"
          + "            <process android:process=\":sub\" android:name=\""
          + INNER_CLASS_NAME
          + "\"/>\n"
          + "        </processes>\n"
          + "    </application>\n"
          + "</manifest>";

  public AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder().withManifest(MANIFEST_WITH_PROCESS).build(temp);
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
