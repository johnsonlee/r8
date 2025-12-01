// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardConfiguration.ProcessKotlinNullChecks;
import com.android.tools.r8.shaking.ProguardConfigurationParser.ProguardConfigurationSourceParser;
import com.android.tools.r8.shaking.ProguardConfigurationParserConsumer;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.shaking.ProguardPathList;
import com.android.tools.r8.shaking.WhyAreYouKeepingRule;
import com.android.tools.r8.shaking.WhyAreYouNotInliningRule;
import com.android.tools.r8.shaking.WhyAreYouNotObfuscatingRule;
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

  private void handleRule(
      ProguardConfigurationSourceParser parser, Position position, String rule) {
    reporter.error(new LibraryConsumerRuleDiagnostic(parser.getOrigin(), position, rule));
  }

  private void handleKeepAttribute(
      ProguardConfigurationSourceParser parser, Position position, String attribute) {
    reporter.error(
        new KeepAttributeLibraryConsumerRuleDiagnostic(parser.getOrigin(), position, attribute));
  }

  @Override
  public void disableOptimization(ProguardConfigurationSourceParser parser, Position position) {
    handleRule(parser, position, "-dontoptimize");
  }

  @Override
  public void disableObfuscation(ProguardConfigurationSourceParser parser, Position position) {
    handleRule(parser, position, "-dontobfuscate");
  }

  @Override
  public void disableRepackaging(ProguardConfigurationSourceParser parser, Position position) {
    handleRule(parser, position, "-dontrepackage");
  }

  @Override
  public void disableShrinking(ProguardConfigurationSourceParser parser, Position position) {
    handleRule(parser, position, "-dontshrink");
  }

  @Override
  public void enableRepackageClasses(
      String packagePrefix,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-repackageclasses");
  }

  @Override
  public void enableFlattenPackageHierarchy(
      String packagePrefix,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-flattenpackagehierarchy");
  }

  @Override
  public void enableAllowAccessModification(
      ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart) {
    handleRule(parser, position, "-allowaccessmodification");
  }

  @Override
  public void setRenameSourceFileAttribute(
      String s,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-renamesourcefileattribute");
  }

  @Override
  public void addBaseDirectory(
      Path baseDirectory, ProguardConfigurationSourceParser parser, TextPosition positionStart) {}

  @Override
  public void addIgnoredOption(
      String option, ProguardConfigurationSourceParser parser, TextPosition positionStart) {}

  @Override
  public void addInclude(
      Path includePath, ProguardConfigurationSourceParser parser, TextPosition positionStart) {
    // TODO(b/270289387): Report error.
  }

  @Override
  public void addLeadingBOM() {}

  @Override
  public void addParsedConfiguration(ProguardConfigurationSourceParser parser) {}

  @Override
  public void addRule(
      ProguardConfigurationRule rule,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {
    if (rule instanceof WhyAreYouKeepingRule) {
      handleRule(parser, positionStart, "-whyareyoukeeping");
    } else if (rule instanceof WhyAreYouNotInliningRule) {
      handleRule(parser, positionStart, "-whyareyounotinlining");
    } else if (rule instanceof WhyAreYouNotObfuscatingRule) {
      handleRule(parser, positionStart, "-whyareyounotobfuscating");
    }
  }

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
  public void addKeepKotlinMetadata(
      ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart) {}

  @Override
  public void addProcessKotlinNullChecks(
      ProcessKotlinNullChecks value,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, positionStart, "-processkotlinnullchecks");
  }

  @Override
  public void addKeepPackageNamesPattern(
      ProguardClassNameList proguardClassNameList,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {}

  @Override
  public void setKeepParameterNames(
      ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart) {}

  @Override
  public void enableKeepDirectories(
      ProguardPathList keepDirectoryPatterns,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {}

  @Override
  public void enableProtoShrinking(
      ProguardConfigurationSourceParser parser, TextPosition positionStart) {
    handleRule(parser, positionStart, "-shrinkunusedprotofields");
  }

  @Override
  public void setIgnoreWarnings(
      ProguardConfigurationSourceParser parser, TextPosition positionStart) {}

  @Override
  public void addDontWarnPattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {}

  @Override
  public void addDontNotePattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {}

  @Override
  public void enablePrintConfiguration(
      Path printConfigurationFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-printconfiguration");
  }

  @Override
  public void enablePrintMapping(
      Path printMappingFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-printmapping");
  }

  @Override
  public void enablePrintSeeds(
      Path printSeedsFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-printseeds");
  }

  @Override
  public void enablePrintUsage(
      Path printUsageFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-printusage");
  }

  @Override
  public void setApplyMappingFile(
      Path applyMappingFile,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-applymapping");
  }

  @Override
  public void addInjars(
      List<FilteredClassPath> filteredClassPaths,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-injars");
  }

  @Override
  public void addLibraryJars(
      List<FilteredClassPath> filteredClassPaths,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-libraryjars");
  }

  @Override
  public void setObfuscationDictionary(
      Path path,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-obfuscationdictionary");
  }

  @Override
  public void setClassObfuscationDictionary(
      Path path,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-classobfuscationdictionary");
  }

  @Override
  public void setPackageObfuscationDictionary(
      Path path,
      ProguardConfigurationSourceParser parser,
      Position position,
      TextPosition positionStart) {
    handleRule(parser, position, "-packageobfuscationdictionary");
  }

  @Override
  public void addAdaptClassStringsPattern(
      ProguardClassNameList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {}

  @Override
  public void addAdaptResourceFileContents(
      ProguardPathList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {}

  @Override
  public void addAdaptResourceFilenames(
      ProguardPathList pattern,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {}

  @Override
  public void joinMaxRemovedAndroidLogLevel(
      int maxRemovedAndroidLogLevel,
      ProguardConfigurationSourceParser parser,
      TextPosition positionStart) {
    handleRule(parser, positionStart, "-maximumremovedandroidloglevel <int>");
  }

  @Override
  public PackageObfuscationMode getPackageObfuscationMode() {
    return null;
  }
}
