// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.processkeeprules.annotations.KeepForProcessKeepRulesApi;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationParserConsumer;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.Reporter;
import java.util.List;

@KeepForApi
@KeepForProcessKeepRulesApi
public class ProcessKeepRules {

  private final DexItemFactory factory;
  private final Reporter reporter;

  private final List<ProguardConfigurationSource> keepRules;
  private final StringConsumer filteredKeepRulesConsumer;
  private final boolean validateLibraryConsumerRules;

  private ProcessKeepRules(DexItemFactory factory, ProcessKeepRulesCommand command) {
    this.factory = factory;
    this.reporter = command.getReporter();
    this.keepRules = command.getKeepRules();
    this.filteredKeepRulesConsumer = command.getFilteredKeepRulesConsumer();
    this.validateLibraryConsumerRules = command.isValidateLibraryConsumerRules();
  }

  public static void run(ProcessKeepRulesCommand command) throws CompilationFailedException {
    ExceptionUtils.withCompilationHandler(
        command.getReporter(),
        () -> {
          DexItemFactory factory = new DexItemFactory();
          new ProcessKeepRules(factory, command).internalRun();
        });
  }

  private void internalRun() {
    FilteredKeepRulesBuilder filteredKeepRulesBuilder =
        filteredKeepRulesConsumer != null
            ? new FilteredKeepRulesBuilder(filteredKeepRulesConsumer, reporter)
            : null;
    ProguardConfigurationParserConsumer parserConsumer =
        validateLibraryConsumerRules
            ? new ValidateLibraryConsumerRulesKeepRuleProcessor(reporter)
            : filteredKeepRulesBuilder;
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(factory, reporter, parserConsumer);
    parser.parse(keepRules);
    supplyConsumers();
    reporter.failIfPendingErrors();
  }

  private void supplyConsumers() {
    if (filteredKeepRulesConsumer != null) {
      filteredKeepRulesConsumer.finished(reporter);
    }
  }
}
