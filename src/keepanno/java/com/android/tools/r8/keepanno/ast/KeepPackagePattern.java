// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.PackagePattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;

public abstract class KeepPackagePattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepPackagePattern any() {
    return Any.getInstance();
  }

  public static KeepPackagePattern top() {
    return Top.getInstance();
  }

  public static KeepPackagePattern exact(String fullPackage) {
    return KeepPackagePattern.builder().exact(fullPackage).build();
  }

  public static KeepPackagePattern fromProto(PackagePattern proto) {
    return builder().applyProto(proto).build();
  }

  public PackagePattern.Builder buildProto() {
    PackagePattern.Builder builder = PackagePattern.newBuilder();
    if (isAny()) {
      // An unset oneof implies "any package" (including multiple package parts).
      return builder;
    }
    if (isTop()) {
      // The top/unspecified package is encoded as the empty package name.
      return builder.setName(KeepSpecProtos.StringPattern.newBuilder().setExact(""));
    }
    // TODO(b/343389186): Rewrite the package patterns to use the tree structure.
    return builder.setExactPackageHack(getExactPackageAsString());
  }

  public static class Builder {

    private KeepPackagePattern pattern;

    public Builder applyProto(PackagePattern pkg) {
      if (pkg.hasExactPackageHack()) {
        exact(pkg.getExactPackageHack());
        return this;
      }
      if (pkg.hasName()) {
        KeepStringPattern stringPattern = KeepStringPattern.fromProto(pkg.getName());
        if (stringPattern.isExact() && stringPattern.asExactString().isEmpty()) {
          return top();
        }
        throw new Unimplemented();
      }
      if (pkg.hasNode()) {
        throw new Unimplemented();
      }
      // The unset oneof implies any package.
      assert pattern.isAny();
      return this;
    }

    public Builder any() {
      pattern = Any.getInstance();
      return this;
    }

    public Builder top() {
      pattern = Top.getInstance();
      return this;
    }

    public Builder exact(String fullPackage) {
      pattern = fullPackage.isEmpty() ? KeepPackagePattern.top() : new Exact(fullPackage);
      return this;
    }

    public KeepPackagePattern build() {
      if (pattern == null) {
        throw new KeepEdgeException("Invalid package pattern: null");
      }
      return pattern;
    }
  }

  private static final class Any extends KeepPackagePattern {

    private static final Any INSTANCE = new Any();

    public static Any getInstance() {
      return INSTANCE;
    }

    private Any() {}

    @Override
    public boolean isAny() {
      return true;
    }

    @Override
    public boolean isTop() {
      return false;
    }

    @Override
    public boolean isExact() {
      return false;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "*";
    }
  }

  private static final class Top extends Exact {

    private static final Top INSTANCE = new Top();

    public static Top getInstance() {
      return INSTANCE;
    }

    private Top() {
      super("");
    }

    @Override
    public boolean isAny() {
      return false;
    }

    @Override
    public boolean isTop() {
      return true;
    }

    @Override
    public String toString() {
      return "";
    }
  }

  private static class Exact extends KeepPackagePattern {

    private final String fullPackage;

    private Exact(String fullPackage) {
      assert fullPackage != null;
      this.fullPackage = fullPackage;
      // TODO: Verify valid package identifiers.
    }

    @Override
    public boolean isAny() {
      return false;
    }

    @Override
    public boolean isTop() {
      return fullPackage.equals("");
    }

    @Override
    public boolean isExact() {
      return true;
    }

    @Override
    public String getExactPackageAsString() {
      return fullPackage;
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Exact that = (Exact) o;
      return fullPackage.equals(that.fullPackage);
    }

    @Override
    public int hashCode() {
      return fullPackage.hashCode();
    }

    @Override
    public String toString() {
      return fullPackage;
    }
  }

  public abstract boolean isAny();

  public abstract boolean isTop();

  public abstract boolean isExact();

  public String getExactPackageAsString() {
    throw new IllegalStateException();
  }
}
