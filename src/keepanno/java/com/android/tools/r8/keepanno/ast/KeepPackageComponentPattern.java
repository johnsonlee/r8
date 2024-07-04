// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.PackageComponentPattern;
import java.util.Objects;

/**
 * Pattern over package components.
 *
 * <p>The patterns allow matching a single component with a string pattern, or the special case of
 * matching any number of arbitrary components (including none).
 *
 * <p>It is not possible to match across component separators except by the "zero or more" pattern.
 */
public class KeepPackageComponentPattern {

  private static final KeepPackageComponentPattern ZERO_OR_MORE =
      new KeepPackageComponentPattern(null);

  private static final KeepPackageComponentPattern ANY_SINGLE = single(KeepStringPattern.any());

  public static KeepPackageComponentPattern zeroOrMore() {
    return ZERO_OR_MORE;
  }

  public static KeepPackageComponentPattern single(KeepStringPattern singlePattern) {
    assert singlePattern != null;
    return singlePattern.isAny() ? ANY_SINGLE : new KeepPackageComponentPattern(singlePattern);
  }

  public static KeepPackageComponentPattern exact(String exact) {
    return single(KeepStringPattern.exact(exact));
  }

  public static KeepPackageComponentPattern fromProto(PackageComponentPattern proto) {
    if (proto.hasSingleComponent()) {
      return single(KeepStringPattern.fromProto(proto.getSingleComponent()));
    }
    return zeroOrMore();
  }

  public PackageComponentPattern.Builder buildProto() {
    PackageComponentPattern.Builder builder = PackageComponentPattern.newBuilder();
    // The zero-or-more pattern is a component without a single-pattern.
    if (isSingle()) {
      builder.setSingleComponent(singlePattern.buildProto());
    }
    return builder;
  }

  private final KeepStringPattern singlePattern;

  private KeepPackageComponentPattern(KeepStringPattern singlePattern) {
    this.singlePattern = singlePattern;
  }

  public boolean isZeroOrMore() {
    return !isSingle();
  }

  public boolean isSingle() {
    return singlePattern != null;
  }

  public boolean isExact() {
    return isSingle() && singlePattern.isExact();
  }

  public KeepStringPattern getSinglePattern() {
    assert isSingle();
    return singlePattern;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof KeepPackageComponentPattern)) {
      return false;
    }
    KeepPackageComponentPattern other = (KeepPackageComponentPattern) obj;
    return Objects.equals(singlePattern, other.singlePattern);
  }
}
