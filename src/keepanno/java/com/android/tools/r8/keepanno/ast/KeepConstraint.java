// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.AnnotationConstants.Constraints;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.Constraint;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.ConstraintElement;
import java.util.Set;
import java.util.function.Consumer;

public abstract class KeepConstraint {

  public Constraint.Builder buildProto() {
    return Constraint.newBuilder().setElement(buildElementProto());
  }

  public abstract ConstraintElement buildElementProto();

  public static void fromProto(Constraint proto, Consumer<KeepConstraint> callback) {
    if (proto.hasElement()) {
      KeepConstraint constraint = getConstraintFromProtoEnum(proto.getElement());
      if (constraint != null) {
        callback.accept(constraint);
      }
      return;
    }
    if (proto.hasAnnotation()) {
      callback.accept(
          KeepConstraint.annotation(KeepAnnotationPattern.fromProto(proto.getAnnotation())));
    }
  }

  private static KeepConstraint getConstraintFromProtoEnum(ConstraintElement proto) {
    switch (proto.getNumber()) {
      case ConstraintElement.CONSTRAINT_LOOKUP_VALUE:
        return lookup();
      case ConstraintElement.CONSTRAINT_NAME_VALUE:
        return name();
      case ConstraintElement.CONSTRAINT_VISIBILITY_RELAX_VALUE:
        return visibilityRelax();
      case ConstraintElement.CONSTRAINT_VISIBILITY_RESTRICT_VALUE:
        return visibilityRestrict();
      case ConstraintElement.CONSTRAINT_NEVER_INLINE_VALUE:
        return neverInline();
      case ConstraintElement.CONSTRAINT_CLASS_INSTANTIATE_VALUE:
        return classInstantiate();
      case ConstraintElement.CONSTRAINT_CLASS_OPEN_HIERARCHY_VALUE:
        return classOpenHierarchy();
      case ConstraintElement.CONSTRAINT_METHOD_INVOKE_VALUE:
        return methodInvoke();
      case ConstraintElement.CONSTRAINT_METHOD_REPLACE_VALUE:
        return methodReplace();
      case ConstraintElement.CONSTRAINT_FIELD_GET_VALUE:
        return fieldGet();
      case ConstraintElement.CONSTRAINT_FIELD_SET_VALUE:
        return fieldSet();
      case ConstraintElement.CONSTRAINT_FIELD_REPLACE_VALUE:
        return fieldReplace();
      case ConstraintElement.CONSTRAINT_GENERIC_SIGNATURE_VALUE:
        return genericSignature();
      default:
        assert proto == ConstraintElement.CONSTRAINT_UNSPECIFIED;
        return null;
    }
  }

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

  public abstract void accept(KeepConstraintVisitor visitor);

  public abstract String getEnumValue();

  public KeepAnnotationPattern asAnnotationPattern() {
    return null;
  }

  public abstract void convertToDisallowKeepOptions(KeepOptions.Builder builder);

  public void addRequiredKeepAttributes(Set<KeepAttribute> attributes) {
    // Common case is no required attributes.
  }

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
    public String getEnumValue() {
      return Constraints.LOOKUP;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onLookup(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.SHRINKING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_LOOKUP;
    }
  }

  public static Name name() {
    return Name.INSTANCE;
  }

  public static final class Name extends KeepConstraint {

    private static final Name INSTANCE = new Name();

    private Name() {}

    @Override
    public String getEnumValue() {
      return Constraints.NAME;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onName(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OBFUSCATING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_NAME;
    }
  }

  public static VisibilityRelax visibilityRelax() {
    return VisibilityRelax.INSTANCE;
  }

  public static final class VisibilityRelax extends KeepConstraint {

    private static final VisibilityRelax INSTANCE = new VisibilityRelax();

    private VisibilityRelax() {}

    @Override
    public String getEnumValue() {
      return Constraints.VISIBILITY_RELAX;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onVisibilityRelax(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      // The compiler currently satisfies that access is never restricted.
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_VISIBILITY_RELAX;
    }
  }

  public static VisibilityRestrict visibilityRestrict() {
    return VisibilityRestrict.INSTANCE;
  }

  public static final class VisibilityRestrict extends KeepConstraint {

    private static final VisibilityRestrict INSTANCE = new VisibilityRestrict();

    private VisibilityRestrict() {}

    @Override
    public String getEnumValue() {
      return Constraints.VISIBILITY_RESTRICT;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onVisibilityRestrict(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      // We don't have directional rules so this prohibits any modification.
      builder.add(KeepOption.ACCESS_MODIFICATION);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_VISIBILITY_RESTRICT;
    }
  }

  public static NeverInline neverInline() {
    return NeverInline.INSTANCE;
  }

  public static final class NeverInline extends KeepConstraint {

    private static final NeverInline INSTANCE = new NeverInline();

    private NeverInline() {}

