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
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepXmlTransitiveCodeReachabilityTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  public static String XML_WITH_CODE_REFERENCE =
      "<view xmlns:android=\"http://schemas.android.com/apk/res/android\" class=\""
          + Bar.class.getTypeName()
          + "\"/>\n";

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.xml.class)
        .addKeepXmlFor("@xml/xml_with_bar_reference")
        .addXml("xml_with_bar_reference.xml", XML_WITH_CODE_REFERENCE)
        .build(temp);
  }

  @Test
  public void testXmlReferenceWithBarClassInserted() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(TestClass.class, Bar.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(TestClass.class)
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName(
                  "xml", "xml_with_bar_reference");
            })
        .inspect(
            codeInspector -> {
              // TODO(b/326564914): Ensure that we handle code references from resources that are
              //  kept with xml keep rules (i.e., Bar is present).
              assertThat(codeInspector.clazz(Bar.class), isAbsent());
            });
  }

  public static class TestClass {
    public static void main(String[] args) {
      // Bar should be kept from the xml file, ensure that we can instantiate it.
      String classname = TestClass.class.getTypeName().replace("TestClass", "Bar");
      try {
        Class.forName(classname).newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  // Only referenced from XML file
  public static class Bar {
    public Bar() {
      System.out.println("init");
    }

    public Bar(String x) {
      System.out.println("init with string");
    }

    public void foo() {
      System.out.println("foo");
    }

    public static void bar() {
      System.out.println("bar");
    }
  }

  public static class R {
    public static class xml {
      public static int xml_with_bar_reference;
      public static int xml_with_foo_reference;
    }
  }
}
