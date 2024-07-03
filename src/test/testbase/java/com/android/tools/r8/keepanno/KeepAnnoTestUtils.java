// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.asm.KeepEdgeWriter.AnnotationVisitorInterface;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.AnnotationVisitor;

public class KeepAnnoTestUtils {

  public static ProguardVersion PG_VERSION = ProguardVersion.V7_4_1;

  // Track support for R8 version 8.0.46 which is included in AGP 8.0.2
  public static Path R8_LIB =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8-releases", "8.0.46", "r8lib.jar");

  public static Path getKeepAnnoLib(TemporaryFolder temp) throws IOException {
    Path archive = temp.newFolder().toPath().resolve("keepanno.jar");
    ArchiveConsumer consumer = new ArchiveConsumer(archive);
    for (Path root : ToolHelper.getBuildPropKeepAnnoRuntimePath()) {
      Path annoDir =
          root.resolve(Paths.get("com", "android", "tools", "r8", "keepanno", "annotations"));
      assertTrue(Files.isDirectory(root));
      assertTrue(Files.isDirectory(annoDir));
      try (Stream<Path> paths = Files.list(annoDir)) {
        paths.forEach(
            p -> {
              if (FileUtils.isClassFile(p)) {
                byte[] data = FileUtils.uncheckedReadAllBytes(p);
                String fileName = p.getFileName().toString();
                String className = fileName.substring(0, fileName.lastIndexOf('.'));
                String desc = "Lcom/android/tools/r8/keepanno/annotations/" + className + ";";
                consumer.accept(ByteDataView.of(data), desc, null);
              }
            });
      }
    }
    consumer.finished(null);
    return archive;
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

  public static AnnotationVisitorInterface wrap(AnnotationVisitor visitor) {
    return visitor == null ? null : new WrappedAnnotationVisitor(visitor);
  }

  private static class WrappedAnnotationVisitor implements AnnotationVisitorInterface {

    private final AnnotationVisitor visitor;

    private WrappedAnnotationVisitor(AnnotationVisitor visitor) {
      this.visitor = visitor;
    }

    @Override
    public int version() {
      return InternalOptions.ASM_VERSION;
    }

    @Override
    public void visit(String name, Object value) {
      visitor.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      visitor.visitEnum(name, descriptor, value);
    }

    @Override
    public AnnotationVisitorInterface visitAnnotation(String name, String descriptor) {
      return wrap(visitor.visitAnnotation(name, descriptor));
    }

    @Override
    public AnnotationVisitorInterface visitArray(String name) {
      return wrap(visitor.visitArray(name));
    }

    @Override
    public void visitEnd() {
      visitor.visitEnd();
    }
  }
}
