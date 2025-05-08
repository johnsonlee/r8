// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlib;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InputStreamTransferToTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("Hello World!", "Hello World!", "Hello World!", "$Hello World!");
  private static final Class<?> MAIN_CLASS = TestClass.class;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build(),
        ImmutableList.of(JDK11_PATH),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public InputStreamTransferToTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Exception {
    // The method is not present on JDK8 so if we don't desugar that won't work.
    Assume.assumeFalse(
        parameters.isCfRuntime(CfVm.JDK8)
            && !libraryDesugaringSpecification.hasNioFileDesugaring(parameters)
            && compilationSpecification.isCfToCf());
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(MAIN_CLASS)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  /**
   * See b/248200357, in T an override or transferTo was introduced in android.jar changing
   * resolution.
   */
  @Test
  public void testWithAndroidJarFromT() throws Exception {
    // The method is not present on JDK8 so if we don't desugar that won't work.
    Assume.assumeFalse(
        parameters.isCfRuntime(CfVm.JDK8)
            && !libraryDesugaringSpecification.hasNioFileDesugaring(parameters)
            && compilationSpecification.isCfToCf());
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(MAIN_CLASS)
        .overrideLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class MyInputStream extends ByteArrayInputStream {

    public MyInputStream(byte[] buf) {
      super(buf);
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
      out.write((int) '$');
      return super.transferTo(out);
    }
  }

  public static class TestClass {
    public static void main(String[] args) throws IOException {
      transferTo();
      transferToOverride();
    }

    public static void transferTo() throws IOException {
      String initialString = "Hello World!";
      System.out.println(initialString);

      try (InputStream inputStream = new ByteArrayInputStream(initialString.getBytes());
          ByteArrayOutputStream targetStream = new ByteArrayOutputStream()) {
        inputStream.transferTo(targetStream);
        String copied = new String(targetStream.toByteArray());
        System.out.println(copied);
      }
    }

    public static void transferToOverride() throws IOException {
      String initialString = "Hello World!";
      System.out.println(initialString);

      try (MyInputStream inputStream = new MyInputStream(initialString.getBytes());
          ByteArrayOutputStream targetStream = new ByteArrayOutputStream()) {
        inputStream.transferTo(targetStream);
        String copied = new String(targetStream.toByteArray());
        System.out.println(copied);
      }
    }
  }
}
