// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;


import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.util.Collections;
import java.util.List;

/** Experimental API to extract keep rules from keep annotations. */
@KeepForApi
public class ExtractKeepAnnoRules {

  private static void run(
      AndroidApp app,
      StringConsumer consumer,
      KeepRuleExtractorOptions extractorOptions,
      Reporter reporter) {
    try {
      extractKeepAnnotationRules(app, consumer, extractorOptions, reporter);
    } catch (ResourceException e) {
      throw reporter.fatalError(new ExceptionDiagnostic(e));
    }
    consumer.finished(reporter);
  }

  private static void extractKeepAnnotationRules(
      AndroidApp app,
      StringConsumer consumer,
      KeepRuleExtractorOptions extractorOptions,
      Reporter reporter)
      throws ResourceException {
    // TODO(b/425252849): Parallelize.
    for (ProgramResourceProvider provider : app.getProgramResourceProviders()) {
      for (ProgramResource resource : provider.getProgramResources()) {
        if (resource.getKind() == Kind.CF) {
          List<KeepDeclaration> declarations = KeepEdgeReader.readKeepEdges(resource.getBytes());
          if (!declarations.isEmpty()) {
            KeepRuleExtractor extractor =
                new KeepRuleExtractor(
                    rule -> {
                      ProguardConfigurationSourceStrings source =
                          new ProguardConfigurationSourceStrings(
                              Collections.singletonList(rule), null, resource.getOrigin());
                      consumer.accept(source.get(), reporter);
                    },
                    extractorOptions);
            declarations.forEach(extractor::extract);
          }
        }
      }
    }
  }

  /** Experimental API to extract keep rules from keep annotations. */
  public static void run(ExtractKeepAnnoRulesCommand command) throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    StringConsumer rulesConsumer = command.getRulesConsumer();
    KeepRuleExtractorOptions extractorOptions = command.getExtractorOptions();
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withCompilationHandler(
        options.reporter,
        () -> {
          run(app, rulesConsumer, extractorOptions, options.reporter);
        });
  }

  public static void main(String[] args) throws CompilationFailedException {
    System.out.println("The keep annotations keep rules extraction tool is experimental.");
    ExtractKeepAnnoRulesCommand.Builder builder = ExtractKeepAnnoRulesCommand.parse(args);
    ExtractKeepAnnoRulesCommand command = builder.build();
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
