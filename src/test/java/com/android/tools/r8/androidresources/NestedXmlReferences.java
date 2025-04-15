// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
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
public class NestedXmlReferences extends TestBase {

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

  public static String TO_BE_INCLUDED =
      "<view xmlns:android=\"http://schemas.android.com/apk/res/android\" class=\"%s\"/>\n";

  public static String INCLUDING_OTHER_XML =
      "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    android:orientation=\"vertical\"\n"
          + "    android:layout_width=\"match_parent\"\n"
          + "    android:layout_height=\"match_parent\"\n"
          + "    android:gravity=\"center_horizontal\""
          + "    class=\"%s\">\n"
          + "\n"
          + "    <include layout=\"@xml/to_be_included\"/>\n"
          + "\n"
          + "    <TextView android:layout_width=\"match_parent\"\n"
          + "              android:layout_height=\"wrap_content\"\n"
          + "              android:padding=\"10dp\" />\n"
          + "</LinearLayout>";

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.xml.class)
        .addXml("to_be_included.xml", String.format(TO_BE_INCLUDED, Foo.class.getTypeName()))
        .addXml(
            "including_other_xml.xml", String.format(INCLUDING_OTHER_XML, Bar.class.getTypeName()))
        .build(temp);
  }

  @Test
  public void testTransitiveReference() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class, Bar.class, Foo.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(TestClass.class)
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("xml", "including_other_xml");
              resourceTableInspector.assertContainsResourceWithName("xml", "to_be_included");
            })
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            codeInspector -> {
              ClassSubject barClass = codeInspector.clazz(Bar.class);
              assertThat(barClass, isPresentAndNotRenamed());
              ClassSubject fooClass = codeInspector.clazz(Foo.class);
              assertThat(fooClass, isPresentAndNotRenamed());
            })
        .assertSuccess();
  }

  public static class TestClass {
    public static void main(String[] args) {
      // Reference only the xml
      System.out.println(R.xml.including_other_xml);
    }
  }

  public static class Bar {}

  public static class Foo {}

  public static class R {
    public static class xml {
      public static int to_be_included;
      public static int including_other_xml;
    }
  }
}
