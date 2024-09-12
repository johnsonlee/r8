// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.genericsignature;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureSelfReferencingEnclosingConstructorTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String CONSTRUCTOR =
      TestClass.class.getTypeName() + "$1A(" + TestClass.class.getTypeName() + ")";
  private static final String EXPECTED_OUTPUT = StringUtils.lines(CONSTRUCTOR);

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getProgramClassFileDataWithSelfReferencingEnclosingMethod())
        // .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getProgramClassFileDataWithSelfReferencingEnclosingMethod())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/366140351): This should not crash the compiler.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(TestClass.class)
                .addProgramClassFileData(
                    getProgramClassFileDataWithSelfReferencingEnclosingMethod())
                .addKeepMainRule(TestClass.class)
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      assertEquals(1, diagnostics.getErrors().size());
                      assertTrue(diagnostics.getErrors().get(0) instanceof ExceptionDiagnostic);
                      assertTrue(
                          ((ExceptionDiagnostic) diagnostics.getErrors().get(0)).getCause()
                              instanceof StackOverflowError);
                    }));
  }

  // Change the EnclosingMethod attribute for the local class A to be a constructor of A itself.
  private byte[] getProgramClassFileDataWithSelfReferencingEnclosingMethod() throws IOException {
    Iterator<Path> innerClassesIterator =
        ToolHelper.getClassFilesForInnerClasses(TestClass.class).iterator();
    Path innerClass = innerClassesIterator.next();
    assertFalse(innerClassesIterator.hasNext());
    return transformer(
            innerClass, Reference.classFromBinaryName(binaryName(TestClass.class) + "$1A"))
        .rewriteEnclosingMethod(
            binaryName(TestClass.class) + "$1A", "<init>", "(" + descriptor(TestClass.class) + ")V")
        .transform();
  }

  static class TestClass {
    TestClass() {
      class A {
        public void m() {
          System.out.println(A.class.getEnclosingConstructor());
        }
      }
      new A().m();
    }

    public static void main(String[] args) {
      new TestClass();
    }
  }
}
