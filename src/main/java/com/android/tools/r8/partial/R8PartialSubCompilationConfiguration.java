// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.Target;
import com.android.tools.r8.shaking.MissingClasses;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public abstract class R8PartialSubCompilationConfiguration {

  public final Timing timing;

  R8PartialSubCompilationConfiguration(Timing timing) {
    this.timing = timing;
  }

  public boolean isD8() {
    return false;
  }

  public R8PartialD8SubCompilationConfiguration asD8() {
    return null;
  }

  public boolean isR8() {
    return false;
  }

  public R8PartialR8SubCompilationConfiguration asR8() {
    return null;
  }

  public static class R8PartialD8SubCompilationConfiguration
      extends R8PartialSubCompilationConfiguration {

    private final Set<DexType> d8Types;
    private final Set<DexType> r8Types;

    private ClassToFeatureSplitMap classToFeatureSplitMap;
    private Collection<DexProgramClass> dexedOutputClasses;
    private Collection<DexProgramClass> desugaredOutputClasses;

    public R8PartialD8SubCompilationConfiguration(
        Set<DexType> d8Types, Set<DexType> r8Types, Timing timing) {
      super(timing);
      this.d8Types = d8Types;
      this.r8Types = r8Types;
    }

    public ClassToFeatureSplitMap getClassToFeatureSplitMap() {
      return classToFeatureSplitMap;
    }

    public Collection<DexProgramClass> getDexedOutputClasses() {
      assert dexedOutputClasses != null;
      return dexedOutputClasses;
    }

    public Collection<DexProgramClass> getDesugaredOutputClasses() {
      assert desugaredOutputClasses != null;
      return desugaredOutputClasses;
    }

    public MethodConversionOptions.Target getTargetFor(
        ProgramDefinition definition, AppView<?> appView) {
      DexType type = definition.getContextType();
      if (d8Types.contains(type)) {
        return Target.DEX;
      } else if (r8Types.contains(type)) {
        return Target.CF;
      } else {
        SyntheticItems syntheticItems = appView.getSyntheticItems();
        assert syntheticItems.isSynthetic(definition.getContextClass());
        Collection<DexType> syntheticContexts =
            syntheticItems.getSynthesizingContextTypes(definition.getContextType());
        assert syntheticContexts.size() == 1;
        DexType syntheticContext = syntheticContexts.iterator().next();
        if (d8Types.contains(syntheticContext)) {
          return Target.DEX;
        } else {
          assert r8Types.contains(syntheticContext);
          return Target.CF;
        }
      }
    }

    @Override
    public boolean isD8() {
      return true;
    }

    @Override
    public R8PartialD8SubCompilationConfiguration asD8() {
      return this;
    }

    public void writeApplication(AppView<AppInfo> appView) {
      classToFeatureSplitMap =
          appView.appInfo().getClassToFeatureSplitMap().commitSyntheticsForR8Partial(appView);
      dexedOutputClasses = new ArrayList<>();
      desugaredOutputClasses = new ArrayList<>();
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        if (getTargetFor(clazz, appView) == Target.DEX) {
          dexedOutputClasses.add(clazz);
        } else {
          desugaredOutputClasses.add(clazz);
        }
      }
    }
  }

  public static class R8PartialR8SubCompilationConfiguration
      extends R8PartialSubCompilationConfiguration {

    private ClassToFeatureSplitMap classToFeatureSplitMap;
    private Collection<DexProgramClass> dexingOutputClasses;

    public R8PartialR8SubCompilationConfiguration(
        ClassToFeatureSplitMap classToFeatureSplitMap,
        Collection<DexProgramClass> dexingOutputClasses,
        Timing timing) {
      super(timing);
      this.classToFeatureSplitMap = classToFeatureSplitMap;
      this.dexingOutputClasses = dexingOutputClasses;
    }

    public ClassToFeatureSplitMap getClassToFeatureSplitMap() {
      return classToFeatureSplitMap;
    }

    public Collection<DexProgramClass> getDexingOutputClasses() {
      assert dexingOutputClasses != null;
      return dexingOutputClasses;
    }

    public void commitDexingOutputClasses(AppView<? extends AppInfoWithClassHierarchy> appView) {
      Set<DexType> dexingOutputTypes =
          SetUtils.mapIdentityHashSet(dexingOutputClasses, DexClass::getType);
      DirectMappedDexApplication newApp =
          appView
              .app()
              .asDirect()
              .builder()
              .removeClasspathClasses(clazz -> dexingOutputTypes.contains(clazz.getType()))
              .addProgramClasses(dexingOutputClasses)
              .build();
      appView.rebuildAppInfo(newApp);
      assert amendMissingClasses(appView);
      dexingOutputClasses = null;
    }

    private boolean amendMissingClasses(AppView<? extends AppInfoWithClassHierarchy> appView) {
      if (appView.hasLiveness()) {
        MissingClasses.Builder missingClassesBuilder =
            appView.appInfo().getMissingClasses().builder();
        for (DexProgramClass clazz : dexingOutputClasses) {
          clazz.forEachImmediateSuperClassMatching(
              appView.app(),
              (supertype, superclass) -> superclass == null,
              (supertype, superclass) ->
                  missingClassesBuilder.addNewMissingClass(supertype, clazz));
        }
        appView
            .appInfoWithLiveness()
            .setMissingClasses(missingClassesBuilder.ignoreMissingClasses());
      }
      return true;
    }

    @Override
    public boolean isR8() {
      return true;
    }

    @Override
    public R8PartialR8SubCompilationConfiguration asR8() {
      return this;
    }
  }
}
