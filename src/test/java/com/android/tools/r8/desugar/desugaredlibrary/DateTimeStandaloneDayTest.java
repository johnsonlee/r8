// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DateTimeStandaloneDayTest extends DesugaredLibraryTestBase {

  // Standalone weekday names is only supported inn JDK-11 desugared library, see b/362277530.
  private static final String EXPECTED_OUTPUT_TO_JDK8 =
      StringUtils.lines("1", "2", "3", "4", "5", "6", "7");
  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

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

  public DateTimeStandaloneDayTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testStandaloneDay() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            libraryDesugaringSpecification == JDK11
                || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT_TO_JDK8));
  }

  public static void main(String[] args) {
    TestClass.main(args);
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US));
      System.out.println(DayOfWeek.TUESDAY.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US));
      System.out.println(DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US));
      System.out.println(DayOfWeek.THURSDAY.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US));
      System.out.println(DayOfWeek.FRIDAY.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US));
      System.out.println(DayOfWeek.SATURDAY.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US));
      System.out.println(DayOfWeek.SUNDAY.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US));
    }
  }
}
