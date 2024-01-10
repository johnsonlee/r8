// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.naming.retrace.StackTraceLineProxy;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test from b/319235218
@RunWith(Parameterized.class)
public class RetraceIdentifiersWithNumbersTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final String mapping =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "123pkg.456Foo -> 321pkg.654Foo:",
          "# {'id':'sourceFile','fileName':'456Foo.java'}",
          "    123pkg.1A 2foo(123pkg.1B):100 -> 1a",
          "    0:10:123pkg.1A 2bar(123pkg.1B):42:42 -> 1b");

  private final StackTrace stacktrace =
      StackTrace.builder()
          .add(
              StackTraceLine.builder()
                  .setClassName("321pkg.654Foo")
                  .setMethodName("1a")
                  .setFileName("100SourceFile")
                  .setLineNumber(400)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName("321pkg.654Foo")
                  .setMethodName("1b")
                  .setFileName("100SourceFile")
                  .setLineNumber(7)
                  .build())
          .build();

  private final StackTrace retraced =
      StackTrace.builder()
          .add(
              StackTraceLine.builder()
                  .setClassName("123pkg.456Foo")
                  .setMethodName("2foo")
                  .setFileName("456Foo.java")
                  .setLineNumber(100)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName("123pkg.456Foo")
                  .setMethodName("2bar")
                  .setFileName("456Foo.java")
                  .setLineNumber(42)
                  .build())
          .build();

  @Test
  public void test() {
    // The regular expression based parser does not support identifiers with leading digits.
    // This tests that if we manually construct the stack trace structure, the parsing and retracing
    // works as intended.
    List<RetraceStackFrameAmbiguousResult<StackTraceLine>> overallResult =
        Retrace.<StackTraceLine, StackTraceLineProxy>builder()
            .setMappingSupplier(
                ProguardMappingSupplier.builder()
                    .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
                    .build())
            .setStackTraceLineParser(StackTraceLineProxy::new)
            .build()
            .retraceStackTrace(stacktrace.getStackTraceLines(), RetraceStackTraceContext.empty())
            .getResult();

    // Input stack was size 2 so output is as well.
    assertEquals(2, overallResult.size());

    StackTrace.Builder retracedBuilder = StackTrace.builder();
    for (RetraceStackFrameAmbiguousResult<StackTraceLine> frameResult : overallResult) {
      // The stack frames should not be ambiguous.
      assertFalse(frameResult.isAmbiguous());
      RetraceStackFrameResult<StackTraceLine> singleResult = frameResult.get(0);
      singleResult.forEach(retracedBuilder::add);
    }
    assertThat(retracedBuilder.build(), isSame(retraced));
  }
}
