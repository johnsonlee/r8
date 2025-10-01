// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.origin.Origin;
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

  private void handleGlobalRule(Origin origin, Position position, String rule) {
    reporter.error(new GlobalLibraryConsumerRuleDiagnostic(origin, position, rule));
  }

  private void handleKeepAttribute(Origin origin, Position position, String attribute) {
    reporter.error(new KeepAttributeLibraryConsumerRuleDiagnostic(origin, position, attribute));
  }

  @Override
  public void disableOptimization(Origin origin, Position position) {
    handleGlobalRule(origin, position, "-dontoptimize");
  }

  @Override
  public void disableObfuscation(Origin origin, Position position) {
    handleGlobalRule(origin, position, "-dontobfuscate");
  }

  @Override
  public void disableShrinking(Origin origin, Position position) {
    handleGlobalRule(origin, position, "-dontshrink");
  }

  @Override
  public void enableRepackageClasses(Origin origin, Position position) {
    handleGlobalRule(origin, position, "-repackageclasses");
  }

  @Override
  public void enableFlattenPackageHierarchy(Origin origin, Position position) {
    handleGlobalRule(origin, position, "-flattenpackagehierarchy");
  }

  @Override
  public void enableAllowAccessModification(Origin origin, Position position) {
    handleGlobalRule(origin, position, "-allowaccessmodification");
  }

  @Override
  public void setRenameSourceFileAttribute(String s, Origin origin, Position position) {
    handleGlobalRule(origin, position, "-renamesourcefileattribute");
  }

  @Override
  public void addParsedConfiguration(String s) {}

  @Override
  public void addRule(ProguardConfigurationRule rule) {}

  @Override
  public void addKeepAttributePatterns(
      List<String> attributesPatterns,
      Origin origin,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    // TODO(b/270289387): Add support for more attributes.
    ProguardKeepAttributes keepAttributes = ProguardKeepAttributes.fromPatterns(attributesPatterns);
    if (keepAttributes.lineNumberTable) {
      handleKeepAttribute(origin, position, ProguardKeepAttributes.LINE_NUMBER_TABLE);
    }
    if (keepAttributes.runtimeInvisibleAnnotations) {
      handleKeepAttribute(origin, position, ProguardKeepAttributes.RUNTIME_INVISIBLE_ANNOTATIONS);
    }
    if (keepAttributes.runtimeInvisibleTypeAnnotations) {
      handleKeepAttribute(
          origin, position, ProguardKeepAttributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS);
    }
    if (keepAttributes.runtimeInvisibleParameterAnnotations) {
      handleKeepAttribute(
          origin, position, ProguardKeepAttributes.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS);
    }
    if (keepAttributes.sourceFile) {
      handleKeepAttribute(origin, position, ProguardKeepAttributes.SOURCE_FILE);
    }
  }

  @Override
  public void addKeepPackageNamesPattern(ProguardClassNameList proguardClassNameList) {}

  @Override
  public void setKeepParameterNames(boolean b, Origin origin, Position position) {}

  @Override
  public void enableKeepDirectories() {}

  @Override
  public void addKeepDirectories(ProguardPathList proguardPathList) {}

  @Override
  public void setPrintUsage(boolean b) {}

  @Override
  public void setPrintUsageFile(Path path) {}

  @Override
  public void enableProtoShrinking() {}

  @Override
  public void setIgnoreWarnings(boolean b) {}

  @Override
  public void addDontWarnPattern(ProguardClassNameList pattern) {}

  @Override
  public void addDontNotePattern(ProguardClassNameList pattern) {}

  @Override
  public void enablePrintConfiguration(Origin origin, Position position) {
    handleGlobalRule(origin, position, "-printconfiguration");
  }

  @Override
  public void setPrintConfigurationFile(Path path) {}

  @Override
  public void enablePrintMapping(Origin origin, Position position) {
    handleGlobalRule(origin, position, "-printmapping");
  }

  @Override
  public void setPrintMappingFile(Path path) {}

  @Override
  public void setApplyMappingFile(Path path, Origin origin, Position position) {
    handleGlobalRule(origin, position, "-applymapping");
  }

  @Override
  public void addInjars(
      List<FilteredClassPath> filteredClassPaths, Origin origin, Position position) {
    handleGlobalRule(origin, position, "-injars");
  }

  @Override
  public void addLibraryJars(
      List<FilteredClassPath> filteredClassPaths, Origin origin, Position position) {
    handleGlobalRule(origin, position, "-libraryjars");
  }

  @Override
  public void setPrintSeeds(boolean b, Origin origin, Position position) {
    handleGlobalRule(origin, position, "-printseeds");
  }

  @Override
  public void setSeedFile(Path path) {}

  @Override
  public void setObfuscationDictionary(Path path, Origin origin, Position position) {
    handleGlobalRule(origin, position, "-obfuscationdictionary");
  }

  @Override
  public void setClassObfuscationDictionary(Path path, Origin origin, Position position) {
    handleGlobalRule(origin, position, "-classobfuscationdictionary");
  }

  @Override
  public void setPackageObfuscationDictionary(Path path, Origin origin, Position position) {
    handleGlobalRule(origin, position, "-packageobfuscationdictionary");
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
