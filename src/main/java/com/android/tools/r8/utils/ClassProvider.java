// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Represents a provider for classes loaded from different sources. */
public abstract class ClassProvider<T extends DexClass> {
  private final ClassKind<T> classKind;

  ClassProvider(ClassKind<T> classKind) {
    this.classKind = classKind;
  }

  /** The kind of the classes created by the provider. */
  final ClassKind<T> getClassKind() {
    return classKind;
  }

  /**
   * The provider uses the callback to return all the classes that might
   * be associated with the descriptor asked for.
   *
   * NOTE: the provider is not required to cache created classes and this
   * method may create a new class instance in case it is called twice for
   * the same type. For this reason it is recommended that the provider
   * user only calls this method once per any given type.
   *
   * NOTE: thread-safe.
   */
  public abstract void collectClass(DexType type, Consumer<T> classConsumer);

  /**
   * Returns all the types of classes that might be produced by this provider.
   *
   * NOTE: thread-safe.
   */
  public abstract Collection<DexType> collectTypes();

  public abstract void forceLoad(
      Consumer<T> classConsumer,
      InternalOptions options,
      Predicate<ProgramResource> predicate,
      TaskCollection<?> tasks,
      Timing timing);

  /** Create class provider for java class resource provider. */
  public static <T extends DexClass> ClassProvider<T> forClassFileResources(
      ClassKind<T> classKind, ClassFileResourceProvider provider, JarApplicationReader reader) {
    return new ClassFileResourceReader<>(classKind, provider, reader);
  }

  public FilteringClassProvider<T> without(Set<DexType> filteredTypes) {
    return new FilteringClassProvider<>(classKind, this, filteredTypes);
  }

  /** Create class provider for preloaded classes. */
  public static <T extends DexClass> ClassProvider<T> combine(
      ClassKind<T> classKind, List<ClassProvider<T>> providers) {
    if (providers.isEmpty()) {
      return null;
    } else if (providers.size() == 1) {
      return providers.get(0);
    } else {
      return new CombinedClassProvider<>(classKind, providers);
    }
  }

  private static class ClassFileResourceReader<T extends DexClass> extends ClassProvider<T> {
    private final ClassKind<T> classKind;
    private final ClassFileResourceProvider provider;
    private final JarApplicationReader reader;

