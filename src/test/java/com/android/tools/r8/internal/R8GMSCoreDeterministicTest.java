// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.IROrdering.NondeterministicIROrdering;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class R8GMSCoreDeterministicTest extends GMSCoreCompilationTestBase {

  private static class CompilationResult {
    AndroidApp app;
    String proguardMap;
  }

  private CompilationResult doRun() throws CompilationFailedException {
    R8Command command =
        R8Command.builder()
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addProgramFiles(Paths.get(GMSCORE_V7_DIR, GMSCORE_APK))
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .setMinApiLevel(AndroidApiLevel.L.getLevel())
            .build();
    CompilationResult result = new CompilationResult();
    result.app =
        ToolHelper.runR8(
            command,
            options -> {
              // For this test just do random shuffle.
              options.testing.irOrdering = NondeterministicIROrdering.getInstance();
              // Only use one thread to process to process in the order decided by the callback.
              options.numberOfThreads = 1;
              // Ignore the missing classes.
              options.ignoreMissingClasses = true;
              // Store the generated Proguard map.
              options.proguardMapConsumer =
                  ToolHelper.consumeString(proguardMap -> result.proguardMap = proguardMap);
            });
    return result;
  }

  @Test
  public void deterministic() throws Exception {
    // Run two independent compilations.
    CompilationResult result1 = doRun();
    CompilationResult result2 = doRun();

    // Check that the generated bytecode runs through the dex2oat verifier with no errors.
    Path combinedInput = temp.getRoot().toPath().resolve("all.jar");
    Path oatFile = temp.getRoot().toPath().resolve("all.oat");
    result1.app.writeToZip(combinedInput, OutputMode.DexIndexed);
    ToolHelper.runDex2Oat(combinedInput, oatFile);

    // Verify that the result of the two compilations was the same.
    assertIdenticalApplications(result1.app, result2.app);
    assertEquals(result1.proguardMap, result2.proguardMap);
  }
}
