// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapMultiTest extends DesugaredLibraryTestBase {

  private static final String[] INVALID_EXPECTED_RESULT = {
    "class java.lang.NoSuchMethodError", "class java.lang.NoSuchMethodError"
  };
  private static final String[] EXPECTED_RESULT = {"[2.02, 4.04]", "[1, 2, 4, 5]"};

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        // Note that JDK8 is completely broken here.
        ImmutableList.of(JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public MapMultiTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  public String[] getExpectedResult(boolean desugLib) {
    if (parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V14_0_0)) {
      if (desugLib && parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.N)) {
        // Unfortunately these methods are in android.jar from 14 but not in desugared library
        // and are not supported.
        return INVALID_EXPECTED_RESULT;
      }
      return EXPECTED_RESULT;
    }
    return INVALID_EXPECTED_RESULT;
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
        .assertSuccessWithOutputLines(getExpectedResult(false));
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
        .assertSuccessWithOutputLines(getExpectedResult(true));
  }

  static class Executor {

    public static void main(String[] args) {
      run(External::intMapMulti);
      run(External::doubleMapMulti);
    }

    private static void run(Runnable r) {
      try {
        r.run();
      } catch (Throwable t) {
        System.out.println(t.getClass());
      }
    }

    static class External {

      @NeverInline
      static void intMapMulti() {
        List<Integer> integers = Arrays.asList(1, 2, 3, 4, 5);
        double percentage = .01;
        List<Double> evenDoubles =
            integers.stream()
                .<Double>mapMulti(
                    (integer, consumer) -> {
                      if (integer % 2 == 0) {
                        consumer.accept((double) integer * (1 + percentage));
                      }
                    })
                .toList();
        System.out.println(evenDoubles);
      }

      @NeverInline
      static void doubleMapMulti() {
        List<Double> doubles = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        double percentage = .01;
        List<Integer> non3Int =
            doubles.stream()
                .<Integer>mapMulti(
                    (doubl, consumer) -> {
                      if (doubl != 3.0) {
                        consumer.accept((int) Math.floor(doubl * (1 + percentage)));
                      }
                    })
                .toList();
        System.out.println(non3Int);
      }
    }
  }
}
