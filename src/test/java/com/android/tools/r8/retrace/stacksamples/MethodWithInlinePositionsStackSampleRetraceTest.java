// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.stacksamples;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.BeforeClass;
import org.junit.Test;

public class MethodWithInlinePositionsStackSampleRetraceTest extends StackSampleRetraceTestBase {

  private static final String obfuscatedClassName = "com.android.tools.r8.retrace.stacksamples.a";
  private static final String obfuscatedMethodName = "a";

  static byte[] programClassFileData;

  // Map the line numbers of the Main class so that the line numbers start from 42.
  // This ensures that changes to the test does not impact the line numbers of the test data.
  @BeforeClass
  public static void setup() throws Exception {
    int firstLineNumber = getFirstLineNumber(Main.class);
    programClassFileData = transformer(Main.class).mapLineNumbers(42 - firstLineNumber).transform();
    assertEquals(42, getFirstLineNumber(programClassFileData));
  }

  @Test
  public void test() throws Exception {
    runTest(
        testBuilder ->
            testBuilder.addProgramClassFileData(programClassFileData).enableInliningAnnotations());
  }

  @Override
  Class<?> getMainClass() {
    return Main.class;
  }

  // TODO(b/462362930): Should use pc encoding.
  @Override
  String getExpectedMap() {
    return StringUtils.joinLines(
        "com.android.tools.r8.retrace.stacksamples.MethodWithInlinePositionsStackSampleRetraceTest$Main"
            + " -> com.android.tools.r8.retrace.stacksamples.a:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"MethodWithInlinePositionsStackSampleRetraceTest.java\"}",
        "    1:1:void foo():54:54 -> a",
        "    1:1:void test():50 -> a",
        "    2:2:void bar():59:59 -> a",
        "    2:2:void foo():55 -> a",
        "    2:2:void test():50 -> a",
        "    3:3:void baz():64:64 -> a",
        "    3:3:void bar():60 -> a",
        "    3:3:void foo():55 -> a",
        "    3:3:void test():50 -> a",
        "    1:4:void main(java.lang.String[]):45:45 -> main");
  }

  @Override
  String getExpectedOutput() {
    return StringUtils.lines("foo", "bar", "baz");
  }

  @Override
  void inspectCode(CodeInspector inspector) {
    // Verify all methods have been inlined into the test method.
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertEquals(2, mainClass.allMethods().size());

    // Verify Main.test is renamed to a.a.
    assertEquals(obfuscatedClassName, mainClass.getFinalName());
    assertEquals(
        obfuscatedMethodName, mainClass.uniqueMethodWithOriginalName("test").getFinalName());
  }

  @Override
  void testRetrace(R8TestCompileResultBase<?> compileResult) throws Exception {
    // Expected: `a.a` should retrace to `void Main.test()`.
    RetraceMethodElement retraceMethodElement =
        getSingleRetraceMethodElement(
            Reference.classFromTypeName(obfuscatedClassName), obfuscatedMethodName, compileResult);
    assertEquals(
        Reference.methodFromMethod(Main.class.getDeclaredMethod("test")),
        retraceMethodElement.getRetracedMethod().asKnown().getMethodReference());
  }

  static class Main {

    public static void main(String[] args) {
      test();
    }

    @NeverInline
    static void test() {
      foo();
    }

    static void foo() {
      System.out.println("foo");
      bar();
    }

    static void bar() {
      System.out.println("bar");
      baz();
    }

    static void baz() {
      System.out.println("baz");
    }
  }
}
