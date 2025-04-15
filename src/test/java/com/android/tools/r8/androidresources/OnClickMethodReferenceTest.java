// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import android.view.View;
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
public class OnClickMethodReferenceTest extends TestBase {

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

  public static String XML_WITH_ONCLICK =
      "<"
          + Bar.class.getTypeName()
          + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "  android:onClick=\"theMagicOnClick\""
          + "/>";

  public static String XML_WITH_NESTED_CLASS_AND_ONCLICK =
      "<"
          + Bar.class.getTypeName()
          + "  xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
          + "  <"
          + Foo.class.getTypeName()
          + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    android:onClick=\"theMagicOnClick\""
          + "  />"
          + "</"
          + Bar.class.getTypeName()
          + ">";

  public static AndroidTestResource getTestResources(TemporaryFolder temp, String xml)
      throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.xml.class)
        .addXml("xml_with_onclick.xml", xml)
        .build(temp);
  }

  @Test
  public void testSingleClassWithOnclick() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class, Bar.class, Foo.class)
        .addAndroidResources(getTestResources(temp, XML_WITH_ONCLICK))
        .addKeepMainRule(TestClass.class)
        .enableOptimizedShrinking()
        .compile()
        .inspect(
            codeInspector -> {
              ClassSubject barClass = codeInspector.clazz(Bar.class);
              assertThat(barClass, isPresent());
              assertThat(
                  barClass.uniqueMethodWithOriginalName("theMagicOnClick"),
                  isPresentAndNotRenamed());

              if (!parameters.isRandomPartialCompilation()) {
                ClassSubject fooClass = codeInspector.clazz(Foo.class);
                assertThat(fooClass, isAbsent());
              }
            });
  }

  @Test
  public void testNestedClassWithOnclick() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class, Bar.class, Foo.class)
        .addAndroidResources(getTestResources(temp, XML_WITH_NESTED_CLASS_AND_ONCLICK))
        .addKeepMainRule(TestClass.class)
        .enableOptimizedShrinking()
        .compile()
        .inspect(
            codeInspector -> {
              ClassSubject barClass = codeInspector.clazz(Bar.class);
              assertThat(barClass, isPresent());
              assertThat(
                  barClass.uniqueMethodWithOriginalName("theMagicOnClick"),
                  isPresentAndNotRenamed());
              ClassSubject fooClass = codeInspector.clazz(Foo.class);
              assertThat(fooClass, isPresentAndNotRenamed());
              assertThat(
                  fooClass.uniqueMethodWithOriginalName("theMagicOnClick"),
                  isPresentAndNotRenamed());
            });
  }

  public static class TestClass {
    public static void main(String[] args) {
      // Reference only the xml
      System.out.println(R.xml.xml_with_onclick);
    }
  }

  public static class R {
    public static class xml {
      public static int xml_with_onclick;
    }
  }
}

// Element names can't include $, so don't have these as an inner class.
class Bar {
  public void theMagicOnClick(View view) {
    System.out.println("clicked the magic button");
  }
}

class Foo {
  public void theMagicOnClick(View view) {
    System.out.println("clicked the magic button");
  }
}
