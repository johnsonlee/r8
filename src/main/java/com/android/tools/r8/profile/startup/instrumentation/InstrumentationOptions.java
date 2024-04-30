// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.instrumentation;

import static com.android.tools.r8.utils.SystemPropertyUtils.getSystemPropertyForDevelopment;
import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyForDevelopmentOrDefault;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Set;

public class InstrumentationOptions {

  /** Set of method references where all calls to the exact method reference should print. */
  private Set<DexMethod> callSitesToInstrument = Collections.emptySet();

  /**
   * When enabled, each method will be instrumented to notify the startup InstrumentationServer that
   * it has been executed.
   *
   * <p>This will also inject the startup runtime library (i.e., the InstrumentationServer) into the
   * app.
   */
  private boolean enableExecutedClassesAndMethodsInstrumentation =
      parseSystemPropertyForDevelopmentOrDefault(
          "com.android.tools.r8.instrumentation.executedclassesandmethods", false);

  /**
   * Specifies the synthetic context of the startup runtime library. When this is set, the startup
   * runtime library will only be injected into the app when the synthetic context is in the
   * program. This can be used to avoid that the startup runtime library is injected multiple times
   * in presence of separate compilation.
   *
   * <p>Example synthetic context: "app.tivi.home.MainActivity".
   *
   * <p>Note that this is only meaningful when one or more instrumentations are enabled.
   */
  private String syntheticServerContext =
      getSystemPropertyForDevelopment(
          "com.android.tools.r8.instrumentation.syntheticservercontext");

  /**
   * Specifies the logcat tag that should be used by the InstrumentationServer when logging events.
   *
   * <p>When a logcat tag is not specified, the InstrumentationServer will not print events to
   * logcat. Instead, the startup events must be obtained by requesting the InstrumentationServer to
   * write the events to a file.
   */
  private String tag = getSystemPropertyForDevelopment("com.android.tools.r8.instrumentation.tag");

  public InstrumentationOptions(InternalOptions options) {
    String callSitesToInstrumentString =
        getSystemPropertyForDevelopment("com.android.tools.r8.instrumentation.callsites");
    if (callSitesToInstrumentString != null) {
      setCallSitesToInstrument(
          parseCallSitesToInstrument(callSitesToInstrumentString, options.dexItemFactory()));
    }
  }

  private static Set<DexMethod> parseCallSitesToInstrument(
      String smaliStrings, DexItemFactory factory) {
    ImmutableSet.Builder<DexMethod> builder = ImmutableSet.builder();
    for (String smaliString : Splitter.on(':').split(smaliStrings)) {
      MethodReference methodReference = MethodReferenceUtils.parseSmaliString(smaliString);
      if (methodReference == null) {
        throw new IllegalArgumentException(smaliString);
      }
      builder.add(MethodReferenceUtils.toDexMethod(methodReference, factory));
    }
    return builder.build();
  }

  public boolean isInstrumentationEnabled() {
    return enableExecutedClassesAndMethodsInstrumentation || !callSitesToInstrument.isEmpty();
  }

  public Set<DexMethod> getCallSitesToInstrument() {
    return callSitesToInstrument;
  }

  public void setCallSitesToInstrument(Set<DexMethod> callSitesToInstrument) {
    this.callSitesToInstrument = callSitesToInstrument;
  }

  public boolean isExecutedClassesAndMethodsInstrumentationEnabled() {
    return enableExecutedClassesAndMethodsInstrumentation;
  }

  public InstrumentationOptions setEnableExecutedClassesAndMethodsInstrumentation() {
    enableExecutedClassesAndMethodsInstrumentation = true;
    return this;
  }

  public boolean hasSyntheticServerContext() {
    return syntheticServerContext != null;
  }

  public String getSyntheticServerContext() {
    return syntheticServerContext;
  }

  public boolean hasTag() {
    return tag != null;
  }

  public String getTag() {
    return tag;
  }

  public InstrumentationOptions setTag(String tag) {
    this.tag = tag;
    return this;
  }
}
