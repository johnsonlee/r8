// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.nio.file.Path;
import java.util.function.Consumer;

public class D8NonLazyRunExamplesAndroidOTest extends D8IncrementalRunExamplesAndroidOTest {
  class D8LazyTestRunner extends D8IncrementalTestRunner {

    D8LazyTestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    void addClasspathReference(
        Path testJarFile,
        Consumer<Path> pathConsumer,
        Consumer<ClassFileResourceProvider> providerConsumer) {
      pathConsumer.accept(testJarFile);
    }

    @Override
    void addLibraryReference(
        Path location,
        Consumer<Path> pathConsumer,
        Consumer<ClassFileResourceProvider> providerConsumer) {
      pathConsumer.accept(location);
    }

    @Override
    D8LazyTestRunner self() {
      return this;
    }
  }

  @Override
  D8IncrementalTestRunner test(String testName, String packageName, String mainClass) {
    D8IncrementalTestRunner result = new D8LazyTestRunner(testName, packageName, mainClass);
    // Eliminate the tool specific marker in the resulting dex applications.
    // This allows for byte-wise comparison of the results.
    result.withOptionConsumer(options -> options.setMarker(null));
    return result;
  }
}
