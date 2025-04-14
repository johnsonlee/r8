// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk17.records;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationRecordTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testExcluded() throws Exception {
    runTest(builder -> builder.addProgramClassFileData(clearNest(ExcludedRecord.class)));
  }

  @Test
  public void testIncluded() throws Exception {
    runTest(
        builder ->
            builder
                .addProgramClassFileData(clearNest(IncludedRecord.class))
                .addKeepClassAndMembersRules(IncludedRecord.class));
  }

  @Test
  public void testBoth() throws Exception {
    runTest(
        builder ->
            builder
                .addProgramClassFileData(
                    clearNest(ExcludedRecord.class), clearNest(IncludedRecord.class))
                .addKeepClassAndMembersRules(IncludedRecord.class));
  }

  private void runTest(ThrowableConsumer<? super R8PartialTestBuilder> configuration)
      throws Exception {
    parameters.assumeCanUseR8Partial();
    testForR8Partial(parameters)
        .apply(configuration)
        .setR8PartialConfiguration(
            builder ->
                builder
                    .addJavaTypeIncludePattern(IncludedRecord.class.getTypeName())
                    .addJavaTypeExcludePattern(ExcludedRecord.class.getTypeName()))
        .compile()
        .inspectWithOptions(
            inspector ->
                assertThat(
                    inspector.clazz("com.android.tools.r8.RecordTag"),
                    isPresentIf(isRecordsFullyDesugaredForD8(parameters))),
            options -> options.testing.disableRecordApplicationReaderMap = true);
  }

  private static byte[] clearNest(Class<?> clazz) throws IOException {
    return transformer(clazz).clearNest().transform();
  }

  record ExcludedRecord() {}

  record IncludedRecord() {}
}
