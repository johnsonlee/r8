// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ExtractR8Rules;
import com.android.tools.r8.ExtractR8RulesCommand;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryProvidedProguardRulesExtractR8RulesTest
    extends LibraryProvidedProguardRulesTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean includeOriginComments;

  @Parameters(name = "{0}, includeOriginComments: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  private static final String EXPECTED_A = StringUtils.lines("-keep class A1", "-keep class A2");

  private static final String EXPECTED_B = StringUtils.lines("-keep class B1", "-keep class B2");

  private static final String EXPECTED_C = StringUtils.lines("-keep class C1", "-keep class C2");

  private static final String EXPECTED_D = StringUtils.lines("-keep class D1", "-keep class D2");

  private static final String EXPECTED_E = StringUtils.lines("-keep class E1", "-keep class E2");

  private static final String EXPECTED_X = StringUtils.lines("-keep class X1", "-keep class X2");

  private List<Path> buildLibraries() throws Exception {
    List<Path> result = new ArrayList<>();
    List<Pair<String, String>> rules = new ArrayList<>();
    rules.add(new Pair<>("META-INF/com.android.tools/r8/test1.pro", "-keep class A1"));
    rules.add(new Pair<>("META-INF/com.android.tools/r8/test2.pro", "-keep class A2"));
    rules.add(new Pair<>("META-INF/com.android.tools/r8-from-4.0.0/test1.pro", "-keep class B1"));
    rules.add(new Pair<>("META-INF/com.android.tools/r8-from-4.0.0/test2.pro", "-keep class B2"));
    rules.add(new Pair<>("META-INF/com.android.tools/r8-upto-8.1.0/test1.pro", "-keep class C1"));
    rules.add(new Pair<>("META-INF/com.android.tools/r8-upto-8.1.0/test2.pro", "-keep class C2"));
    rules.add(
        new Pair<>(
            "META-INF/com.android.tools/r8-from-5.0.0-upto-8.0.0/test1.pro", "-keep class D1"));
    rules.add(
        new Pair<>(
            "META-INF/com.android.tools/r8-from-5.0.0-upto-8.0.0/test2.pro", "-keep class D2"));
    rules.add(new Pair<>("META-INF/com.android.tools/r8-from-10.5.0/test1.pro", "-keep class E1"));
    rules.add(new Pair<>("META-INF/com.android.tools/r8-from-10.5.0/test2.pro", "-keep class E2"));
    rules.add(new Pair<>("META-INF/proguard/test1.pro", "-keep class X1"));
    rules.add(new Pair<>("META-INF/proguard/test2.pro", "-keep class X2"));
    Path folder = temp.newFolder().toPath();
    for (int i = 0; i < rules.size(); i++) {
      Path zipFile = folder.resolve("test" + i + ".jar");
      ZipBuilder jarBuilder = ZipBuilder.builder(zipFile);
      jarBuilder.addText(rules.get(i).getFirst(), rules.get(i).getSecond());
      result.add(jarBuilder.build());
    }
    return result;
  }

  private String filterOriginCommentsFromExtractedRules(String extractedRules) {
    List<String> newResult = new ArrayList<>();
    List<String> lines = StringUtils.splitLines(extractedRules);
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.equals("# Rules extracted from:")) {
        i++;
        assertThat(lines.get(i), containsString("test"));
        assertThat(lines.get(i), containsString(".jar"));
      } else {
        newResult.add(line);
      }
    }
    return StringUtils.lines(newResult);
  }

  private void runTestRulesConsumer(SemanticVersion compilerVersion, String expected)
      throws Exception {
    List<Path> libraries = buildLibraries();
    ExtractR8RulesCommand.Builder builder = ExtractR8RulesCommand.builder();
    libraries.forEach(builder::addProgramFiles);
    StringBuilder resultBuilder = new StringBuilder();
    ExtractR8RulesCommand command =
        builder
            .setRulesConsumer((s, h) -> resultBuilder.append(s))
            .setIncludeOriginComments(includeOriginComments)
            .setCompilerVersion(compilerVersion)
            .build();
    ExtractR8Rules.run(command);
    String extractedRules = resultBuilder.toString();
    if (includeOriginComments) {
      extractedRules = filterOriginCommentsFromExtractedRules(extractedRules);
    }
    assertEquals(expected, extractedRules);
  }

  private void runTestRulesOutputPath(SemanticVersion compilerVersion, String expected)
      throws Exception {
    List<Path> libraries = buildLibraries();
    ExtractR8RulesCommand.Builder builder = ExtractR8RulesCommand.builder();
    libraries.forEach(builder::addProgramFiles);
    Path rulesOutput = temp.newFile().toPath();
    ExtractR8RulesCommand command =
        builder
            .setRulesOutputPath(rulesOutput)
            .setIncludeOriginComments(includeOriginComments)
            .setCompilerVersion(compilerVersion)
            .build();
    ExtractR8Rules.run(command);
    String extractedRules = FileUtils.readTextFile(rulesOutput, StandardCharsets.UTF_8);
    if (includeOriginComments) {
      extractedRules = filterOriginCommentsFromExtractedRules(extractedRules);
    }
    assertEquals(expected, extractedRules);
  }

  private void runTest(SemanticVersion compilerVersion, String expected) throws Exception {
    runTestRulesConsumer(compilerVersion, expected);
    runTestRulesOutputPath(compilerVersion, expected);
  }

  @Test
  public void runTestVersion3() throws Exception {
    runTest(
        SemanticVersion.create(3, 0, 0),
        StringUtils.lines(EXPECTED_A.trim(), EXPECTED_C.trim(), EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion4() throws Exception {
    runTest(
        SemanticVersion.create(4, 0, 0),
        StringUtils.lines(
            EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_C.trim(), EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion5() throws Exception {
    runTest(
        SemanticVersion.create(5, 0, 0),
        StringUtils.lines(
            EXPECTED_A.trim(),
            EXPECTED_B.trim(),
            EXPECTED_C.trim(),
            EXPECTED_D.trim(),
            EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion7_99_99() throws Exception {
    runTest(
        SemanticVersion.create(7, 99, 99),
        StringUtils.lines(
            EXPECTED_A.trim(),
            EXPECTED_B.trim(),
            EXPECTED_C.trim(),
            EXPECTED_D.trim(),
            EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion8() throws Exception {
    runTest(
        SemanticVersion.create(8, 0, 0),
        StringUtils.lines(
            EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_C.trim(), EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion8_0_99() throws Exception {
    runTest(
        SemanticVersion.create(8, 0, 99),
        StringUtils.lines(
            EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_C.trim(), EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion8_1() throws Exception {
    runTest(
        SemanticVersion.create(8, 1, 0),
        StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion8_2() throws Exception {
    runTest(
        SemanticVersion.create(8, 2, 0),
        StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion10() throws Exception {
    runTest(
        SemanticVersion.create(10, 0, 0),
        StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_X.trim()));
  }

  @Test
  public void runTestVersion10_5() throws Exception {
    runTest(
        SemanticVersion.create(10, 5, 0),
        StringUtils.lines(
            EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_E.trim(), EXPECTED_X.trim()));
  }
}
