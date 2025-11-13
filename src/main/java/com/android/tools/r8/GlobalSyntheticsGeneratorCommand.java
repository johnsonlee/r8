// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.BaseCommand.LibraryInputOrigin;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * Immutable command structure for an invocation of the {@link GlobalSyntheticsGenerator} compiler.
 */
@KeepForApi
public final class GlobalSyntheticsGeneratorCommand {

  private final GlobalSyntheticsConsumer globalsConsumer;
  private final Reporter reporter;
  private final int minMajorApiLevel;

  @SuppressWarnings("UnusedVariable")
  private final int minMinorApiLevel;

  private final boolean classfileDesugaringOnly;
  private final boolean enableVerboseSyntheticNames;

  private final boolean printHelp;
  private final boolean printVersion;

  private final AndroidApp inputApp;

  private final DexItemFactory factory = new DexItemFactory();

  private GlobalSyntheticsGeneratorCommand(
      AndroidApp inputApp,
      GlobalSyntheticsConsumer globalsConsumer,
      Reporter reporter,
      int minMajorApiLevel,
      int minMinorApiLevel,
      boolean classfileDesugaringOnly,
      boolean enableVerboseSyntheticNames) {
    this.inputApp = inputApp;
    this.globalsConsumer = globalsConsumer;
    this.minMajorApiLevel = minMajorApiLevel;
    this.minMinorApiLevel = minMinorApiLevel;
    this.classfileDesugaringOnly = classfileDesugaringOnly;
    this.enableVerboseSyntheticNames = enableVerboseSyntheticNames;
    this.reporter = reporter;
    this.printHelp = false;
    this.printVersion = false;
  }

  private GlobalSyntheticsGeneratorCommand(boolean printHelp, boolean printVersion) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;

    this.inputApp = null;
    this.globalsConsumer = null;
    this.minMajorApiLevel = AndroidApiLevel.B.getLevel();
    this.minMinorApiLevel = 0;
    this.classfileDesugaringOnly = false;
    this.enableVerboseSyntheticNames = false;

