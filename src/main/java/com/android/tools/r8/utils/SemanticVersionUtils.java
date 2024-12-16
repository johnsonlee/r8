// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.Version;
import com.google.common.base.Suppliers;
import java.util.function.Supplier;

public class SemanticVersionUtils {

  public static Supplier<SemanticVersion> compilerVersionSemanticVersionSupplier(
      SemanticVersion forceCompilerVersion,
      String artificialMaxVersionWarningInfo,
      Reporter reporter) {
    return Suppliers.memoize(
        () -> {
          SemanticVersion compilerVersion =
              forceCompilerVersion == null
                  ? SemanticVersion.create(
                      Version.getMajorVersion(),
                      Version.getMinorVersion(),
                      Version.getPatchVersion())
                  : forceCompilerVersion;
          if (compilerVersion.getMajor() < 0) {
            compilerVersion =
                SemanticVersion.create(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
            reporter.warning(
                "Running R8 version "
                    + Version.getVersionString()
                    + ", which cannot be represented as a semantic version."
                    + (artificialMaxVersionWarningInfo == null
                        ? ""
                        : (" " + artificialMaxVersionWarningInfo)));
          }
          return compilerVersion;
        });
  }
}
