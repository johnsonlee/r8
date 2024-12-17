// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;

@KeepForApi
/** Experimental API to extract embedded rules from libraries. */
public class ExtractR8RulesCommand extends BaseCommand {

  private final StringConsumer rulesConsumer;
  private final boolean includeOriginComments;
  private final SemanticVersion compilerVersion;
  private final DexItemFactory factory;
  private final Reporter reporter;

  @KeepForApi
  public static class Builder extends BaseCommand.Builder<ExtractR8RulesCommand, Builder> {

    private final DexItemFactory factory = new DexItemFactory();
    private StringConsumer rulesConsumer = null;
    private boolean includeOriginComments = false;
    private SemanticVersion compilerVersion = null;

    private Builder() {}

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    @Override
    ExtractR8RulesCommand.Builder self() {
      return this;
    }

    /** TBD */
    public ExtractR8RulesCommand.Builder setRulesOutputPath(Path rulesOutputPath) {
      rulesConsumer = new StringConsumer.FileConsumer(rulesOutputPath);
      return self();
    }

    /** TBD */
    public ExtractR8RulesCommand.Builder setRulesConsumer(StringConsumer rulesConsumer) {
      this.rulesConsumer = rulesConsumer;
      return self();
    }

    /** TBD */
    public ExtractR8RulesCommand.Builder setIncludeOriginComments(boolean include) {
      this.includeOriginComments = include;
      return self();
    }

    /** TBD */
    public Builder setCompilerVersion(SemanticVersion version) {
      compilerVersion = version;
      return self();
    }

    @Override
    protected ExtractR8RulesCommand makeCommand() {
      // If printing versions ignore everything else.
      if (isPrintHelp() || isPrintVersion()) {
        return new ExtractR8RulesCommand(isPrintHelp(), isPrintVersion());
      }

      return new ExtractR8RulesCommand(
          factory,
          getAppBuilder().build(),
          rulesConsumer,
          includeOriginComments,
          compilerVersion,
          getReporter());
    }
  }

  static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: TBD",
          "  --rules-output <file>      # Output the extracted keep rules.",
          "  --compiler-version <version>  # Output the proguard rules extracted.",
          "  --include-origin-comments  # Include comments with origin for extracted rules.",
          "  --version                  # Print the version.",
          "  --help                     # Print this message.");

  public static ExtractR8RulesCommand.Builder builder() {
    return new ExtractR8RulesCommand.Builder();
  }

  public static ExtractR8RulesCommand.Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new ExtractR8RulesCommand.Builder(diagnosticsHandler);
  }

  public static ExtractR8RulesCommand.Builder parse(String[] args) {
    ExtractR8RulesCommand.Builder builder = builder();
    parse(args, builder);
    return builder;
  }

  public StringConsumer getRulesConsumer() {
    return rulesConsumer;
  }

  public boolean getIncludeOriginComments() {
    return includeOriginComments;
  }

  public SemanticVersion getCompilerVersion() {
    return compilerVersion;
  }

  Reporter getReporter() {
    return reporter;
  }

  private static void parse(String[] args, ExtractR8RulesCommand.Builder builder) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i].trim();
      if (arg.length() == 0) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals("--rules-output")) {
        builder.setRulesOutputPath(Paths.get(args[++i]));
      } else if (arg.equals("--compiler-version")) {
        builder.setCompilerVersion(SemanticVersion.parse(args[++i]));
      } else if (arg.equals("--include-origin-comments")) {
        builder.setIncludeOriginComments(true);
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

  private ExtractR8RulesCommand(
      DexItemFactory factory,
      AndroidApp inputApp,
      StringConsumer rulesConsumer,
      boolean includeOriginComments,
      SemanticVersion compilerVersion,
      Reporter reporter) {
    super(inputApp);
    this.factory = factory;
    this.rulesConsumer = rulesConsumer;
    this.includeOriginComments = includeOriginComments;
    this.compilerVersion = compilerVersion;
    this.reporter = reporter;
  }

  private ExtractR8RulesCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    this.factory = new DexItemFactory();
    this.rulesConsumer = null;
    this.includeOriginComments = false;
    this.compilerVersion = null;
    this.reporter = new Reporter();
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, reporter);
    internal.programConsumer = DexIndexedConsumer.emptyConsumer();
    assert internal.retainCompileTimeAnnotations;
    internal.retainCompileTimeAnnotations = false;
    return internal;
  }
}
