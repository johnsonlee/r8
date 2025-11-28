// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.ObjectUtils;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class EnqueuerDeferredAnnotationTracing {

  private final Enqueuer enqueuer;

  /**
   * A map from annotation classes to annotations that need to be processed should the classes ever
   * become live.
   *
   * <p>Concurrent instance since the concurrent processing of deferred annotations may update this.
   */
  private final Map<DexProgramClass, Set<DeferredAnnotation>> deferredAnnotationsByAnnotationClass =
      new IdentityHashMap<>();

  /**
   * A map from annotated items to the annotations that need to be processed if/when the keep info
   * of the annotated item changes.
   *
   * <p>Concurrent instance since the concurrent processing of deferred annotations may update this.
   */
  private final Map<DexDefinition, Set<DeferredAnnotation>> deferredAnnotationsByAnnotatedItem =
      new IdentityHashMap<>();

  EnqueuerDeferredAnnotationTracing(Enqueuer enqueuer) {
    this.enqueuer = enqueuer;
  }

  public void enqueueAnnotationForProcessingWhenAnnotationTypeBecomesLive(
      ProgramDefinition annotatedItem,
      DexAnnotation annotation,
      DexProgramClass annotationClass,
      AnnotatedKind kind) {
    assert annotationClass.isAnnotation();
    deferredAnnotationsByAnnotationClass
        .computeIfAbsent(annotationClass, ignoreKey(HashSet::new))
        .add(new DeferredAnnotation(annotatedItem, annotation, annotationClass, kind));
  }

  public void enqueueAnnotationForProcessingWhenKeepInfoChanges(
      ProgramDefinition annotatedItem,
      DexAnnotation annotation,
      DexClass annotationClass,
      AnnotatedKind kind) {
    deferredAnnotationsByAnnotatedItem
        .computeIfAbsent(annotatedItem.getDefinition(), ignoreKey(Sets::newHashSet))
        .add(new DeferredAnnotation(annotatedItem, annotation, annotationClass, kind));
  }

  // Called by the Enqueuer when an item has its keep info changed.
  public void notifyKeepInfoChanged(ProgramDefinition definition) {
    processDeferredAnnotations(definition.getDefinition(), deferredAnnotationsByAnnotatedItem);
  }

  // Called by the Enqueuer when an annotation class becomes live.
  public void notifyNewlyLiveAnnotation(DexProgramClass newlyLiveAnnotationClass) {
    assert newlyLiveAnnotationClass.isAnnotation();
    processDeferredAnnotations(newlyLiveAnnotationClass, deferredAnnotationsByAnnotationClass);
  }

  private <T> void processDeferredAnnotations(
      T key, Map<T, Set<DeferredAnnotation>> deferredAnnotations) {
    Set<DeferredAnnotation> annotations =
        MapUtils.removeOrDefault(deferredAnnotations, key, Collections.emptySet());
    for (var annotation : annotations) {
      enqueuer.processAnnotation(
          annotation.annotatedItem,
          annotation.annotation,
          annotation.kind,
          annotation.annotationClass);
    }
  }

  private static class DeferredAnnotation {

    private final ProgramDefinition annotatedItem;
    private final DexAnnotation annotation;
    private final DexClass annotationClass;
    private final AnnotatedKind kind;

    DeferredAnnotation(
        ProgramDefinition annotatedItem,
        DexAnnotation annotation,
        DexClass annotationClass,
        AnnotatedKind kind) {
      this.annotatedItem = annotatedItem;
      this.annotation = annotation;
      this.annotationClass = annotationClass;
      this.kind = kind;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof DeferredAnnotation)) {
        return false;
      }
      DeferredAnnotation other = (DeferredAnnotation) obj;
      if (ObjectUtils.identical(annotation, other.annotation)
          && annotatedItem.getDefinition() == other.annotatedItem.getDefinition()
          && kind == other.kind) {
        assert annotationClass == other.annotationClass;
        return true;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return (31 * (31 * (31 + System.identityHashCode(annotation)))
              + annotatedItem.getDefinition().hashCode())
          + kind.hashCode();
    }
  }
}
