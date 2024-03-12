// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.serviceloader;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderRewritingLineSeparatorTest extends ServiceLoaderTestBase {

  private final TestParameters parameters;
  private final Separator lineSeparator;

  private final String EXPECTED_OUTPUT =
      StringUtils.lines("Hello World!", "Hello World!", "Hello World!");

  enum Separator {
    WINDOWS,
    LINUX;

    public String getSeparator() {
      switch (this) {
        case WINDOWS:
          return "\r\n";
        case LINUX:
          return "\n";
        default:
          assert false;
      }
      return null;
    }
  }

  @Parameters(name = "{0}, separator: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), Separator.values());
  }

  public ServiceLoaderRewritingLineSeparatorTest(TestParameters parameters, Separator separator) {
    this.parameters = parameters;
    this.lineSeparator = separator;
  }

  @Test
  public void testRewritingWithMultipleWithLineSeparator()
      throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(ServiceLoaderRewritingTest.class)
        .addKeepMainRule(ServiceLoaderRewritingTest.MainRunner.class)
        .setMinApi(parameters)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.join(
                        lineSeparator.getSeparator(),
                        ServiceLoaderRewritingTest.ServiceImpl.class.getTypeName(),
                        ServiceLoaderRewritingTest.ServiceImpl2.class.getTypeName())
                    .getBytes(),
                "META-INF/services/" + ServiceLoaderRewritingTest.Service.class.getTypeName(),
                Origin.unknown()))
        .compile()
        .writeToZip(path)
        .run(parameters.getRuntime(), ServiceLoaderRewritingTest.MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT + StringUtils.lines("Hello World 2!"))
        // Check that we have actually rewritten the calls to ServiceLoader.load.
        .inspect(inspector -> assertEquals(0, getServiceLoaderLoads(inspector)));

    // Check that we have removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    assertNull(zip.getEntry("META-INF/services"));
  }
}
