// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources.xmltags;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class XmlTagClassReferenceTest extends TestBase {

  public static final String xmlWithBarReference = "xml_with_bar_reference";
  public static final String xmlWithFooReference = "xml_with_foo_reference";

  public static final List<String> allXml =
      ImmutableList.of(xmlWithBarReference, xmlWithFooReference);

  public static final List<Class> allClasses = ImmutableList.of(Foo.class, Bar.class);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  public static String NO_REF_XML = "<Z/>";

  public static String XML_WITH_DIRECT_FOO_REFERENCE = "<" + Foo.class.getTypeName() + "/>";

  public static String XML_WITH_DIRECT_BAR_REFERENCE = "<" + Bar.class.getTypeName() + "/>";

  public static String XML_NO_PACKAGE =
      "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
          + "    android:layout_width=\"match_parent\"\n"
          + "    android:layout_height=\"match_parent\">\n"
          + "\n"
          + "    <view class=\".Foo\"\n"
          + "        android:layout_width=\"300dp\"\n"
          + "        android:layout_height=\"300dp\"\n"
          + "        android:paddingLeft=\"20dp\"\n"
          + "        android:paddingBottom=\"40dp\"\n/>"
          + "\n"
          + "</FrameLayout>";

  public static AndroidTestResource getTestResources(
      TemporaryFolder temp, String xmlForXmlWithBar, String xmlForXmlWithFoo) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .setPackageName(R.class.getPackage().getName())
        .addRClassInitializeWithDefaultValues(R.xml.class)
        .addXml(xmlWithFooReference + ".xml", xmlForXmlWithFoo)
        .addXml(xmlWithBarReference + ".xml", xmlForXmlWithBar)
        .build(temp);
  }

  @Test
  public void testTextXml() throws Exception {
    test(
        getTestResources(temp, NO_REF_XML, XML_WITH_DIRECT_FOO_REFERENCE),
        ImmutableList.of(Foo.class),
        allXml);
  }

  @Test
  public void testTextXmlNoPackagePrefix() throws Exception {
    test(getTestResources(temp, NO_REF_XML, XML_NO_PACKAGE), ImmutableList.of(Foo.class), allXml);
  }

  @Test
  public void testTransitiveReferences() throws Exception {
    test(
        getTestResources(temp, XML_WITH_DIRECT_BAR_REFERENCE, XML_WITH_DIRECT_FOO_REFERENCE),
        allClasses,
        allXml);
  }

  public void test(
      AndroidTestResource androidTestResource,
      List<Class> presentClass,
      List<String> presentXmlFiles)
      throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(TestClass.class, Foo.class, Bar.class)
        .addAndroidResources(androidTestResource)
        .addKeepMainRule(TestClass.class)
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              for (String xml : allXml) {
                if (presentXmlFiles.contains(xml)) {
                  resourceTableInspector.assertContainsResourceWithName("xml", xml);
                } else {
                  resourceTableInspector.assertDoesNotContainResourceWithName("xml", xml);
                }
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            codeInspector -> {
              for (Class clazz : allClasses) {
                assertThat(codeInspector.clazz(clazz), isPresentIf(presentClass.contains(clazz)));
              }
            })
        .assertSuccess();
  }

  public static class TestClass {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        // Reference only the xml
        System.out.println(R.xml.xml_with_foo_reference);
      }
    }
  }
}
