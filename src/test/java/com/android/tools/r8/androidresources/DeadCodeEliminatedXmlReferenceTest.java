// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DeadCodeEliminatedXmlReferenceTest extends TestBase {

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

  public static String VIEW_WITH_CLASS_ATTRIBUTE_REFERENCE =
      "<view xmlns:android=\"http://schemas.android.com/apk/res/android\" class=\"%s\"/>\n";

  public static AndroidTestResource getTestResources(TemporaryFolder temp, String xmlFile)
      throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.xml.class)
        .addXml(
            "xml_with_bar_reference.xml",
            String.format(VIEW_WITH_CLASS_ATTRIBUTE_REFERENCE, Bar.class.getTypeName()))
        .build(temp);
  }

  @Test
  public void testDeadReference() throws Exception {
    String formatedXmlFile =
        String.format(VIEW_WITH_CLASS_ATTRIBUTE_REFERENCE, Bar.class.getTypeName());
    testForR8(parameters)
        .setMinApi(parameters)
        .addProgramClasses(TestClass.class, Bar.class)
        .addAndroidResources(getTestResources(temp, formatedXmlFile))
        .addKeepMainRule(TestClass.class)
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              // If the TestClass is in the D8 compilation, then we have a reference to
              // the xml file from X() that is not dead code.
              if (!parameters.getPartialCompilationTestParameters().isRandom()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "xml", "xml_with_bar_reference");
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            codeInspector -> {
              ClassSubject barClass = codeInspector.clazz(Bar.class);
              if (!parameters.getPartialCompilationTestParameters().isRandom()) {
                assertThat(barClass, isAbsent());
              }
            })
        .assertSuccess();
  }

  public static class TestClass {
    public static void main(String[] args) {
      // Reference only the xml
      int i = 42;
      if (i == 43) {
        System.out.println(R.xml.xml_with_bar_reference);
      }
    }

    public static void x() {
      System.out.println(R.xml.xml_with_bar_reference);
    }
  }

  // Only referenced from XML file
  public static class Bar {}

  public static class R {
    public static class xml {
      public static int xml_with_bar_reference;
    }
  }
}
