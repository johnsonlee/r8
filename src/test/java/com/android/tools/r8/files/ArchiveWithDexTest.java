// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.files;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArchiveWithDexTest extends TestBase {

  static final String MESSAGE = "containing both DEX and Java-bytecode content";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static Path zipWithDexAndClass;
  private static Origin zipWithDexAndClassOrigin;

  @BeforeClass
  public static void createZipWitDexAndClass() throws IOException {
    Path zipContent = getStaticTemp().newFolder().toPath();
    Files.copy(
        ToolHelper.getClassFileForTestClass(TestClass.class),
        zipContent.resolve("TestClass.class"));
    Files.createFile(zipContent.resolve("other.dex"));
    zipWithDexAndClass = getStaticTemp().newFolder().toPath().resolve("input.zip");
    ZipUtils.zip(zipWithDexAndClass, zipContent);
    zipWithDexAndClassOrigin = new PathOrigin(zipWithDexAndClass);
  }

  private void checkOneDexFiles(Path archive) throws Exception {
    BooleanBox seenClassesDex = new BooleanBox();
    ZipUtils.iter(
        archive,
        (entry, stream) -> {
          if (entry.getName().equals("classes.dex")) {
            seenClassesDex.set();
          } else {
            fail();
          }
        });
    assertTrue(seenClassesDex.get());
  }

  private void checkTwoDexFiles(Path archive) throws Exception {
    BooleanBox seenClassesDex = new BooleanBox();
    BooleanBox seenOtherDex = new BooleanBox();
    ZipUtils.iter(
        archive,
        (entry, stream) -> {
          if (entry.getName().equals("classes.dex")) {
            seenClassesDex.set();
          } else if (entry.getName().equals("other.dex")) {
            seenOtherDex.set();
          } else {
            fail();
          }
        });
    assertTrue(seenClassesDex.get() && seenOtherDex.get());
  }

  @Test
  public void testFileInputD8() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(zipWithDexAndClass)
                .setMinApi(AndroidApiLevel.B)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                diagnosticMessage(containsString("input.zip")),
                                diagnosticMessage(containsString(MESSAGE)),
                                diagnosticOrigin(zipWithDexAndClassOrigin)))));
  }

  @Test
  public void testProviderInputD8() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramResourceProviders(
                    ArchiveProgramResourceProvider.fromArchive(zipWithDexAndClass))
                .setMinApi(AndroidApiLevel.B)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                // When using providers the file name is not in the message.
                                diagnosticMessage(containsString(MESSAGE)),
                                diagnosticOrigin(zipWithDexAndClassOrigin)))));
  }

  @Test
  public void testR8() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(Backend.DEX)
                .addProgramResourceProviders(
                    ArchiveProgramResourceProvider.fromArchive(zipWithDexAndClass))
                .addKeepMainRule(TestClass.class)
                .setMinApi(AndroidApiLevel.B)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                // When using providers the file name is not in the message.
                                diagnosticMessage(containsString(MESSAGE)),
                                diagnosticOrigin(zipWithDexAndClassOrigin)))));
  }

  @Test
  public void testR8WithFilter() throws Exception {
    checkOneDexFiles(
        testForR8(Backend.DEX)
            .addProgramResourceProviders(
                ArchiveProgramResourceProvider.fromArchive(
                    zipWithDexAndClass, FileUtils::isClassFile))
            .addKeepMainRule(TestClass.class)
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .writeToZip());
  }

  @Test
  public void testR8LegacyProgramResourceProviderIgnoreDex() throws Exception {
    // The other.dex is seen as a resource and passed through.
    checkTwoDexFiles(
        testForR8(Backend.DEX)
            .addProgramResourceProviders(
                ArchiveResourceProvider.fromArchive(zipWithDexAndClass, true))
            .addKeepMainRule(TestClass.class)
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .writeToZip());
  }

  @Test
  public void testRawCommandBuilderR8() throws Exception {
    // Check that the error is wrapped and reported at the point of compiling (not arg building).
    R8Command command =
        R8Command.builder()
            .addProgramResourceProvider(
                ArchiveProgramResourceProvider.fromArchive(zipWithDexAndClass))
            .setMinApiLevel(1)
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .build();
    assertThrows(CompilationFailedException.class, () -> R8.run(command));
  }

  @Test
  public void testR8LegacyProgramResourceProviderDontIgnoreDex() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(Backend.DEX)
                .addProgramResourceProviders(
                    ArchiveResourceProvider.fromArchive(zipWithDexAndClass, false))
                .addKeepMainRule(TestClass.class)
                .setMinApi(AndroidApiLevel.B)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                // When using providers the file name is not in the message.
                                diagnosticMessage(containsString(MESSAGE)),
                                diagnosticOrigin(zipWithDexAndClassOrigin)))));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
