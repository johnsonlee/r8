// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.shaking.KeepInfoEquivalenceNoAnnotations.ClassEquivalenceNoAnnotations;
import com.android.tools.r8.shaking.KeepInfoEquivalenceNoAnnotations.FieldEquivalenceNoAnnotations;
import com.android.tools.r8.shaking.KeepInfoEquivalenceNoAnnotations.MethodEquivalenceNoAnnotations;
import com.android.tools.r8.utils.LRUCache;
import com.google.common.base.Equivalence;
import java.util.Map;

public abstract class KeepInfoCanonicalizer {

  private static final int CACHE_SIZE = 25;

  public static KeepInfoCanonicalizer newCanonicalizer() {
    return new KeepInfoConcreteCanonicalizer();
  }

  public static KeepInfoCanonicalizer newNopCanonicalizer() {
    return new KeepInfoNopCanonicalizer();
  }

  public abstract KeepClassInfo canonicalizeKeepClassInfo(KeepClassInfo classInfo);

  public abstract KeepMethodInfo canonicalizeKeepMethodInfo(KeepMethodInfo methodInfo);

  public abstract KeepFieldInfo canonicalizeKeepFieldInfo(KeepFieldInfo fieldInfo);

  static class KeepInfoConcreteCanonicalizer extends KeepInfoCanonicalizer {

    private final ClassEquivalenceNoAnnotations classEquivalence =
        new ClassEquivalenceNoAnnotations();
    private final Map<Equivalence.Wrapper<KeepClassInfo>, Equivalence.Wrapper<KeepClassInfo>>
        keepClassInfos = new LRUCache<>(CACHE_SIZE);
    private final MethodEquivalenceNoAnnotations methodEquivalence =
        new MethodEquivalenceNoAnnotations();
    private final Map<Equivalence.Wrapper<KeepMethodInfo>, Equivalence.Wrapper<KeepMethodInfo>>
        keepMethodInfos = new LRUCache<>(CACHE_SIZE);
    private final FieldEquivalenceNoAnnotations fieldEquivalence =
        new FieldEquivalenceNoAnnotations();
    private final Map<Equivalence.Wrapper<KeepFieldInfo>, Equivalence.Wrapper<KeepFieldInfo>>
        keepFieldInfos = new LRUCache<>(CACHE_SIZE);

    private boolean hasNonTrivialKeepInfoAnnotationInfo(KeepInfo<?, ?> info) {
      return !info.internalAnnotationsInfo().isTopOrBottom()
          || !info.internalTypeAnnotationsInfo().isTopOrBottom();
    }

    @Override
    public KeepClassInfo canonicalizeKeepClassInfo(KeepClassInfo classInfo) {
      if (hasNonTrivialKeepInfoAnnotationInfo(classInfo)) {
        return classInfo;
      }
      return keepClassInfos.computeIfAbsent(classEquivalence.wrap(classInfo), w -> w).get();
    }

    @Override
    public KeepMethodInfo canonicalizeKeepMethodInfo(KeepMethodInfo methodInfo) {
      if (hasNonTrivialKeepInfoAnnotationInfo(methodInfo)
          || !methodInfo.internalParameterAnnotationsInfo().isTopOrBottom()) {
        return methodInfo;
      }
      return keepMethodInfos.computeIfAbsent(methodEquivalence.wrap(methodInfo), w -> w).get();
    }

    @Override
    public KeepFieldInfo canonicalizeKeepFieldInfo(KeepFieldInfo fieldInfo) {
      if (hasNonTrivialKeepInfoAnnotationInfo(fieldInfo)) {
        return fieldInfo;
      }
      return keepFieldInfos.computeIfAbsent(fieldEquivalence.wrap(fieldInfo), w -> w).get();
    }
  }

  static class KeepInfoNopCanonicalizer extends KeepInfoCanonicalizer {

    @Override
    public KeepClassInfo canonicalizeKeepClassInfo(KeepClassInfo classInfo) {
      return classInfo;
    }

    @Override
    public KeepMethodInfo canonicalizeKeepMethodInfo(KeepMethodInfo methodInfo) {
      return methodInfo;
    }

    @Override
    public KeepFieldInfo canonicalizeKeepFieldInfo(KeepFieldInfo fieldInfo) {
      return fieldInfo;
    }
  }
}
