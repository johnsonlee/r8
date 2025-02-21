// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno;

import static com.android.tools.r8.R8TestBuilder.KeepAnnotationLibrary.ANDROIDX;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.R8TestBuilder.KeepAnnotationLibrary;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ListUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.rules.TemporaryFolder;

public class KeepAnnoTestUtils {

  public static ProguardVersion PG_VERSION = ProguardVersion.V7_4_1;

  public static final String DESCRIPTOR_PREFIX = "Landroidx/annotation/keep/";
  public static final String DESCRIPTOR_LEGACY_PREFIX =
      "Lcom/android/tools/r8/keepanno/annotations/";

  // Track support for R8 version 8.0.46 which is included in AGP 8.0.2
  public static Path R8_LIB =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8-releases", "8.0.46", "r8lib.jar");

  public static Path getKeepAnnoLib(
      TemporaryFolder temp, KeepAnnotationLibrary keepAnnotationLibrary) throws IOException {
    Path archive = temp.newFolder().toPath().resolve("keepanno.jar");
    ArchiveConsumer consumer = new ArchiveConsumer(archive);
    for (Path root : ToolHelper.getBuildPropKeepAnnoRuntimePath()) {
      String descriptorPrefix =
          (keepAnnotationLibrary == ANDROIDX ? DESCRIPTOR_PREFIX : DESCRIPTOR_LEGACY_PREFIX);
      Path annoDir = root.resolve(descriptorPrefix.substring(1, descriptorPrefix.length() - 1));
      assertTrue(Files.isDirectory(root));
      assertTrue(Files.isDirectory(annoDir));
      try (Stream<Path> paths = Files.list(annoDir)) {
        paths.forEach(
            p -> {
              if (FileUtils.isClassFile(p)) {
                byte[] data = FileUtils.uncheckedReadAllBytes(p);
                String fileName = p.getFileName().toString();
                String className = fileName.substring(0, fileName.lastIndexOf('.'));
                String desc = descriptorPrefix + className + ";";
                consumer.accept(ByteDataView.of(data), desc, null);
              }
            });
      }
    }
    consumer.finished(null);
    return archive;
  }

  public static Path getKeepAnnoLib(TemporaryFolder temp) throws IOException {
    return getKeepAnnoLib(temp, ANDROIDX);
  }

  public static List<String> extractRulesFromFiles(
      List<Path> inputFiles, KeepRuleExtractorOptions extractorOptions) {
    return extractRulesFromBytes(
        ListUtils.map(
            inputFiles,
            path -> {
              try {
                return Files.readAllBytes(path);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }),
        extractorOptions);
  }

  public static List<String> extractRules(
      List<Class<?>> inputClasses, KeepRuleExtractorOptions extractorOptions) {
    return extractRulesFromBytes(
        ListUtils.map(
            inputClasses,
            clazz -> {
              try {
                return ToolHelper.getClassAsBytes(clazz);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }),
        extractorOptions);
  }

  public static List<String> extractRulesFromBytes(
      List<byte[]> inputClasses, KeepRuleExtractorOptions extractorOptions) {
    List<String> rules = new ArrayList<>();
    for (byte[] bytes : inputClasses) {
      List<KeepDeclaration> declarations = KeepEdgeReader.readKeepEdges(bytes);
      KeepRuleExtractor extractor = new KeepRuleExtractor(rules::add, extractorOptions);
      declarations.forEach(extractor::extract);
    }
    return rules;
  }
}
