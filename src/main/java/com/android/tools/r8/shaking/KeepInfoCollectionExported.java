// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class KeepInfoCollectionExported {

  private final Map<TypeReference, ExportedClassInfo> classInfos;

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

    public List<String> lines() {
      List<String> lines = new ArrayList<>();
      if (keepClassInfo != null) {
        lines.add("class info");
        lines.addAll(keepClassInfo.lines());
      }
      List<FieldReference> fieldRefs = new ArrayList<>(fieldInfos.keySet());
      fieldRefs.sort(Comparator.comparing(FieldReference::toSourceString));
      for (FieldReference fieldRef : fieldRefs) {
        lines.add("field " + fieldRef.toSourceString());
        lines.addAll(fieldInfos.get(fieldRef).lines());
      }
      List<MethodReference> methodRefs = new ArrayList<>(methodInfos.keySet());
      methodRefs.sort(Comparator.comparing(MethodReference::toSourceString));
      for (MethodReference methodRef : methodRefs) {
        lines.add("method " + methodRef.toSourceString());
        lines.addAll(methodInfos.get(methodRef).lines());
      }
      return lines;
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
