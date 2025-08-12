// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.sideeffect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryTypesAssumedToBePresentTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    BooleanBox seen = new BooleanBox();
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options.getTestingOptions().horizontallyMergedClassesConsumer =
                    (appView, ignore) -> {
                      if (seen.isFalse()) {
                        inspect(appView);
                        seen.set();
                      }
                    })
        .setMinApi(AndroidApiLevel.B)
        .compile();
    assertTrue(seen.get());
  }

  private void inspect(AppView<?> appView) {
    AndroidApiLevelCompute apiLevelCompute = appView.apiLevelCompute();
    DexItemFactory factory = appView.dexItemFactory();
    for (DexType type : factory.libraryTypesAssumedToBePresent) {
      ComputedApiLevel computedApiLevel = apiLevelCompute.computeApiLevelForLibraryReference(type);
      assertTrue(type.getTypeName() + " " + computedApiLevel, computedApiLevel.isKnownApiLevel());
      assertEquals(
          type.getTypeName() + " " + computedApiLevel,
          AndroidApiLevel.B,
          computedApiLevel.asKnownApiLevel().getApiLevel());
    }
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
