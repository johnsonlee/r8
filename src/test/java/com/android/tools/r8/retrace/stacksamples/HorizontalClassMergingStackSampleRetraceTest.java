// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.stacksamples;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

public class HorizontalClassMergingStackSampleRetraceTest extends StackSampleRetraceTestBase {

  private static final String obfuscatedClassName = "com.android.tools.r8.retrace.stacksamples.a";
  private static final String obfuscatedMethodNameFoo = "c";
  private static final String obfuscatedMethodNameBar = "b";
  private static final String obfuscatedMethodNameBaz = "a";

  static List<byte[]> programClassFileData;

  // Map the line numbers of the Main class so that the line numbers start from 42.
  // This ensures that changes to the test does not impact the line numbers of the test data.
  @BeforeClass
  public static void setup() throws Exception {
    programClassFileData =
        Stream.of(Main.class, I.class, A.class, B.class)
            .map(
                clazz ->
                    transformer(clazz).mapLineNumbers(42 - getFirstLineNumber(clazz)).transform())
            .collect(Collectors.toList());
  }

  @Test
  public void test() throws Exception {
    runTest(
        testBuilder ->
            testBuilder
                .addProgramClassFileData(programClassFileData)
                .addOptionsModification(
                    options -> options.inlinerOptions().enableConstructorInlining = false)
                .enableInliningAnnotations()
                .enableNoVerticalClassMergingAnnotations());
  }

  @Override
  Class<?> getMainClass() {
    return Main.class;
  }

  // TODO(b/462362930): Should use pc encoding.
  @Override
  String getExpectedMap() {
    return StringUtils.joinLines(
        "com.android.tools.r8.retrace.stacksamples.HorizontalClassMergingStackSampleRetraceTest$A"
            + " -> com.android.tools.r8.retrace.stacksamples.a:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"HorizontalClassMergingStackSampleRetraceTest.java\"}",
        "    int $r8$classId -> a",
        "      # {\"id\":\"com.android.tools.r8.synthesized\"}",
        "    1:1:void <init>(int):0:0 -> <init>",
        "      # {\"id\":\"com.android.tools.r8.synthesized\"}",
        "    1:1:void"
            + " com.android.tools.r8.retrace.stacksamples.HorizontalClassMergingStackSampleRetraceTest$B.baz():51:51"
            + " -> a",
        "    2:2:void baz():51:51 -> a",
        "    1:8:void"
            + " com.android.tools.r8.retrace.stacksamples.HorizontalClassMergingStackSampleRetraceTest$B.bar():46:46"
            + " -> b",
        "    1:8:void foo():46:46 -> c",
        "com.android.tools.r8.retrace.stacksamples.HorizontalClassMergingStackSampleRetraceTest$I"
            + " -> com.android.tools.r8.retrace.stacksamples.b:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"HorizontalClassMergingStackSampleRetraceTest.java\"}",
        "    void baz() -> a",
        "com.android.tools.r8.retrace.stacksamples.HorizontalClassMergingStackSampleRetraceTest$Main"
            + " -> com.android.tools.r8.retrace.stacksamples.c:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"HorizontalClassMergingStackSampleRetraceTest.java\"}",
        "    1:3:void main(java.lang.String[]):45:47 -> main");
  }

  @Override
  String getExpectedOutput() {
    return StringUtils.lines("foo", "bar", "baz");
  }

  @Override
  void inspectCode(CodeInspector inspector) {
    // Verify that foo(), bar() and baz() are all present on A, and that they have been renamed to
    // b(), a() and c(), respectively
    ClassSubject aClass = inspector.clazz(A.class);
    assertThat(aClass, isPresent());
    assertEquals(obfuscatedClassName, aClass.getFinalName());
    assertEquals(4, aClass.allMethods().size());

    MethodSubject initMethod = aClass.uniqueInstanceInitializer();
    assertThat(initMethod, isPresent());

    MethodSubject fooMethod = aClass.uniqueMethodWithOriginalName("foo");
    assertThat(fooMethod, isPresent());
    assertEquals(obfuscatedMethodNameFoo, fooMethod.getFinalName());

    MethodSubject barMethod = aClass.uniqueMethodWithOriginalName("bar");
    assertThat(barMethod, isPresent());
    assertEquals(obfuscatedMethodNameBar, barMethod.getFinalName());

    MethodSubject bazMethod = aClass.uniqueMethodWithOriginalName("baz");
    assertThat(bazMethod, isPresent());
    assertEquals(obfuscatedMethodNameBaz, bazMethod.getFinalName());
  }

  @Override
  void inspectHorizontallyMergedClasses(HorizontallyMergedClassesInspector inspector) {
    inspector.assertMergedInto(B.class, A.class).assertNoOtherClassesMerged();
  }

  @Override
  void testRetrace(R8TestCompileResultBase<?> compileResult) throws Exception {
    // Expected: `A.<init>` should retrace to `void A.<init>(int)`.
    // TODO(b/460808033): Assert that this is a synthetic method.
    {
      RetraceMethodElement retraceResult =
          getSingleRetraceMethodElement(
              Reference.classFromTypeName(obfuscatedClassName), "<init>", compileResult);
      assertEquals(
          Reference.methodFromDescriptor(Reference.classFromClass(A.class), "<init>", "(I)V"),
          retraceResult.getRetracedMethod().asKnown().getMethodReference());
    }

    // Expected: `a.c` should retrace to `void A.foo()`.
    {
      RetraceMethodElement retraceResult =
          getSingleRetraceMethodElement(
              Reference.classFromTypeName(obfuscatedClassName),
              obfuscatedMethodNameFoo,
              compileResult);
      assertEquals(
          Reference.methodFromMethod(A.class.getDeclaredMethod("foo")),
          retraceResult.getRetracedMethod().asKnown().getMethodReference());
    }

    // Expected: `a.b` should retrace to `void B.bar()`.
    {
      RetraceMethodElement retraceResult =
          getSingleRetraceMethodElement(
              Reference.classFromTypeName(obfuscatedClassName),
              obfuscatedMethodNameBar,
              compileResult);
      assertEquals(
          Reference.methodFromMethod(B.class.getDeclaredMethod("bar")),
          retraceResult.getRetracedMethod().asKnown().getMethodReference());
    }

    // Expected: `a.a` should retrace to {`void A.baz()`, `void B.baz()`}.
    {
      Set<MethodReference> retraceResult =
          getRetraceMethodElements(
                  Reference.classFromTypeName(obfuscatedClassName),
                  obfuscatedMethodNameBaz,
                  compileResult)
              .stream()
              .map(
                  retraceMethodElement ->
                      retraceMethodElement.getRetracedMethod().asKnown().getMethodReference())
              .collect(Collectors.toSet());
      assertEquals(
          ImmutableSet.of(
              Reference.methodFromMethod(A.class.getDeclaredMethod("baz")),
              Reference.methodFromMethod(B.class.getDeclaredMethod("baz"))),
          retraceResult);
    }
  }

  static class Main {

    public static void main(String[] args) {
      A.foo();
      B.bar();
      (System.currentTimeMillis() > 0 ? new A() : new B()).baz();
    }
  }

  @NoVerticalClassMerging
  interface I {

    void baz();
  }

  static class A implements I {

    @NeverInline
    static void foo() {
      System.out.println("foo");
    }

    @Override
    public void baz() {
      System.out.println("baz");
    }
  }

  static class B implements I {

    @NeverInline
    static void bar() {
      System.out.println("bar");
    }

    @Override
    public void baz() {
      System.out.println("baz");
    }
  }
}
