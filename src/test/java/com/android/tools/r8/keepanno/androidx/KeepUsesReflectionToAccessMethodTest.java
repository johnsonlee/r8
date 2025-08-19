// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;

import androidx.annotation.keep.UsesReflectionToAccessMethod;
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
public class KeepUsesReflectionToAccessMethodTest extends KeepAnnoTestExtractedRulesBase {

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
      return getFilesInTestFolderRelativeToClass(
          KeepUsesReflectionToAccessMethodTest.class, "kt", "Methods.kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    compilationResults = getCompileMemoizerWithKeepAnnoLib(getKotlinSources());
  }

  private static ExpectedRules getExpectedRulesJava(
      Class<?> conditionClass, String... consequentMembers) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b ->
            b.setConditionClass(conditionClass)
                .setConditionMembers("{ void foo(java.lang.Class); }");
    ExpectedRules.Builder builder = ExpectedRules.builder();
    for (int i = 0; i < consequentMembers.length; i++) {
      builder.add(
          ExpectedKeepRule.builder()
              .apply(setCondition)
              .setConsequentClass(KeptClass.class)
              .setConsequentMembers(consequentMembers[i])
              .build());
    }
    addConsequentKotlinMetadata(builder, b -> b.apply(setCondition));
    return builder.build();
  }

  private static ExpectedRules getExpectedRulesKotlin(
      String conditionClass, String conditionMembers, String... consequentMembers) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b ->
            b.setConditionClass("com.android.tools.r8.keepanno.androidx.kt.Methods")
                .setConditionMembers(conditionMembers);
    ExpectedRules.Builder builder = ExpectedRules.builder();
    for (int i = 0; i < consequentMembers.length; i++) {
      builder.add(
          ExpectedKeepRule.builder()
              .apply(setCondition)
              .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.MethodsKeptClass")
              .setConsequentMembers(consequentMembers[i])
              .build());
    }
    addConsequentKotlinMetadata(builder, b -> b.apply(setCondition));
    return builder.build();
  }

  private static void buildUsesReflectionToAccessMethod(
      AnnotationBuilder builder, Object clazz, String methodName, Class<?>... parameterTypes) {
    AnnotationContentBuilder ab =
        builder.setAnnotationClass(Reference.classFromClass(UsesReflectionToAccessMethod.class));
    if (clazz instanceof String) {
      ab.setField("className", clazz);
    } else {
      assert clazz instanceof Class<?> || clazz instanceof Type;
      ab.setField("classConstant", clazz);
    }
    ab.setField("methodName", methodName);
    // No parameterTypes or parameterTypeNames means any method.
    if (parameterTypes != null && parameterTypes.length > 0) {
      ab.setArray("parameterTypes", (Object[]) parameterTypes);
    }
    // No returnType or returnTypeName means any return type.
  }

  private static void buildUsesReflectionToAccessMethodMultiple(
      AnnotationBuilder builder, Object clazz) {
    builder
        .setAnnotationClass(
            Reference.classFromBinaryName(
                Reference.classFromClass(UsesReflectionToAccessMethod.class).getBinaryName()
                    + "$Container"))
        .buildArray(
            "value",
            builder1 ->
                builder1
                    .setAnnotationField(
                        null,
                        builder2 ->
                            buildUsesReflectionToAccessMethod(builder2, clazz, "m", int.class))
                    .setAnnotationField(
                        null,
                        builder3 ->
                            buildUsesReflectionToAccessMethod(
                                builder3, clazz, "m", int.class, long.class))
                    .setAnnotationField(
                        null,
                        builder4 ->
                            buildUsesReflectionToAccessMethod(
                                builder4, clazz, "m", String.class, String.class, String.class)));
  }

  @Test
  public void testAnyReturnTypeAndAnyParameters() throws Exception {
    testExtractedRulesAndRunJava(
        ClassWithAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder -> buildUsesReflectionToAccessMethod(builder, KeptClass.class, "m"))),
        getExpectedRulesJava(ClassWithAnnotation.class, "{ *** m(...); }"),
        StringUtils.lines("4"));
  }

  @Test
  public void testAnyReturnTypeAndIntParameter() throws Exception {
    testExtractedRulesAndRunJava(
        ClassWithAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessMethod(builder, KeptClass.class, "m", int.class))),
        getExpectedRulesJava(ClassWithAnnotation.class, "{ *** m(int); }"),
        parameters.isReference() ? StringUtils.lines("4") : StringUtils.lines("1"));
  }

  @Test
  public void testAnyReturnTypeAndMultipleParameterLists() throws Exception {
    testExtractedRulesAndRunJava(
        ClassWithAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder -> buildUsesReflectionToAccessMethodMultiple(builder, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            "{ *** m(int); }",
            "{ *** m(int, long); }",
            "{ *** m(java.lang.String, java.lang.String, java.lang.String); }"),
        parameters.isReference() ? StringUtils.lines("4") : StringUtils.lines("3"));
  }

  @Test
  public void testAnyReturnTypeAndAnyParametersKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName("com.android.tools.r8.keepanno.androidx.kt.Methods"),
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessMethod(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.MethodsKeptClass")),
                        "m")),
        "com.android.tools.r8.keepanno.androidx.kt.MethodsKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.Methods",
            "{ void foo(kotlin.reflect.KClass); }",
            "{ *** m(...); }"),
        StringUtils.lines("4"));
  }

  @Test
  public void testAnyReturnTypeAndIntParameterKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName("com.android.tools.r8.keepanno.androidx.kt.Methods"),
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessMethod(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.MethodsKeptClass")),
                        "m",
                        int.class)),
        "com.android.tools.r8.keepanno.androidx.kt.MethodsKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.Methods",
            "{ void foo(kotlin.reflect.KClass); }",
            "{ *** m(int); }"),
        parameters.isReference() ? StringUtils.lines("4") : StringUtils.lines("1"));
  }

  @Test
  public void testAnyReturnTypeAndMultipleParameterListsKotlin() throws Exception {
    testExtractedRules(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName("com.android.tools.r8.keepanno.androidx.kt.Methods"),
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToAccessMethodMultiple(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.MethodsKeptClass")))),
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.Methods",
            "{ void foo(kotlin.reflect.KClass); }",
            "{ *** m(int); }",
            "{ *** m(int, long); }",
            "{ *** m(java.lang.String, java.lang.String, java.lang.String); }"));
  }

  // Test class without annotation to be used by multiple tests inserting annotations using a
  // transformer.
  static class ClassWithAnnotation {

    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        System.out.println(clazz.getDeclaredMethods().length);
      }
    }

    public static void main(String[] args) throws Exception {
      new ClassWithAnnotation().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  static class KeptClass {
    public void m() {}

    public void m(int i) {}

    public void m(int i, long l) {}

    public void m(String s1, String s2, String s3) {}
  }
}
