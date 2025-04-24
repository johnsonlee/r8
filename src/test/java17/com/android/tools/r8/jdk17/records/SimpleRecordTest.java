// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleRecordTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe", "42", "true", "true", "true", "false", "false", "false", "false");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean forceInvokeRangeForInvokeCustom;

  @Parameters(name = "{0}, forceInvokeRangeForInvokeCustom: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  private boolean isCfRuntimeWithNativeRecordSupport() {
    return parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)
        && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(isCfRuntimeWithNativeRecordSupport());
    assumeFalse(forceInvokeRangeForInvokeCustom);
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), SimpleRecord.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    assumeFalse(forceInvokeRangeForInvokeCustom);
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .compile()
        .inspectWithOptions(
            i -> RecordTestUtils.assertNoJavaLangRecord(i, parameters),
            options -> options.testing.disableRecordApplicationReaderMap = true)
        .run(parameters.getRuntime(), SimpleRecord.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    ;
  }

  @Test
  public void testD8Intermediate() throws Exception {
    parameters.assumeDexRuntime().assumeNoPartialCompilation();
    assumeFalse(forceInvokeRangeForInvokeCustom);
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path path = compileIntermediate(globals);
    testForD8()
        .addProgramFiles(path)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters),
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(globals.getIndexedModeProvider()))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), SimpleRecord.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8IntermediateNoDesugaringInStep2() throws Exception {
    parameters.assumeDexRuntime().assumeNoPartialCompilation();
    assumeFalse(forceInvokeRangeForInvokeCustom);
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path path = compileIntermediate(globals);
    // In Android Studio they disable desugaring at this point to improve build speed.
    testForD8()
        .addProgramFiles(path)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters),
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(globals.getIndexedModeProvider()))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        .disableDesugaring()
        .run(parameters.getRuntime(), SimpleRecord.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private Path compileIntermediate(GlobalSyntheticsConsumer globalSyntheticsConsumer)
      throws Exception {
    return testForD8(Backend.DEX)
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .setIntermediate(true)
        .setIncludeClassesChecksum(true)
        .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globalSyntheticsConsumer))
        .compile()
        .writeToZip();
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    assumeTrue(forceInvokeRangeForInvokeCustom || parameters.isCfRuntime());
    R8TestBuilder<?, ?, ?> builder =
        testForR8(parameters)
            .apply(
                b -> {
                  if (b.isR8PartialTestBuilder()) {
                    b.addR8PartialR8OptionsModification(this::configure);
                  } else {
                    b.addOptionsModification(this::configure);
                  }
                })
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .addKeepMainRule(SimpleRecord.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .inspect(inspector -> inspector.clazz(Person.class).isRenamed())
          .run(parameters.getRuntime(), SimpleRecord.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder
        .compile()
        .inspectWithOptions(
            i -> RecordTestUtils.assertNoJavaLangRecord(i, parameters),
            options -> options.testing.disableRecordApplicationReaderMap = true)
        .run(parameters.getRuntime(), SimpleRecord.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void configure(InternalOptions options) {
    options.testing.forceInvokeRangeForInvokeCustom = forceInvokeRangeForInvokeCustom;
  }

  @Test
  public void testR8NoMinification() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    assumeTrue(forceInvokeRangeForInvokeCustom || !parameters.isDexRuntime());
    R8TestBuilder<?, ?, ?> builder =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addDontObfuscate()
            .addKeepMainRule(SimpleRecord.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), SimpleRecord.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder
        .compile()
        .inspectWithOptions(
            i -> RecordTestUtils.assertNoJavaLangRecord(i, parameters),
            options -> options.testing.disableRecordApplicationReaderMap = true)
        .run(parameters.getRuntime(), SimpleRecord.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  record Person(String name, int age) {}

  public class SimpleRecord {

    public static void main(String[] args) {
      Person janeDoe = new Person("Jane Doe", 42);
      System.out.println(janeDoe.name());
      System.out.println(janeDoe.age());

      // Test equals with self.
      System.out.println(janeDoe.equals(janeDoe));

      // Test equals with structurally equals Person.
      Person otherJaneDoe = new Person("Jane Doe", 42);
      System.out.println(janeDoe.equals(otherJaneDoe));
      System.out.println(otherJaneDoe.equals(janeDoe));

      // Test equals with not-structually equals Person.
      Person johnDoe = new Person("John Doe", 42);
      System.out.println(janeDoe.equals(johnDoe));
      System.out.println(johnDoe.equals(janeDoe));

      // Test equals with Object and null.
      System.out.println(janeDoe.equals(new Object()));
      System.out.println(janeDoe.equals(null));
    }
  }
}
