// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.sourcefile;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.R8CommandParser;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SourceFileTemplateTest extends TestBase {

  @Parameter(0)
  public boolean enablePartialCompilation;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, partial: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void testNoVariables() throws Exception {
    String template = "MySourceFile";
    compileWithSourceFileTemplate(
        template,
        inspector ->
            assertEquals(
                template,
                inspector.clazz(TestClass.class).getDexProgramClass().getSourceFile().toString()));
  }

  @Test
  public void testInvalidVariables() {
    assumeFalse(enablePartialCompilation);
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseSourceFileTemplate("My%Source%File", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            Arrays.asList(
                diagnosticMessage(containsString("Invalid template variable starting with %So")),
                diagnosticMessage(containsString("Invalid template variable starting with %Fi"))));
  }

  @Test
  public void testInvalidVariablesMix() {
    assumeFalse(enablePartialCompilation);
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseSourceFileTemplate("My%%MAP_IDJUNK", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            diagnosticMessage(containsString("Invalid template variable starting with %%MAP_")));
  }

  @Test
  public void testNoEscape() {
    assumeFalse(enablePartialCompilation);
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseSourceFileTemplate("My%%SourceFile", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            Arrays.asList(
                diagnosticMessage(containsString("Invalid template variable starting with %%S")),
                diagnosticMessage(containsString("Invalid template variable starting with %So"))));
  }

  @Test
  public void testMapId() throws Exception {
    String template = "MySourceFile %MAP_ID";
    compileWithSourceFileTemplate(
        template,
        inspector -> {
          String actual =
              inspector.clazz(TestClass.class).getDexProgramClass().getSourceFile().toString();
          assertThat(actual, startsWith("MySourceFile "));
          assertThat(actual, not(containsString("%")));
          assertEquals("MySourceFile ".length() + 7, actual.length());
        });
  }

  @Test
  public void testMapHash() throws Exception {
    String template = "MySourceFile %MAP_HASH";
    compileWithSourceFileTemplate(
        template,
        inspector -> {
          String actual =
              inspector.clazz(TestClass.class).getDexProgramClass().getSourceFile().toString();
          assertThat(actual, startsWith("MySourceFile "));
          assertThat(actual, not(containsString("%")));
          assertEquals("MySourceFile ".length() + 64, actual.length());
        });
  }

  @Test
  public void testMultiple() throws Exception {
    String template = "id %MAP_ID hash %MAP_HASH id %MAP_ID hash %MAP_HASH";
    compileWithSourceFileTemplate(
        template,
        inspector -> {
          String actual =
              inspector.clazz(TestClass.class).getDexProgramClass().getSourceFile().toString();
          assertEquals("id  hash  id  hash ".length() + 2 * 7 + 2 * 64, actual.length());
        });
  }

  private <E extends Exception> void compileWithSourceFileTemplate(
      String template, ThrowingConsumer<CodeInspector, E> inspection) throws Exception {
    R8TestBuilder<?, ?, ?> builder;
    if (enablePartialCompilation) {
      parameters.assumeCanUseR8Partial();
      builder = testForR8Partial(parameters.getBackend()).addR8IncludedClasses(TestClass.class);
    } else {
      builder = testForR8(parameters.getBackend()).addProgramClasses(TestClass.class);
    }
    builder
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .setSourceFileTemplate(template)
        .compile()
        .inspect(inspection);
  }

  private Builder parseSourceFileTemplate(String template, DiagnosticsHandler handler) {
    return R8CommandParser.parse(
        new String[] {"--source-file-template", template}, Origin.unknown(), handler);
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello world");
    }
  }
}
