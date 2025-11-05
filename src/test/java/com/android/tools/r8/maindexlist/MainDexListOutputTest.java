// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexListOutputTest extends TestBase {

  interface MyConsumer<T> {
    void accept(T element);
  }

  static class TestClass {
    public void f(MyConsumer<String> s) {
      s.accept("asdf");
    }

    public void g() {
      f(System.out::println);
    }
  }

  private static class TestMainDexListConsumer implements StringConsumer {
    public boolean called = false;
    public boolean finished = false;
    public StringBuilder builder = new StringBuilder();

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      called = true;
      builder.append(string);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      finished = true;
    }

    public void inspect(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
      List<ClassReference> mainDexList =
          StringUtils.splitLines(builder.toString()).stream()
              .map(line -> Reference.classFromDescriptor(DescriptorUtils.guessTypeDescriptor(line)))
              .collect(Collectors.toList());
      assertThat(mainDexList, hasItem(Reference.classFromClass(TestClass.class)));
      assertThat(
          mainDexList,
          hasItem(
              new TypeSafeMatcher<ClassReference>() {

                @Override
                public void describeTo(Description description) {}

                @Override
                protected boolean matchesSafely(ClassReference classReference) {
                  return syntheticItems.isExternalLambda(classReference);
                }
              }));
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MainDexListOutputTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoMainDex() throws Exception {
    Path mainDexListOutput = temp.getRoot().toPath().resolve("main-dex-output.txt");
    testForR8(Backend.DEX)
        .addProgramClasses(HelloWorldMain.class)
        .setMainDexListConsumer(new FileConsumer(mainDexListOutput))
        .setMinApi(AndroidApiLevel.K)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyErrors()
                    .assertErrorsMatch(
                        diagnosticMessage(
                            containsString(
                                "--main-dex-list-output requires --main-dex-rules and/or"
                                    + " --main-dex-list"))));
  }

  @Test
  public void testWithMainDex() throws Exception {
    Path mainDexRules = writeTextToTempFile(keepMainProguardConfiguration(HelloWorldMain.class));
    Path mainDexListOutput = temp.getRoot().toPath().resolve("main-dex-output.txt");
    testForR8(Backend.DEX)
        .addProgramClasses(HelloWorldMain.class)
        .addDontShrink()
        .addDontObfuscate()
        .setMinApi(AndroidApiLevel.K)
        .addMainDexRuleFiles(mainDexRules)
        .setMainDexListConsumer(new FileConsumer(mainDexListOutput))
        .compile();
    // Main dex list with the single class.
    assertEquals(
        ImmutableList.of(HelloWorldMain.class.getTypeName().replace('.', '/') + ".class"),
        FileUtils.readAllLines(mainDexListOutput)
            .stream()
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList()));
  }

  @Test
  public void testD8DesugaredLambdasInMainDexList() throws Exception {
    TestMainDexListConsumer consumer = new TestMainDexListConsumer();
    testForD8()
        .setMinApi(AndroidApiLevel.K)
        .addProgramClasses(ImmutableList.of(TestClass.class, MyConsumer.class))
        .addMainDexKeepClassAndMemberRules(TestClass.class)
        .setMainDexListConsumer(consumer)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(consumer::inspect);
    assertTrue(consumer.called);
    assertTrue(consumer.finished);
  }

  @Test
  public void testD8DesugaredLambdasInMainDexListMerging() throws Exception {
    // Build intermediate dex code first.
    Path dexOutput =
        testForD8()
            .setMinApi(AndroidApiLevel.K)
            .addProgramClasses(ImmutableList.of(TestClass.class, MyConsumer.class))
            .setIntermediate(true)
            .compile()
            .writeToZip();
    // Now test that when merging with a main dex list it is correctly updated.
    TestMainDexListConsumer consumer = new TestMainDexListConsumer();
    testForD8()
        .setMinApi(AndroidApiLevel.K)
        .addProgramFiles(dexOutput)
        .addMainDexKeepClassAndMemberRules(TestClass.class)
        .setMainDexListConsumer(consumer)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(consumer::inspect);
    assertTrue(consumer.called);
    assertTrue(consumer.finished);
  }
}
