// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMember;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.Set;

public class KotlinMetadataMembersTracker {

  private int count;
  // Only used for asserting equality during local testing.
  private final Set<DexMember<?, ?>> references;

  public KotlinMetadataMembersTracker(AppView<?> appView) {
    references = appView.options().testing.enableTestAssertions ? Sets.newIdentityHashSet() : null;
  }

  public void add(DexMember<?, ?> reference) {
    count += 1;
    if (references != null) {
      references.add(reference);
    }
  }

  public boolean isEqual(KotlinMetadataMembersTracker tracker, AppView<?> appView) {
    if (count != tracker.count) {
      return false;
    }
    assert verifyReferencesDiff(tracker, appView);
    return true;
  }

  private boolean verifyReferencesDiff(KotlinMetadataMembersTracker tracker, AppView<?> appView) {
    if (references != null) {
      assert tracker.references != null;
      assert references.size() == tracker.references.size();
      SetView<DexMember<?, ?>> diffComparedToRewritten =
          Sets.difference(references, tracker.references);
      if (!diffComparedToRewritten.isEmpty()) {
        SetView<DexMember<?, ?>> diffComparedToOriginal =
            Sets.difference(tracker.references, references);
        diffComparedToRewritten.forEach(
            diff -> {
              DexMember<?, ?> rewrittenReference =
                  appView
                      .graphLens()
                      .getRenamedMemberSignature(diff, appView.getKotlinMetadataLens());
              assert diffComparedToOriginal.contains(rewrittenReference);
            });
      }
    }
    return true;
  }
}
