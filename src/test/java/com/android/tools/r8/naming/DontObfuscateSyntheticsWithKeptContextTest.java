// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DontObfuscateSyntheticsWithKeptContextTest extends TestBase {

  private static ClassReference getLambdaClassReference(SyntheticItemsTestUtils syntheticItems) {
    return syntheticItems.syntheticLambdaClass(Main.class, 0);
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableRepackaging;

  @Parameter(2)
  public boolean restrictRenaming;

  @Parameters(name = "{0}, repackage: {1}, restrict renaming: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().withPartialCompilation().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    R8TestBuilder<?, ?, ?> testBuilder = testForR8(parameters);
    testBuilder
        .addInnerClasses(getClass())
        // Allow access modification to ensure that the lambda is subject to repackaging.
        .addKeepRules("-keep,allowaccessmodification class ** { *; }")
        .applyIf(enableRepackaging, b -> b.addKeepRules("-repackageclasses"))
        .applyIf(
            parameters.getPartialCompilationTestParameters().isSome(),
            b -> b.addR8PartialR8OptionsModification(this::configure),
            b -> b.addOptionsModification(this::configure))
        // Verify repackaging happens when renaming is not restricted.
        .addRepackagingInspector(
            inspector -> {
              ClassReference lambdaClassReference =
                  getLambdaClassReference(testBuilder.getState().getSyntheticItems());
              ClassReference repackagedLambdaClassReference =
                  inspector.getTarget(lambdaClassReference);
              if (restrictRenaming) {
                assertEquals(lambdaClassReference, repackagedLambdaClassReference);
              } else {
                assertNotEquals(lambdaClassReference, repackagedLambdaClassReference);
              }
            })
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              if (!parameters.isRandomPartialCompilation()) {
                ClassReference lambdaClassReference = getLambdaClassReference(syntheticItems);
                ClassSubject lambdaClassSubject = inspector.clazz(lambdaClassReference);
                assertThat(lambdaClassSubject, isPresentAndRenamed(!restrictRenaming));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private void configure(InternalOptions options) {
    options.getSyntheticItemsOptions().restrictRenaming = restrictRenaming;
  }

  static class Main {

    public static void main(String[] args) {
      run(() -> System.out.println("Hello, world!"));
    }

    static void run(Runnable r) {
      r.run();
    }
  }
}
