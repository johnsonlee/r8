// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_DESCRIPTOR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DateTimeFormatterStandaloneMonthTest extends DesugaredLibraryTestBase {

  private static final String expectedOutputDesugaredLibJdk8 =
      StringUtils.lines(
          "1 - 1", "2 - 2", "3 - 3", "4 - 4", "5 - 5", "6 - 6", "7 - 7", "8 - 8", "9 - 9",
          "10 - 10", "11 - 11", "12 - 12");
  private static final String expectedOutput =
      StringUtils.lines(
          "Jan - January",
          "Feb - February",
          "Mar - March",
          "Apr - April",
          "May - May",
          "Jun - June",
          "Jul - July",
          "Aug - August",
          "Sep - September",
          "Oct - October",
          "Nov - November",
          "Dec - December");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DateTimeFormatterStandaloneMonthTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testFormatter() throws Throwable {
    SingleTestRunResult<?> run =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(getClass())
            .addKeepMainRule(TestClass.class)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccess();
    if (apiLevelWithJavaTime(parameters)
        || libraryDesugaringSpecification.getDescriptor() == JDK11_DESCRIPTOR) {
      run.assertSuccessWithOutput(expectedOutput);
    } else {
      run.assertSuccessWithOutput(expectedOutputDesugaredLibJdk8);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
      DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("LLL");
      DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("LLLL");
      DateTimeFormatter formatter =
          builder.append(formatter1).appendLiteral(" - ").append(formatter2).toFormatter();
      for (int month = 1; month <= 12; month++) {
        LocalDateTime dateTime = LocalDateTime.of(2024, month, 1, 0, 0);
        String str = dateTime.format(formatter);
        System.out.println(str);
      }
    }
  }
}
