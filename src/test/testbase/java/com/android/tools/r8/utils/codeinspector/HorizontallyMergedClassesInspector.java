// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.horizontalclassmerging.HorizontallyMergedClasses;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.repackaging.RepackagingLens;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HorizontallyMergedClassesInspector {

  private final DexItemFactory dexItemFactory;
  private final HorizontallyMergedClasses horizontallyMergedClasses;
  private final NonIdentityGraphLens repackagingLens;

  private final Set<ClassReference> seen = new HashSet<>();

  public HorizontallyMergedClassesInspector(
      AppView<?> appView, HorizontallyMergedClasses horizontallyMergedClasses) {
    this.dexItemFactory = appView.dexItemFactory();
    this.horizontallyMergedClasses = horizontallyMergedClasses;
    this.repackagingLens =
        appView.graphLens().isNonIdentityLens()
            ? appView
                .graphLens()
                .asNonIdentityLens()
                .find(lens -> lens.isRepackagingLens() && !((RepackagingLens) lens).isEmpty())
            : null;
  }

  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    horizontallyMergedClasses.forEachMergeGroup(consumer);
  }

  public Set<Set<DexType>> getMergeGroups() {
    Set<Set<DexType>> mergeGroups = Sets.newLinkedHashSet();
    forEachMergeGroup(
        (sources, target) -> {
          Set<DexType> mergeGroup = SetUtils.newIdentityHashSet(sources);
          mergeGroup.add(target);
          mergeGroups.add(mergeGroup);
        });
    return mergeGroups;
  }

  public Set<DexType> getSources() {
    return horizontallyMergedClasses.getSources();
  }

  public Set<DexType> getNonRepackagedSourcesForNonRepackagedClass(DexType clazz) {
    DexType repackagedClass = getRepackagedType(clazz);
    Set<DexType> repackagedSources = horizontallyMergedClasses.getSourcesFor(repackagedClass);
    return SetUtils.mapIdentityHashSet(repackagedSources, this::getNonRepackagedType);
  }

  public ClassReference getTarget(ClassReference classReference) {
    DexType sourceType = ClassReferenceUtils.toDexType(classReference, dexItemFactory);
    DexType targetType = getRepackagedTargetForNonRepackagedClass(sourceType);
    return targetType.asClassReference();
  }

  public DexType getRepackagedTargetForNonRepackagedClass(DexType clazz) {
    DexType repackagedClass = getRepackagedType(clazz);
    return horizontallyMergedClasses.getMergeTargetOrDefault(repackagedClass, repackagedClass);
  }

  public Set<DexType> getRepackagedTargets() {
    return horizontallyMergedClasses.getTargets();
  }

  public boolean isNonRepackagedClassMergeSource(DexType clazz) {
    return horizontallyMergedClasses.isMergeSource(getRepackagedType(clazz));
  }

  public boolean isNonRepackagedClassMergeTarget(DexType clazz) {
    return horizontallyMergedClasses.isMergeTarget(getRepackagedType(clazz));
  }

  private DexType getRepackagedType(DexType type) {
    return repackagingLens != null ? repackagingLens.getNextType(type) : type;
  }

  private DexType getNonRepackagedType(DexType type) {
    return repackagingLens != null ? repackagingLens.getPreviousClassType(type) : type;
  }

  private void markNonRepackagedClassesAsSeen(DexType... nonRepackagedClasses) {
    markNonRepackagedClassesAsSeen(Arrays.asList(nonRepackagedClasses));
  }

  private void markNonRepackagedClassesAsSeen(Iterable<DexType> nonRepackagedClasses) {
    for (DexType nonRepackagedClass : nonRepackagedClasses) {
      seen.add(getRepackagedType(nonRepackagedClass).asClassReference());
    }
  }

  public HorizontallyMergedClassesInspector applyIf(
      boolean condition, ThrowableConsumer<HorizontallyMergedClassesInspector> consumer) {
    return applyIf(condition, consumer, ThrowableConsumer.empty());
  }

  public HorizontallyMergedClassesInspector applyIf(
      boolean condition,
      ThrowableConsumer<HorizontallyMergedClassesInspector> thenConsumer,
      ThrowableConsumer<HorizontallyMergedClassesInspector> elseConsumer) {
    if (condition) {
      thenConsumer.acceptWithRuntimeException(this);
    } else {
      elseConsumer.acceptWithRuntimeException(this);
    }
    return this;
  }

  public HorizontallyMergedClassesInspector applyIf(
      boolean condition,
      ThrowableConsumer<HorizontallyMergedClassesInspector> thenConsumer,
      boolean elseIfCondition,
      ThrowableConsumer<HorizontallyMergedClassesInspector> elseIfThenConsumer) {
    if (condition) {
      thenConsumer.acceptWithRuntimeException(this);
    } else if (elseIfCondition) {
      elseIfThenConsumer.acceptWithRuntimeException(this);
    }
    return this;
  }

  public HorizontallyMergedClassesInspector assertMergedInto(Class<?> from, Class<?> target) {
    return assertMergedInto(toDexType(from), toDexType(target));
  }

  public HorizontallyMergedClassesInspector assertMergedInto(
      ClassReference from, ClassReference target) {
    return assertMergedInto(toDexType(from), toDexType(target));
  }

  public HorizontallyMergedClassesInspector assertMergedInto(DexType from, DexType target) {
    assertEquals(getRepackagedTargetForNonRepackagedClass(from), getRepackagedType(target));
    markNonRepackagedClassesAsSeen(from, target);
    return this;
  }

  public HorizontallyMergedClassesInspector assertNoClassesMerged() {
    if (!horizontallyMergedClasses.getSources().isEmpty()) {
      DexType source = horizontallyMergedClasses.getSources().iterator().next();
      fail(
          "Expected no classes to be merged, got: "
              + source.getTypeName()
              + " -> "
              + getRepackagedTargetForNonRepackagedClass(source).getTypeName());
    }
    return this;
  }

  public HorizontallyMergedClassesInspector assertNoOtherClassesMerged() {
    horizontallyMergedClasses.forEachMergeGroup(
        (sources, target) -> {
          for (DexType source : sources) {
            assertTrue(source.getTypeName(), seen.contains(source.asClassReference()));
          }
          assertTrue(target.getTypeName(), seen.contains(target.asClassReference()));
        });
    return this;
  }

  public HorizontallyMergedClassesInspector assertClassesNotMerged(Class<?>... classes) {
    return assertClassesNotMerged(Arrays.asList(classes));
  }

  public HorizontallyMergedClassesInspector assertClassesNotMerged(Collection<Class<?>> classes) {
    return assertTypesNotMerged(classes.stream().map(this::toDexType).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertClassReferencesNotMerged(
      ClassReference... classReferences) {
    return assertClassReferencesNotMerged(Arrays.asList(classReferences));
  }

  public HorizontallyMergedClassesInspector assertClassReferencesNotMerged(
      Collection<ClassReference> classReferences) {
    return assertTypesNotMerged(
        classReferences.stream().map(this::toDexType).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertTypesNotMerged(DexType... types) {
    return assertTypesNotMerged(Arrays.asList(types));
  }

  public HorizontallyMergedClassesInspector assertTypesNotMerged(Collection<DexType> types) {
    for (DexType type : types) {
      assertTrue(type.isClassType());
      assertFalse(horizontallyMergedClasses.isMergeSourceOrTarget(type));
    }
    seen.addAll(types.stream().map(DexType::asClassReference).collect(Collectors.toList()));
    return this;
  }

  public HorizontallyMergedClassesInspector assertIsCompleteMergeGroup(Class<?>... classes) {
    return assertIsCompleteMergeGroup(
        Stream.of(classes).map(Reference::classFromClass).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertIsCompleteMergeGroup(
      ClassReference... classReferences) {
    return assertIsCompleteMergeGroup(Arrays.asList(classReferences));
  }

  public HorizontallyMergedClassesInspector assertIsCompleteMergeGroup(String... typeNames) {
    return assertIsCompleteMergeGroup(
        Stream.of(typeNames).map(Reference::classFromTypeName).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertIsCompleteMergeGroup(
      Collection<ClassReference> classReferences) {
    assertFalse(classReferences.isEmpty());
    List<DexType> types =
        classReferences.stream().map(this::toDexType).collect(Collectors.toList());
    DexType uniqueTarget = null;
    for (DexType type : types) {
      if (isNonRepackagedClassMergeTarget(type)) {
        if (uniqueTarget == null) {
          uniqueTarget = type;
        } else {
          fail(
              "Expected a single merge target, but found "
                  + type.getTypeName()
                  + " and "
                  + uniqueTarget.getTypeName());
        }
      }
    }
    if (uniqueTarget == null) {
      for (DexType type : types) {
        if (isNonRepackagedClassMergeSource(type)) {
          fail(
              "Expected merge target "
                  + getRepackagedTargetForNonRepackagedClass(type).getTypeName()
                  + " to be in merge group");
        }
      }
      fail("Expected to find a merge target, but none found");
    }
    Set<DexType> sources = getNonRepackagedSourcesForNonRepackagedClass(uniqueTarget);
    assertEquals(
        "Expected to find "
            + (classReferences.size() - 1)
            + " source(s) for merge target "
            + uniqueTarget.getTypeName()
            + ", but only found: "
            + StringUtils.join(", ", sources, DexType::getTypeName),
        classReferences.size() - 1,
        sources.size());
    assertTrue(types.containsAll(sources));
    markNonRepackagedClassesAsSeen(types);
    return this;
  }

  private DexType toDexType(Class<?> clazz) {
    return TestBase.toDexType(clazz, dexItemFactory);
  }

  private DexType toDexType(ClassReference classReference) {
    return TestBase.toDexType(classReference, dexItemFactory);
  }
}
