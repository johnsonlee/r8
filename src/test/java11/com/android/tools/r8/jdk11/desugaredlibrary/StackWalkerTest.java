// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StackWalkerTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("[main]", "[frame2, frame1, main]", "frameFirst");
  private static final String EXPECTED_OUTPUT_R8 = StringUtils.lines("[main]", "[main]", "main");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimesStartingFromExcluding(Version.V13_0_0)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public StackWalkerTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testReference() throws Exception {
    Assume.assumeTrue(
        "Run only once",
        compilationSpecification == D8_L8DEBUG && libraryDesugaringSpecification == JDK11);
    // No desugared library, this should work.
    testForD8()
        .setMinApi(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Example.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testDesugaredLibrary() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(Example.class)
        .overrideLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.MAIN))
        // Missing class java.lang.StackWalker$StackFrame.
        .addOptionsModification(opt -> opt.ignoreMissingClasses = true)
        .run(parameters.getRuntime(), Example.class)
        .assertSuccessWithOutput(
            compilationSpecification.isProgramShrink() ? EXPECTED_OUTPUT_R8 : EXPECTED_OUTPUT);
  }

  public static class Example {
    public static void main(String[] args) {
      List<String> OneFrameStack =
          StackWalker.getInstance()
              .walk(s -> s.limit(7).map(StackFrame::getMethodName).collect(Collectors.toList()));
      System.out.println(OneFrameStack);
      frame1();
      frameFirst();
    }

    public static void frame1() {
      frame2();
    }

    public static void frame2() {
      List<String> ThreeFrameStack =
          StackWalker.getInstance()
              .walk(s -> s.limit(7).map(StackFrame::getMethodName).collect(Collectors.toList()));
      System.out.println(ThreeFrameStack);
    }

    public static void frameFirst() {
      Optional<StackFrame> walk = StackWalker.getInstance().walk(s -> s.findFirst());
      walk.ifPresent(f -> System.out.println(f.getMethodName()));
    }
  }
}
