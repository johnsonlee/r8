// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@KeepForApi
public class ProcessKeepRulesCommand {
  private final Reporter reporter;
  private final List<ProguardConfigurationSource> keepRules;
  private final boolean validateLibraryConsumerRules;

  private ProcessKeepRulesCommand(
      Reporter reporter,
      List<ProguardConfigurationSource> keepRules,
      boolean validateLibraryConsumerRules) {
    this.reporter = reporter;
    this.keepRules = keepRules;
    this.validateLibraryConsumerRules = validateLibraryConsumerRules;
  }

  /**
   * Utility method for obtaining a <code>ProcessKeepRules.Builder</code> with a default diagnostics
   * handler.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Utility method for obtaining a <code>ProcessKeepRules.Builder</code>.
   *
   * @param diagnosticsHandler The diagnostics handler for consuming messages.
   */
  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  Reporter getReporter() {
    return reporter;
  }

  boolean getValidateLibraryConsumerRules() {
    return validateLibraryConsumerRules;
  }

  List<ProguardConfigurationSource> getKeepRules() {
    return keepRules;
  }

  @KeepForApi
  public static class Builder {

    private final Reporter reporter;
    private List<ProguardConfigurationSource> keepRuleFiles = new ArrayList<>();
    private boolean validateLibraryConsumerRules = false;

    // TODO(b/447161121) introduce a DefaultDiagnosticHandler instead.
    private Builder() {
      this(new DiagnosticsHandler() {});
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    /** Add proguard configuration-file resources. */
    public Builder addKeepRuleFiles(Collection<Path> paths) {
      for (Path path : paths) {
        keepRuleFiles.add(new ProguardConfigurationSourceFile(path));
      }
      return this;
    }

    public Builder setLibraryConsumerRuleValidation(boolean enable) {
      validateLibraryConsumerRules = enable;
      return this;
    }

    public ProcessKeepRulesCommand build() {
      validate();
      return new ProcessKeepRulesCommand(reporter, keepRuleFiles, validateLibraryConsumerRules);
    }

    private void validate() {
      if (keepRuleFiles.isEmpty()) {
        reporter.error("No keep rule files provided.");
      }
      if (!validateLibraryConsumerRules) {
        reporter.error("No rule validation enabled.");
      }
      reporter.failIfPendingErrors();
    }
  }
}
