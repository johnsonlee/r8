// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.gson;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.gson.GsonNoDefaultConstructorTest.Data;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GsonDefaultConstructorTest extends GsonTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableUnsafe;

  @Parameters(name = "{0}, enableUnsafe {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            // Gson use java.lang.ReflectiveOperationException causing VerifyError on Dalvik 4.0.4.
            .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
            .withCfRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build(),
        BooleanUtils.values());
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  private void inspect(CodeInspector inspector) {
    ClassSubject dataSubject = inspector.clazz(Data.class);
    assertThat(dataSubject, isPresentAndRenamed());
    assertThat(dataSubject.uniqueFieldWithOriginalName("s"), isPresentAndRenamed());
    assertThat(dataSubject.init(), isPresent());
    assertThat(dataSubject.init("java.lang.String"), isAbsent());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(b -> addRuntimeLibrary(b, parameters))
        .addInnerClasses(getClass())
        .apply(GsonTestBase::addGsonLibraryAndKeepRules)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class, enableUnsafe ? "enable" : "disable")
        .inspect(this::serializedNamePresentAndRenamed)
        .inspect(this::inspect)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testProguard() throws Exception {
    parameters.assumeProguardTestParameters();
    testForProguard(ProguardVersion.getLatest())
        .apply(b -> addRuntimeLibrary(b, parameters))
        .addInnerClasses(getClass())
        .apply(GsonTestBase::addGsonLibraryAndKeepRules)
        .addKeepMainRule(TestClass.class)
        .addDontNote("*")
        .addDontWarn(getClass())
        .run(parameters.getRuntime(), TestClass.class, enableUnsafe ? "enable" : "disable")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Data {
    @SerializedName("s")
    private final String s;

    public Data() {
      this.s = "default";
    }

    public Data(String s) {
      this.s = s;
    }

    public String getS() {
      return s;
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      GsonBuilder builder = new GsonBuilder();
      if (args[0].equals("disable")) {
        builder.disableJdkUnsafe();
      }
      Gson gson = builder.create();
      System.out.println(gson.fromJson("{\"s\":\"Hello, world!\"}", Data.class).getS());
    }
  }
}
