// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.shaking.ProguardConfiguration.ProcessKotlinNullChecks.DEFAULT;
import static com.android.tools.r8.shaking.ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS;

import com.android.tools.r8.errors.dontwarn.DontWarnConfiguration;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.DictionaryReader;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IncludeWorkItem;
import com.android.tools.r8.shaking.ProguardConfigurationParser.ProguardConfigurationSourceParser;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ProguardConfiguration {

  public enum ProcessKotlinNullChecks {
    DEFAULT,
    KEEP,
    REMOVE_MESSAGE,
    REMOVE;

    public boolean isRemoveMessage() {
      return this == DEFAULT || this == REMOVE_MESSAGE;
    }

    public boolean isRemove() {
      return this == REMOVE;
    }

    public ProcessKotlinNullChecks meet(ProcessKotlinNullChecks other) {
      assert other != DEFAULT;
      return other.ordinal() > ordinal() ? other : this;
    }
  }

  public static class Builder implements ProguardConfigurationParserConsumer {

    private final StringBuilder parsedConfiguration = new StringBuilder();
    private final List<FilteredClassPath> injars = new ArrayList<>();

    private final List<FilteredClassPath> libraryJars = new ArrayList<>();

    private final Reporter reporter;
    private boolean allowAccessModification;
    private boolean ignoreWarnings;
    private boolean optimizing = true;
    private boolean obfuscating = true;
    private boolean shrinking = true;
    private boolean printConfiguration;
    private Path printConfigurationFile;
    private boolean printUsage;
    private Path printUsageFile;
    private boolean printMapping;
    private Path printMappingFile;
    private Path applyMappingFile;
    private String renameSourceFileAttribute;
    private final List<String> keepAttributePatterns = new ArrayList<>();
    private final ProguardClassFilter.Builder keepPackageNamesPatterns =
        ProguardClassFilter.builder();
    private final ProguardClassFilter.Builder dontWarnPatterns = ProguardClassFilter.builder();
    private final ProguardClassFilter.Builder dontNotePatterns = ProguardClassFilter.builder();
    protected final Set<ProguardConfigurationRule> rules = Sets.newLinkedHashSet();
    private final DexItemFactory dexItemFactory;
    private boolean printSeeds;
    private Path printSeedsFile;
    private Path obfuscationDictionary;
    private Path classObfuscationDictionary;
    private Path packageObfuscationDictionary;
    private boolean keepParameterNames;
    private final ProguardClassFilter.Builder adaptClassStrings = ProguardClassFilter.builder();
    private final ProguardPathFilter.Builder adaptResourceFilenames =
        ProguardPathFilter.builder()
            .addPattern(ProguardPathList.builder().addFileName("META-INF/services/*").build());
    private final ProguardPathFilter.Builder adaptResourceFileContents =
        ProguardPathFilter.builder()
            .addPattern(ProguardPathList.builder().addFileName("META-INF/services/*").build());
    private final ProguardPathFilter.Builder keepDirectories =
        ProguardPathFilter.builder().disable();
    private boolean forceProguardCompatibility = false;
    private boolean protoShrinking = false;
    private int maxRemovedAndroidLogLevel = MaximumRemovedAndroidLogLevelRule.NOT_SET;
    private ProcessKotlinNullChecks processKotlinNullChecks = DEFAULT;
    PackageObfuscationMode packageObfuscationMode = PackageObfuscationMode.NONE;
    String packagePrefix = "";

    private Builder(DexItemFactory dexItemFactory, Reporter reporter) {
      this.dexItemFactory = dexItemFactory;
      this.reporter = reporter;
    }

    public List<FilteredClassPath> getInjars() {
      return injars;
    }

    @Override
    public void addBaseDirectory(
        Path baseDirectory, ProguardConfigurationSourceParser parser, TextPosition positionStart) {
      // Intentionally empty.
    }

    @Override
    public void addIgnoredOption(
        String option, ProguardConfigurationSourceParser parser, TextPosition positionStart) {
      // Intentionally empty.
    }

    @Override
    public void addInclude(
        Path includePath, ProguardConfigurationSourceParser parser, TextPosition positionStart) {
      IncludeWorkItem include = new IncludeWorkItem(includePath, positionStart, parser.getOffset());
      parser.getPendingIncludes().add(include);
    }

    @Override
    public void addLeadingBOM() {
      // Intentionally empty.
    }

    @Override
    public void addParsedConfiguration(ProguardConfigurationSourceParser parser) {
      parsedConfiguration.append(
          "# The proguard configuration file for the following section is " + parser.getOrigin());
      parsedConfiguration.append(System.lineSeparator());
      int lastIncludePositionEnd = 0;
      for (IncludeWorkItem pendingInclude : parser.getPendingIncludes()) {
        int includePositionStart = pendingInclude.includePositionStart.getOffsetAsInt();
        parsedConfiguration.append(
            parser.getContentInRange(lastIncludePositionEnd, includePositionStart));
        lastIncludePositionEnd = pendingInclude.includePositionEnd;
      }
      parsedConfiguration.append(parser.getContentAfter(lastIncludePositionEnd));
      parsedConfiguration.append(System.lineSeparator());
      parsedConfiguration.append("# End of content from ");
      parsedConfiguration.append(parser.getOrigin());
      parsedConfiguration.append(System.lineSeparator());
    }

    @Override
    public void addInjars(
        List<FilteredClassPath> injars,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.injars.addAll(injars);
    }

    @Override
    public void addLibraryJars(
        List<FilteredClassPath> libraryJars,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.libraryJars.addAll(libraryJars);
    }

    @Override
    public void enableAllowAccessModification(
        ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart) {
      this.allowAccessModification = true;
    }

    @Override
    public void setIgnoreWarnings(
        ProguardConfigurationSourceParser parser, TextPosition positionStart) {
      this.ignoreWarnings = true;
    }

    @Override
    public void disableOptimization(ProguardConfigurationSourceParser parser, Position position) {
      this.optimizing = false;
    }

    @Override
    public void disableObfuscation(ProguardConfigurationSourceParser parser, Position position) {
      this.obfuscating = false;
    }

    @Override
    public void disableShrinking(ProguardConfigurationSourceParser parser, Position position) {
      this.shrinking = false;
    }

    public Builder disableOptimization() {
      this.optimizing = false;
      return this;
    }

    public Builder disableObfuscation() {
      this.obfuscating = false;
      return this;
    }

    boolean isObfuscating() {
      return obfuscating;
    }

    public boolean isOptimizing() {
      return optimizing;
    }

    public boolean isShrinking() {
      return shrinking;
    }

    public Builder disableShrinking() {
      shrinking = false;
      return this;
    }

    @Override
    public void enablePrintConfiguration(
        Path printConfigurationFile,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.printConfiguration = true;
      this.printConfigurationFile = printConfigurationFile;
    }

    @Override
    public void enablePrintUsage(
        Path printUsageFile,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.printUsage = true;
      this.printUsageFile = printUsageFile;
    }

    @Override
    public void enablePrintMapping(
        Path printMappingFile,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.printMapping = true;
      this.printMappingFile = printMappingFile;
    }

    @Override
    public void setApplyMappingFile(
        Path applyMappingFile,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.applyMappingFile = applyMappingFile;
    }

    @Override
    public void setRenameSourceFileAttribute(
        String renameSourceFileAttribute,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.renameSourceFileAttribute = renameSourceFileAttribute;
    }

    @Override
    public void addKeepAttributePatterns(
        List<String> keepAttributePatterns,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.keepAttributePatterns.addAll(keepAttributePatterns);
    }

    @Override
    public void addKeepKotlinMetadata(
        ProguardConfigurationSourceParser parser, Position position, TextPosition positionStart) {
      Origin origin = parser.getOrigin();
      String source = "-keepkotlinmetadata";
      ProguardKeepRule keepKotlinMetadata =
          ProguardKeepRuleUtils.keepClassAndMembersRule(
              origin, positionStart, dexItemFactory.kotlinMetadataType, source);
      // Mark the rules as used to ensure we do not report any information messages if the class
      // is not present.
      keepKotlinMetadata.markAsUsed();
      addRule(keepKotlinMetadata, parser, positionStart);
      addKeepAttributePatterns(
          Collections.singletonList(RUNTIME_VISIBLE_ANNOTATIONS), parser, position, positionStart);
    }

    @Override
    public void addProcessKotlinNullChecks(
        ProcessKotlinNullChecks value,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      processKotlinNullChecks = processKotlinNullChecks.meet(value);
    }

    public Builder addKeepAttributePatterns(List<String> keepAttributePatterns) {
      this.keepAttributePatterns.addAll(keepAttributePatterns);
      return this;
    }

    @Override
    public void addRule(
        ProguardConfigurationRule rule,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      this.rules.add(rule);
    }

    @Override
    public void addKeepPackageNamesPattern(
        ProguardClassNameList pattern,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      keepPackageNamesPatterns.addPattern(pattern);
    }

    @Override
    public void joinMaxRemovedAndroidLogLevel(
        int maxRemovedAndroidLogLevel,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      assert maxRemovedAndroidLogLevel >= MaximumRemovedAndroidLogLevelRule.NONE;
      if (this.maxRemovedAndroidLogLevel == MaximumRemovedAndroidLogLevelRule.NOT_SET) {
        this.maxRemovedAndroidLogLevel = maxRemovedAndroidLogLevel;
      } else {
        // If there are multiple -maximumremovedandroidloglevel rules we only allow removing logging
        // calls that are removable according to all rules.
        this.maxRemovedAndroidLogLevel =
            Math.min(this.maxRemovedAndroidLogLevel, maxRemovedAndroidLogLevel);
      }
    }

    public int getMaxRemovedAndroidLogLevel() {
      return maxRemovedAndroidLogLevel;
    }

    @Override
    public PackageObfuscationMode getPackageObfuscationMode() {
      return packageObfuscationMode;
    }

    @Override
    public void enableFlattenPackageHierarchy(
        String packagePrefix,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      packageObfuscationMode = PackageObfuscationMode.FLATTEN;
      this.packagePrefix = packagePrefix;
    }

    @Override
    public void enableRepackageClasses(
        String packagePrefix,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      packageObfuscationMode = PackageObfuscationMode.REPACKAGE;
      this.packagePrefix = packagePrefix;
    }

    @Override
    public void addDontWarnPattern(
        ProguardClassNameList pattern,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      dontWarnPatterns.addPattern(pattern);
    }

    @Override
    public void addDontNotePattern(
        ProguardClassNameList pattern,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      dontNotePatterns.addPattern(pattern);
    }

    @Override
    public void enablePrintSeeds(
        Path printSeedsFile,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.printSeeds = true;
      this.printSeedsFile = printSeedsFile;
    }

    @Override
    public void setObfuscationDictionary(
        Path obfuscationDictionary,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.obfuscationDictionary = obfuscationDictionary;
    }

    @Override
    public void setClassObfuscationDictionary(
        Path classObfuscationDictionary,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.classObfuscationDictionary = classObfuscationDictionary;
    }

    @Override
    public void setPackageObfuscationDictionary(
        Path packageObfuscationDictionary,
        ProguardConfigurationSourceParser parser,
        Position position,
        TextPosition positionStart) {
      this.packageObfuscationDictionary = packageObfuscationDictionary;
    }

    @Override
    public void setKeepParameterNames(
        ProguardConfigurationSourceParser optionOrigin,
        Position optionPosition,
        TextPosition positionStart) {
      assert optionOrigin != null || !keepParameterNames;
      this.keepParameterNames = true;
    }

    @Override
    public void addAdaptClassStringsPattern(
        ProguardClassNameList pattern,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      adaptClassStrings.addPattern(pattern);
    }

    @Override
    public void addAdaptResourceFilenames(
        ProguardPathList pattern,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      adaptResourceFilenames.addPattern(pattern);
    }

    public Builder applyAdaptResourceFilenamesBuilder(
        Consumer<ProguardPathFilter.Builder> consumer) {
      consumer.accept(adaptResourceFilenames);
      return this;
    }

    @Override
    public void addAdaptResourceFileContents(
        ProguardPathList pattern,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      adaptResourceFileContents.addPattern(pattern);
    }

    @Override
    public void enableKeepDirectories(
        ProguardPathList keepDirectoryPatterns,
        ProguardConfigurationSourceParser parser,
        TextPosition positionStart) {
      keepDirectories.enable().addPattern(keepDirectoryPatterns);
    }

    public boolean isForceProguardCompatibility() {
      return forceProguardCompatibility;
    }

    public Builder setForceProguardCompatibility(boolean forceProguardCompatibility) {
      this.forceProguardCompatibility = forceProguardCompatibility;
      return this;
    }

    @Override
    public void enableProtoShrinking(
        ProguardConfigurationSourceParser parser, TextPosition positionStart) {
      protoShrinking = true;
    }

    public ProguardConfiguration buildRaw() {
      ProguardKeepAttributes proguardKeepAttributes =
          ProguardKeepAttributes.fromPatterns(keepAttributePatterns);
      // For Proguard -keepattributes are only applicable when obfuscating.
      if (forceProguardCompatibility && !isObfuscating()) {
        proguardKeepAttributes.keepAllAttributesExceptRuntimeInvisibleAnnotations();
      }
      ProguardConfiguration configuration =
          new ProguardConfiguration(
              parsedConfiguration.toString(),
              dexItemFactory,
              injars,
              libraryJars,
              packageObfuscationMode,
              packagePrefix,
              allowAccessModification,
              ignoreWarnings,
              optimizing,
              obfuscating,
              shrinking,
              printConfiguration,
              printConfigurationFile,
              printUsage,
              printUsageFile,
              printMapping,
              printMappingFile,
              applyMappingFile,
              renameSourceFileAttribute,
              proguardKeepAttributes,
              keepPackageNamesPatterns.build(),
              dontWarnPatterns.build(),
              dontNotePatterns.build(),
              rules,
              printSeeds,
              printSeedsFile,
              DictionaryReader.readAllNames(obfuscationDictionary, reporter),
              DictionaryReader.readAllNames(classObfuscationDictionary, reporter),
              DictionaryReader.readAllNames(packageObfuscationDictionary, reporter),
              keepParameterNames,
              adaptClassStrings.build(),
              adaptResourceFilenames.build(),
              adaptResourceFileContents.build(),
              keepDirectories.build(),
              protoShrinking,
              getMaxRemovedAndroidLogLevel(),
              processKotlinNullChecks);

      reporter.failIfPendingErrors();

      return configuration;
    }

    public ProguardConfiguration build() {

      if (packageObfuscationMode == PackageObfuscationMode.NONE && obfuscating) {
        packageObfuscationMode = PackageObfuscationMode.MINIFICATION;
      }

      return buildRaw();
    }
  }

  private final String parsedConfiguration;
  private final DexItemFactory dexItemFactory;
  private final ImmutableList<FilteredClassPath> injars;
  private final ImmutableList<FilteredClassPath> libraryJars;
  private final PackageObfuscationMode packageObfuscationMode;
  private final String packagePrefix;
  private final boolean allowAccessModification;
  private final boolean ignoreWarnings;
  private final boolean optimizing;
  private final boolean obfuscating;
  private final boolean shrinking;
  private final boolean printConfiguration;
  private final Path printConfigurationFile;
  private final boolean printUsage;
  private final Path printUsageFile;
  private final boolean printMapping;
  private final Path printMappingFile;
  private final Path applyMappingFile;
  private final String renameSourceFileAttribute;
  private final ProguardKeepAttributes keepAttributes;
  private ProguardClassFilter keepPackageNamesPatterns;
  private final ProguardClassFilter dontWarnPatterns;
  private final ProguardClassFilter dontNotePatterns;
  protected final ImmutableList<ProguardConfigurationRule> rules;
  private final boolean printSeeds;
  private final Path seedFile;
  private final ImmutableList<String> obfuscationDictionary;
  private final ImmutableList<String> classObfuscationDictionary;
  private final ImmutableList<String> packageObfuscationDictionary;
  private final boolean keepParameterNames;
  private final ProguardClassFilter adaptClassStrings;
  private final ProguardPathFilter adaptResourceFilenames;
  private final ProguardPathFilter adaptResourceFileContents;
  private final ProguardPathFilter keepDirectories;
  private final boolean protoShrinking;
  private final int maxRemovedAndroidLogLevel;
  private final boolean hasWhyAreYouNotInliningRule;
  private final boolean hasWhyAreYouNotObfuscatingRule;
  private final ProcessKotlinNullChecks processKotlinNullChecks;

  private ProguardConfiguration(
      String parsedConfiguration,
      DexItemFactory factory,
      List<FilteredClassPath> injars,
      List<FilteredClassPath> libraryJars,
      PackageObfuscationMode packageObfuscationMode,
      String packagePrefix,
      boolean allowAccessModification,
      boolean ignoreWarnings,
      boolean optimizing,
      boolean obfuscating,
      boolean shrinking,
      boolean printConfiguration,
      Path printConfigurationFile,
      boolean printUsage,
      Path printUsageFile,
      boolean printMapping,
      Path printMappingFile,
      Path applyMappingFile,
      String renameSourceFileAttribute,
      ProguardKeepAttributes keepAttributes,
      ProguardClassFilter keepPackageNamesPatterns,
      ProguardClassFilter dontWarnPatterns,
      ProguardClassFilter dontNotePatterns,
      Set<ProguardConfigurationRule> rules,
      boolean printSeeds,
      Path seedFile,
      ImmutableList<String> obfuscationDictionary,
      ImmutableList<String> classObfuscationDictionary,
      ImmutableList<String> packageObfuscationDictionary,
      boolean keepParameterNames,
      ProguardClassFilter adaptClassStrings,
      ProguardPathFilter adaptResourceFilenames,
      ProguardPathFilter adaptResourceFileContents,
      ProguardPathFilter keepDirectories,
      boolean protoShrinking,
      int maxRemovedAndroidLogLevel,
      ProcessKotlinNullChecks processKotlinNullChecks) {
    this.parsedConfiguration = parsedConfiguration;
    this.dexItemFactory = factory;
    this.injars = ImmutableList.copyOf(injars);
    this.libraryJars = ImmutableList.copyOf(libraryJars);
    this.packageObfuscationMode = packageObfuscationMode;
    this.packagePrefix = packagePrefix;
    this.allowAccessModification = allowAccessModification;
    this.ignoreWarnings = ignoreWarnings;
    this.optimizing = optimizing;
    this.obfuscating = obfuscating;
    this.shrinking = shrinking;
    this.printConfiguration = printConfiguration;
    this.printConfigurationFile = printConfigurationFile;
    this.printUsage = printUsage;
    this.printUsageFile = printUsageFile;
    this.printMapping = printMapping;
    this.printMappingFile = printMappingFile;
    this.applyMappingFile = applyMappingFile;
    this.renameSourceFileAttribute = renameSourceFileAttribute;
    this.keepAttributes = keepAttributes;
    this.keepPackageNamesPatterns = keepPackageNamesPatterns;
    this.dontWarnPatterns = dontWarnPatterns;
    this.dontNotePatterns = dontNotePatterns;
    this.rules = ImmutableList.copyOf(rules);
    this.printSeeds = printSeeds;
    this.seedFile = seedFile;
    this.obfuscationDictionary = obfuscationDictionary;
    this.classObfuscationDictionary = classObfuscationDictionary;
    this.packageObfuscationDictionary = packageObfuscationDictionary;
    this.keepParameterNames = keepParameterNames;
    this.adaptClassStrings = adaptClassStrings;
    this.adaptResourceFilenames = adaptResourceFilenames;
    this.adaptResourceFileContents = adaptResourceFileContents;
    this.keepDirectories = keepDirectories;
    this.protoShrinking = protoShrinking;
    this.maxRemovedAndroidLogLevel = maxRemovedAndroidLogLevel;
    this.hasWhyAreYouNotInliningRule =
        Iterables.any(rules, rule -> rule instanceof WhyAreYouNotInliningRule);
    this.hasWhyAreYouNotObfuscatingRule =
        Iterables.any(rules, rule -> rule instanceof WhyAreYouNotObfuscatingRule);
    this.processKotlinNullChecks = processKotlinNullChecks;
  }

  /**
   * Create a new empty builder.
   */
  public static Builder builder(DexItemFactory dexItemFactory,
      Reporter reporter) {
    return new Builder(dexItemFactory, reporter);
  }

  public String getParsedConfiguration() {
    return parsedConfiguration;
  }

  public DexItemFactory getDexItemFactory() {
    return dexItemFactory;
  }

  public List<FilteredClassPath> getInjars() {
    return injars;
  }

  public List<FilteredClassPath> getLibraryjars() {
    return libraryJars;
  }

  public PackageObfuscationMode getPackageObfuscationMode() {
    return packageObfuscationMode;
  }

  public String getPackagePrefix() {
    return packagePrefix;
  }

  public boolean isAccessModificationAllowed() {
    return allowAccessModification;
  }

  public boolean isPrintMapping() {
    return printMapping;
  }

  public Path getPrintMappingFile() {
    return printMappingFile;
  }

  public boolean hasApplyMappingFile() {
    return applyMappingFile != null;
  }

  public Path getApplyMappingFile() {
    return applyMappingFile;
  }

  public boolean isIgnoreWarnings() {
    return ignoreWarnings;
  }

  public boolean isOptimizing() {
    return optimizing;
  }

  public boolean isObfuscating() {
    return obfuscating;
  }

  public boolean isShrinking() {
    return shrinking;
  }

  public boolean isPrintConfiguration() {
    return printConfiguration;
  }

  public Path getPrintConfigurationFile() {
    return printConfigurationFile;
  }

  public boolean isPrintUsage() {
    return printUsage;
  }

  public Path getPrintUsageFile() {
    return printUsageFile;
  }

  public String getRenameSourceFileAttribute() {
    return renameSourceFileAttribute;
  }

  public ProguardKeepAttributes getKeepAttributes() {
    return keepAttributes;
  }

  public ProguardClassFilter getKeepPackageNamesPatterns() {
    return keepPackageNamesPatterns;
  }

  public void setKeepPackageNamesPatterns(ProguardClassFilter keepPackageNamesPatterns) {
    this.keepPackageNamesPatterns = keepPackageNamesPatterns;
  }

  public boolean hasDontWarnPatterns() {
    return !dontWarnPatterns.isEmpty();
  }

  public ProguardClassFilter getDontWarnPatterns(DontWarnConfiguration.Witness witness) {
    assert witness != null;
    return dontWarnPatterns;
  }

  public ProguardClassFilter getDontNotePatterns() {
    return dontNotePatterns;
  }

  public List<ProguardConfigurationRule> getRules() {
    return rules;
  }

  public List<String> getObfuscationDictionary() {
    return obfuscationDictionary;
  }

  public List<String> getClassObfuscationDictionary() {
    return classObfuscationDictionary;
  }

  public List<String> getPackageObfuscationDictionary() {
    return packageObfuscationDictionary;
  }

  public boolean isKeepParameterNames() {
    return keepParameterNames;
  }

  public ProguardClassFilter getAdaptClassStrings() {
    return adaptClassStrings;
  }

  public ProguardPathFilter getAdaptResourceFilenames() {
    return adaptResourceFilenames;
  }

  public ProguardPathFilter getAdaptResourceFileContents() {
    return adaptResourceFileContents;
  }

  public ProguardPathFilter getKeepDirectories() {
    return keepDirectories;
  }

  public boolean isPrintSeeds() {
    return printSeeds;
  }

  public Path getSeedFile() {
    return seedFile;
  }

  public boolean isProtoShrinkingEnabled() {
    return protoShrinking;
  }

  public int getMaxRemovedAndroidLogLevel() {
    return maxRemovedAndroidLogLevel;
  }

  public boolean hasMaximumRemovedAndroidLogLevelRules() {
    return Iterables.any(rules, ProguardConfigurationRule::isMaximumRemovedAndroidLogLevelRule);
  }

  public boolean hasWhyAreYouNotInliningRule() {
    return hasWhyAreYouNotInliningRule;
  }

  public boolean hasWhyAreYouNotObfuscatingRule() {
    return hasWhyAreYouNotObfuscatingRule;
  }

  public ProcessKotlinNullChecks getProcessKotlinNullChecks() {
    return processKotlinNullChecks;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (!keepAttributes.isEmpty()) {
      keepAttributes.append(builder);
      builder.append(StringUtils.LINE_SEPARATOR);
    }
    for (ProguardConfigurationRule rule : rules) {
      rule.append(builder);
      builder.append(StringUtils.LINE_SEPARATOR);
    }
    return builder.toString();
  }
}
