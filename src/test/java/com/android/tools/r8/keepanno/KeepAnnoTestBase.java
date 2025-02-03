// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.KeepAnnoParameters.KeepAnnoConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class KeepAnnoTestBase extends TestBase {

  public static List<KeepAnnoParameters> createParameters(
      TestParametersCollection parametersCollection) {
    List<KeepAnnoParameters> keepAnnoParams = new ArrayList<>();
    for (TestParameters parameters : parametersCollection) {
      for (KeepAnnoConfig config : KeepAnnoConfig.values()) {
        if (config == KeepAnnoConfig.PG && !parameters.isCfRuntime()) {
          continue;
        }
        keepAnnoParams.add(new KeepAnnoParameters(parameters, config));
      }
    }
    return keepAnnoParams;
  }

  public KeepAnnoTestBuilder testForKeepAnno(KeepAnnoParameters params) throws IOException {
    return KeepAnnoTestBuilder.forKeepAnnoTest(params, temp);
  }
}
