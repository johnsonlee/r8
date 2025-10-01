// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardConfigurationParser.ProguardConfigurationSourceParser;
import com.android.tools.r8.shaking.ProguardConfigurationParserConsumer;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardPathList;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.util.List;

// TODO(b/437139566): This is not yet feature complete. Add support for writing all directives.
// TODO(b/437139566): Implement filtering by commenting out rules.
public class FilteredKeepRulesBuilder implements ProguardConfigurationParserConsumer {

  private final StringConsumer consumer;
  private final Reporter reporter;

  FilteredKeepRulesBuilder(StringConsumer consumer, Reporter reporter) {
    this.consumer = consumer;
    this.reporter = reporter;
  }

  private void write(ProguardConfigurationSourceParser parser, TextPosition positionStart) {
    write(parser.getContentSince(positionStart));
  }

  private void write(String string) {
    consumer.accept(string, reporter);
  }

  @Override
  public void addKeepAttributePatterns(
      List<String> attributesPatterns,
      Origin origin,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    write(parser, positionStart);
  }

  @Override
  public void addRule(ProguardConfigurationRule rule) {
    write(rule.getSource());
  }

  @Override
  public void addWhitespace(ProguardConfigurationSourceParser parser, TextPosition positionStart) {
    write(parser, positionStart);
  }

  @Override
  public void disableObfuscation(Origin origin, Position position) {
    write("-dontobfuscate");
  }

  @Override
  public void disableOptimization(Origin origin, Position position) {
    write("-dontoptimize");
  }

  @Override
  public void disableShrinking(Origin origin, Position position) {
    write("-dontshrink");
  }

  @Override
  public void setRenameSourceFileAttribute(String s, Origin origin, Position position) {}

  @Override
  public void addKeepPackageNamesPattern(ProguardClassNameList proguardClassNameList) {}

  @Override
  public void setKeepParameterNames(boolean b, Origin origin, Position position) {}

  @Override
  public void enableKeepDirectories() {}

  @Override
  public void addKeepDirectories(ProguardPathList proguardPathList) {}

  @Override
  public void addParsedConfiguration(String s) {}

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
  public void enableAllowAccessModification(Origin origin, Position position) {}

  @Override
  public void enablePrintConfiguration(Origin origin, Position position) {}

  @Override
  public void setPrintConfigurationFile(Path path) {}

  @Override
  public void enablePrintMapping(Origin origin, Position position) {}

  @Override
  public void setPrintMappingFile(Path path) {}

  @Override
  public void setApplyMappingFile(Path path, Origin origin, Position position) {}

  @Override
  public void addInjars(
      List<FilteredClassPath> filteredClassPaths, Origin origin, Position position) {}

  @Override
  public void addLibraryJars(
      List<FilteredClassPath> filteredClassPaths, Origin origin, Position position) {}

  @Override
  public void setPrintSeeds(boolean b, Origin origin, Position position) {}

  @Override
  public void setSeedFile(Path path) {}

  @Override
  public void setObfuscationDictionary(Path path, Origin origin, Position position) {}

  @Override
  public void setClassObfuscationDictionary(Path path, Origin origin, Position position) {}

  @Override
  public void setPackageObfuscationDictionary(Path path, Origin origin, Position position) {}

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

  @Override
  public void enableRepackageClasses(Origin origin, Position position) {}

  @Override
  public void enableFlattenPackageHierarchy(Origin origin, Position position) {}
}
