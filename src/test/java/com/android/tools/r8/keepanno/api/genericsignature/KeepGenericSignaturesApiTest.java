// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.api.genericsignature;

import static com.android.tools.r8.R8TestBuilder.KeepAnnotationLibrary.LEGACY;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.JavaCompilerTool;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.KeepAnnoParameters;
import com.android.tools.r8.keepanno.KeepAnnoTestBase;
import com.android.tools.r8.keepanno.KeepAnnoTestUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepGenericSignaturesApiTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world", "hello, again!");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(getTestParameters().withDefaultCfRuntime().build());
  }

  public List<Class<?>> getLibraryClasses() {
    return ImmutableList.of(MyValueBox.class, MyOtherValueBox.class);
  }

  public List<Class<?>> getClientClasses() {
    return ImmutableList.of(MyValueBoxClient.class);
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addProgramClasses(getLibraryClasses())
        .addProgramClasses(getClientClasses())
        .run(MyValueBoxClient.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testSeparateCompilation() throws Exception {
    assumeFalse(parameters.isReference());
    assertTrue(parameters.isShrinker());
    Box<Path> lib = new Box<>();
    testForKeepAnno(parameters)
        .addProgramClasses(getLibraryClasses())
        .applyIfShrinker(b -> lib.set(b.compile().writeToZip()));
    assertNotNull(lib.get());

    // This checks that we can compile up against the library from Java source.
    // Doing so requires the generic signature to be kept.
    Path clientOut =
        JavaCompilerTool.create(parameters.parameters().asCfRuntime(), temp)
            .addSourceFiles(
                ListUtils.map(getClientClasses(), ToolHelper::getSourceFileForTestClass))
            .addClasspathFiles(lib.get(), KeepAnnoTestUtils.getKeepAnnoLib(temp, LEGACY))
            .compile();

    testForRuntime(parameters.parameters())
        .addRunClasspathFiles(lib.get(), clientOut)
        .run(parameters.getRuntime(), MyValueBoxClient.class)
        .assertSuccessWithOutput(EXPECTED);
  }
}
