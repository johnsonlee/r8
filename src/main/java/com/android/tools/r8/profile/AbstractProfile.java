// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.profile.AbstractProfile.Builder;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.function.BiConsumer;

public interface AbstractProfile<
    ClassRule extends AbstractProfileClassRule,
    MethodRule extends AbstractProfileMethodRule,
    Profile extends AbstractProfile<ClassRule, MethodRule, Profile, ProfileBuilder>,
    ProfileBuilder extends Builder<ClassRule, MethodRule, Profile, ProfileBuilder>> {

  boolean containsClassRule(DexType type);

  boolean containsMethodRule(DexMethod method);

  <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<? super ClassRule, E1> classRuleConsumer,
      ThrowingConsumer<? super MethodRule, E2> methodRuleConsumer)
      throws E1, E2;

  ClassRule getClassRule(DexType type);

  MethodRule getMethodRule(DexMethod method);

  ProfileBuilder toEmptyBuilderWithCapacity();

  default Profile toProfileWithSuperclasses(AppView<?> appView) {
    return transform(
        (classRule, builder) -> builder.addClassAndParentClasses(classRule.getReference(), appView),
        (methodRule, builder) -> {
          builder.addClassAndParentClasses(methodRule.getReference().getHolderType(), appView);
          builder.addMethodRule(methodRule);
        });
  }

  default Profile transform(
      BiConsumer<ClassRule, ProfileBuilder> classRuleTransformer,
      BiConsumer<MethodRule, ProfileBuilder> methodRuleTransformer) {
    ProfileBuilder builder = toEmptyBuilderWithCapacity();
    forEachRule(
        classRule -> classRuleTransformer.accept(classRule, builder),
        methodRule -> methodRuleTransformer.accept(methodRule, builder));
    return builder.build();
  }

  interface Builder<
      ClassRule extends AbstractProfileClassRule,
      MethodRule extends AbstractProfileMethodRule,
      Profile extends AbstractProfile<ClassRule, MethodRule, Profile, ProfileBuilder>,
      ProfileBuilder extends Builder<ClassRule, MethodRule, Profile, ProfileBuilder>> {

    ProfileBuilder addRule(AbstractProfileRule rule);

    ProfileBuilder addClassRule(ClassRule classRule);

    boolean addClassRule(DexType type);

    default void addClassAndParentClasses(DexType type, AppView<?> appView) {
      DexProgramClass definition = appView.app().programDefinitionFor(type);
      if (definition != null) {
        addClassAndParentClasses(definition, appView);
      }
    }

    private void addClassAndParentClasses(DexProgramClass clazz, AppView<?> appView) {
      if (addClassRule(clazz.getType())) {
        addParentClasses(clazz, appView);
      }
    }

    private void addParentClasses(DexProgramClass clazz, AppView<?> appView) {
      clazz.forEachImmediateSupertype(supertype -> addClassAndParentClasses(supertype, appView));
    }

    ProfileBuilder addMethodRule(MethodRule methodRule);

    Profile build();

    int size();
  }
}
