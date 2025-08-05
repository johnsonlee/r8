// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DayOfWeekTest extends DesugaredLibraryTestBase {
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DayOfWeekTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testDay() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addInnerClasses(DayOfWeekTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines("6", "7");
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            // TODO(b/432689492): Fix non standalone names.
            libraryDesugaringSpecification == LibraryDesugaringSpecification.JDK8
                || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O),
            b -> b.assertSuccessWithOutputLines("6", "7"),
            SingleTestRunResult::assertFailure);
  }

  static class Main {

    public static void main(String[] args) {
      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
      TemporalAccessor access1 = formatter.parse("Sun, 06 Jul 2025 10:07:45 GMT");
      System.out.println(access1.get(IsoFields.DAY_OF_QUARTER));
      TemporalAccessor access2 = formatter.parse("Mon, 07 Jul 2025 10:07:45 GMT");
      System.out.println(access2.get(IsoFields.DAY_OF_QUARTER));
    }
  }
}
