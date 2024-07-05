// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.keepanno.ast.KeepAnnotationPattern;
import com.android.tools.r8.keepanno.ast.KeepBindingReference;
import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.ast.KeepCheck;
import com.android.tools.r8.keepanno.ast.KeepCheck.KeepCheckKind;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConstraint.Annotation;
import com.android.tools.r8.keepanno.ast.KeepConstraint.ClassInstantiate;
import com.android.tools.r8.keepanno.ast.KeepConstraint.ClassOpenHierarchy;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldGet;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldReplace;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldSet;
import com.android.tools.r8.keepanno.ast.KeepConstraint.GenericSignature;
import com.android.tools.r8.keepanno.ast.KeepConstraint.Lookup;
import com.android.tools.r8.keepanno.ast.KeepConstraint.MethodInvoke;
import com.android.tools.r8.keepanno.ast.KeepConstraint.MethodReplace;
import com.android.tools.r8.keepanno.ast.KeepConstraint.Name;
import com.android.tools.r8.keepanno.ast.KeepConstraint.NeverInline;
import com.android.tools.r8.keepanno.ast.KeepConstraint.VisibilityRelax;
import com.android.tools.r8.keepanno.ast.KeepConstraint.VisibilityRestrict;
import com.android.tools.r8.keepanno.ast.KeepConstraintVisitor;
import com.android.tools.r8.keepanno.ast.KeepConstraints;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.shaking.KeepAnnotationCollectionInfo.RetentionInfo;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class KeepAnnotationMatcher {

  public static ApplicableRulesEvaluator computeInitialRules(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      List<KeepDeclaration> keepDeclarations,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    KeepAnnotationMatcherPredicates predicates =
        new KeepAnnotationMatcherPredicates(appView.dexItemFactory());
    ApplicableRulesEvaluator.Builder builder = ApplicableRulesEvaluator.initialRulesBuilder();
    ThreadUtils.processItems(
        keepDeclarations,
        declaration -> processDeclaration(declaration, appView.appInfo(), predicates, builder),
        threadingModule,
        executorService);
    return builder.build(appView);
  }

  private static void processDeclaration(
      KeepDeclaration declaration,
      AppInfoWithClassHierarchy appInfo,
      KeepAnnotationMatcherPredicates predicates,
      ApplicableRulesEvaluator.Builder builder) {
    EdgeMatcher edgeMatcher = new EdgeMatcher(appInfo, predicates);
    declaration.match(
        edge ->
            edgeMatcher.forEachMatch(
                edge,
                result -> {
                  if (result.preconditions.isEmpty()) {
                    builder.addRootRule(
                        keepInfoCollection ->
                            createKeepInfo(result, keepInfoCollection, predicates));
                  } else {
                    builder.addConditionalRule(
                        new PendingInitialConditionalRule(
                            result.preconditions,
                            createKeepInfo(
                                result, MinimumKeepInfoCollection.create(), predicates)));
                  }
                }),
        check -> {
          edgeMatcher.forEachMatch(
              check,
              result -> {
                assert result.preconditions.isEmpty();
                builder.addRootRule(
                    keepInfoCollection ->
                        createCheckDiscardInfo(check, result, keepInfoCollection));
              });
        });
  }

  private static void createCheckDiscardInfo(
      KeepCheck check, MatchResult result, MinimumKeepInfoCollection keepInfoCollection) {
    boolean isRemovedCheck = check.getKind() == KeepCheckKind.REMOVED;
    ListUtils.forEachWithIndex(
        result.consequences,
        (item, i) -> {
          applyCheckDiscardConstraints(isRemovedCheck, keepInfoCollection, item);
          // If a check-discard is annotated on a type, then it applies to all members of the type.
          if (item.isClass()) {
            item.asProgramClass()
                .forEachProgramMember(
                    member ->
                        applyCheckDiscardConstraints(isRemovedCheck, keepInfoCollection, member));
          }
        });
  }

  private static void applyCheckDiscardConstraints(
      boolean isRemovedCheck,
      MinimumKeepInfoCollection keepInfoCollection,
      ProgramDefinition item) {
    Joiner<?, ?, ?> joiner = keepInfoCollection.getOrCreateMinimumKeepInfoFor(item.getReference());
    joiner.setCheckDiscarded();
    if (isRemovedCheck) {
      joiner.disallowOptimization();
    }
  }

  private static MinimumKeepInfoCollection createKeepInfo(
      MatchResult result,
      MinimumKeepInfoCollection minimumKeepInfoCollection,
      KeepAnnotationMatcherPredicates predicates) {
    ListUtils.forEachWithIndex(
        result.consequences,
        (item, i) -> {
          Joiner<?, ?, ?> joiner =
              minimumKeepInfoCollection.getOrCreateMinimumKeepInfoFor(item.getReference());
          updateWithConstraints(
              item, joiner, result.constraints.get(i), result.declaration, predicates);
        });
    return minimumKeepInfoCollection;
  }

  private static void updateWithConstraints(
      ProgramDefinition item,
      Joiner<?, ?, ?> joiner,
      KeepConstraints constraints,
      KeepDeclaration declaration,
      KeepAnnotationMatcherPredicates predicates) {
    constraints.forEachAccept(
        new KeepConstraintVisitor() {

          @Override
          public void onLookup(Lookup constraint) {
            joiner.disallowShrinking();
            joiner.addRule(new KeepAnnotationFakeProguardRule(declaration.getMetaInfo()));
          }

          @Override
          public void onName(Name constraint) {
            joiner.disallowMinification();
            if (item.isProgramClass()) {
              joiner.asClassJoiner().disallowRepackaging();
            }
          }

          @Override
          public void onVisibilityRelax(VisibilityRelax constraint) {
            // R8 will never restrict the access, so this constraint is implicitly maintained.
          }

          @Override
          public void onVisibilityRestrict(VisibilityRestrict constraint) {
            joiner.disallowAccessModification();
          }

          @Override
          public void onNeverInline(NeverInline constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onClassInstantiate(ClassInstantiate constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onClassOpenHierarchy(ClassOpenHierarchy constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onMethodInvoke(MethodInvoke constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onMethodReplace(MethodReplace constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onFieldGet(FieldGet constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onFieldSet(FieldSet constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onFieldReplace(FieldReplace constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onGenericSignature(GenericSignature constraint) {
            joiner.disallowSignatureRemoval();
          }

          @Override
          public void onAnnotation(Annotation constraint) {
            KeepAnnotationPattern pattern = constraint.asAnnotationPattern();
            if (pattern.getNamePattern().isAny()) {
              joiner.disallowAnnotationRemoval(toRetentionInfo(pattern));
            } else {
              item.getDefinition()
                  .annotations()
                  .forEach(
                      annotation -> {
                        if (predicates.matchesAnnotation(annotation, pattern)) {
                          joiner.disallowAnnotationRemoval(
                              toRetentionInfo(pattern), annotation.getAnnotationType());
                        }
                      });
            }
          }

          private RetentionInfo toRetentionInfo(KeepAnnotationPattern pattern) {
            if (pattern.includesRuntimeRetention() && pattern.includesClassRetention()) {
              return RetentionInfo.getRetainAll();
            }
            if (pattern.includesRuntimeRetention()) {
              return RetentionInfo.getRetainVisible();
            }
            if (pattern.includesClassRetention()) {
              return RetentionInfo.getRetainInvisible();
            }
            return RetentionInfo.getRetainNone();
          }
        });
  }

  public static class MatchResult {
    private final KeepDeclaration declaration;
    private final List<ProgramDefinition> preconditions;
    private final List<ProgramDefinition> consequences;
    private final List<KeepConstraints> constraints;

    public MatchResult(
        KeepDeclaration declaration,
        List<ProgramDefinition> preconditions,
        List<ProgramDefinition> consequences,
        List<KeepConstraints> constraints) {
      this.declaration = declaration;
      this.preconditions = preconditions;
      this.consequences = consequences;
      this.constraints = constraints;
    }
  }

  public static class EdgeMatcher {

    private final AppInfoWithClassHierarchy appInfo;
    private final KeepAnnotationMatcherPredicates predicates;

    private NormalizedSchema schema;
    private Assignment assignment;
    private Consumer<MatchResult> callback;

    public EdgeMatcher(
        AppInfoWithClassHierarchy appInfo, KeepAnnotationMatcherPredicates predicates) {
      this.appInfo = appInfo;
      this.predicates = predicates;
    }

    public void forEachMatch(KeepDeclaration declaration, Consumer<MatchResult> callback) {
      this.callback = callback;
      schema = new NormalizedSchema(declaration);
      assignment = new Assignment(schema);
      findMatchingClass(0);
      schema = null;
      assignment = null;
    }

    private void foundMatch() {
      MatchResult match = assignment.createMatch(schema);
      // We might not have found any matching consequences and this is not an actual match.
      if (!match.consequences.isEmpty()) {
        callback.accept(match);
      }
    }

    private void findMatchingClass(int classIndex) {
      if (classIndex == schema.classes.size()) {
        // All classes and their members are assigned, so report the assignment.
        foundMatch();
        return;
      }
      KeepClassItemPattern classPattern = schema.classes.get(classIndex);
      if (classPattern.getClassNamePattern().isExact()) {
        DexType type =
            appInfo
                .dexItemFactory()
                .createType(classPattern.getClassNamePattern().getExactDescriptor());
        DexProgramClass clazz = DexProgramClass.asProgramClassOrNull(appInfo.definitionFor(type));
        if (clazz == null) {
          continueWithNoClass(classIndex);
          return;
        }
        if (!predicates.matchesClass(clazz, classPattern, appInfo)) {
          continueWithNoClass(classIndex);
          return;
        }
        continueWithClass(classIndex, clazz);
        return;
      }
      // TODO(b/323816623): This repeated iteration on all classes must be avoided.
      for (DexProgramClass clazz : appInfo.classes()) {
        if (predicates.matchesClass(clazz, classPattern, appInfo)) {
          continueWithClass(classIndex, clazz);
        }
      }
    }

    private void continueWithClass(int classIndex, DexProgramClass clazz) {
      assignment.setClass(classIndex, clazz);
      IntList classMemberIndexList = schema.classMembers.get(classIndex);
      findMatchingMember(0, classMemberIndexList, clazz, classIndex + 1);
    }

    private void continueWithNoClass(int classIndex) {
      continueWithNoClassClearingMembers(classIndex, 0, null);
    }

    private void continueWithNoClassClearingMembers(
        int classIndex, int memberInHolderIndex, IntList memberIndexTranslation) {
      if (schema.isOptionalClass(classIndex)) {
        assignment.setClass(classIndex, null);
        for (int i = 0; i < memberInHolderIndex; i++) {
          assignment.setMember(memberIndexTranslation.getInt(i), null);
        }
        findMatchingClass(classIndex + 1);
      }
    }

    private void findMatchingMember(
        int memberInHolderIndex,
        IntList memberIndexTranslation,
        DexProgramClass holder,
        int nextClassIndex) {
      if (memberInHolderIndex == memberIndexTranslation.size()) {
        // All members of this class are assigned, continue search for the next class.
        findMatchingClass(nextClassIndex);
        return;
      }
      int memberIndex = memberIndexTranslation.getInt(memberInHolderIndex);
      KeepMemberItemPattern memberItemPattern = schema.members.get(memberIndex);
      BooleanBox didContinue = new BooleanBox(false);
      Consumer<ProgramDefinition> continueWithMember =
          m -> {
            didContinue.isTrue();
            continueWithMember(
                m, memberIndex, memberInHolderIndex + 1, memberIndexTranslation, nextClassIndex);
          };
      memberItemPattern
          .getMemberPattern()
          .match(
              generalMemberPattern -> {
                if (!holder.hasMethodsOrFields() && generalMemberPattern.isAllMembers()) {
                  // The empty class can only match the "all member" pattern but with no assignment.
                  continueWithMember.accept(holder);
                } else {
                  holder.forEachProgramMember(
                      m -> {
                        if (predicates.matchesGeneralMember(
                            m.getDefinition(), generalMemberPattern)) {
                          continueWithMember.accept(m);
                        }
                      });
                }
              },
              fieldPattern ->
                  holder.forEachProgramFieldMatching(
                      f -> predicates.matchesField(f, fieldPattern, appInfo), continueWithMember),
              methodPattern ->
                  holder.forEachProgramMethodMatching(
                      m -> predicates.matchesMethod(m, methodPattern, appInfo),
                      continueWithMember));
      if (didContinue.isFalse()) {
        // No match for the member pattern existed, continue with next class.
        continueWithNoClassClearingMembers(
            nextClassIndex - 1, memberInHolderIndex, memberIndexTranslation);
      }
    }

    private void continueWithMember(
        ProgramDefinition definition,
        int memberIndex,
        int nextMemberInHolderIndex,
        IntList memberIndexTranslation,
        int nextClassIndex) {
      if (definition.isProgramMember()) {
        assignment.setMember(memberIndex, definition.asProgramMember());
      } else {
        assert definition.isProgramClass();
        assert !definition.asProgramClass().hasMethodsOrFields();
        assignment.setEmptyMemberMatch(memberIndex);
      }
      findMatchingMember(
          nextMemberInHolderIndex,
          memberIndexTranslation,
          definition.getContextClass(),
          nextClassIndex);
    }
  }

  /**
   * The normalized edge schema maps an edge into integer indexed class patterns and member
   * patterns. The preconditions and consequences are then index references to these pattern. Each
   * index denotes the identity of an item, thus the same reference must share the same item found
   * by a pattern.
   */
  private static class NormalizedSchema {

    final KeepDeclaration declaration;
    final Reference2IntMap<KeepBindingSymbol> symbolToKey = new Reference2IntOpenHashMap<>();
    final List<KeepClassItemPattern> classes = new ArrayList<>();
    final List<KeepMemberItemPattern> members = new ArrayList<>();
    final List<IntList> classMembers = new ArrayList<>();
    final IntList preconditions = new IntArrayList();
    final IntList consequences = new IntArrayList();
    final List<KeepConstraints> constraints = new ArrayList<>();
    int preconditionClassesCount = -1;
    int preconditionMembersCount = -1;

    public NormalizedSchema(KeepDeclaration declaration) {
      this.declaration = declaration;
      declaration.match(
          edge -> {
            edge.getPreconditions().forEach(this::addPrecondition);
            preconditionClassesCount = classes.size();
            preconditionMembersCount = members.size();
            edge.getConsequences().forEachTarget(this::addConsequence);
          },
          check -> {
            preconditionClassesCount = 0;
            preconditionMembersCount = 0;
            consequences.add(defineBindingReference(check.getItemReference()));
          });
    }

    private KeepItemPattern getItemForBinding(KeepBindingSymbol symbol) {
      KeepBindings bindings = declaration.apply(KeepEdge::getBindings, KeepCheck::getBindings);
      return bindings.get(symbol).getItem();
    }

    public boolean isOptionalClass(int classIndex) {
      return classIndex >= preconditionClassesCount;
    }

    public static boolean isClassKeyReference(int keyRef) {
      return keyRef >= 0;
    }

    private int encodeClassKey(int key) {
      assert isClassKeyReference(key);
      return key;
    }

    public static int decodeClassKeyReference(int key) {
      assert isClassKeyReference(key);
      return key;
    }

    private int encodeMemberKey(int key) {
      assert key >= 0;
      return -(key + 1);
    }

    public static int decodeMemberKeyReference(int key) {
      assert !isClassKeyReference(key);
      assert key < 0;
      return -(key + 1);
    }

    private int defineBindingReference(KeepBindingReference reference) {
      return symbolToKey.computeIfAbsent(
          reference.getName(), symbol -> defineItemPattern(getItemForBinding(symbol)));
    }

    private int defineItemPattern(KeepItemPattern item) {
      if (item.isClassItemPattern()) {
        int classIndex = classes.size();
        classes.add(item.asClassItemPattern());
        classMembers.add(new IntArrayList());
        return encodeClassKey(classIndex);
      } else {
        KeepBindingReference reference = item.asMemberItemPattern().getClassReference();
        int classIndex = defineBindingReference(reference);
        int memberIndex = members.size();
        members.add(item.asMemberItemPattern());
        classMembers.get(classIndex).add(memberIndex);
        return encodeMemberKey(memberIndex);
      }
    }

    public void addPrecondition(KeepCondition condition) {
      preconditions.add(defineBindingReference(condition.getItem()));
    }

    private void addConsequence(KeepTarget target) {
      KeepBindingReference reference = target.getItem();
      consequences.add(defineBindingReference(reference));
      constraints.add(target.getConstraints());
    }
  }

  /**
   * The assignment contains the full matching of the pattern, if a matching was found. The
   * assignment is mutable and updated during the search. When a match is found the required
   * information must be copied over immediately by creating a match result.
   */
  private static class Assignment {

    final List<DexProgramClass> classes;
    final List<ProgramMember<?, ?>> members;
    boolean hasEmptyMembers = false;

    private Assignment(NormalizedSchema schema) {
      classes = Arrays.asList(new DexProgramClass[schema.classes.size()]);
      members = Arrays.asList(new ProgramMember<?, ?>[schema.members.size()]);
    }

    ProgramDefinition getItemForReference(int keyReference) {
      if (NormalizedSchema.isClassKeyReference(keyReference)) {
        return classes.get(NormalizedSchema.decodeClassKeyReference(keyReference));
      }
      return members.get(NormalizedSchema.decodeMemberKeyReference(keyReference));
    }

    void setClass(int index, DexProgramClass type) {
      classes.set(index, type);
    }

    void setMember(int index, ProgramMember<?, ?> member) {
      members.set(index, member);
    }

    void setEmptyMemberMatch(int index) {
      hasEmptyMembers = true;
      members.set(index, null);
    }

    public MatchResult createMatch(NormalizedSchema schema) {
      return new MatchResult(
          schema.declaration,
          schema.preconditions.isEmpty()
              ? Collections.emptyList()
              : getItemList(schema.preconditions, false),
          getItemList(schema.consequences, true),
          getConstraints(schema));
    }

    private List<ProgramDefinition> getItemList(IntList indexReferences, boolean allowUnset) {
      assert !indexReferences.isEmpty();
      List<ProgramDefinition> definitions = new ArrayList<>(indexReferences.size());
      for (int i = 0; i < indexReferences.size(); i++) {
        ProgramDefinition item = getItemForReference(indexReferences.getInt(i));
        assert item != null || hasEmptyMembers || allowUnset;
        if (item != null) {
          definitions.add(item);
        }
      }
      return definitions;
    }

    private List<KeepConstraints> getConstraints(NormalizedSchema schema) {
      if (!hasEmptyMembers) {
        return schema.constraints;
      }
      // Since members may have a matching "empty" pattern, we need to prune those from the
      // constraints, so it matches the consequence list.
      ImmutableList.Builder<KeepConstraints> builder = ImmutableList.builder();
      for (int i = 0; i < schema.consequences.size(); i++) {
        ProgramDefinition item = getItemForReference(schema.consequences.getInt(i));
        if (item != null) {
          builder.add(schema.constraints.get(i));
        }
      }
      return builder.build();
    }
  }
}
