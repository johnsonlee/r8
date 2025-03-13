// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_5_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_6_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_9_21;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_2_0_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_2_1_10;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexNewInstance;
import com.android.tools.r8.dex.code.DexSgetObject;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinClassInlinerTest extends AbstractR8KotlinTestBase {

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersLambdaGenerationsAndTargetVersions().build());
  }

  public KotlinClassInlinerTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(parameters, kotlinParameters, true);
  }

  @Test
  public void testJStyleLambdas() throws Exception {
    // SAM interfaces lambdas are implemented by invoke dynamic in kotlin 1.5 unlike 1.4 where a
    // class is generated for each. In CF we leave invokeDynamic but for DEX we desugar the classes
    // and merge them.
    boolean hasKotlinCGeneratedLambdaClasses = kotlinParameters.isOlderThan(KOTLINC_1_5_0);
    String mainClassName = "class_inliner_lambda_j_style.MainKt";
    runTest(
            "class_inliner_lambda_j_style",
            mainClassName,
            testBuilder ->
                testBuilder
                    // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
                    .addKeepRules("-neverinline class * { void test*State*(...); }")
                    .addNoHorizontalClassMergingRule(
                        "class_inliner_lambda_j_style.SamIface$Consumer")
                    .addHorizontallyMergedClassesInspector(
                        inspector -> {
                          if (hasKotlinCGeneratedLambdaClasses) {
                            inspector.assertIsCompleteMergeGroup(
                                "class_inliner_lambda_j_style.MainKt$testStateful$1",
                                "class_inliner_lambda_j_style.MainKt$testStateful$2",
                                "class_inliner_lambda_j_style.MainKt$testStateful$2$1",
                                "class_inliner_lambda_j_style.MainKt$testStateful$3",
                                "class_inliner_lambda_j_style.MainKt$testStateful2$1",
                                "class_inliner_lambda_j_style.MainKt$testStateful3$1");
                          } else if (testParameters.isDexRuntime()) {
                            Set<Set<DexType>> mergeGroups = inspector.getMergeGroups();
                            assertEquals(1, mergeGroups.size());
                            inspector.assertIsCompleteMergeGroup(
                                "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda0",
                                "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda2",
                                "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda4",
                                "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda3",
                                "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda5",
                                "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda6");
                          }
                          inspector.assertNoOtherClassesMerged();
                        })
                    .noClassInlining())
        .inspect(
            inspector -> {
              if (testParameters.isCfRuntime() && !hasKotlinCGeneratedLambdaClasses) {
                assertEquals(5, inspector.allClasses().size());
              } else if (!hasKotlinCGeneratedLambdaClasses) {
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda1"),
                    isPresent());
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda2"),
                    isAbsent());
              } else {
                assertThat(
                    inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateless$1"),
                    isAbsent());
                assertThat(
                    inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful$1"),
                    isPresent());
              }
            });

    runTest(
            "class_inliner_lambda_j_style",
            mainClassName,
            testBuilder ->
                testBuilder
                    // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
                    .addKeepRules("-neverinline class * { void test*State*(...); }")
                    .addNoHorizontalClassMergingRule(
                        "class_inliner_lambda_j_style.SamIface$Consumer")
                    .addNoVerticalClassMergingRule("class_inliner_lambda_j_style.SamIface"))
        .inspect(
            inspector -> {
              if (testParameters.isCfRuntime() && !hasKotlinCGeneratedLambdaClasses) {
                assertEquals(5, inspector.allClasses().size());
                return;
              }
              if (!hasKotlinCGeneratedLambdaClasses) {
                // Kotlin 1.6.20 and later do not create intrinsics.stringPlus for two argument
                // string concatination. That allow R8's stringbuilder optimization to reduce the
                // size of strings and therefore inline the synthetic lambda.
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda1"),
                    isAbsentIf(kotlinParameters.isNewerThan(KOTLINC_1_6_0)));
              } else {
                assertThat(
                    inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateless$1"),
                    isAbsent());
              }

              if (hasKotlinCGeneratedLambdaClasses) {
                assertThat(
                    testParameters.isCfRuntime()
                        ? inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful2$1")
                        : inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful$2"),
                    isPresent());
              } else {
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda2"),
                    isAbsent());
              }
            });
  }

  @Test
  public void testKStyleLambdas() throws Exception {
    String mainClassName = "class_inliner_lambda_k_style.MainKt";
    runTest(
            "class_inliner_lambda_k_style",
            mainClassName,
            testBuilder ->
                testBuilder
                    // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
                    .addKeepRules(
                        "-neverinline class * { void test*State*(...); }",
                        "-neverinline class * { void testBigExtraMethod(...); }",
                        "-neverinline class * { void testBigExtraMethodReturningLambda(...); }")
                    .addHorizontallyMergedClassesInspector(
                        inspector -> {
                          if (kotlinParameters.getLambdaGeneration().isClass()) {
                            inspector.assertIsCompleteMergeGroup(
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethod2$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethod3$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda2$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda3$1",
                                "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateless$1");
                          } else if (testParameters.isDexRuntime()) {
                            ClassReference mainKt =
                                Reference.classFromTypeName("class_inliner_lambda_k_style.MainKt");
                            inspector
                                .assertIsCompleteMergeGroup(
                                    SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 2),
                                    SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 3),
                                    SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 4),
                                    SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 5),
                                    SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 6),
                                    SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 7),
                                    SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 8))
                                .assertIsCompleteMergeGroup(
                                    Reference.classFromTypeName("kotlin.jvm.functions.Function0"),
                                    Reference.classFromTypeName("kotlin.jvm.functions.Function1"))
                                .assertIsCompleteMergeGroup(
                                    Reference.classFromTypeName(
                                        "kotlin.sequences.TransformingSequence"),
                                    Reference.classFromTypeName(
                                        "kotlin.sequences.GeneratorSequence"))
                                .applyIf(
                                    kotlinParameters
                                        .getCompiler()
                                        .getCompilerVersion()
                                        .isBetweenBothIncluded(KOTLINC_1_9_21, KOTLINC_2_0_20),
                                    i ->
                                        i.assertIsCompleteMergeGroup(
                                            SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 0),
                                            SyntheticItemsTestUtils.syntheticLambdaClass(
                                                mainKt, 1)),
                                    kotlinParameters
                                        .getCompiler()
                                        .getCompilerVersion()
                                        .isGreaterThanOrEqualTo(KOTLINC_2_1_10),
                                    i -> {
                                      ClassReference sequencesKt =
                                          Reference.classFromTypeName(
                                              "kotlin.sequences.SequencesKt__SequencesKt");
                                      i.assertIsCompleteMergeGroup(
                                          SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 0),
                                          SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 1),
                                          SyntheticItemsTestUtils.syntheticLambdaClass(
                                              sequencesKt, 0));
                                    })
                                .assertNoOtherClassesMerged();
                          } else {
                            assert kotlinParameters.getLambdaGeneration().isInvokeDynamic()
                                && testParameters.isCfRuntime();
                            inspector
                                .assertIsCompleteMergeGroup(
                                    Reference.classFromTypeName("kotlin.jvm.functions.Function0"),
                                    Reference.classFromTypeName("kotlin.jvm.functions.Function1"))
                                .assertIsCompleteMergeGroup(
                                    Reference.classFromTypeName(
                                        "kotlin.sequences.TransformingSequence"),
                                    Reference.classFromTypeName(
                                        "kotlin.sequences.GeneratorSequence"))
                                .assertNoOtherClassesMerged();
                          }
                        })
                    .noClassInlining())
        .inspect(
            inspector -> {
              if (kotlinParameters.getLambdaGeneration().isClass()) {
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateful$1"),
                    isAbsent());
                assertThat(
                    inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1"),
                    isPresent());
              }
            });

    runTest(
            "class_inliner_lambda_k_style",
            mainClassName,
            testBuilder ->
                testBuilder
                    // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
                    .addKeepRules(
                    "-neverinline class * { void test*State*(...); }",
                    "-neverinline class * { void testBigExtraMethod(...); }",
                    "-neverinline class * { void testBigExtraMethodReturningLambda(...); }"))
        .inspect(
            inspector -> {
              if (kotlinParameters.getLambdaGeneration().isClass()) {
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateless$1"),
                    isPresentIf(testParameters.isDexRuntime()));
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateful$1"),
                    isAbsent());
                assertThat(
                    inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1"),
                    isPresentIf(testParameters.isCfRuntime()));
              }
            });
  }

  @Test
  public void testDataClass() throws Exception {
    String mainClassName = "class_inliner_data_class.MainKt";
    runTest(
            "class_inliner_data_class",
            mainClassName,
            testBuilder ->
                testBuilder.addKeepRules(
                    "-neverinline class kotlin.jvm.internal.Intrinsics {",
                    "  *** checkNotNullParameter(...);",
                    "  *** checkParameterIsNotNull(...);",
                    "}"))
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(mainClassName);
              assertEquals(
                  Collections.emptySet(),
                  collectAccessedTypes(
                      type -> !type.toSourceString().startsWith("java."),
                      clazz,
                      "main",
                      String[].class.getCanonicalName()));
              String kotlinIntrinsics = "void kotlin.jvm.internal.Intrinsics";
              assertEquals(
                  Lists.newArrayList(
                      kotlinIntrinsics
                          + (kotlinc.is(KOTLINC_1_3_72)
                              ? ".checkParameterIsNotNull"
                              : ".checkNotNullParameter")
                          + "(java.lang.Object, java.lang.String)"),
                  collectStaticCalls(clazz, "main", String[].class.getCanonicalName()));
            });
  }

  private Set<String> collectAccessedTypes(
      Predicate<DexType> isTypeOfInterest,
      ClassSubject clazz,
      String methodName,
      String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, "void", params);
    return clazz
        .method(signature)
        .streamInstructions()
        .filter(instruction -> instruction.isNewInstance() || instruction.isStaticGet())
        .map(
            instruction -> {
              if (instruction.isCfInstruction()) {
                CfInstruction baseInstruction = instruction.asCfInstruction().getInstruction();
                if (baseInstruction instanceof CfNew) {
                  return ((CfNew) baseInstruction).getType();
                } else if (instruction.getField().getType().isReferenceType()) {
                  return instruction.getField().getHolderType();
                }
              } else {
                DexInstruction baseInstruction = instruction.asDexInstruction().getInstruction();
                if (baseInstruction instanceof DexSgetObject) {
                  return baseInstruction.getField().getHolderType();
                } else if (baseInstruction instanceof DexNewInstance) {
                  return ((DexNewInstance) baseInstruction).getType();
                }
              }
              return null;
            })
        .filter(type -> type != null && isTypeOfInterest.test(type))
        .map(DexType::toSourceString)
        .collect(Collectors.toSet());
  }

  private List<String> collectStaticCalls(ClassSubject clazz, String methodName, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, "void", params);
    MethodSubject method = clazz.method(signature);
    return Streams.stream(method.iterateInstructions(InstructionSubject::isInvokeStatic))
        .map(insn -> insn.getMethod().toSourceString())
        .sorted()
        .collect(Collectors.toList());
  }
}
