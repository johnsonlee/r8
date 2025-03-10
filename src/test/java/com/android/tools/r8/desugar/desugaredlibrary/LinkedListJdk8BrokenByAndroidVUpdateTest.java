// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LinkedListJdk8BrokenByAndroidVUpdateTest extends DesugaredLibraryTestBase {

  private static final String[] JDK8_EXPECTED_RESULT = {"class java.lang.NoSuchMethodError"};
  private static final String[] EXPECTED_RESULT = {"[1, 2, 3]"};

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        ImmutableList.of(JDK8, JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public LinkedListJdk8BrokenByAndroidVUpdateTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  public String[] getExpectedResult() {
    if (libraryDesugaringSpecification == JDK8
        && parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
      return JDK8_EXPECTED_RESULT;
    }
    return EXPECTED_RESULT;
  }

  @Test
  public void testReference() throws Exception {
    Assume.assumeTrue(
        "Run only once",
        libraryDesugaringSpecification == JDK11 && compilationSpecification == D8_L8DEBUG);
    testForD8()
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines(getExpectedResult());
  }

  @Test
  public void testDesugaredLib() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .enableInliningAnnotations()
        .allowDiagnosticWarningMessages(parameters.getApiLevel().equals(AndroidApiLevel.MAIN))
        .overrideLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.MAIN))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines(getExpectedResult());
  }

  static class Executor {

    public static void main(String[] args) {
      run(Executor::ll);
    }

    @NeverInline
    private static void ll() {
      LinkedList<Integer> ll = new LinkedList<>();
      ll.addFirst(2);
      ll.addFirst(1);
      ll.addLast(3);
      System.out.println(ll);
    }

    @NeverInline
    private static void run(Runnable r) {
      try {
        r.run();
      } catch (Throwable t) {
        System.out.println(t.getClass());
      }
    }
  }
}
