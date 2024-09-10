// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Iterables;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public abstract class ProguardConfigurationRule extends ProguardClassSpecification {

  private boolean used = false;
  // TODO(b/164019179): Since we are using the rule language for tracing main dex we can end up in
  //  a situation where the references to types are dead.
  private boolean canReferenceDeadTypes = false;

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

  public boolean isTrivalAllClassMatch() {
    BooleanBox booleanBox = new BooleanBox(true);
    getClassNames()
        .forEachTypeMatcher(
            unused -> booleanBox.set(false),
            proguardTypeMatcher -> !proguardTypeMatcher.isMatchAnyClassPattern());
    return booleanBox.get()
        && getClassAnnotations().isEmpty()
        && getClassAccessFlags().isDefaultFlags()
        && getNegatedClassAccessFlags().isDefaultFlags()
        && !getClassTypeNegated()
        && getClassType() == ProguardClassType.CLASS
        && getInheritanceAnnotations().isEmpty()
        && getInheritanceClassName() == null
        && getMemberRules().isEmpty();
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

  Iterable<DexProgramClass> relevantCandidatesForRule(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      SubtypingInfo subtypingInfo,
      Iterable<DexProgramClass> defaultValue,
      Predicate<DexProgramClass> isRelevant) {
    Iterable<DexType> specificTypes;
    if (getClassNames().hasSpecificTypes()) {
      specificTypes = getClassNames().getSpecificTypes();
    } else if (hasInheritanceClassName() && getInheritanceClassName().hasSpecificType()) {
      DexType type = getInheritanceClassName().getSpecificType();
      if (appView.getVerticallyMergedClasses() != null
          && appView.getVerticallyMergedClasses().hasBeenMergedIntoSubtype(type)) {
        DexType target = appView.getVerticallyMergedClasses().getTargetFor(type);
        DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(target));
        assert clazz != null;
        specificTypes = IterableUtils.append(subtypingInfo.subtypes(type), clazz.getType());
      } else {
        specificTypes = subtypingInfo.subtypes(type);
      }
    } else {
      return defaultValue;
    }
    assert specificTypes != null;
    return DexProgramClass.asProgramClasses(
        specificTypes,
        new DexDefinitionSupplier() {
          @Override
          public ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(
              DexType type) {
            throw new Unreachable("Add support for multiple definitions with rule evaluation");
          }

          @Override
          public DexProgramClass definitionFor(DexType type) {
            DexProgramClass result =
                asProgramClassOrNull(
                    canReferenceDeadTypes
                        ? appView.appInfo().definitionForWithoutExistenceAssert(type)
                        : appView.definitionFor(type));
            if (result != null && isRelevant.test(result)) {
              return result;
            }
            return null;
          }

          @Override
          public DexItemFactory dexItemFactory() {
            return appView.dexItemFactory();
          }
        });
  }

  abstract String typeString();

  String typeSuffix() {
    return null;
  }

  String modifierString() {
    return null;
  }

  public boolean applyToNonProgramClasses() {
    return false;
  }

  protected Iterable<ProguardWildcard> getWildcards() {
    List<ProguardMemberRule> memberRules = getMemberRules();
    return Iterables.concat(
        ProguardTypeMatcher.getWildcardsOrEmpty(getClassAnnotations()),
        ProguardClassNameList.getWildcardsOrEmpty(getClassNames()),
        ProguardTypeMatcher.getWildcardsOrEmpty(getInheritanceAnnotations()),
        ProguardTypeMatcher.getWildcardsOrEmpty(getInheritanceClassName()),
        memberRules == null
            ? Collections::emptyIterator
            : IterableUtils.flatMap(memberRules, ProguardMemberRule::getWildcards));
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
}
