// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.androidresources.AndroidResourceTestingUtils.TINY_PNG;

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
public class AndroidManifestWithTransitiveXmlReferenceTest extends TestBase {

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

  public static String MANIFEST_WITH_XML_REFERENCE =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    xmlns:tools=\"http://schemas.android.com/tools\""
          + "    package=\"com.android.tools.r8\""
          + ">\n"
          + "    <application\n"
          + "        android:label=\"@string/app_name\">\n"
          + "             <meta-data\n"
          + "                android:name=\"android.service.dream\"\n"
          + "                android:resource=\"@xml/xml_with_reference\" />\n"
          + "    </application>\n"
          + "</manifest>";

  public static String XML_WITH_XML_REFERENCE =
      "<dream xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "       android:previewImage=\"@drawable/image_with_ref_from_xml\"\n"
          + "    />\n";

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withManifest(MANIFEST_WITH_XML_REFERENCE)
        .addXml("xml_with_reference.xml", XML_WITH_XML_REFERENCE)
        .addDrawable("image_with_ref_from_xml.png", TINY_PNG)
        .addStringValue("app_name", "The one and only.")
        .build(temp);
  }

  @Test
  public void testManifestReferences() throws Exception {
    testForR8(parameters)
        .addAndroidResources(getTestResources(temp))
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "app_name");
              resourceTableInspector.assertContainsResourceWithName("xml", "xml_with_reference");
              resourceTableInspector.assertContainsResourceWithName(
                  "drawable", "image_with_ref_from_xml");
            });
  }
}