    reporter = new Reporter();
  }

  public AndroidApp getInputApp() {
    return inputApp;
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  /**
   * Parse the GlobalSyntheticsGenerator command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return GlobalSyntheticsGenerator command builder with state according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin) {
    return GlobalSyntheticsGeneratorCommandParser.parse(args, origin);
  }

  /**
   * Parse the GlobalSyntheticsGenerator command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return GlobalSyntheticsGenerator command builder with state according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return GlobalSyntheticsGeneratorCommandParser.parse(args, origin, handler);
  }

  protected static class DefaultR8DiagnosticsHandler implements DiagnosticsHandler {

    @Override
    public void error(Diagnostic error) {
      if (error instanceof DexFileOverflowDiagnostic) {
        DexFileOverflowDiagnostic overflowDiagnostic = (DexFileOverflowDiagnostic) error;
        DiagnosticsHandler.super.error(
            new StringDiagnostic(
                overflowDiagnostic.getDiagnosticMessage()
                    + ". Library too large. GlobalSyntheticsGenerator can only produce a single"
                    + " .dex file"));
        return;
      }
      DiagnosticsHandler.super.error(error);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, reporter);
    assert !internal.debug;
    assert !internal.minimalMainDex;
    internal.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(minMajorApiLevel));
    assert internal.retainCompileTimeAnnotations;
    internal.intermediate = true;
    internal.programConsumer =
        classfileDesugaringOnly ? new ThrowingCfConsumer() : new ThrowingDexConsumer();
    internal.setGlobalSyntheticsConsumer(globalsConsumer);
    if (classfileDesugaringOnly) {
      internal
          .apiModelingOptions()
          .disableApiCallerIdentification()
          .disableOutlining()
          .disableStubbingOfClasses();
    }

    // Assert and fixup defaults.
    assert !internal.isShrinking();
    assert !internal.isMinifying();
    assert !internal.passthroughDexCode;

    internal.tool = Tool.GlobalSyntheticsGenerator;
    internal.desugarState = DesugarState.ON;
    internal.desugarSpecificOptions().enableVerboseSyntheticNames = enableVerboseSyntheticNames;
    internal.enableVarHandleDesugaring = true;

    internal.getArtProfileOptions().setEnableCompletenessCheckForTesting(false);

    return internal;
  }

  private static class ThrowingCfConsumer implements ClassFileConsumer {

    @Override
    public void accept(ByteDataView data, String descriptor, DiagnosticsHandler handler) {
      throw new Unreachable("Unexpected attempt to write a non-global artifact");
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      // Nothing to do.
    }
  }

  private static class ThrowingDexConsumer implements DexIndexedConsumer {

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      throw new Unreachable("Unexpected attempt to write a non-global artifact");
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      // Nothing to do.
    }
  }

  /**
   * Builder for constructing a GlobalSyntheticsGeneratorCommand.
   *
   * <p>A builder is obtained by calling {@link GlobalSyntheticsGeneratorCommand#builder}.
   */
  @KeepForApi
  public static class Builder {

    private GlobalSyntheticsConsumer globalsConsumer = null;
    private final Reporter reporter;
    private int minMajorApiLevel = AndroidApiLevel.B.getLevel();
    private int minMinorApiLevel = 0;
    private boolean classfileDesugaringOnly = false;
    private boolean enableVerboseSyntheticNames = false;
    private boolean printHelp = false;
    private boolean printVersion = false;
    private final AndroidApp.Builder appBuilder = AndroidApp.builder();

    private Builder() {
      this(new DefaultR8DiagnosticsHandler());
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    /** Set the min api level. */
    public Builder setMinApiLevel(int minMajorApiLevel) {
      return setMinApiLevel(minMajorApiLevel, 0);
    }

    /** Set the min api level. */
    public Builder setMinApiLevel(int minMajorApiLevel, int minMinorApiLevel) {
      this.minMajorApiLevel = minMajorApiLevel;
      this.minMinorApiLevel = minMinorApiLevel;
      return this;
    }

    public Builder setClassfileDesugaringOnly(boolean value) {
      this.classfileDesugaringOnly = value;
      return this;
    }

    /** Set the value of the print-help flag. */
    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    /** Set the value of the print-version flag. */
    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    /** Add library file resources. */
    public Builder addLibraryFiles(Path... files) {
      addLibraryFiles(Arrays.asList(files));
      return this;
    }

    /** Add library file resources. */
    public Builder addLibraryFiles(Collection<Path> files) {
      guard(
          () -> {
            for (Path path : files) {
              try {
                appBuilder.addLibraryFile(path);
              } catch (CompilationError e) {
                error(new LibraryInputOrigin(path), e);
              }
            }
          });
      return this;
    }

    /** Set a destination to write the resulting global synthetics output file. */
    public Builder setGlobalSyntheticsOutput(Path path) {
      return setGlobalSyntheticsConsumer(
          new GlobalSyntheticsConsumer() {

            private boolean written = false;

            @Override
            public synchronized void accept(
                ByteDataView data, ClassReference context, DiagnosticsHandler handler) {
              if (written) {
                throw new Unreachable("Unexpected attempt to repeatedly write global synthetics");
              }
              written = true;
              try {
                Files.write(path, data.copyByteData());
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          });
    }

    /** Set a consumer for obtaining the resulting global synthetics output. */
    public Builder setGlobalSyntheticsConsumer(GlobalSyntheticsConsumer globalsConsumer) {
      this.globalsConsumer = globalsConsumer;
      return this;
    }

    /**
     * By default, the compiler uses the same naming scheme for synthetic classes as javac uses for
     * anonymous inner classes. By enabling verbose synthetic names, the synthetic classes will
     * include a "$$ExternalSynthetic" marker, which includes the synthetic kind (e.g., "Lambda").
     */
    public Builder setEnableVerboseSyntheticNames(boolean enableVerboseSyntheticNames) {
      this.enableVerboseSyntheticNames = enableVerboseSyntheticNames;
      return this;
    }

    public GlobalSyntheticsGeneratorCommand build() {
      validate();
      if (isPrintHelpOrPrintVersion()) {
        return new GlobalSyntheticsGeneratorCommand(printHelp, printVersion);
      }
      return new GlobalSyntheticsGeneratorCommand(
          appBuilder.build(),
          globalsConsumer,
          reporter,
          minMajorApiLevel,
          minMinorApiLevel,
          classfileDesugaringOnly,
          enableVerboseSyntheticNames);
    }

    private boolean isPrintHelpOrPrintVersion() {
      return printHelp || printVersion;
    }

    private void validate() {
      if (isPrintHelpOrPrintVersion()) {
        return;
      }
      if (globalsConsumer == null) {
        reporter.error("GlobalSyntheticsGenerator does not support compiling without output");
      }
    }

    // Helper to guard and handle exceptions.
    private void guard(Runnable action) {
      try {
        action.run();
      } catch (CompilationError e) {
        reporter.error(e.toStringDiagnostic());
      } catch (AbortException e) {
        // Error was reported and exception will be thrown by build.
      }
    }

    /** Signal an error. */
    public void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
    }

    // Helper to signify an error.
    public void error(Origin origin, Throwable throwable) {
      reporter.error(new ExceptionDiagnostic(throwable, origin));
    }
  }
}
