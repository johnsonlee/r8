// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.profile.art.ArtProfileCollection;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import java.util.Collection;

public class R8PartialD8Result {

  private final ArtProfileCollection artProfiles;
  private final ClassToFeatureSplitMap classToFeatureSplitMap;
  private final Collection<DexProgramClass> dexedClasses;
  private final Collection<DexProgramClass> desugaredClasses;
  private final StartupProfile startupProfile;

  public R8PartialD8Result(
      ArtProfileCollection artProfiles,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      Collection<DexProgramClass> dexedClasses,
      Collection<DexProgramClass> desugaredClasses,
      StartupProfile startupProfile) {
    this.artProfiles = artProfiles;
    this.classToFeatureSplitMap = classToFeatureSplitMap;
    this.dexedClasses = dexedClasses;
    this.desugaredClasses = desugaredClasses;
    this.startupProfile = startupProfile;
  }

  public ArtProfileCollection getArtProfiles() {
    return artProfiles;
  }

  public ClassToFeatureSplitMap getClassToFeatureSplitMap() {
    return classToFeatureSplitMap;
  }

  public Collection<DexProgramClass> getDexedClasses() {
    return dexedClasses;
  }

  public Collection<DexProgramClass> getDesugaredClasses() {
    return desugaredClasses;
  }

  public StartupProfile getStartupProfile() {
    return startupProfile;
  }
}
