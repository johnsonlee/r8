// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryTypeRewriter.MachineTypeRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.timing.Timing;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

public class LibraryDesugaringOptions {

  // Contains flags describing library desugaring.
  private boolean enabled = false;
  private MachineDesugaredLibrarySpecification machineDesugaredLibrarySpecification =
      DesugaredLibrarySpecification.empty();
  private String synthesizedClassPrefix = "";
  private volatile DesugaredLibraryTypeRewriter typeRewriter;

  private ThrowingBiConsumer<Timing, DexApplication, IOException>
      loadMachineDesugaredLibrarySpecification = null;

  private final InternalOptions options;

  public LibraryDesugaringOptions(InternalOptions options) {
    this.options = options;
  }

  public void forEachPossiblyCompilerSynthesizedType(Consumer<DexType> consumer) {
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getApiGenericConversion().keySet());
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getCovariantRetarget());
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getEmulatedInterfaces().keySet());
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getEmulatedVirtualRetarget().keySet());
    forEachPossiblyCompilerSynthesizedType(
        consumer,
        machineDesugaredLibrarySpecification.getEmulatedVirtualRetargetThroughEmulatedInterface());
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getNonEmulatedVirtualRetarget());
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getRewriteDerivedTypeOnly());
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getRewriteType());
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getStaticFieldRetarget());
    forEachPossiblyCompilerSynthesizedType(
        consumer, machineDesugaredLibrarySpecification.getStaticRetarget());
  }

  private static void forEachPossiblyCompilerSynthesizedType(
      Consumer<DexType> consumer, Collection<? extends DexReference> collection) {
    collection.forEach(element -> consumer.accept(element.getContextType()));
  }

  private static void forEachPossiblyCompilerSynthesizedType(
      Consumer<DexType> consumer, Map<? extends DexReference, ? extends DexReference> rewriteMap) {
    rewriteMap.forEach(
        (from, to) -> {
          consumer.accept(from.getContextType());
          consumer.accept(to.getContextType());
        });
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

  public LibraryDesugaringOptions setMachineDesugaredLibrarySpecification(
      MachineDesugaredLibrarySpecification machineDesugaredLibrarySpecification) {
    this.enabled = true;
    this.machineDesugaredLibrarySpecification = machineDesugaredLibrarySpecification;
    return this;
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

  public void setTypeRewriter(DesugaredLibraryTypeRewriter typeRewriter) {
    this.typeRewriter = typeRewriter;
  }

  public boolean isDesugaredLibraryCompilation() {
    return machineDesugaredLibrarySpecification.isLibraryCompilation();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isL8() {
    return !synthesizedClassPrefix.isEmpty();
  }

  public boolean isCfToCfLibraryDesugaringEnabled() {
    return isEnabled() && !isLirToLirLibraryDesugaringEnabled();
  }

  public boolean isLirToLirLibraryDesugaringEnabled() {
    return isEnabled()
        && options.partialSubCompilationConfiguration != null
        && options.partialSubCompilationConfiguration.isR8();
  }

  public void resetDesugaredLibrarySpecificationForTesting() {
    enabled = false;
    loadMachineDesugaredLibrarySpecification = null;
    machineDesugaredLibrarySpecification = DesugaredLibrarySpecification.empty();
    typeRewriter = null;
  }

  public void configureDesugaredLibrary(
      DesugaredLibrarySpecification desugaredLibrarySpecification) {
    configureDesugaredLibrary(desugaredLibrarySpecification, "");
  }

  public void configureDesugaredLibrary(
      DesugaredLibrarySpecification desugaredLibrarySpecification, String synthesizedClassPrefix) {
    assert desugaredLibrarySpecification != null;
    assert synthesizedClassPrefix != null;
    if (desugaredLibrarySpecification == DesugaredLibrarySpecification.empty()
        && synthesizedClassPrefix.isEmpty()) {
      return;
    }
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

  private void setDesugaredLibrarySpecification(
      DesugaredLibrarySpecification specification, String postPrefix) {
    enabled = true;
    if (specification.isMachine()) {
      machineDesugaredLibrarySpecification = specification.asMachine();
      if (postPrefix != null) {
        machineDesugaredLibrarySpecification =
            machineDesugaredLibrarySpecification.withPostPrefix(
                options.dexItemFactory(), postPrefix);
      }
    } else {
      setMachineDesugaredLibrarySpecificationLoader(
          (timing, app) ->
              setDesugaredLibrarySpecification(
                  specification.toMachineSpecification(app, timing), postPrefix));
    }
  }

  public void setMachineDesugaredLibrarySpecificationLoader(
      ThrowingBiConsumer<Timing, DexApplication, IOException> loader) {
    this.loadMachineDesugaredLibrarySpecification = loader;
  }

  public void loadMachineDesugaredLibrarySpecification(Timing timing, DexApplication app)
      throws IOException {
    if (loadMachineDesugaredLibrarySpecification == null) {
      return;
    }
    timing.begin("Load machine specification");
    loadMachineDesugaredLibrarySpecification.accept(timing, app);
    loadMachineDesugaredLibrarySpecification = null;
    timing.end();
  }
}
