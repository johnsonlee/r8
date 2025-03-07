// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ArtProfileCollection implements Iterable<ArtProfile> {

  public static ArtProfileCollection createInitialArtProfileCollection(
      AppInfo appInfo, InternalOptions options) {
    ArtProfileOptions artProfileOptions = options.getArtProfileOptions();
    List<ArtProfile> artProfiles = new ArrayList<>();
    if (options.partialSubCompilationConfiguration == null
        || options.partialSubCompilationConfiguration.isD8()) {
      Collection<ArtProfileProvider> artProfileProviders =
          artProfileOptions.getArtProfileProviders();
      for (ArtProfileProvider artProfileProvider : artProfileProviders) {
        ArtProfile.Builder artProfileBuilder =
            ArtProfile.builderForInitialArtProfile(artProfileProvider, options);
        artProfileProvider.getArtProfile(artProfileBuilder);
        artProfiles.add(artProfileBuilder.build());
      }
    } else {
      assert options.partialSubCompilationConfiguration.isR8();
      Iterables.addAll(
          artProfiles, options.partialSubCompilationConfiguration.asR8().getArtProfiles());
    }
    if (artProfileOptions.isCompletenessCheckForTestingEnabled()) {
      artProfiles.add(createCompleteArtProfile(appInfo));
    }
    if (artProfileOptions.isNopCheckForTestingEnabled()) {
      assert artProfileOptions.setNopCheckForTestingHashCode(appInfo);
    }
    if (artProfiles.isEmpty()) {
      return empty();
    }
    return new NonEmptyArtProfileCollection(artProfiles);
  }

  private static ArtProfile createCompleteArtProfile(AppInfo appInfo) {
    ArtProfile.Builder artProfileBuilder = ArtProfile.builder();
    for (DexProgramClass clazz : appInfo.classesWithDeterministicOrder()) {
      artProfileBuilder.addClassRule(clazz.getType());
      clazz.forEachMethod(
          method ->
              artProfileBuilder.addMethodRule(
                  ArtProfileMethodRule.builder()
                      .setMethod(method.getReference())
                      .acceptMethodRuleInfoBuilder(
                          methodRuleInfoBuilder ->
                              methodRuleInfoBuilder.setIsHot().setIsStartup().setIsPostStartup())
                      .build()));
    }
    R8PartialSubCompilationConfiguration subCompilationConfiguration =
        appInfo.options().partialSubCompilationConfiguration;
    if (subCompilationConfiguration != null && subCompilationConfiguration.isR8()) {
      subCompilationConfiguration.asR8().amendCompleteArtProfile(artProfileBuilder);
    }
    return artProfileBuilder.build();
  }

  public static EmptyArtProfileCollection empty() {
    return EmptyArtProfileCollection.getInstance();
  }

  public abstract boolean isEmpty();

  public abstract boolean isNonEmpty();

  public abstract NonEmptyArtProfileCollection asNonEmpty();

  public abstract ArtProfileCollection rewrittenWithLens(
      AppView<?> appView, GraphLens lens, Timing timing);

  public abstract ArtProfileCollection rewrittenWithLens(AppView<?> appView, NamingLens lens);

  public abstract void supplyConsumers(AppView<?> appView);

  public abstract ArtProfileCollection transformForR8Partial(AppView<AppInfo> appView);

  public abstract ArtProfileCollection withoutMissingItems(AppView<?> appView);

  public abstract ArtProfileCollection withoutPrunedItems(PrunedItems prunedItems, Timing timing);
}
