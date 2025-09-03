// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LibraryDesugaringTestConfiguration {

  private final List<StringResource> desugaredLibrarySpecificationResources;

  public static final LibraryDesugaringTestConfiguration DISABLED =
      new LibraryDesugaringTestConfiguration();

  private LibraryDesugaringTestConfiguration() {
    this.desugaredLibrarySpecificationResources = null;
  }

  private LibraryDesugaringTestConfiguration(
      List<StringResource> desugaredLibrarySpecificationResources) {
    this.desugaredLibrarySpecificationResources = desugaredLibrarySpecificationResources;
  }

  public static class Builder {

    private final List<StringResource> desugaredLibrarySpecificationResources = new ArrayList<>();

    private Builder() {}

    public Builder addDesugaredLibraryConfiguration(StringResource desugaredLibrarySpecification) {
      desugaredLibrarySpecificationResources.add(desugaredLibrarySpecification);
      return this;
    }

    public LibraryDesugaringTestConfiguration build() {
      assert !desugaredLibrarySpecificationResources.isEmpty();
      return new LibraryDesugaringTestConfiguration(desugaredLibrarySpecificationResources);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static LibraryDesugaringTestConfiguration forSpecification(Path specification) {
    return LibraryDesugaringTestConfiguration.builder()
        .addDesugaredLibraryConfiguration(StringResource.fromFile(specification))
        .build();
  }

  public boolean isEnabled() {
    return this != DISABLED;
  }

  public void configure(D8Command.Builder builder) {
    if (!isEnabled()) {
      return;
    }
    desugaredLibrarySpecificationResources.forEach(builder::addDesugaredLibraryConfiguration);
  }

  public void configure(R8Command.Builder builder) {
    if (!isEnabled()) {
      return;
    }
    desugaredLibrarySpecificationResources.forEach(builder::addDesugaredLibraryConfiguration);
  }
}
