// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationParserConsumer;
import com.android.tools.r8.utils.ExceptionUtils;

@KeepForApi
public class ProcessKeepRules {
  public static void run(ProcessKeepRulesCommand command) throws CompilationFailedException {
    ExceptionUtils.withCompilationHandler(
        command.getReporter(),
        () -> {
          // This is the only valid form of keep rule processing for now.
          assert command.isValidateLibraryConsumerRules();
          DexItemFactory dexItemFactory = new DexItemFactory();
          ProguardConfigurationParserConsumer configurationConsumer =
              new ValidateLibraryConsumerRulesKeepRuleProcessor(command.getReporter());
          ProguardConfigurationParser parser =
              new ProguardConfigurationParser(
                  dexItemFactory, command.getReporter(), configurationConsumer);
          parser.parse(command.getKeepRules());
          command.getReporter().failIfPendingErrors();
        });
  }
}
