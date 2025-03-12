// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesJava11Test extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT = StringUtils.lines("content:this", "content:this");
  private static final Class<?> MAIN_CLASS = FilesMain.class;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public FilesJava11Test(
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

  private void assertCalls(CodeInspector i) {
    i.clazz(MAIN_CLASS)
        .uniqueMethodWithOriginalName("main")
        .streamInstructions()
        .filter(InstructionSubject::isInvokeStatic)
        .forEach(
            invokeStatic -> {
              String methodName = invokeStatic.getMethod().getName().toString();
              if (methodName.equals("readString") || methodName.equals("writeString")) {
                if (parameters.getApiLevel().isLessThan(AndroidApiLevel.O)) {
                  assertEquals(
                      invokeStatic.getMethod().getHolderType().toSourceString(),
                      "j$.nio.file.Files");
                } else {
                  assertEquals(
                      invokeStatic.getMethod().getHolderType().toSourceString(),
                      "j$.nio.file.DesugarFiles");
                }
              }
            });
  }

  public static class FilesMain {

    public static void main(String[] args) throws IOException {
      Path temp = Files.createTempFile("temp", ".txt");
      Files.writeString(temp, "content:", StandardOpenOption.WRITE);
      Files.writeString(temp, "this", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
      System.out.println(Files.readString(temp));
      System.out.println(Files.readString(temp, StandardCharsets.UTF_8));
      Files.deleteIfExists(temp);
    }
  }
}
