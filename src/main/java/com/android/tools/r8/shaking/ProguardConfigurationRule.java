// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder.satisfyAccessFlag;
import static com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder.satisfyAnnotation;
import static com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder.satisfyClassType;
import static com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder.satisfyNonSyntheticClass;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.ProguardWildcard.BackReference;
import com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class ProguardConfigurationRule extends ProguardClassSpecification {

  private boolean used = false;
  // TODO(b/164019179): Since we are using the rule language for tracing main dex we can end up in
  //  a situation where the references to types are dead.
  private boolean canReferenceDeadTypes = false;
  private OptionalBool trivialAllClassMatch = OptionalBool.unknown();

  ProguardConfigurationRule(
      Origin origin,
      Position position,
      String source,
      List<ProguardTypeMatcher> classAnnotations,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags,
      boolean classTypeNegated,
      ProguardClassType classType,
      ProguardClassNameList classNames,
      List<ProguardTypeMatcher> inheritanceAnnotations,
      ProguardTypeMatcher inheritanceClassName,
      boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules) {
    super(
        origin,
        position,
        source,
        classAnnotations,
        classAccessFlags,
        negatedClassAccessFlags,
        classTypeNegated,
        classType,
        classNames,
        inheritanceAnnotations,
        inheritanceClassName,
        inheritanceIsExtends,
        memberRules);
  }

  public boolean isTrivialAllClassMatch() {
    if (trivialAllClassMatch.isUnknown()) {
      trivialAllClassMatch = OptionalBool.of(computeIsTrivialAllClassMatch());
    }
    assert trivialAllClassMatch.isTrue() == computeIsTrivialAllClassMatch();
    return trivialAllClassMatch.isTrue();
  }

  public boolean isTrivialAllClassMatchWithNoMembersRules() {
    return isTrivialAllClassMatch() && !hasMemberRules();
  }

  public boolean computeIsTrivialAllClassMatch() {
    return getClassNames().isMatchAnyClassPattern()
        && getClassAnnotations().isEmpty()
        && getClassAccessFlags().isDefaultFlags()
        && getNegatedClassAccessFlags().isDefaultFlags()
        && !getClassTypeNegated()
        && getClassType() == ProguardClassType.CLASS
        && getInheritanceAnnotations().isEmpty()
        && !hasInheritanceClassName();
  }

  public boolean isUsed() {
    return used;
  }

  public void markAsUsed() {
    used = true;
  }

  public boolean isMaximumRemovedAndroidLogLevelRule() {
    return false;
  }

  public MaximumRemovedAndroidLogLevelRule asMaximumRemovedAndroidLogLevelRule() {
    return null;
  }

  public boolean isProguardCheckDiscardRule() {
    return false;
  }

  public ProguardCheckDiscardRule asProguardCheckDiscardRule() {
    return null;
  }

  public boolean isProguardKeepRule() {
    return false;
  }

  public ProguardKeepRule asProguardKeepRule() {
    return null;
  }

  public boolean isProguardIfRule() {
    return false;
  }

  public ProguardIfRule asProguardIfRule() {
    return null;
  }

  public boolean isClassInlineRule() {
    return false;
  }

  public ClassInlineRule asClassInlineRule() {
    return null;
  }

  public boolean isReprocessClassInitializerRule() {
    return false;
  }

  public ReprocessClassInitializerRule asReprocessClassInitializerRule() {
    return null;
  }

  public boolean isReprocessMethodRule() {
    return false;
  }

  public ReprocessMethodRule asReprocessMethodRule() {
    return null;
  }

  public void canReferenceDeadTypes() {
    this.canReferenceDeadTypes = true;
  }

  void forEachRelevantCandidate(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateAppSubtypingInfo subtypingInfo,
      Iterable<DexProgramClass> defaultValue,
      Predicate<DexProgramClass> isRelevant,
      Consumer<DexClass> consumer) {
    if (getClassNames().hasSpecificTypes()) {
      for (DexType type : getClassNames().getSpecificTypes()) {
        DexProgramClass clazz =
            asProgramClassOrNull(
                canReferenceDeadTypes
                    ? appView.appInfo().definitionForWithoutExistenceAssert(type)
                    : appView.definitionFor(type));
        if (clazz != null && isRelevant.test(clazz)) {
          consumer.accept(clazz);
        }
      }
    } else if (hasInheritanceClassName() && getInheritanceClassName().hasSpecificType()) {
      DexType type = getInheritanceClassName().getSpecificType();
      DexClass clazz = appView.definitionFor(type);
      if (clazz != null) {
        subtypingInfo.forEachTransitiveProgramSubclassMatching(clazz, isRelevant, consumer);
        if (appView.getVerticallyMergedClasses() != null
            && appView.getVerticallyMergedClasses().hasBeenMergedIntoSubtype(type)) {
          DexType targetType = appView.getVerticallyMergedClasses().getTargetFor(type);
          DexProgramClass targetClass = asProgramClassOrNull(appView.definitionFor(targetType));
          assert targetClass != null;
          if (isRelevant.test(targetClass)) {
            consumer.accept(targetClass);
          }
        }
      }
    } else {
      defaultValue.forEach(consumer);
    }
  }

  abstract String typeString();

  String typeSuffix() {
    return null;
  }

  String modifierString() {
    return null;
  }

  public boolean isApplicableToClasspathClasses() {
    return false;
  }

  public boolean isApplicableToLibraryClasses() {
    return false;
  }

  public final boolean isOnlyApplicableToProgramClasses() {
    return !isApplicableToClasspathClasses() && !isApplicableToLibraryClasses();
  }

  protected boolean hasBackReferences() {
    return !Iterables.isEmpty(getBackReferences());
  }

  public Iterable<BackReference> getBackReferences() {
    return getWildcardsThatMatches(ProguardWildcard::isBackReference);
  }

  protected final Iterable<ProguardWildcard> getWildcards() {
    return getWildcardsThatMatches(alwaysTrue());
  }

  protected <T extends ProguardWildcard> Iterable<T> getWildcardsThatMatches(
      Predicate<? super ProguardWildcard> predicate) {
    return Iterables.concat(
        ProguardTypeMatcher.getWildcardsThatMatchesOrEmpty(getClassAnnotations(), predicate),
        ProguardClassNameList.getWildcardsThatMatchesOrEmpty(getClassNames(), predicate),
        ProguardTypeMatcher.getWildcardsThatMatchesOrEmpty(getInheritanceAnnotations(), predicate),
        ProguardTypeMatcher.getWildcardsThatMatchesOrEmpty(getInheritanceClassName(), predicate),
        hasMemberRules()
            ? IterableUtils.flatMap(
                getMemberRules(), memberRule -> memberRule.getWildcardsThatMatches(predicate))
            : IterableUtils.empty());
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardConfigurationRule)) {
      return false;
    }
    ProguardConfigurationRule that = (ProguardConfigurationRule) o;
    if (used != that.used) {
      return false;
    }
    if (!Objects.equals(typeString(), that.typeString())) {
      return false;
    }
    if (!Objects.equals(modifierString(), that.modifierString())) {
      return false;
    }
    return super.equals(that);
  }

  @Override
  public int hashCode() {
    int result = 3 * typeString().hashCode();
    result = 3 * result + (used ? 1 : 0);
    String modifier = modifierString();
    result = 3 * result + (modifier != null ? modifier.hashCode() : 0);
    return result + super.hashCode();
  }

  @Override
  protected StringBuilder append(StringBuilder builder) {
    builder.append("-");
    builder.append(typeString());
    StringUtils.appendNonEmpty(builder, ",", modifierString(), null);
    StringUtils.appendNonEmpty(builder, " ", typeSuffix(), null);
    builder.append(' ');
    super.append(builder);
    return builder;
  }

  public boolean testClassCondition(
      DexClass clazz,
      AppView<?> appView,
      Consumer<AnnotationMatchResult> annotationMatchResultConsumer) {
    if (!satisfyNonSyntheticClass(clazz, appView)) {
      return false;
    }
    if (!satisfyClassType(this, clazz)) {
      return false;
    }
    if (!satisfyAccessFlag(this, clazz)) {
      return false;
    }
    AnnotationMatchResult annotationMatchResult = satisfyAnnotation(this, clazz);
    if (annotationMatchResult == null) {
      return false;
    }
    annotationMatchResultConsumer.accept(annotationMatchResult);
    // In principle it should make a difference whether the user specified in a class
    // spec that a class either extends or implements another type. However, proguard
    // seems not to care, so users have started to use this inconsistently. We are thus
    // inconsistent, as well, but tell them.
    // TODO(herhut): One day make this do what it says.
    if (hasInheritanceClassName()
        && !satisfyInheritanceRule(clazz, appView, annotationMatchResultConsumer)) {
      return false;
    }
    return getClassNames().matches(clazz.type);
  }

  public boolean satisfyInheritanceRule(
      DexClass clazz,
      AppView<?> appView,
      Consumer<AnnotationMatchResult> annotationMatchResultConsumer) {
    return satisfyExtendsRule(clazz, appView, annotationMatchResultConsumer)
        || satisfyImplementsRule(clazz, appView, annotationMatchResultConsumer);
  }

  private boolean satisfyExtendsRule(
      DexClass clazz,
      AppView<?> appView,
      Consumer<AnnotationMatchResult> annotationMatchResultConsumer) {
    if (anySuperTypeMatchesExtendsRule(clazz.superType, appView, annotationMatchResultConsumer)) {
      return true;
    }
    // It is possible that this class used to inherit from another class X, but no longer does it,
    // because X has been merged into `clazz`.
    return anySourceMatchesInheritanceRuleDirectly(clazz, false, appView);
  }

  private boolean anySuperTypeMatchesExtendsRule(
      DexType type,
      AppView<?> appView,
      Consumer<AnnotationMatchResult> annotationMatchResultConsumer) {
    while (type != null) {
      DexClass clazz = appView.definitionFor(type);
      if (clazz == null) {
        // TODO(herhut): Warn about broken supertype chain?
        return false;
      }
      // TODO(b/110141157): Should the vertical class merger move annotations from the source to
      //  the target class? If so, it is sufficient only to apply the annotation-matcher to the
      //  annotations of `class`.
      if (getInheritanceClassName().matches(clazz.type, appView)) {
        AnnotationMatchResult annotationMatchResult =
            RootSetBuilder.containsAllAnnotations(getInheritanceAnnotations(), clazz);
        if (annotationMatchResult != null) {
          annotationMatchResultConsumer.accept(annotationMatchResult);
          return true;
        }
      }
      type = clazz.superType;
    }
    return false;
  }

  private boolean satisfyImplementsRule(
      DexClass clazz,
      AppView<?> appView,
      Consumer<AnnotationMatchResult> annotationMatchResultConsumer) {
    if (anyImplementedInterfaceMatchesImplementsRule(
        clazz, appView, annotationMatchResultConsumer)) {
      return true;
    }
    // It is possible that this class used to implement an interface I, but no longer does it,
    // because I has been merged into `clazz`.
    return anySourceMatchesInheritanceRuleDirectly(clazz, true, appView);
  }

  private boolean anyImplementedInterfaceMatchesImplementsRule(
      DexClass clazz,
      AppView<?> appView,
      Consumer<AnnotationMatchResult> annotationMatchResultConsumer) {
    // TODO(herhut): Maybe it would be better to do this breadth first.
    for (DexType iface : clazz.getInterfaces()) {
      DexClass ifaceClass = appView.definitionFor(iface);
      if (ifaceClass == null) {
        // TODO(herhut): Warn about broken supertype chain?
        continue;
      }
      // TODO(b/110141157): Should the vertical class merger move annotations from the source to
      // the target class? If so, it is sufficient only to apply the annotation-matcher to the
      // annotations of `ifaceClass`.
      if (getInheritanceClassName().matches(iface, appView)) {
        AnnotationMatchResult annotationMatchResult =
            RootSetBuilder.containsAllAnnotations(getInheritanceAnnotations(), ifaceClass);
        if (annotationMatchResult != null) {
          annotationMatchResultConsumer.accept(annotationMatchResult);
          return true;
        }
      }
      if (anyImplementedInterfaceMatchesImplementsRule(
          ifaceClass, appView, annotationMatchResultConsumer)) {
        return true;
      }
    }
    if (!clazz.hasSuperType()) {
      return false;
    }
    DexClass superClass = appView.definitionFor(clazz.getSuperType());
    return superClass != null
        && anyImplementedInterfaceMatchesImplementsRule(
            superClass, appView, annotationMatchResultConsumer);
  }

  private boolean anySourceMatchesInheritanceRuleDirectly(
      DexClass clazz, boolean isInterface, AppView<?> appView) {
    // TODO(b/110141157): Figure out what to do with annotations. Should the annotations of
    // the DexClass corresponding to `sourceType` satisfy the `annotation`-matcher?
    return appView.getVerticallyMergedClasses() != null
        && appView.getVerticallyMergedClasses().getSourcesFor(clazz.type).stream()
            .filter(
                sourceType ->
                    appView.definitionFor(sourceType).accessFlags.isInterface() == isInterface)
            .anyMatch(getInheritanceClassName()::matches);
  }
}
