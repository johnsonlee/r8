// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.tracereferences.TraceReferencesCommand;
import com.android.tools.r8.utils.ArchiveDataResourceProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class R8PartialInput {

  private final Path d8Program;
  private final Path r8Program;
  private final CompilerDump dump;

  public R8PartialInput(Path d8Program, Path r8Program, CompilerDump dump) {
    this.d8Program = d8Program;
    this.r8Program = r8Program;
    this.dump = dump;
  }

  public void configure(D8Command.Builder commandBuilder) throws IOException {
    configureBase(commandBuilder);
    configureDesugaredLibrary(commandBuilder);
    commandBuilder.addProgramFiles(d8Program).addClasspathFiles(r8Program);
  }

  public void configureDesugar(D8Command.Builder commandBuilder) throws IOException {
    configureBase(commandBuilder);
    commandBuilder.addProgramFiles(r8Program).addClasspathFiles(d8Program);
  }

  public void configureMerge(D8Command.Builder commandBuilder) throws IOException {
    configureBase(commandBuilder);
  }

  public void configure(R8Command.Builder commandBuilder) throws IOException {
    configureBase(commandBuilder);
    configureDesugaredLibrary(commandBuilder);
    commandBuilder
        .addProgramResourceProvider(new ArchiveDataResourceProvider(r8Program))
        .addClasspathFiles(d8Program)
        .addProguardConfigurationFiles(dump.getProguardConfigFile());
  }

  public void configure(TraceReferencesCommand.Builder commandBuilder) throws IOException {
    commandBuilder.addLibraryFiles(dump.getLibraryArchive()).addSourceFiles(d8Program);
  }

  private void configureBase(BaseCompilerCommand.Builder<?, ?> commandBuilder) throws IOException {
    commandBuilder
        .addClasspathFiles(dump.getClasspathArchive())
        .addLibraryFiles(dump.getLibraryArchive())
        .setMinApiLevel(dump.getBuildProperties().getMinApi())
        .setMode(dump.getBuildProperties().getCompilationMode());
  }

  private void configureDesugaredLibrary(BaseCompilerCommand.Builder<?, ?> commandBuilder)
      throws IOException {
    if (dump.hasDesugaredLibrary()) {
      commandBuilder.addDesugaredLibraryConfiguration(
          Files.readString(dump.getDesugaredLibraryFile(), UTF_8));
    }
  }
}
