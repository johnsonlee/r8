// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class R8PartialCompilationConfiguration {

  private final boolean enabled;
  private Path tempDir = null;
  private final List<Predicate<DexString>> includePredicates;
  private final List<Predicate<DexString>> excludePredicates;

  public Consumer<AndroidApp> r8InputAppConsumer;
  public Consumer<AndroidApp> d8InputAppConsumer;
  public Consumer<AndroidApp> r8OutputAppConsumer;
  public Consumer<AndroidApp> d8OutputAppConsumer;

  public Consumer<InternalOptions> d8DexOptionsConsumer = ConsumerUtils.emptyConsumer();
  public Consumer<InternalOptions> d8DesugarOptionsConsumer = ConsumerUtils.emptyConsumer();
  public Consumer<InternalOptions> d8MergeOptionsConsumer = ConsumerUtils.emptyConsumer();
  public Consumer<InternalOptions> r8OptionsConsumer = ConsumerUtils.emptyConsumer();

  private static final R8PartialCompilationConfiguration disabledConfiguration =
      new R8PartialCompilationConfiguration(false, null, null);

  private R8PartialCompilationConfiguration(
      boolean enabled,
      List<Predicate<DexString>> includePredicates,
      List<Predicate<DexString>> excludePredicates) {
    assert !enabled || !includePredicates.isEmpty();
    assert !enabled || excludePredicates != null;
    this.enabled = enabled;
    this.includePredicates = includePredicates;
    this.excludePredicates = excludePredicates;
  }

  public boolean test(DexProgramClass clazz) {
    DexString name = clazz.getType().getDescriptor();
    for (Predicate<DexString> isR8ClassPredicate : includePredicates) {
      if (isR8ClassPredicate.test(name)) {
        for (Predicate<DexString> isD8ClassPredicate : excludePredicates) {
          if (isD8ClassPredicate.test(name)) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  public static R8PartialCompilationConfiguration disabledConfiguration() {
    return disabledConfiguration;
  }

  public static R8PartialCompilationConfiguration fromIncludeExcludePatterns(
      String includePatterns, String excludePatterns) {
    boolean enabled = includePatterns != null || excludePatterns != null;
    if (!enabled) {
      return disabledConfiguration();
    }
    Builder builder = builder();
    if (includePatterns != null) {
      Splitter.on(",").splitToList(includePatterns).forEach(builder::addJavaTypeIncludePattern);
    }
    if (excludePatterns != null) {
      Splitter.on(",").splitToList(excludePatterns).forEach(builder::addJavaTypeExcludePattern);
    }
    return builder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public synchronized Path getTempDir() throws IOException {
    if (tempDir == null) {
      setTempDir(Files.createTempDirectory("r8PartialCompilation"));
    }
    return tempDir;
  }

  public void setTempDir(Path tempDir) {
    this.tempDir = tempDir;
  }

  public static class Builder {
    private final List<Predicate<DexString>> includePredicates = new ArrayList<>();
    private final List<Predicate<DexString>> excludePredicates = new ArrayList<>();

    private Builder() {}

    public R8PartialCompilationConfiguration build() {
      return new R8PartialCompilationConfiguration(
          !includePredicates.isEmpty(), includePredicates, excludePredicates);
    }

    public Builder includeAll() {
      includePredicates.add(Predicates.alwaysTrue());
      return this;
    }

    public Builder addJavaTypeIncludePattern(String pattern) {
      includePredicates.add(
          createMatcher("L" + DescriptorUtils.getBinaryNameFromJavaType(pattern)));
      return this;
    }

    public Builder addJavaTypeExcludePattern(String pattern) {
      excludePredicates.add(
          createMatcher("L" + DescriptorUtils.getBinaryNameFromJavaType(pattern)));
      return this;
    }

    public Builder addDescriptorIncludePattern(String pattern) {
      includePredicates.add(createMatcher(pattern));
      return this;
    }

    public Builder addDescriptorExcludePattern(String pattern) {
      excludePredicates.add(createMatcher(pattern));
      return this;
    }

    private Predicate<DexString> createMatcher(String descriptorPrefix) {
      assert descriptorPrefix.startsWith("L");
      assert descriptorPrefix.indexOf('.') == -1;

      if (descriptorPrefix.equals("L**")) {
        return new AllClassesMatcher();
      } else if (descriptorPrefix.equals("L*")) {
        return new UnnamedPackageMatcher();
      } else if (descriptorPrefix.endsWith("/**")) {
        return new PackageAndSubpackagePrefixMatcher(
            descriptorPrefix.substring(0, descriptorPrefix.length() - 2));
      } else if (descriptorPrefix.endsWith("/*")) {
        return new PackagePrefixMatcher(
            descriptorPrefix.substring(0, descriptorPrefix.length() - 1));
      }
      if (descriptorPrefix.endsWith("*")) {
        return new ClassPrefixMatcher(descriptorPrefix.substring(0, descriptorPrefix.length() - 1));
      } else {
        return new ClassNameMatcher(descriptorPrefix + ';');
      }
    }

    public Builder includeClasses(Class<?>... classes) {
      return includeClasses(Arrays.asList(classes));
    }

    public Builder includeClasses(Collection<Class<?>> classes) {
      classes.forEach(
          clazz ->
              includePredicates.add(
                  descriptor ->
                      descriptor.toString().equals(DescriptorUtils.javaClassToDescriptor(clazz))));
      return this;
    }

    public Builder includeJavaType(Predicate<String> include) {
      includePredicates.add(
          descriptor -> include.test(DescriptorUtils.descriptorToJavaType(descriptor.toString())));
      return this;
    }

    public Builder excludeClasses(Class<?>... classes) {
      return excludeClasses(Arrays.asList(classes));
    }

    public Builder excludeClasses(Collection<Class<?>> classes) {
      classes.forEach(
          clazz ->
              excludePredicates.add(
                  descriptor ->
                      descriptor.toString().equals(DescriptorUtils.javaClassToDescriptor(clazz))));
      return this;
    }

    public Builder excludeJavaType(Predicate<String> exclude) {
      excludePredicates.add(
          descriptor -> exclude.test(DescriptorUtils.descriptorToJavaType(descriptor.toString())));
      return this;
    }
  }

  private static class AllClassesMatcher implements Predicate<DexString> {

    AllClassesMatcher() {}

    @Override
    public boolean test(DexString descriptor) {
      return true;
    }
  }

  private static class UnnamedPackageMatcher implements Predicate<DexString> {

    UnnamedPackageMatcher() {}

    @Override
    public boolean test(DexString descriptor) {
      return descriptor.indexOf('/') == -1;
    }
  }

  private static class PackageAndSubpackagePrefixMatcher implements Predicate<DexString> {

    private final byte[] descriptorPrefix;

    PackageAndSubpackagePrefixMatcher(String descriptorPrefix) {
      this.descriptorPrefix = DexString.encodeToMutf8(descriptorPrefix);
    }

    @Override
    public boolean test(DexString descriptor) {
      return descriptor.startsWith(descriptorPrefix);
    }
  }

  private static class PackagePrefixMatcher implements Predicate<DexString> {

    private final byte[] descriptorPrefix;
    private final int descriptorPrefixLength;

    PackagePrefixMatcher(String descriptorPrefix) {
      this.descriptorPrefix = DexString.encodeToMutf8(descriptorPrefix);
      this.descriptorPrefixLength = descriptorPrefix.length();
    }

    @Override
    public boolean test(DexString descriptor) {
      return descriptor.startsWith(descriptorPrefix)
          && descriptor.lastIndexOf('/') == descriptorPrefixLength - 1;
    }
  }

  private static class ClassPrefixMatcher implements Predicate<DexString> {

    private final byte[] descriptorPrefix;
    private final int descriptorPrefixLength;

    ClassPrefixMatcher(String descriptorPrefix) {
      this.descriptorPrefix = DexString.encodeToMutf8(descriptorPrefix);
      this.descriptorPrefixLength = descriptorPrefix.length();
    }

    @Override
    public boolean test(DexString descriptor) {
      return descriptor.startsWith(descriptorPrefix)
          && descriptor.lastIndexOf('/') < descriptorPrefixLength - 1;
    }
  }

  private static class ClassNameMatcher implements Predicate<DexString> {

    private final String descriptor;

    ClassNameMatcher(String descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public boolean test(DexString descriptor) {
      return descriptor.toString().equals(this.descriptor);
    }
  }
}
