// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.utils.compiledump.ArtProfileDumpUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;

public class CompileDumpBase {

  static void addArtProfilesForRewriting(
      BaseCompilerCommand.Builder<?, ?> builder, Map<Path, Path> artProfileFiles) {
    for (Entry<Path, Path> inputOutput : artProfileFiles.entrySet()) {
      runIgnoreMissing(
          () ->
              ArtProfileDumpUtils.addArtProfileForRewriting(
                  inputOutput.getKey(), inputOutput.getValue(), builder),
          "Unable to setup art profile rewriting for " + inputOutput.getKey());
    }
  }

  @SuppressWarnings({"CatchAndPrintStackTrace", "DefaultCharset"})
  // We cannot use StringResource since this class is added to the class path and has access only
  // to the public APIs.
  static String readAllBytesJava7(Path filePath) {
    String content = "";

    try {
      content = new String(Files.readAllBytes(filePath));
    } catch (IOException e) {
      e.printStackTrace();
    }

    return content;
  }

  protected static void runIgnoreMissing(Runnable runnable, String onMissing) {
    try {
      runnable.run();
    } catch (NoClassDefFoundError | NoSuchMethodError e) {
      System.out.println(onMissing);
    }
  }

  protected static class BooleanBox {
    public boolean value = false;

    public BooleanBox(boolean value) {
      this.value = value;
    }

    public void set(boolean value) {
      this.value = value;
    }

    public boolean get() {
      return value;
    }
  }
}
