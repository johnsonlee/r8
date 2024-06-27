// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.MemberPatternField;
import com.android.tools.r8.keepanno.utils.Unimplemented;
import java.util.Objects;

public final class KeepFieldPattern extends KeepMemberPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepFieldPattern allFields() {
    return builder().build();
  }

  public static class Builder {

    private OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern =
        OptionalPattern.absent();
    private KeepFieldAccessPattern accessPattern = KeepFieldAccessPattern.anyFieldAccess();
    private KeepFieldNamePattern namePattern = KeepFieldNamePattern.any();
    private KeepFieldTypePattern typePattern = KeepFieldTypePattern.any();

    private Builder() {}

    public Builder self() {
      return this;
    }

    public Builder copyFromMemberPattern(KeepMemberPattern memberPattern) {
      assert memberPattern.isGeneralMember();
      return setAccessPattern(
          KeepFieldAccessPattern.builder()
              .copyOfMemberAccess(memberPattern.getAccessPattern())
              .build());
    }

    public Builder setAnnotatedByPattern(
        OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern) {
      this.annotatedByPattern = annotatedByPattern;
      return this;
    }

    public Builder setAccessPattern(KeepFieldAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
      return self();
    }

    public Builder setNamePattern(KeepFieldNamePattern namePattern) {
      this.namePattern = namePattern;
      return self();
    }

    public Builder setTypePattern(KeepFieldTypePattern typePattern) {
      this.typePattern = typePattern;
      return self();
    }

    public KeepFieldPattern build() {
      return new KeepFieldPattern(annotatedByPattern, accessPattern, namePattern, typePattern);
    }
  }

  private final OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern;
  private final KeepFieldAccessPattern accessPattern;
  private final KeepFieldNamePattern namePattern;
  private final KeepFieldTypePattern typePattern;

  private KeepFieldPattern(
      OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern,
      KeepFieldAccessPattern accessPattern,
      KeepFieldNamePattern namePattern,
      KeepFieldTypePattern typePattern) {
    assert annotatedByPattern != null;
    assert accessPattern != null;
    assert namePattern != null;
    assert typePattern != null;
    this.annotatedByPattern = annotatedByPattern;
    this.accessPattern = accessPattern;
    this.namePattern = namePattern;
    this.typePattern = typePattern;
  }

  @Override
  public KeepFieldPattern asField() {
    return this;
  }

  public boolean isAnyField() {
    return annotatedByPattern.isAbsent()
        && accessPattern.isAny()
        && namePattern.isAny()
        && typePattern.isAny();
  }

  @Override
  public OptionalPattern<KeepQualifiedClassNamePattern> getAnnotatedByPattern() {
    return annotatedByPattern;
  }

  @Override
  public KeepFieldAccessPattern getAccessPattern() {
    return accessPattern;
  }

  public KeepFieldNamePattern getNamePattern() {
    return namePattern;
  }

  public KeepFieldTypePattern getTypePattern() {
    return typePattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepFieldPattern)) {
      return false;
    }
    KeepFieldPattern that = (KeepFieldPattern) o;
    return annotatedByPattern.equals(that.annotatedByPattern)
        && accessPattern.equals(that.accessPattern)
        && namePattern.equals(that.namePattern)
        && typePattern.equals(that.typePattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(annotatedByPattern, accessPattern, namePattern, typePattern);
  }

  @Override
  public String toString() {
    return "KeepFieldPattern{"
        + annotatedByPattern.mapOrDefault(p -> "@" + p + ", ", "")
        + "access="
        + accessPattern
        + ", name="
        + namePattern
        + ", type="
        + typePattern
        + '}';
  }

  public MemberPatternField.Builder buildFieldProto() {
    throw new Unimplemented();
  }
}
