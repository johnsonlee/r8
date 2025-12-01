// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.shaking.ProguardConfiguration.ProcessKotlinNullChecks;
import com.android.tools.r8.shaking.ProguardConfigurationParser.ProguardConfigurationSourceParser;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import java.nio.file.Path;
import java.util.List;

public interface ProguardConfigurationParserConsumer {

  void addBaseDirectory(
      Path baseDirectory, ProguardConfigurationSourceParser parser, TextPosition positionStart);

  void addIgnoredOption(
      String option, ProguardConfigurationSourceParser parser, TextPosition positionStart);

  void addInclude(
      Path includePath, ProguardConfigurationSourceParser parser, TextPosition positionStart);

  void addLeadingBOM();

  void addParsedConfiguration(ProguardConfigurationSourceParser parser);

  void addRule(
      ProguardConfigurationRule rule,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  void addKeepAttributePatterns(
      List<String> attributesPatterns,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void addKeepKotlinMetadata(
      ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart);

  void addProcessKotlinNullChecks(
      ProcessKotlinNullChecks value,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void addKeepPackageNamesPattern(
      ProguardClassNameList proguardClassNameList,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  void setKeepParameterNames(
      ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart);

  void setRenameSourceFileAttribute(
      String s,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void enableKeepDirectories(
      ProguardPathList keepDirectoryPatterns,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  void enablePrintConfiguration(
      Path printConfigurationFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void enablePrintMapping(
      Path printMappingFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void enablePrintSeeds(
      Path printSeedsFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void enablePrintUsage(
      Path printUsageFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void disableOptimization(ProguardConfigurationSourceParser parser, Position position);

  void disableObfuscation(ProguardConfigurationSourceParser parser, Position position);

  void disableRepackaging(ProguardConfigurationSourceParser parser, Position position);

  void disableShrinking(ProguardConfigurationSourceParser parser, Position position);

  void enableProtoShrinking(ProguardConfigurationSourceParser parser, TextPosition positionStart);

  void setIgnoreWarnings(ProguardConfigurationSourceParser parser, TextPosition positionStart);

  void addDontWarnPattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  void addDontNotePattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  void enableAllowAccessModification(
      ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart);

  void setApplyMappingFile(
      Path applyMappingFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void addInjars(
      List<FilteredClassPath> filteredClassPaths,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void addLibraryJars(
      List<FilteredClassPath> filteredClassPaths,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void setObfuscationDictionary(
      Path path,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void setClassObfuscationDictionary(
      Path path,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void setPackageObfuscationDictionary(
      Path path,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void addAdaptClassStringsPattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  void addAdaptResourceFileContents(
      ProguardPathList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  void addAdaptResourceFilenames(
      ProguardPathList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  void joinMaxRemovedAndroidLogLevel(
      int maxRemovedAndroidLogLevel,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart);

  PackageObfuscationMode getPackageObfuscationMode();

  void enableRepackageClasses(
      String packagePrefix,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  void enableFlattenPackageHierarchy(
      String packagePrefix,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart);

  default void addWhitespace(ProguardConfigurationSourceParser parser, TextPosition position) {}
}
