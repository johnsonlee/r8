// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.sampleapi;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.Version;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * This API test is a copy over from the old API sample test set up.
 *
 * <p>NOTE: Don't use this test as the basis for new API tests. Instead, use one of the simpler and
 * more feature directed tests found in the sibling packages.
 */
public class R8ApiUsageSampleTest extends CompilerApiTestRunner {

  public R8ApiUsageSampleTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void test() throws IOException {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    test.run(temp);
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    private static final Origin origin =
        new Origin(Origin.root()) {
          @Override
          public String part() {
            return "R8ApiUsageSample";
          }
        };

    private static final DiagnosticsHandler handler = new DiagnosticsHandler() {};

    @Test
    public void test() throws IOException {
      run(temp);
    }

    public void run(TemporaryFolder temp) throws IOException {
      Path pgConf = temp.getRoot().toPath().resolve("rules.conf");
      Files.write(pgConf, getKeepMainRules(getMockClass()));
      runFromArgs(
          new String[] {
            "--output",
            temp.newFolder().getAbsolutePath(),
            "--min-api",
            "19",
            "--pg-conf",
            pgConf.toString(),
            "--lib",
            getAndroidJar().toString(),
            getPathForClass(getMockClass()).toString()
          });
    }

    public void runFromArgs(String[] args) {
      // Check version API
      checkVersionApi();
      // Parse arguments with the commandline parser to make use of its API.
      R8Command.Builder cmd = R8Command.parse(args, origin);
      CompilationMode mode = cmd.getMode();
      Path temp = cmd.getOutputPath();
      int minApiLevel = cmd.getMinApiLevel();
      // The Builder API does not provide access to the concrete paths
      // (everything is put into providers) so manually parse them here.
      List<Path> libraries = new ArrayList<>(1);
      List<Path> pgConf = new ArrayList<>(1);
      List<Path> inputs = new ArrayList<>(args.length);
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("--lib")) {
          libraries.add(Paths.get(args[++i]));
        } else if (args[i].equals("--pg-conf")) {
          pgConf.add(Paths.get(args[++i]));
        } else if (isArchive(args[i]) || isClassFile(args[i])) {
          inputs.add(Paths.get(args[i]));
        }
      }
      if (!Files.exists(temp) || !Files.isDirectory(temp)) {
        throw new RuntimeException("Must supply a temp/output directory");
      }
      if (inputs.isEmpty()) {
        throw new RuntimeException("Must supply program inputs");
      }
      if (libraries.isEmpty()) {
        throw new RuntimeException("Must supply library inputs");
      }
      if (pgConf.isEmpty()) {
        throw new RuntimeException("Must supply pg-conf inputs");
      }

