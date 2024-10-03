// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.partial.pkg1.A1;
import com.android.tools.r8.partial.pkg1.A2;
import com.android.tools.r8.partial.pkg1.subpkg.B;
import com.android.tools.r8.partial.pkg2.C1;
import com.android.tools.r8.partial.pkg2.C2;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationBasicPreviewPatternsTest extends TestBase {

  private static String PKG1 = getPackageName(A1.class);
  private static String SUBPKG = getPackageName(B.class);
  private static String PKG2 = getPackageName(C2.class);

  private static String getPackageName(Class<?> clazz) {
    return clazz.getTypeName().substring(0, clazz.getTypeName().lastIndexOf('.'));
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  // Test with min API level 24.
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntime(DexVm.Version.V7_0_0)
        .withApiLevel(AndroidApiLevel.N)
        .build();
  }

  private static final List<Class<?>> ALL_CLASSES =
      ImmutableList.of(A1.class, A2.class, B.class, C1.class, C2.class, Main.class);
  private static final String[] ALL_TYPE_NAMES =
      new String[] {
        A1.class.getTypeName(),
        A2.class.getTypeName(),
        B.class.getTypeName(),
        C1.class.getTypeName(),
        C2.class.getTypeName()
      };

  @Test
  public void pkg1AndSubpackagesCompiledWithR8() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ALL_CLASSES)
        .setR8PartialConfiguration(builder -> builder.addJavaTypeIncludePattern(PKG1 + ".**"))
        .compile()
        .inspectD8Input(
            inspector ->
                assertTrue(inspector.hasExactlyProgramClasses(C1.class, C2.class, Main.class)))
        .inspectR8Input(
            inspector ->
                assertTrue(inspector.hasExactlyProgramClasses(A1.class, A2.class, B.class)))
        .inspect(
            inspector ->
                assertTrue(inspector.hasExactlyProgramClasses(C1.class, C2.class, Main.class)))
        .run(parameters.getRuntime(), Main.class, ALL_TYPE_NAMES)
        .assertSuccessWithOutputLines(
            "Not instantiated",
            "Not instantiated",
            "Not instantiated",
            "Instantiated",
            "Instantiated");
  }

  @Test
  public void pkg1CompiledWithR8() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ALL_CLASSES)
        .setR8PartialConfiguration(builder -> builder.addJavaTypeIncludePattern(PKG1 + ".*"))
        .compile()
        .inspectD8Input(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(B.class, C1.class, C2.class, Main.class)))
        .inspectR8Input(
            inspector -> assertTrue(inspector.hasExactlyProgramClasses(A1.class, A2.class)))
        .inspect(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(B.class, C1.class, C2.class, Main.class)))
        .run(parameters.getRuntime(), Main.class, ALL_TYPE_NAMES)
        .assertSuccessWithOutputLines(
            "Not instantiated", "Not instantiated", "Instantiated", "Instantiated", "Instantiated");
  }

  @Test
  public void pkg1AndSubpackagesExcludeSubPkgCompiledWithR8() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ALL_CLASSES)
        .setR8PartialConfiguration(
            builder ->
                builder
                    .addJavaTypeIncludePattern(PKG1 + ".**")
                    .addJavaTypeExcludePattern(SUBPKG + ".*"))
        .compile()
        .inspectD8Input(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(C1.class, C2.class, B.class, Main.class)))
        .inspectR8Input(
            inspector -> assertTrue(inspector.hasExactlyProgramClasses(A1.class, A2.class)))
        .inspect(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(B.class, C1.class, C2.class, Main.class)))
        .run(parameters.getRuntime(), Main.class, ALL_TYPE_NAMES)
        .assertSuccessWithOutputLines(
            "Not instantiated", "Not instantiated", "Instantiated", "Instantiated", "Instantiated");
  }

  @Test
  public void pkg1AndSubpackagesExcludeAPrefixCompiledWithR8() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ALL_CLASSES)
        .setR8PartialConfiguration(
            builder ->
                builder
                    .addJavaTypeIncludePattern(PKG1 + ".**")
                    .addJavaTypeExcludePattern(PKG1 + ".A*"))
        .compile()
        .inspectD8Input(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(
                        A1.class, A2.class, C1.class, C2.class, Main.class)))
        .inspectR8Input(inspector -> assertTrue(inspector.hasExactlyProgramClasses(B.class)))
        .inspect(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(
                        A1.class, A2.class, C1.class, C2.class, Main.class)))
        .run(parameters.getRuntime(), Main.class, ALL_TYPE_NAMES)
        .assertSuccessWithOutputLines(
            "Instantiated", "Instantiated", "Not instantiated", "Instantiated", "Instantiated");
  }

  @Test
  public void pkg1AndSubpackagesExcludeA1AndA2CompiledWithR8() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ALL_CLASSES)
        .setR8PartialConfiguration(
            builder ->
                builder
                    .addJavaTypeIncludePattern(PKG1 + ".**")
                    .addJavaTypeExcludePattern(PKG1 + ".A1")
                    .addJavaTypeExcludePattern(PKG1 + ".A2"))
        .compile()
        .inspectD8Input(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(
                        A1.class, A2.class, C1.class, C2.class, Main.class)))
        .inspectR8Input(inspector -> assertTrue(inspector.hasExactlyProgramClasses(B.class)))
        .inspect(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(
                        A1.class, A2.class, C1.class, C2.class, Main.class)))
        .run(parameters.getRuntime(), Main.class, ALL_TYPE_NAMES)
        .assertSuccessWithOutputLines(
            "Instantiated", "Instantiated", "Not instantiated", "Instantiated", "Instantiated");
  }

  @Test
  public void allExeptC1CompiledWithR8() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ALL_CLASSES)
        .setR8PartialConfiguration(
            builder ->
                builder
                    .addJavaTypeIncludePattern(PKG1 + ".**")
                    .addJavaTypeIncludePattern(PKG2 + ".**")
                    .addJavaTypeExcludePattern(PKG2 + ".C1"))
        .compile()
        .inspectD8Input(
            inspector -> assertTrue(inspector.hasExactlyProgramClasses(C1.class, Main.class)))
        .inspectR8Input(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(A1.class, A2.class, B.class, C2.class)))
        .inspect(inspector -> assertTrue(inspector.hasExactlyProgramClasses(C1.class, Main.class)))
        .run(parameters.getRuntime(), Main.class, ALL_TYPE_NAMES)
        .assertSuccessWithOutputLines(
            "Not instantiated",
            "Not instantiated",
            "Not instantiated",
            "Instantiated",
            "Not instantiated");
  }

  @Test
  public void allExeptA1CompiledWithR8() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ALL_CLASSES)
        .setR8PartialConfiguration(
            builder ->
                builder
                    .addJavaTypeIncludePattern(PKG1 + ".*")
                    .addJavaTypeIncludePattern(SUBPKG + ".*")
                    .addJavaTypeIncludePattern(PKG2 + ".*")
                    .addJavaTypeExcludePattern(PKG1 + ".A1"))
        .compile()
        .inspectD8Input(
            inspector -> assertTrue(inspector.hasExactlyProgramClasses(A1.class, Main.class)))
        .inspectR8Input(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(A2.class, B.class, C1.class, C2.class)))
        .inspect(inspector -> assertTrue(inspector.hasExactlyProgramClasses(A1.class, Main.class)))
        .run(parameters.getRuntime(), Main.class, ALL_TYPE_NAMES)
        .assertSuccessWithOutputLines(
            "Instantiated",
            "Not instantiated",
            "Not instantiated",
            "Not instantiated",
            "Not instantiated");
  }

  @Test
  public void allExeptBCompiledWithR8() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ALL_CLASSES)
        .setR8PartialConfiguration(
            builder ->
                builder
                    .addJavaTypeIncludePattern(PKG1 + ".**")
                    .addJavaTypeIncludePattern(PKG2 + ".**")
                    .addJavaTypeExcludePattern(SUBPKG + ".*"))
        .compile()
        .inspectD8Input(
            inspector -> assertTrue(inspector.hasExactlyProgramClasses(B.class, Main.class)))
        .inspectR8Input(
            inspector ->
                assertTrue(
                    inspector.hasExactlyProgramClasses(A1.class, A2.class, C1.class, C2.class)))
        .inspect(inspector -> assertTrue(inspector.hasExactlyProgramClasses(B.class, Main.class)))
        .run(parameters.getRuntime(), Main.class, ALL_TYPE_NAMES)
        .assertSuccessWithOutputLines(
            "Not instantiated",
            "Not instantiated",
            "Instantiated",
            "Not instantiated",
            "Not instantiated");
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      for (String arg : args) {
        try {
          Class.forName(arg);
          System.out.println("Instantiated");
        } catch (ClassNotFoundException e) {
          System.out.println("Not instantiated");
        }
      }
    }
  }
}
