// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileName;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.KeepUnusedReturnValue;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingRetraceTest extends RetraceTestBase {

  @Parameters(name = "{0}, mode: {1}, compat: {2}, bridge analysis: {3}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        CompilationMode.values(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private final boolean enableBridgeAnalysis;

  public VerticalClassMergingRetraceTest(
      TestParameters parameters,
      CompilationMode mode,
      boolean compat,
      boolean enableBridgeAnalysis) {
    super(parameters, mode, compat);
    this.enableBridgeAnalysis = enableBridgeAnalysis;
  }

  @Override
  public void configure(R8TestBuilder<?, ?, ?> builder) {
    builder
        .addOptionsModification(
            options ->
                options
                    .getVerticalClassMergerOptions()
                    .setEnableBridgeAnalysis(enableBridgeAnalysis))
        .enableInliningAnnotations()
        .enableKeepUnusedReturnValueAnnotations()
        .enableNeverClassInliningAnnotations();
  }

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(getMainClass(), ResourceWrapper.class, TintResources.class);
  }

  @Override
  public Class<?> getMainClass() {
    return MainApp.class;
  }

  private int expectedActualStackTraceHeight() {
    // In RELEASE mode a synthetic bridge is added by the vertical class merger if the method is
    // targeted by the invoke-super (which is modeled by setting enableBridgeAnalysis to false).
    // Due to single caller inlining we still end up with a stack trace height of 2.
    return 2;
  }

  private boolean filterSynthesizedMethodWhenLineNumberAvailable(
      StackTraceLine retracedStackTraceLine) {
    return retracedStackTraceLine.lineNumber > 0;
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          // Even when SourceFile is present retrace replaces the file name in the stack trace.
          StackTrace reprocessedStackTrace =
              mode == CompilationMode.DEBUG
                  ? retracedStackTrace
                  : retracedStackTrace.filter(this::filterSynthesizedMethodWhenLineNumberAvailable);
          assertThat(reprocessedStackTrace, isSameExceptForFileName(getExpectedStackTrace()));
          assertEquals(
              expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
        });
  }

  @Test
  public void testLineNumberTableOnly() throws Exception {
    assumeTrue(compat);
    parameters.assumeDexRuntime();
    runTest(
        ImmutableList.of("-keepattributes LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          StackTrace reprocessedStackTrace =
              mode == CompilationMode.DEBUG
                  ? retracedStackTrace
                  : retracedStackTrace.filter(this::filterSynthesizedMethodWhenLineNumberAvailable);
          assertThat(reprocessedStackTrace, isSameExceptForFileName(getExpectedStackTrace()));
          assertEquals(
              expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
        });
  }

  @Test
  public void testNoLineNumberTable() throws Exception {
    // This tests will show a difference in r8 retrace compared to proguard retrace. Given the
    // mapping:
    // com.android.tools.r8.naming.retrace.TintResources -> com.android.tools.r8.naming.retrace.a:
    //     1:1:void com.android.tools.r8.naming.retrace.ResourceWrapper.<init>():0:0 -> <init>
    //     1:1:void <init>():0 -> <init>
    //     1:1:void foo()      -> b
    //     java.lang.String com.android.tools.r8.naming.retrace.ResourceWrapper.foo() -> a
    //     java.lang.String com.android.tools.r8.naming.retrace.ResourceWrapper.foo() -> b
    //
    // and stack trace:
    // at com.android.tools.r8.naming.retrace.a.b(Unknown Source:1)
    // at com.android.tools.r8.naming.retrace.a.a(Unknown Source:0)
    // at com.android.tools.r8.naming.retrace.MainApp.main(Unknown Source:7)
    //
    // proguard retrace will produce:
    // at com.android.tools.r8.naming.retraceproguard.TintResources.b(TintResources.java:1)
    // at com.android.tools.r8.naming.retraceproguard.ResourceWrapper.foo(ResourceWrapper.java:0)
    // at com.android.tools.r8.naming.retraceproguard.MainApp.main(MainApp.java:7)
    //
    // We should instead translate to:
    // at com.android.tools.r8.naming.retraceproguard.ResourceWrapper.foo(ResourceWrapper.java:0)
    // at com.android.tools.r8.naming.retraceproguard.MainApp.main(MainApp.java:7)
    // since the synthetic bridge belongs to ResourceWrapper.foo.
    assumeTrue(compat);
    parameters.assumeDexRuntime();
    runTest(
        ImmutableList.of(),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(
              retracedStackTrace, isSameExceptForFileNameAndLineNumber(getExpectedStackTrace()));
          assertEquals(
              expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
        });
  }
}

class ResourceWrapper {
  // Will be merged down, and represented as:
  //     java.lang.String ...ResourceWrapper.foo() -> a
  @NeverInline
  // TODO(b/313404813): Remove @KeepUnusedReturnValue as a workaround for a retrace failure.
  @KeepUnusedReturnValue
  String foo() {
    throw null;
  }
}

@NeverClassInline
class TintResources extends ResourceWrapper {}

class MainApp {
  public static void main(String[] args) {
    TintResources t = new TintResources();
    System.out.println(t.foo());
  }
}
