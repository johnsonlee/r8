// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.keepanno.KeepAnnoParameters;
import com.android.tools.r8.keepanno.KeepAnnoTestBase;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.runners.Parameterized.Parameter;

public abstract class KeepAnnoTestExtractedRulesBase extends KeepAnnoTestBase {

  @Parameter(0)
  public KeepAnnoParameters parameters;

  @Parameter(1)
  public KotlinTestParameters kotlinParameters;

  protected static KotlinCompileMemoizer compilationResults;
  protected static KotlinCompileMemoizer compilationResultsClassName;

  protected String getExpectedOutput() {
    assert false
        : "implement either this or both of getExpectedOutputForJava and"
            + " getExpectedOutputForKotlin";
    return null;
  }

  protected String getExpectedOutputForJava() {
    return getExpectedOutput();
  }

  protected String getExpectedOutputForKotlin() {
    return getExpectedOutput();
  }

  protected static List<String> trimRules(List<String> rules) {
    List<String> trimmedRules =
        rules.stream()
            .flatMap(s -> Arrays.stream(s.split("\n")))
            .filter(rule -> !rule.startsWith("#"))
            .sorted()
            .distinct()
            .collect(Collectors.toList());
    return trimmedRules;
  }

  public abstract static class ExpectedRule {
    public abstract String getRule(boolean r8);
  }

  public static class ExpectedKeepRule extends ExpectedRule {

    private final String conditionClass;
    private final String conditionMembers;
    private final String consequentClass;
    private final String consequentMembers;

    private ExpectedKeepRule(Builder builder) {
      this.conditionClass = builder.conditionClass;
      this.conditionMembers = builder.conditionMembers;
      this.consequentClass = builder.consequentClass;
      this.consequentMembers = builder.consequentMembers;
    }

    @Override
    public String getRule(boolean r8) {
      return "-if class "
          + conditionClass
          + " "
          + conditionMembers
          + " -keepclasseswithmembers"
          + (r8 ? ",allowaccessmodification" : "")
          + " class "
          + consequentClass
          + " "
          + consequentMembers;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {

      private String conditionClass;
      private String conditionMembers;
      private String consequentClass;
      private String consequentMembers;

      private Builder() {}

      public Builder setConditionClass(Class<?> conditionClass) {
        this.conditionClass = conditionClass.getTypeName();
        return this;
      }

      public Builder setConditionClass(String conditionClass) {
        this.conditionClass = conditionClass;
        return this;
      }

      public Builder setConditionMembers(String conditionMembers) {
        this.conditionMembers = conditionMembers;
        return this;
      }

      public Builder setConsequentClass(Class<?> consequentClass) {
        this.consequentClass = consequentClass.getTypeName();
        return this;
      }

      public Builder setConsequentClass(String consequentClass) {
        this.consequentClass = consequentClass;
        return this;
      }

      public Builder setConsequentMembers(String consequentMembers) {
        this.consequentMembers = consequentMembers;
        return this;
      }

      public ExpectedKeepRule build() {
        return new ExpectedKeepRule(this);
      }
    }
  }

  public static class ExpectedKeepAttributesRule extends ExpectedRule {

    private final boolean runtimeVisibleAnnotations;
    private final boolean runtimeVisibleParameterAnnotations;
    private final boolean runtimeVisibleTypeAnnotations;

    private ExpectedKeepAttributesRule(Builder builder) {
      this.runtimeVisibleAnnotations = builder.runtimeVisibleAnnotations;
      this.runtimeVisibleParameterAnnotations = builder.runtimeVisibleParameterAnnotations;
      this.runtimeVisibleTypeAnnotations = builder.runtimeVisibleTypeAnnotations;
    }

    @Override
    public String getRule(boolean r8) {
      StringBuilder builder = new StringBuilder("-keepattributes");
      if (runtimeVisibleAnnotations) {
        builder.append(" RuntimeVisibleAnnotations");
      }
      if (runtimeVisibleParameterAnnotations) {
        builder.append(",RuntimeVisibleParameterAnnotations");
      }
      if (runtimeVisibleTypeAnnotations) {
        builder.append(",RuntimeVisibleTypeAnnotations");
      }
      return builder.toString();
    }

    public static Builder builder() {
      return new Builder();
    }

    public static ExpectedKeepAttributesRule buildAllRuntimeVisibleAnnotations() {
      return builder()
          .setRuntimeVisibleAnnotations()
          .setRuntimeVisibleParameterAnnotations()
          .setRuntimeVisibleTypeAnnotations()
          .build();
    }

    public static class Builder {

      private boolean runtimeVisibleAnnotations = false;
      private boolean runtimeVisibleParameterAnnotations = false;
      private boolean runtimeVisibleTypeAnnotations = false;

