// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Threading module interface to enable non-blocking usage of R8.
 *
 * <p>The threading module has multiple implementations outside the main R8 jar. The concrete
 * implementations are loaded via reflection. Since these implementations are dynamically loaded the
 * interface they implement must be kept.
 */
@UsedByReflection(
    description = "Implementations of this interface are dynamically loaded at runtime",
    kind = KeepItemKind.CLASS_AND_MEMBERS,
    memberAccess = {MemberAccessFlags.PUBLIC})
public interface ThreadingModule {

  ExecutorService createSingleThreadedExecutorService();

  ExecutorService createThreadedExecutorService(int threadCount);

  boolean isSingleThreaded();

  <T> Future<T> submit(Callable<T> task, ExecutorService executorService) throws ExecutionException;

  <T> void awaitFutures(List<Future<T>> futures) throws ExecutionException;

  <T> void forEach(List<Future<T>> futures, Consumer<T> consumer) throws ExecutionException;

  class Loader {

    @UsedByReflection(
        description = "Prevent analysis any inlining and assumptions of the provider class names",
        constraints = {KeepConstraint.NEVER_INLINE})
    private static List<String> getProviderNames() {
      return ImmutableList.of(
          "com.android.tools.r8.threading.providers.blocking.ThreadingModuleBlockingProvider",
          "com.android.tools.r8.threading.providers.singlethreaded.ThreadingModuleSingleThreadedProvider");
    }

    @UsesReflection({
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_METHODS,
          className =
              "com.android.tools.r8.threading.providers."
                  + "blocking.ThreadingModuleBlockingProvider",
          methodName = "<init>",
          methodParameters = {}),
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_METHODS,
          className =
              "com.android.tools.r8.threading.providers."
                  + "singlethreaded.ThreadingModuleSingleThreadedProvider",
          methodName = "<init>",
          methodParameters = {})
    })
    public static ThreadingModuleProvider load() {
      for (String name : getProviderNames()) {
        try {
          Class<?> providerClass = Class.forName(name);
          return (ThreadingModuleProvider) providerClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException ignored) {
          continue;
        } catch (ReflectiveOperationException e) {
          throw new CompilationError("Failure creating provider for the threading module", e);
        }
      }
      throw new CompilationError("Failure to find a provider for the threading module");
    }
  }
}
