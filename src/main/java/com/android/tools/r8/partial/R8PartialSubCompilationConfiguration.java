// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.Target;
import com.android.tools.r8.ir.desugar.desugaredlibrary.LibraryDesugaringOptions;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileCollection;
import com.android.tools.r8.profile.art.ArtProfileMethodRule;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final LibraryDesugaringOptions libraryDesugaringOptions;

    private ArtProfileCollection artProfiles;
    private ClassToFeatureSplitMap classToFeatureSplitMap;
    private Collection<DexProgramClass> dexedOutputClasses;
    private Collection<DexProgramClass> desugaredOutputClasses;
    private Collection<DexClasspathClass> outputClasspathClasses;
    private Collection<DexLibraryClass> outputLibraryClasses;
    private StartupProfile startupProfile;

    public R8PartialD8SubCompilationConfiguration(
        Set<DexType> d8Types,
        Set<DexType> r8Types,
        LibraryDesugaringOptions libraryDesugaringOptions,
        Timing timing) {
      super(timing);
      this.d8Types = d8Types;
      this.r8Types = r8Types;
      this.libraryDesugaringOptions = libraryDesugaringOptions;
    }

    public ArtProfileCollection getArtProfiles() {
      assert artProfiles != null;
      return artProfiles;
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

    public LibraryDesugaringOptions getLibraryDesugaringOptions() {
      return libraryDesugaringOptions;
    }

    public Collection<DexClasspathClass> getOutputClasspathClasses() {
      assert outputClasspathClasses != null;
      return outputClasspathClasses;
    }

    public Collection<DexLibraryClass> getOutputLibraryClasses() {
      assert outputLibraryClasses != null;
      return outputLibraryClasses;
    }

    public StartupProfile getStartupProfile() {
      assert startupProfile != null;
      return startupProfile;
    }

    public MethodConversionOptions.Target getTargetFor(
        ProgramDefinition definition, AppView<?> appView) {
      DexType type = definition.getContextType();
      if (d8Types.contains(type)) {
        return Target.LIR;
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
          return Target.LIR;
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
      artProfiles = appView.getArtProfileCollection().transformForR8Partial(appView);
      classToFeatureSplitMap =
          appView.appInfo().getClassToFeatureSplitMap().commitSyntheticsForR8Partial(appView);
      dexedOutputClasses = new ArrayList<>();
      desugaredOutputClasses = new ArrayList<>();
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        if (getTargetFor(clazz, appView) == Target.LIR) {
          dexedOutputClasses.add(clazz);
        } else {
          desugaredOutputClasses.add(clazz);
        }
      }
      DirectMappedDexApplication app = appView.app().toDirect();
      outputClasspathClasses = app.classpathClasses();
      outputLibraryClasses = app.libraryClasses();
      startupProfile = appView.getStartupProfile();
    }
  }

  public static class R8PartialR8SubCompilationConfiguration
      extends R8PartialSubCompilationConfiguration {

    private ArtProfileCollection artProfiles;
    private ClassToFeatureSplitMap classToFeatureSplitMap;
    private Map<DexType, DexProgramClass> dexingOutputClasses;
    private List<KeepDeclaration> keepDeclarations;
    private StartupProfile startupProfile;

    // Stores the missing class references from the D8 compilation unit in R8 partial.
    // We use this to ensure that calling AppInfoWithLiveness#definitionFor does not fail
    // when looking up a missing class type from the D8 part (which happens during library
    // desugaring).
    //
    // Always empty when assertions are disabled.
    public final Set<DexType> d8MissingClasses = ConcurrentHashMap.newKeySet();

    public R8PartialR8SubCompilationConfiguration(
        ArtProfileCollection artProfiles,
        ClassToFeatureSplitMap classToFeatureSplitMap,
        Collection<DexProgramClass> dexingOutputClasses,
        List<KeepDeclaration> keepDeclarations,
        StartupProfile startupProfile,
        Timing timing) {
      super(timing);
      this.artProfiles = artProfiles;
      this.classToFeatureSplitMap = classToFeatureSplitMap;
      this.dexingOutputClasses =
          MapUtils.transform(dexingOutputClasses, IdentityHashMap::new, DexClass::getType);
      this.keepDeclarations = keepDeclarations;
      this.startupProfile = startupProfile;
    }

    public ArtProfileCollection getArtProfiles() {
      return artProfiles;
    }

    public ClassToFeatureSplitMap getClassToFeatureSplitMap() {
      return classToFeatureSplitMap;
    }

    public Collection<DexProgramClass> getDexingOutputClasses() {
      assert dexingOutputClasses != null;
      return dexingOutputClasses.values();
    }

    public List<KeepDeclaration> getAndClearKeepDeclarations() {
      List<KeepDeclaration> result = keepDeclarations;
      assert result != null;
      keepDeclarations = null;
      return result;
    }

    public StartupProfile getStartupProfile() {
      assert startupProfile != null;
      return startupProfile;
    }

    public void amendCompleteArtProfile(ArtProfile.Builder artProfileBuilder) {
      List<DexProgramClass> dexingOutputClassesSorted =
          ListUtils.sort(dexingOutputClasses.values(), Comparator.comparing(DexClass::getType));
      for (DexProgramClass clazz : dexingOutputClassesSorted) {
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
    }

    public void commitDexingOutputClasses(AppView<? extends AppInfoWithClassHierarchy> appView) {
      DirectMappedDexApplication newApp =
          appView
              .app()
              .asDirect()
              .builder()
              .removeClasspathClasses(clazz -> dexingOutputClasses.containsKey(clazz.getType()))
              .addProgramClasses(dexingOutputClasses.values())
              .build();
      appView.rebuildAppInfo(newApp);
    }

    public void uncommitDexingOutputClasses(AppView<? extends AppInfoWithClassHierarchy> appView) {
      List<DexClasspathClass> newClasspathClasses =
          ListUtils.sort(
              DexClasspathClass.toClasspathClasses(dexingOutputClasses.values()).values(),
              Comparator.comparing(DexClass::getType));
      DirectMappedDexApplication newApp =
          appView
              .app()
              .asDirect()
              .builder()
              .removeProgramClasses(clazz -> dexingOutputClasses.containsKey(clazz.getType()))
              .addClasspathClasses(newClasspathClasses)
              .build();
      appView.rebuildAppInfo(newApp);
    }

    public boolean hasD8DefinitionFor(DexReference reference) {
      if (reference.isDexType()) {
        return dexingOutputClasses.containsKey(reference.asDexType());
      } else {
        DexMember<?, ?> member = reference.asDexMember();
        DexProgramClass holder = dexingOutputClasses.get(member.getHolderType());
        return member.isDefinedOnClass(holder);
      }
    }

    public boolean isD8Definition(ProgramDefinition definition) {
      return hasD8DefinitionFor(definition.getReference());
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
