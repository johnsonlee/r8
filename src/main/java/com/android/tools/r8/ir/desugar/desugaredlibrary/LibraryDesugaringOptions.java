// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryTypeRewriter.MachineTypeRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;

public class LibraryDesugaringOptions {

  // Contains flags describing library desugaring.
  private MachineDesugaredLibrarySpecification machineDesugaredLibrarySpecification =
      DesugaredLibrarySpecification.empty();
  private String synthesizedClassPrefix = "";
  private volatile DesugaredLibraryTypeRewriter typeRewriter;

  private final InternalOptions options;

  public LibraryDesugaringOptions(InternalOptions options) {
    this.options = options;
  }

  public boolean hasIdentifier() {
    return machineDesugaredLibrarySpecification.getIdentifier() != null;
  }

  public String getIdentifier() {
    return machineDesugaredLibrarySpecification.getIdentifier();
  }

  public MachineDesugaredLibrarySpecification getMachineDesugaredLibrarySpecification() {
    return machineDesugaredLibrarySpecification;
  }

  public void setMachineDesugaredLibrarySpecificationForTesting(
      MachineDesugaredLibrarySpecification machineDesugaredLibrarySpecificationForTesting) {
    this.machineDesugaredLibrarySpecification = machineDesugaredLibrarySpecificationForTesting;
  }

  public String getSynthesizedClassPrefix() {
    return synthesizedClassPrefix;
  }

  public DesugaredLibraryTypeRewriter getTypeRewriter() {
    if (typeRewriter == null) {
      synchronized (this) {
        if (typeRewriter == null) {
          typeRewriter =
              machineDesugaredLibrarySpecification.requiresTypeRewriting()
                  ? new MachineTypeRewriter(
                      options.dexItemFactory(), machineDesugaredLibrarySpecification)
                  : DesugaredLibraryTypeRewriter.empty();
        }
      }
    }
    return typeRewriter;
  }

  public boolean hasTypeRewriter() {
    return getTypeRewriter().isRewriting();
  }

  public boolean isDesugaredLibraryCompilation() {
    return machineDesugaredLibrarySpecification.isLibraryCompilation();
  }

  public boolean isL8() {
    return !synthesizedClassPrefix.isEmpty();
  }

  public void resetDesugaredLibrarySpecificationForTesting() {
    loadMachineDesugaredLibrarySpecification = null;
    machineDesugaredLibrarySpecification = DesugaredLibrarySpecification.empty();
    typeRewriter = null;
  }

  public void configureDesugaredLibrary(
      DesugaredLibrarySpecification desugaredLibrarySpecification, String synthesizedClassPrefix) {
    assert synthesizedClassPrefix != null;
    assert desugaredLibrarySpecification != null;
    String prefix =
        synthesizedClassPrefix.isEmpty()
            ? System.getProperty("com.android.tools.r8.synthesizedClassPrefix", "")
            : synthesizedClassPrefix;
    String postPrefix = System.getProperty("com.android.tools.r8.desugaredLibraryPostPrefix", null);
    setDesugaredLibrarySpecification(desugaredLibrarySpecification, postPrefix);
    String post =
        postPrefix == null ? "" : DescriptorUtils.getPackageBinaryNameFromJavaType(postPrefix);
    this.synthesizedClassPrefix = prefix.isEmpty() ? "" : prefix + post;
  }

  public void setDesugaredLibrarySpecification(DesugaredLibrarySpecification specification) {
    setDesugaredLibrarySpecification(specification, null);
  }

  private void setDesugaredLibrarySpecification(
      DesugaredLibrarySpecification specification, String postPrefix) {
    if (specification.isEmpty()) {
      return;
    }
    loadMachineDesugaredLibrarySpecification =
        (timing, app) -> {
          MachineDesugaredLibrarySpecification machineSpec =
              specification.toMachineSpecification(app, timing);
          machineDesugaredLibrarySpecification =
              postPrefix != null
                  ? machineSpec.withPostPrefix(options.dexItemFactory(), postPrefix)
                  : machineSpec;
        };
  }

  private ThrowingBiConsumer<Timing, DexApplication, IOException>
      loadMachineDesugaredLibrarySpecification = null;

  public void loadMachineDesugaredLibrarySpecification(Timing timing, DexApplication app)
      throws IOException {
    if (loadMachineDesugaredLibrarySpecification == null) {
      return;
    }
    timing.begin("Load machine specification");
    loadMachineDesugaredLibrarySpecification.accept(timing, app);
    timing.end();
  }
}
