// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.InlineRule.InlineRuleType;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ProguardIfRule extends ProguardKeepRuleBase {

  private static final Origin NEVER_INLINE_ORIGIN =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return "<SYNTHETIC_NEVER_INLINE_RULE>";
        }
      };

  private final DexProgramClass precondition;
  final ProguardKeepRule subsequentRule;

  private Map<DexField, DexField> inlinableFieldsInPrecondition = new ConcurrentHashMap<>();

  public DexProgramClass getPrecondition() {
    assert precondition != null;
    return precondition;
  }

  public ProguardKeepRule getSubsequentRule() {
    return subsequentRule;
  }

  public void addInlinableFieldMatchingPrecondition(DexField field) {
    if (inlinableFieldsInPrecondition != null) {
      inlinableFieldsInPrecondition.put(field, field);
    }
  }

  public Set<DexField> getAndClearInlinableFieldsMatchingPrecondition() {
    Set<DexField> fields = inlinableFieldsInPrecondition.keySet();
    inlinableFieldsInPrecondition = null;
    return fields;
  }

  public static class Builder extends ProguardKeepRuleBase.Builder<ProguardIfRule, Builder> {

    ProguardKeepRule subsequentRule = null;

    protected Builder() {
      super();
    }

    @Override
    public Builder self() {
      return this;
    }

    public void setSubsequentRule(ProguardKeepRule rule) {
      subsequentRule = rule;
    }

    @Override
    public ProguardIfRule build() {
      assert subsequentRule != null : "Option -if without a subsequent rule.";
      return new ProguardIfRule(
          origin,
          getPosition(),
          source,
          buildClassAnnotations(),
          classAccessFlags,
          negatedClassAccessFlags,
          classTypeNegated,
          classType,
          classNames,
          buildInheritanceAnnotations(),
          inheritanceClassName,
          inheritanceIsExtends,
          memberRules,
          subsequentRule,
          null);
    }
  }

  private ProguardIfRule(
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
      List<ProguardMemberRule> memberRules,
      ProguardKeepRule subsequentRule,
      DexProgramClass precondition) {
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
        memberRules,
        ProguardKeepRuleType.CONDITIONAL,
        ProguardKeepRuleModifiers.builder().build());
    this.subsequentRule = subsequentRule;
    this.precondition = precondition;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  protected <T extends ProguardWildcard> Iterable<T> getWildcardsThatMatches(
      Predicate<? super ProguardWildcard> predicate) {
    return Iterables.concat(
        super.getWildcardsThatMatches(predicate),
        subsequentRule.getWildcardsThatMatches(predicate));
  }

  @Override
  public boolean isProguardIfRule() {
    return true;
  }

  @Override
  public ProguardIfRule asProguardIfRule() {
    return this;
  }

  protected ProguardKeepRule materialize(DexItemFactory dexItemFactory) {
    markAsUsed();
    return subsequentRule.materialize(dexItemFactory);
  }

  /**
   * Consider the following rule, which requests that class Y should be kept if the method X.m() is
   * in the final output.
   *
   * <pre>
   * -if class X {
   *   public void m();
   * }
   * -keep class Y
   * </pre>
   *
   * When the {@link Enqueuer} finds that the method X.m() is reachable, it applies the subsequent
   * keep rule of the -if rule. Thus, Y will be marked as pinned, which guarantees, for example,
   * that it will not be merged into another class by the vertical class merger.
   *
   * <p>However, when the {@link Enqueuer} runs for the second time, it is important that X.m() has
   * not been inlined into another method Z.z(), because that would mean that Z.z() now relies on
   * the presence of Y, meanwhile Y will not be kept because X.m() is no longer present.
   *
   * <p>Therefore, each time the subsequent rule of an -if rule is applied, we also apply a
   * -neverinline rule for the condition of the -if rule.
   */
  protected InlineRule neverInlineRuleForCondition(
      DexItemFactory dexItemFactory, InlineRuleType type) {
    if (getMemberRules() == null || getMemberRules().isEmpty()) {
      return null;
    }
    return new InlineRule(
        NEVER_INLINE_ORIGIN,
        Position.UNKNOWN,
        null,
        ProguardTypeMatcher.materializeList(getClassAnnotations(), dexItemFactory),
        getClassAccessFlags(),
        getNegatedClassAccessFlags(),
        getClassTypeNegated(),
        getClassType(),
        getClassNames().materialize(dexItemFactory),
        ProguardTypeMatcher.materializeList(getInheritanceAnnotations(), dexItemFactory),
        getInheritanceClassName() == null
            ? null
            : getInheritanceClassName().materialize(dexItemFactory),
        getInheritanceIsExtends(),
        getMemberRules().stream()
            .filter(rule -> rule.getRuleType().includesMethods())
            .map(memberRule -> memberRule.materialize(dexItemFactory))
            .collect(Collectors.toList()),
        type);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardIfRule)) {
      return false;
    }
    ProguardIfRule other = (ProguardIfRule) o;
    if (!subsequentRule.equals(other.subsequentRule)) {
      return false;
    }
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode() * 3 + subsequentRule.hashCode();
  }

  @Override
  String typeString() {
    return "if";
  }

  @Override
  protected StringBuilder append(StringBuilder builder) {
    super.append(builder);
    builder.append('\n');
    return subsequentRule.append(builder);
  }

  public ProguardIfRule withPrecondition(DexProgramClass precondition) {
    return new ProguardIfRule(
        getOrigin(),
        getPosition(),
        getSource(),
        getClassAnnotations(),
        getClassAccessFlags(),
        getNegatedClassAccessFlags(),
        getClassTypeNegated(),
        getClassType(),
        getClassNames(),
        getInheritanceAnnotations(),
        getInheritanceClassName(),
        getInheritanceIsExtends(),
        getMemberRules(),
        getSubsequentRule(),
        precondition);
  }
}
