// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import java.nio.file.Path;
import java.util.List;

public interface ProguardConfigurationParserConsumer {

  void addParsedConfiguration(String s);

  void addRule(ProguardConfigurationRule rule);

  void addKeepAttributePatterns(List<String> attributesPatterns, Origin origin, Position position);

  void setRenameSourceFileAttribute(String s, Origin origin, Position position);

  void addKeepPackageNamesPattern(ProguardClassNameList proguardClassNameList);

  void setKeepParameterNames(boolean b, Origin origin, Position position);

  void enableKeepDirectories();

  void addKeepDirectories(ProguardPathList proguardPathList);

  void disableOptimization(Origin origin, Position position);

  void disableObfuscation(Origin origin, Position position);

  void disableShrinking(Origin origin, Position position);

  void setPrintUsage(boolean b);

  void setPrintUsageFile(Path path);

  void enableProtoShrinking();

  void setIgnoreWarnings(boolean b);

  void addDontWarnPattern(ProguardClassNameList pattern);

  void addDontNotePattern(ProguardClassNameList pattern);

  void enableAllowAccessModification(Origin origin, Position position);

  void enablePrintConfiguration(Origin origin, Position position);

  void setPrintConfigurationFile(Path path);

  void enablePrintMapping(Origin origin, Position position);

  void setPrintMappingFile(Path path);

  void setApplyMappingFile(Path path, Origin origin, Position position);

  void addInjars(List<FilteredClassPath> filteredClassPaths, Origin origin, Position position);

  void addLibraryJars(List<FilteredClassPath> filteredClassPaths, Origin origin, Position position);

  void setPrintSeeds(boolean b, Origin origin, Position position);

  void setSeedFile(Path path);

  void setObfuscationDictionary(Path path, Origin origin, Position position);

  void setClassObfuscationDictionary(Path path, Origin origin, Position position);

  void setPackageObfuscationDictionary(Path path, Origin origin, Position position);

  void addAdaptClassStringsPattern(ProguardClassNameList pattern);

  void addAdaptResourceFileContents(ProguardPathList pattern);

  void addAdaptResourceFilenames(ProguardPathList pattern);

  void joinMaxRemovedAndroidLogLevel(int maxRemovedAndroidLogLevel);

  PackageObfuscationMode getPackageObfuscationMode();

  void setPackagePrefix(String s);

  void setFlattenPackagePrefix(String s);

  void enableRepackageClasses(Origin origin, Position position);

  void enableFlattenPackageHierarchy(Origin origin, Position position);
}
