// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.determinism;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NowInAndroidDeterminismTest extends DumpDeterminismTestBase {

  public NowInAndroidDeterminismTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  Path getDumpFile() {
    return Paths.get(
        ToolHelper.THIRD_PARTY_DIR, "opensource-apps/android/nowinandroid/dump_app.zip");
  }
}
