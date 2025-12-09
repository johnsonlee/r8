// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules.sanitychecks;

import static com.android.tools.r8.ToolHelper.PROCESS_KEEP_RULES_JAR;
import static com.android.tools.r8.ToolHelper.PROCESS_KEEP_RULES_MAP;
import static com.android.tools.r8.ToolHelper.isWindows;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.Version;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StreamUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test relies on build/libs/processkeepruleslib.jar being built. This test therefore only runs
 * when test.py is run with the --only_internal flag, which adds a dependency on
 * :main:processKeepRulesLibWithRelocatedDeps.
 */
@RunWith(Parameterized.class)
public class ProcessKeepRulesSanityCheckTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testProcessKeepRulesJarContent() throws Exception {
    assertTrue(Files.exists(PROCESS_KEEP_RULES_JAR));
    assertTrue(Files.exists(PROCESS_KEEP_RULES_MAP));
    BooleanBox apiDatabaseSeen = new BooleanBox();
    BooleanBox licenseSeen = new BooleanBox();
    BooleanBox manifestSeen = new BooleanBox();
    BooleanBox versionSeen = new BooleanBox();
    ZipUtils.iter(
        PROCESS_KEEP_RULES_JAR,
        (entry, input) -> {
          String name = entry.getName();
          if (ZipUtils.isClassFile(name) || FileUtils.isKotlinBuiltinsFile(name)) {
            assertThat(name, startsWith("com/android/tools/r8/"));
            if (ZipUtils.isClassFile(name)) {
              byte[] classFileBytes = StreamUtils.streamToByteArrayClose(input);
              String sourceFile = extractSourceFile(classFileBytes);
              assertNotNull(sourceFile);
              if (Version.isMainVersion()) {
                assertTrue(sourceFile, sourceFile.startsWith("R8_"));
                assertEquals(108, sourceFile.length());
              } else {
                assertTrue(sourceFile, sourceFile.startsWith("R8_" + Version.LABEL));
                assertEquals(68 + Version.LABEL.length(), sourceFile.length());
              }
              versionSeen.or(name.equals("com/android/tools/r8/Version.class"));
            }
          } else if (name.equals("META-INF/MANIFEST.MF")) {
            manifestSeen.set();
          } else if (name.equals("LICENSE")) {
            licenseSeen.set();
          } else if (name.equals("resources/new_api_database.ser")) {
            apiDatabaseSeen.set();
          } else if (name.endsWith("/")) {
            fail("Unexpected directory entry '" + name + "'");
          } else {
            fail("Unexpected entry '" + name + "'");
          }
        });

    // TODO(b/466252770): The API database is still in the JAR when building on Windows.
    assertTrue("Api database entry FOUND", apiDatabaseSeen.isFalse() || isWindows());
    assertTrue("LICENSE entry NOT FOUND", licenseSeen.isTrue());
    assertTrue("META-INF/MANIFEST.MF entry NOT FOUND", manifestSeen.isTrue());
    assertTrue("com/android/tools/r8/Version.class entry NOT FOUND", versionSeen.isTrue());
  }
}
