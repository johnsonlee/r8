// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.stacksamples;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static com.android.tools.r8.utils.StringUtils.UNIX_LINE_SEPARATOR;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceClassResult;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.retrace.RetraceMethodResult;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public abstract class StackSampleRetraceTestBase extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  void runTest(ThrowableConsumer<R8TestBuilder<?, ?, ?>> testBuilderConsumer) throws Exception {
    testForR8(parameters)
        .addKeepClassRulesWithAllowObfuscation(getMainClass())
        .addKeepRules(
            "-keepclassmembers class " + getMainClass().getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .addHorizontallyMergedClassesInspector(this::inspectHorizontallyMergedClasses)
        .apply(testBuilderConsumer)
        .compile()
        .inspect(this::inspectCode)
        .applyIf(parameters.isDexRuntime(), this::inspectMap)
        .apply(this::testRetrace)
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpectedOutput());
  }

  abstract Class<?> getMainClass();

  abstract String getExpectedMap();

  abstract String getExpectedOutput();

  abstract void inspectCode(CodeInspector inspector);

  void inspectHorizontallyMergedClasses(HorizontallyMergedClassesInspector inspector) {}

  abstract void testRetrace(R8TestCompileResultBase<?> compileResult) throws Exception;

  private void inspectMap(R8TestCompileResultBase<?> compileResult) {
    assertEquals(getExpectedMap(), getMapWithoutHeader(compileResult));
  }

  RetraceMethodElement getSingleRetraceMethodElement(
      ClassReference obfuscatedClassReference,
      String obfuscatedMethodName,
      R8TestCompileResultBase<?> compileResult) {
    List<RetraceMethodElement> retraceMethodElements =
        getRetraceMethodElements(obfuscatedClassReference, obfuscatedMethodName, compileResult);
    assertEquals(1, retraceMethodElements.size());
    return retraceMethodElements.get(0);
  }

  List<RetraceMethodElement> getRetraceMethodElements(
      ClassReference obfuscatedClassReference,
      String obfuscatedMethodName,
      R8TestCompileResultBase<?> compileResult) {
    TestDiagnosticMessages diagnostics = new TestDiagnosticMessagesImpl();
    Retracer retracer =
        Retracer.createDefault(
            ProguardMapProducer.fromString(compileResult.getProguardMap()), diagnostics);
    RetraceClassResult retraceClassResult = retracer.retraceClass(obfuscatedClassReference);
    RetraceMethodResult retraceMethodResult = retraceClassResult.lookupMethod(obfuscatedMethodName);
    List<RetraceMethodElement> retraceMethodElements =
        retraceMethodResult.stream().collect(Collectors.toList());
    diagnostics.assertNoMessages();
    return retraceMethodElements;
  }

  static int getFirstLineNumber(Class<?> clazz) {
    return getFirstLineNumber(ToolHelper.getClassAsBytes(clazz));
  }

  static int getFirstLineNumber(byte[] classFileData) {
    ClassReader reader = new ClassReader(classFileData);
    IntBox result = new IntBox(Integer.MAX_VALUE);
    reader.accept(
        new ClassVisitor(ASM_VERSION) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor subvisitor =
                super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(ASM_VERSION, subvisitor) {
              @Override
              public void visitLineNumber(int line, Label start) {
                super.visitLineNumber(line, start);
                result.setMin(line);
              }
            };
          }
        },
        0);
    return result.get();
  }

  static String getMapWithoutHeader(R8TestCompileResultBase<?> compileResult) {
    BooleanBox pastHeader = new BooleanBox();
    return StringUtils.splitLines(compileResult.getProguardMap()).stream()
        .filter(
            line -> {
              if (line.startsWith("#") && pastHeader.isFalse()) {
                return false;
              } else {
                pastHeader.set();
                return true;
              }
            })
        .collect(Collectors.joining(UNIX_LINE_SEPARATOR));
  }
}
