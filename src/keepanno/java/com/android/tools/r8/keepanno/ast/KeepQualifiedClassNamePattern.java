// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.ClassNamePattern;
import com.android.tools.r8.keepanno.utils.DescriptorUtils;
import java.util.Objects;
import java.util.function.Consumer;

public final class KeepQualifiedClassNamePattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepQualifiedClassNamePattern any() {
    return KeepQualifiedClassNamePattern.builder()
        .setPackagePattern(KeepPackagePattern.any())
        .setNamePattern(KeepUnqualfiedClassNamePattern.any())
        .build();
  }

  public static KeepQualifiedClassNamePattern exactFromDescriptor(String classDescriptor) {
    if (!DescriptorUtils.isValidClassDescriptor(classDescriptor)) {
      throw new KeepEdgeException("Invalid class descriptor: " + classDescriptor);
    }
    return exact(classDescriptor.substring(1, classDescriptor.length() - 1).replace('/', '.'));
  }

  public static KeepQualifiedClassNamePattern exact(String qualifiedClassName) {
    int pkgSeparator = qualifiedClassName.lastIndexOf('.');
    if (pkgSeparator == 0) {
      throw new KeepEdgeException("Unexpected '.' at index 0 in '" + qualifiedClassName + "'");
    }
    if (pkgSeparator > 0) {
      return KeepQualifiedClassNamePattern.builder()
          .setPackagePattern(
              KeepPackagePattern.exact(qualifiedClassName.substring(0, pkgSeparator)))
          .setNamePattern(
              KeepUnqualfiedClassNamePattern.exact(qualifiedClassName.substring(pkgSeparator + 1)))
          .build();
    }
    return KeepQualifiedClassNamePattern.builder()
        .setPackagePattern(KeepPackagePattern.top())
        .setNamePattern(KeepUnqualfiedClassNamePattern.exact(qualifiedClassName))
        .build();
  }

  public static KeepQualifiedClassNamePattern fromProto(ClassNamePattern clazz) {
    return KeepQualifiedClassNamePattern.builder().applyProto(clazz).build();
  }

  public void buildProtoIfNotAny(Consumer<ClassNamePattern.Builder> setter) {
    if (!isAny()) {
      ClassNamePattern.Builder builder = ClassNamePattern.newBuilder();
      builder.setPackage(packagePattern.buildProto()).setUnqualifiedName(namePattern.buildProto());
      setter.accept(builder);
    }
  }

  public static class Builder {

    private KeepPackagePattern packagePattern = KeepPackagePattern.any();
    private KeepUnqualfiedClassNamePattern namePattern = KeepUnqualfiedClassNamePattern.any();

    private Builder() {}

    public Builder applyProto(ClassNamePattern proto) {
      assert packagePattern.isAny();
      if (proto.hasPackage()) {
        setPackagePattern(KeepPackagePattern.fromProto(proto.getPackage()));
      }

      assert namePattern.isAny();
      if (proto.hasUnqualifiedName()) {
        setNamePattern(KeepUnqualfiedClassNamePattern.fromProto(proto.getUnqualifiedName()));
      }
      return this;
    }

    public Builder setPackagePattern(KeepPackagePattern packagePattern) {
      this.packagePattern = packagePattern;
      return this;
    }

    public Builder setNamePattern(KeepUnqualfiedClassNamePattern namePattern) {
      this.namePattern = namePattern;
      return this;
    }

    public KeepQualifiedClassNamePattern build() {
      return new KeepQualifiedClassNamePattern(packagePattern, namePattern);
    }
  }

  private final KeepPackagePattern packagePattern;
  private final KeepUnqualfiedClassNamePattern namePattern;

  public KeepQualifiedClassNamePattern(
      KeepPackagePattern packagePattern, KeepUnqualfiedClassNamePattern namePattern) {
    assert packagePattern != null;
    assert namePattern != null;
    this.packagePattern = packagePattern;
    this.namePattern = namePattern;
  }

  public boolean isAny() {
    return packagePattern.isAny() && namePattern.isAny();
  }

  public boolean isExact() {
    return packagePattern.isExact() && namePattern.isExact();
  }

  public String getExactDescriptor() {
    if (!isExact()) {
      throw new KeepEdgeException("Attempt to obtain exact qualified type for inexact pattern");
    }
    return 'L'
        + packagePattern.getExactPackageAsString().replace('.', '/')
        + (packagePattern.isTop() ? "" : "/")
        + namePattern.asExactString()
        + ';';
  }

  public KeepPackagePattern getPackagePattern() {
    return packagePattern;
  }

  public KeepUnqualfiedClassNamePattern getNamePattern() {
    return namePattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeepQualifiedClassNamePattern that = (KeepQualifiedClassNamePattern) o;
    return packagePattern.equals(that.packagePattern) && namePattern.equals(that.namePattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packagePattern.hashCode(), namePattern.hashCode());
  }

  @Override
  public String toString() {
    return packagePattern + (packagePattern.isTop() ? "" : ".") + namePattern;
  }
}
