// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrules;

import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardIfRule;
import com.android.tools.r8.shaking.ProguardIfRulePreconditionMatch;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.shaking.ProguardTypeMatcher.MatchSpecificTypes;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MaterializedSubsequentRulesOptimizer {

  public static List<Pair<ProguardIfRulePreconditionMatch, ProguardKeepRule>> optimize(
      ProguardIfRule ifRule,
      List<Pair<ProguardIfRulePreconditionMatch, ProguardKeepRule>> materializedSubsequentRules) {
    if (materializedSubsequentRules.size() <= 1) {
      // Nothing to optimize.
      return materializedSubsequentRules;
    }
    if (IterableUtils.hasSize(ifRule.getSubsequentRule().getBackReferences(), 1)) {
      return optimizeMaterializedSubsequentRulesWithSingleBackReference(
          ifRule, materializedSubsequentRules);
    }
    return materializedSubsequentRules;
  }

  private static List<Pair<ProguardIfRulePreconditionMatch, ProguardKeepRule>>
      optimizeMaterializedSubsequentRulesWithSingleBackReference(
          ProguardIfRule ifRule,
          List<Pair<ProguardIfRulePreconditionMatch, ProguardKeepRule>>
              materializedSubsequentRules) {
    ProguardKeepRule representativeKeepRule =
        IterableUtils.first(materializedSubsequentRules).getSecond();

    // Back references may occur in the following five elements only.
    List<ProguardTypeMatcher> classAnnotations = representativeKeepRule.getClassAnnotations();
    ProguardClassNameList classNames = representativeKeepRule.getClassNames();
    List<ProguardTypeMatcher> inheritanceAnnotations =
        representativeKeepRule.getInheritanceAnnotations();
    ProguardTypeMatcher inheritanceClassName = representativeKeepRule.getInheritanceClassName();
    int memberRuleWithBackReferenceIndex =
        Iterables.indexOf(
            ifRule.getSubsequentRule().getMemberRules(), ProguardMemberRule::hasBackReference);
    ProguardMemberRule replacementMemberRule =
        memberRuleWithBackReferenceIndex >= 0
            ? representativeKeepRule.getMemberRule(memberRuleWithBackReferenceIndex)
            : null;

    // Iterate over the other keep rules and merge.
    for (ProguardKeepRule keepRule :
        Iterables.transform(Iterables.skip(materializedSubsequentRules, 1), Pair::getSecond)) {
      assert ObjectUtils.identical(keepRule.getOrigin(), representativeKeepRule.getOrigin());
      assert ObjectUtils.identical(keepRule.getPosition(), representativeKeepRule.getPosition());
      assert ObjectUtils.identical(keepRule.getSource(), representativeKeepRule.getSource());
      assert ObjectUtils.identical(
          keepRule.getClassAccessFlags(), representativeKeepRule.getClassAccessFlags());
      assert ObjectUtils.identical(
          keepRule.getNegatedClassAccessFlags(),
          representativeKeepRule.getNegatedClassAccessFlags());
      assert ObjectUtils.identical(
          keepRule.getClassTypeNegated(), representativeKeepRule.getClassTypeNegated());
      assert ObjectUtils.identical(keepRule.getClassType(), representativeKeepRule.getClassType());
      assert ObjectUtils.identical(
          keepRule.getInheritanceIsExtends(), representativeKeepRule.getInheritanceIsExtends());
      assert ObjectUtils.identical(keepRule.getType(), representativeKeepRule.getType());
      assert ObjectUtils.identical(keepRule.getModifiers(), representativeKeepRule.getModifiers());
      if (keepRule.getClassAnnotations() != classAnnotations
          || !keepRule.getClassNames().equals(classNames)
          || keepRule.getInheritanceAnnotations() != inheritanceAnnotations
          || !Objects.equals(keepRule.getInheritanceClassName(), inheritanceClassName)) {
        // Not implemented.
        return materializedSubsequentRules;
      }
      if (replacementMemberRule != null) {
        ProguardMemberRule otherMemberRule =
            keepRule.getMemberRule(memberRuleWithBackReferenceIndex);
        replacementMemberRule =
            optimizeMaterializedMemberRulesWithSingleBackReference(
                replacementMemberRule, otherMemberRule);
        if (replacementMemberRule == null) {
          // Not implemented.
          return materializedSubsequentRules;
        }
      }
    }
    List<ProguardMemberRule> memberRules;
    if (replacementMemberRule != null) {
      memberRules = new ArrayList<>(representativeKeepRule.getMemberRules());
      memberRules.set(memberRuleWithBackReferenceIndex, replacementMemberRule);
    } else {
      memberRules = Collections.emptyList();
    }
    ProguardKeepRule replacementKeepRule =
        new ProguardKeepRule(
            representativeKeepRule.getOrigin(),
            representativeKeepRule.getPosition(),
            representativeKeepRule.getSource(),
            classAnnotations,
            representativeKeepRule.getClassAccessFlags(),
            representativeKeepRule.getNegatedClassAccessFlags(),
            representativeKeepRule.getClassTypeNegated(),
            representativeKeepRule.getClassType(),
            classNames,
            inheritanceAnnotations,
            inheritanceClassName,
            representativeKeepRule.getInheritanceIsExtends(),
            memberRules,
            representativeKeepRule.getType(),
            representativeKeepRule.getModifiers());
    // TODO(b/368502790): We currently use the reason of the first materialiazed if rule. We should
    //  either report all reasons, or better, identify which reason lead to the subsequent rule
    //  matching a given item.
    ProguardIfRulePreconditionMatch replacementIfRulePreconditionMatch =
        IterableUtils.first(materializedSubsequentRules).getFirst();
    return ImmutableList.of(new Pair<>(replacementIfRulePreconditionMatch, replacementKeepRule));
  }

  private static ProguardMemberRule optimizeMaterializedMemberRulesWithSingleBackReference(
      ProguardMemberRule memberRule, ProguardMemberRule other) {
    assert ObjectUtils.identical(memberRule.getAccessFlags(), other.getAccessFlags());
    assert ObjectUtils.identical(memberRule.getNegatedAccessFlags(), other.getNegatedAccessFlags());
    assert ObjectUtils.identical(memberRule.getRuleType(), other.getRuleType());
    assert ObjectUtils.identical(memberRule.getReturnValue(), other.getReturnValue());
    if (memberRule.getAnnotations() != other.getAnnotations()
        || !memberRule.getName().equals(other.getName())
        || memberRule.getArguments() != other.getArguments()) {
      // Not implemented.
      return null;
    }
    ProguardTypeMatcher type = null;
    if (memberRule.hasType()) {
      type =
          optimizeMaterializedTypeMatcherWithSingleBackReference(
              memberRule.getType(), other.getType());
      if (type == null) {
        // Not implemented.
        return null;
      }
    }
    return new ProguardMemberRule(
        memberRule.getAnnotations(),
        memberRule.getAccessFlags(),
        memberRule.getNegatedAccessFlags(),
        memberRule.getRuleType(),
        type,
        memberRule.getName(),
        memberRule.getArguments(),
        memberRule.getReturnValue());
  }

  private static ProguardTypeMatcher optimizeMaterializedTypeMatcherWithSingleBackReference(
      ProguardTypeMatcher type, ProguardTypeMatcher other) {
    assert !other.hasSpecificTypes();
    if (type.hasSpecificType()) {
      if (other.hasSpecificType()) {
        return new MatchSpecificTypes(
            SetUtils.newIdentityHashSet(type.getSpecificType(), other.getSpecificType()));
      }
    } else if (type.hasSpecificTypes()) {
      if (other.hasSpecificType()) {
        type.getSpecificTypes().add(other.getSpecificType());
        return type;
      }
    }
    // Not implemented.
    return null;
  }
}
