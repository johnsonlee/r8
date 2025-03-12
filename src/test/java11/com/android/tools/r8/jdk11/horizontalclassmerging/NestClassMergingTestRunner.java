// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.horizontalclassmerging;

import static com.android.tools.r8.jdk11.horizontalclassmerging.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestClassMergingTest;
import static com.android.tools.r8.jdk11.horizontalclassmerging.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostA;
import static com.android.tools.r8.jdk11.horizontalclassmerging.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostA$NestMemberA;
import static com.android.tools.r8.jdk11.horizontalclassmerging.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostA$NestMemberB;
import static com.android.tools.r8.jdk11.horizontalclassmerging.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostB;
import static com.android.tools.r8.jdk11.horizontalclassmerging.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostB$NestMemberA;
import static com.android.tools.r8.jdk11.horizontalclassmerging.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostB$NestMemberB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class NestClassMergingTestRunner extends HorizontalClassMergingTestBase {

  public static class HorizontalClassMergingTestSources {

    public static final Class<?> nestClassMergingTest = NestClassMergingTest.class;
    public static final Class<?> nestHostA = NestHostA.class;
    public static final Class<?> nestHostA$NestMemberA = NestHostA.NestMemberA.class;
    public static final Class<?> nestHostA$NestMemberB = NestHostA.NestMemberB.class;
    public static final Class<?> nestHostB = NestHostB.class;
    public static final Class<?> nestHostB$NestMemberA = NestHostB.NestMemberA.class;
    public static final Class<?> nestHostB$NestMemberB = NestHostB.NestMemberB.class;
  }

  public NestClassMergingTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  @Test
  public void test() throws Exception {
    runTest(
        builder ->
            builder
                .addDontObfuscate()
                .addHorizontallyMergedClassesInspector(
                    inspector -> {
                      if (parameters.canUseNestBasedAccesses()) {
                        inspector
                            .assertIsCompleteMergeGroup(
                                nestHostA, nestHostA$NestMemberA, nestHostA$NestMemberB)
                            .assertIsCompleteMergeGroup(
                                nestHostB, nestHostB$NestMemberA, nestHostB$NestMemberB);
                      } else {
                        inspector.assertIsCompleteMergeGroup(
                            nestHostA,
                            nestHostA$NestMemberA,
                            nestHostA$NestMemberB,
                            nestHostB,
                            nestHostB$NestMemberA,
                            nestHostB$NestMemberB);
                      }
                    }));
  }

  @Test
  public void testMergeHostIntoNestMemberA() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(
        builder ->
            builder
                .addHorizontallyMergedClassesInspector(
                    inspector ->
                        inspector
                            .assertIsCompleteMergeGroup(nestHostA, nestHostA$NestMemberA)
                            .assertIsCompleteMergeGroup(nestHostB, nestHostB$NestMemberA)
                            .assertClassReferencesNotMerged(
                                Reference.classFromClass(nestHostA$NestMemberB),
                                Reference.classFromClass(nestHostB$NestMemberB)))
                .addNoHorizontalClassMergingRule(
                    nestHostA$NestMemberB.getTypeName(), nestHostB$NestMemberB.getTypeName())
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(
                                Reference.classFromClass(nestHostA))) {
                              assertEquals(
                                  ImmutableSet.of(
                                      Reference.classFromClass(nestHostA),
                                      Reference.classFromClass(nestHostA$NestMemberA)),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      Reference.classFromClass(nestHostB),
                                      Reference.classFromClass(nestHostB$NestMemberA)),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(
                                          Reference.classFromClass(nestHostA$NestMemberA))
                                      || classReference.equals(
                                          Reference.classFromClass(nestHostB$NestMemberA));
                                });
                          };
                    }));
  }

  @Test
  public void testMergeHostIntoNestMemberB() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(
        builder ->
            builder
                .addHorizontallyMergedClassesInspector(
                    inspector ->
                        inspector
                            .assertIsCompleteMergeGroup(nestHostA, nestHostA$NestMemberB)
                            .assertIsCompleteMergeGroup(nestHostB, nestHostB$NestMemberB)
                            .assertClassReferencesNotMerged(
                                Reference.classFromClass(nestHostA$NestMemberA),
                                Reference.classFromClass(nestHostB$NestMemberA)))
                .addNoHorizontalClassMergingRule(
                    nestHostA$NestMemberA.getTypeName(), nestHostB$NestMemberA.getTypeName())
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(
                                Reference.classFromClass(nestHostA))) {
                              assertEquals(
                                  ImmutableSet.of(
                                      Reference.classFromClass(nestHostA),
                                      Reference.classFromClass(nestHostA$NestMemberB)),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      Reference.classFromClass(nestHostB),
                                      Reference.classFromClass(nestHostB$NestMemberB)),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(
                                          Reference.classFromClass(nestHostA$NestMemberB))
                                      || classReference.equals(
                                          Reference.classFromClass(nestHostB$NestMemberB));
                                });
                          };
                    }));
  }

  @Test
  public void testMergeMemberAIntoNestHost() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(
        builder ->
            builder
                .addHorizontallyMergedClassesInspector(
                    inspector ->
                        inspector
                            .assertIsCompleteMergeGroup(nestHostA, nestHostA$NestMemberA)
                            .assertIsCompleteMergeGroup(nestHostB, nestHostB$NestMemberA)
                            .assertClassReferencesNotMerged(
                                Reference.classFromClass(nestHostA$NestMemberB),
                                Reference.classFromClass(nestHostB$NestMemberB)))
                .addNoHorizontalClassMergingRule(
                    nestHostA$NestMemberB.getTypeName(), nestHostB$NestMemberB.getTypeName())
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(
                                Reference.classFromClass(nestHostA))) {
                              assertEquals(
                                  ImmutableSet.of(
                                      Reference.classFromClass(nestHostA),
                                      Reference.classFromClass(nestHostA$NestMemberA)),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      Reference.classFromClass(nestHostB),
                                      Reference.classFromClass(nestHostB$NestMemberA)),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(Reference.classFromClass(nestHostA))
                                      || classReference.equals(Reference.classFromClass(nestHostB));
                                });
                          };
                    }));
  }

  private void runTest(ThrowableConsumer<R8FullTestBuilder> configuration) throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(nestClassMergingTest)
        .addProgramClassesAndInnerClasses(
            NestClassMergingTest.class, NestHostB.class, NestHostA.class)
        .addKeepRules(
            "-keeppackagenames com.android.tools.r8.jdk11.horizontalclassmerging",
            "-nomethodstaticizing class * { void privatePrint(...); }")
        .apply(configuration)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), nestClassMergingTest.getTypeName())
        .assertSuccessWithOutputLines(
            "NestHostA",
            "NestHostA$NestMemberA",
            "NestHostA$NestMemberB",
            "NestHostB",
            "NestHostB$NestMemberA",
            "NestHostB$NestMemberB");
  }
}
