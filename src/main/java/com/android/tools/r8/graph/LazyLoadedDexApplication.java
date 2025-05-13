// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.dex.ApplicationReader.ProgramClassConflictResolver;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.ClasspathClassCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LibraryClassCollection;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LazyLoadedDexApplication extends DexApplication {

  private final ProgramClassCollection programClasses;
  private final ClasspathClassCollection classpathClasses;
  private final Map<DexType, DexClasspathClass> synthesizedClasspathClasses;
  private final LibraryClassCollection libraryClasses;
  private final List<KeepDeclaration> keepDeclarations;

  /** Constructor should only be invoked by the DexApplication.Builder. */
  private LazyLoadedDexApplication(
      ClassNameMapper proguardMap,
      DexApplicationReadFlags flags,
      ProgramClassCollection programClasses,
      ImmutableList<DataResourceProvider> dataResourceProviders,
      ClasspathClassCollection classpathClasses,
      Map<DexType, DexClasspathClass> synthesizedClasspathClasses,
      LibraryClassCollection libraryClasses,
      List<KeepDeclaration> keepDeclarations,
      InternalOptions options,
      Timing timing) {
    super(proguardMap, flags, dataResourceProviders, options, timing);
    this.programClasses = programClasses;
    this.classpathClasses = classpathClasses;
    this.synthesizedClasspathClasses = synthesizedClasspathClasses;
    this.libraryClasses = libraryClasses;
    this.keepDeclarations = keepDeclarations;
  }

  public List<KeepDeclaration> getKeepDeclarations() {
    return keepDeclarations;
  }

  @Override
  List<DexProgramClass> programClasses() {
    return programClasses.forceLoad().getAllClasses();
  }

  @Override
  public void forEachProgramType(Consumer<DexType> consumer) {
    programClasses.getAllTypes().forEach(consumer);
  }

  @Override
  public void forEachLibraryType(Consumer<DexType> consumer) {
    libraryClasses.getAllClassProviderTypes().forEach(consumer);
  }

  @Override
  public ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(DexType type) {
    ClassResolutionResult.Builder builder = ClassResolutionResult.builder();
    if (libraryClasses != null) {
      addClassToBuilderIfNotNull(libraryClasses.get(type), builder::add);
    }
    if (programClasses == null
        || !addClassToBuilderIfNotNull(programClasses.get(type), builder::add)) {
      // When looking up a type that exists both on program path and classpath, we assume the
      // program class is taken and only if not present will look at classpath.
      DexClasspathClass classpathClass;
      if (classpathClasses != null) {
        classpathClass = classpathClasses.get(type);
        addClassToBuilderIfNotNull(classpathClass, builder::add);
      } else {
        classpathClass = null;
      }

      if (synthesizedClasspathClasses != null) {
        if (classpathClass == null) {
          addClassToBuilderIfNotNull(synthesizedClasspathClasses.get(type), builder::add);
        } else {
          assert !synthesizedClasspathClasses.containsKey(type);
        }
      }
    }
    return builder.build();
  }

  private <T extends DexClass> boolean addClassToBuilderIfNotNull(T clazz, Consumer<T> adder) {
    if (clazz != null) {
      adder.accept(clazz);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public DexClass definitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    DexClass clazz = programClasses.get(type);
    if (clazz == null && classpathClasses != null) {
      clazz = classpathClasses.get(type);
    }
    if (clazz == null && synthesizedClasspathClasses != null) {
      clazz = synthesizedClasspathClasses.get(type);
    }
    if (clazz == null && libraryClasses != null) {
      clazz = libraryClasses.get(type);
    }
    return clazz;
  }

  @Override
  public DexProgramClass programDefinitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    return programClasses.get(type);
  }

  static class AllClasses {

    // Mapping of all types to their definitions.
    // Collections of the three different types for iteration.
    private final ImmutableMap<DexType, DexProgramClass> programClasses;
    private final ImmutableMap<DexType, DexClasspathClass> classpathClasses;
    private final ImmutableMap<DexType, DexLibraryClass> libraryClasses;

    @SuppressWarnings("ReferenceEquality")
    AllClasses(
        LibraryClassCollection libraryClassesLoader,
        ClasspathClassCollection classpathClassesLoader,
        Map<DexType, DexClasspathClass> synthesizedClasspathClasses,
        ProgramClassCollection programClassesLoader,
        InternalOptions options) {

      // When desugaring VarHandle do not read the VarHandle and MethodHandles$Lookup classes
      // from the library as they will be synthesized during desugaring.
      Predicate<DexType> forceLoadPredicate =
          type ->
              !(options.shouldDesugarVarHandle()
                  && (type == options.dexItemFactory().varHandleType
                      || type == options.dexItemFactory().lookupType));

      // Force-load library classes.
      ImmutableMap<DexType, DexLibraryClass> allLibraryClasses;
      if (libraryClassesLoader != null) {
        libraryClassesLoader.forceLoad(forceLoadPredicate);
        allLibraryClasses = libraryClassesLoader.getAllClassesInMap();
      } else {
        allLibraryClasses = ImmutableMap.of();
      }

      // Program classes should be fully loaded.
      assert programClassesLoader != null;
      assert programClassesLoader.isFullyLoaded();
      ImmutableMap<DexType, DexProgramClass> allProgramClasses =
          programClassesLoader.forceLoad().getAllClassesInMap();

      // Force-load classpath classes.
      ImmutableMap.Builder<DexType, DexClasspathClass> allClasspathClassesBuilder =
          ImmutableMap.builder();
      if (classpathClassesLoader != null) {
        classpathClassesLoader.forceLoad().forEach(allClasspathClassesBuilder::put);
      }
      if (synthesizedClasspathClasses != null) {
        allClasspathClassesBuilder.putAll(synthesizedClasspathClasses);
      }
      ImmutableMap<DexType, DexClasspathClass> allClasspathClasses =
          allClasspathClassesBuilder.build();

      // Collect loaded classes in the precedence order library classes, program classes and
      // class path classes or program classes, classpath classes and library classes depending
      // on the configured lookup order.
      if (options.loadAllClassDefinitions) {
        libraryClasses = allLibraryClasses;
        programClasses = allProgramClasses;
        classpathClasses =
            fillPrioritizedClasses(allClasspathClasses, programClasses::get, options);
      } else {
        programClasses = fillPrioritizedClasses(allProgramClasses, type -> null, options);
        classpathClasses =
            fillPrioritizedClasses(allClasspathClasses, programClasses::get, options);
        libraryClasses =
            fillPrioritizedClasses(
                allLibraryClasses,
                type -> {
                  DexProgramClass clazz = programClasses.get(type);
                  if (clazz != null) {
                    options.recordLibraryAndProgramDuplicate(
                        type, clazz, allLibraryClasses.get(type));
                    return clazz;
                  }
                  return classpathClasses.get(type);
                },
                options);
      }
    }

    public ImmutableMap<DexType, DexProgramClass> getProgramClasses() {
      return programClasses;
    }

    public ImmutableMap<DexType, DexClasspathClass> getClasspathClasses() {
      return classpathClasses;
    }

    public ImmutableMap<DexType, DexLibraryClass> getLibraryClasses() {
      return libraryClasses;
    }
  }

  private static <T extends DexClass> ImmutableMap<DexType, T> fillPrioritizedClasses(
      Map<DexType, T> classCollection,
      Function<DexType, DexClass> getExisting,
      InternalOptions options) {
    if (classCollection != null) {
      Set<DexClass> javaLibraryOverride = Sets.newIdentityHashSet();
      ImmutableMap.Builder<DexType, T> builder = ImmutableMap.builder();
      classCollection.forEach(
          (type, clazz) -> {
            DexClass other = getExisting.apply(type);
            if (other == null) {
              builder.put(type, clazz);
            } else if (type.getPackageName().startsWith("java.")
                && (clazz.isLibraryClass() || other.isLibraryClass())) {
              javaLibraryOverride.add(clazz.isLibraryClass() ? other : clazz);
            }
          });
      if (!javaLibraryOverride.isEmpty()) {
        warnJavaLibraryOverride(options, javaLibraryOverride);
      }
      return builder.build();
    } else {
      return ImmutableMap.of();
    }
  }

  private static void warnJavaLibraryOverride(
      InternalOptions options, Set<DexClass> javaLibraryOverride) {
    if (options.ignoreJavaLibraryOverride) {
      return;
    }
    String joined =
        javaLibraryOverride.stream()
            .sorted(Comparator.comparing(DexClass::getType))
            .map(clazz -> clazz.toSourceString() + " (origin: " + clazz.getOrigin() + ")")
            .collect(Collectors.joining(", "));
    String message =
        "The following library types, prefixed by 'java.',"
            + " are present both as library and non library classes: "
            + joined
            + ". Library classes will be ignored.";
    options.reporter.warning(message);
  }

  /**
   * Force load all classes and return type -> class map containing all the classes.
   */
  public AllClasses loadAllClasses() {
    return new AllClasses(
        libraryClasses, classpathClasses, synthesizedClasspathClasses, programClasses, options);
  }

  public static class Builder extends DexApplication.Builder<Builder> {

    private ClasspathClassCollection classpathClasses;
    private Map<DexType, DexClasspathClass> synthesizedClasspathClasses;
    private LibraryClassCollection libraryClasses;
    private List<KeepDeclaration> keepDeclarations = Collections.emptyList();

    Builder(InternalOptions options, Timing timing) {
      super(options, timing);
      this.classpathClasses = ClasspathClassCollection.empty();
      this.synthesizedClasspathClasses = null;
      this.libraryClasses = LibraryClassCollection.empty();
    }

    private Builder(LazyLoadedDexApplication application) {
      super(application);
      this.classpathClasses = application.classpathClasses;
      this.synthesizedClasspathClasses = application.synthesizedClasspathClasses;
      this.libraryClasses = application.libraryClasses;
    }

    @Override
    Builder self() {
      return this;
    }

    public Builder setClasspathClassCollection(ClasspathClassCollection classes) {
      this.classpathClasses = classes;
      return this;
    }

    public Builder setLibraryClassCollection(LibraryClassCollection classes) {
      this.libraryClasses = classes;
      return this;
    }

    public Builder setKeepDeclarations(List<KeepDeclaration> declarations) {
      this.keepDeclarations = declarations;
      return this;
    }

    @Override
    public void addProgramClassPotentiallyOverridingNonProgramClass(DexProgramClass clazz) {
      addProgramClass(clazz);
      classpathClasses.clearType(clazz.type);
      libraryClasses.clearType(clazz.type);
    }

    @Override
    public Builder addClasspathClass(DexClasspathClass clazz) {
      if (synthesizedClasspathClasses == null) {
        synthesizedClasspathClasses = new IdentityHashMap<>();
      }
      assert classpathClasses.get(clazz.getType()) == null;
      assert !synthesizedClasspathClasses.containsKey(clazz.getType());
      synthesizedClasspathClasses.put(clazz.getType(), clazz);
      return this;
    }

    @Override
    public LazyLoadedDexApplication build() {
      ProgramClassConflictResolver resolver =
          options.programClassConflictResolver == null
              ? ProgramClassCollection.defaultConflictResolver(options.reporter)
              : options.programClassConflictResolver;
      return new LazyLoadedDexApplication(
          proguardMap,
          flags,
          ProgramClassCollection.create(getProgramClasses(), resolver),
          ImmutableList.copyOf(dataResourceProviders),
          classpathClasses,
          synthesizedClasspathClasses,
          libraryClasses,
          keepDeclarations,
          options,
          timing);
    }
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  @Override
  public DirectMappedDexApplication toDirect() {
    return new DirectMappedDexApplication.Builder(this).build().asDirect();
  }

  @Override
  public boolean isDirect() {
    return false;
  }

  @Override
  public String toString() {
    return "Application (" + programClasses + "; " + classpathClasses + "; " + libraryClasses
        + ")";
  }
}
