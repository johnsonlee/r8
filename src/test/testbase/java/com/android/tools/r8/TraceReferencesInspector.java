// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer;
import java.util.HashSet;
import java.util.Set;

public class TraceReferencesInspector implements TraceReferencesConsumer {

  private final Set<TracedClass> classes = new HashSet<>();
  private final Set<TracedField> fields = new HashSet<>();
  private final Set<TracedMethod> methods = new HashSet<>();
  private final Set<PackageReference> packages = new HashSet<>();

  @Override
  public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
    classes.add(tracedClass);
  }

  @Override
  public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
    fields.add(tracedField);
  }

  @Override
  public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
    methods.add(tracedMethod);
  }

  @Override
  public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
    packages.add(pkg);
  }

  public Set<PackageReference> getPackages() {
    return packages;
  }
}
