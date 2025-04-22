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
import com.android.tools.r8.utils.InternalOptions;
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
  private boolean enableLibraryApiModeling =
      System.getProperty("com.android.tools.r8.disableApiModeling") == null;

  // Flag to specify Android extension libraries (also known as OEM-implemented shared libraries
  // or sidecars). All APIs within these libraries are handled as having an API level
  // higher than any existing API level as these APIs might not exist on any device independent
  // of API level (the nature of an extension API).
  public String androidApiExtensionLibraries =
      System.getProperty("com.android.tools.r8.androidApiExtensionLibraries");

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

  // The flag enableApiCallerIdentification controls if we can inline or merge targets with
  // different api levels. It is also the flag that specifies if we assign api levels to
  // references.
  private boolean enableApiCallerIdentification = true;
  private boolean enableStubbingOfClasses = true;
  private boolean enableOutliningOfMethods = true;
  private boolean checkAllApiReferencesAreSet = true;

  // TODO(b/232823652): Enable when we can compute the offset correctly.
  public boolean useMemoryMappedByteBuffer = false;

  // A mapping from references to the api-level introducing them.
  public Map<MethodReference, AndroidApiLevel> methodApiMapping = new HashMap<>();
  public Map<FieldReference, AndroidApiLevel> fieldApiMapping = new HashMap<>();
  public Map<ClassReference, AndroidApiLevel> classApiMapping = new HashMap<>();
  public BiConsumer<MethodReference, ComputedApiLevel> tracedMethodApiLevelCallback = null;

  private final InternalOptions options;

  public AndroidApiModelingOptions(InternalOptions options) {
    this.options = options;
  }

  public void forEachAndroidApiExtensionClassDescriptor(Consumer<String> consumer) {
    assert isApiModelingEnabled();
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

  public void forEachAndroidApiExtensionPackage(Consumer<String> consumer) {
    assert isApiModelingEnabled();
    if (androidApiExtensionPackages != null) {
      StringUtils.split(androidApiExtensionPackages, ',').forEach(consumer);
    }
  }

  public boolean isApiModelingEnabled() {
    // TODO(b/384426376): Should return false when compiling to CF.
    return enableLibraryApiModeling;
  }

  public boolean isApiCallerIdentificationEnabled() {
    return isApiModelingEnabled() && enableApiCallerIdentification;
  }

  public boolean isStubbingOfClassesEnabled() {
    // TODO(b/384426376): Should not check backend when isApiModelingEnabled() return false for CF.
    return isApiModelingEnabled() && options.isGeneratingDex() && enableStubbingOfClasses;
  }

  public boolean isOutliningOfMethodsEnabled() {
    // TODO(b/384426376): Should not check backend when isApiModelingEnabled() return false for CF.
    return isApiModelingEnabled() && options.isGeneratingDex() && enableOutliningOfMethods;
  }

  public boolean isCfToCfApiOutliningEnabled() {
    if (isOutliningOfMethodsEnabled()) {
      // Enable cf-to-cf when running normal D8/R8. When running R8 partial, enable cf-to-cf
      // desugaring when there is no library desugaring.
      return options.partialSubCompilationConfiguration == null
          || !options.getSubCompilationLibraryDesugaringOptions().isEnabled();
    }
    return false;
  }

  public boolean isLirToLirApiOutliningEnabled() {
    return isOutliningOfMethodsEnabled() && !isCfToCfApiOutliningEnabled();
  }

  public boolean isCheckAllApiReferencesAreSet() {
    return isApiModelingEnabled() && checkAllApiReferencesAreSet;
  }

  public boolean isReportUnknownApiReferencesEnabled() {
    return isApiModelingEnabled()
        && System.getProperty("com.android.tools.r8.reportUnknownApiReferences") != null;
  }

  public AndroidApiModelingOptions disableApiModeling() {
    return setEnableApiModeling(false);
  }

  public AndroidApiModelingOptions setEnableApiModeling(boolean value) {
    enableLibraryApiModeling = value;
    return this;
  }

  public AndroidApiModelingOptions disableApiCallerIdentification() {
    return setEnableApiCallerIdentification(false);
  }

  public AndroidApiModelingOptions setEnableApiCallerIdentification(boolean value) {
    enableApiCallerIdentification = value;
    return this;
  }

  public AndroidApiModelingOptions disableOutlining() {
    return setEnableOutliningOfMethods(false);
  }

  public AndroidApiModelingOptions setEnableOutliningOfMethods(boolean value) {
    enableOutliningOfMethods = value;
    return this;
  }

  public AndroidApiModelingOptions disableStubbingOfClasses() {
    return setEnableStubbingOfClasses(false);
  }

  public AndroidApiModelingOptions setEnableStubbingOfClasses(boolean value) {
    enableStubbingOfClasses = value;
    return this;
  }

  public AndroidApiModelingOptions setCheckAllApiReferencesAreSet(boolean value) {
    checkAllApiReferencesAreSet = value;
    return this;
  }

  public void visitMockedApiLevelsForReferences(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    assert isApiModelingEnabled();
    classApiMapping.forEach(
        (classReference, apiLevel) ->
            apiLevelConsumer.accept(factory.createType(classReference.getDescriptor()), apiLevel));
    fieldApiMapping.forEach(
        (fieldReference, apiLevel) ->
            apiLevelConsumer.accept(factory.createField(fieldReference), apiLevel));
    methodApiMapping.forEach(
        (methodReference, apiLevel) ->
            apiLevelConsumer.accept(factory.createMethod(methodReference), apiLevel));
  }
}