      useProgramFileList(CompilationMode.DEBUG, minApiLevel, libraries, inputs);
      useProgramFileList(CompilationMode.RELEASE, minApiLevel, libraries, inputs);
      useProgramData(minApiLevel, libraries, inputs);
      useProgramResourceProvider(minApiLevel, libraries, inputs);
      useLibraryResourceProvider(minApiLevel, libraries, inputs);
      useProguardConfigFiles(minApiLevel, libraries, inputs, pgConf);
      useProguardConfigLines(minApiLevel, libraries, inputs, pgConf);
      useAssertionConfig(minApiLevel, libraries, inputs);
      useVArgVariants(minApiLevel, libraries, inputs, pgConf);
      useProguardConfigConsumers(minApiLevel, libraries, inputs, pgConf);
    }

    private static class InMemoryStringConsumer implements StringConsumer {

      public String value = null;

      @Override
      public void accept(String string, DiagnosticsHandler handler) {
        value = string;
      }
    }

    private static void useProguardConfigConsumers(
        int minApiLevel, Collection<Path> libraries, Collection<Path> inputs, List<Path> pgConf) {
      InMemoryStringConsumer usageConsumer = new InMemoryStringConsumer();
      InMemoryStringConsumer seedsConsumer = new InMemoryStringConsumer();
      InMemoryStringConsumer configConsumer = new InMemoryStringConsumer();
      try {
        R8.run(
            R8Command.builder(handler)
                .setMinApiLevel(minApiLevel)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addLibraryFiles(libraries)
                .addProgramFiles(inputs)
                .addProguardConfigurationFiles(pgConf)
                .setProguardUsageConsumer(usageConsumer)
                .setProguardSeedsConsumer(seedsConsumer)
                .setProguardConfigurationConsumer(configConsumer)
                .build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exception", e);
      }
      if (usageConsumer.value == null) {
        throw new RuntimeException("Expected usage info but had none");
      }
      if (seedsConsumer.value == null) {
        throw new RuntimeException("Expected seeds info but had none");
      }
      if (configConsumer.value == null) {
        throw new RuntimeException("Expected config info but had none");
      }
    }

    // Check API support for compiling Java class-files from the file system.
    private static void useProgramFileList(
        CompilationMode mode,
        int minApiLevel,
        Collection<Path> libraries,
        Collection<Path> inputs) {
      try {
        R8.run(
            R8Command.builder(handler)
                .setMode(mode)
                .setMinApiLevel(minApiLevel)
                .setDisableTreeShaking(true)
                .setDisableMinification(true)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addLibraryFiles(libraries)
                .addProgramFiles(inputs)
                .build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exceptions", e);
      }
    }

    // Check API support for compiling Java class-files from byte content.
    private static void useProgramData(
        int minApiLevel, Collection<Path> libraries, Collection<Path> inputs) {
      try {
        R8Command.Builder builder =
            R8Command.builder(handler)
                .setMinApiLevel(minApiLevel)
                .setDisableTreeShaking(true)
                .setDisableMinification(true)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addLibraryFiles(libraries);
        for (ClassFileContent classfile : readClassFiles(inputs)) {
          builder.addClassProgramData(classfile.data, classfile.origin);
        }
        R8.run(builder.build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exceptions", e);
      } catch (IOException e) {
        throw new RuntimeException("Unexpected IO exception", e);
      }
    }

    // Check API support for compiling Java class-files from a program provider abstraction.
    private static void useProgramResourceProvider(
        int minApiLevel, Collection<Path> libraries, Collection<Path> inputs) {
      try {
        R8Command.Builder builder =
            R8Command.builder(handler)
                .setMinApiLevel(minApiLevel)
                .setDisableTreeShaking(true)
                .setDisableMinification(true)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addLibraryFiles(libraries);
        for (Path input : inputs) {
          if (isArchive(input)) {
            builder.addProgramResourceProvider(
                ArchiveProgramResourceProvider.fromArchive(
                    input, ArchiveProgramResourceProvider::includeClassFileEntries));
          } else {
            builder.addProgramResourceProvider(
                new ProgramResourceProvider() {
                  @Override
                  public Collection<ProgramResource> getProgramResources()
                      throws ResourceException {
                    return Collections.singleton(ProgramResource.fromFile(Kind.CF, input));
                  }
                });
          }
        }
        R8.run(builder.build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exceptions", e);
      }
    }

    private static void useLibraryResourceProvider(
        int minApiLevel, Collection<Path> libraries, Collection<Path> inputs) {
      try {
        R8Command.Builder builder =
            R8Command.builder(handler)
                .setMinApiLevel(minApiLevel)
                .setDisableTreeShaking(true)
                .setDisableMinification(true)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addProgramFiles(inputs);
        for (Path library : libraries) {
          builder.addLibraryResourceProvider(new ArchiveClassFileProvider(library));
        }
        R8.run(builder.build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exceptions", e);
      } catch (IOException e) {
        throw new RuntimeException("Unexpected IO exception", e);
      }
    }

    private static void useProguardConfigFiles(
        int minApiLevel, Collection<Path> libraries, Collection<Path> inputs, List<Path> pgConf) {
      try {
        R8.run(
            R8Command.builder(handler)
                .setMinApiLevel(minApiLevel)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addLibraryFiles(libraries)
                .addProgramFiles(inputs)
                .addProguardConfigurationFiles(pgConf)
                .build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exceptions", e);
      }
    }

    private static void useProguardConfigLines(
        int minApiLevel, Collection<Path> libraries, Collection<Path> inputs, List<Path> pgConf) {
      try {
        R8Command.Builder builder =
            R8Command.builder(handler)
                .setMinApiLevel(minApiLevel)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addLibraryFiles(libraries)
                .addProgramFiles(inputs);
        for (Path file : pgConf) {
          builder.addProguardConfiguration(Files.readAllLines(file), new PathOrigin(file));
        }
        R8.run(builder.build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exceptions", e);
      } catch (IOException e) {
        throw new RuntimeException("Unexpected IO exception", e);
      }
    }

    private static void useAssertionConfig(
        int minApiLevel, Collection<Path> libraries, Collection<Path> inputs) {
      String pkg = "com.android.tools.r8.compilerapi.sampleapi";
      try {
        R8.run(
            R8Command.builder(handler)
                .setDisableTreeShaking(true)
                .setMinApiLevel(minApiLevel)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addLibraryFiles(libraries)
                .addProgramFiles(inputs)
                .addAssertionsConfiguration(b -> b.setScopeAll().setCompileTimeEnable().build())
                .addAssertionsConfiguration(b -> b.setScopeAll().setCompileTimeDisable().build())
                .addAssertionsConfiguration(
                    b -> b.setScopePackage(pkg).setCompileTimeEnable().build())
                .addAssertionsConfiguration(b -> b.setScopePackage(pkg).setPassthrough().build())
                .addAssertionsConfiguration(
                    b -> b.setScopePackage(pkg).setCompileTimeDisable().build())
                .addAssertionsConfiguration(
                    b ->
                        b.setScopeClass(pkg + "R8ApiUsageSampleTest")
                            .setCompileTimeEnable()
                            .build())
                .addAssertionsConfiguration(
                    b -> b.setScopeClass(pkg + "R8ApiUsageSampleTest").setPassthrough().build())
                .addAssertionsConfiguration(
                    b ->
                        b.setScopeClass(pkg + "R8ApiUsageSampleTest")
                            .setCompileTimeDisable()
                            .build())
                .addAssertionsConfiguration(
                    AssertionsConfiguration.Builder::compileTimeEnableAllAssertions)
                .addAssertionsConfiguration(
                    AssertionsConfiguration.Builder::passthroughAllAssertions)
                .addAssertionsConfiguration(
                    AssertionsConfiguration.Builder::compileTimeDisableAllAssertions)
                .build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exceptions", e);
      }
    }

    // Check API support for all the varg variants.
    private static void useVArgVariants(
        int minApiLevel, List<Path> libraries, List<Path> inputs, List<Path> pgConf) {
      try {
        R8.run(
            R8Command.builder(handler)
                .setMinApiLevel(minApiLevel)
                .setProgramConsumer(new EnsureOutputConsumer())
                .addLibraryFiles(libraries.get(0))
                .addLibraryFiles(libraries.stream().skip(1).toArray(Path[]::new))
                .addProgramFiles(inputs.get(0))
                .addProgramFiles(inputs.stream().skip(1).toArray(Path[]::new))
                .addProguardConfigurationFiles(pgConf.get(0))
                .addProguardConfigurationFiles(pgConf.stream().skip(1).toArray(Path[]::new))
                .build());
      } catch (CompilationFailedException e) {
        throw new RuntimeException("Unexpected compilation exceptions", e);
      }
    }

    // Helpers for tests.
    // Some of this reimplements stuff in R8 utils, but that is not public API and we should not
    // rely on it.

    private static List<ClassFileContent> readClassFiles(Collection<Path> files)
        throws IOException {
      List<ClassFileContent> classfiles = new ArrayList<>();
      for (Path file : files) {
        if (isArchive(file)) {
          Origin zipOrigin = new PathOrigin(file);
          ZipInputStream zip =
              new ZipInputStream(Files.newInputStream(file), StandardCharsets.UTF_8);
          ZipEntry entry;
          while (null != (entry = zip.getNextEntry())) {
            String name = entry.getName();
            if (isClassFile(name)) {
              Origin origin = new ArchiveEntryOrigin(name, zipOrigin);
              classfiles.add(new ClassFileContent(origin, readBytes(zip)));
            }
          }
        } else if (isClassFile(file)) {
          classfiles.add(new ClassFileContent(new PathOrigin(file), Files.readAllBytes(file)));
        }
      }
      return classfiles;
    }

    private static byte[] readBytes(InputStream stream) throws IOException {
      try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[0xffff];
        for (int length; (length = stream.read(buffer)) != -1; ) {
          bytes.write(buffer, 0, length);
        }
        return bytes.toByteArray();
      }
    }

    private static String toLowerCase(String str) {
      return str.toLowerCase(Locale.ROOT);
    }

    private static boolean isClassFile(Path file) {
      return isClassFile(file.toString());
    }

    private static boolean isClassFile(String file) {
      file = toLowerCase(file);
      return file.endsWith(".class");
    }

    private static boolean isArchive(Path file) {
      return isArchive(file.toString());
    }

    private static boolean isArchive(String file) {
      file = toLowerCase(file);
      return file.endsWith(".zip") || file.endsWith(".jar");
    }

    private static class ClassFileContent {

      final Origin origin;
      final byte[] data;

      public ClassFileContent(Origin origin, byte[] data) {
        this.origin = origin;
        this.data = data;
      }
    }

    private static class EnsureOutputConsumer implements DexIndexedConsumer {

      boolean hasOutput = false;

      @Override
      public synchronized void accept(
          int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
        hasOutput = true;
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        if (!hasOutput) {
          handler.error(new StringDiagnostic("Expected to produce output but had none"));
        }
      }
    }

    private static void checkVersionApi() {
      String labelValue;
      int labelAccess;
      try {
        Field field = Version.class.getDeclaredField("LABEL");
        labelAccess = field.getModifiers();
        labelValue = (String) field.get(Version.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (!Modifier.isPublic(labelAccess)
          || !Modifier.isStatic(labelAccess)
          || !Modifier.isFinal(labelAccess)) {
        throw new RuntimeException("Expected public static final LABEL");
      }
      if (labelValue.isEmpty()) {
        throw new RuntimeException("Expected LABEL constant");
      }
      if (Version.getVersionString() == null) {
        throw new RuntimeException("Expected getVersionString API");
      }
      if (Version.getMajorVersion() < -1) {
        throw new RuntimeException("Expected getMajorVersion API");
      }
      if (Version.getMinorVersion() < -1) {
        throw new RuntimeException("Expected getMinorVersion API");
      }
      if (Version.getPatchVersion() < -1) {
        throw new RuntimeException("Expected getPatchVersion API");
      }
      if (Version.getPreReleaseString() == null && false) {
        throw new RuntimeException("Expected getPreReleaseString API");
      }
      if (Version.isDevelopmentVersion() && false) {
        throw new RuntimeException("Expected isDevelopmentVersion API");
      }
    }
  }
}
