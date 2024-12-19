// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DayTest extends DesugaredLibraryTestBase {
  private static final String MISSING_STANDALONE = StringUtils.lines("1", "1", "1234567");
  private static final String SIMPLIFIED_CHINESE_NARROW_DAY_JDK8 =
      StringUtils.lines("星期一", "周一", "星星星星星星星");

  private static final String UK_EXPECTED_RESULT_JDK8 =
      StringUtils.lines("Monday", "Mon", "1234567");
  private static final String UK_EXPECTED_RESULT = StringUtils.lines("Monday", "Mon", "MTWTFSS");

  private static final String SIMPLIFIED_CHINESE_EXPECTED_RESULT_JDK8 =
      StringUtils.lines("星期一", "星期一", "一二三四五六日");
  private static final String SIMPLIFIED_CHINESE_EXPECTED_RESULT =
      StringUtils.lines("星期一", "周一", "一二三四五六日");

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

  public DayTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getUKStandaloneExpectedResult() {
    if (parameters.isCfRuntime() && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK9)) {
      return UK_EXPECTED_RESULT_JDK8;
    }
    if (parameters.isDexRuntime()
        && libraryDesugaringSpecification.hasTimeDesugaring(parameters)
        && libraryDesugaringSpecification == JDK8) {
      return MISSING_STANDALONE;
    }
    return UK_EXPECTED_RESULT;
  }

  private String getSimplifiedChineseStandaloneExpectedResult() {
    if (parameters.isCfRuntime() && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK9)) {
      return SIMPLIFIED_CHINESE_EXPECTED_RESULT_JDK8;
    }
    if (parameters.isDexRuntime()
        && libraryDesugaringSpecification.hasTimeDesugaring(parameters)
        && libraryDesugaringSpecification == JDK8) {
      return MISSING_STANDALONE;
    }
    return SIMPLIFIED_CHINESE_EXPECTED_RESULT;
  }

  private String getSimplifiedChineseExpectedResult() {
    if (parameters.isCfRuntime() && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK9)) {
      return SIMPLIFIED_CHINESE_EXPECTED_RESULT_JDK8;
    }
    if (parameters.isDexRuntime()
        && libraryDesugaringSpecification.hasTimeDesugaring(parameters)
        && libraryDesugaringSpecification == JDK8) {
      return SIMPLIFIED_CHINESE_NARROW_DAY_JDK8;
    }
    return SIMPLIFIED_CHINESE_EXPECTED_RESULT;
  }

  private String getExpectedResult() {
    return getUKStandaloneExpectedResult()
        + UK_EXPECTED_RESULT
        + getSimplifiedChineseStandaloneExpectedResult()
        + getSimplifiedChineseExpectedResult();
  }

  @Test
  public void testDay() throws Exception {
    if (parameters.isCfRuntime()) {
      Assume.assumeFalse(
          "Missing data for Locale SIMPLIFIED_CHINESE on Windows", ToolHelper.isWindows());
      testForJvm(parameters)
          .addInnerClasses(DayTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(getExpectedResult());
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  static class Main {

    public static void testStandalone(Locale locale) {
      System.out.println(DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL_STANDALONE, locale));
      System.out.println(DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT_STANDALONE, locale));
      System.out.println(
          DayOfWeek.MONDAY.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
              + DayOfWeek.TUESDAY.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
              + DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
              + DayOfWeek.THURSDAY.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
              + DayOfWeek.FRIDAY.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
              + DayOfWeek.SATURDAY.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
              + DayOfWeek.SUNDAY.getDisplayName(TextStyle.NARROW_STANDALONE, locale));
    }

    public static void test(Locale locale) {
      System.out.println(DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, locale));
      System.out.println(DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, locale));
      System.out.println(
          DayOfWeek.MONDAY.getDisplayName(TextStyle.NARROW, locale)
              + DayOfWeek.TUESDAY.getDisplayName(TextStyle.NARROW, locale)
              + DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.NARROW, locale)
              + DayOfWeek.THURSDAY.getDisplayName(TextStyle.NARROW, locale)
              + DayOfWeek.FRIDAY.getDisplayName(TextStyle.NARROW, locale)
              + DayOfWeek.SATURDAY.getDisplayName(TextStyle.NARROW, locale)
              + DayOfWeek.SUNDAY.getDisplayName(TextStyle.NARROW, locale));
    }

    public static void main(String[] args) {
      testStandalone(Locale.UK);
      test(Locale.UK);
      testStandalone(Locale.SIMPLIFIED_CHINESE);
      test(Locale.SIMPLIFIED_CHINESE);
    }
  }
}
