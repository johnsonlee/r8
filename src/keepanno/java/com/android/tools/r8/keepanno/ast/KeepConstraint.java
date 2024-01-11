// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;

public abstract class KeepConstraint {

  @Override
  public String toString() {
    String typeName = getClass().getTypeName();
    return typeName.substring(typeName.lastIndexOf('$') + 1);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  public abstract void convertToDisallowKeepOptions(KeepOptions.Builder builder);

  public final boolean validForClass() {
    return !isMethodOnly() && !isFieldOnly();
  }

  public final boolean validForMethod() {
    return !isClassOnly() && !isFieldOnly();
  }

  public final boolean validForField() {
    return !isClassOnly() && !isMethodOnly();
  }

  boolean isClassOnly() {
    return false;
  }

  boolean isMethodOnly() {
    return false;
  }

  boolean isFieldOnly() {
    return false;
  }

  public static Lookup lookup() {
    return Lookup.INSTANCE;
  }

  public static final class Lookup extends KeepConstraint {

    private static final Lookup INSTANCE = new Lookup();

    private Lookup() {}

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.SHRINKING);
    }
  }

  public static Name name() {
    return Name.INSTANCE;
  }

  public static final class Name extends KeepConstraint {

    private static final Name INSTANCE = new Name();

    private Name() {}

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OBFUSCATING);
    }
  }

  public static VisibilityRelax visibilityRelax() {
    return VisibilityRelax.INSTANCE;
  }

  public static final class VisibilityRelax extends KeepConstraint {

    private static final VisibilityRelax INSTANCE = new VisibilityRelax();

    private VisibilityRelax() {}

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      // The compiler currently satisfies that access is never restricted.
    }
  }

  public static VisibilityRestrict visibilityRestrict() {
    return VisibilityRestrict.INSTANCE;
  }

  public static final class VisibilityRestrict extends KeepConstraint {

    private static final VisibilityRestrict INSTANCE = new VisibilityRestrict();

    private VisibilityRestrict() {}

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      // We don't have directional rules so this prohibits any modification.
      builder.add(KeepOption.ACCESS_MODIFICATION);
    }
  }

  public static NeverInline neverInline() {
    return NeverInline.INSTANCE;
  }

  public static final class NeverInline extends KeepConstraint {

    private static final NeverInline INSTANCE = new NeverInline();

    private NeverInline() {}

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }
  }

  public static ClassInstantiate classInstantiate() {
    return ClassInstantiate.INSTANCE;
  }

  public static final class ClassInstantiate extends KeepConstraint {

    private static final ClassInstantiate INSTANCE = new ClassInstantiate();

    private ClassInstantiate() {}

    @Override
    boolean isClassOnly() {
      return true;
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }
  }

  public static ClassOpenHierarchy classOpenHierarchy() {
    return ClassOpenHierarchy.INSTANCE;
  }

  public static final class ClassOpenHierarchy extends KeepConstraint {

    private static final ClassOpenHierarchy INSTANCE = new ClassOpenHierarchy();

    private ClassOpenHierarchy() {}

    @Override
    boolean isClassOnly() {
      return true;
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }
  }

  public static MethodInvoke methodInvoke() {
    return MethodInvoke.INSTANCE;
  }

  public static final class MethodInvoke extends KeepConstraint {

    private static final MethodInvoke INSTANCE = new MethodInvoke();

    private MethodInvoke() {}

    @Override
    boolean isMethodOnly() {
      return true;
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }
  }

  public static MethodReplace methodReplace() {
    return MethodReplace.INSTANCE;
  }

  public static final class MethodReplace extends KeepConstraint {

    private static final MethodReplace INSTANCE = new MethodReplace();

    private MethodReplace() {}

    @Override
    boolean isMethodOnly() {
      return true;
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }
  }

  public static FieldGet fieldGet() {
    return FieldGet.INSTANCE;
  }

  public static final class FieldGet extends KeepConstraint {

    private static final FieldGet INSTANCE = new FieldGet();

    private FieldGet() {}

    @Override
    boolean isFieldOnly() {
      return true;
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }
  }

  public static FieldSet fieldSet() {
    return FieldSet.INSTANCE;
  }

  public static final class FieldSet extends KeepConstraint {

    private static final FieldSet INSTANCE = new FieldSet();

    private FieldSet() {}

    @Override
    boolean isFieldOnly() {
      return true;
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }
  }

  public static FieldReplace fieldReplace() {
    return FieldReplace.INSTANCE;
  }

  public static final class FieldReplace extends KeepConstraint {

    private static final FieldReplace INSTANCE = new FieldReplace();

    private FieldReplace() {}

    @Override
    boolean isFieldOnly() {
      return true;
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }
  }

  public static Annotation annotationsAll() {
    return Annotation.ALL_INSTANCE;
  }

  public static Annotation annotation(KeepQualifiedClassNamePattern pattern) {
    if (pattern.isAny()) {
      return annotationsAll();
    }
    return new Annotation(pattern);
  }

  public static final class Annotation extends KeepConstraint {

    private static final Annotation ALL_INSTANCE =
        new Annotation(KeepQualifiedClassNamePattern.any());

    private final KeepQualifiedClassNamePattern classNamePattern;

    private Annotation(KeepQualifiedClassNamePattern classNamePattern) {
      this.classNamePattern = classNamePattern;
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      // The annotation constraint only implies that annotations should remain, no restrictions
      // are on the item otherwise. Also, we can't restrict the rule to just the annotations being
      // constrained in the legacy rules.
      builder.add(KeepOption.ANNOTATION_REMOVAL);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Annotation)) {
        return false;
      }
      Annotation that = (Annotation) o;
      return classNamePattern.equals(that);
    }

    @Override
    public int hashCode() {
      return classNamePattern.hashCode();
    }
  }
}
