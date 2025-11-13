// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

/** This test is a regression test for the issue identified in b/324270842#comment37 */
@RunWith(Parameterized.class)
public class RepeatedCompilationNestedSyntheticsAndStrippedMarkerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Backend intermediateBackend;

  @Parameterized.Parameters(name = "{0}, intermediate: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build(),
        Backend.values());
  }

  @Test
  public void test() throws Exception {
    Map<String, byte[]> firstCompilation = new HashMap<>();
    SyntheticItemsTestUtils firstSyntheticItems =
        testForD8(Backend.CF)
            // High API level such that only the lambda is desugared.
            .setMinApi(AndroidApiLevel.S)
            .setIntermediate(true)
            .addClasspathClasses(I.class)
            .addProgramClasses(UsesBackport.class)
            .addOptionsModification(o -> o.testing.disableSyntheticMarkerAttributeWriting = true)
            .collectSyntheticItems()
            .setProgramConsumer(
                new ClassFileConsumer() {
                  @Override
                  public void accept(
                      ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                    byte[] bytes = data.copyByteData();
                    assertEquals(
                        Collections.emptyList(), SyntheticMarkerCfTest.readAttributes(bytes));
                    firstCompilation.put(descriptor, bytes);
                  }

                  @Override
                  public void finished(DiagnosticsHandler handler) {}
                })
            .compile()
            .getSyntheticItems();

    ClassReference syntheticLambdaClass =
        firstSyntheticItems.syntheticLambdaClass(UsesBackport.class, 0);
    ImmutableSet<String> expectedClassOutputs =
        ImmutableSet.of(descriptor(UsesBackport.class), syntheticLambdaClass.getDescriptor());
    assertEquals(expectedClassOutputs, firstCompilation.keySet());

    Map<String, byte[]> secondCompilation = new HashMap<>();
    SyntheticItemsTestUtils secondSyntheticItems = null;
    ImmutableSet.Builder<String> allDescriptors = ImmutableSet.builder();
    BooleanBox matched = new BooleanBox(false);
    for (Entry<String, byte[]> entry : firstCompilation.entrySet()) {
      byte[] bytes = entry.getValue();
      Origin origin =
          new Origin(Origin.root()) {
            @Override
            public String part() {
              return entry.getKey();
            }
          };
      D8TestCompileResult secondCompileResult =
          testForD8(intermediateBackend)
              .collectSyntheticItems()
              .setMinApi(parameters)
              .setIntermediate(true)
              .addClasspathClasses(I.class)
              .apply(b -> b.getBuilder().addClassProgramData(bytes, origin))
              .apply(
                  b ->
                      b.getBuilder()
                          .setDesugarGraphConsumer(
                              new DesugarGraphConsumer() {

                                @Override
                                public void accept(Origin dependent, Origin dependency) {
                                  assertThat(
                                      dependency.toString(), containsString(binaryName(I.class)));
                                  assertThat(
                                      dependent.toString(),
                                      containsString(syntheticLambdaClass.getBinaryName()));
                                  matched.set(true);
                                }

                                @Override
                                public void finished() {}
                              }))
              .applyIf(
                  intermediateBackend == Backend.CF,
                  b ->
                      b.setProgramConsumer(
                          new ClassFileConsumer() {
                            @Override
                            public void accept(
                                ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                              secondCompilation.put(descriptor, data.copyByteData());
                              allDescriptors.add(descriptor);
                            }

                            @Override
                            public void finished(DiagnosticsHandler handler) {}
                          }),
                  b ->
                      b.setProgramConsumer(
                          new DexFilePerClassFileConsumer() {

                            @Override
                            public void accept(
                                String primaryClassDescriptor,
                                ByteDataView data,
                                Set<String> descriptors,
                                DiagnosticsHandler handler) {
                              secondCompilation.put(primaryClassDescriptor, data.copyByteData());
                              allDescriptors.addAll(descriptors);
                            }

                            @Override
                            public void finished(DiagnosticsHandler handler) {}
                          }))
              .compile();
      if (entry.getKey().equals(syntheticLambdaClass.getDescriptor())) {
        secondSyntheticItems = secondCompileResult.getSyntheticItems();
      }
    }
    assertTrue(matched.get());
    // The dex file per class file output should maintain the exact same set of primary descriptors.
    if (intermediateBackend == Backend.DEX) {
      assertEquals(expectedClassOutputs, secondCompilation.keySet());
    }
    // The total set of classes should also include the backport. The backport should be
    // hygienically placed under the synthetic lambda (not the context of the lambda!).
    ClassReference syntheticBackportClass =
        secondSyntheticItems.syntheticBackportClass(syntheticLambdaClass, 0);
    assertEquals(syntheticLambdaClass.getTypeName() + "$0", syntheticBackportClass.getTypeName());
    assertEquals(
        ImmutableSet.<String>builder()
            .addAll(expectedClassOutputs)
            .add(syntheticBackportClass.getDescriptor())
            .build(),
        allDescriptors.build());

    List<byte[]> secondCompilationWitoutOuterContext =
        secondCompilation.entrySet().stream()
            .filter(e -> !e.getKey().equals(descriptor(UsesBackport.class)))
            .map(Entry::getValue)
            .collect(Collectors.toList());
    D8TestCompileResult thirdNonIntermediateCompileResult =
        testForD8(Backend.DEX)
            .setMinApi(parameters)
            .addProgramClasses(I.class, TestClass.class)
            .applyIf(
                intermediateBackend == Backend.CF,
                b -> b.addProgramClassFileData(secondCompilationWitoutOuterContext),
                b -> b.addProgramDexFileData(secondCompilationWitoutOuterContext))
            .collectSyntheticItems()
            .compile();

    byte[] secondCompilationOfOuterContext = secondCompilation.get(descriptor(UsesBackport.class));
    testForD8(Backend.DEX)
        .setMinApi(parameters)
        .addProgramFiles(thirdNonIntermediateCompileResult.writeToZip())
        .applyIf(
            intermediateBackend == Backend.CF,
            b -> b.addProgramClassFileData(secondCompilationOfOuterContext),
            b -> b.addProgramDexFileData(secondCompilationOfOuterContext))
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("1")
        .inspect(
            inspector -> {
              Set<String> descriptors =
                  inspector.allClasses().stream()
                      .map(c -> c.getFinalReference().getDescriptor())
                      .collect(Collectors.toSet());
              // The initial lambda stays as the only item under UsesBackport.
              ClassReference lambdaClass =
                  firstSyntheticItems.syntheticLambdaClass(UsesBackport.class, 0);
              // The nested backport has context in the lambda since the lambda was not marked.
              ClassReference backportClass =
                  thirdNonIntermediateCompileResult
                      .getSyntheticItems()
                      .syntheticBackportClass(lambdaClass, 0);
              assertEquals(
                  ImmutableSet.of(
                      descriptor(I.class),
                      descriptor(TestClass.class),
                      descriptor(UsesBackport.class),
                      backportClass.getDescriptor(),
                      lambdaClass.getDescriptor()),
                  descriptors);
            });
  }

  interface I {
    int compare(boolean b1, boolean b2);
  }

  static class UsesBackport {
    public static I foo() {
      return Boolean::compare;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(UsesBackport.foo().compare(true, false));
    }
  }
}
