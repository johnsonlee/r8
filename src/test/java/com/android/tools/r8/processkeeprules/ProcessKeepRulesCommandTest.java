// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProcessKeepRulesCommandTest extends TestBase {

  private static final Map<String, String> testRules =
      ImmutableMap.<String, String>builder()
          .put("-dontoptimize", "-dontoptimize not allowed in library consumer rules.")
          .put("-dontobfuscate", "-dontobfuscate not allowed in library consumer rules.")
          .put("-dontshrink", "-dontshrink not allowed in library consumer rules.")
          .put("-repackageclasses", "-repackageclasses not allowed in library consumer rules.")
          .put("-applymapping foo", "-applymapping not allowed in library consumer rules.")
          .put("-injars foo", "-injars not allowed in library consumer rules.")
          .put("-libraryjars foo", "-libraryjars not allowed in library consumer rules.")
          .put("-printconfiguration", "-printconfiguration not allowed in library consumer rules.")
          .put("-printmapping", "-printmapping not allowed in library consumer rules.")
          .put("-printseeds", "-printseeds not allowed in library consumer rules.")
          .put("-printusage", "-printusage not allowed in library consumer rules.")
          .put(
              "-obfuscationdictionary foo",
              "-obfuscationdictionary not allowed in library consumer rules.")
          .put(
              "-classobfuscationdictionary foo",
              "-classobfuscationdictionary not allowed in library consumer rules.")
          .put(
              "-packageobfuscationdictionary foo",
              "-packageobfuscationdictionary not allowed in library consumer rules.")
          .put(
              "-flattenpackagehierarchy",
              "-flattenpackagehierarchy not allowed in library consumer rules.")
          .put(
              "-allowaccessmodification",
              "-allowaccessmodification not allowed in library consumer rules.")
          .put(
              "-keepattributes LineNumberTable",
              "Illegal attempt to keep the attribute 'LineNumberTable' in library consumer rules.")
          .put(
              "-keepattributes RuntimeInvisibleAnnotations",
              "Illegal attempt to keep the attribute 'RuntimeInvisibleAnnotations' in library"
                  + " consumer rules.")
          .put(
              "-keepattributes RuntimeInvisibleTypeAnnotations",
              "Illegal attempt to keep the attribute 'RuntimeInvisibleTypeAnnotations' in library"
                  + " consumer rules.")
          .put(
              "-keepattributes RuntimeInvisibleParameterAnnotations",
              "Illegal attempt to keep the attribute 'RuntimeInvisibleParameterAnnotations' in"
                  + " library consumer rules.")
          .put(
              "-keepattributes SourceFile",
              "Illegal attempt to keep the attribute 'SourceFile' in library consumer rules.")
          .put(
              "-maximumremovedandroidloglevel 2",
              "-maximumremovedandroidloglevel <int> not allowed in library consumer rules.")
          .put(
              "-renamesourcefileattribute",
              "-renamesourcefileattribute not allowed in library consumer rules.")
          .put(
              "-shrinkunusedprotofields",
              "-shrinkunusedprotofields not allowed in library consumer rules.")
          .put(
              "-whyareyoukeeping class *",
              "-whyareyoukeeping not allowed in library consumer rules.")
          .put(
              "-whyareyounotobfuscating class *",
              "-whyareyounotobfuscating not allowed in library consumer rules.")
          .put(
              "-whyareyounotinlining class * { *; }",
              "-whyareyounotinlining not allowed in library consumer rules.")
          .put(
              "-processkotlinnullchecks",
              "-processkotlinnullchecks not allowed in library consumer rules.")
          .put(
              "-processkotlinnullchecks keep",
              "-processkotlinnullchecks not allowed in library consumer rules.")
          .put(
              "-processkotlinnullchecks remove_message",
              "-processkotlinnullchecks not allowed in library consumer rules.")
          .put(
              "-processkotlinnullchecks remove",
              "-processkotlinnullchecks not allowed in library consumer rules.")
          .build();

  @Parameter(1)
  public TestParameters parameters;

  @Parameter(0)
  public Map.Entry<String, String> configAndExpectedDiagnostic;

  @Parameters(name = "{1}, configAndExpectedDiagnostic = {0}")
  public static List<Object[]> data() throws IOException {
    return buildParameters(testRules.entrySet(), getTestParameters().withNoneRuntime().build());
  }

  @Test
  public void test() throws Exception {
    String rules = configAndExpectedDiagnostic.getKey();
    Origin origin = new PathOrigin(Paths.get("keep.txt"));
    try {
      validate(
          rules,
          origin,
          diagnostics ->
              diagnostics.assertErrorsMatch(
                  allOf(
                      rules.startsWith("-keepattributes")
                          ? diagnosticType(KeepAttributeLibraryConsumerRuleDiagnostic.class)
                          : diagnosticType(LibraryConsumerRuleDiagnostic.class),
                      diagnosticOrigin(equalTo(origin)),
                      diagnosticMessage(equalTo(configAndExpectedDiagnostic.getValue())))));
      fail("Expect the compilation to fail.");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    // Rerun validation after filtering. This should succeed without diagnostics.
    validate(filter(rules, origin), origin, TestDiagnosticMessagesImpl::assertNoMessages);
  }

  private String filter(String rules, Origin origin) throws CompilationFailedException {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    StringBuilder result = new StringBuilder();
    ProcessKeepRulesCommand command =
        ProcessKeepRulesCommand.builder(diagnostics)
            .addKeepRules(rules, origin)
            .setFilteredKeepRulesConsumer((s, h) -> result.append(s))
            .build();
    ProcessKeepRules.run(command);
    diagnostics.assertNoMessages();
    return result.toString();
  }

  private void validate(
      String rules, Origin origin, Consumer<TestDiagnosticMessagesImpl> diagnosticsInspector)
      throws CompilationFailedException {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      ProcessKeepRulesCommand command =
          ProcessKeepRulesCommand.builder(diagnostics)
              .addKeepRules(rules, origin)
              .setLibraryConsumerRuleValidation(true)
              .build();
      ProcessKeepRules.run(command);
      diagnosticsInspector.accept(diagnostics);
    } catch (CompilationFailedException e) {
      diagnosticsInspector.accept(diagnostics);
      throw e;
    }
  }
}
