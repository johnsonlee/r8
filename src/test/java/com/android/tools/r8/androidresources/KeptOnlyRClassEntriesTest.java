// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.TestResourceTable;
import com.android.tools.r8.transformers.ClassTransformer;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class KeptOnlyRClassEntriesTest extends TestBase {

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

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.drawable.class)
        .build(temp);
  }

  @Test
  public void testKeptField() throws Exception {
    AndroidTestResource testResources = getTestResources(temp);
    TestResourceTable testResourceTable = testResources.getTestResourceTable();
    testResources
        .getRClass()
        .transformClassFileData(
            new ClassTransformer() {
              @Override
              public FieldVisitor visitField(
                  int access, String name, String descriptor, String signature, Object value) {
                if (name.equals("kept_field")) {
                  value = Integer.valueOf(testResourceTable.idFor("drawable", "kept_field"));
                }
                return super.visitField(access, name, descriptor, signature, value);
              }

              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if (name.equals("<clinit>")) {
                  // We are explicitly setting the value in the field visitor above
                  return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
              }
            },
            s -> s.contains("drawable"));
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addAndroidResources(testResources)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep class **R*drawable { int kept_field;}")
        .enableOptimizedShrinking()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("drawable", "kept_field");
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  public static class TestClass {
    public static void main(String[] args) {}
  }

  public static class R {

    public static class drawable {
      public static int kept_field = 0x7f010001;
    }
  }
}
