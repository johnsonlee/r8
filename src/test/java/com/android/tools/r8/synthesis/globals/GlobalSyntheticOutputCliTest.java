// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.synthesis.globals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GlobalSyntheticOutputCliTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines("Hello", "all good...", "Hello again", "still good...");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.N_MR1).build();
  }

  public GlobalSyntheticOutputCliTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static String getAndroidJar() {
    return ToolHelper.getAndroidJar(AndroidApiLevel.LATEST).toString();
  }

  private String getApiLevelString() {
    return "" + parameters.getApiLevel().getLevel();
  }

  private ProcessResult forkD8(String... args) throws IOException {
    return forkD8(Arrays.asList(args));
  }

  private ProcessResult forkD8(List<String> args) throws IOException {
    ImmutableList.Builder<String> command =
        new ImmutableList.Builder<String>()
            .add(CfRuntime.getSystemRuntime().getJavaExecutable().toString())
            .add("-Dcom.android.tools.r8.enableApiOutliningAndStubbing=1")
            .add("-cp")
            .add(System.getProperty("java.class.path"))
            .add(D8.class.getName())
            .add("--min-api", getApiLevelString())
            .add("--lib", getAndroidJar())
            .addAll(args);
    ProcessBuilder processBuilder = new ProcessBuilder(command.build());
    ProcessResult result = ToolHelper.runProcess(processBuilder);
    assertEquals(result.toString(), 0, result.exitCode);
    return result;
  }

  @Test
  public void testDexIndexedZip() throws Exception {
    Path input1 = transformClass(TestClass1.class);
    Path input2 = transformClass(TestClass2.class);
    Path dexOut = temp.newFolder().toPath().resolve("out.jar");
    Path globalsOut = temp.newFolder().toPath().resolve("out.zip");
    forkD8(
        input1.toString(),
        input2.toString(),
        "--intermediate",
        "--output",
        dexOut.toString(),
        "--globals-output",
        globalsOut.toString());

    assertTrue(Files.exists(dexOut));
    assertTrue(Files.exists(globalsOut));

    Path finalOut = temp.newFolder().toPath().resolve("out.jar");
    forkD8(dexOut.toString(), "--globals", globalsOut.toString(), "--output", finalOut.toString());

    testForD8()
        .addProgramFiles(finalOut)
        .run(parameters.getRuntime(), TestClass1.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDexIndexedDir() throws Exception {
    Path input1 = transformClass(TestClass1.class);
    Path input2 = transformClass(TestClass2.class);
    Path dexOut = temp.newFolder().toPath().resolve("out.jar");
    Path globalsOut = temp.newFolder("out").toPath();
    forkD8(
        input1.toString(),
        input2.toString(),
        "--intermediate",
        "--output",
        dexOut.toString(),
        "--globals-output",
        globalsOut.toString());

    Path expectedGlobalsFile = globalsOut.resolve("classes.globals");
    assertTrue(Files.exists(dexOut));
    assertTrue(Files.isDirectory(globalsOut));
    assertTrue(Files.exists(expectedGlobalsFile));

    Path finalOut = temp.newFolder().toPath().resolve("out.jar");
    forkD8(
        dexOut.toString(),
        "--globals",
        expectedGlobalsFile.toString(),
        "--output",
        finalOut.toString());

    testForD8()
        .addProgramFiles(finalOut)
        .run(parameters.getRuntime(), TestClass1.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDexPerClassZip() throws Exception {
    Path input1 = transformClass(TestClass1.class);
    Path input2 = transformClass(TestClass2.class);
    Path dexOut = temp.newFolder().toPath().resolve("out.jar");
    Path globalsOut = temp.newFolder().toPath().resolve("out.zip");
    forkD8(
        input1.toString(),
        input2.toString(),
        "--file-per-class",
        "--output",
        dexOut.toString(),
        "--globals-output",
        globalsOut.toString());

    assertTrue(Files.exists(dexOut));
    assertTrue(Files.exists(globalsOut));

    Path finalOut = temp.newFolder().toPath().resolve("out.jar");
    forkD8(dexOut.toString(), "--globals", globalsOut.toString(), "--output", finalOut.toString());

    testForD8()
        .addProgramFiles(finalOut)
        .run(parameters.getRuntime(), TestClass1.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDexPerClassDir() throws Exception {
    Path input1 = transformClass(TestClass1.class);
    Path input2 = transformClass(TestClass2.class);
    Path dexOut = temp.newFolder().toPath().resolve("out.jar");
    Path globalsOut = temp.newFolder("out").toPath();
    forkD8(
        input1.toString(),
        input2.toString(),
        "--file-per-class",
        "--output",
        dexOut.toString(),
        "--globals-output",
        globalsOut.toString());

    assertTrue(Files.exists(dexOut));
    assertTrue(Files.isDirectory(globalsOut));

    List<Path> globalFiles =
        ImmutableList.of(
            globalsOut.resolve(binaryName(TestClass1.class) + ".globals"),
            globalsOut.resolve(binaryName(TestClass2.class) + ".globals"));
    globalFiles.forEach(f -> assertTrue(Files.exists(f)));

    Path finalOut = temp.newFolder().toPath().resolve("out.jar");
    forkD8(
        ImmutableList.<String>builder()
            .add(dexOut.toString())
            .addAll(
                ListUtils.flatMap(globalFiles, f -> ImmutableList.of("--globals", f.toString())))
            .add("--output")
            .add(finalOut.toString())
            .build());

    testForD8()
        .addProgramFiles(finalOut)
        .run(parameters.getRuntime(), TestClass1.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDexPerClassFileZip() throws Exception {
    Path input1 = transformClass(TestClass1.class);
    Path input2 = transformClass(TestClass2.class);
    Path dexOut = temp.newFolder().toPath().resolve("out.jar");
    Path globalsOut = temp.newFolder().toPath().resolve("out.zip");
    forkD8(
        input1.toString(),
        input2.toString(),
        "--file-per-class-file",
        "--output",
        dexOut.toString(),
        "--globals-output",
        globalsOut.toString());

    assertTrue(Files.exists(dexOut));
    assertTrue(Files.exists(globalsOut));

    Path finalOut = temp.newFolder().toPath().resolve("out.jar");
    forkD8(dexOut.toString(), "--globals", globalsOut.toString(), "--output", finalOut.toString());

    testForD8()
        .addProgramFiles(finalOut)
        .run(parameters.getRuntime(), TestClass1.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDexPerClassFileDir() throws Exception {
    Path input1 = transformClass(TestClass1.class);
    Path input2 = transformClass(TestClass2.class);
    Path dexOut = temp.newFolder().toPath().resolve("out.jar");
    Path globalsOut = temp.newFolder("out").toPath();
    forkD8(
        input1.toString(),
        input2.toString(),
        "--file-per-class-file",
        "--output",
        dexOut.toString(),
        "--globals-output",
        globalsOut.toString());

    assertTrue(Files.exists(dexOut));
    assertTrue(Files.isDirectory(globalsOut));

    List<Path> globalFiles =
        ImmutableList.of(
            globalsOut.resolve(binaryName(TestClass1.class) + ".globals"),
            globalsOut.resolve(binaryName(TestClass2.class) + ".globals"));
    globalFiles.forEach(f -> assertTrue(Files.exists(f)));

    Path finalOut = temp.newFolder().toPath().resolve("out.jar");
    forkD8(
        ImmutableList.<String>builder()
            .add(dexOut.toString())
            .addAll(
                ListUtils.flatMap(globalFiles, f -> ImmutableList.of("--globals", f.toString())))
            .add("--output")
            .add(finalOut.toString())
            .build());

    testForD8()
        .addProgramFiles(finalOut)
        .run(parameters.getRuntime(), TestClass1.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  // Transform the class such that its handlers use the AuthenticationRequiredException which
  // is introduced with API level 25. This triggers the need for a class stub for the exception.
  private Path transformClass(Class<?> clazz) throws IOException {
    byte[] bytes =
        transformer(clazz)
            .transformTryCatchBlock(
                MethodPredicate.all(),
                (start, end, handler, type, visitor) -> {
                  assertEquals("java/lang/Exception", type);
                  visitor.visitTryCatchBlock(
                      start, end, handler, "android/app/AuthenticationRequiredException");
                })
            .transform();
    Path file = temp.newFolder().toPath().resolve("input.class");
    Files.write(file, bytes);
    return file;
  }

  static class TestClass1 {

    public static void main(String[] args) {
      Runnable r =
          () -> {
            try {
              System.out.println("Hello");
            } catch (Exception /* will be AuthenticationRequiredException */ e) {
              System.out.println("fail...");
              throw e;
            }
            System.out.println("all good...");
          };
      r.run();
      TestClass2.main(args);
    }
  }

  static class TestClass2 {

    public static void main(String[] args) {
      try {
        System.out.println("Hello again");
      } catch (Exception /* will be AuthenticationRequiredException */ e) {
        System.out.println("fail...");
        throw e;
      }
      System.out.println("still good...");
    }
  }
}
