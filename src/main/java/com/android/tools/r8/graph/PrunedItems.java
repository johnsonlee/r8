// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PrunedItems {

  private final DexApplication prunedApp;
  private final Set<DexReference> additionalPinnedItems;
  private final Set<DexType> removedClasses;
  private final Set<DexField> removedFields;
  private final Set<DexMethod> removedMethods;

  private PrunedItems(
      DexApplication prunedApp,
      Set<DexReference> additionalPinnedItems,
      Set<DexType> removedClasses,
      Set<DexField> removedFields,
      Set<DexMethod> removedMethods) {
    this.prunedApp = prunedApp;
    this.additionalPinnedItems = additionalPinnedItems;
    this.removedClasses = removedClasses;
    this.removedFields = removedFields;
    this.removedMethods = removedMethods;
  }

  public static ConcurrentBuilder concurrentBuilder() {
    return new ConcurrentBuilder();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static PrunedItems empty(DexApplication application) {
    return new Builder().setPrunedApp(application).build();
  }

  public boolean isEmpty() {
    return additionalPinnedItems.isEmpty()
        && removedClasses.isEmpty()
        && removedFields.isEmpty()
        && removedMethods.isEmpty();
  }

  public boolean isRemoved(DexField field) {
    return removedFields.contains(field) || removedClasses.contains(field.getHolderType());
  }

  public boolean isRemoved(DexMethod method) {
    return removedMethods.contains(method) || removedClasses.contains(method.getHolderType());
  }

  public boolean isRemoved(DexReference reference) {
    return reference.apply(this::isRemoved, this::isRemoved, this::isRemoved);
  }

  public boolean isRemoved(DexType type) {
    return removedClasses.contains(type);
  }

  public DexApplication getPrunedApp() {
    return prunedApp;
  }

  public Set<? extends DexReference> getAdditionalPinnedItems() {
    return additionalPinnedItems;
  }

  public boolean hasRemovedClasses() {
    return !removedClasses.isEmpty();
  }

  public boolean hasRemovedFields() {
    return !removedFields.isEmpty();
  }

  public boolean hasRemovedMembers() {
    return hasRemovedFields() || hasRemovedMethods();
  }

  public boolean hasRemovedMethods() {
    return !removedMethods.isEmpty();
  }

  public Set<DexType> getRemovedClasses() {
    return removedClasses;
  }

  public Set<DexField> getRemovedFields() {
    return removedFields;
  }

  public Set<DexMethod> getRemovedMethods() {
    return removedMethods;
  }

  public static class Builder {

    private DexApplication prunedApp;

    private final Set<DexReference> additionalPinnedItems;
    private Map<DexMethod, ProgramMethod> fullyInlinedMethods;
    private Set<DexType> removedClasses;
    private final Set<DexField> removedFields;
    private Set<DexMethod> removedMethods;

    Builder() {
      additionalPinnedItems = newEmptySet();
      fullyInlinedMethods = newEmptyMap();
      removedClasses = newEmptySet();
      removedFields = newEmptySet();
      removedMethods = newEmptySet();
    }

    Builder(PrunedItems prunedItems) {
      this();
      additionalPinnedItems.addAll(prunedItems.getAdditionalPinnedItems());
      prunedApp = prunedItems.getPrunedApp();
      removedClasses.addAll(prunedItems.getRemovedClasses());
      removedFields.addAll(prunedItems.getRemovedFields());
      removedMethods.addAll(prunedItems.getRemovedMethods());
    }

    <T> Set<T> newEmptySet() {
      return Sets.newIdentityHashSet();
    }

    <S, T> Map<S, T> newEmptyMap() {
      return new IdentityHashMap<>();
    }

    public Builder setPrunedApp(DexApplication prunedApp) {
      this.prunedApp = prunedApp;
      return this;
    }

    public Builder addAdditionalPinnedItems(
        Collection<? extends DexReference> additionalPinnedItems) {
      this.additionalPinnedItems.addAll(additionalPinnedItems);
      return this;
    }

    public boolean hasFullyInlinedMethods() {
      return !fullyInlinedMethods.isEmpty();
    }

    public Builder addFullyInlinedMethod(DexMethod method, ProgramMethod singleCaller) {
      assert !fullyInlinedMethods.containsKey(method);
      fullyInlinedMethods.put(method, singleCaller);
      removedMethods.add(method);
      return this;
    }

    public void clearFullyInlinedMethods() {
      fullyInlinedMethods.clear();
    }

    public Builder addRemovedClass(DexType removedClass) {
      this.removedClasses.add(removedClass);
      return this;
    }

    public Builder addRemovedClasses(Set<DexType> removedClasses) {
      this.removedClasses.addAll(removedClasses);
      return this;
    }

    public Builder addRemovedField(DexField removedField) {
      removedFields.add(removedField);
      return this;
    }

    public Builder addRemovedFields(Collection<DexField> removedFields) {
      this.removedFields.addAll(removedFields);
      return this;
    }

    public boolean hasRemovedMethods() {
      return !removedMethods.isEmpty();
    }

    public Builder addRemovedMethod(DexMethod removedMethod) {
      removedMethods.add(removedMethod);
      return this;
    }

    public Builder addRemovedMethods(Collection<DexMethod> removedMethods) {
      this.removedMethods.addAll(removedMethods);
      return this;
    }

    public void clearRemovedMethods() {
      removedMethods.clear();
    }

    public Builder setRemovedClasses(Set<DexType> removedClasses) {
      this.removedClasses = removedClasses;
      return this;
    }

    public Builder setRemovedMethods(Set<DexMethod> removedMethods) {
      this.removedMethods = removedMethods;
      return this;
    }

    public PrunedItems build() {
      return new PrunedItems(
          prunedApp,
          additionalPinnedItems,
          removedClasses,
          removedFields,
          removedMethods);
    }
  }

  public static class ConcurrentBuilder extends Builder {

    @Override
    <T> Set<T> newEmptySet() {
      return SetUtils.newConcurrentHashSet();
    }

    @Override
    <S, T> Map<S, T> newEmptyMap() {
      return new ConcurrentHashMap<>();
    }

    @Override
    public Builder setRemovedClasses(Set<DexType> removedClasses) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Builder setRemovedMethods(Set<DexMethod> removedMethods) {
      throw new UnsupportedOperationException();
    }
  }
}
