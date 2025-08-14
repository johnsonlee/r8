// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class KeepInfoCollectionExported {

  static final String CLASS_INFO = "class info";
  static final String FIELD = "field";
  static final String METHOD = "method";

  private final Map<TypeReference, ExportedClassInfo> classInfos;

  public static KeepInfoCollectionExported parse(Path folder) throws IOException {
    Map<TypeReference, ExportedClassInfo> result = new IdentityHashMap<>();
    parseKeepInfo(folder, folder, result);
    return new KeepInfoCollectionExported(ImmutableMap.copyOf(result));
  }

  private static void parseKeepInfo(
      Path root, Path dir, Map<TypeReference, ExportedClassInfo> result) throws IOException {
    File file = dir.toFile();
    if (file.exists()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          Path childPath = child.toPath();
          if (child.isDirectory()) {
            parseKeepInfo(root, childPath, result);
          } else {
            Path relative = root.relativize(childPath);
            result.put(
                Reference.typeFromDescriptor("L" + relative + ";"),
                ExportedClassInfo.parse(childPath));
          }
        }
      }
    }
  }

  static class ExportedClassInfo {

    private final KeepClassInfo keepClassInfo;
    private final Map<FieldReference, KeepFieldInfo> fieldInfos;
    private final Map<MethodReference, KeepMethodInfo> methodInfos;

    ExportedClassInfo(
        KeepClassInfo keepClassInfo,
        Map<FieldReference, KeepFieldInfo> fieldInfos,
        Map<MethodReference, KeepMethodInfo> methodInfos) {
      this.keepClassInfo = keepClassInfo;
      this.fieldInfos = fieldInfos;
      this.methodInfos = methodInfos;
    }

    public boolean isEqualsTo(ExportedClassInfo other) {
      if (!fieldInfos.keySet().equals(other.fieldInfos.keySet())) {
        return false;
      }
      for (FieldReference fieldReference : fieldInfos.keySet()) {
        if (!fieldInfos
            .get(fieldReference)
            .equalsWithAnnotations(other.fieldInfos.get(fieldReference))) {
          return false;
        }
      }
      if (!methodInfos.keySet().equals(other.methodInfos.keySet())) {
        return false;
      }
      for (MethodReference methodReference : methodInfos.keySet()) {
        if (!methodInfos
            .get(methodReference)
            .equalsWithAnnotations(other.methodInfos.get(methodReference))) {
          return false;
        }
      }
      if (keepClassInfo == null) {
        return other.keepClassInfo == null;
      }
      return keepClassInfo.equalsWithAnnotations(other.keepClassInfo);
    }

    @Override
    public int hashCode() {
      return Objects.hash(keepClassInfo, fieldInfos, methodInfos);
    }

    public List<String> lines() {
      List<String> lines = new ArrayList<>();
      if (keepClassInfo != null) {
        lines.add(CLASS_INFO);
        lines.addAll(keepClassInfo.lines());
        lines.add("");
      }
      List<FieldReference> fieldRefs = new ArrayList<>(fieldInfos.keySet());
      fieldRefs.sort(Comparator.comparing(FieldReference::toSourceString));
      for (FieldReference fieldRef : fieldRefs) {
        lines.add(FIELD + " " + fieldRef.toSourceString());
        lines.addAll(fieldInfos.get(fieldRef).lines());
        lines.add("");
      }
      List<MethodReference> methodRefs = new ArrayList<>(methodInfos.keySet());
      methodRefs.sort(Comparator.comparing(MethodReference::toSourceString));
      for (MethodReference methodRef : methodRefs) {
        lines.add(METHOD + " " + methodRef.toSourceString());
        lines.addAll(methodInfos.get(methodRef).lines());
        lines.add("");
      }
      return lines;
    }

    public static ExportedClassInfo parse(Path path) throws IOException {
      List<String> lines = Files.readAllLines(path);

      Builder builder = new Builder();
      Iterator<String> iterator = lines.iterator();
      while (iterator.hasNext()) {
        String line = iterator.next();
        while (line.isEmpty() && iterator.hasNext()) {
          line = iterator.next();
        }
        if (line.equals(CLASS_INFO)) {
          builder.setKeepClassInfo(KeepClassInfo.parse(iterator));
        }
        if (line.startsWith(FIELD)) {
          FieldReference fieldReference = readFieldReference(line.substring(FIELD.length() + 1));
          builder.putFieldInfo(fieldReference, KeepFieldInfo.parse(iterator));
        }
        if (line.startsWith(METHOD)) {
          MethodReference methodReference =
              readMethodReference(line.substring(METHOD.length() + 1));
          builder.putMethodInfo(methodReference, KeepMethodInfo.parse(iterator));
        }
      }
      return builder.build();
    }

    private static MethodReference readMethodReference(String substring) {
      int first = substring.indexOf(" ");
      String returnString = substring.substring(0, first);
      TypeReference returnType =
          returnString.equals("void") ? null : Reference.typeFromTypeName(returnString);
      int brace = substring.indexOf("(");
      String holderAndName = substring.substring(first + 1, brace);
      int nameIndex = holderAndName.lastIndexOf(".");
      ClassReference holder = Reference.classFromTypeName(holderAndName.substring(0, nameIndex));
      String methodName = holderAndName.substring(nameIndex + 1);
      String[] argStrings = substring.substring(brace + 1, substring.length() - 1).split(", ");
      TypeReference[] args =
          argStrings.length == 1 && argStrings[0].isEmpty()
              ? new TypeReference[0]
              : ArrayUtils.map(argStrings, Reference::typeFromTypeName, new TypeReference[0]);
      return Reference.method(holder, methodName, Arrays.asList(args), returnType);
    }

    private static FieldReference readFieldReference(String string) {
      int first = string.indexOf(" ");
      int last = string.lastIndexOf(".");
      TypeReference fieldType = Reference.typeFromTypeName(string.substring(0, first));
      ClassReference classType = Reference.classFromTypeName(string.substring(first + 1, last));
      String fieldName = string.substring(last + 1);
      return Reference.field(classType, fieldName, fieldType);
    }

    static Builder builder() {
      return new Builder();
    }

    static class Builder {

      private KeepClassInfo keepClassInfo;
      private final Map<FieldReference, KeepFieldInfo> fieldInfos = new HashMap<>();
      private final Map<MethodReference, KeepMethodInfo> methodInfos = new HashMap<>();

      public void setKeepClassInfo(KeepClassInfo keepClassInfo) {
        this.keepClassInfo = keepClassInfo;
      }

      public void putFieldInfo(FieldReference fieldReference, KeepFieldInfo keepFieldInfo) {
        fieldInfos.put(fieldReference, keepFieldInfo);
      }

      public void putMethodInfo(MethodReference methodReference, KeepMethodInfo keepMethodInfo) {
        methodInfos.put(methodReference, keepMethodInfo);
      }

      public ExportedClassInfo build() {
        return new ExportedClassInfo(
            keepClassInfo, ImmutableMap.copyOf(fieldInfos), ImmutableMap.copyOf(methodInfos));
      }
    }
  }

  public KeepInfoCollectionExported(Map<TypeReference, ExportedClassInfo> classInfos) {
    this.classInfos = classInfos;
  }

  public KeepInfoCollectionExported(
      Map<DexType, KeepClassInfo> keepClassInfo,
      Map<DexMethod, KeepMethodInfo> keepMethodInfo,
      Map<DexField, KeepFieldInfo> keepFieldInfo) {
    Map<TypeReference, ExportedClassInfo.Builder> classInfosBuilder = new HashMap<>();
    keepClassInfo.forEach(
        (type, info) -> {
          ExportedClassInfo.Builder builder = getBuilder(classInfosBuilder, type.asTypeReference());
          builder.setKeepClassInfo(export(info));
        });
    keepMethodInfo.forEach(
        (method, info) -> {
          ExportedClassInfo.Builder builder =
              getBuilder(classInfosBuilder, method.getHolderType().asTypeReference());
          builder.putMethodInfo(method.asMethodReference(), export(info));
        });
    keepFieldInfo.forEach(
        (field, info) -> {
          ExportedClassInfo.Builder builder =
              getBuilder(classInfosBuilder, field.getHolderType().asTypeReference());
          builder.putFieldInfo(field.asFieldReference(), export(info));
        });
    ImmutableMap.Builder<TypeReference, ExportedClassInfo> mapBuilder = ImmutableMap.builder();
    classInfosBuilder.forEach((typeRef, builder) -> mapBuilder.put(typeRef, builder.build()));
    classInfos = mapBuilder.build();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof KeepInfoCollectionExported)) {
      return false;
    }
    KeepInfoCollectionExported other = (KeepInfoCollectionExported) obj;
    if (!classInfos.keySet().equals(other.classInfos.keySet())) {
      return false;
    }
    for (TypeReference typeReference : classInfos.keySet()) {
      if (!classInfos.get(typeReference).isEqualsTo(other.classInfos.get(typeReference))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return classInfos.hashCode();
  }

  public KeepClassInfo getKeepClassInfo(TypeReference typeReference) {
    ExportedClassInfo info = classInfos.get(typeReference);
    return info == null ? null : info.keepClassInfo;
  }

  public KeepMethodInfo getKeepMethodInfo(MethodReference methodReference) {
    if (!classInfos.containsKey(methodReference.getHolderClass())) {
      return null;
    }
    return classInfos.get(methodReference.getHolderClass()).methodInfos.get(methodReference);
  }

  public KeepFieldInfo getKeepFieldInfo(FieldReference fieldReference) {
    if (!classInfos.containsKey(fieldReference.getHolderClass())) {
      return null;
    }
    return classInfos.get(fieldReference.getHolderClass()).fieldInfos.get(fieldReference);
  }

  private ExportedClassInfo.Builder getBuilder(
      Map<TypeReference, ExportedClassInfo.Builder> classInfosBuilder,
      TypeReference typeReference) {
    return classInfosBuilder.computeIfAbsent(typeReference, t -> ExportedClassInfo.builder());
  }

  public void exportToDirectory(Path directory) throws IOException {
    for (Entry<TypeReference, ExportedClassInfo> entry : classInfos.entrySet()) {
      TypeReference typeReference = entry.getKey();
      ExportedClassInfo exportedClassInfo = entry.getValue();
      String binaryName =
          DescriptorUtils.getClassBinaryNameFromDescriptor(typeReference.getDescriptor());
      Files.createDirectories(directory.resolve(binaryName).getParent());
      Files.write(
          directory.resolve(binaryName), exportedClassInfo.lines(), StandardOpenOption.CREATE);
    }
  }

  static boolean noAnnotation(KeepInfo<?, ?> info) {
    return info.internalAnnotationsInfo().isTopOrBottom()
        && info.internalTypeAnnotationsInfo().isTopOrBottom();
  }

  private KeepClassInfo export(KeepClassInfo classInfo) {
    if (noAnnotation(classInfo)) {
      return classInfo;
    }
    KeepClassInfo.Builder builder = classInfo.builder();
    builder.getAnnotationsInfo().setExport();
    builder.getTypeAnnotationsInfo().setExport();
    return builder.build();
  }

  private KeepFieldInfo export(KeepFieldInfo fieldInfo) {
    if (noAnnotation(fieldInfo)) {
      return fieldInfo;
    }
    KeepFieldInfo.Builder builder = fieldInfo.builder();
    builder.getAnnotationsInfo().setExport();
    builder.getTypeAnnotationsInfo().setExport();
    return builder.build();
  }

  private KeepMethodInfo export(KeepMethodInfo methodInfo) {
    if (noAnnotation(methodInfo) && methodInfo.internalParameterAnnotationsInfo().isTopOrBottom()) {
      return methodInfo;
    }
    KeepMethodInfo.Builder builder = methodInfo.builder();
    builder.getAnnotationsInfo().setExport();
    builder.getTypeAnnotationsInfo().setExport();
    builder.getParameterAnnotationsInfo().setExport();
    return builder.build();
  }

  public static class KeepAnnotationCollectionInfoExported extends KeepAnnotationCollectionInfo {

    static KeepAnnotationCollectionInfo createExported(KeepAnnotationCollectionInfo from) {
      if (from.isTopOrBottom()) {
        return from;
      }
      return new KeepAnnotationCollectionInfoExported(from.toString());
    }

    private final String export;

    KeepAnnotationCollectionInfoExported(String export) {
      this.export = export;
    }

    public static KeepAnnotationCollectionInfo.Builder parse(String value) {
      if (value.equals("top")) {
        return Builder.createTop();
      }
      if (value.equals("bottom")) {
        return Builder.createBottom();
      }
      return new Builder(KeepAnnotationInfo.getTop()) {
        @Override
        public KeepAnnotationCollectionInfo build() {
          return new KeepAnnotationCollectionInfoExported(value);
        }
      };
    }

    @Override
    public String toString() {
      return export;
    }

    @Override
    public boolean isRemovalAllowed(DexAnnotation annotation) {
      throw new Unreachable();
    }
  }
}
