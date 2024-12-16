// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidapi;

import static com.android.tools.r8.utils.CfUtils.extractClassDescriptor;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AndroidApiModelingOptions {

  // Flag to specify if we should load the database or not. The api database is used for
  // library member rebinding.
  public boolean enableLibraryApiModeling =
      System.getProperty("com.android.tools.r8.disableApiModeling") == null;

  // Flag to specify Android extension libraries (also known as OEM-implemented shared libraries
  // or sidecars). All APIs within these libraries are handled as having an API level
  // higher than any existing API level as these APIs might not exist on any device independent
  // of API level (the nature of an extension API).
  public String androidApiExtensionLibraries =
      System.getProperty("com.android.tools.r8.androidApiExtensionLibraries");

  public void forEachAndroidApiExtensionClassDescriptor(Consumer<String> consumer) {
    if (androidApiExtensionLibraries != null) {
      StringUtils.split(androidApiExtensionLibraries, ',')
          .forEach(
              lib -> {
                try {
                  ZipUtils.iter(
                      Paths.get(lib),
                      (entry, input) -> {
                        if (ZipUtils.isClassFile(entry.getName())) {
                          consumer.accept(extractClassDescriptor(input));
                        }
                      });
                } catch (Exception e) {
                  throw new CompilationError("Failed to read extension library " + lib, e);
                }
              });
    }
  }

  // TODO(b/326252366): Remove support for list of extension packages in favour of only
  //  supporting passing extension libraries as JAR files.
  // Flag to specify packages for Android extension APIs (also known as OEM-implemented
  // shared libraries or sidecars). The packages are specified as java package names
  // separated by commas. All APIs within these packages are handled as having an API level
  // higher than any existing API level as these APIs might not exist on any device independent
  // of API level (the nature of an extension API).
  // TODO(b/326252366): This mechanism should be extended to also specify the extension for
  //  each package to prevent merging of API outline methods fron different extensions.
  public String androidApiExtensionPackages =
      System.getProperty("com.android.tools.r8.androidApiExtensionPackages");

  public void forEachAndroidApiExtensionPackage(Consumer<String> consumer) {
    if (androidApiExtensionPackages != null) {
      StringUtils.split(androidApiExtensionPackages, ',').forEach(consumer);
    }
  }

  // The flag enableApiCallerIdentification controls if we can inline or merge targets with
  // different api levels. It is also the flag that specifies if we assign api levels to
  // references.
  public boolean enableApiCallerIdentification =
      System.getProperty("com.android.tools.r8.disableApiModeling") == null;
  public boolean checkAllApiReferencesAreSet =
      System.getProperty("com.android.tools.r8.disableApiModeling") == null;
  public boolean enableStubbingOfClasses =
      System.getProperty("com.android.tools.r8.disableApiModeling") == null;
  public boolean enableOutliningOfMethods =
      System.getProperty("com.android.tools.r8.disableApiModeling") == null;
  public boolean reportUnknownApiReferences =
      System.getProperty("com.android.tools.r8.reportUnknownApiReferences") != null;

  // TODO(b/232823652): Enable when we can compute the offset correctly.
  public boolean useMemoryMappedByteBuffer = false;

  // A mapping from references to the api-level introducing them.
  public Map<MethodReference, AndroidApiLevel> methodApiMapping = new HashMap<>();
  public Map<FieldReference, AndroidApiLevel> fieldApiMapping = new HashMap<>();
  public Map<ClassReference, AndroidApiLevel> classApiMapping = new HashMap<>();
  public BiConsumer<MethodReference, ComputedApiLevel> tracedMethodApiLevelCallback = null;

  public void visitMockedApiLevelsForReferences(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    if (methodApiMapping.isEmpty() && fieldApiMapping.isEmpty() && classApiMapping.isEmpty()) {
      return;
    }
    classApiMapping.forEach(
        (classReference, apiLevel) -> {
          apiLevelConsumer.accept(factory.createType(classReference.getDescriptor()), apiLevel);
        });
    fieldApiMapping.forEach(
        (fieldReference, apiLevel) -> {
          apiLevelConsumer.accept(factory.createField(fieldReference), apiLevel);
        });
    methodApiMapping.forEach(
        (methodReference, apiLevel) -> {
          apiLevelConsumer.accept(factory.createMethod(methodReference), apiLevel);
        });
  }

  public boolean isApiLibraryModelingEnabled() {
    return enableLibraryApiModeling;
  }

  public boolean isCheckAllApiReferencesAreSet() {
    return enableLibraryApiModeling && checkAllApiReferencesAreSet;
  }

  public boolean isApiCallerIdentificationEnabled() {
    return enableLibraryApiModeling && enableApiCallerIdentification;
  }

  public void disableApiModeling() {
    enableLibraryApiModeling = false;
    enableApiCallerIdentification = false;
    enableOutliningOfMethods = false;
    enableStubbingOfClasses = false;
    checkAllApiReferencesAreSet = false;
  }

  /**
   * Disable the workarounds for missing APIs. This does not disable the use of the database, just
   * the introduction of soft-verification workarounds for potentially missing API references.
   */
  public void disableOutliningAndStubbing() {
    enableOutliningOfMethods = false;
    enableStubbingOfClasses = false;
  }

  public void disableApiCallerIdentification() {
    enableApiCallerIdentification = false;
  }

  public void disableStubbingOfClasses() {
    enableStubbingOfClasses = false;
  }
}
