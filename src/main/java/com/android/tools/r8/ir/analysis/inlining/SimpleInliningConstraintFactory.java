// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNull;

import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.utils.ArrayUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SimpleInliningConstraintFactory {

  // Immutable argument constraints for low argument indices to avoid overhead of ConcurrentHashMap.
  private final ConstSimpleInliningConstraint[] lowConstConstraints =
      ArrayUtils.initialize(
          new ConstSimpleInliningConstraint[5], i -> ConstSimpleInliningConstraint.create(i, this));
  private final EqualToBooleanSimpleInliningConstraint[] lowEqualToFalseConstraints =
      ArrayUtils.initialize(
          new EqualToBooleanSimpleInliningConstraint[5],
          i -> EqualToBooleanSimpleInliningConstraint.create(i, false, this));
  private final EqualToBooleanSimpleInliningConstraint[] lowEqualToTrueConstraints =
      ArrayUtils.initialize(
          new EqualToBooleanSimpleInliningConstraint[5],
          i -> EqualToBooleanSimpleInliningConstraint.create(i, true, this));
  private final NullSimpleInliningConstraint[] lowNotEqualToNullConstraints =
      ArrayUtils.initialize(
          new NullSimpleInliningConstraint[5],
          i -> NullSimpleInliningConstraint.create(i, definitelyNotNull(), this));
  private final NullSimpleInliningConstraint[] lowEqualToNullConstraints =
      ArrayUtils.initialize(
          new NullSimpleInliningConstraint[5],
          i -> NullSimpleInliningConstraint.create(i, definitelyNull(), this));

  // Argument constraints for high argument indices.
  private final Map<Integer, ConstSimpleInliningConstraint> highConstConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, EqualToBooleanSimpleInliningConstraint> highEqualToFalseConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, EqualToBooleanSimpleInliningConstraint> highEqualToTrueConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, NullSimpleInliningConstraint> highNotEqualToNullConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, NullSimpleInliningConstraint> highEqualToNullConstraints =
      new ConcurrentHashMap<>();

  public ConstSimpleInliningConstraint createConstConstraint(int argumentIndex) {
    return createArgumentConstraint(
        argumentIndex,
        lowConstConstraints,
        highConstConstraints,
        () -> ConstSimpleInliningConstraint.create(argumentIndex, this));
  }

  public EqualToBooleanSimpleInliningConstraint createEqualToFalseConstraint(int argumentIndex) {
    return createEqualToBooleanConstraint(argumentIndex, false);
  }

  public EqualToBooleanSimpleInliningConstraint createEqualToTrueConstraint(int argumentIndex) {
    return createEqualToBooleanConstraint(argumentIndex, true);
  }

  public EqualToBooleanSimpleInliningConstraint createEqualToBooleanConstraint(
      int argumentIndex, boolean value) {
    return createArgumentConstraint(
        argumentIndex,
        value ? lowEqualToTrueConstraints : lowEqualToFalseConstraints,
        value ? highEqualToTrueConstraints : highEqualToFalseConstraints,
        () -> EqualToBooleanSimpleInliningConstraint.create(argumentIndex, value, this));
  }

  public NullSimpleInliningConstraint createEqualToNullConstraint(int argumentIndex) {
    return createNullConstraint(argumentIndex, definitelyNull());
  }

  public NullSimpleInliningConstraint createNotEqualToNullConstraint(int argumentIndex) {
    return createNullConstraint(argumentIndex, definitelyNotNull());
  }

  public NullSimpleInliningConstraint createNullConstraint(
      int argumentIndex, Nullability nullability) {
    return createArgumentConstraint(
        argumentIndex,
        nullability.isDefinitelyNull() ? lowEqualToNullConstraints : lowNotEqualToNullConstraints,
        nullability.isDefinitelyNull() ? highEqualToNullConstraints : highNotEqualToNullConstraints,
        () -> NullSimpleInliningConstraint.create(argumentIndex, nullability, this));
  }

  public NotEqualToNumberSimpleInliningConstraint createNotEqualToNumberConstraint(
      int argumentIndex, long rawValue) {
    return NotEqualToNumberSimpleInliningConstraint.create(argumentIndex, rawValue, this);
  }

  public EqualToNumberSimpleInliningConstraint createEqualToNumberConstraint(
      int argumentIndex, long rawValue) {
    return EqualToNumberSimpleInliningConstraint.create(argumentIndex, rawValue, this);
  }

  private <T extends SimpleInliningArgumentConstraint> T createArgumentConstraint(
      int argumentIndex, T[] lowConstraints, Map<Integer, T> highConstraints, Supplier<T> fn) {
    return argumentIndex < lowConstraints.length
        ? lowConstraints[argumentIndex]
        : highConstraints.computeIfAbsent(argumentIndex, key -> fn.get());
  }
}
