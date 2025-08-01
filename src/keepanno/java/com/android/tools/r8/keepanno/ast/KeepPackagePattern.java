// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.PackageComponentPattern;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.PackagePattern;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Pattern over the Java package structure.
 *
 * <p>The patterns are represented as a list of package component patterns. A package component is
 * the string content between two package separators (separators are `.` as well as the package
 * start and end).
 */
public class KeepPackagePattern {

  private static final KeepPackagePattern ANY =
      new KeepPackagePattern(false, ImmutableList.of(KeepPackageComponentPattern.zeroOrMore()));

  private static final KeepPackagePattern TOP =
      new KeepPackagePattern(true, ImmutableList.of(KeepPackageComponentPattern.exact("")));

  public static KeepPackagePattern any() {
    return ANY;
  }

  public static KeepPackagePattern top() {
    return TOP;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static KeepPackagePattern exact(String fullPackage) {
    if (fullPackage.isEmpty()) {
      return top();
    }
    int length = fullPackage.length();
    Builder builder = KeepPackagePattern.builder();
    int componentStart = 0;
    for (int i = 0; i < length; i++) {
      if (fullPackage.charAt(i) == '.') {
        if (componentStart == i) {
          throw new KeepEdgeException("Invalid package string: " + fullPackage + "'");
        }
        String substring = fullPackage.substring(componentStart, i);
        builder.append(KeepPackageComponentPattern.exact(substring));
        componentStart = i + 1;
      }
    }
    if (componentStart == length) {
      throw new KeepEdgeException("Invalid package string: '" + fullPackage + "'");
    }
    String remaining = fullPackage.substring(componentStart, length);
    builder.append(KeepPackageComponentPattern.exact(remaining));
    return builder.build();
  }

  public static KeepPackagePattern fromProto(PackagePattern proto) {
    return builder().applyProto(proto).build();
  }

  public final PackagePattern.Builder buildProto() {
    PackagePattern.Builder builder = PackagePattern.newBuilder();
    if (isAny()) {
      // Any package is serialized out as the empty proto.
      return builder;
    }
    for (KeepPackageComponentPattern componentPattern : componentPatterns) {
      builder.addComponents(componentPattern.buildProto());
    }
    return builder;
  }

  public static class Builder {

    private Deque<KeepPackageComponentPattern> componentPatterns = new ArrayDeque<>();

    public Builder applyProto(PackagePattern proto) {
      for (PackageComponentPattern componentProto : proto.getComponentsList()) {
        append(KeepPackageComponentPattern.fromProto(componentProto));
      }
      return this;
    }

    public Builder any() {
      componentPatterns.clear();
      return this;
    }

    public Builder top() {
      componentPatterns.clear();
      componentPatterns.add(KeepPackageComponentPattern.single(KeepStringPattern.exact("")));
      return this;
    }

    public Builder append(KeepPackageComponentPattern componentPattern) {
      componentPatterns.addLast(componentPattern);
      return this;
    }

    public KeepPackagePattern build() {
      if (componentPatterns.isEmpty()) {
        return KeepPackagePattern.any();
      }
      boolean isExact = true;
      boolean previousIsZeroOrMore = false;
      ImmutableList.Builder<KeepPackageComponentPattern> builder = ImmutableList.builder();
      for (KeepPackageComponentPattern component : componentPatterns) {
        if (component.isZeroOrMore()) {
          if (!previousIsZeroOrMore) {
            builder.add(component);
          }
          isExact = false;
          previousIsZeroOrMore = true;
        } else {
          builder.add(component);
          isExact &= component.getSinglePattern().isExact();
        }
      }
      ImmutableList<KeepPackageComponentPattern> finalComponents = builder.build();
      if (finalComponents.size() == 1) {
        KeepPackageComponentPattern single = finalComponents.get(0);
        if (single.isZeroOrMore()) {
          return KeepPackagePattern.any();
        }
        if (isExact && single.getSinglePattern().asExactString().isEmpty()) {
          return KeepPackagePattern.top();
        }
      }
      return new KeepPackagePattern(isExact, finalComponents);
    }
  }

  // Cached value to avoid traversing the components to determine exact package strings.
  private final boolean isExact;
  private final List<KeepPackageComponentPattern> componentPatterns;

  private KeepPackagePattern(
      boolean isExact, ImmutableList<KeepPackageComponentPattern> componentPatterns) {
    assert !componentPatterns.isEmpty();
    assert !isExact || componentPatterns.stream().allMatch(KeepPackageComponentPattern::isExact);
    this.isExact = isExact;
    this.componentPatterns = componentPatterns;
  }

  public List<KeepPackageComponentPattern> getComponents() {
    return componentPatterns;
  }

  public boolean isAny() {
    return componentPatterns.size() == 1 && componentPatterns.get(0).isZeroOrMore();
  }

  public boolean isTop() {
    if (componentPatterns.size() != 1) {
      return false;
    }
    KeepPackageComponentPattern component = componentPatterns.get(0);
    if (component.isZeroOrMore()) {
      return false;
    }
    KeepStringPattern singlePattern = component.getSinglePattern();
    return singlePattern.isExact() && singlePattern.asExactString().isEmpty();
  }

  public boolean isExact() {
    return isExact;
  }

  public String getExactPackageAsString() {
    if (!isExact) {
      throw new KeepEdgeException("Invalid attempt to get exact from inexact package pattern");
    }
    if (isTop()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < componentPatterns.size(); i++) {
      KeepPackageComponentPattern componentPattern = componentPatterns.get(i);
      if (i > 0) {
        builder.append('.');
      }
      builder.append(componentPattern.getSinglePattern().asExactString());
    }
    return builder.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof KeepPackagePattern)) {
      return false;
    }
    KeepPackagePattern other = (KeepPackagePattern) obj;
    return isExact == other.isExact && componentPatterns.equals(other.componentPatterns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isExact, componentPatterns);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    componentPatterns.forEach(p -> sb.append(p).append("|"));
    return sb.toString();
  }
}
