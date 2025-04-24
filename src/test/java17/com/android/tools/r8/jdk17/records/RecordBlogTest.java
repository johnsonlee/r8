// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordBlogTest extends TestBase {

  private static final String REFERENCE_OUTPUT_TEMPLATE = "Person[name=%s, age=42]";
  private static final String CLASS = RecordBlog.Person.class.getTypeName();
  private static final Map<String, String> KEEP_RULE_TO_OUTPUT_FORMAT =
      ImmutableMap.<String, String>builder()
          .put("-dontobfuscate\n-dontoptimize", "RecordBlog$Person[name=%s, age=42]")
          .put("", "a[a=%s]")
          .put("-keep,allowshrinking class " + CLASS, "RecordBlog$Person[a=%s]")
          .put(
              "-keepclassmembers,allowshrinking,allowoptimization class "
                  + CLASS
                  + " { <fields>; }",
              "a[name=%s]")
          .put("-keep class " + CLASS + " { <fields>; }", "RecordBlog$Person[name=%s, age=42]")
          .put(
              "-keepclassmembers,allowobfuscation,allowoptimization class "
                  + CLASS
                  + " { <fields>; }",
              "a[a=%s, b=42]")
          .build();

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  private boolean isCfRuntimeWithNativeRecordSupport() {
    return parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)
        && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  private String getExpectedOutputFromKeepRule(String keepRule) {
    String template = null;
    if (parameters.getPartialCompilationTestParameters().isIncludeAll()) {
      if (keepRule.isEmpty()
          || keepRule.equals(
              "-keepclassmembers,allowobfuscation,allowoptimization class "
                  + CLASS
                  + " { <fields>; }")
          || keepRule.equals(
              "-keepclassmembers,allowshrinking,allowoptimization class "
                  + CLASS
                  + " { <fields>; }")) {
        template = "a[name=%s, age=42]";
      } else if (keepRule.equals("-keep,allowshrinking class " + CLASS)) {
        template = "RecordBlog$Person[name=%s, age=42]";
      }
    }
    if (template == null) {
      template = KEEP_RULE_TO_OUTPUT_FORMAT.get(keepRule);
    }
    return getExpectedOutputFromTemplate(template);
  }

  private String getExpectedOutputFromTemplate(String template) {
    return StringUtils.lines(String.format(template, "Jane"), String.format(template, "John"));
  }

  private boolean isExpectedOutput(String keepRule, Map<String, String> results) {
    if (parameters.getPartialCompilationTestParameters().isRandom()) {
      return true;
    }
    String actual = results.get(keepRule);
    String expected = getExpectedOutputFromKeepRule(keepRule);
    return expected.equals(actual);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(isCfRuntimeWithNativeRecordSupport());
    testForJvm(parameters)
        .addProgramClassesAndInnerClasses(RecordBlog.class)
        .run(parameters.getRuntime(), RecordBlog.class)
        .assertSuccessWithOutput(getExpectedOutputFromTemplate(REFERENCE_OUTPUT_TEMPLATE));
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClassesAndInnerClasses(RecordBlog.class)
        .run(parameters.getRuntime(), RecordBlog.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r ->
                r.assertSuccessWithOutput(getExpectedOutputFromTemplate(REFERENCE_OUTPUT_TEMPLATE)),
            r -> r.assertFailureWithErrorThatThrows(ClassNotFoundException.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    Map<String, String> results = new HashMap<>();
    KEEP_RULE_TO_OUTPUT_FORMAT.forEach(
        (kr, outputFormat) -> {
          try {
            R8TestBuilder<?, R8TestRunResult, ?> builder =
                testForR8(parameters)
                    .addProgramClassesAndInnerClasses(RecordBlog.class)
                    .addKeepRules(kr)
                    .addKeepMainRule(RecordBlog.class);
            String res;
            if (parameters.isCfRuntime()) {
              res =
                  builder
                      .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
                      .run(parameters.getRuntime(), RecordBlog.class)
                      .assertSuccess()
                      .getStdOut();
            } else {
              res = builder.run(parameters.getRuntime(), RecordBlog.class).getStdOut();
            }
            results.put(kr, res);
          } catch (Exception e) {
            // Preserve AssumptionViolatedException.
            if (e instanceof RuntimeException) {
              throw (RuntimeException) e;
            } else {
              throw new RuntimeException(e);
            }
          }
        });
    boolean success = true;
    for (String kr : KEEP_RULE_TO_OUTPUT_FORMAT.keySet()) {
      if (!isExpectedOutput(kr, results)) {
        success = false;
      }
    }
    if (!success) {
      for (String kr : KEEP_RULE_TO_OUTPUT_FORMAT.keySet()) {
        if (!isExpectedOutput(kr, results)) {
          System.out.println("==========");
          System.out.println("Keep rules:\n" + kr + "\n");
          System.out.println("Expected:\n" + getExpectedOutputFromKeepRule(kr));
          System.out.println("Got:\n" + results.get(kr));
        }
      }
      System.out.println("==========");
    }
    assertTrue(success);
  }
}
