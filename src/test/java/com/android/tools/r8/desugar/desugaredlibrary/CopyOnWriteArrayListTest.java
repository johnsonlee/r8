// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CopyOnWriteArrayListTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
            .build(),
        getJdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public CopyOnWriteArrayListTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Executor.class)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            r -> r.assertSuccessWithOutputLines("B", "A", "A", "B"),
            // TODO(b/457569906): This fails with desugared libraru and min API below 24, as
            //  desugared library use the default sort implementation from java.util.List,
            //  which in turn use List.set not supported by CopyOnWriteArrayList.
            r -> r.assertFailureWithErrorThatThrows(UnsupportedOperationException.class));
  }

  private static class Executor {

    public static void main(String[] args) {
      List<String> coll = new CopyOnWriteArrayList<>();
      coll.add("B");
      coll.add("A");
      coll.forEach(System.out::println);
      coll.sort(Comparator.naturalOrder());
      coll.forEach(System.out::println);
    }
  }
}
