// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
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
public class XmlFilesWithClassReferences extends TestBase {

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

  public static String TRANSITION_WITH_CLASS_ATTRIBUTE =
      "<transitionSet>\n" + "  <transition class=\"%s\"/>\n" + "</transitionSet>\n";

  public static String CHANGE_BOUNDS_WITH_CLASS_ATTRIBUTE =
      "<changeBounds>\n" + "  <pathMotion class=\"%s\"/>\n" + "</changeBounds>";

  public static String FRAGMENT_CONTAINER_VIEW_WITH_CLASS_ATTRIBUTE =
      "<androidx.fragment.app.FragmentContainerView class=\"%s\"/>";

  public static String FRAGMENT_WITH_CLASS_ATTRIBUTE = "<fragment class=\"%s\"/>";

  public static AndroidTestResource getTestResources(TemporaryFolder temp, String xmlFile)
      throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.xml.class)
        .addXml("xml_with_bar_reference.xml", xmlFile)
        .build(temp);
  }

  @Test
  public void testFragmentContainerWithClassAttributeReference() throws Exception {
    testXmlReferenceWithBarClassInserted(FRAGMENT_CONTAINER_VIEW_WITH_CLASS_ATTRIBUTE, false);
  }

  @Test
  public void testViewWithClassAttributeReference() throws Exception {
    testXmlReferenceWithBarClassInserted(VIEW_WITH_CLASS_ATTRIBUTE_REFERENCE, false);
  }

  @Test
  public void testTransitionWithClassAttributeReference() throws Exception {
    testXmlReferenceWithBarClassInserted(TRANSITION_WITH_CLASS_ATTRIBUTE, false);
  }

  @Test
  public void testChangeBoundsWithClassAttributeReference() throws Exception {
    testXmlReferenceWithBarClassInserted(CHANGE_BOUNDS_WITH_CLASS_ATTRIBUTE, false);
  }

  @Test
  public void testFragmentWithClassAttributeReference() throws Exception {
    testXmlReferenceWithBarClassInserted(FRAGMENT_WITH_CLASS_ATTRIBUTE, false);
  }

  public void testXmlReferenceWithBarClassInserted(String xmlFile, boolean assertFoo)
      throws Exception {
    String formatedXmlFile = String.format(xmlFile, Bar.class.getTypeName());
    testForR8(parameters)
        .addProgramClasses(TestClass.class, Bar.class, BarFoo.class)
        .addAndroidResources(getTestResources(temp, formatedXmlFile))
        .addKeepMainRule(TestClass.class)
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName(
                  "xml", "xml_with_bar_reference");
            })
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            codeInspector -> {
              ClassSubject barClass = codeInspector.clazz(Bar.class);
              assertThat(barClass, isPresentAndNotRenamed());
              // We should have two and only two methods, the two constructors.
              assertEquals(barClass.allMethods(MethodSubject::isInstanceInitializer).size(), 2);
              if (!parameters.isRandomPartialCompilation()) {
                assertEquals(barClass.allMethods().size(), 2);
                assertThat(codeInspector.clazz(BarFoo.class), assertFoo ? isPresent() : isAbsent());
              }
            })
        .assertSuccess();
  }

  public static class TestClass {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        // Reference only the xml
        System.out.println(R.xml.xml_with_bar_reference);
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

  public static class BarFoo {}

  public static class R {
    public static class xml {
      public static int xml_with_bar_reference;
      public static int xml_with_foo_reference;
    }
  }
}
