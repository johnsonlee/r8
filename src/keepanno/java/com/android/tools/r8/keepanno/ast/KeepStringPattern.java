// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.StringPattern;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.StringPatternInexact;
import java.util.Objects;

public class KeepStringPattern {

  private static final KeepStringPattern ANY = new KeepStringPattern(null, null, null);

  public static KeepStringPattern any() {
    return ANY;
  }

  public static KeepStringPattern exact(String exact) {
    return KeepStringPattern.builder().setExact(exact).build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static KeepStringPattern fromProto(StringPattern proto) {
    return builder().applyProto(proto).build();
  }

  public StringPattern.Builder buildProto() {
    StringPattern.Builder builder = StringPattern.newBuilder();
    if (isAny()) {
      // An unset oneof signifies the "any" string pattern.
      return builder;
    }
    if (isExact()) {
      return builder.setExact(exact);
    }
    return builder.setInexact(
        StringPatternInexact.newBuilder()
            .setPrefix(prefix == null ? "" : prefix)
            .setSuffix(suffix == null ? "" : suffix));
  }

  public static class Builder {
    private String exact = null;
    private String prefix = null;
    private String suffix = null;

    private Builder() {}

    public Builder applyProto(StringPattern name) {
      if (name.hasExact()) {
        return setExact(name.getExact());
      }
      if (name.hasInexact()) {
        StringPatternInexact inexact = name.getInexact();
        setPrefix(inexact.getPrefix());
        setSuffix(inexact.getSuffix());
        return this;
      }
      // The unset oneof implies any. Don't assign any fields.
      assert build().isAny();
      return this;
    }

    public Builder setExact(String exact) {
      this.exact = exact;
      return this;
    }

    public Builder setPrefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public Builder setSuffix(String suffix) {
      this.suffix = suffix;
      return this;
    }

    public KeepStringPattern build() {
      if (exact != null) {
        return new KeepStringPattern(exact, null, null);
      }
      if (prefix == null && suffix == null) {
        return ANY;
      }
      return new KeepStringPattern(exact, prefix, suffix);
    }
  }

  private final String exact;
  private final String prefix;
  private final String suffix;

  private KeepStringPattern(String exact, String prefix, String suffix) {
    this.exact = exact;
    this.prefix = prefix;
    this.suffix = suffix;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepStringPattern)) {
      return false;
    }
    KeepStringPattern that = (KeepStringPattern) o;
    return Objects.equals(exact, that.exact)
        && Objects.equals(prefix, that.prefix)
        && Objects.equals(suffix, that.suffix);
  }

  @Override
  public int hashCode() {
    return Objects.hash(exact, prefix, suffix);
  }

  public boolean isAny() {
    return ANY == this;
  }

  public boolean isExact() {
    return exact != null;
  }

  public String asExactString() {
    return exact;
  }

  public boolean hasPrefix() {
    return prefix != null;
  }

  public boolean hasSuffix() {
    return suffix != null;
  }

  public String getPrefixString() {
    return prefix;
  }

  public String getSuffixString() {
    return suffix;
  }

  @Override
  public String toString() {
    if (isAny()) {
      return "<*>";
    }
    if (isExact()) {
      return exact;
    }
    return (hasPrefix() ? prefix : "") + "<*>" + (hasSuffix() ? suffix : "");
  }
}
