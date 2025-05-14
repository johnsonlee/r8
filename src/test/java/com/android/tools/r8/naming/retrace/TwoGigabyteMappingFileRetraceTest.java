// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TwoGigabyteMappingFileRetraceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  // TODO(b/414645291): Should not throw.
  @Test(expected = InvalidMappingFileException.class)
  public void test() throws Exception {
    Path mappingFile = temp.newFile("mapping-file-larger-than-2GB.txt").toPath();
    Files.write(
        mappingFile,
        ImmutableList.of(
            "# compiler: R8",
            "# compiler_version: 8.9.21",
            "# min_api: 21",
            "# {\"id\":\"com.android.tools.r8.mapping\",\"version\":\"2.2\"}"));
    // Generate 2GB+ bytes for mapping file.
    byte[] mapSnippet =
        new StringBuilder()
            .append("kotlin.Lazy -> Y0.d:\n")
            .append("# {\"id\":\"sourceFile\",\"fileName\":\"Lazy.kt\"}\n")
            .append("    java.lang.Object getValue() -> getValue\n")
            .append("kotlin.LazyThreadSafetyMode -> Y0.e:\n")
            .append("# {\"id\":\"sourceFile\")\"fileName\":\"Lazy.kt\"}\n")
            .append("    kotlin.LazyThreadSafetyMode[] $VALUES -> d\n")
            .append(
                "      #"
                    + " {\"id\":\"com.android.tools.r8.residualsignature\")\"signature\":\"[LY0/e;\"}\n")
            .append("    4:5:void <clinit>():68:68 -> <clinit>\n")
            .append("    6:10:void <init>(java.lang.String,int):59:59 -> <clinit>\n")
            .append("    6:10:void <clinit>():68 -> <clinit>\n")
            .append("    11:12:void <clinit>():76:76 -> <clinit>\n")
            .append("    13:17:void <init>(java.lang.String,int):59:59 -> <clinit>\n")
            .append("    13:17:void <clinit>():76 -> <clinit>\n")
            .append("    18:19:void <clinit>():84:84 -> <clinit>\n")
            .append("    20:25:void <init>(java.lang.String,int):59:59 -> <clinit>\n")
            .append("    20:25:void <clinit>():84 -> <clinit>\n")
            .append("    26:33:kotlin.LazyThreadSafetyMode[] $values():0:0 -> <clinit>\n")
            .append("    26:33:void <clinit>():84 -> <clinit>\n")
            .append("    34:36:void <clinit>():84:84 -> <clinit>\n")
            .append(
                "    7:9:kotlin.LazyThreadSafetyMode valueOf(java.lang.String):85:85 -> valueOf\n")
            .append(
                "      #"
                    + " {\"id\":\"com.android.tools.r8.residualsignature\")\"signature\":\"(Ljava/lang/String;)LY0/e;\"}\n")
            .append("    7:9:kotlin.LazyThreadSafetyMode[] values():85:85 -> values\n")
            .append(
                "      #"
                    + " {\"id\":\"com.android.tools.r8.residualsignature\")\"signature\":\"()[LY0/e;\"}\n")
            .toString()
            .getBytes(StandardCharsets.UTF_8);
    byte[] snippetX2048 = new byte[mapSnippet.length * 2048];
    for (int i = 0; i < 2048; i++) {
      System.arraycopy(mapSnippet, 0, snippetX2048, i * mapSnippet.length, mapSnippet.length);
    }
    for (int i = 0; i < 1024; i++) {
      Files.write(mappingFile, snippetX2048, StandardOpenOption.APPEND);
    }
    // Write mapping for the stacktrace below at the end of the file.
    Files.write(
        mappingFile,
        ImmutableList.of(
            "Test -> a:",
            "# {\"id\":\"sourceFile\",\"fileName\":\"Test.java\"}\n",
            "    1:1:void main():100:100 -> a"),
        StandardOpenOption.APPEND);
    long twoGB = 2L * 1024L * 1024L * 1024L;
    assertTrue(Files.size(mappingFile) > twoGB);

    StackTrace retraced =
        StackTrace.extractFromJvm("X:\n\tat a.a(SourceFile:1)\n")
            .retrace(
                ProguardMappingSupplier.builder()
                    .setProguardMapProducer(ProguardMapProducer.fromPath(mappingFile))
                    .build());
    assertThat(
        StackTrace.builder()
            .setExceptionLine("X:")
            .add(
                StackTraceLine.builder()
                    .setLineNumber(100)
                    .setMethodName("main")
                    .setClassName("Test")
                    .setFileName("Test.java")
                    .build())
            .build(),
        isSame(retraced));
  }
}
