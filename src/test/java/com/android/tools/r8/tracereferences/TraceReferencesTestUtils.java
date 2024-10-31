// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.references.ClassReference;
import java.util.Set;
import java.util.stream.Collectors;

public class TraceReferencesTestUtils {

  public static Set<ClassReference> collectMissingClassReferences(
      TestDiagnosticMessages diagnostic) {
    return diagnostic
        .assertSingleErrorDiagnosticType(MissingDefinitionsDiagnostic.class)
        .getMissingDefinitions()
        .stream()
        .map(info -> info.asMissingClass().getClassReference())
        .collect(Collectors.toSet());
  }

  public static Set<ClassReference> filterAndCollectMissingClassReferences(
      TestDiagnosticMessages diagnostic) {
    return diagnostic
        .assertSingleErrorDiagnosticType(MissingDefinitionsDiagnostic.class)
        .getMissingDefinitions()
        .stream()
        .filter(MissingDefinitionInfo::isMissingClass)
        .map(info -> info.asMissingClass().getClassReference())
        .collect(Collectors.toSet());
  }
}