    @Override
    public String getEnumValue() {
      return Constraints.NEVER_INLINE;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onNeverInline(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_NEVER_INLINE;
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
    public String getEnumValue() {
      return Constraints.CLASS_INSTANTIATE;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onClassInstantiate(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_CLASS_INSTANTIATE;
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
    public String getEnumValue() {
      return Constraints.CLASS_OPEN_HIERARCHY;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onClassOpenHierarchy(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_CLASS_OPEN_HIERARCHY;
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
    public String getEnumValue() {
      return Constraints.METHOD_INVOKE;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onMethodInvoke(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_METHOD_INVOKE;
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
    public String getEnumValue() {
      return Constraints.METHOD_REPLACE;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onMethodReplace(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_METHOD_REPLACE;
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
    public String getEnumValue() {
      return Constraints.FIELD_GET;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onFieldGet(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_FIELD_GET;
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
    public String getEnumValue() {
      return Constraints.FIELD_SET;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onFieldSet(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_FIELD_SET;
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
    public String getEnumValue() {
      return Constraints.FIELD_REPLACE;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onFieldReplace(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.OPTIMIZING);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_FIELD_REPLACE;
    }
  }

  public static GenericSignature genericSignature() {
    return GenericSignature.INSTANCE;
  }

  public static final class GenericSignature extends KeepConstraint {

    private static final GenericSignature INSTANCE = new GenericSignature();

    private GenericSignature() {}

    @Override
    public String getEnumValue() {
      return Constraints.GENERIC_SIGNATURE;
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onGenericSignature(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      builder.add(KeepOption.SIGNATURE_REMOVAL);
    }

    @Override
    public void addRequiredKeepAttributes(Set<KeepAttribute> attributes) {
      attributes.add(KeepAttribute.GENERIC_SIGNATURES);
    }

    @Override
    public ConstraintElement buildElementProto() {
      return ConstraintElement.CONSTRAINT_GENERIC_SIGNATURE;
    }
  }

  public static Annotation annotationsAll() {
    return Annotation.ALL_INSTANCE;
  }

  public static Annotation annotationsAllWithRuntimeRetention() {
    return Annotation.ALL_WITH_RUNTIME_RETENTION_INSTANCE;
  }

  public static Annotation annotationsAllWithClassRetention() {
    return Annotation.ALL_WITH_CLASS_RETENTION_INSTANCE;
  }

  public static Annotation annotation(KeepAnnotationPattern pattern) {
    if (pattern.isAny()) {
      return annotationsAll();
    }
    if (pattern.isAnyWithRuntimeRetention()) {
      return annotationsAllWithRuntimeRetention();
    }
    if (pattern.isAnyWithClassRetention()) {
      return annotationsAllWithClassRetention();
    }
    return new Annotation(pattern);
  }

  public static final class Annotation extends KeepConstraint {

    private static final Annotation ALL_INSTANCE = new Annotation(KeepAnnotationPattern.any());

    private static final Annotation ALL_WITH_RUNTIME_RETENTION_INSTANCE =
        new Annotation(KeepAnnotationPattern.anyWithRuntimeRetention());

    private static final Annotation ALL_WITH_CLASS_RETENTION_INSTANCE =
        new Annotation(KeepAnnotationPattern.anyWithClassRetention());

    private final KeepAnnotationPattern annotationPattern;

    private Annotation(KeepAnnotationPattern annotationPattern) {
      assert annotationPattern != null;
      this.annotationPattern = annotationPattern;
    }

    @Override
    public KeepAnnotationPattern asAnnotationPattern() {
      return annotationPattern;
    }

    @Override
    public String getEnumValue() {
      // The annotation constraints cannot be represented by an enum value.
      return null;
    }

    @Override
    public ConstraintElement buildElementProto() {
      throw new KeepEdgeException("Unexpected attempt to build element for annotation constraint");
    }

    @Override
    public Constraint.Builder buildProto() {
      return Constraint.newBuilder().setAnnotation(annotationPattern.buildProto());
    }

    @Override
    public void accept(KeepConstraintVisitor visitor) {
      visitor.onAnnotation(this);
    }

    @Override
    public void convertToDisallowKeepOptions(KeepOptions.Builder builder) {
      // The annotation constraint only implies that annotations should remain, no restrictions
      // are on the item otherwise. Also, we can't restrict the rule to just the annotations being
      // constrained in the legacy rules.
      builder.add(KeepOption.ANNOTATION_REMOVAL);
    }

    @Override
    public void addRequiredKeepAttributes(Set<KeepAttribute> attributes) {
      if (annotationPattern.includesRuntimeRetention()) {
        attributes.add(KeepAttribute.RUNTIME_VISIBLE_ANNOTATIONS);
        attributes.add(KeepAttribute.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS);
        attributes.add(KeepAttribute.RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
      }
      if (annotationPattern.includesClassRetention()) {
        attributes.add(KeepAttribute.RUNTIME_INVISIBLE_ANNOTATIONS);
        attributes.add(KeepAttribute.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS);
        attributes.add(KeepAttribute.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS);
      }
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
      return annotationPattern.equals(that.annotationPattern);
    }

    @Override
    public int hashCode() {
      return annotationPattern.hashCode();
    }
  }
}
