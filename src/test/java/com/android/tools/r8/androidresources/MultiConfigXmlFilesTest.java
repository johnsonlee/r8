// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultiConfigXmlFilesTest extends TestBase {

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
      "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" class=\"%s\"/>\n";

  public AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.xml.class)
        // We add two configurations of the xml file, one for default and one for -v24.
        // They reference different classes, both of which we should keep.
        .addXml(
            "xml_with_class_reference.xml",
            String.format(VIEW_WITH_CLASS_ATTRIBUTE_REFERENCE, Foo.class.getTypeName()))
        .addApiSpecificXml(
            "xml_with_class_reference.xml",
            String.format(VIEW_WITH_CLASS_ATTRIBUTE_REFERENCE, Bar.class.getTypeName()))
        .build(temp);
  }

  @Test
  public void testClassReferences() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class, Foo.class, Bar.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(TestClass.class)
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName(
                  "xml", "xml_with_class_reference");
            })
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Foo.class), isPresent());
              assertThat(codeInspector.clazz(Bar.class), isPresent());
            })
        .assertSuccess();
  }

  public static class TestClass {
    public static void main(String[] args) {
      System.out.println(R.xml.xml_with_class_reference);
    }
  }

  // Only referenced from XML file
  public static class Bar {}

  // Only referenced from XML file
  public static class Foo {}

  public static class R {
    public static class xml {
      public static int xml_with_class_reference;
    }
  }
}
