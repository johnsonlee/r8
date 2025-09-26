// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.ClasspathClassCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LibraryClassCollection;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    assert programClasses.isFullyLoaded();
    return programClasses.getAllClasses();
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
  public ProgramOrClasspathClass definitionForProgramOrClasspathClassNotOnLibrary(DexType type) {
    return null;
  }

  @Override
  public DexProgramClass programDefinitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    return programClasses.get(type);
  }

  public static class AllClasses {

    // Mapping of all types to their definitions.
    // Collections of the three different types for iteration.
    private final Map<DexType, DexProgramClass> programClasses;
    private final ImmutableMap<DexType, DexClasspathClass> classpathClasses;
    private final ImmutableMap<DexType, DexLibraryClass> libraryClasses;

    AllClasses(
        LibraryClassCollection libraryClassesLoader,
        ClasspathClassCollection classpathClassesLoader,
        Map<DexType, DexClasspathClass> synthesizedClasspathClasses,
        Map<DexType, DexProgramClass> allProgramClasses,
        InternalOptions options,
        Timing timing) {
      // Force-load library classes.
      ImmutableMap<DexType, DexLibraryClass> allLibraryClasses;
      try (Timing t0 = timing.begin("Force-load library classes")) {
        if (libraryClassesLoader != null) {
          assert libraryClassesLoader.isFullyLoaded();
          allLibraryClasses = libraryClassesLoader.getAllClassesInMap();
        } else {
          allLibraryClasses = ImmutableMap.of();
        }
      }

      // Force-load classpath classes.
      ImmutableMap<DexType, DexClasspathClass> allClasspathClasses;
      try (Timing t0 = timing.begin("Force-load classpath classes")) {
        ImmutableMap.Builder<DexType, DexClasspathClass> allClasspathClassesBuilder =
            ImmutableMap.builder();
        if (classpathClassesLoader != null) {
          assert classpathClassesLoader.isFullyLoaded();
          classpathClassesLoader.forEach(allClasspathClassesBuilder::put);
        }
        if (synthesizedClasspathClasses != null) {
          allClasspathClassesBuilder.putAll(synthesizedClasspathClasses);
        }
        allClasspathClasses = allClasspathClassesBuilder.build();
      }

      // Collect loaded classes in the precedence order library classes, program classes and
      // class path classes or program classes, classpath classes and library classes depending
      // on the configured lookup order.
      try (Timing t0 = timing.begin("Fill prioritized classes")) {
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
    }

    public static Builder builder() {
      return new Builder();
    }

    public Collection<DexProgramClass> getProgramClasses() {
      return programClasses.values();
    }

    public Collection<DexClasspathClass> getClasspathClasses() {
      return classpathClasses.values();
    }

    public ImmutableMap<DexType, DexLibraryClass> getLibraryClasses() {
      return libraryClasses;
    }

    public static class Builder {

      private LibraryClassCollection libraryClasses;
      private ClasspathClassCollection classpathClasses;
      private Map<DexType, DexClasspathClass> synthesizedClasspathClasses;
      private Map<DexType, DexProgramClass> programClasses;

      private Builder() {}

      public Builder setClasspathClasses(ClasspathClassCollection classpathClasses) {
        this.classpathClasses = classpathClasses;
        return this;
      }

      public Builder setLibraryClasses(LibraryClassCollection libraryClasses) {
        this.libraryClasses = libraryClasses;
        return this;
      }

      public Builder setProgramClasses(Map<DexType, DexProgramClass> programClasses) {
        this.programClasses = programClasses;
        return this;
      }

      public Builder setSynthesizedClasspathClasses(
          Map<DexType, DexClasspathClass> synthesizedClasspathClasses) {
        this.synthesizedClasspathClasses = synthesizedClasspathClasses;
        return this;
      }

      public Builder forceLoadNonProgramClassCollections(
          InternalOptions options, TaskCollection<?> tasks, Timing timing) {
        // When desugaring VarHandle do not read the VarHandle and MethodHandles$Lookup classes
        // from the library as they will be synthesized during desugaring.
        Predicate<ProgramResource> forceLoadPredicate =
            programResource -> {
              if (!options.shouldDesugarVarHandle()) {
                return true;
              }
              Set<String> descriptors = programResource.getClassDescriptors();
              if (descriptors.size() != 1) {
                return true;
              }
              String descriptor = descriptors.iterator().next();
              return !descriptor.equals(DexItemFactory.varHandleDescriptorString)
                  && !descriptor.equals(DexItemFactory.methodHandlesLookupDescriptorString);
            };
        if (classpathClasses != null) {
          classpathClasses.forceLoad(options, tasks, timing, alwaysTrue());
        }
        if (libraryClasses != null) {
          libraryClasses.forceLoad(options, tasks, timing, forceLoadPredicate);
        }
        return this;
      }

      public AllClasses build(InternalOptions options, Timing timing) {
        if (classpathClasses != null) {
          classpathClasses.setFullyLoaded();
        }
        if (libraryClasses != null) {
          libraryClasses.setFullyLoaded();
        }
        return new AllClasses(
            libraryClasses,
            classpathClasses,
            synthesizedClasspathClasses,
            programClasses,
            options,
            timing);
      }
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

  /** Force load all classes and return type -> class map containing all the classes. */
  public AllClasses loadAllClasses(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    TaskCollection<?> tasks = new TaskCollection<>(options, executorService);
    AllClasses.Builder allClassesBuilder =
        AllClasses.builder()
            .setClasspathClasses(classpathClasses)
            .setLibraryClasses(libraryClasses)
            .setProgramClasses(programClasses.getAllClassesInMap())
            .setSynthesizedClasspathClasses(synthesizedClasspathClasses)
            .forceLoadNonProgramClassCollections(options, tasks, timing);
    tasks.await();
    return allClassesBuilder.build(options, timing);
  }

  public static class Builder extends DexApplication.Builder<LazyLoadedDexApplication, Builder> {

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
      return new LazyLoadedDexApplication(
          proguardMap,
          flags,
          ProgramClassCollection.create(getProgramClasses(), options),
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
  public LazyLoadedDexApplication asLazy() {
    return this;
  }

  @Deprecated
  public DirectMappedDexApplication toDirectSingleThreadedForTesting() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    return toDirectForTesting(executor);
  }

  @Deprecated
  private DirectMappedDexApplication toDirectForTesting(ExecutorService executorService) {
    try (Timing t0 = timing.begin("To direct app")) {
      // As a side-effect, this will force-load all classes.
      AllClasses allClasses = loadAllClasses(executorService, timing);
      DirectMappedDexApplication.Builder builder =
          new DirectMappedDexApplication.Builder(this, allClasses);
      return builder.build();
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    }
  }

  @Override
  public String toString() {
    return "Application (" + programClasses + "; " + classpathClasses + "; " + libraryClasses
        + ")";
  }
}
