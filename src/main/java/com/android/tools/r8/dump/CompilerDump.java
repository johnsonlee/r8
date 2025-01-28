// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static com.android.tools.r8.utils.AndroidApp.dumpR8ExcludeFileName;
import static com.android.tools.r8.utils.AndroidApp.dumpR8IncludeFileName;

import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class CompilerDump {

  private final Path directory;

  public static CompilerDump fromArchive(Path dumpArchive) throws IOException {
    return fromArchive(dumpArchive, dumpArchive.getParent());
  }

  public static CompilerDump fromArchive(Path dumpArchive, Path dumpExtractionDirectory)
      throws IOException {
    ZipUtils.unzip(dumpArchive, dumpExtractionDirectory);
    return fromDirectory(dumpExtractionDirectory);
  }

  public static CompilerDump fromDirectory(Path dumpDirectory) {
    return new CompilerDump(dumpDirectory);
  }

  public CompilerDump(Path directory) {
    this.directory = directory;
  }

  public void forEachFeatureArchive(Consumer<? super Path> consumer) {
    int i = 1;
    while (true) {
      Path featureJar = directory.resolve("feature-" + i + ".jar");
      if (!Files.exists(featureJar)) {
        break;
      }
      consumer.accept(featureJar);
      i++;
    }
  }

  public Path getProgramArchive() {
    return directory.resolve("program.jar");
  }

  public Path getClasspathArchive() {
    return directory.resolve("classpath.jar");
  }

  public Path getLibraryArchive() {
    return directory.resolve("library.jar");
  }

  public Path getBuildPropertiesFile() {
    return directory.resolve("build.properties");
  }

  public Path getProguardConfigFile() {
    return directory.resolve("proguard.config");
  }

  public boolean hasDesugaredLibrary() {
    return Files.exists(directory.resolve("desugared-library.json"));
  }

  public Path getAndroidResources() {
    return directory.resolve("app-res.ap_");
  }

  public Path getAndroidResourcesForFeature(int feature) {
    return directory.resolve("feature-" + feature + ".ap_");
  }

  public Path getDesugaredLibraryFile() {
    return directory.resolve("desugared-library.json");
  }

  public Path getR8PartialIncludeFile() {
    return directory.resolve(dumpR8IncludeFileName);
  }

  public Path getR8PartialExcludeFile() {
    return directory.resolve(dumpR8ExcludeFileName);
  }

  public void sanitizeProguardConfig(ProguardConfigSanitizer sanitizer) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(getProguardConfigFile())) {
      String next = reader.readLine();
      while (next != null) {
        sanitizer.sanitize(next);
        next = reader.readLine();
      }
    }
  }

  public DumpOptions getBuildProperties() throws IOException {
    if (Files.exists(getBuildPropertiesFile())) {
      DumpOptions.Builder builder = new DumpOptions.Builder();
      DumpOptions.parse(FileUtils.readTextFile(getBuildPropertiesFile()), builder);
      return builder.build();
    }
    return null;
  }

  public List<String> getR8PartialIncludePatterns() throws IOException {
    if (Files.exists(getR8PartialIncludeFile())) {
      List<String> includePatterns = FileUtils.readAllLines(getR8PartialIncludeFile());
      assert includePatterns.stream().noneMatch(String::isEmpty);
      return includePatterns;
    }
    return null;
  }

  public List<String> getR8PartialExcludePatternsOrDefault(List<String> defaultValue)
      throws IOException {
    if (Files.exists(getR8PartialExcludeFile())) {
      List<String> excludePatterns = FileUtils.readAllLines(getR8PartialExcludeFile());
      assert excludePatterns.stream().noneMatch(String::isEmpty);
      return excludePatterns;
    }
    return defaultValue;
  }
}
