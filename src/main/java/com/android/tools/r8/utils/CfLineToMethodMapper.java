// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.KotlinSourceDebugExtensionParserResult;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ArchiveResourceProvider.ArchiveResourceProviderHelper;
import com.android.tools.r8.utils.timing.Timing;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class CfLineToMethodMapper {

  private static final String NAME_DESCRIPTOR_SEPARATOR = ";;";

  private final Map<String, Int2ObjectMap<String>> sourceMethodMapping;

  private CfLineToMethodMapper(Map<String, Int2ObjectMap<String>> sourceMethodMapping) {
    this.sourceMethodMapping = sourceMethodMapping;
  }

  public static CfLineToMethodMapper create(
      AndroidApp inputApp,
      KotlinSourceDebugExtensionCollection kotlinSourceDebugExtensions,
      Timing timing) {
    Set<ClassReference> kotlinClassesWithInlineFunctions =
        getKotlinClassesWithInlineFunctions(kotlinSourceDebugExtensions, timing);
    return new CfLineToMethodMapper(
        readLineNumbersFromClassFiles(inputApp, kotlinClassesWithInlineFunctions, timing));
  }

  public String lookupNameAndDescriptor(String binaryName, int lineNumber)
      throws ResourceException {
    return sourceMethodMapping.getOrDefault(binaryName, Int2ObjectMaps.emptyMap()).get(lineNumber);
  }

  private static Set<ClassReference> getKotlinClassesWithInlineFunctions(
      KotlinSourceDebugExtensionCollection kotlinSourceDebugExtensions, Timing timing) {
    try (Timing t0 = timing.begin("Get kotlin classes with inline functions")) {
      Set<ClassReference> kotlinClassesWithInlineFunctions = new HashSet<>();
      for (KotlinSourceDebugExtensionParserResult kotlinSourceDebugExtension :
          kotlinSourceDebugExtensions.values()) {
        boolean first = true;
        for (var inlineePosition : kotlinSourceDebugExtension.getInlineePositions().values()) {
          if (first) {
            // Skip. It is the current holder.
            first = false;
          } else if (inlineePosition != null) {
            String binaryName = inlineePosition.getSource().getPath();
            kotlinClassesWithInlineFunctions.add(Reference.classFromBinaryName(binaryName));
          }
        }
      }
      return kotlinClassesWithInlineFunctions;
    }
  }

  private static Map<String, Int2ObjectMap<String>> readLineNumbersFromClassFiles(
      AndroidApp inputApp, Set<ClassReference> kotlinClassesWithInlineFunctions, Timing timing) {
    try (Timing t0 = timing.begin("Read line numbers from class files")) {
      Map<String, Int2ObjectMap<String>> sourceMethodMapping = new HashMap<>();
      try {
        for (ProgramResourceProvider resourceProvider : inputApp.getProgramResourceProviders()) {
          // TODO(b/391785584): Do not use the input providers here.
          if (resourceProvider instanceof InternalProgramClassProvider) {
            continue;
          }
          if (resourceProvider instanceof ArchiveResourceProvider) {
            // Special case the ArchiveResourceProvider to minimize disk I/O.
            ArchiveResourceProvider provider = (ArchiveResourceProvider) resourceProvider;
            ArchiveResourceProviderHelper helper = new ArchiveResourceProviderHelper(provider);
            timing.begin("Read archive");
            helper.accept(
                programResource ->
                    processProgramResource(
                        programResource,
                        kotlinClassesWithInlineFunctions,
                        sourceMethodMapping,
                        timing),
                entry -> {
                  String name = entry.getName();
                  if (!name.endsWith(CLASS_EXTENSION)) {
                    return false;
                  }
                  String binaryName = name.substring(0, name.length() - CLASS_EXTENSION.length());
                  return kotlinClassesWithInlineFunctions.contains(
                      Reference.classFromBinaryName(binaryName));
                });
            timing.end();
          } else {
            ProgramResourceProviderUtils.forEachProgramResourceCompat(
                resourceProvider,
                programResource ->
                    processProgramResource(
                        programResource,
                        kotlinClassesWithInlineFunctions,
                        sourceMethodMapping,
                        timing));
          }
        }
      } catch (ResourceException e) {
        throw new RuntimeException(e);
      }
      return sourceMethodMapping;
    }
  }

  private static void processProgramResource(
      ProgramResource programResource,
      Set<ClassReference> kotlinClassesWithInlineFunctions,
      Map<String, Int2ObjectMap<String>> sourceMethodMapping,
      Timing timing) {
    if (programResource.getKind() != Kind.CF) {
      return;
    }
    Set<String> classDescriptors = programResource.getClassDescriptors();
    if (classDescriptors == null || classDescriptors.size() != 1) {
      return;
    }
    String classDescriptor = classDescriptors.iterator().next();
    ClassReference classReference = Reference.classFromDescriptor(classDescriptor);
    if (!kotlinClassesWithInlineFunctions.contains(classReference)) {
      return;
    }
    timing.begin("Parse class");
    Int2ObjectMap<String> currentLineNumberMapping = new Int2ObjectOpenHashMap<>();
    ClassVisitor.visit(programResource, currentLineNumberMapping);
    sourceMethodMapping.put(classReference.getBinaryName(), currentLineNumberMapping);
    timing.end();
  }

  public static String getName(String nameAndDescriptor) {
    int index = nameAndDescriptor.indexOf(NAME_DESCRIPTOR_SEPARATOR);
    assert index > 0;
    return nameAndDescriptor.substring(0, index);
  }

  public static String getDescriptor(String nameAndDescriptor) {
    int index = nameAndDescriptor.indexOf(NAME_DESCRIPTOR_SEPARATOR);
    assert index > 0;
    return nameAndDescriptor.substring(index + NAME_DESCRIPTOR_SEPARATOR.length());
  }

  private static class ClassVisitor extends org.objectweb.asm.ClassVisitor {

    private final Int2ObjectMap<String> lineNumberMapping;

    private ClassVisitor(Int2ObjectMap<String> lineNumberMapping) {
      super(InternalOptions.ASM_VERSION);
      this.lineNumberMapping = lineNumberMapping;
    }

    public static void visit(
        ProgramResource programResource, Int2ObjectMap<String> lineNumberMapping) {
      byte[] bytes;
      try {
        bytes = StreamUtils.streamToByteArrayClose(programResource.getByteStream());
      } catch (ResourceException | IOException e) {
        // Intentionally left empty because the addition of inline info for kotlin inline
        // functions is a best effort.
        return;
      }
      new ClassReader(bytes).accept(new ClassVisitor(lineNumberMapping), ClassReader.SKIP_FRAMES);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new MethodLineVisitor(
          name + NAME_DESCRIPTOR_SEPARATOR + descriptor, lineNumberMapping);
    }
  }

  private static class MethodLineVisitor extends MethodVisitor {

    private final String nameAndDescriptor;
    private final Map<Integer, String> lineMethodMapping;

    private MethodLineVisitor(String nameAndDescriptor, Map<Integer, String> lineMethodMapping) {
      super(InternalOptions.ASM_VERSION);
      this.nameAndDescriptor = nameAndDescriptor;
      this.lineMethodMapping = lineMethodMapping;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      lineMethodMapping.put(line, nameAndDescriptor);
    }
  }
}
