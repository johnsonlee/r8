// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.desugaredlibrary.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstantSourceTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "86400000",
          "86400",
          "86400000",
          "86400000",
          "86400",
          "86400000",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public InstantSourceTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    Assume.assumeTrue(parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U));
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class Main {

    public static class InstantSourceImpl implements InstantSource {

      @Override
      public Instant instant() {
        return Instant.EPOCH;
      }
    }

    public static void main(String[] args) {
      Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault());
      testInstantSource(clock);
      testInstantSource(new InstantSourceImpl());
      testInstantSource(InstantSource.offset(clock, Duration.ofDays(1)));
      testInstantSource(InstantSource.offset(new InstantSourceImpl(), Duration.ofDays(1)));
      testInstantSource(InstantSource.tick(clock, Duration.ofDays(1)));
      testInstantSource(InstantSource.tick(new InstantSourceImpl(), Duration.ofDays(1)));
      testInstantSource(InstantSource.fixed(Instant.EPOCH));
    }

    @NeverInline
    public static void testInstantSource(InstantSource is) {
      System.out.println(is.millis());
      System.out.println(is.instant().getEpochSecond());
      System.out.println(is.withZone(ZoneId.systemDefault()).millis());
    }
  }
}
