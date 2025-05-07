// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlib;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CollectionOfTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT_BACKPORT = StringUtils.lines("false", "false");
  private static final String EXPECTED_OUTPUT_CORRECT = StringUtils.lines("npe", "npe");
  private static final Class<?> MAIN_CLASS = CollectionOfMain.class;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public CollectionOfTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getExpectedOutput() {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.R)) {
      return EXPECTED_OUTPUT_CORRECT;
    }
    return EXPECTED_OUTPUT_BACKPORT;
  }

  @Test
  public void testCollectionOf() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(MAIN_CLASS)
        .compile()
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testCollectionOfReference() throws Throwable {
    Assume.assumeTrue(
        "Run only once",
        libraryDesugaringSpecification == JDK8 && compilationSpecification == D8_L8DEBUG);
    testForD8()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  public static class CollectionOfMain {

    public static void main(String[] args) {
      try {
        System.out.println(Set.of("one").contains(null));
      } catch (NullPointerException npe) {
        System.out.println("npe");
      }
      try {
        System.out.println(List.of("one").contains(null));
      } catch (NullPointerException npe) {
        System.out.println("npe");
      }
    }
  }
}