    private ClassFileResourceReader(
        ClassKind<T> classKind, ClassFileResourceProvider provider, JarApplicationReader reader) {
      super(classKind);
      this.classKind = classKind;
      this.provider = provider;
      this.reader = reader;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void collectClass(DexType type, Consumer<T> classConsumer) {
      if (provider instanceof InternalClasspathOrLibraryClassProvider) {
        InternalClasspathOrLibraryClassProvider<T> internalProvider =
            (InternalClasspathOrLibraryClassProvider<T>) provider;
        T clazz = internalProvider.getClass(type);
        if (clazz != null) {
          classConsumer.accept(clazz);
        }
        return;
      }
      String descriptor = type.descriptor.toString();
      ProgramResource resource = provider.getProgramResource(descriptor);
      if (resource != null) {
        try {
          JarClassFileReader<T> classReader =
              new JarClassFileReader<>(reader, classConsumer, classKind);
          classReader.read(resource);
        } catch (ResourceException e) {
          throw new CompilationError("Failed to load class: " + descriptor, e);
        }
      }
    }

    @Override
    public Collection<DexType> collectTypes() {
      if (provider instanceof InternalClasspathOrLibraryClassProvider) {
        InternalClasspathOrLibraryClassProvider<?> internalProvider =
            (InternalClasspathOrLibraryClassProvider<?>) provider;
        return internalProvider.getTypes();
      }
      List<DexType> types = new ArrayList<>();
      for (String descriptor : provider.getClassDescriptors()) {
        types.add(reader.options.itemFactory.createType(descriptor));
      }
      return types;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void forceLoad(
        Consumer<T> classConsumer,
        InternalOptions options,
        Predicate<ProgramResource> predicate,
        TaskCollection<?> tasks,
        Timing timing) {
      if (provider instanceof InternalClasspathOrLibraryClassProvider) {
        InternalClasspathOrLibraryClassProvider<T> internalProvider =
            (InternalClasspathOrLibraryClassProvider<T>) provider;
        internalProvider.getClasses().forEach(classConsumer);
      } else {
        JarClassFileReader<T> classReader =
            new JarClassFileReader<>(reader, classConsumer, classKind);
        try {
          provider.getProgramResources(
              programResource -> {
                if (predicate.test(programResource)) {
                  tasks.submitUnchecked(
                      () -> {
                        Timing threadTiming = timing.createThreadTiming("Force load", options);
                        try {
                          classReader.read(programResource);
                        } catch (ResourceException e) {
                          throw new RuntimeException(e);
                        }
                        threadTiming.end().notifyThreadTimingFinished();
                      });
                }
              });
        } catch (Unimplemented e) {
          options.reporter.warning(
              "Class file resource provider does not support async parsing: "
                  + provider.getClass().getTypeName());
          for (DexType type : collectTypes()) {
            collectClass(type, classConsumer);
          }
        }
      }
    }

    @Override
    public String toString() {
      return "class-resource-provider(" + provider.toString() + ")";
    }
  }

  /** Class provider which ignores a list of filtered classes */
  private static class FilteringClassProvider<T extends DexClass> extends ClassProvider<T> {
    private final ClassProvider<T> provider;
    private final Set<DexType> filteredOut;

    FilteringClassProvider(
        ClassKind<T> classKind, ClassProvider<T> provider, Set<DexType> filteredOut) {
      super(classKind);
      assert !(provider instanceof FilteringClassProvider) : "Nested Filtering class providers";
      this.provider = provider;
      this.filteredOut = filteredOut;
    }

    @Override
    public void forceLoad(
        Consumer<T> classConsumer,
        InternalOptions options,
        Predicate<ProgramResource> predicate,
        TaskCollection<?> tasks,
        Timing timing) {
      provider.forceLoad(
          clazz -> {
            if (!filteredOut.contains(clazz.getType())) {
              classConsumer.accept(clazz);
            }
          },
          options,
          predicate,
          tasks,
          timing);
    }

    @Override
    public FilteringClassProvider<T> without(Set<DexType> filteredTypes) {
      ImmutableSet<DexType> newSet =
          ImmutableSet.<DexType>builder().addAll(filteredOut).addAll(filteredTypes).build();
      return new FilteringClassProvider<>(getClassKind(), provider, newSet);
    }

    @Override
    public void collectClass(DexType type, Consumer<T> classConsumer) {
      if (filteredOut.contains(type)) {
        return;
      }
      provider.collectClass(type, classConsumer);
    }

    @Override
    public Collection<DexType> collectTypes() {
      Collection<DexType> dexTypes = provider.collectTypes();
      dexTypes.removeAll(filteredOut);
      return dexTypes;
    }

    @Override
    public String toString() {
      return provider + " without " + filteredOut;
    }
  }

  private static class CombinedClassProvider<T extends DexClass> extends ClassProvider<T> {
    private final List<ClassProvider<T>> providers;

    private CombinedClassProvider(ClassKind<T> classKind, List<ClassProvider<T>> providers) {
      super(classKind);
      this.providers = providers;
    }

    @Override
    public void collectClass(DexType type, Consumer<T> classConsumer) {
      for (ClassProvider<T> provider : providers) {
        provider.collectClass(type, classConsumer);
      }
    }

    @Override
    public Collection<DexType> collectTypes() {
      Set<DexType> types = Sets.newIdentityHashSet();
      for (ClassProvider<T> provider : providers) {
        types.addAll(provider.collectTypes());
      }
      return types;
    }

    @Override
    public void forceLoad(
        Consumer<T> classConsumer,
        InternalOptions options,
        Predicate<ProgramResource> predicate,
        TaskCollection<?> tasks,
        Timing timing) {
      for (ClassProvider<T> provider : providers) {
        provider.forceLoad(classConsumer, options, predicate, tasks, timing);
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      String prefix = "combined(";
      for (ClassProvider<T> provider : providers) {
        builder.append(prefix);
        prefix = ", ";
        builder.append(provider);
      }
      return builder.append(")").toString();
    }
  }
}
