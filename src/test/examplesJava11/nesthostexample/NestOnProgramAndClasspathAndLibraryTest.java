// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestOnProgramAndClasspathAndLibraryTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean alsoOnClasspath;

  @Parameters(name = "{0}, alsoOnClasspath: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testD8MethodBridges() {
    Assume.assumeTrue(parameters.isDexRuntime());
    // 1 inner class.
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClasses(
                BasicNestHostWithInnerClassMethods.BasicNestedClass.class,
                true,
                BasicNestHostWithInnerClassMethods.class));
    // Outer class.
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClasses(
                BasicNestHostWithInnerClassMethods.class,
                false,
                BasicNestHostWithInnerClassMethods.class));
    // 2 inner classes.
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClasses(
                NestHostExample.StaticNestMemberInner.class, true, NestHostExample.class));
  }

  @Test
  public void testD8ConstructorBridges() {
    Assume.assumeTrue(parameters.isDexRuntime());
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClasses(
                BasicNestHostWithInnerClassConstructors.BasicNestedClass.class,
                true,
                BasicNestHostWithInnerClassConstructors.class));
    assertThrows(
        CompilationFailedException.class,
        () ->
            compileClassesWithD8ProgramClasses(
                BasicNestHostWithInnerClassConstructors.class,
                false,
                BasicNestHostWithInnerClassConstructors.class));
  }

  private void compileClassesWithD8ProgramClasses(
      Class<?> clazz, boolean withInnerClasses, Class<?> outer) throws Exception {
    Path nestZip = buildNestZip(outer);
    testForD8()
        .setMinApi(parameters)
        .addProgramClasses(clazz)
        .applyIf(withInnerClasses, b -> b.addInnerClasses(clazz))
        .applyIf(alsoOnClasspath, b -> b.addClasspathFiles(nestZip))
        .addLibraryFiles(nestZip)
        .compile();
    Files.delete(nestZip);
  }

  private Path buildNestZip(Class<?> outer) throws IOException {
    List<Path> nest = new ArrayList<>();
    nest.add(ToolHelper.getClassFileForTestClass(outer));
    nest.addAll(ToolHelper.getClassFilesForInnerClasses(outer));
    Path zip = temp.getRoot().toPath().resolve("nest.zip");
    ZipBuilder zp = ZipBuilder.builder(zip);
    nest.forEach(
        f -> {
          try {
            zp.addFile(f.getFileName().toString(), f);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    return zp.build();
  }
}
