// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.MemberPatternMethod;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.MethodParameterTypesPattern;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.MethodReturnTypePattern;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.TypePattern;
import java.util.Objects;

public final class KeepMethodPattern extends KeepMemberPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepMethodPattern allMethods() {
    return builder().build();
  }

  public static class Builder {

    private OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern =
        OptionalPattern.absent();
    private KeepMethodAccessPattern accessPattern = KeepMethodAccessPattern.anyMethodAccess();
    private KeepMethodNamePattern namePattern = KeepMethodNamePattern.any();
    private KeepMethodReturnTypePattern returnTypePattern = KeepMethodReturnTypePattern.any();
    private KeepMethodParametersPattern parametersPattern = KeepMethodParametersPattern.any();

    private Builder() {}

    public Builder self() {
      return this;
    }

    public Builder applyProto(MemberPatternMethod proto) {
      assert namePattern.isAny();
      if (proto.hasName()) {
        setNamePattern(
            KeepMethodNamePattern.fromStringPattern(KeepStringPattern.fromProto(proto.getName())));
      }

      assert returnTypePattern.isAny();
      if (proto.hasReturnType()) {
        MethodReturnTypePattern returnType = proto.getReturnType();
        if (returnType.hasVoidType()) {
          setReturnTypeVoid();
        } else if (returnType.hasSomeType()) {
          setReturnTypePattern(
              KeepMethodReturnTypePattern.fromType(
                  KeepTypePattern.fromProto(returnType.getSomeType())));
        }
      }

      assert parametersPattern.isAny();
      if (proto.hasParameterTypes()) {
        MethodParameterTypesPattern parameterTypes = proto.getParameterTypes();
        KeepMethodParametersPattern.Builder parametersBuilder =
            KeepMethodParametersPattern.builder();
        for (TypePattern typePattern : parameterTypes.getTypesList()) {
          parametersBuilder.addParameterTypePattern(KeepTypePattern.fromProto(typePattern));
        }
        setParametersPattern(parametersBuilder.build());
      }

      assert accessPattern.isAny();
      if (proto.hasAccess()) {
        setAccessPattern(KeepMethodAccessPattern.fromMethodProto(proto.getAccess()));
      }

      assert annotatedByPattern.isAbsent();
      if (proto.hasAnnotatedBy()) {
        setAnnotatedByPattern(KeepSpecUtils.annotatedByFromProto(proto.getAnnotatedBy()));
      }
      return this;
    }

    public Builder copyFromMemberPattern(KeepMemberPattern memberPattern) {
      assert memberPattern.isGeneralMember();
      return setAccessPattern(
          KeepMethodAccessPattern.builder()
              .copyOfMemberAccess(memberPattern.getAccessPattern())
              .build());
    }

    public Builder setAnnotatedByPattern(
        OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern) {
      this.annotatedByPattern = annotatedByPattern;
      return this;
    }

    public Builder setAccessPattern(KeepMethodAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
      return self();
    }

    public Builder setNamePattern(KeepMethodNamePattern namePattern) {
      this.namePattern = namePattern;
      return self();
    }

    public Builder setReturnTypePattern(KeepMethodReturnTypePattern returnTypePattern) {
      this.returnTypePattern = returnTypePattern;
      return self();
    }

    public Builder setReturnTypeVoid() {
      return setReturnTypePattern(KeepMethodReturnTypePattern.voidType());
    }

    public Builder setParametersPattern(KeepMethodParametersPattern parametersPattern) {
      this.parametersPattern = parametersPattern;
      return self();
    }

    public KeepMethodPattern build() {
      KeepMethodReturnTypePattern returnTypePattern = this.returnTypePattern;
      if (namePattern.isInstanceInitializer() || namePattern.isClassInitializer()) {
        if (!this.returnTypePattern.isAny() && !this.returnTypePattern.isVoid()) {
          throw new KeepEdgeException("Method constructor pattern must match 'void' type.");
        }
        returnTypePattern = KeepMethodReturnTypePattern.voidType();
      }
      return new KeepMethodPattern(
          annotatedByPattern, accessPattern, namePattern, returnTypePattern, parametersPattern);
    }
  }

  private final OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern;
  private final KeepMethodAccessPattern accessPattern;
  private final KeepMethodNamePattern namePattern;
  private final KeepMethodReturnTypePattern returnTypePattern;
  private final KeepMethodParametersPattern parametersPattern;

  private KeepMethodPattern(
      OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern,
      KeepMethodAccessPattern accessPattern,
      KeepMethodNamePattern namePattern,
      KeepMethodReturnTypePattern returnTypePattern,
      KeepMethodParametersPattern parametersPattern) {
    assert annotatedByPattern != null;
    assert accessPattern != null;
    assert namePattern != null;
    assert returnTypePattern != null;
    assert parametersPattern != null;
    this.annotatedByPattern = annotatedByPattern;
    this.accessPattern = accessPattern;
    this.namePattern = namePattern;
    this.returnTypePattern = returnTypePattern;
    this.parametersPattern = parametersPattern;
  }

  @Override
  public KeepMethodPattern asMethod() {
    return this;
  }

  public boolean isAnyMethod() {
    return annotatedByPattern.isAbsent()
        && accessPattern.isAny()
        && namePattern.isAny()
        && returnTypePattern.isAny()
        && parametersPattern.isAny();
  }

  @Override
  public OptionalPattern<KeepQualifiedClassNamePattern> getAnnotatedByPattern() {
    return annotatedByPattern;
  }

  @Override
  public KeepMethodAccessPattern getAccessPattern() {
    return accessPattern;
  }

  public KeepMethodNamePattern getNamePattern() {
    return namePattern;
  }

  public KeepMethodReturnTypePattern getReturnTypePattern() {
    return returnTypePattern;
  }

  public KeepMethodParametersPattern getParametersPattern() {
    return parametersPattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepMethodPattern)) {
      return false;
    }
    KeepMethodPattern that = (KeepMethodPattern) o;
    return annotatedByPattern.equals(that.annotatedByPattern)
        && accessPattern.equals(that.accessPattern)
        && namePattern.equals(that.namePattern)
        && returnTypePattern.equals(that.returnTypePattern)
        && parametersPattern.equals(that.parametersPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        annotatedByPattern, accessPattern, namePattern, returnTypePattern, parametersPattern);
  }

  @Override
  public String toString() {
    return "KeepMethodPattern{"
        + annotatedByPattern.mapOrDefault(p -> "@" + p + ", ", "")
        + "access="
        + accessPattern
        + ", name="
        + namePattern
        + ", returnType="
        + returnTypePattern
        + ", parameters="
        + parametersPattern
        + '}';
  }

  public static KeepMemberPattern fromMethodMemberProto(MemberPatternMethod methodMember) {
    return builder().applyProto(methodMember).build();
  }

  public MemberPatternMethod.Builder buildMethodProto() {
    MemberPatternMethod.Builder builder =
        MemberPatternMethod.newBuilder()
            .setName(namePattern.asStringPattern().buildProto())
            .setReturnType(returnTypePattern.buildProto());
    if (!parametersPattern.isAny()) {
      builder.setParameterTypes(parametersPattern.buildProto());
    }
    accessPattern.buildMethodProto(builder::setAccess);
    KeepSpecUtils.buildAnnotatedByProto(annotatedByPattern, builder::setAnnotatedBy);
    return builder;
  }
}
