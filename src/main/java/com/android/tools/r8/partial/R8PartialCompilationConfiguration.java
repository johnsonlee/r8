// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.PartialOptimizationConfigurationBuilder;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.partial.predicate.AllClassesMatcher;
import com.android.tools.r8.partial.predicate.ClassNameMatcher;
import com.android.tools.r8.partial.predicate.ClassPrefixMatcher;
import com.android.tools.r8.partial.predicate.PackageAndSubpackagePrefixMatcher;
import com.android.tools.r8.partial.predicate.PackagePrefixMatcher;
import com.android.tools.r8.partial.predicate.R8PartialPredicate;
import com.android.tools.r8.partial.predicate.R8PartialPredicateCollection;
import com.android.tools.r8.partial.predicate.UnnamedPackageMatcher;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Splitter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.function.Consumer;

public class R8PartialCompilationConfiguration {

  public static final String INCLUDE_PROPERTY_NAME =
      "com.android.tools.r8.experimentalPartialShrinkingIncludePatterns";
  public static final String EXCLUDE_PROPERTY_NAME =
      "com.android.tools.r8.experimentalPartialShrinkingExcludePatterns";
  public static final String RANDOMIZE_PROPERTY_NAME =
      "com.android.tools.r8.experimentalPartialShrinkingRandomize";
  public static final String RANDOMIZE_SEED_PROPERTY_NAME =
      "com.android.tools.r8.experimentalPartialShrinkingRandomizeSeed";

  private final boolean enabled;
  private final R8PartialPredicateCollection includePredicates;
  private final R8PartialPredicateCollection excludePredicates;
  private final Random randomizeForTesting;

  public Consumer<InternalOptions> d8DexOptionsConsumer = ConsumerUtils.emptyConsumer();
  public Consumer<InternalOptions> r8OptionsConsumer = ConsumerUtils.emptyConsumer();

  public boolean printPartitioningForTesting = false;

  private static final R8PartialCompilationConfiguration disabledConfiguration =
      new R8PartialCompilationConfiguration(false, null, null, null);

  private R8PartialCompilationConfiguration(
      boolean enabled,
      R8PartialPredicateCollection includePredicates,
      R8PartialPredicateCollection excludePredicates,
      Random randomizeForTesting) {
    assert !enabled || !includePredicates.isEmpty() || randomizeForTesting != null;
    assert !enabled || excludePredicates != null;
    this.enabled = enabled;
    this.includePredicates = includePredicates;
    this.excludePredicates = excludePredicates;
    this.randomizeForTesting = randomizeForTesting;
  }

  public R8PartialPredicateCollection getIncludePredicates() {
    return includePredicates;
  }

  public R8PartialPredicateCollection getExcludePredicates() {
    return excludePredicates;
  }

  public void partition(
      DirectMappedDexApplication app,
      Consumer<DexProgramClass> d8ClassConsumer,
      Consumer<DexProgramClass> r8ClassConsumer) {
    Collection<DexProgramClass> classes =
        randomizeForTesting != null ? app.classesWithDeterministicOrder() : app.classes();
    for (DexProgramClass clazz : classes) {
      if (test(clazz)) {
        r8ClassConsumer.accept(clazz);
      } else {
        d8ClassConsumer.accept(clazz);
      }
    }
  }

