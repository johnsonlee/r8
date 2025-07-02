// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static org.junit.Assert.assertEquals;
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

  protected abstract String getExpectedOutput();

  protected static List<String> trimRules(List<String> rules) {
    List<String> trimmedRules =
        rules.stream()
            .flatMap(s -> Arrays.stream(s.split("\n")))
            .filter(rule -> !rule.startsWith("#"))
            .collect(Collectors.toList());
    return trimmedRules;
  }

  public static class ExpectedRule {
    private final String conditionClass;
    private final String conditionMembers;
    private final String consequentClass;
    private final String consequentMembers;

    private ExpectedRule(Builder builder) {
      this.conditionClass = builder.conditionClass;
      this.conditionMembers = builder.conditionMembers;
      this.consequentClass = builder.consequentClass;
      this.consequentMembers = builder.consequentMembers;
    }

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

      public ExpectedRule build() {
        return new ExpectedRule(this);
      }
    }
  }

  protected void runTestExtractedRulesJava(List<Class<?>> testClasses, ExpectedRule expectedRule)
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
                assertEquals(
                    ImmutableList.of(expectedRule.getRule(parameters.isR8())), trimRules(rules));
              }
            })
        .run(mainClass)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  protected void runTestExtractedRulesKotlin(
      KotlinCompileMemoizer compilation, String mainClass, ExpectedRule expectedRule)
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
                assertEquals(
                    ImmutableList.of(expectedRule.getRule(parameters.isR8())), trimRules(rules));
              }
            })
        .run(mainClass)
        .applyIf(
            parameters.isExtractRules(),
            b -> b.assertSuccessWithOutput(getExpectedOutput()),
            b -> b.assertSuccessWithOutput(""));
  }
}
