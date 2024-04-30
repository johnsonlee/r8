// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.instrumentation;

import static com.android.tools.r8.utils.SystemPropertyUtils.getSystemPropertyForDevelopment;
import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyForDevelopmentOrDefault;

import com.android.tools.r8.graph.DexMethod;
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
  private boolean enableStartupInstrumentation =
      parseSystemPropertyForDevelopmentOrDefault(
          "com.android.tools.r8.startup.instrumentation.instrument", false);

  /**
   * Specifies the synthetic context of the startup runtime library. When this is set, the startup
   * runtime library will only be injected into the app when the synthetic context is in the
   * program. This can be used to avoid that the startup runtime library is injected multiple times
   * in presence of separate compilation.
   *
   * <p>Example synthetic context: "app.tivi.home.MainActivity".
   *
   * <p>Note that this is only meaningful when {@link #enableStartupInstrumentation} is set to true.
   */
  private String startupInstrumentationServerSyntheticContext =
      getSystemPropertyForDevelopment(
          "com.android.tools.r8.startup.instrumentation.instrumentationserversyntheticcontext");

  /**
   * Specifies the logcat tag that should be used by the InstrumentationServer when logging events.
   *
   * <p>When a logcat tag is not specified, the InstrumentationServer will not print events to
   * logcat. Instead, the startup events must be obtained by requesting the InstrumentationServer to
   * write the events to a file.
   */
  private String startupInstrumentationTag =
      getSystemPropertyForDevelopment(
          "com.android.tools.r8.startup.instrumentation.instrumentationtag");

  public Set<DexMethod> getCallSitesToInstrument() {
    return callSitesToInstrument;
  }

  public void setCallSitesToInstrument(Set<DexMethod> callSitesToInstrument) {
    this.callSitesToInstrument = callSitesToInstrument;
  }

  public boolean hasStartupInstrumentationServerSyntheticContext() {
    return startupInstrumentationServerSyntheticContext != null;
  }

  public String getStartupInstrumentationServerSyntheticContext() {
    return startupInstrumentationServerSyntheticContext;
  }

  public InstrumentationOptions setStartupInstrumentationServerSyntheticContext(
      String startupInstrumentationServerSyntheticContext) {
    this.startupInstrumentationServerSyntheticContext =
        startupInstrumentationServerSyntheticContext;
    return this;
  }

  public boolean hasStartupInstrumentationTag() {
    return startupInstrumentationTag != null;
  }

  public String getStartupInstrumentationTag() {
    return startupInstrumentationTag;
  }

  public InstrumentationOptions setStartupInstrumentationTag(String startupInstrumentationTag) {
    this.startupInstrumentationTag = startupInstrumentationTag;
    return this;
  }

  public boolean isStartupInstrumentationEnabled() {
    return enableStartupInstrumentation;
  }

  public InstrumentationOptions setEnableStartupInstrumentation() {
    enableStartupInstrumentation = true;
    return this;
  }
}
