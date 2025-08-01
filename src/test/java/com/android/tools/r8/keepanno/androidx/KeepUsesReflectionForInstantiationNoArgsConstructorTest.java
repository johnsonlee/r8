// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static org.junit.Assert.assertEquals;

import androidx.annotation.keep.UsesReflectionToConstruct;
import com.android.tools.r8.ToolHelper.DexVm.Version;
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

  @BeforeClass
  public static void beforeClass() throws Exception {
    compilationResults = getCompileMemoizerWithKeepAnnoLib(getKotlinSources());
    compilationResultsClassName = getCompileMemoizerWithKeepAnnoLib(getKotlinSourcesClassName());
  }

  @Test
  public void testOnlyNoArgsConstructor() throws Exception {
    String conditionMember = "{ void foo(java.lang.Class); }";
    ExpectedRules.Builder builder =
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setConditionClass(OnlyNoArgsConstructor.class)
                    .setConditionMembers(conditionMember)
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void <init>(); }")
                    .build());
    addConsequentKotlinMetadata(
        builder,
        b -> b.setConditionClass(OnlyNoArgsConstructor.class).setConditionMembers(conditionMember));
    runTestExtractedRulesJava(
        ImmutableList.of(OnlyNoArgsConstructor.class, KeptClass.class), builder.build());
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
  public void testOnlyNoArgsConstructorClassNames() throws Exception {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b ->
            b.setConditionClass(OnlyNoArgsConstructorClassNames.class)
                .setConditionMembers("{ void foo(java.lang.Class); }");
    ExpectedRules.Builder builder =
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .apply(setCondition)
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void <init>(); }")
                    .build());
    addConsequentKotlinMetadata(builder, b -> b.apply(setCondition));
    runTestExtractedRulesJava(
        ImmutableList.of(OnlyNoArgsConstructorClassNames.class, KeptClass.class), builder.build());
  }

  static class OnlyNoArgsConstructorClassNames {

    @UsesReflectionToConstruct(
        className = classNameOfKeptClass,
        parameterTypes = {})
    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        clazz.getDeclaredConstructor().newInstance();
      }
    }

    public static void main(String[] args) throws Exception {
      new OnlyNoArgsConstructorClassNames().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  @Test
  public void testOnlyNoArgsConstructorKotlin() throws Exception {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b ->
            b.setConditionClass("com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructor")
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
    runTestExtractedRulesKotlin(
        compilationResults,
        "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorKt",
        builder.build());
  }

  @Test
  public void testOnlyNoArgsConstructorKotlinClassName() throws Exception {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b ->
            b.setConditionClass(
                    "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorClassName")
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
    runTestExtractedRulesKotlin(
        compilationResultsClassName,
        "com.android.tools.r8.keepanno.androidx.kt.OnlyNoArgsConstructorClassNameKt",
        builder.build());
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
