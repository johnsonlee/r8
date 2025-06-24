// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.SourceFileEnvironment;
import com.android.tools.r8.SourceFileProvider;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.InternalOptions;

/** Computes the source file provider based on the proguard configuration if none is set. */
public class SourceFileRewriter {

  public static SourceFileProvider computeSourceFileProvider(
      SourceFileProvider provider, InternalOptions options) {
    if (provider != null) {
      return provider;
    }
    String renaming = getRenameSourceFileAttribute(options);
    if (renaming != null) {
      return rewriteTo(renaming, isDefaultOrEmpty(renaming, options));
    }
    if (options.getTestingOptions().enableMapIdInSourceFile) {
      if (!options.forceProguardCompatibility
          || !options.getProguardConfiguration().getKeepAttributes().sourceFile) {
        if (options.isMinifying() || options.isOptimizing()) {
          return env ->
              env.getMapId() != null
                  ? "r8-map-id-" + env.getMapId()
                  : options.dexItemFactory().defaultSourceFileAttributeString;
        }
      }
    }
    if (!options.getProguardConfiguration().getKeepAttributes().sourceFile) {
      return rewriteToDefaultSourceFile(options.dexItemFactory());
    }
    return null;
  }

  private static String getRenameSourceFileAttribute(InternalOptions options) {
    // Only apply -renamesourcefileattribute if the SourceFile attribute is kept.
    if (!options.getProguardConfiguration().getKeepAttributes().sourceFile) {
      return null;
    }
    // Compatibility mode will only apply -renamesourcefileattribute when minifying names.
    if (options.forceProguardCompatibility && !options.isMinifying()) {
      return null;
    }
    return options.getProguardConfiguration().getRenameSourceFileAttribute();
  }

  public static boolean isDefaultOrEmpty(String sourceFile, InternalOptions options) {
    return sourceFile.isEmpty()
        || options.dexItemFactory().defaultSourceFileAttributeString.equals(sourceFile);
  }

  private static SourceFileProvider rewriteToDefaultSourceFile(DexItemFactory factory) {
    return rewriteTo(factory.defaultSourceFileAttributeString, true);
  }

  private static SourceFileProvider rewriteTo(String renaming, boolean allowDiscard) {
    return new SourceFileProvider() {
      @Override
      public String get(SourceFileEnvironment environment) {
        return renaming;
      }

      @Override
      public boolean allowDiscardingSourceFile() {
        return allowDiscard;
      }
    };
  }
}
