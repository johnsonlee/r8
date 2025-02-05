// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.partial.predicate.AllClassesMatcher;
import com.android.tools.r8.partial.predicate.ClassNameMatcher;
import com.android.tools.r8.partial.predicate.ClassPrefixMatcher;
import com.android.tools.r8.partial.predicate.PackageAndSubpackagePrefixMatcher;
import com.android.tools.r8.partial.predicate.PackagePrefixMatcher;
import com.android.tools.r8.partial.predicate.R8PartialPredicate;
import com.android.tools.r8.partial.predicate.R8PartialPredicateCollection;
import com.android.tools.r8.partial.predicate.UnnamedPackageMatcher;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Splitter;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

public class R8PartialCompilationConfiguration {

  private final boolean enabled;
  private final R8PartialPredicateCollection includePredicates;
  private final R8PartialPredicateCollection excludePredicates;

  public Consumer<InternalOptions> d8DexOptionsConsumer = ConsumerUtils.emptyConsumer();
  public Consumer<InternalOptions> r8OptionsConsumer = ConsumerUtils.emptyConsumer();

  private static final R8PartialCompilationConfiguration disabledConfiguration =
      new R8PartialCompilationConfiguration(false, null, null);

  private R8PartialCompilationConfiguration(
      boolean enabled,
      R8PartialPredicateCollection includePredicates,
      R8PartialPredicateCollection excludePredicates) {
    assert !enabled || !includePredicates.isEmpty();
    assert !enabled || excludePredicates != null;
    this.enabled = enabled;
    this.includePredicates = includePredicates;
    this.excludePredicates = excludePredicates;
  }

  public R8PartialPredicateCollection getIncludePredicates() {
    return includePredicates;
  }

  public R8PartialPredicateCollection getExcludePredicates() {
    return excludePredicates;
  }

  public boolean test(DexProgramClass clazz) {
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

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public static class Builder {
    private final R8PartialPredicateCollection includePredicates =
        new R8PartialPredicateCollection();
    private final R8PartialPredicateCollection excludePredicates =
        new R8PartialPredicateCollection();

    private Builder() {}

    public R8PartialCompilationConfiguration build() {
      return new R8PartialCompilationConfiguration(
          !includePredicates.isEmpty(), includePredicates, excludePredicates);
    }

    public Builder includeAll() {
      includePredicates.add(new AllClassesMatcher());
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
            descriptorPrefix.substring(0, descriptorPrefix.length() - 2));
      } else if (descriptorPrefix.endsWith("/*")) {
        return new PackagePrefixMatcher(
            descriptorPrefix.substring(0, descriptorPrefix.length() - 1));
      } else if (descriptorPrefix.endsWith("*")) {
        return new ClassPrefixMatcher(descriptorPrefix.substring(0, descriptorPrefix.length() - 1));
      } else {
        return new ClassNameMatcher(descriptorPrefix + ';');
      }
    }
  }
}
