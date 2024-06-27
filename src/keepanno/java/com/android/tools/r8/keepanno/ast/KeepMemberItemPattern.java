// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.BindingReference;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.MemberItemPattern;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

public class KeepMemberItemPattern extends KeepItemPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepItemPattern fromMemberProto(
      MemberItemPattern protoMember, Function<String, KeepBindingSymbol> getSymbol) {
    return builder().applyProto(protoMember, getSymbol).build();
  }

  public MemberItemPattern.Builder buildMemberProto() {
    return MemberItemPattern.newBuilder()
        .setClassReference(classReference.buildProto())
        .setMemberPattern(memberPattern.buildProto());
  }

  public static class Builder {

    private KeepClassBindingReference classReference = null;
    private KeepMemberPattern memberPattern = KeepMemberPattern.allMembers();

    private Builder() {}

    public Builder applyProto(
        MemberItemPattern protoMember, Function<String, KeepBindingSymbol> getSymbol) {
      // The class-reference is special as we can't artificially insert an "any class" without
      // patching up the bindings. Thus, the following hard fails if the format is missing any
      // part of the class-reference structure, both here and in the bindings themselves.
      if (!protoMember.hasClassReference()) {
        throw new KeepEdgeException("Invalid MemberItemPattern, must have a valid class reference");
      }
      BindingReference protoClassRef = protoMember.getClassReference();
      String protoName = protoClassRef.getName();
      KeepBindingSymbol symbol = getSymbol.apply(protoName);
      if (symbol == null) {
        throw new KeepEdgeException(
            "Invalid MemberItemPattern, reference to unbound binding: '" + protoName + "'");
      }
      setClassReference(KeepBindingReference.forClass(symbol));

      assert memberPattern.isAllMembers();
      if (protoMember.hasMemberPattern()) {
        setMemberPattern(KeepMemberPattern.fromMemberProto(protoMember.getMemberPattern()));
      }
      return this;
    }

    public Builder copyFrom(KeepMemberItemPattern pattern) {
      return setClassReference(pattern.getClassReference())
          .setMemberPattern(pattern.getMemberPattern());
    }

    public Builder setClassReference(KeepClassBindingReference classReference) {
      this.classReference = classReference;
      return this;
    }

    public Builder setMemberPattern(KeepMemberPattern memberPattern) {
      this.memberPattern = memberPattern;
      return this;
    }

    public KeepMemberItemPattern build() {
      if (classReference == null) {
        throw new KeepEdgeException(
            "Invalid attempt to build a member pattern without a class reference");
      }
      return new KeepMemberItemPattern(classReference, memberPattern);
    }
  }

  private final KeepClassBindingReference classReference;
  private final KeepMemberPattern memberPattern;

  private KeepMemberItemPattern(
      KeepClassBindingReference classReference, KeepMemberPattern memberPattern) {
    assert classReference != null;
    assert memberPattern != null;
    this.classReference = classReference;
    this.memberPattern = memberPattern;
  }

  @Override
  public KeepMemberItemPattern asMemberItemPattern() {
    return this;
  }

  public KeepClassBindingReference getClassReference() {
    return classReference;
  }

  public KeepMemberPattern getMemberPattern() {
    return memberPattern;
  }

  public Collection<KeepBindingReference> getBindingReferences() {
    return Collections.singletonList(classReference);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof KeepMemberItemPattern)) {
      return false;
    }
    KeepMemberItemPattern that = (KeepMemberItemPattern) obj;
    return classReference.equals(that.classReference) && memberPattern.equals(that.memberPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classReference, memberPattern);
  }

  @Override
  public String toString() {
    return "KeepMemberItemPattern"
        + "{ class="
        + classReference
        + ", members="
        + memberPattern
        + '}';
  }
}
