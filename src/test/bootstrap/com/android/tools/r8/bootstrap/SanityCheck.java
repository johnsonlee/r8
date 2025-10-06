// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bootstrap;

import static com.android.tools.r8.utils.DescriptorUtils.JAVA_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.Version;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.CfUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StreamUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SanityCheck extends TestBase {

  private static final String SRV_PREFIX = "META-INF/services/";
  private static final String METADATA_EXTENSION =
      "com.android.tools.r8.jetbrains.kotlin.metadata.internal.extensions.MetadataExtensions";
  private static final String EXT_IN_SRV = SRV_PREFIX + METADATA_EXTENSION;
  private static final String THREADING_MODULE_SERVICE_FILE =
      "META-INF/services/com.android.tools.r8.threading.ThreadingModuleProvider";

  @Parameters
  public static TestParametersCollection data() {
    return TestParameters.builder().withNoneRuntime().build();
  }

  public SanityCheck(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private void checkJarContent(
      Path jar, boolean allowDirectories, ClassNameMapper mapping, Predicate<String> entryTester)
      throws Exception {
    BooleanBox licenseSeen = new BooleanBox();
    Set<String> apiDatabaseFiles = Sets.newHashSet("resources/new_api_database.ser");
    Set<String> r8AssistantRuntime =
        ImmutableSet.of(
            "ReflectiveEventType.java",
            "ReflectiveOperationJsonLogger.java",
            "ReflectiveOracle.java",
            "EmptyReflectiveOperationReceiver.java",
            "ReflectiveOperationReceiver.java",
            "ReflectiveOperationLogger.java");
    try {
      ZipUtils.iter(
          jar,
          (entry, input) -> {
            String name = entry.getName();
            if (ZipUtils.isClassFile(name) || FileUtils.isKotlinBuiltinsFile(name)) {
              assertThat(name, startsWith("com/android/tools/r8/"));
              if (ZipUtils.isClassFile(name)) {
                byte[] classFileBytes = StreamUtils.streamToByteArrayClose(input);
                String sourceFile = extractSourceFile(classFileBytes);
                if (!r8AssistantRuntime.contains(sourceFile)) {
                  if (mapping != null) {
                    assertNotNull(sourceFile);
                    if (Version.isMainVersion()) {
                      assertTrue(sourceFile, sourceFile.startsWith("R8_"));
                      assertEquals(
                          sourceFile.contains("+excldeps") ? 117 : 108, sourceFile.length());
                    } else {
                      assertTrue(sourceFile, sourceFile.startsWith("R8_" + Version.LABEL));
                      assertEquals(
                          68 + Version.LABEL.length() + (sourceFile.contains("+excldeps") ? 9 : 0),
                          sourceFile.length());
                    }
                  } else {
                    // Some class files from third party libraries does not have a SourceFile
                    // attribute.
                    if (sourceFile != null) {
                      assertTrue(
                          sourceFile, sourceFile.endsWith(".java") || sourceFile.endsWith(".kt"));
                    }
                  }
                } else {
                  String className = CfUtils.extractClassName(classFileBytes);
                  int lastPackageSeparator = className.lastIndexOf(JAVA_PACKAGE_SEPARATOR);
                  String expectedSourceFile = className.substring(lastPackageSeparator + 1);
                  int firstInnerclassSeparator = expectedSourceFile.indexOf('$');
                  if (firstInnerclassSeparator >= 0) {
                    expectedSourceFile = expectedSourceFile.substring(0, firstInnerclassSeparator);
                  }
                  expectedSourceFile += JAVA_EXTENSION;
                  assertEquals(expectedSourceFile, sourceFile);
                }
              }
            } else if (name.equals("META-INF/MANIFEST.MF")) {
              // Allow.
            } else if (name.equals("LICENSE")) {
              licenseSeen.set();
            } else if (name.equals(THREADING_MODULE_SERVICE_FILE)) {
              // Allow.
            } else if (entryTester.test(name)) {
              // Allow.
            } else if (apiDatabaseFiles.contains(name)) {
              // Allow all api database files.
              apiDatabaseFiles.remove(name);
            } else if (name.endsWith("/")) {
              assertTrue("Unexpected directory entry in" + jar, allowDirectories);
            } else {
              fail("Unexpected entry '" + name + "' in " + jar);
            }
          });
      assertTrue(apiDatabaseFiles.isEmpty());
      assertTrue(
          "No LICENSE entry found in " + jar, licenseSeen.isAssigned() && licenseSeen.isTrue());
    } catch (IOException e) {
      if (!Files.exists(jar)) {
        throw new NoSuchFileException(jar.toString());
      } else {
        throw e;
      }
    }
  }

  private void checkLibJarContent(Path jar, Path map) throws Exception {
    if (!Files.exists(jar)) {
      return;
    }
    assertTrue(Files.exists(map));
    ClassNameMapper mapping = ClassNameMapper.mapperFromFile(map);
    checkJarContent(jar, false, mapping, name -> metadataExtensionTester(name, mapping));
  }

  private void checkJarContent(Path jar) throws Exception {
    if (!Files.exists(jar)) {
      return;
    }
    checkJarContent(jar, true, null, name -> metadataExtensionTester(name, null));
  }

  private boolean metadataExtensionTester(String name, ClassNameMapper mapping) {
    if (name.equals(EXT_IN_SRV)) {
      assertNull(mapping);
      return true;
    }
    if (mapping != null && name.startsWith(SRV_PREFIX)) {
      String obfuscatedName = name.substring(SRV_PREFIX.length());
      String originalName =
          mapping.getObfuscatedToOriginalMapping().original
              .getOrDefault(obfuscatedName, obfuscatedName);
      if (originalName.equals(METADATA_EXTENSION)) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void testLibJarsContent() throws Exception {
    assumeTrue(ToolHelper.isTestingR8Lib());
    checkLibJarContent(ToolHelper.R8LIB_JAR, ToolHelper.R8LIB_MAP);
    checkLibJarContent(ToolHelper.R8LIB_EXCLUDE_DEPS_JAR, ToolHelper.R8LIB_EXCLUDE_DEPS_MAP);
  }

  @Test
  public void testJarsContent() throws Exception {
    checkJarContent(ToolHelper.getR8WithRelocatedDeps());
  }
}
