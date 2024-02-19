// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class KeepAnnoTestBase extends TestBase {

  public static List<KeepAnnoParameters> createParameters(
      TestParametersCollection parametersCollection) {
    List<KeepAnnoParameters> keepAnnoParams = new ArrayList<>();
    for (TestParameters parameters : parametersCollection) {
      keepAnnoParams.add(
          new KeepAnnoParameters(parameters, KeepAnnoParameters.KeepAnnoConfig.REFERENCE));
      keepAnnoParams.add(
          new KeepAnnoParameters(parameters, KeepAnnoParameters.KeepAnnoConfig.R8_DIRECT));
      keepAnnoParams.add(
          new KeepAnnoParameters(parameters, KeepAnnoParameters.KeepAnnoConfig.R8_NORMALIZED));
      keepAnnoParams.add(
          new KeepAnnoParameters(parameters, KeepAnnoParameters.KeepAnnoConfig.R8_RULES));
      keepAnnoParams.add(
          new KeepAnnoParameters(parameters, KeepAnnoParameters.KeepAnnoConfig.R8_LEGACY));
      if (parameters.isCfRuntime()) {
        keepAnnoParams.add(
            new KeepAnnoParameters(parameters, KeepAnnoParameters.KeepAnnoConfig.PG));
      }
    }
    return keepAnnoParams;
  }

  public KeepAnnoTestBuilder testForKeepAnno(KeepAnnoParameters params) throws IOException {
    return KeepAnnoTestBuilder.forKeepAnnoTest(params, temp);
  }
}
