// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.doctests;

import com.android.tools.r8.keepanno.KeepAnnoParameters;
import com.android.tools.r8.keepanno.KeepAnnoTestBase;
import com.android.tools.r8.keepanno.doctests.GenericSignaturePrinter.TestClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class SignatureConstraintsDocumentationTest extends KeepAnnoTestBase {

  static final String EXPECTED =
      StringUtils.lines(
          typeName(GenericSignaturePrinter.class) + "$MyString uses type java.lang.String",
          typeName(GenericSignaturePrinter.class) + "$MyBool uses type java.lang.Boolean");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addProgramClasses(GenericSignaturePrinter.class)
        .addInnerClasses(GenericSignaturePrinter.class)
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }
}
