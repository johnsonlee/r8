// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static org.junit.Assert.assertEquals;

import androidx.annotation.keep.UsesReflectionToConstruct;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
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

@RunWith(Parameterized.class)
public class KeepUsesReflectionForInstantiationMultipleConstructorsTest
    extends KeepAnnoTestExtractedRulesBase {

  // String constant to be referenced from annotations.
  static final String classNameOfKeptClass =
      "com.android.tools.r8.keepanno.androidx.KeepUsesReflectionForInstantiationMultipleConstructorsTest$KeptClass";

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    assertEquals(KeptClass.class.getTypeName(), classNameOfKeptClass);
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
    return StringUtils.lines("<init>(int)", "<init>(long)");
  }

  @Override
  protected String getExpectedOutputForKotlin() {
    // Kotlin secondary constructors has to delegate to the primary constructor.
    return StringUtils.lines(
        "fun `<init>`(kotlin.Int): com.android.tools.r8.keepanno.androidx.kt.KeptClass",
        "In KeptClass.<init>()",
        "In KeptClass.<init>(Int)",
        "fun `<init>`(kotlin.Long): com.android.tools.r8.keepanno.androidx.kt.KeptClass",
        "In KeptClass.<init>()",
        "In KeptClass.<init>(Long)");
  }

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(
          KeepUsesReflectionForInstantiationMultipleConstructorsTest.class,
          "kt",
          "IntAndLongArgsConstructors.kt",
          "KeptClass.kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Collection<Path> getKotlinSourcesClassName() {
    try {
      return getFilesInTestFolderRelativeToClass(
          KeepUsesReflectionForInstantiationMultipleConstructorsTest.class,
          "kt",
          "IntAndLongArgsConstructorsClassName.kt",
          "KeptClass.kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    compilationResults = getCompileMemoizerWithKeepAnnoLib(getKotlinSources());
    compilationResultsClassName = getCompileMemoizerWithKeepAnnoLib(getKotlinSourcesClassName());
  }

  private ExpectedRules expectedRulesJava(Class<?> conditionClass) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b ->
            b.setConditionClass(conditionClass)
                .setConditionMembers("{ void foo(java.lang.Class); }");
    return ExpectedRules.builder()
        .add(
            ExpectedKeepRule.builder()
                .apply(setCondition)
                .setKeepVariant("-keepclasseswithmembers")
                .setConsequentClass(KeptClass.class)
                .setConsequentMembers("{ void <init>(int); }")
                .build())
        .add(
            ExpectedKeepRule.builder()
                .apply(setCondition)
                .setKeepVariant("-keepclasseswithmembers")
                .setConsequentClass(KeptClass.class)
                .setConsequentMembers("{ void <init>(long); }")
                .build())
        .build();
  }

  private ExpectedRules expectedRulesKotlin(String conditionClass) {
    String conditionMember = "{ void foo(kotlin.reflect.KClass); }";
    return ExpectedRules.builder()
        .add(
            ExpectedKeepRule.builder()
                .setConditionClass(conditionClass)
                .setConditionMembers(conditionMember)
                .setKeepVariant("-keepclasseswithmembers")
                .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.KeptClass")
                .setConsequentMembers("{ void <init>(int); }")
                .build())
        .add(
            ExpectedKeepRule.builder()
                .setConditionClass(conditionClass)
                .setConditionMembers(conditionMember)
                .setKeepVariant("-keepclasseswithmembers")
                .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.KeptClass")
                .setConsequentMembers("{ void <init>(long); }")
                .build())
        .build();
  }

  @Test
  public void testIntAndLongArgsConstructors() throws Exception {
    testExtractedRulesAndRunJava(
        ImmutableList.of(IntAndLongArgsConstructors.class, KeptClass.class),
        expectedRulesJava(IntAndLongArgsConstructors.class));
  }

  static class IntAndLongArgsConstructors {

    @UsesReflectionToConstruct(
        classConstant = KeptClass.class,
        parameterTypes = {int.class})
    @UsesReflectionToConstruct(
        classConstant = KeptClass.class,
        parameterTypes = {long.class})
    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        clazz.getDeclaredConstructor(int.class).newInstance(1);
        clazz.getDeclaredConstructor(long.class).newInstance(2L);
      }
    }

    public static void main(String[] args) throws Exception {
      new IntAndLongArgsConstructors().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  @Test
  public void testIntLongArgsConstructorsClassNames() throws Exception {
    testExtractedRulesAndRunJava(
        ImmutableList.of(IntAndLongConstructorsClassName.class, KeptClass.class),
        expectedRulesJava(IntAndLongConstructorsClassName.class));
  }

  @Test
  public void testPrimitiveTypesAsTypeName() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    builder
                        .setAnnotationClass(
                            Reference.classFromBinaryName(
                                Reference.classFromClass(UsesReflectionToConstruct.class)
                                        .getBinaryName()
                                    + "$Container"))
                        .buildArray(
                            "value",
                            builder1 ->
                                builder1
                                    .setAnnotationField(
                                        null,
                                        builder2 ->
                                            builder2
                                                .setAnnotationClass(
                                                    Reference.classFromClass(
                                                        UsesReflectionToConstruct.class))
                                                .setField("classConstant", KeptClass.class)
                                                .setArray("parameterTypeNames", "Int"))
                                    .setAnnotationField(
                                        null,
                                        builder3 ->
                                            builder3
                                                .setAnnotationClass(
                                                    Reference.classFromClass(
                                                        UsesReflectionToConstruct.class))
                                                .setField("classConstant", KeptClass.class)
                                                .setArray("parameterTypeNames", "Long"))))),
        expectedRulesJava(ClassWithAnnotation.class));
  }

  static class IntAndLongConstructorsClassName {

    @UsesReflectionToConstruct(
        className = classNameOfKeptClass,
        parameterTypes = {int.class})
    @UsesReflectionToConstruct(
        className = classNameOfKeptClass,
        parameterTypes = {long.class})
    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        clazz.getDeclaredConstructor(int.class).newInstance(1);
        clazz.getDeclaredConstructor(long.class).newInstance(2L);
      }
    }

    public static void main(String[] args) throws Exception {
      new IntAndLongConstructorsClassName().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  @Test
  public void testIntLongArgsConstructorsKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        "com.android.tools.r8.keepanno.androidx.kt.IntAndLongArgsConstructorsKt",
        expectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.IntAndLongArgsConstructors"));
  }

  @Test
  public void testIntLongArgsConstructorsKotlinClassName() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResultsClassName,
        "com.android.tools.r8.keepanno.androidx.kt.IntAndLongArgsConstructorsClassNameKt",
        expectedRulesKotlin(
            "com.android.tools.r8.keepanno.androidx.kt.IntAndLongArgsConstructorsClassName"));
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
    KeptClass() {
      System.out.println("<init>()");
    }

    KeptClass(int i) {
      System.out.println("<init>(int)");
    }

    KeptClass(long j) {
      System.out.println("<init>(long)");
    }

    KeptClass(String s) {
      System.out.println("<init>(String)");
    }
  }
}
