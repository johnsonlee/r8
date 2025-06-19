// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Experimental API to extract keep rules from keep annotations. */
@KeepForApi
public class ExtractKeepAnnoRulesCommand extends BaseCommand {

  private final StringConsumer rulesConsumer;
  private final KeepRuleExtractorOptions extractorOptions;
  private final DexItemFactory factory;
  private final Reporter reporter;

  @KeepForApi
  public static class Builder extends BaseCommand.Builder<ExtractKeepAnnoRulesCommand, Builder> {

    private StringConsumer rulesConsumer = null;
    private KeepRuleExtractorOptions extractorOptions = KeepRuleExtractorOptions.getR8Options();

    private Builder() {}

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    @Override
    Builder self() {
      return this;
    }

    /** TBD - still experimental */
    public Builder setRulesOutputPath(Path rulesOutputPath) {
      rulesConsumer = new StringConsumer.FileConsumer(rulesOutputPath);
      return self();
    }

    /** TBD - still experimental */
    public Builder setRulesConsumer(StringConsumer rulesConsumer) {
      this.rulesConsumer = rulesConsumer;
      return self();
    }

    /** TBD - still experimental and still package private */
    Builder setExtractorOptions(KeepRuleExtractorOptions extractorOptions) {
      this.extractorOptions = extractorOptions;
      return self();
    }

    @Override
    protected ExtractKeepAnnoRulesCommand makeCommand() {
      // If printing versions ignore everything else.
      if (isPrintHelp() || isPrintVersion()) {
        return new ExtractKeepAnnoRulesCommand(isPrintHelp(), isPrintVersion());
      }

      return new ExtractKeepAnnoRulesCommand(
          new DexItemFactory(),
          getAppBuilder().build(),
          rulesConsumer,
          extractorOptions,
          getReporter());
    }
  }

  static final String USAGE_MESSAGE =
      // TODO(b/425263915): Need to support more, including extracting for different versions and
      //  keepedge AST proto.
      StringUtils.lines(
          "Usage: EXPERIMENTAL tool to extract keep rules from keep annotations",
          "  --rules-output <file>      # Output the extracted keep rules.",
          "  --rules-target r8|pg       # Optimizer rules are for (default r8).",
          "  --version                  # Print the version.",
          "  --help                     # Print this message.");

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  public static Builder parse(String[] args) {
    Builder builder = builder();
    parse(args, builder);
    return builder;
  }

  public StringConsumer getRulesConsumer() {
    return rulesConsumer;
  }

  public KeepRuleExtractorOptions getExtractorOptions() {
    return extractorOptions;
  }

  Reporter getReporter() {
    return reporter;
  }

  private static void parse(String[] args, Builder builder) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i].trim();
      if (arg.length() == 0) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals("--rules-target")) {
        i++;
        if (args.length == i) {
          builder
              .getReporter()
              .fatalError(
                  new StringDiagnostic(
                      "Missing argument to --rules-target", CommandLineOrigin.INSTANCE));
        }
        String target = args[i].trim();
        if (target.equals("r8")) {
          builder.setExtractorOptions(KeepRuleExtractorOptions.getR8Options());
        } else if (target.equals("pg")) {
          builder.setExtractorOptions(KeepRuleExtractorOptions.getPgOptions());
        } else {
          builder
              .getReporter()
              .fatalError(
                  new StringDiagnostic(
                      "Unsupported argument '" + target + "' to --rules-target",
                      CommandLineOrigin.INSTANCE));
        }
      } else if (arg.equals("--rules-output")) {
        if (args.length == i) {
          builder
              .getReporter()
              .fatalError(
                  new StringDiagnostic(
                      "Missing argument to --rules-output", CommandLineOrigin.INSTANCE));
        }
        builder.setRulesOutputPath(Paths.get(args[++i]));
      } else {
        if (arg.startsWith("--")) {
          builder
              .getReporter()
              .fatalError(
                  new StringDiagnostic("Unknown option: " + arg, CommandLineOrigin.INSTANCE));
        }
        builder.addProgramFiles(Paths.get(arg));
      }
    }
  }

  private ExtractKeepAnnoRulesCommand(
      DexItemFactory factory,
      AndroidApp inputApp,
      StringConsumer rulesConsumer,
      KeepRuleExtractorOptions extractorOptions,
      Reporter reporter) {
    super(inputApp);
    this.factory = factory;
    this.rulesConsumer = rulesConsumer;
    this.extractorOptions = extractorOptions;
    this.reporter = reporter;
  }

  private ExtractKeepAnnoRulesCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    this.factory = new DexItemFactory();
    this.rulesConsumer = null;
    this.extractorOptions = null;
    this.reporter = new Reporter();
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, reporter);
    internal.programConsumer = DexIndexedConsumer.emptyConsumer();
    return internal;
  }
}
