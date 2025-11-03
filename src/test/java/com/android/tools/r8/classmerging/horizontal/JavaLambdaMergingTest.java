// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.codeinspector.RepackagingInspector;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class JavaLambdaMergingTest extends HorizontalClassMergingTestBase {

  public JavaLambdaMergingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void test() throws Exception {
    parameters.assumeDexRuntime();
    Box<RepackagingInspector> repackagingBox = new Box<>();
    R8FullTestBuilder testBuilder = testForR8(parameters.getBackend());
    testBuilder
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspectorIf(
            parameters.isDexRuntime(),
            inspector -> {
              RepackagingInspector repackaging = repackagingBox.get();
              SyntheticItemsTestUtils syntheticItems = testBuilder.getState().getSyntheticItems();
              Set<DexType> lambdaSources =
                  inspector.getSources().stream()
                      .filter(type -> syntheticItems.isExternalLambda(repackaging.getSource(type)))
                      .collect(Collectors.toSet());
              assertEquals(3, lambdaSources.size());
              ClassReference firstTarget =
                  inspector.getTarget(lambdaSources.iterator().next().asClassReference());
              for (DexType lambdaSource : lambdaSources) {
                assertEquals(firstTarget, inspector.getTarget(lambdaSource.asClassReference()));
              }
            })
        .addRepackagingInspector(repackagingBox::set)
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .collectSyntheticItems()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private static boolean isLambda(DexType type, SyntheticItemsTestUtils syntheticItems) {
    return syntheticItems.isExternalLambda(type.asClassReference());
  }

  public static class Main {

    public static void main(String[] args) {
      HelloGreeter helloGreeter =
          System.currentTimeMillis() > 0
              ? () -> System.out.print("Hello")
              : () -> {
                throw new RuntimeException();
              };
      WorldGreeter worldGreeter =
          System.currentTimeMillis() > 0
              ? () -> System.out.println(" world!")
              : () -> {
                throw new RuntimeException();
              };
      helloGreeter.hello();
      worldGreeter.world();
    }
  }

  interface HelloGreeter {

    void hello();
  }

  interface WorldGreeter {

    void world();
  }
}
