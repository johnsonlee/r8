// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static org.junit.Assume.assumeFalse;

import androidx.annotation.keep.UsesReflectionToConstruct;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.AnnotationBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer.AnnotationContentBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class KeepUsesReflectionForInstantiationAnyArgsConstructorTest
    extends KeepAnnoTestExtractedRulesBase {

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    // Test with Android 14, which has `java.lang.ClassValue` to avoid having to deal with R8
    // missing class warnings for tests using the kotlin-reflect library.
    return buildParameters(
        createParameters(
            getTestParameters()
                .withDexRuntime(Version.V14_0_0)
                .withDefaultCfRuntime()
                .withMaximumApiLevel()
                .build()),
        getKotlinTestParameters().withLatestCompiler().build());
  }

  @Override
  protected String getExpectedOutputForJava() {
    return StringUtils.lines("4");
  }

  @Override
  protected String getExpectedOutputForKotlin() {
    return StringUtils.lines(
        "fun `<init>`(): com.android.tools.r8.keepanno.androidx.kt.KeptClass",
        "In KeptClass.<init>()",
        "4");
  }

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(
          KeepUsesReflectionForInstantiationAnyArgsConstructorTest.class,
          "kt",
          "AnyArgsConstructor.kt",
          "KeptClass.kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    compilationResults = getCompileMemoizerWithKeepAnnoLib(getKotlinSources());
  }

  private static ExpectedRules getExpectedRulesJava(Class<?> conditionClass) {
    return getExpectedRulesJava(conditionClass, null);
  }

  private static ExpectedRules getExpectedRulesJava(
      Class<?> conditionClass, boolean includeSubclasses, String conditionMembers) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b -> b.setConditionClass(conditionClass).setConditionMembers(conditionMembers);
    ExpectedRules.Builder builder =
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .apply(setCondition)
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void <init>(...); }")
                    .build());
    if (includeSubclasses) {
      builder.add(
          ExpectedKeepRule.builder()
              .apply(setCondition)
              .setKeepVariant("-keepclasseswithmembers")
              .setConsequentExtendsClass(KeptClass.class)
              .setConsequentMembers("{ void <init>(...); }")
              .build());
    }
    return builder.build();
  }

  private static ExpectedRules getExpectedRulesJava(
      Class<?> conditionClass, String conditionMembers) {
    return getExpectedRulesJava(conditionClass, false, conditionMembers);
  }

  private static ExpectedRules getExpectedRulesKotlin(String conditionClass) {
    return getExpectedRulesKotlin(conditionClass, null);
  }

  private static ExpectedRules getExpectedRulesKotlin(
      String conditionClass, String conditionMembers) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b -> b.setConditionClass(conditionClass).setConditionMembers(conditionMembers);
    ExpectedRules.Builder builder =
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .apply(setCondition)
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.KeptClass")
                    .setConsequentMembers("{ void <init>(...); }")
                    .build());
    return builder.build();
  }

  private static void buildAnyConstructor(AnnotationBuilder builder, Object clazz) {
    AnnotationContentBuilder b =
        builder.setAnnotationClass(Reference.classFromClass(UsesReflectionToConstruct.class));
    if (clazz instanceof String) {
      b.setField("className", clazz);
    } else {
      assert clazz instanceof Class<?> || clazz instanceof Type;
      b.setField("classConstant", clazz);
    }
    // No parameterTypes or parameterTypeNames means any constructor.
  }

  @Test
  public void testAnyConstructor() throws Exception {
    testExtractedRulesAndRunJava(
        AnyConstructor.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                AnyConstructor.class,
                MethodPredicate.onName("foo"),
                builder -> buildAnyConstructor(builder, KeptClass.class))),
        getExpectedRulesJava(AnyConstructor.class, "{ void foo(java.lang.Class); }"));
  }

  @Test
  public void testAnyConstructorAnnotateClass() throws Exception {
    testExtractedRulesAndRunJava(
        AnyConstructor.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnClass(
                AnyConstructor.class,
                builder -> buildAnyConstructor(builder, KeptClass.class))),
        getExpectedRulesJava(AnyConstructor.class));
  }

  @Test
  public void testIncludeSubclasses() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                AnyConstructor.class,
                MethodPredicate.onName("foo"),
                builder ->
                    builder
                        .setAnnotationClass(
                            Reference.classFromClass(UsesReflectionToConstruct.class))
                        .setField("classConstant", KeptClass.class)
                        .setField("includeSubclasses", true))),
        getExpectedRulesJava(AnyConstructor.class, true, "{ void foo(java.lang.Class); }"));
  }

  @Test
  public void testAnyConstructorKotlin() throws Exception {
    // TODO(b/323816623): With native interpretation kotlin.Metadata still gets stripped
    assumeFalse(parameters.isNativeR8());
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName(
                    "com.android.tools.r8.keepanno.androidx.kt.AnyArgsConstructor"),
                MethodPredicate.onName("foo"),
                builder ->
                    buildAnyConstructor(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.KeptClass")))),
        "com.android.tools.r8.keepanno.androidx.kt.AnyArgsConstructorKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.AnyArgsConstructor",
            "{ void foo(kotlin.reflect.KClass); }"));
  }

  @Test
  public void testAnyConstructorKotlinAnnotateClass() throws Exception {
    // TODO(b/323816623): With native interpretation kotlin.Metadata still gets stripped
    assumeFalse(parameters.isNativeR8());
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnClass(
                classReference,
                classFileBytes,
                Reference.classFromTypeName(
                    "com.android.tools.r8.keepanno.androidx.kt.AnyArgsConstructor"),
                builder ->
                    buildAnyConstructor(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.KeptClass")))),
        "com.android.tools.r8.keepanno.androidx.kt.AnyArgsConstructorKt",
        getExpectedRulesKotlin("com.android.tools.r8.keepanno.androidx.kt.AnyArgsConstructor"),
        // TODO(b/437277192): Constructors should be kept.
        parameters.isExtractRules()
            ? StringUtils.lines("null", "0")
            : getExpectedOutputForKotlin());
  }

  // Test class without annotation to be used by multiple tests inserting annotations using a
  // transformer.
  static class AnyConstructor {

    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        System.out.println(clazz.getDeclaredConstructors().length);
      }
    }

    public static void main(String[] args) throws Exception {
      new AnyConstructor().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  static class KeptClass {
    KeptClass() {}

    KeptClass(int i) {}

    KeptClass(long j) {}

    KeptClass(String s1, String s2, String s3) {}
  }
}
