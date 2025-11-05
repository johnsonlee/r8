// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.staticinterfacemethod;

import static com.android.tools.r8.desugar.staticinterfacemethod.InvokeStaticDesugarTest.Library.foo;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.PartialCompilationTestParameters;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeStaticDesugarTest extends TestBase {

  private static final String EXPECTED = "Hello World!";

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean intermediate;

  @Parameters(name = "{0}, intermediate in first step: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  @Test
  public void testDesugar() throws Exception {
    // Intermediate not used in this test.
    assumeFalse(intermediate);

    TestRunResult<?> runResult =
        testForDesugaring(parameters)
            .addLibraryClasses(Library.class)
            .addProgramClasses(Main.class)
            .addRunClasspathFiles(compileRunClassPath())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      runResult.assertFailureWithErrorThatMatches(containsString("java.lang.VerifyError"));
    } else {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  @Test
  public void testDoubleDesugar() throws Exception {
    // Desugar using API level that cannot leave static interface invokes.
    TestCompileResult<?, ?> initialCompileResult =
        testForD8(Backend.CF, PartialCompilationTestParameters.NONE)
            .addLibraryClasses(Library.class)
            .addProgramClasses(Main.class)
            .collectSyntheticItems()
            .setMinApi(AndroidApiLevel.B)
            .apply(b -> b.asD8TestBuilder().setIntermediate(intermediate))
            .compile()
            .inspectWithSyntheticItems(
                (i, s) -> assertEquals(1, getSyntheticMethods(i, s, null).size()));

    testForDesugaring(parameters)
        .addLibraryClasses(Library.class)
        .addProgramFiles(initialCompileResult.writeToZip())
        .addRunClasspathFiles(compileRunClassPath())
        .collectSyntheticItems()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            // When double desugaring to API level below L two synthetics are seen.
            c ->
                DesugarTestConfiguration.isDesugared(c)
                    && (parameters.isCfRuntime()
                        || parameters
                            .getRuntime()
                            .asDex()
                            .getVm()
                            .isNewerThan(DexVm.ART_4_4_4_HOST))
                    && parameters.getApiLevel().isLessThan(AndroidApiLevel.L),
            r -> {
              assertEquals(intermediate ? 1 : 2, countSynthetics(r, initialCompileResult));
              r.assertSuccessWithOutputLines(EXPECTED);
            },
            // Don't inspect failing code, as inspection is only supported when run succeeds,
            // and testForDesugaring does not have separate compile where the code can be
            // inspected before running.
            c ->
                parameters.isDexRuntime()
                    && parameters
                        .getRuntime()
                        .asDex()
                        .getVm()
                        .isOlderThanOrEqual(DexVm.ART_4_4_4_HOST),
            r -> r.assertFailureWithErrorThatMatches(containsString("java.lang.VerifyError")),
            // When double desugaring to API level L and above one synthetics seen.
            r -> {
              assertEquals(1, countSynthetics(r, initialCompileResult));
              r.assertSuccessWithOutputLines(EXPECTED);
            });
  }

  private Path compileRunClassPath() throws Exception {
    if (parameters.isCfRuntime()) {
      return compileToZip(parameters, ImmutableList.of(), Library.class);
    } else {
      assert parameters.isDexRuntime();
      return testForD8(Backend.DEX, PartialCompilationTestParameters.NONE)
          .addProgramClasses(Library.class)
          .collectSyntheticItems()
          .setMinApi(parameters)
          .disableDesugaring()
          .addOptionsModification(
              options -> options.testing.allowStaticInterfaceMethodsForPreNApiLevel = true)
          .compile()
          .writeToZip();
    }
  }

  private int countSynthetics(
      SingleTestRunResult<?> r, TestCompileResult<?, ?> initialCompileResult) {
    try {
      if (r.isJvmTestRunResult()) {
        return getSyntheticMethods(r.inspector(), initialCompileResult.getSyntheticItems(), null)
            .size();
      } else {
        return getSyntheticMethods(
                r.inspector(), r.getSyntheticItems(), initialCompileResult.getSyntheticItems())
            .size();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Set<FoundMethodSubject> getSyntheticMethods(
      CodeInspector inspector,
      SyntheticItemsTestUtils syntheticItems,
      SyntheticItemsTestUtils initialSyntheticItems) {
    List<FoundClassSubject> syntheticClasses =
        inspector.allClasses().stream()
            .filter(
                c ->
                    syntheticItems.isExternalStaticInterfaceCall(c.getFinalReference())
                        || (initialSyntheticItems != null
                            && initialSyntheticItems.isExternalStaticInterfaceCall(
                                c.getFinalReference())))
            .collect(Collectors.toList());
    assertTrue(
        inspector.allClasses().size() == 1
            || (inspector.allClasses().size() == 2 && syntheticClasses.size() == 1)
            || (inspector.allClasses().size() == 3 && syntheticClasses.size() == 2));
    Set<FoundMethodSubject> methods = new HashSet<>();
    syntheticClasses.forEach(c -> methods.addAll(c.allMethods(m -> !m.isInstanceInitializer())));
    return methods;
  }

  public interface Library {

    static void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      foo();
    }
  }
}
