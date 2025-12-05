// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.processkeeprules.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProcessKeepRulesApiBinaryCompatibilityTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ProcessKeepRulesApiBinaryCompatibilityTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testBinaryJarIsUpToDate() throws Exception {
    new ProcessKeepRulesApiTestCollection(temp).verifyCheckedInJarIsUpToDate();
  }

  @Test
  public void runCheckedInBinaryJar() throws Exception {
    new ProcessKeepRulesApiTestCollection(temp).runJunitOnCheckedInJar();
  }

  // Verify that when running with R8 lib, we are testing against processkeepruleslib.jar.
  @Test
  public void testTargetClassPathWithR8Lib() {
    assumeTrue(ToolHelper.isTestingR8Lib());
    List<Path> targetClasspath = new ProcessKeepRulesApiTestCollection(temp).getTargetClasspath();
    assertEquals(1, targetClasspath.size());
    assertTrue(targetClasspath.get(0).toString().endsWith("processkeepruleslib.jar"));
  }

  /**
   * To produce a new tests.jar run the code below. This will generate a new jar overwriting the
   * existing one. Remember to upload to cloud storage afterwards.
   */
  public static void main(String[] args) throws Exception {
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    new ProcessKeepRulesApiTestCollection(temp)
        .replaceJarForCheckedInTestClasses(TestDataSourceSet.TESTS_JAVA_8);
  }
}
