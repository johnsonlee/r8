// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static org.junit.Assert.assertEquals;

import androidx.annotation.keep.UsesReflectionToConstruct;
import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.AnnotationBuilder;
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
public class KeepUsesReflectionForInstantiationNoArgsConstructorTest
    extends KeepAnnoTestExtractedRulesBase {

  // String constant to be references from annotations.
  static final String classNameOfKeptClass =
      "com.android.tools.r8.keepanno.androidx.KeepUsesReflectionForInstantiationNoArgsConstructorTest$KeptClass";

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    assertEquals(KeptClass.class.getTypeName(), classNameOfKeptClass);
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
    return StringUtils.lines("<init>()");
  }

  @Override
  protected String getExpectedOutputForKotlin() {
    return StringUtils.lines(
        "fun `<init>`(): com.android.tools.r8.keepanno.androidx.kt.KeptClass",
        "<init>()",
        "fun `<init>`(): com.android.tools.r8.keepanno.androidx.kt.KeptClass",
        "<init>()");
  }

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(
          KeepUsesReflectionForInstantiationNoArgsConstructorTest.class,
          "kt",
          "OnlyNoArgsConstructor.kt",
          "KeptClass.kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Collection<Path> getKotlinSourcesClassName() {
    try {
      return getFilesInTestFolderRelativeToClass(
          KeepUsesReflectionForInstantiationNoArgsConstructorTest.class,
          "kt",
          "OnlyNoArgsConstructorClassName.kt",
          "KeptClass.kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static KotlinCompileMemoizer compilationResultsWithoutAnnotation;

  private static Collection<Path> getKotlinSourcesWithoutAnnotation() {
    try {
      return getFilesInTestFolderRelativeToClass(
          KeepUsesReflectionForInstantiationNoArgsConstructorTest.class,
          "kt",
          "OnlyNoArgsConstructorWithoutAnnotation.kt",
          "KeptClass.kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    compilationResults = getCompileMemoizerWithKeepAnnoLib(getKotlinSources());
    compilationResultsClassName = getCompileMemoizerWithKeepAnnoLib(getKotlinSourcesClassName());
    compilationResultsWithoutAnnotation =
        new KotlinCompileMemoizer(getKotlinSourcesWithoutAnnotation());
  }

  private static ExpectedRules getExpectedRulesJava(Class<?> conditionClass) {
    return getExpectedRulesJava(conditionClass, null);
  }

  private static ExpectedRules getExpectedRulesJava(
      Class<?> conditionClass, String contitionMembers) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b -> b.setConditionClass(conditionClass).setConditionMembers(contitionMembers);

    ExpectedRules.Builder builder =
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .apply(setCondition)
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void <init>(); }")
                    .build());
    addConsequentKotlinMetadata(builder, b -> b.apply(setCondition));
    return builder.build();
  }

  private static ExpectedRules getExpectedRulesKotlin(String conditionClass) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b ->
            b.setConditionClass(conditionClass)
                .setConditionMembers("{ void foo(kotlin.reflect.KClass); }");
    ExpectedRules.Builder builder =
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .apply(setCondition)
                    .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.KeptClass")
                    .setConsequentMembers("{ void <init>(); }")
                    .build());
    addConsequentKotlinMetadata(builder, b -> b.apply(setCondition));
    return builder.build();
  }

  @Test
  public void testOnlyNoArgsConstructor() throws Exception {
    runTestExtractedRulesJava(
        ImmutableList.of(OnlyNoArgsConstructor.class, KeptClass.class),
        getExpectedRulesJava(OnlyNoArgsConstructor.class, "{ void foo(java.lang.Class); }"));
  }

  static class OnlyNoArgsConstructor {

    @UsesReflectionToConstruct(
        classConstant = KeptClass.class,
        parameterTypes = {})
    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        clazz.getDeclaredConstructor().newInstance();
      }
    }

    public static void main(String[] args) throws Exception {
      new OnlyNoArgsConstructor().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  @Test
  public void testOnlyNoArgsConstructorClassName() throws Exception {
    runTestExtractedRulesJava(
        ImmutableList.of(OnlyNoArgsConstructorClassName.class, KeptClass.class),
        getExpectedRulesJava(
            OnlyNoArgsConstructorClassName.class, "{ void foo(java.lang.Class); }"));
  }

  static class OnlyNoArgsConstructorClassName {

    @UsesReflectionToConstruct(
        className = classNameOfKeptClass,
        parameterTypes = {})
    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        clazz.getDeclaredConstructor().newInstance();
      }
    }

    public static void main(String[] args) throws Exception {
      new OnlyNoArgsConstructorClassName().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  @Test
  public void testOnlyNoArgsConstructorKotlin() throws Exception {
    runTestExtractedRulesKotlin(
        compilationResults,
        "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorKt",
        getExpectedRulesKotlin("com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructor"));
  }

  @Test
  public void testOnlyNoArgsConstructorKotlinClassName() throws Exception {
    runTestExtractedRulesKotlin(
        compilationResultsClassName,
        "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorClassNameKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorClassName"));
  }

  private static void buildNoArgsConstructor(AnnotationBuilder builder, Object clazz) {
    if (clazz instanceof String) {
      builder.setField("className", clazz);
    } else {
      assert clazz instanceof Class<?> || clazz instanceof Type;
      builder.setField("classConstant", clazz);
    }
    // Set the empty array for the no args constructor.
    builder.setArray("parameterTypes");
  }

  @Test
  // This test is similar to testOnlyNoArgsConstructor() except that the annotation is inserted
  // by a transformer.
  public void testOnlyNoArgsConstructorUsingTransformer() throws Exception {
    runTestExtractedRulesJava(
        OnlyNoArgsConstructorWithoutAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                OnlyNoArgsConstructorWithoutAnnotation.class,
                MethodPredicate.onName("foo"),
                UsesReflectionToConstruct.class,
                builder -> buildNoArgsConstructor(builder, KeptClass.class))),
        getExpectedRulesJava(
            OnlyNoArgsConstructorWithoutAnnotation.class, "{ void foo(java.lang.Class); }"));
  }

  @Test
  public void testOnlyNoArgsConstructorOnClass() throws Exception {
    runTestExtractedRulesJava(
        OnlyNoArgsConstructorWithoutAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnClass(
                OnlyNoArgsConstructorWithoutAnnotation.class,
                UsesReflectionToConstruct.class,
                builder -> buildNoArgsConstructor(builder, KeptClass.class))),
        getExpectedRulesJava(OnlyNoArgsConstructorWithoutAnnotation.class));
  }

  @Test
  // This test is similar to testOnlyNoArgsConstructorClassName() except that the annotation is
  // inserted by a transformer.
  public void testOnlyNoArgsConstructorClassNameUsingTransformer() throws Exception {
    runTestExtractedRulesJava(
        OnlyNoArgsConstructorWithoutAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                OnlyNoArgsConstructorWithoutAnnotation.class,
                MethodPredicate.onName("foo"),
                UsesReflectionToConstruct.class,
                builder -> buildNoArgsConstructor(builder, classNameOfKeptClass))),
        getExpectedRulesJava(
            OnlyNoArgsConstructorWithoutAnnotation.class, "{ void foo(java.lang.Class); }"));
  }

  @Test
  public void testOnlyNoArgsConstructorClassNameOnClass() throws Exception {
    runTestExtractedRulesJava(
        OnlyNoArgsConstructorWithoutAnnotation.class,
        ImmutableList.of(KeptClass.class),
        ImmutableList.of(
            setAnnotationOnClass(
                OnlyNoArgsConstructorWithoutAnnotation.class,
                UsesReflectionToConstruct.class,
                builder -> buildNoArgsConstructor(builder, classNameOfKeptClass))),
        getExpectedRulesJava(OnlyNoArgsConstructorWithoutAnnotation.class));
  }

  @Test
  public void testOnlyNoArgsConstructorKotlinUsingTransformer() throws Exception {
    runTestExtractedRulesKotlin(
        compilationResultsWithoutAnnotation,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName(
                    "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorWithoutAnnotation"),
                MethodPredicate.onName("foo"),
                UsesReflectionToConstruct.class,
                builder ->
                    buildNoArgsConstructor(
                        builder,
                        Type.getType(
                            DescriptorUtils.javaTypeToDescriptor(
                                "com.android.tools.r8.keepanno.androidx.kt.KeptClass")))),
        "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorWithoutAnnotationKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorWithoutAnnotation"));
  }

  @Test
  public void testOnlyNoArgsConstructorKotlinClassNameUsingTransformer() throws Exception {
    runTestExtractedRulesKotlin(
        compilationResultsWithoutAnnotation,
        (classReference, classFileBytes) ->
            setAnnotationOnMethod(
                classReference,
                classFileBytes,
                Reference.classFromTypeName(
                    "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorWithoutAnnotation"),
                MethodPredicate.onName("foo"),
                UsesReflectionToConstruct.class,
                builder ->
                    buildNoArgsConstructor(
                        builder, "com.android.tools.r8.keepanno.androidx.kt.KeptClass")),
        "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorWithoutAnnotationKt",
        getExpectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorWithoutAnnotation"));
  }

  // Test class without annotation to be used by multiple tests inserting annotations using a
  // transformer.
  static class OnlyNoArgsConstructorWithoutAnnotation {

    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        clazz.getDeclaredConstructor().newInstance();
      }
    }

    public static void main(String[] args) throws Exception {
      new OnlyNoArgsConstructorWithoutAnnotation()
          .foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  static class KeptClass {
    KeptClass() {
      System.out.println("<init>()");
    }

    KeptClass(int i) {
      System.out.println("<init>(int)");
    }

    KeptClass(long j) {
      System.out.println("<init>(long)");
    }
  }
}
