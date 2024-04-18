// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.InstanceInitializerMerger.Builder;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InstanceInitializerMergerCollection {

  private final List<InstanceInitializerMerger> instanceInitializerMergers;
  private final Map<InstanceInitializerDescription, List<InstanceInitializerMerger>>
      equivalentInstanceInitializerMergers;

  private InstanceInitializerMergerCollection(
      List<InstanceInitializerMerger> instanceInitializerMergers,
      Map<InstanceInitializerDescription, List<InstanceInitializerMerger>>
          equivalentInstanceInitializerMergers) {
    this.instanceInitializerMergers = instanceInitializerMergers;
    this.equivalentInstanceInitializerMergers = equivalentInstanceInitializerMergers;
  }

  @SuppressWarnings("BadImport")
  public static InstanceInitializerMergerCollection create(
      AppView<?> appView,
      Reference2IntMap<DexType> classIdentifiers,
      HorizontalMergeGroup group,
      HorizontalClassMergerGraphLens.Builder lensBuilder) {
    if (!appView.hasClassHierarchy()) {
      assert appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics();
      assert verifyNoInstanceInitializers(group);
      return new InstanceInitializerMergerCollection(
          Collections.emptyList(), Collections.emptyMap());
    }
    // Create an instance initializer merger for each group of instance initializers that are
    // equivalent.
    AppView<AppInfoWithClassHierarchy> appViewWithClassHierarchy = appView.withClassHierarchy();
    Map<InstanceInitializerDescription, Builder> buildersByDescription = new LinkedHashMap<>();
    ProgramMethodSet buildersWithoutDescription = ProgramMethodSet.createLinked();
    group.forEach(
        clazz ->
            clazz.forEachProgramDirectMethodMatching(
                DexEncodedMethod::isInstanceInitializer,
                instanceInitializer -> {
                  InstanceInitializerDescription description =
                      InstanceInitializerAnalysis.analyze(
                          appViewWithClassHierarchy, group, instanceInitializer);
                  if (description != null) {
                    buildersByDescription
                        .computeIfAbsent(
                            description,
                            ignoreKey(
                                () ->
                                    new InstanceInitializerMerger.Builder(
                                        appViewWithClassHierarchy, classIdentifiers, lensBuilder)))
                        .addEquivalent(instanceInitializer);
                  } else {
                    buildersWithoutDescription.add(instanceInitializer);
                  }
                }));

    Map<InstanceInitializerDescription, List<InstanceInitializerMerger>>
        equivalentInstanceInitializerMergers = new LinkedHashMap<>();
    buildersByDescription.forEach(
        (description, builder) -> {
          List<InstanceInitializerMerger> instanceInitializerMergers =
              builder.buildEquivalent(group, description);
          for (InstanceInitializerMerger instanceInitializerMerger : instanceInitializerMergers) {
            if (instanceInitializerMerger.size() == 1) {
              // If there is only one constructor with a specific behavior, then consider it for
              // normal instance initializer merging below.
              buildersWithoutDescription.addAll(
                  instanceInitializerMerger.getInstanceInitializers());
            } else {
              equivalentInstanceInitializerMergers
                  .computeIfAbsent(description, ignoreKey(ArrayList::new))
                  .add(instanceInitializerMerger);
            }
          }
        });

    // Merge instance initializers with different behavior.
    List<InstanceInitializerMerger> instanceInitializerMergers = new ArrayList<>();
    Map<DexProto, Builder> buildersByProto = new LinkedHashMap<>();
    buildersWithoutDescription.forEach(
        instanceInitializer ->
            buildersByProto
                .computeIfAbsent(
                    instanceInitializer.getDefinition().getProto(),
                    ignore ->
                        new InstanceInitializerMerger.Builder(
                            appViewWithClassHierarchy, classIdentifiers, lensBuilder))
                .add(instanceInitializer));
    for (InstanceInitializerMerger.Builder builder : buildersByProto.values()) {
      instanceInitializerMergers.addAll(builder.build(group));
    }

    // Try and merge the constructors with the most arguments first, to avoid using synthetic
    // arguments if possible.
    instanceInitializerMergers.sort(
        Comparator.comparing(InstanceInitializerMerger::getArity).reversed());
    return new InstanceInitializerMergerCollection(
        instanceInitializerMergers, equivalentInstanceInitializerMergers);
  }

  private static boolean verifyNoInstanceInitializers(HorizontalMergeGroup group) {
    group.forEach(
        clazz -> {
          assert !clazz.programInstanceInitializers().iterator().hasNext();
        });
    return true;
  }

  public void forEach(Consumer<InstanceInitializerMerger> consumer) {
    instanceInitializerMergers.forEach(consumer);
    IterableUtils.flatten(equivalentInstanceInitializerMergers.values()).forEach(consumer);
  }

  public void setObsolete() {
    forEach(InstanceInitializerMerger::setObsolete);
  }
}
