// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.OriginMatcher.hasPart;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class ProcessKeepRulesCommandTest extends TestBase {

  private static final Map<String, String> testRules =
      ImmutableMap.<String, String>builder()
          .put("-dontoptimize", "-dontoptimize not allowed in library consumer rules.")
          .put("-dontobfuscate", "-dontobfuscate not allowed in library consumer rules.")
          .put("-dontshrink", "-dontshrink not allowed in library consumer rules.")
          .put("-repackageclasses", "-repackageclasses not allowed in library consumer rules.")
          .put("-printconfiguration", "-printconfiguration not allowed in library consumer rules.")
          .put("-printmapping", "-printmapping not allowed in library consumer rules.")
          .put("-applymapping foo", "-applymapping not allowed in library consumer rules.")
          .put("-injars foo", "-injars not allowed in library consumer rules.")
          .put("-libraryjars foo", "-libraryjars not allowed in library consumer rules.")
          .put("-printseeds", "-printseeds not allowed in library consumer rules.")
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
              "-renamesourcefileattribute",
              "-renamesourcefileattribute not allowed in library consumer rules.")
          .build();

  @Parameter(1)
  public TestParameters parameters;

  @Parameter(0)
  public Map.Entry<String, String> configAndExpectedDiagnostic;

  @Parameterized.Parameters(name = "{1}, configAndExpectedDiagnostic = {0}")
  public static List<Object[]> data() throws IOException {
    return buildParameters(testRules.entrySet(), getTestParameters().withNoneRuntime().build());
  }

  @Test
  public void test() throws Exception {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    Path tempFile = getStaticTemp().newFile().toPath();
    Files.write(tempFile, configAndExpectedDiagnostic.getKey().getBytes(StandardCharsets.UTF_8));
    ProcessKeepRulesCommand command =
        ProcessKeepRulesCommand.builder(diagnostics)
            .addKeepRuleFiles(ImmutableList.of(tempFile))
            .setLibraryConsumerRuleValidation(true)
            .build();
    try {
      ProcessKeepRules.run(command);
      fail("Expect the compilation to fail.");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(
          allOf(
              configAndExpectedDiagnostic.getKey().startsWith("-keepattributes")
                  ? diagnosticType(KeepAttributeLibraryConsumerRuleDiagnostic.class)
                  : diagnosticType(GlobalLibraryConsumerRuleDiagnostic.class),
              diagnosticOrigin(hasPart(tempFile.toString())),
              diagnosticMessage(equalTo(configAndExpectedDiagnostic.getValue()))));
    }
  }
}
