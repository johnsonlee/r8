// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeFalse;

import androidx.annotation.keep.UsesReflectionToAccessField;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class KeepUsesReflectionToAccessFieldTest extends KeepAnnoTestExtractedRulesBase {

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

  private static Collection<Path> getKotlinSources() {
    try {
      return Stream.concat(
              getFilesInTestFolderRelativeToClass(
                  KeepUsesReflectionToAccessFieldTest.class, "kt", "Fields.kt")
                  .stream(),
              getFilesInTestFolderRelativeToClass(
                  KeepUsesReflectionToAccessFieldTest.class, "kt", "FieldsPropertyAccess.kt")
                  .stream())
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    compilationResults = getCompileMemoizerWithKeepAnnoLib(getKotlinSources());
  }

  private static ExpectedRules getExpectedRulesJava(
      Class<?> conditionClass, boolean includeSubclasses, String... consequentMembers) {
    java.util.function.Consumer<ExpectedKeepRule.Builder> setCondition =
        b ->
            b.setConditionClass(conditionClass)
                .setConditionMembers("{ void foo(java.lang.Class); }");
    ExpectedRules.Builder builder = ExpectedRules.builder();
    for (int i = 0; i < consequentMembers.length; i++) {
      builder.add(
          ExpectedKeepRule.builder()
              .apply(setCondition)
              .setKeepVariant("-keepclasseswithmembers")
              .setConsequentClass(KeptClass.class)
              .setConsequentMembers(consequentMembers[i])
              .build());
      if (includeSubclasses) {
        builder.add(
            ExpectedKeepRule.builder()
                .apply(setCondition)
                .setKeepVariant("-keepclasseswithmembers")
                .setConsequentExtendsClass(KeptClass.class)
                .setConsequentMembers(consequentMembers[i])
                .build());
      }
    }
    return builder.build();
  }

  private static ExpectedRules getExpectedRulesJava(
      Class<?> conditionClass, String... consequentMembers) {
    return getExpectedRulesJava(conditionClass, false, consequentMembers);
  }

  private static ExpectedRules getExpectedRulesKotlin(
      String conditionClass,
      String conditionMembers,
      String consequentClass,
      String... consequentMembers) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b -> b.setConditionClass(conditionClass).setConditionMembers(conditionMembers);
    ExpectedRules.Builder builder = ExpectedRules.builder();
    for (int i = 0; i < consequentMembers.length; i++) {
      builder.add(
          ExpectedKeepRule.builder()
              .apply(setCondition)
              .setKeepVariant("-keepclasseswithmembers")
              .setConsequentClass(consequentClass)
              .setConsequentMembers(consequentMembers[i])
              .build());
    }
    return builder.build();
  }

  private static void buildUsesReflectionToAccessField(
      AnnotationBuilder builder, Object clazz, String fieldName, Class<?> fieldType) {
    AnnotationContentBuilder ab =
        builder.setAnnotationClass(Reference.classFromClass(UsesReflectionToAccessField.class));
    if (clazz instanceof String) {
      ab.setField("className", clazz);
    } else {
      assert clazz instanceof Class<?> || clazz instanceof Type;
      ab.setField("classConstant", clazz);
    }
    ab.setField("fieldName", fieldName);
    // No fieldType means any field type.
    if (fieldType != null) {
      ab.setField("fieldType", fieldType);
    }
  }

  private static void buildUsesReflectionToAccessField(
      AnnotationBuilder builder, Object clazz, String fieldName) {
    buildUsesReflectionToAccessField(builder, clazz, fieldName, null);
  }

  private static void buildUsesReflectionToAccessFieldMultiple(
      AnnotationBuilder builder, Object clazz) {
    builder
        .setAnnotationClass(
            Reference.classFromBinaryName(
                Reference.classFromClass(UsesReflectionToAccessField.class).getBinaryName()
                    + "$Container"))
        .buildArray(
            "value",
            builder1 ->
                builder1
                    .setAnnotationField(
                        null,
                        builder2 ->
                            buildUsesReflectionToAccessField(builder2, clazz, "x", int.class))
                    .setAnnotationField(
                        null,
                        builder3 ->
                            buildUsesReflectionToAccessField(builder3, clazz, "y", long.class))
                    .setAnnotationField(
                        null,
                        builder4 ->
                            buildUsesReflectionToAccessField(builder4, clazz, "s", String.class)));
  }

  @Test
  public void testAnyFieldType() throws Exception {
    testExtractedRulesAndRunJava(
        ClassWithAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder -> buildUsesReflectionToAccessField(builder, KeptClass.class, "x"))),
        getExpectedRulesJava(ClassWithAnnotation.class, "{ *** x; }"),
        parameters.isReference() ? StringUtils.lines("3") : StringUtils.lines("1"));
  }

  @Test
  public void testIntFieldType() throws Exception {
    testExtractedRulesAndRunJava(
        ClassWithAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessField(builder, KeptClass.class, "x", int.class))),
        getExpectedRulesJava(ClassWithAnnotation.class, "{ int x; }"),
        parameters.isReference() ? StringUtils.lines("3") : StringUtils.lines("1"));
  }

  @Test
  public void testMultipleFieldTypes() throws Exception {
    testExtractedRulesAndRunJava(
        ClassWithAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder -> buildUsesReflectionToAccessFieldMultiple(builder, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, "{ int x; }", "{ long y; }", "{ java.lang.String s; }"),
        StringUtils.lines("3"));
  }

  @Test
  public void testIncludeSubclasses() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder -> {
                  builder
                      .setAnnotationClass(
                          Reference.classFromClass(UsesReflectionToAccessField.class))
                      .setField("classConstant", KeptClass.class)
                      .setField("fieldName", "x")
                      .setField("includeSubclasses", true);
                })),
        getExpectedRulesJava(ClassWithAnnotation.class, true, "{ *** x; }"));
  }

  @Test
  public void testAnyFieldTypeKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName("com.android.tools.r8.keepanno.androidx.kt.Fields"),
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessField(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass")),
                        "x")),
        "com.android.tools.r8.keepanno.androidx.kt.FieldsKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.Fields",
            "{ void foo(kotlin.reflect.KClass); }",
            "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass",
            "{ *** x; }"),
        StringUtils.lines("1"));
  }

  @Test
  public void testIntFieldTypeKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName("com.android.tools.r8.keepanno.androidx.kt.Fields"),
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessField(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass")),
                        "x",
                        int.class)),
        "com.android.tools.r8.keepanno.androidx.kt.FieldsKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.Fields",
            "{ void foo(kotlin.reflect.KClass); }",
            "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass",
            "{ int x; }"),
        StringUtils.lines("1"));
  }

  @Test
  public void testMultipleFieldsKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName("com.android.tools.r8.keepanno.androidx.kt.Fields"),
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessFieldMultiple(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass")))),
        "com.android.tools.r8.keepanno.androidx.kt.FieldsKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.Fields",
            "{ void foo(kotlin.reflect.KClass); }",
            "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass",
            "{ int x; }",
            "{ long y; }",
            "{ java.lang.String s; }"),
        StringUtils.lines("3"));
  }

  @Test
  public void testPropertyAccessKotlin() throws Exception {
    // TODO(b/323816623): With native interpretation kotlin.Metadata still gets stripped
    assumeFalse(parameters.isNativeR8());
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName(
                    "com.android.tools.r8.keepanno.androidx.kt.FieldsPropertyAccess"),
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessField(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.FieldsPropertyAccessKeptClass")),
                        "x",
                        int.class)),
        "com.android.tools.r8.keepanno.androidx.kt.FieldsPropertyAccessKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.FieldsPropertyAccess",
            "{ void foo(); }",
            "com.android.tools.r8.keepanno.androidx.kt.FieldsPropertyAccessKeptClass",
            "{ int x; }"),
        // TODO(b/392865072): Not sure why this succeeds on DEX even though the getter getX has
        //  been removed.
        parameters.getBackend().isDex()
            ? r -> r.assertSuccessWithOutput(StringUtils.lines("1"))
            : r ->
                r.assertFailureWithErrorThatMatches(
                    containsString(
                        "Property 'x' (JVM signature: getX()I) not resolved in class"
                            + " com.android.tools.r8.keepanno.androidx.kt")));
  }

  // Test class without annotation to be used by multiple tests inserting annotations using a
  // transformer.
  static class ClassWithAnnotation {

    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        System.out.println(clazz.getDeclaredFields().length);
      }
    }

    public static void main(String[] args) throws Exception {
      new ClassWithAnnotation().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  static class KeptClass {
    public int x;
    public long y;
    public String s;
  }
}
