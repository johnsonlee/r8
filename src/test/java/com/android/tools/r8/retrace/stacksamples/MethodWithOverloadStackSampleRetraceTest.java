// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.stacksamples;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

public class MethodWithOverloadStackSampleRetraceTest extends StackSampleRetraceTestBase {

  private static final String obfuscatedClassName = "com.android.tools.r8.retrace.stacksamples.b";
  private static final String obfuscatedMethodNameObject = "a";
  private static final String obfuscatedMethodNameString = "b";

  static List<byte[]> programClassFileData;

  @Parameter(1)
  public boolean keep;

  @Parameters(name = "{0}, keep: {1}")
  public static List<Object[]> testData() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  // Map the line numbers of the Main class so that the line numbers start from 42.
  // This ensures that changes to the test does not impact the line numbers of the test data.
  @BeforeClass
  public static void setup() throws Exception {
    programClassFileData =
        Stream.of(Main.class, Supplier.class, StringSupplier.class)
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
                .addDontOptimize()
                .applyIf(
                    keep,
                    b ->
                        b.addKeepRules(
                            "-keepclassmembers class "
                                + StringSupplier.class.getTypeName()
                                + " { * get(); }")));
  }

  @Override
  Class<?> getMainClass() {
    return Main.class;
  }

  // TODO(b/462362930): Should use pc encoding.
  @Override
  String getExpectedMap() {
    if (keep) {
      return StringUtils.joinLines(
          "com.android.tools.r8.retrace.stacksamples.MethodWithOverloadStackSampleRetraceTest$Main"
              + " -> com.android.tools.r8.retrace.stacksamples.a:",
          "# {\"id\":\"sourceFile\",\"fileName\":\"MethodWithOverloadStackSampleRetraceTest.java\"}",
          "    1:2:void main(java.lang.String[]):45:46 -> main",
          "com.android.tools.r8.retrace.stacksamples.MethodWithOverloadStackSampleRetraceTest$StringSupplier"
              + " -> com.android.tools.r8.retrace.stacksamples.b:",
          "# {\"id\":\"sourceFile\",\"fileName\":\"MethodWithOverloadStackSampleRetraceTest.java\"}",
          "    1:4:void <init>():42:42 -> <init>",
          "    1:5:java.lang.Object get():42:42 -> get",
          "    6:6:java.lang.String get():46:46 -> get",
          "com.android.tools.r8.retrace.stacksamples.MethodWithOverloadStackSampleRetraceTest$Supplier"
              + " -> com.android.tools.r8.retrace.stacksamples.c:",
          "# {\"id\":\"sourceFile\",\"fileName\":\"MethodWithOverloadStackSampleRetraceTest.java\"}",
          "    1:4:void <init>():42:42 -> <init>");
    } else {
      return StringUtils.joinLines(
          "com.android.tools.r8.retrace.stacksamples.MethodWithOverloadStackSampleRetraceTest$Main"
              + " -> com.android.tools.r8.retrace.stacksamples.a:",
          "# {\"id\":\"sourceFile\",\"fileName\":\"MethodWithOverloadStackSampleRetraceTest.java\"}",
          "    1:2:void main(java.lang.String[]):45:46 -> main",
          "com.android.tools.r8.retrace.stacksamples.MethodWithOverloadStackSampleRetraceTest$StringSupplier"
              + " -> com.android.tools.r8.retrace.stacksamples.b:",
          "# {\"id\":\"sourceFile\",\"fileName\":\"MethodWithOverloadStackSampleRetraceTest.java\"}",
          "    1:4:void <init>():42:42 -> <init>",
          "    1:5:java.lang.Object get():42:42 -> a",
          "    1:3:java.lang.String get():46:46 -> b",
          "com.android.tools.r8.retrace.stacksamples.MethodWithOverloadStackSampleRetraceTest$Supplier"
              + " -> com.android.tools.r8.retrace.stacksamples.c:",
          "# {\"id\":\"sourceFile\",\"fileName\":\"MethodWithOverloadStackSampleRetraceTest.java\"}",
          "    1:4:void <init>():42:42 -> <init>",
          "    java.lang.Object get() -> a");
    }
  }

  @Override
  String getExpectedOutput() {
    return StringUtils.lines("foo", "foo");
  }

  @Override
  void inspectCode(CodeInspector inspector) {
    if (keep) {
      return;
    }
    assertEquals(3, inspector.allClasses().size());

    // Verify `Object StringSupplier.get()` and `String StringSupplier.get()` are renamed to a() and
    // b(), respectively.
    ClassSubject stringSupplierClass = inspector.clazz(StringSupplier.class);
    assertThat(stringSupplierClass, isPresent());
    assertEquals(obfuscatedClassName, stringSupplierClass.getFinalName());
    assertEquals(2, stringSupplierClass.virtualMethods().size());

    MethodSubject getObjectMethod = stringSupplierClass.method("java.lang.Object", "get");
    assertThat(getObjectMethod, isPresent());
    assertEquals(obfuscatedMethodNameObject, getObjectMethod.getFinalName());

    MethodSubject getStringMethod = stringSupplierClass.method("java.lang.String", "get");
    assertThat(getStringMethod, isPresent());
    assertEquals(obfuscatedMethodNameString, getStringMethod.getFinalName());
  }

  @Override
  void testRetrace(R8TestCompileResultBase<?> compileResult) {
    if (keep) {
      // Expected: `b.get` should retrace to {`Object StringSupplier.get()`,
      // `String StringSupplier.get()`}, which can be represented as just `StringSupplier.get()`.
      Set<MethodReference> retraceResult =
          getRetraceMethodElements(
                  Reference.classFromTypeName(obfuscatedClassName), "get", compileResult)
              .stream()
              .map(
                  retraceMethodElement ->
                      retraceMethodElement.getRetracedMethod().asKnown().getMethodReference())
              .collect(Collectors.toSet());
      assertEquals(
          ImmutableSet.of(
              Reference.method(
                  Reference.classFromClass(StringSupplier.class),
                  "get",
                  emptyList(),
                  Reference.classFromClass(Object.class)),
              Reference.method(
                  Reference.classFromClass(StringSupplier.class),
                  "get",
                  emptyList(),
                  Reference.classFromClass(String.class))),
          retraceResult);
    } else {
      // Expected: `b.b` should retrace to `String StringSupplier.get()`.
      {
        RetraceMethodElement retraceMethodElement =
            getSingleRetraceMethodElement(
                Reference.classFromTypeName(obfuscatedClassName),
                obfuscatedMethodNameString,
                compileResult);
        assertEquals(
            Reference.method(
                Reference.classFromClass(StringSupplier.class),
                "get",
                emptyList(),
                Reference.classFromClass(String.class)),
            retraceMethodElement.getRetracedMethod().asKnown().getMethodReference());
      }

      // Expected: `b.a` should retrace to `Object StringSupplier.get()`.
      {
        RetraceMethodElement retraceMethodElement =
            getSingleRetraceMethodElement(
                Reference.classFromTypeName(obfuscatedClassName),
                obfuscatedMethodNameObject,
                compileResult);
        assertEquals(
            Reference.method(
                Reference.classFromClass(StringSupplier.class),
                "get",
                emptyList(),
                Reference.classFromClass(Object.class)),
            retraceMethodElement.getRetracedMethod().asKnown().getMethodReference());
      }
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new StringSupplier().get());
      System.out.println(((Supplier<?>) new StringSupplier()).get());
    }
  }

  abstract static class Supplier<T> {

    abstract T get();
  }

  static class StringSupplier extends Supplier<String> {

    @Override
    public String get() {
      return "foo";
    }
  }
}
