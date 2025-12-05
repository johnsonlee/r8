// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static com.android.tools.r8.utils.FileUtils.isClassFile;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.KeepAnno;
import com.android.tools.r8.keepanno.KeepAnnoParameters;
import com.android.tools.r8.keepanno.KeepAnnoTestBase;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassFileTransformer.AnnotationBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

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
      Consumer<AnnotationBuilder> builderConsumer) {
    return transformer.setAnnotation(builderConsumer).transform();
  }

  protected static byte[] setAnnotationOnClass(
      Class<?> clazz, Consumer<AnnotationBuilder> builderConsumer) throws IOException {
    return setAnnotationOnClass(transformer(clazz), builderConsumer);
  }

  protected static byte[] setAnnotationOnClass(
      ClassReference classReference,
      byte[] classFileBytes,
      ClassReference classReferenceToTransform,
      Consumer<AnnotationBuilder> builderConsumer) {
    if (!classReference.equals(classReferenceToTransform)) {
      return classFileBytes;
    }
    return setAnnotationOnClass(transformer(classFileBytes, classReference), builderConsumer);
  }

  protected static byte[] setAnnotationOnMethod(
      ClassFileTransformer transformer,
      MethodPredicate methodPredicate,
      Consumer<AnnotationBuilder> builderConsumer) {
    return transformer.setAnnotation(methodPredicate, builderConsumer).transform();
  }

  protected static byte[] setAnnotationOnMethod(
      Class<?> clazz, MethodPredicate methodPredicate, Consumer<AnnotationBuilder> builderConsumer)
      throws IOException {
    return setAnnotationOnMethod(transformer(clazz), methodPredicate, builderConsumer);
  }

  protected static byte[] setAnnotationOnMethod(
      ClassReference classReference,
      byte[] classFileBytes,
      ClassReference classReferenceToTransform,
      MethodPredicate methodPredicate,
      Consumer<AnnotationBuilder> builderConsumer) {
    if (!classReference.equals(classReferenceToTransform)) {
      return classFileBytes;
    }
    return setAnnotationOnMethod(
        transformer(classFileBytes, classReference),
        methodPredicate,
        builderConsumer);
  }

  protected static byte[] setAnnotationOnField(
      ClassFileTransformer transformer,
      FieldPredicate fieldPredicate,
      Consumer<AnnotationBuilder> builderConsumer) {
    return transformer.setAnnotation(fieldPredicate, builderConsumer).transform();
  }

  protected static byte[] setAnnotationOnField(
      Class<?> clazz, FieldPredicate fieldPredicate, Consumer<AnnotationBuilder> builderConsumer)
      throws IOException {
    return setAnnotationOnField(transformer(clazz), fieldPredicate, builderConsumer);
  }

  protected static byte[] setAnnotationOnField(
      ClassReference classReference,
      byte[] classFileBytes,
      ClassReference classReferenceToTransform,
      FieldPredicate fieldPredicate,
      Consumer<AnnotationBuilder> builderConsumer) {
    if (!classReference.equals(classReferenceToTransform)) {
      return classFileBytes;
    }
    return setAnnotationOnField(
        transformer(classFileBytes, classReference), fieldPredicate, builderConsumer);
  }

  public abstract static class ExpectedRule {
    public abstract String getRule(boolean r8);
  }

  public static class ExpectedKeepRule extends ExpectedRule {

    private final String keepVariant;
    private final String conditionClass;
    private final String conditionMembers;
    private final String consequentClass;
    private final boolean extendsConsequentClass;
    private final String consequentMembers;

    private ExpectedKeepRule(Builder builder) {
      this.keepVariant = builder.keepVariant;
      this.conditionClass = builder.conditionClass;
      this.conditionMembers = builder.conditionMembers;
      this.consequentClass = builder.consequentClass;
      this.extendsConsequentClass = builder.extendsConsequentClass;
      this.consequentMembers = builder.consequentMembers;
    }

    @Override
    public String getRule(boolean r8) {
      assert keepVariant != null : "Builder missing keep variant.";
      assert consequentClass != null : "Builder missing consequent class.";
      assert consequentMembers != null : "Builder missing consequent members.";
      return (conditionClass != null
              ? "-if class "
                  + conditionClass
                  + (conditionMembers != null ? " " + conditionMembers : "")
                  + " "
              : "")
          + keepVariant
          + (r8 ? ",allowaccessmodification" : "")
          + " class "
          + (extendsConsequentClass ? "** extends " : "")
          + consequentClass
          + " "
          + consequentMembers;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {

      private String keepVariant;
      private String conditionClass;
      private String conditionMembers;
      private String consequentClass;
      private boolean extendsConsequentClass = false;
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
        this.extendsConsequentClass = false;
        return this;
      }

      public Builder setConsequentExtendsClass(Class<?> consequentClass) {
        this.consequentClass = consequentClass.getTypeName();
        this.extendsConsequentClass = true;
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

  // Rule added by rule extraction to avoid compat mode keeping the default constructor.
  protected static void addDefaultInitWorkaround(
      ExpectedRules.Builder b, Consumer<ExpectedKeepRule.Builder> fn) {
    b.add(
        ExpectedKeepRule.builder()
            .setKeepVariant("-keep")
            .setConsequentMembers("{ void finalize(); }")
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
    public Collection<ProgramResource> getProgramResources() {
      return programResources;
    }

    @Override
    public void getProgramResources(Consumer<ProgramResource> consumer) {
      programResources.forEach(consumer);
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return this;
    }

    @Override
    public void accept(Visitor visitor) {
      dataResources.forEach(visitor::visit);
    }
  }

  private void extractRules(Class<?> mainClass, Consumer<String> rulesConsumer) throws IOException {
    extractRules(Files.readAllBytes(ToolHelper.getClassFileForTestClass(mainClass)), rulesConsumer);
  }

  private void extractRules(byte[] classFileData, Consumer<String> rulesConsumer) {
    ClassReader reader = new ClassReader(classFileData);
    ClassVisitor visitor = KeepAnno.createClassVisitorForKeepRulesExtraction(rulesConsumer);
    reader.accept(visitor, ClassReader.SKIP_CODE);
  }

  protected void testExtractedRules(
      Iterable<Class<?>> classes, Iterable<byte[]> classFileData, ExpectedRules expectedRules)
      throws IOException {
    assumeFalse(parameters.isPG());
    if (parameters.isExtractRules()) {
      List<String> extractedRules = new ArrayList<>();
      for (Class<?> testClass : classes) {
        extractRules(testClass, extractedRules::add);
      }
      for (byte[] data : classFileData) {
        extractRules(data, extractedRules::add);
      }
      assertListsAreEqual(expectedRules.getRules(parameters.isR8()), trimRules(extractedRules));
    }
  }

  protected void testExtractedRules(Iterable<byte[]> classFileData, ExpectedRules expectedRules)
      throws IOException {
    testExtractedRules(Collections.emptyList(), classFileData, expectedRules);
  }

  protected void testExtractedRules(
      KotlinCompileMemoizer compilation, List<byte[]> classFileData, ExpectedRules expectedRules)
      throws IOException {
    List<byte[]> kotlinCompilationClassFileData = new ArrayList<>();
    if (compilation != null) {
      ZipUtils.iter(
          compilation.getForConfiguration(kotlinParameters),
          (entry, input) -> {
            if (isClassFile(entry.getName())) {
              kotlinCompilationClassFileData.add(ByteStreams.toByteArray(input));
            }
          });
    }
    testExtractedRules(
        Iterables.concat(kotlinCompilationClassFileData, classFileData), expectedRules);
  }

  protected void testExtractedRules(
      KotlinCompileMemoizer compilation,
      BiFunction<ClassReference, byte[], byte[]> transformerForClass,
      ExpectedRules expectedRules)
      throws IOException {
    testExtractedRules(getTransformedClasses(compilation, transformerForClass), expectedRules);
  }

  protected void testExtractedRulesAndRunJava(
      Class<?> mainClass,
      List<Class<?>> classes,
      List<byte[]> classFileData,
      ExpectedRules expectedRules,
      String expectedOutput)
      throws Exception {
    // TODO(b/392865072): Proguard 7.4.1 fails with "Encountered corrupt @kotlin/Metadata for class
    // <binary name> (version 2.1.0)", as ti avoid missing classes warnings from ProGuard some of
    // the Kotlin libraries has to be included.
    assumeFalse(parameters.isPG());
    testExtractedRules(
        Iterables.concat(ImmutableList.of(mainClass), classes), classFileData, expectedRules);
    testForKeepAnnoAndroidX(parameters)
        .addProgramClasses(classes)
        .addProgramClassFileData(classFileData)
        .addKeepMainRule(mainClass)
        .setExcludedOuterClass(getClass())
        .inspectExtractedRules(
            rules -> {
              if (parameters.isExtractRules()) {
                assertListsAreEqual(expectedRules.getRules(parameters.isR8()), trimRules(rules));
              }
            })
        .run(mainClass)
        .assertSuccessWithOutput(expectedOutput);
  }

  protected void testExtractedRulesAndRunJava(
      Class<?> mainClass,
      List<Class<?>> classes,
      List<byte[]> classFileData,
      ExpectedRules expectedRules)
      throws Exception {
    testExtractedRulesAndRunJava(
        mainClass, classes, classFileData, expectedRules, getExpectedOutputForJava());
  }

  protected void testExtractedRulesAndRunJava(List<Class<?>> classes, ExpectedRules expectedRules)
      throws Exception {
    testExtractedRulesAndRunJava(
        classes.iterator().next(), classes, ImmutableList.of(), expectedRules);
  }

  private List<byte[]> getTransformedClasses(
      KotlinCompileMemoizer compilation,
      BiFunction<ClassReference, byte[], byte[]> transformerForClass)
      throws IOException {
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
    return result;
  }

  protected void testExtractedRulesAndRunKotlin(
      KotlinCompileMemoizer compilation,
      List<byte[]> classFileData,
      String mainClass,
      ExpectedRules expectedRules,
      ThrowingConsumer<TestRunResult<?>, RuntimeException> runResultConsumer)
      throws Exception {
    // TODO(b/392865072): Legacy R8 fails with AssertionError: Synthetic class kinds should agree.
    assumeFalse(parameters.isLegacyR8());
    // TODO(b/392865072): Reference fails with AssertionError: Built-in class kotlin.Any is not
    // found (in kotlin.reflect code).
    assumeFalse(parameters.isReference());
    // TODO(b/392865072): Proguard 7.7.0 compiled code fails with fails with
    //  "java.lang.annotation.IncompleteAnnotationException: kotlin.Metadata missing element bv".
    assumeFalse(parameters.isPG());
    testExtractedRules(compilation, classFileData, expectedRules);
    testForKeepAnnoAndroidX(parameters)
        .applyIf(
            compilation != null,
            b ->
                b.addProgramFiles(
                    ImmutableList.of(compilation.getForConfiguration(kotlinParameters))))
        .applyIfPG(TestShrinkerBuilder::addKeepEnumsRule)
        .addProgramFiles(
            ImmutableList.of(
                kotlinParameters.getCompiler().getKotlinStdlibJar(),
                kotlinParameters.getCompiler().getKotlinReflectJar(),
                kotlinParameters.getCompiler().getKotlinAnnotationJar()))
        .addProgramClassFileData(classFileData)
        // Keep the main entry point for the test.
        .addKeepRules(
            "-keep class " + mainClass + " { public static void main(java.lang.String[]); }")
        // The keep-anno testing addProgramFiles does not add the kotlin-reflect embedded rules.
        .addKeepRules(
            "-keep class kotlin.Metadata { *; }",
            "-keepattributes InnerClasses,Signature,RuntimeVisible*Annotations,EnclosingMethod")
        .inspectExtractedRules(
            rules -> {
              if (parameters.isExtractRules()) {
                assertListsAreEqual(expectedRules.getRules(parameters.isR8()), trimRules(rules));
              }
            })
        .run(mainClass)
        .apply(runResultConsumer);
  }

  protected void testExtractedRulesAndRunKotlin(
      KotlinCompileMemoizer compilation, String mainClass, ExpectedRules expectedRules)
      throws Exception {
    testExtractedRulesAndRunKotlin(
        compilation,
        ImmutableList.of(),
        mainClass,
        expectedRules,
        b -> b.assertSuccessWithOutput(getExpectedOutputForKotlin()));
  }

  protected void testExtractedRulesAndRunKotlin(
      KotlinCompileMemoizer compilation,
      BiFunction<ClassReference, byte[], byte[]> transformerForClass,
      String mainClass,
      ExpectedRules expectedRules,
      String expectedOutput)
      throws Exception {
    testExtractedRulesAndRunKotlin(
        null,
        getTransformedClasses(compilation, transformerForClass),
        mainClass,
        expectedRules,
        b -> b.assertSuccessWithOutput(expectedOutput));
  }

  protected void testExtractedRulesAndRunKotlin(
      KotlinCompileMemoizer compilation,
      BiFunction<ClassReference, byte[], byte[]> transformerForClass,
      String mainClass,
      ExpectedRules expectedRules,
      ThrowingConsumer<TestRunResult<?>, RuntimeException> runResultConsumer)
      throws Exception {
    testExtractedRulesAndRunKotlin(
        null,
        getTransformedClasses(compilation, transformerForClass),
        mainClass,
        expectedRules,
        runResultConsumer);
  }

  protected void testExtractedRulesAndRunKotlin(
      KotlinCompileMemoizer compilation,
      BiFunction<ClassReference, byte[], byte[]> transformerForClass,
      String mainClass,
      ExpectedRules expectedRules)
      throws Exception {
    testExtractedRulesAndRunKotlin(
        compilation, transformerForClass, mainClass, expectedRules, getExpectedOutputForKotlin());
  }
}
