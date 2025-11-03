// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;

import androidx.annotation.keep.UnconditionallyKeep;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.AnnotationBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepUnconditionallyKeepTest extends KeepAnnoTestExtractedRulesBase {

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
                  KeepUnconditionallyKeepTest.class, "kt", "Fields.kt")
                  .stream(),
              getFilesInTestFolderRelativeToClass(
                  KeepUnconditionallyKeepTest.class, "kt", "FieldsPropertyAccess.kt")
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

  private static void buildUnconditionallyKeep(AnnotationBuilder builder) {
    builder.setAnnotationClass(Reference.classFromClass(UnconditionallyKeep.class));
  }

  @Test
  public void testKeepClass() throws Exception {
    testExtractedRulesAndRunJava(
        TestRunner.class,
        ImmutableList.of(TestRunner.class),
        ImmutableList.of(
            setAnnotationOnClass(
                KeptClass.class, KeepUnconditionallyKeepTest::buildUnconditionallyKeep)),
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keep")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void finalize(); }")
                    .build())
            .build(),
        parameters.isReference() ? StringUtils.lines("3", "4") : StringUtils.lines("0", "0"));
  }

  @Test
  public void testKeepOneField() throws Exception {
    testExtractedRulesAndRunJava(
        TestRunner.class,
        ImmutableList.of(TestRunner.class),
        ImmutableList.of(
            setAnnotationOnField(
                KeptClass.class,
                FieldPredicate.onName("x"),
                KeepUnconditionallyKeepTest::buildUnconditionallyKeep)),
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ int x; }")
                    .build())
            .build(),
        parameters.isReference() ? StringUtils.lines("3", "4") : StringUtils.lines("1", "0"));
  }

  @Test
  public void testKeepAllField() throws Exception {
    testExtractedRulesAndRunJava(
        TestRunner.class,
        ImmutableList.of(TestRunner.class),
        ImmutableList.of(
            setAnnotationOnField(
                KeptClass.class,
                FieldPredicate.all(),
                KeepUnconditionallyKeepTest::buildUnconditionallyKeep)),
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ int x; }")
                    .build())
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ long y; }")
                    .build())
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ java.lang.String s; }")
                    .build())
            .build(),
        parameters.isReference() ? StringUtils.lines("3", "4") : StringUtils.lines("3", "0"));
  }

  @Test
  public void testKeepOneMethod() throws Exception {
    testExtractedRulesAndRunJava(
        TestRunner.class,
        ImmutableList.of(TestRunner.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                KeptClass.class,
                MethodPredicate.onReference(
                    Reference.method(
                        Reference.classFromClass(KeptClass.class), "m", ImmutableList.of(), null)),
                KeepUnconditionallyKeepTest::buildUnconditionallyKeep)),
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void m(); }")
                    .build())
            .build(),
        parameters.isReference() ? StringUtils.lines("3", "4") : StringUtils.lines("0", "1"));
  }

  @Test
  public void testKeepAllMethods() throws Exception {
    testExtractedRulesAndRunJava(
        TestRunner.class,
        ImmutableList.of(TestRunner.class),
        ImmutableList.of(
            setAnnotationOnMethod(
                KeptClass.class,
                MethodPredicate.all(),
                KeepUnconditionallyKeepTest::buildUnconditionallyKeep)),
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void <init>(); }")
                    .build())
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void m(); }")
                    .build())
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void m(int); }")
                    .build())
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers(
                        "{ void m(java.lang.String, java.lang.String, java.lang.String); }")
                    .build())
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass(KeptClass.class)
                    .setConsequentMembers("{ void m(int, long); }")
                    .build())
            .build(),
        parameters.isReference() ? StringUtils.lines("3", "4") : StringUtils.lines("0", "4"));
  }

  @Test
  public void testKeepClassKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnClass(
                classReference,
                classFileBytes,
                Reference.classFromTypeName(
                    "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass"),
                KeepUnconditionallyKeepTest::buildUnconditionallyKeep),
        "com.android.tools.r8.keepanno.androidx.kt.FieldsKt",
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keep")
                    .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass")
                    .setConsequentMembers("{ void finalize(); }")
                    .build())
            .build(),
        parameters.isReference() ? StringUtils.lines("3") : StringUtils.lines("0"));
  }

  @Test
  public void testOneFieldKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnField(
                classReference,
                classFileBytes,
                Reference.classFromTypeName(
                    "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass"),
                FieldPredicate.onName("x"),
                KeepUnconditionallyKeepTest::buildUnconditionallyKeep),
        "com.android.tools.r8.keepanno.androidx.kt.FieldsKt",
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass")
                    .setConsequentMembers("{ int x; }")
                    .build())
            .build(),
        StringUtils.lines("1"));
  }

  @Test
  public void testAllFieldsKotlin() throws Exception {
    testExtractedRulesAndRunKotlin(
        compilationResults,
        (classReference, classFileBytes) ->
            setAnnotationOnField(
                classReference,
                classFileBytes,
                Reference.classFromTypeName(
                    "com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass"),
                FieldPredicate.all(),
                KeepUnconditionallyKeepTest::buildUnconditionallyKeep),
        "com.android.tools.r8.keepanno.androidx.kt.FieldsKt",
        ExpectedRules.builder()
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass")
                    .setConsequentMembers("{ int x; }")
                    .build())
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass")
                    .setConsequentMembers("{ long y; }")
                    .build())
            .add(
                ExpectedKeepRule.builder()
                    .setKeepVariant("-keepclasseswithmembers")
                    .setConsequentClass("com.android.tools.r8.keepanno.androidx.kt.FieldsKeptClass")
                    .setConsequentMembers("{ java.lang.String s; }")
                    .build())
            .build(),
        StringUtils.lines("3"));
  }

  static class TestRunner {

    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        System.out.println(clazz.getDeclaredFields().length);
        System.out.println(clazz.getDeclaredMethods().length);
      }
    }

    public static void main(String[] args) throws Exception {
      new TestRunner().foo(System.nanoTime() > 0 ? KeptClass.class : null);
    }
  }

  // Class where @UnconditionallyKeep annotations are inserted using transformer.
  static class KeptClass {
    public int x;
    public long y;
    public String s;

    public void m() {}

    public void m(int i) {}

    public void m(int i, long l) {}

    public void m(String s1, String s2, String s3) {}
  }
}
