// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.util.Collection;
import java.util.Collections;

public abstract class R8PartialSubCompilationConfiguration {

  public final Timing timing;

  R8PartialSubCompilationConfiguration(Timing timing) {
    this.timing = timing;
  }

  public boolean isR8() {
    return false;
  }

  public R8PartialR8SubCompilationConfiguration asR8SubCompilationConfiguration() {
    return null;
  }

  /** Returns true if normal writing should be aborted. */
  public void writeApplication(Collection<DexProgramClass> outputClasses, InternalOptions options) {
    assert false;
  }

  public static class R8PartialD8DexSubCompilationConfiguration
      extends R8PartialSubCompilationConfiguration {

    private Collection<DexProgramClass> outputClasses;

    public R8PartialD8DexSubCompilationConfiguration(Timing timing) {
      super(timing);
    }

    public Collection<DexProgramClass> getOutputClasses() {
      assert outputClasses != null;
      return outputClasses;
    }

    @Override
    public void writeApplication(
        Collection<DexProgramClass> outputClasses, InternalOptions options) {
      this.outputClasses = outputClasses;
    }
  }

  public static class R8PartialD8DesugarSubCompilationConfiguration
      extends R8PartialSubCompilationConfiguration {

    private Collection<DexProgramClass> outputClasses;

    public R8PartialD8DesugarSubCompilationConfiguration(Timing timing) {
      super(timing);
    }

    public Collection<DexProgramClass> getOutputClasses() {
      assert outputClasses != null;
      return outputClasses;
    }

    @Override
    public void writeApplication(
        Collection<DexProgramClass> outputClasses, InternalOptions options) {
      this.outputClasses = outputClasses;
    }
  }

  public static class R8PartialR8SubCompilationConfiguration
      extends R8PartialSubCompilationConfiguration {

    private Collection<DexProgramClass> desugaringOutputClasses;
    private Collection<DexProgramClass> dexingOutputClasses;

    public R8PartialR8SubCompilationConfiguration(
        Collection<DexProgramClass> desugaringOutputClasses,
        Collection<DexProgramClass> dexingOutputClasses,
        Timing timing) {
      super(timing);
      this.desugaringOutputClasses = desugaringOutputClasses;
      this.dexingOutputClasses = dexingOutputClasses;
    }

    public Collection<DexProgramClass> getDexingOutputClasses() {
      assert dexingOutputClasses != null;
      return dexingOutputClasses;
    }

    public DirectMappedDexApplication commitDesugaringOutputClasses(
        DirectMappedDexApplication app) {
      assert app.classes().isEmpty();
      DirectMappedDexApplication newApp =
          app.builder().addProgramClasses(desugaringOutputClasses).build();
      desugaringOutputClasses = null;
      return newApp;
    }

    public void commitDexingOutputClasses(AppView<AppInfoWithClassHierarchy> appView) {
      DirectMappedDexApplication newApp =
          appView
              .app()
              .asDirect()
              .builder()
              // TODO(b/390570711): This should not clear all classpath classes, only the ones we
              //  inject into program.
              .replaceClasspathClasses(Collections.emptyList())
              .addProgramClasses(dexingOutputClasses)
              .build();
      appView.setAppInfo(appView.appInfo().rebuildWithClassHierarchy(newApp));
      dexingOutputClasses = null;
    }

    @Override
    public boolean isR8() {
      return true;
    }

    @Override
    public R8PartialR8SubCompilationConfiguration asR8SubCompilationConfiguration() {
      return this;
    }
  }
}