      public Builder setRuntimeVisibleAnnotations() {
        this.runtimeVisibleAnnotations = true;
        return this;
      }

      public Builder setRuntimeVisibleParameterAnnotations() {
        this.runtimeVisibleParameterAnnotations = true;
        return this;
      }

      public Builder setRuntimeVisibleTypeAnnotations() {
        this.runtimeVisibleTypeAnnotations = true;
        return this;
      }

      public ExpectedKeepAttributesRule build() {
        return new ExpectedKeepAttributesRule(this);
      }
    }
  }

  public static class ExpectedRules {

    private final ImmutableList<ExpectedRule> rules;

    private ExpectedRules(Builder builder) {
      this.rules = builder.rules.build();
    }

    public ImmutableList<String> getRules(boolean r8) {
      return rules.stream()
          .map(rule -> rule.getRule(r8))
          .sorted()
          .collect(ImmutableList.toImmutableList());
    }

    public static Builder builder() {
      return new Builder();
    }

    public static ExpectedRules singleRule(ExpectedRule rule) {
      return new Builder().add(rule).build();
    }

    public static class Builder {

      private final ImmutableList.Builder<ExpectedRule> rules = ImmutableList.builder();

      public Builder add(ExpectedRule rule) {
        rules.add(rule);
        return this;
      }

      public ExpectedRules build() {
        return new ExpectedRules(this);
      }
    }
  }

  protected void runTestExtractedRulesJava(List<Class<?>> testClasses, ExpectedRules expectedRules)
      throws Exception {
    Class<?> mainClass = testClasses.iterator().next();
    testForKeepAnnoAndroidX(parameters)
        .applyIfPG(
            b -> {
              KotlinCompiler kotlinc =
                  new KotlinCompiler(KotlinCompilerVersion.MAX_SUPPORTED_VERSION);
              b.addLibraryFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar());
            })
        .addProgramClasses(testClasses)
        .addKeepMainRule(mainClass)
        .setExcludedOuterClass(getClass())
        .inspectExtractedRules(
            rules -> {
              if (parameters.isExtractRules()) {
                assertListsAreEqual(expectedRules.getRules(parameters.isR8()), trimRules(rules));
              }
            })
        .run(mainClass)
        .assertSuccessWithOutput(getExpectedOutputForJava());
  }

  protected void runTestExtractedRulesKotlin(
      KotlinCompileMemoizer compilation, String mainClass, ExpectedRules expectedRules)
      throws Exception {
    // TODO(b/392865072): Legacy R8 fails with AssertionError: Synthetic class kinds should agree.
    assumeFalse(parameters.isLegacyR8());
    // TODO(b/392865072): Reference fails with AssertionError: Built-in class kotlin.Any is not
    // found (in kotlin.reflect code).
    assumeFalse(parameters.isReference());
    // TODO(b/392865072): Proguard 7.4.1 fails with "Encountered corrupt @kotlin/Metadata for class
    // <binary name> (version 2.1.0)".
    assumeFalse(parameters.isPG());
    testForKeepAnnoAndroidX(parameters)
        .addProgramFiles(ImmutableList.of(compilation.getForConfiguration(kotlinParameters)))
        .addProgramFilesWithoutAnnotations(
            ImmutableList.of(
                kotlinParameters.getCompiler().getKotlinStdlibJar(),
                kotlinParameters.getCompiler().getKotlinReflectJar(),
                kotlinParameters.getCompiler().getKotlinAnnotationJar()))
        .applyIfR8Current(R8TestBuilder::allowDiagnosticWarningMessages)
        .addKeepRules("-keepattributes RuntimeVisibleAnnotations")
        .addKeepRules("-keep class kotlin.Metadata { *; }")
        .addKeepRules(
            "-keep class " + mainClass + " { public static void main(java.lang.String[]); }")
        .applyIfR8OrR8Partial(
            b ->
                b.addOptionsModification(
                    options -> {
                      options.testing.allowedUnusedDontWarnPatterns.add(
                          "kotlin.reflect.jvm.internal.**");
                      options.testing.allowedUnusedDontWarnPatterns.add("java.lang.ClassValue");
                    }),
            b ->
                b.addR8PartialR8OptionsModification(
                    options -> {
                      options.testing.allowedUnusedDontWarnPatterns.add(
                          "kotlin.reflect.jvm.internal.**");
                      options.testing.allowedUnusedDontWarnPatterns.add("java.lang.ClassValue");
                    }))
        .inspectExtractedRules(
            rules -> {
              if (parameters.isExtractRules()) {
                assertListsAreEqual(expectedRules.getRules(parameters.isR8()), trimRules(rules));
              }
            })
        .run(mainClass)
        .assertSuccessWithOutput(getExpectedOutputForKotlin());
  }
}
