// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageWithMainDexListTest extends RepackageTestBase {

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
            .build());
  }

  public RepackageWithMainDexListTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    Box<String> r8MainDexList = new Box<>();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        // -keep,allowobfuscation does not prohibit repackaging.
        .addKeepClassRulesWithAllowObfuscation(TestClass.class, OtherTestClass.class)
        .addKeepRules(
            "-keepclassmembers class " + TestClass.class.getTypeName() + " { <methods>; }")
        // Add a class that will be repackaged to the main dex list.
        .addMainDexListClasses(TestClass.class)
        .apply(this::configureRepackaging)
        .setMainDexListConsumer(ToolHelper.consumeString(r8MainDexList::set))
        // Debug mode to enable minimal main dex.
        .debug()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .apply(result -> checkCompileResult(result, r8MainDexList.get()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void checkCompileResult(R8TestCompileResult compileResult, String mainDexList)
      throws Exception {
    Path out = temp.newFolder().toPath();
    compileResult.app.writeToDirectory(out, OutputMode.DexIndexed);
    Path classes = out.resolve("classes.dex");
    Path classes2 = out.resolve("classes2.dex");
    inspectMainDex(new CodeInspector(classes, compileResult.getProguardMap()), mainDexList);
    inspectSecondaryDex(new CodeInspector(classes2, compileResult.getProguardMap()));
  }

  private void inspectMainDex(CodeInspector inspector, String mainDexList) {
    ClassSubject testClass = inspector.clazz(TestClass.class);
    assertThat(testClass, isPresentAndRenamed());
    assertThat(TestClass.class, isRepackaged(inspector));
    List<String> mainDexTypeNames = StringUtils.splitLines(mainDexList);
    assertEquals(1, mainDexTypeNames.size());
    assertEquals(testClass.getFinalBinaryName(), mainDexTypeNames.get(0).replace(".class", ""));
    assertThat(inspector.clazz(OtherTestClass.class), not(isPresent()));
  }

  private void inspectSecondaryDex(CodeInspector inspector) {
    assertThat(inspector.clazz(TestClass.class), not(isPresent()));
    assertThat(inspector.clazz(OtherTestClass.class), isPresent());
    assertThat(OtherTestClass.class, isRepackaged(inspector));
  }

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }

  public static class OtherTestClass {}
}
