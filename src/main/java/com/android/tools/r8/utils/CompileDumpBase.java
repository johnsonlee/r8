// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.utils.compiledump.ArtProfileDumpUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

public class CompileDumpBase {

  static void setEnableExperimentalMissingLibraryApiModeling(
      Object builder, boolean enableMissingLibraryApiModeling) {
    getReflectiveBuilderMethod(
            builder, "setEnableExperimentalMissingLibraryApiModeling", boolean.class)
        .accept(new Object[] {enableMissingLibraryApiModeling});
  }

  static void setAndroidPlatformBuild(Object builder, boolean androidPlatformBuild) {
    getReflectiveBuilderMethod(builder, "setAndroidPlatformBuild", boolean.class)
        .accept(new Object[] {androidPlatformBuild});
  }

  static void setIsolatedSplits(Object builder, boolean isolatedSplits) {
    getReflectiveBuilderMethod(builder, "setEnableIsolatedSplits", boolean.class)
        .accept(new Object[] {isolatedSplits});
  }

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

  static Consumer<Object[]> getReflectiveBuilderMethod(
      Object builder, String setter, Class<?>... parameters) {
    try {
      Method declaredMethod = builder.getClass().getMethod(setter, parameters);
      return args -> {
        try {
          declaredMethod.invoke(builder, args);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      };
    } catch (NoSuchMethodException e) {
      System.out.println(setter + " is not available on the compiledump version.");
      // The option is not available so we just return an empty consumer
      return args -> {};
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
}
