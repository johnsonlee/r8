// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.features;

import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.IterableUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class FeatureSplitConfiguration {

  private final LinkedHashMap<FeatureSplit, List<FeatureSplitProgramResourceProvider>>
      featureSplits;
  private final boolean isolatedSplits;

  public FeatureSplitConfiguration(
      LinkedHashMap<FeatureSplit, List<FeatureSplitProgramResourceProvider>> featureSplits,
      boolean isolatedSplits) {
    this.featureSplits = featureSplits;
    this.isolatedSplits = isolatedSplits;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class DataResourceProvidersAndConsumer {

    public final FeatureSplit featureSplit;
    public final Set<DataResourceProvider> providers;
    public final DataResourceConsumer consumer;

    public DataResourceProvidersAndConsumer(
        FeatureSplit featureSplit,
        Set<DataResourceProvider> providers,
        DataResourceConsumer consumer) {
      this.featureSplit = featureSplit;
      this.providers = providers;
      this.consumer = consumer;
    }
  }

  public Collection<DataResourceProvidersAndConsumer> getDataResourceProvidersAndConsumers() {
    List<DataResourceProvidersAndConsumer> result = new ArrayList<>();
    for (FeatureSplit featureSplit : getFeatureSplits()) {
      DataResourceConsumer dataResourceConsumer =
          featureSplit.getProgramConsumer().getDataResourceConsumer();
      if (dataResourceConsumer != null) {
        Set<DataResourceProvider> dataResourceProviders = new HashSet<>();
        for (ProgramResourceProvider programResourceProvider :
            featureSplit.getProgramResourceProviders()) {
          DataResourceProvider dataResourceProvider =
              programResourceProvider.getDataResourceProvider();
          if (dataResourceProvider != null) {
            dataResourceProviders.add(dataResourceProvider);
          }
        }
        if (!dataResourceProviders.isEmpty()) {
          result.add(
              new DataResourceProvidersAndConsumer(
                  featureSplit, dataResourceProviders, dataResourceConsumer));
        }
      }
    }
    return result;
  }

  public Collection<FeatureSplit> getFeatureSplits() {
    return featureSplits.keySet();
  }

  public List<FeatureSplitProgramResourceProvider> getFeatureSplitProgramResourceProviders(
      FeatureSplit featureSplit) {
    return featureSplits.get(featureSplit);
  }

  public boolean isIsolatedSplitsEnabled() {
    return isolatedSplits;
  }

  public static class Builder {

    private final LinkedHashMap<FeatureSplit, List<FeatureSplitProgramResourceProvider>>
        featureSplits = new LinkedHashMap<>();
    private boolean isolatedSplits;

    public Builder addFeatureSplit(
        FeatureSplit featureSplit,
        List<FeatureSplitProgramResourceProvider> featureSplitProgramResourceProviders) {
      featureSplits.put(featureSplit, featureSplitProgramResourceProviders);
      return this;
    }

    public Collection<FeatureSplit> getFeatureSplits() {
      return featureSplits.keySet();
    }

    public Builder setEnableIsolatedSplits(boolean isolatedSplits) {
      this.isolatedSplits = isolatedSplits;
      return this;
    }

    public FeatureSplitConfiguration build(DexItemFactory factory) {
      if (featureSplits.isEmpty()) {
        return null;
      }
      for (FeatureSplitProgramResourceProvider programResourceProvider :
          IterableUtils.flatten(featureSplits.values())) {
        programResourceProvider.setDexItemFactory(factory);
      }
      return new FeatureSplitConfiguration(featureSplits, isolatedSplits);
    }
  }
}
