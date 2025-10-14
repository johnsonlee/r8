// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.StringConsumer;
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
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;

// TODO(b/437139566): This is not yet feature complete. Add support for writing all directives.
// TODO(b/437139566): Implement filtering by commenting out rules.
public class FilteredKeepRulesBuilder implements ProguardConfigurationParserConsumer {

  private final StringConsumer consumer;
  private final Reporter reporter;

  private boolean isInComment;

  FilteredKeepRulesBuilder(StringConsumer consumer, Reporter reporter) {
    this.consumer = consumer;
    this.reporter = reporter;
  }

  private void appendToCurrentLine(String string) {
    assert string.indexOf('\n') < 0;
    consumer.accept(string, reporter);
    if (!isInComment && string.indexOf('#') >= 0) {
      isInComment = true;
    }
  }

  private void ensureComment() {
    if (!isInComment) {
      appendToCurrentLine("#");
      isInComment = true;
    }
  }

  private void ensureNewlineAfterComment() {
    if (isInComment) {
      exitComment();
      consumer.accept(StringUtils.UNIX_LINE_SEPARATOR, reporter);
    }
  }

  private void exitComment() {
    isInComment = false;
  }

  private void write(ProguardConfigurationSourceParser parser, TextPosition positionStart) {
    write(parser.getContentSince(positionStart));
  }

  private void write(String string) {
    int lastNewlineIndex = string.lastIndexOf('\n');
    if (lastNewlineIndex < 0) {
      appendToCurrentLine(string);
    } else {
      // Write the lines leading up to the last line.
      String untilNewlineInclusive = string.substring(0, lastNewlineIndex + 1);
      consumer.accept(untilNewlineInclusive, reporter);
      // Due to the newline character we are no longer inside a comment.
      exitComment();
      // Emit everything after the newline character.
      String fromNewlineExclusive = string.substring(lastNewlineIndex + 1);
      appendToCurrentLine(fromNewlineExclusive);
    }
  }

  @Override
  public void addAdaptClassStringsPattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void addDontNotePattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void addDontWarnPattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void addKeepAttributePatterns(
      List<String> attributesPatterns,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void addKeepPackageNamesPattern(
      ProguardClassNameList proguardClassNameList,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void addRule(ProguardConfigurationRule rule) {
    ensureNewlineAfterComment();
    write(rule.getSource());
  }

  @Override
  public void addWhitespace(ProguardConfigurationSourceParser parser, TextPosition positionStart) {
    write(parser, positionStart);
  }

  @Override
  public void disableObfuscation(ProguardConfigurationSourceParser parser, Position position) {
    ensureComment();
    write("-dontobfuscate");
  }

  @Override
  public void disableOptimization(ProguardConfigurationSourceParser parser, Position position) {
    ensureComment();
    write("-dontoptimize");
  }

  @Override
  public void disableShrinking(ProguardConfigurationSourceParser parser, Position position) {
    ensureComment();
    write("-dontshrink");
  }

  @Override
  public void enableAllowAccessModification(
      ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void enableKeepDirectories(
      ProguardPathList keepDirectoryPatterns,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void enablePrintConfiguration(
      Path printConfigurationFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void enablePrintMapping(
      Path printMappingFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void enablePrintSeeds(
      Path printSeedsFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void enablePrintUsage(
      Path printUsageFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void enableProtoShrinking(
      ProguardConfigurationSourceParser parser, TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void setIgnoreWarnings(
      ProguardConfigurationSourceParser parser, TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void setRenameSourceFileAttribute(
      String s,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    ensureNewlineAfterComment();
    write(parser, positionStart);
  }

  @Override
  public void setKeepParameterNames(ProguardConfigurationSourceParser parser, Position position) {}

  @Override
  public void addParsedConfiguration(String s) {}

  @Override
  public void setApplyMappingFile(
      Path path, ProguardConfigurationSourceParser parser, Position position) {}

  @Override
  public void addInjars(
      List<FilteredClassPath> filteredClassPaths,
      ProguardConfigurationSourceParser parser,
      Position position) {}

  @Override
  public void addLibraryJars(
      List<FilteredClassPath> filteredClassPaths,
      ProguardConfigurationSourceParser parser,
      Position position) {}

  @Override
  public void setObfuscationDictionary(
      Path path, ProguardConfigurationSourceParser parser, Position position) {}

  @Override
  public void setClassObfuscationDictionary(
      Path path, ProguardConfigurationSourceParser parser, Position position) {}

  @Override
  public void setPackageObfuscationDictionary(
      Path path, ProguardConfigurationSourceParser parser, Position position) {}

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
  public void enableRepackageClasses(ProguardConfigurationSourceParser parser, Position position) {}

  @Override
  public void enableFlattenPackageHierarchy(
      ProguardConfigurationSourceParser parser, Position position) {}
}
