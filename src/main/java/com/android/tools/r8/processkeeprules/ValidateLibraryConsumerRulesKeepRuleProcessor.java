// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardConfigurationParser.ProguardConfigurationSourceParser;
import com.android.tools.r8.shaking.ProguardConfigurationParserConsumer;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.shaking.ProguardPathList;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.util.List;

class ValidateLibraryConsumerRulesKeepRuleProcessor implements ProguardConfigurationParserConsumer {
  private final Reporter reporter;

  public ValidateLibraryConsumerRulesKeepRuleProcessor(Reporter reporter) {
    super();
    this.reporter = reporter;
  }

  private void handleGlobalRule(
      ProguardConfigurationSourceParser parser, Position position, String rule) {
    reporter.error(new GlobalLibraryConsumerRuleDiagnostic(parser.getOrigin(), position, rule));
  }

  private void handleKeepAttribute(
      ProguardConfigurationSourceParser parser, Position position, String attribute) {
    reporter.error(
        new KeepAttributeLibraryConsumerRuleDiagnostic(parser.getOrigin(), position, attribute));
  }

  @Override
  public void disableOptimization(ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-dontoptimize");
  }

  @Override
  public void disableObfuscation(ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-dontobfuscate");
  }

  @Override
  public void disableShrinking(ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-dontshrink");
  }

  @Override
  public void enableRepackageClasses(ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-repackageclasses");
  }

  @Override
  public void enableFlattenPackageHierarchy(
      ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-flattenpackagehierarchy");
  }

  @Override
  public void enableAllowAccessModification(
      ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-allowaccessmodification");
  }

  @Override
  public void setRenameSourceFileAttribute(
      String s,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleGlobalRule(parser, position, "-renamesourcefileattribute");
  }

  @Override
  public void addParsedConfiguration(String s) {}

  @Override
  public void addRule(ProguardConfigurationRule rule) {}

  @Override
  public void addKeepAttributePatterns(
      List<String> attributesPatterns,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    // TODO(b/270289387): Add support for more attributes.
    ProguardKeepAttributes keepAttributes = ProguardKeepAttributes.fromPatterns(attributesPatterns);
    if (keepAttributes.lineNumberTable) {
      handleKeepAttribute(parser, position, ProguardKeepAttributes.LINE_NUMBER_TABLE);
    }
    if (keepAttributes.runtimeInvisibleAnnotations) {
      handleKeepAttribute(parser, position, ProguardKeepAttributes.RUNTIME_INVISIBLE_ANNOTATIONS);
    }
    if (keepAttributes.runtimeInvisibleTypeAnnotations) {
      handleKeepAttribute(
          parser, position, ProguardKeepAttributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS);
    }
    if (keepAttributes.runtimeInvisibleParameterAnnotations) {
      handleKeepAttribute(
          parser, position, ProguardKeepAttributes.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS);
    }
    if (keepAttributes.sourceFile) {
      handleKeepAttribute(parser, position, ProguardKeepAttributes.SOURCE_FILE);
    }
  }

  @Override
  public void addKeepPackageNamesPattern(ProguardClassNameList proguardClassNameList) {}

  @Override
  public void setKeepParameterNames(ProguardConfigurationSourceParser parser, Position position) {}

  @Override
  public void enableKeepDirectories(
      ProguardPathList keepDirectoryPatterns,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {}

  @Override
  public void enableProtoShrinking() {}

  @Override
  public void setIgnoreWarnings() {}

  @Override
  public void addDontWarnPattern(ProguardClassNameList pattern) {}

  @Override
  public void addDontNotePattern(ProguardClassNameList pattern) {}

  @Override
  public void enablePrintConfiguration(
      Path printConfigurationFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleGlobalRule(parser, position, "-printconfiguration");
  }

  @Override
  public void enablePrintMapping(
      Path printMappingFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleGlobalRule(parser, position, "-printmapping");
  }

  @Override
  public void enablePrintSeeds(
      Path printSeedsFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleGlobalRule(parser, position, "-printseeds");
  }

  @Override
  public void enablePrintUsage(
      Path printUsageFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleGlobalRule(parser, position, "-printusage");
  }

  @Override
  public void setApplyMappingFile(
      Path path, ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-applymapping");
  }

  @Override
  public void addInjars(
      List<FilteredClassPath> filteredClassPaths,
      ProguardConfigurationSourceParser parser,
      Position position) {
    handleGlobalRule(parser, position, "-injars");
  }

  @Override
  public void addLibraryJars(
      List<FilteredClassPath> filteredClassPaths,
      ProguardConfigurationSourceParser parser,
      Position position) {
    handleGlobalRule(parser, position, "-libraryjars");
  }

  @Override
  public void setObfuscationDictionary(
      Path path, ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-obfuscationdictionary");
  }

  @Override
  public void setClassObfuscationDictionary(
      Path path, ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-classobfuscationdictionary");
  }

  @Override
  public void setPackageObfuscationDictionary(
      Path path, ProguardConfigurationSourceParser parser, Position position) {
    handleGlobalRule(parser, position, "-packageobfuscationdictionary");
  }

  @Override
  public void addAdaptClassStringsPattern(ProguardClassNameList pattern) {}

  @Override
  public void addAdaptResourceFileContents(ProguardPathList pattern) {}

  @Override
  public void addAdaptResourceFilenames(ProguardPathList pattern) {}

  @Override
  public void joinMaxRemovedAndroidLogLevel(int maxRemovedAndroidLogLevel) {}

  @Override
  public PackageObfuscationMode getPackageObfuscationMode() {
    return null;
  }

  @Override
  public void setPackagePrefix(String s) {}

  @Override
  public void setFlattenPackagePrefix(String s) {}
}
