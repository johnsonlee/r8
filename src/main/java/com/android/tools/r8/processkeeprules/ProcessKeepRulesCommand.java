// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.processkeeprules.annotations.KeepForProcessKeepRulesApi;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@KeepForApi
@KeepForProcessKeepRulesApi
public class ProcessKeepRulesCommand {

  private final Reporter reporter;
  private final List<ProguardConfigurationSource> keepRules;
  private final StringConsumer filteredKeepRulesConsumer;
  private final boolean validateLibraryConsumerRules;

  private ProcessKeepRulesCommand(
      Reporter reporter,
      List<ProguardConfigurationSource> keepRules,
      StringConsumer filteredKeepRulesConsumer,
      boolean validateLibraryConsumerRules) {
    this.reporter = reporter;
    this.keepRules = keepRules;
    this.filteredKeepRulesConsumer = filteredKeepRulesConsumer;
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

  boolean isValidateLibraryConsumerRules() {
    return validateLibraryConsumerRules;
  }

  StringConsumer getFilteredKeepRulesConsumer() {
    return filteredKeepRulesConsumer;
  }

  List<ProguardConfigurationSource> getKeepRules() {
    return keepRules;
  }

  @KeepForApi
  @KeepForProcessKeepRulesApi
  public static class Builder {

    private final Reporter reporter;
    private final List<ProguardConfigurationSource> keepRules = new ArrayList<>();

    private StringConsumer filteredKeepRulesConsumer = null;
    private boolean validateLibraryConsumerRules = false;

    // TODO(b/447161121) introduce a DefaultDiagnosticHandler instead.
    private Builder() {
      this(new DiagnosticsHandler() {});
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    public Builder addKeepRules(String data, Origin origin) {
      keepRules.add(new ProguardConfigurationSourceStrings(data, Paths.get("."), origin));
      return this;
    }

    /** Add proguard configuration-file resources. */
    public Builder addKeepRuleFiles(Collection<Path> paths) {
      for (Path path : paths) {
        keepRules.add(new ProguardConfigurationSourceFile(path));
      }
      return this;
    }

    public Builder addKeepRuleFiles(Path... paths) {
      return addKeepRuleFiles(Arrays.asList(paths));
    }

    public Builder setFilteredKeepRulesConsumer(StringConsumer filteredKeepRulesConsumer) {
      this.filteredKeepRulesConsumer = filteredKeepRulesConsumer;
      return this;
    }

    public Builder setLibraryConsumerRuleValidation(boolean enable) {
      validateLibraryConsumerRules = enable;
      return this;
    }

    public ProcessKeepRulesCommand build() {
      validate();
      return new ProcessKeepRulesCommand(
          reporter, keepRules, filteredKeepRulesConsumer, validateLibraryConsumerRules);
    }

    private void validate() {
      if (keepRules.isEmpty()) {
        reporter.error("No keep rules provided.");
      }
      if (filteredKeepRulesConsumer == null) {
        if (!validateLibraryConsumerRules) {
          reporter.error("Filtering or validation not enabled.");
        }
      } else if (validateLibraryConsumerRules) {
        reporter.error("Filtering and validation not supported simultaneously.");
      }
      reporter.failIfPendingErrors();
    }
  }
}
