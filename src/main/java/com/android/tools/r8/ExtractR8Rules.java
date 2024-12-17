// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.EmbeddedRulesExtractor;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.SemanticVersionUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

@KeepForApi
public class ExtractR8Rules {

  private static void run(
      AndroidApp app,
      StringConsumer consumer,
      boolean includeOriginComments,
      SemanticVersion compilerVersion,
      Reporter reporter) {
    Supplier<SemanticVersion> semanticVersionSupplier =
        SemanticVersionUtils.compilerVersionSemanticVersionSupplier(
            compilerVersion,
            "Using an artificial version newer than any known version for selecting"
                + " Proguard configurations embedded under META-INF/. This means that"
                + " all rules with a '-upto-' qualifier will be excluded and all"
                + " rules with a -from- qualifier will be included.",
            reporter);
    for (ProgramResourceProvider provider : app.getProgramResourceProviders()) {
      DataResourceProvider dataResourceProvider = provider.getDataResourceProvider();
      if (dataResourceProvider == null) {
        return;
      }
      try {
        EmbeddedRulesExtractor embeddedProguardConfigurationVisitor =
            new EmbeddedRulesExtractor(reporter, semanticVersionSupplier);
        dataResourceProvider.accept(embeddedProguardConfigurationVisitor);
        embeddedProguardConfigurationVisitor.visitRelevantRules(
            rules -> {
              try {
                if (includeOriginComments) {
                  consumer.accept("# Rules extracted from:", reporter);
                  consumer.accept(StringUtils.LINE_SEPARATOR, reporter);
                  consumer.accept("# ", reporter);
                  consumer.accept(rules.getOrigin().toString(), reporter);
                  consumer.accept(StringUtils.LINE_SEPARATOR, reporter);
                }
                consumer.accept(rules.get(), reporter);
                consumer.accept(StringUtils.LINE_SEPARATOR, reporter);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
      } catch (ResourceException e) {
        reporter.error(new ExceptionDiagnostic(e));
      }
    }
    consumer.finished(reporter);
  }

  /** Experimental API to extract embedded rules from libraries. */
  public static void run(ExtractR8RulesCommand command) throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    StringConsumer rulesConsumer = command.getRulesConsumer();
    boolean includeOriginComments = command.getIncludeOriginComments();
    SemanticVersion compilerVersion = command.getCompilerVersion();
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withCompilationHandler(
        options.reporter,
        () -> {
          run(app, rulesConsumer, includeOriginComments, compilerVersion, options.reporter);
        });
  }

  public static void main(String[] args) throws CompilationFailedException {
    ExtractR8RulesCommand.Builder builder = ExtractR8RulesCommand.parse(args);
    ExtractR8RulesCommand command = builder.build();
    if (command.isPrintHelp()) {
      System.out.println(ExtractR8RulesCommand.USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("ExtractR8Rules " + Version.LABEL);
      return;
    }
    run(command);
  }
}
