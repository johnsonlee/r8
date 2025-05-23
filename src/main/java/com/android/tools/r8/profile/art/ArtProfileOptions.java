// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyOrDefault;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

public class ArtProfileOptions {

  public static final String COMPLETENESS_PROPERTY_KEY =
      "com.android.tools.r8.artprofilerewritingcompletenesscheck";

  private Collection<ArtProfileForRewriting> artProfilesForRewriting = Collections.emptyList();
  private boolean enableCompletenessCheckForTesting =
      parseSystemPropertyOrDefault(COMPLETENESS_PROPERTY_KEY, false);
  private boolean enableNopCheckForTesting;
  private boolean hasReadArtProfileProviders = false;
  private boolean allowReadingEmptyArtProfileProvidersMultipleTimesForTesting = false;

  private final InternalOptions options;

  private String nopCheckForTestingHashCode;

  public ArtProfileOptions(InternalOptions options) {
    this.options = options;
  }

  // Constructor for D8 in R8 partial.
  public ArtProfileOptions(InternalOptions options, ArtProfileOptions artProfileOptions) {
    this(options);
    this.artProfilesForRewriting = artProfileOptions.artProfilesForRewriting;
    this.enableCompletenessCheckForTesting = artProfileOptions.enableCompletenessCheckForTesting;
    this.enableNopCheckForTesting = artProfileOptions.enableNopCheckForTesting;
    this.hasReadArtProfileProviders = artProfileOptions.hasReadArtProfileProviders;
    this.allowReadingEmptyArtProfileProvidersMultipleTimesForTesting =
        artProfileOptions.allowReadingEmptyArtProfileProvidersMultipleTimesForTesting;
  }

  public Collection<ArtProfileForRewriting> getArtProfilesForRewriting() {
    return artProfilesForRewriting;
  }

  public Collection<ArtProfileProvider> getArtProfileProviders() {
    assert !hasReadArtProfileProviders
        || (allowReadingEmptyArtProfileProvidersMultipleTimesForTesting
            && artProfilesForRewriting.isEmpty());
    assert options.partialSubCompilationConfiguration == null
        || options.partialSubCompilationConfiguration.isD8();
    hasReadArtProfileProviders = true;
    return ListUtils.map(artProfilesForRewriting, ArtProfileForRewriting::getArtProfileProvider);
  }

  public InternalOptions getOptions() {
    return options;
  }

  public boolean isCompletenessCheckForTestingEnabled() {
    return enableCompletenessCheckForTesting
        && !options.getLibraryDesugaringOptions().isDesugaredLibraryCompilation()
        && !options.getStartupOptions().isStartupCompletenessCheckForTestingEnabled()
        && !options.getInstrumentationOptions().isInstrumentationEnabled();
  }

  public boolean isNopCheckForTestingEnabled() {
    return enableNopCheckForTesting;
  }

  public boolean isIncludingApiReferenceStubs() {
    // We only include API reference stubs in the residual ART profiles for completeness testing.
    // This is because the API reference stubs should never be used at runtime except for
    // verification, meaning there should be no need to AOT them.
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingBackportedClasses() {
    // Similar to isIncludingVarHandleClasses().
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingConstantDynamicClass() {
    // Similar to isIncludingVarHandleClasses().
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingDesugaredLibraryRetargeterForwardingMethodsUnconditionally() {
    // TODO(b/265729283): If we get as input the profile for the desugared library, maybe we can
    //  tell if the method targeted by the forwarding method is in the profile, e.g.:
    //  java.time.Instant java.util.DesugarDate.toInstant(java.util.Date).
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingThrowingMethods() {
    // The throw methods we insert should generally be dead a runtime, so no need for them to be
    // optimized.
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingVarHandleClasses() {
    // We only include var handle classes in the residual ART profiles for completeness testing,
    // since the classes synthesized by var handle desugaring are fairly large and may not be that
    // important for runtime performance.
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingLambdaMethodAnnotation() {
    // We only include the LambdaMethod annotaiton in the residual ART profiles for completeness
    // testing. It is an annotation which is not relevant for runtime performance.
    return enableCompletenessCheckForTesting;
  }

  public ArtProfileOptions setAllowReadingEmptyArtProfileProvidersMultipleTimesForTesting(
      boolean allowReadingEmptyArtProfileProvidersMultipleTimesForTesting) {
    this.allowReadingEmptyArtProfileProvidersMultipleTimesForTesting =
        allowReadingEmptyArtProfileProvidersMultipleTimesForTesting;
    return this;
  }

  public ArtProfileOptions setArtProfilesForRewriting(Collection<ArtProfileForRewriting> inputs) {
    this.artProfilesForRewriting = inputs;
    return this;
  }

  public ArtProfileOptions setEnableCompletenessCheckForTesting(
      boolean enableCompletenessCheckForTesting) {
    this.enableCompletenessCheckForTesting = enableCompletenessCheckForTesting;
    return this;
  }

  public ArtProfileOptions setEnableNopCheckForTesting() {
    this.enableNopCheckForTesting = true;
    return this;
  }

  public boolean setNopCheckForTestingHashCode(AppInfo appInfo) {
    if (nopCheckForTestingHashCode != null) {
      String hashCode = computeNopCheckForTestingHashCode(appInfo);
      assert hashCode.equals(nopCheckForTestingHashCode);
    } else {
      nopCheckForTestingHashCode = computeNopCheckForTestingHashCode(appInfo);
    }
    return true;
  }

  private static String computeNopCheckForTestingHashCode(AppInfo appInfo) {
    Hasher hasher = Hashing.sha256().newHasher();
    for (DexProgramClass clazz : appInfo.classesWithDeterministicOrder()) {
      hasher.putString(clazz.getType().toDescriptorString(), StandardCharsets.UTF_8);
      for (DexEncodedMethod method : clazz.methods()) {
        hasher.putString(method.getReference().toSmaliString(), StandardCharsets.UTF_8);
      }
    }
    return hasher.hash().toString();
  }

  public boolean verifyHasNopCheckForTestingHashCode() {
    assert nopCheckForTestingHashCode != null;
    return true;
  }
}
