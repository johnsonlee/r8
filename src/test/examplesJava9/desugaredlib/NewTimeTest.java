// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlib;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NewTimeTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT = StringUtils.lines("UTC", "-31557014135553600");
  private static final Class<?> MAIN_CLASS = NewTimeMain.class;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public NewTimeTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(MAIN_CLASS)
        .compile()
        .withArt6Plus64BitsLib()
        .inspect(this::assertCalls)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void assertCalls(CodeInspector codeInspector) {
    codeInspector
        .clazz(MAIN_CLASS)
        .uniqueMethodWithOriginalName("main")
        .streamInstructions()
        .filter(InstructionSubject::isInvoke)
        .forEach(this::assertCorrectInvoke);
  }

  private void assertCorrectInvoke(InstructionSubject invoke) {
    String name = invoke.getMethod().getName().toString();
    if (name.equals("tickMillis")) {
      if (parameters.getApiLevel().isLessThan(AndroidApiLevel.O)) {
        assertEquals("j$.time.Clock", invoke.getMethod().getHolderType().toString());
      } else {
        assertEquals("j$.time.DesugarClock", invoke.getMethod().getHolderType().toString());
      }
    }
    if (name.equals("toEpochSecond")) {
      if (parameters.getApiLevel().isLessThan(AndroidApiLevel.O)) {
        assertEquals("j$.time.OffsetTime", invoke.getMethod().getHolderType().toString());
      } else {
        assertEquals("j$.time.DesugarOffsetTime", invoke.getMethod().getHolderType().toString());
      }
    }
  }

  public static class NewTimeMain {

    public static void main(String[] args) {
      Clock utc = Clock.tickMillis(ZoneId.of("UTC"));
      System.out.println(utc.getZone());
      OffsetTime ot = OffsetTime.of(LocalTime.NOON, ZoneOffset.UTC);
      System.out.println(ot.toEpochSecond(LocalDate.MIN));
    }
  }
}
