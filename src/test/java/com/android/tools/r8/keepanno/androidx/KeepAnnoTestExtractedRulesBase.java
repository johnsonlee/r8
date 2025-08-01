// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.keepanno.KeepAnnoParameters;
import com.android.tools.r8.keepanno.KeepAnnoTestBase;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassFileTransformer.AnnotationBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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

  protected static byte[] setAnnotationOnClass(
      ClassFileTransformer transformer,
      Class<?> annotationClass,
      Consumer<AnnotationBuilder> builderConsumer) {
    return transformer.setAnnotation(annotationClass, builderConsumer).transform();
  }

  protected static byte[] setAnnotationOnMethod(
      ClassFileTransformer transformer,
      MethodPredicate methodPredicate,
      Class<?> annotationClass,
      Consumer<AnnotationBuilder> builderConsumer) {
    return transformer.setAnnotation(methodPredicate, annotationClass, builderConsumer).transform();
  }

  protected static byte[] setAnnotationOnClass(
      Class<?> clazz, Class<?> annotationClass, Consumer<AnnotationBuilder> builderConsumer)
      throws IOException {
    return setAnnotationOnClass(transformer(clazz), annotationClass, builderConsumer);
  }

  protected static byte[] setAnnotationOnMethod(
      Class<?> clazz,
      MethodPredicate methodPredicate,
      Class<?> annotationClass,
      Consumer<AnnotationBuilder> builderConsumer)
      throws IOException {
    return setAnnotationOnMethod(
        transformer(clazz), methodPredicate, annotationClass, builderConsumer);
  }

  protected static byte[] setAnnotationOnClass(
      ClassReference classReference,
      byte[] classFileBytes,
      ClassReference classReferenceToTransform,
      Class<?> annotationClass,
      Consumer<AnnotationBuilder> builderConsumer) {
    if (!classReference.equals(classReferenceToTransform)) {
      return classFileBytes;
    }
    return setAnnotationOnClass(
        transformer(classFileBytes, classReference), annotationClass, builderConsumer);
  }

  protected static byte[] setAnnotationOnMethod(
      ClassReference classReference,
      byte[] classFileBytes,
      ClassReference classReferenceToTransform,
      MethodPredicate methodPredicate,
      Class<?> annotationClass,
      Consumer<AnnotationBuilder> builderConsumer) {
    if (!classReference.equals(classReferenceToTransform)) {
      return classFileBytes;
    }
    return setAnnotationOnMethod(
        transformer(classFileBytes, classReference),
        methodPredicate,
        annotationClass,
        builderConsumer);
  }

  public abstract static class ExpectedRule {
    public abstract String getRule(boolean r8);
  }

  public static class ExpectedKeepRule extends ExpectedRule {

    private final String keepVariant;
    private final String conditionClass;
    private final String conditionMembers;
    private final String consequentClass;
    private final String consequentMembers;

    private ExpectedKeepRule(Builder builder) {
      this.keepVariant = builder.keepVariant;
      this.conditionClass = builder.conditionClass;
      this.conditionMembers = builder.conditionMembers;
      this.consequentClass = builder.consequentClass;
      this.consequentMembers = builder.consequentMembers;
    }

    @Override
    public String getRule(boolean r8) {
      return "-if class "
          + conditionClass
          + (conditionMembers != null ? " " + conditionMembers : "")
          + " "
          + keepVariant
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

      private String keepVariant = "-keepclasseswithmembers";
      private String conditionClass;
      private String conditionMembers;
      private String consequentClass;
      private String consequentMembers;

      private Builder() {}

      public Builder setKeepVariant(String keepVariant) {
        this.keepVariant = keepVariant;
        return this;
      }

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

      public Builder apply(Consumer<Builder> fn) {
        fn.accept(this);
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
      List<String> attributes = new ArrayList<>();
      if (runtimeVisibleAnnotations) {
        attributes.add(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS);
      }
      if (runtimeVisibleParameterAnnotations) {
        attributes.add(ProguardKeepAttributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS);
      }
      if (runtimeVisibleTypeAnnotations) {
        attributes.add(ProguardKeepAttributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
      }
      return "-keepattributes " + String.join(",", attributes);
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

      public Builder apply(Consumer<Builder> fn) {
        fn.accept(this);
        return this;
      }

      public ExpectedRules build() {
        return new ExpectedRules(this);
      }
    }
  }

  // Add the expected rules for kotlin.Metadata (the class with members and the required
  // attributes).
  protected static void addConsequentKotlinMetadata(
      ExpectedRules.Builder b, Consumer<ExpectedKeepRule.Builder> fn) {
    b.add(ExpectedKeepAttributesRule.buildAllRuntimeVisibleAnnotations())
        .add(
            ExpectedKeepRule.builder()
                .setKeepVariant("-keep")
                .setConsequentClass("kotlin.Metadata")
                .setConsequentMembers("{ *; }")
                .apply(fn)
                .build());
  }

  static class ArchiveResourceProviderClassFilesOnly
      implements ProgramResourceProvider, DataResourceProvider {

    private final List<ProgramResource> programResources = new ArrayList<>();
    private final List<DataEntryResource> dataResources = new ArrayList<>();

    ArchiveResourceProviderClassFilesOnly(Path path) throws ResourceException {
      try {
        ZipUtils.iter(
            path,
            (entry, inputStream) -> {
              if (ZipUtils.isClassFile(entry.getName())) {
                programResources.add(
                    ProgramResource.fromBytes(
                        Origin.unknown(),
                        ProgramResource.Kind.CF,
                        ByteStreams.toByteArray(inputStream),
                        Collections.singleton(
                            DescriptorUtils.guessTypeDescriptor(entry.getName()))));
              } else if (FileUtils.isKotlinModuleFile(entry.getName())) {
                dataResources.add(
                    DataEntryResource.fromBytes(
                        ByteStreams.toByteArray(inputStream), entry.getName(), Origin.unknown()));
              } else if (FileUtils.isKotlinBuiltinsFile(entry.getName())) {
                dataResources.add(
                    DataEntryResource.fromBytes(
                        ByteStreams.toByteArray(inputStream), entry.getName(), Origin.unknown()));
              }
            });
      } catch (IOException e) {
        throw new ResourceException(Origin.unknown(), "Caught IOException", e);
      }
    }

    @Override
    public Collection<ProgramResource> getProgramResources() throws ResourceException {
      return programResources;
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return this;
    }

    @Override
    public void accept(Visitor visitor) throws ResourceException {
      dataResources.forEach(visitor::visit);
    }
  }

  protected void runTestExtractedRulesJava(
      Class<?> mainClass,
      List<Class<?>> testClasses,
      List<byte[]> testClassFileData,
      ExpectedRules expectedRules)
      throws Exception {
    // TODO(b/392865072): Proguard 7.4.1 fails with "Encountered corrupt @kotlin/Metadata for class
    // <binary name> (version 2.1.0)", as ti avoid missing classes warnings from ProGuard some of
    // the Kotlin libraries has to be included.
    assumeFalse(parameters.isPG());
    testForKeepAnnoAndroidX(parameters)
        .addProgramClasses(testClasses)
        .addProgramClassFileData(testClassFileData)
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

  protected void runTestExtractedRulesJava(List<Class<?>> testClasses, ExpectedRules expectedRules)
      throws Exception {
    runTestExtractedRulesJava(
        testClasses.iterator().next(), testClasses, ImmutableList.of(), expectedRules);
  }

  protected void runTestExtractedRulesKotlin(
      KotlinCompileMemoizer compilation,
      List<byte[]> classFileData,
      String mainClass,
      ExpectedRules expectedRules)
      throws Exception {
    // TODO(b/392865072): Legacy R8 fails with AssertionError: Synthetic class kinds should agree.
    assumeFalse(parameters.isLegacyR8());
    // TODO(b/392865072): Reference fails with AssertionError: Built-in class kotlin.Any is not
    // found (in kotlin.reflect code).
    assumeFalse(parameters.isReference());
    // TODO(b/392865072): Proguard 7.7.0 compiled code fails with fails with
    //  "java.lang.annotation.IncompleteAnnotationException: kotlin.Metadata missing element bv".
    assumeFalse(parameters.isPG());
    testForKeepAnnoAndroidX(parameters)
        .applyIf(
            compilation != null,
            b ->
                b.addProgramFiles(
                    ImmutableList.of(compilation.getForConfiguration(kotlinParameters))))
        .applyIfPG(
            b ->
                b.addProgramFiles(
                        ImmutableList.of(
                            kotlinParameters.getCompiler().getKotlinStdlibJar(),
                            kotlinParameters.getCompiler().getKotlinReflectJar(),
                            kotlinParameters.getCompiler().getKotlinAnnotationJar()))
                    .addKeepEnumsRule())
        // addProgramResourceProviders is not implemented for ProGuard, so effectively exclusive
        // from addProgramFiles above. If this changed ProGuard will report duplicate classes.
        .addProgramResourceProviders(
            // Only add class files from Kotlin libraries to ensure the keep annotations does not
            // rely on rules like `-keepattributes RuntimeVisibleAnnotations` and
            // `-keep class kotlin.Metadata { *; }` but are self-contained.
            new ArchiveResourceProviderClassFilesOnly(
                kotlinParameters.getCompiler().getKotlinStdlibJar()),
            new ArchiveResourceProviderClassFilesOnly(
                kotlinParameters.getCompiler().getKotlinReflectJar()),
            new ArchiveResourceProviderClassFilesOnly(
                kotlinParameters.getCompiler().getKotlinAnnotationJar()))
        .addProgramClassFileData(classFileData)
        // TODO(b/323816623): With native interpretation kotlin.Metadata still gets stripped
        //  without -keepattributes RuntimeVisibleAnnotations`.
        .applyIfR8(
            b ->
                b.applyIf(
                    parameters.isNativeR8(),
                    bb -> bb.addKeepRules("-keepattributes RuntimeVisibleAnnotations")))
        // Keep the main entry point for the test.
        .addKeepRules(
            "-keep class " + mainClass + " { public static void main(java.lang.String[]); }")
        .inspectExtractedRules(
            rules -> {
              if (parameters.isExtractRules()) {
                assertListsAreEqual(expectedRules.getRules(parameters.isR8()), trimRules(rules));
              }
            })
        .run(mainClass)
        .assertSuccessWithOutput(getExpectedOutputForKotlin());
  }

  protected void runTestExtractedRulesKotlin(
      KotlinCompileMemoizer compilation, String mainClass, ExpectedRules expectedRules)
      throws Exception {
    runTestExtractedRulesKotlin(compilation, ImmutableList.of(), mainClass, expectedRules);
  }

  protected void runTestExtractedRulesKotlin(
      KotlinCompileMemoizer compilation,
      BiFunction<ClassReference, byte[], byte[]> transformerForClass,
      String mainClass,
      ExpectedRules expectedRules)
      throws Exception {
    List<byte[]> result = new ArrayList<>();
    ZipUtils.iter(
        compilation.getForConfiguration(kotlinParameters),
        (entry, inputStream) -> {
          ClassReference classReference = ZipUtils.entryToClassReference(entry);
          if (classReference == null) {
            return;
          }
          result.add(
              transformerForClass.apply(classReference, ByteStreams.toByteArray(inputStream)));
        });
    runTestExtractedRulesKotlin(null, result, mainClass, expectedRules);
  }
}
