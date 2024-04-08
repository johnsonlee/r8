// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.globalsyntheticsgenerator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.GlobalSyntheticsGenerator;
import com.android.tools.r8.GlobalSyntheticsGeneratorCommand;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.references.ClassReference;
import org.junit.Test;

public class GlobalSyntheticsGeneratorTest extends CompilerApiTestRunner {

  public GlobalSyntheticsGeneratorTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testGlobalSynthetics() throws Exception {
    new ApiTest(ApiTest.PARAMETERS)
        .run(
            new GlobalSyntheticsConsumer() {
              boolean hasOutput = false;

              @Override
              public void accept(
                  ByteDataView data, ClassReference context, DiagnosticsHandler handler) {
                assertNull(context);
                assertFalse(hasOutput);
                assertTrue(data.getLength() > 0);
                hasOutput = true;
              }

              @Override
              public void finished(DiagnosticsHandler handler) {
                assertTrue(hasOutput);
              }
            });
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run(GlobalSyntheticsConsumer consumer) throws Exception {
      GlobalSyntheticsGenerator.run(
          GlobalSyntheticsGeneratorCommand.builder()
              .addLibraryFiles(getAndroidJar())
              .setMinApiLevel(33)
              .setGlobalSyntheticsConsumer(consumer)
              .build());
    }

    @Test
    public void testGlobalSynthetics() throws Exception {
      run(
          new GlobalSyntheticsConsumer() {
            @Override
            public void accept(
                ByteDataView data, ClassReference context, DiagnosticsHandler handler) {
              // Ignoring output in API test.
            }
          });
    }
  }
}
