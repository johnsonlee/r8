// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

public abstract class DexByteCodeWriter {

  public interface OutputStreamProvider {
    PrintStream get(DexClass clazz) throws IOException;
  }

  final DexApplication application;
  final InternalOptions options;

  final Set<ClassReference> classReferences;
  final Set<FieldReference> fieldReferences;
  final Set<MethodReference> methodReferences;

  DexByteCodeWriter(
      DexApplication application,
      InternalOptions options,
      Set<ClassReference> classReferences,
      Set<FieldReference> fieldReferences,
      Set<MethodReference> methodReferences) {
    this.application = application;
    this.options = options;
    this.classReferences = classReferences;
    this.fieldReferences = fieldReferences;
    this.methodReferences = methodReferences;
  }

  private static void ensureParentExists(Path path) throws IOException {
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }

  public static OutputStreamProvider oneFilePerClass(
      ClassNameMapper classNameMapper, Path path, String fileEnding) {
    return (clazz) -> {
      String className =
          DescriptorUtils.descriptorToJavaType(clazz.type.toDescriptorString(), classNameMapper);
      Path classOutput = path.resolve(className.replace('.', File.separatorChar) + fileEnding);
      ensureParentExists(classOutput);
      return new PrintStream(Files.newOutputStream(classOutput));
    };
  }

  public void writeMarkers(PrintStream output) {
    Collection<Marker> markers = application.dexItemFactory.extractMarkers();
    System.out.println("Number of markers: " + markers.size());
    for (Marker marker : markers) {
      output.println(marker.toString());
    }
  }

  public void write(PrintStream output) throws IOException {
    writeMarkers(output);
    write(x -> output, x -> {});
  }

  public void write(OutputStreamProvider outputStreamProvider, Consumer<PrintStream> closer)
      throws IOException {
    Iterable<DexProgramClass> classes = application.classesWithDeterministicOrder();
    for (DexProgramClass clazz : classes) {
      if (shouldWriteClass(clazz)) {
        PrintStream ps = outputStreamProvider.get(clazz);
        try {
          writeClass(clazz, ps);
        } finally {
          closer.accept(ps);
        }
      }
    }
  }

  private void writeClass(DexProgramClass clazz, PrintStream ps) {
    writeClassHeader(clazz, ps);
    writeFieldsHeader(clazz, ps);
    clazz.forEachField(field -> writeField(field, ps));
    writeFieldsFooter(clazz, ps);
    writeMethodsHeader(clazz, ps);
    clazz.forEachProgramMethod(method -> writeMethod(method, ps));
    writeMethodsFooter(clazz, ps);
    writeClassFooter(clazz, ps);
  }

  abstract void writeClassHeader(DexProgramClass clazz, PrintStream ps);

  void writeFieldsHeader(DexProgramClass clazz, PrintStream ps) {
    // Do nothing.
  }

  abstract void writeField(DexEncodedField field, PrintStream ps);

  void writeFieldsFooter(DexProgramClass clazz, PrintStream ps) {
    // Do nothing.
  }

  void writeMethodsHeader(DexProgramClass clazz, PrintStream ps) {
    // Do nothing.
  }

  abstract void writeMethod(ProgramMethod method, PrintStream ps);

  void writeMethodsFooter(DexProgramClass clazz, PrintStream ps) {
    // Do nothing.
  }

  abstract void writeClassFooter(DexProgramClass clazz, PrintStream ps);

  boolean shouldWriteClass(DexClass clazz) {
    if (classReferences == null && fieldReferences == null && methodReferences == null) {
      return true;
    }
    if (classReferences != null && classReferences.contains(clazz.getClassReference())) {
      return true;
    }
    if (fieldReferences != null
        && clazz
            .fields(field -> fieldReferences.contains(field.getReference().asFieldReference()))
            .iterator()
            .hasNext()) {
      return true;
    }
    if (methodReferences != null
        && clazz
            .methods(method -> methodReferences.contains(method.getReference().asMethodReference()))
            .iterator()
            .hasNext()) {
      return true;
    }
    return false;
  }

  boolean shouldWriteField(DexEncodedField field) {
    if (classReferences == null && fieldReferences == null && methodReferences == null) {
      return true;
    }
    if (classReferences != null
        && classReferences.contains(field.getHolderType().asClassReference())) {
      return true;
    }
    if (fieldReferences != null
        && fieldReferences.contains(field.getReference().asFieldReference())) {
      return true;
    }
    return false;
  }

  boolean shouldWriteMethod(DexEncodedMethod method) {
    if (classReferences == null && fieldReferences == null && methodReferences == null) {
      return true;
    }
    if (classReferences != null
        && classReferences.contains(method.getHolderType().asClassReference())) {
      return true;
    }
    if (methodReferences != null
        && methodReferences.contains(method.getReference().asMethodReference())) {
      return true;
    }
    return false;
  }
}
