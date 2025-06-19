// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ColorInliningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimize;

  @Parameter(2)
  public boolean addResourcesSubclass;

  @Parameter(3)
  public boolean aapt2BinaryRoundtrip;

  @Parameters(name = "{0}, optimize: {1}, addResourcesSubclass: {2}, aapt2BinaryRoundtrip: {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withAllApiLevels()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.color.class, R.xml.class)
        .addXmlWithColorReference("with_color.xml", "foo")
        .addExtraColorConfig("bar")
        .setOverlayableFor("color", "overlayable")
        .setAaptBinaryRoundtrip(aapt2BinaryRoundtrip)
        .build(temp);
  }

  private byte[] getResourcesSubclass() throws IOException {
    String resourceStubDescriptor = descriptor(Resources.class);
    return transformer(ResourcesSubclass.class)
        .setSuper(DexItemFactory.androidResourcesDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            resourceStubDescriptor, DexItemFactory.androidResourcesDescriptorString)
        .replaceClassDescriptorInMembers(
            resourceStubDescriptor, DexItemFactory.androidResourcesDescriptorString)
        .transform();
  }

  @Test
  public void testR8Optimized() throws Exception {
    assumeTrue(optimize || parameters.getPartialCompilationTestParameters().isNone());
    AndroidTestResource testResources = getTestResources(temp);
    testForR8(parameters)
        .addProgramClassFileData(
            AndroidResourceTestingUtils.transformResourcesReferences(FooBar.class))
        .addAndroidResources(testResources)
        .addKeepMainRule(FooBar.class)
        .applyIf(optimize, R8TestBuilder::enableOptimizedShrinking)
        .applyIf(
            addResourcesSubclass,
            builder ->
                builder
                    .addProgramClassFileData(getResourcesSubclass())
                    .addKeepMethodRules(
                        ResourcesSubclass.class, "getColor()", "resourceGetter(...)"))
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              if (optimize && !addResourcesSubclass) {
                if (!parameters.isRandomPartialCompilation()) {
                  resourceTableInspector.assertDoesNotContainResourceWithName("color", "foo");
                }
              } else {
                // When there are resource subclasses we should not inline, since this can have
                // side effects (or return different values).
                resourceTableInspector.assertContainsResourceWithName("color", "foo");
              }
              // Has multiple values, don't inline.
              resourceTableInspector.assertContainsResourceWithName("color", "bar");
              // Has overlayable value, don't inline.
              resourceTableInspector.assertContainsResourceWithName("color", "overlayable");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "color", "unused_color");
              }
            })
        .inspect(
            inspector -> {
              // We should have removed one of the calls to getColor if we are optimizing.
              MethodSubject mainMethodSubject = inspector.clazz(FooBar.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              if (!parameters.isRandomPartialCompilation()) {
                assertEquals(
                    optimize && !addResourcesSubclass ? 2 : 3,
                    getResourceCallsCount(mainMethodSubject));
              }
              if (addResourcesSubclass) {
                MethodSubject resourceGetterSubject =
                    inspector
                        .clazz(ResourcesSubclass.class)
                        .method("void", "resourceGetter", "android.content.res.Resources");
                assertThat(resourceGetterSubject, isPresent());
                // Assert that we never inline in this case as we are calling a subtype of
                // Resources.
                assertEquals(1, getResourceCallsCount(resourceGetterSubject));
              }
            })
        .run(parameters.getRuntime(), FooBar.class);
  }

  private static long getResourceCallsCount(MethodSubject methodSubject) {
    return methodSubject
        .streamInstructions()
        .filter(InstructionSubject::isInvokeVirtual)
        .filter(
            i ->
                i.getMethod()
                    .getHolderType()
                    .toDescriptorString()
                    .equals(DexItemFactory.androidResourcesDescriptorString))
        .count();
  }

  public static class FooBar {

    public static void main(String[] args) {
      Resources resources = new Resources();
      // Ensure that we correctly handle the out value propagation
      int c = resources.getColor(R.color.foo);
      int t = 1;
      int u = System.currentTimeMillis() > 0 ? c : t;
      System.out.println(u);
      System.out.println(resources.getColor(R.color.bar));
      System.out.println(resources.getColor(R.color.overlayable));
      System.out.println(R.xml.with_color);
    }
  }

  public static class ResourcesSubclass extends Resources {
    @Override
    public int getColor(int id) {
      System.out.println("Side effect");
      return super.getColor(id);
    }

    public static void resourceGetter(Resources res) {
      // Here we don't know if res is a subtype of Resources
      res.getColor(R.color.foo);
    }
  }

  public static class R {

    public static class xml {
      public static int with_color;
    }

    public static class color {
      public static int foo;
      public static int bar;
      public static int overlayable;
      public static int unused_color;
    }
  }
}