  private boolean test(DexProgramClass clazz) {
    if (randomizeForTesting != null) {
      return randomizeForTesting.nextBoolean();
    }
    return includePredicates.test(clazz) && !excludePredicates.test(clazz);
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

  private static String systemPropertyValue(String propertyName) {
    String value = System.getProperty(propertyName);
    return value != null ? value : "";
  }

  public static R8PartialCompilationConfiguration fromSystemProperties() {
    R8PartialCompilationConfiguration configuration = fromSystemProperties(true);
    if (configuration.isEnabled()) {
      System.out.println("R8 partial compilation configered through system properties:");
      System.out.println(
          RANDOMIZE_PROPERTY_NAME + "=" + systemPropertyValue(RANDOMIZE_PROPERTY_NAME));
      System.out.println(
          RANDOMIZE_SEED_PROPERTY_NAME + "=" + systemPropertyValue(RANDOMIZE_SEED_PROPERTY_NAME));
      System.out.println(INCLUDE_PROPERTY_NAME + "=" + systemPropertyValue(INCLUDE_PROPERTY_NAME));
      System.out.println(EXCLUDE_PROPERTY_NAME + "=" + systemPropertyValue(EXCLUDE_PROPERTY_NAME));
    }
    return configuration;
  }

  public static R8PartialCompilationConfiguration fromSystemProperties(boolean printSeed) {
    if (System.getProperty(RANDOMIZE_PROPERTY_NAME) != null
        || System.getProperty(RANDOMIZE_SEED_PROPERTY_NAME) != null) {
      if (System.getProperty(RANDOMIZE_SEED_PROPERTY_NAME) != null) {
        long seed = Long.parseLong(System.getProperty(RANDOMIZE_SEED_PROPERTY_NAME));
        return builder().randomizeForTesting(printSeed, seed).build();
      } else {
        return builder().randomizeForTesting(printSeed).build();
      }
    }
    return fromIncludeExcludePatterns(
        System.getProperty(INCLUDE_PROPERTY_NAME), System.getProperty(EXCLUDE_PROPERTY_NAME));
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isRandomizeForTestingEnabled() {
    return randomizeForTesting != null;
  }

  public static class Builder implements PartialOptimizationConfigurationBuilder {
    private final R8PartialPredicateCollection includePredicates =
        new R8PartialPredicateCollection();
    private final R8PartialPredicateCollection excludePredicates =
        new R8PartialPredicateCollection();
    private Random randomizeForTesting;

    public R8PartialCompilationConfiguration build() {
      return new R8PartialCompilationConfiguration(
          !includePredicates.isEmpty() || randomizeForTesting != null,
          includePredicates,
          excludePredicates,
          randomizeForTesting);
    }

    public Builder includeAll() {
      includePredicates.add(new AllClassesMatcher());
      return this;
    }

    public Builder excludeAll() {
      excludePredicates.add(new AllClassesMatcher());
      return this;
    }

    public Builder randomizeForTesting(boolean printSeed) {
      return randomizeForTesting(printSeed, System.currentTimeMillis());
    }

    public Builder randomizeForTesting(boolean printSeed, long seed) {
      randomizeForTesting = new Random();
      randomizeForTesting.setSeed(seed);
      if (printSeed) {
        System.out.println(
            "Partial compilation seed: "
                + seed
                + ". Use .setPartialCompilationSeed(parameters, "
                + seed
                + "L) to reproduce.");
      }
      return this;
    }

    public Builder addJavaTypeIncludePattern(String pattern) {
      includePredicates.add(
          createPredicate("L" + DescriptorUtils.getBinaryNameFromJavaType(pattern)));
      return this;
    }

    public Builder addJavaTypeExcludePattern(String pattern) {
      excludePredicates.add(
          createPredicate("L" + DescriptorUtils.getBinaryNameFromJavaType(pattern)));
      return this;
    }

    public Builder includeClasses(Class<?>... classes) {
      return includeClasses(Arrays.asList(classes));
    }

    public Builder includeClasses(Collection<Class<?>> classes) {
      for (Class<?> clazz : classes) {
        includePredicates.add(new ClassNameMatcher(DescriptorUtils.javaClassToDescriptor(clazz)));
      }
      return this;
    }

    public Builder excludeClasses(Class<?>... classes) {
      return excludeClasses(Arrays.asList(classes));
    }

    public Builder excludeClasses(Collection<Class<?>> classes) {
      for (Class<?> clazz : classes) {
        excludePredicates.add(new ClassNameMatcher(DescriptorUtils.javaClassToDescriptor(clazz)));
      }
      return this;
    }

    private R8PartialPredicate createPredicate(String descriptorPrefix) {
      assert descriptorPrefix.startsWith("L");
      assert descriptorPrefix.indexOf('.') == -1;
      if (descriptorPrefix.equals(AllClassesMatcher.PATTERN)) {
        return new AllClassesMatcher();
      } else if (descriptorPrefix.equals(UnnamedPackageMatcher.PATTERN)) {
        return new UnnamedPackageMatcher();
      } else if (descriptorPrefix.endsWith("/**")) {
        return new PackageAndSubpackagePrefixMatcher(
            extractDescriptorPrefixWithoutWildcards(descriptorPrefix, 2));
      } else if (descriptorPrefix.endsWith("/*")) {
        return new PackagePrefixMatcher(
            extractDescriptorPrefixWithoutWildcards(descriptorPrefix, 1));
      } else if (descriptorPrefix.endsWith("*")) {
        return new ClassPrefixMatcher(extractDescriptorPrefixWithoutWildcards(descriptorPrefix, 1));
      } else {
        return new ClassNameMatcher(
            extractDescriptorPrefixWithoutWildcards(descriptorPrefix, 0) + ';');
      }
    }

    private static String extractDescriptorPrefixWithoutWildcards(
        String descriptorPattern, int numberOfWildcardsToRemove) {
      String descriptorPrefixWithoutWildcards =
          descriptorPattern.substring(0, descriptorPattern.length() - numberOfWildcardsToRemove);
      if (descriptorPrefixWithoutWildcards.indexOf('*') >= 0) {
        throw new IllegalArgumentException(
            "Expected descriptor prefix without wildcards, got: "
                + descriptorPrefixWithoutWildcards);
      }
      return descriptorPrefixWithoutWildcards;
    }

    @Override
    public PartialOptimizationConfigurationBuilder addClass(ClassReference classReference) {
      includePredicates.add(createPredicate("L" + classReference.getBinaryName()));
      return this;
    }

    @Override
    public PartialOptimizationConfigurationBuilder addPackage(PackageReference packageReference) {
      includePredicates.add(createPredicate("L" + packageReference.getPackageBinaryName() + "/*"));
      return this;
    }
  }
}
