// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import java.util.function.Function;

public abstract class OptionalPattern<T> {

  public static <T> OptionalPattern<T> absent() {
    return (OptionalPattern<T>) Absent.INSTANCE;
  }

  public static <T> OptionalPattern<T> of(T value) {
    assert value != null;
    return new Some<>(value);
  }

  public static <T> OptionalPattern<T> ofNullable(T value) {
    return value != null ? of(value) : absent();
  }

  public abstract boolean isAbsent();

  public final boolean isPresent() {
    return !isAbsent();
  }

  public T get() {
    throw new KeepEdgeException("Unexpected attempt to get absent value");
  }

  public final <S> OptionalPattern<S> map(Function<T, S> fn) {
    return mapOrDefault(fn.andThen(OptionalPattern::of), absent());
  }

  public <S> S mapOrDefault(Function<T, S> fn, S defaultValue) {
    return defaultValue;
  }

  private static final class Absent extends OptionalPattern {
    private static final Absent INSTANCE = new Absent();

    @Override
    public boolean isAbsent() {
      return true;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "<absent>";
    }
  }

  private static final class Some<T> extends OptionalPattern<T> {
    private final T value;

    public Some(T value) {
      this.value = value;
    }

    @Override
    public boolean isAbsent() {
      return false;
    }

    @Override
    public T get() {
      return value;
    }

    @Override
    public <S> S mapOrDefault(Function<T, S> fn, S defaultValue) {
      return fn.apply(value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Some)) {
        return false;
      }
      Some<?> some = (Some<?>) o;
      return value.equals(some.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public String toString() {
      return value.toString();
    }
  }
}
