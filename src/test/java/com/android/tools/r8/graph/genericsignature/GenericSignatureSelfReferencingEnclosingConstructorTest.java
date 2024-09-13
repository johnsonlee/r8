// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.genericsignature;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
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
  private static final String EXPECTED_OUTPUT_R8_DEX = StringUtils.lines("public " + CONSTRUCTOR);

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
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getProgramClassFileDataWithSelfReferencingEnclosingMethod())
        .addKeepMainRule(TestClass.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepRules("-keep class **$1A { <methods>; }")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(
            parameters.isDexRuntime() ? EXPECTED_OUTPUT_R8_DEX : EXPECTED_OUTPUT);
  }

  @Test
  public void testR8NoKeep() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getProgramClassFileDataWithSelfReferencingEnclosingMethod())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("null");
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
