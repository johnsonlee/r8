// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.b149971007;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dexsplitter.SplitterTestBase;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B149971007 extends SplitterTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private boolean invokesOutline(MethodSubject method, String outlineClassName) {
    assertThat(method, isPresent());
    for (InstructionSubject instruction : method.asFoundMethodSubject().instructions()) {
      if (instruction.isInvoke()
          && instruction.getMethod().holder.toSourceString().equals(outlineClassName)) {
        return true;
      }
    }
    return false;
  }

  private boolean referenceFeatureClass(FoundMethodSubject method) {
    for (InstructionSubject instruction : method.instructions()) {
      if (instruction.isInvoke()
          && instruction.getMethod().holder.toSourceString().endsWith("FeatureClass")) {
        return true;
      }
    }
    return false;
  }

  private ClassSubject checkOutlineFromFeature(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    // There are two expected outlines, in a single single class after horizontal class merging.
    ClassSubject classSubject0 =
        inspector.syntheticClass(syntheticItems.syntheticOutlineClass(FeatureClass.class, 0));
    ClassSubject classSubject1 =
        inspector.clazz(syntheticItems.syntheticOutlineClass(FeatureClass.class, 1));
    assertThat(classSubject1, notIf(isPresent(), classSubject0.isPresent()));

    ClassSubject classSubject = classSubject0.isPresent() ? classSubject0 : classSubject1;

    List<FoundMethodSubject> allMethods = classSubject.allMethods();
    assertEquals(2, allMethods.size());

    // One of the methods is StringBuilder the other references the feature.
    assertTrue(allMethods.stream().anyMatch(this::referenceFeatureClass));

    return classSubject;
  }

  @Test
  public void testWithoutSplit() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, FeatureAPI.class, FeatureClass.class)
            .addKeepClassAndMembersRules(TestClass.class)
            .addKeepClassAndMembersRules(FeatureClass.class)
            .collectSyntheticItems()
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject outlineClass =
        checkOutlineFromFeature(inspector, compileResult.getSyntheticItems());

    // Check that parts of method1, ..., method4 in FeatureClass was outlined.
    ClassSubject featureClass = inspector.clazz(FeatureClass.class);
    assertThat(featureClass, isPresent());

    // Find the final names of the two outline classes.
    String outlineClassName = outlineClass.getFinalName();

    // Verify they are called from the feature methods.
    // Note: should the choice of synthetic grouping change these expectations will too.
    assertTrue(
        invokesOutline(featureClass.uniqueMethodWithOriginalName("method1"), outlineClassName));
    assertTrue(
        invokesOutline(featureClass.uniqueMethodWithOriginalName("method2"), outlineClassName));
    assertTrue(
        invokesOutline(featureClass.uniqueMethodWithOriginalName("method3"), outlineClassName));
    assertTrue(
        invokesOutline(featureClass.uniqueMethodWithOriginalName("method4"), outlineClassName));

    compileResult.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput("123456");
  }

  private void checkNoOutlineFromFeature(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    // The features do not give rise to an outline.
    ClassSubject featureOutlineClass =
        inspector.syntheticClass(syntheticItems.syntheticOutlineClass(FeatureClass.class, 0));
    assertThat(featureOutlineClass, isAbsent());
    // The main TestClass entry does.
    ClassSubject mainOutlineClazz =
        inspector.clazz(syntheticItems.syntheticOutlineClass(TestClass.class, 0));
    assertThat(mainOutlineClazz, isPresent());
    assertEquals(1, mainOutlineClazz.allMethods().size());
    assertTrue(mainOutlineClazz.allMethods().stream().noneMatch(this::referenceFeatureClass));
  }

  @Test
  public void testWithSplit() throws Exception {
    Path featureCode = temp.newFile("feature.zip").toPath();

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, FeatureAPI.class)
            .addKeepClassAndMembersRules(TestClass.class)
            .addKeepClassAndMembersRules(FeatureClass.class)
            .collectSyntheticItems()
            .setMinApi(parameters)
            .addFeatureSplit(
                builder -> simpleSplitProvider(builder, featureCode, temp, FeatureClass.class))
            .addOptionsModification(this::configure)
            .compile()
            .inspectWithSyntheticItems(this::checkNoOutlineFromFeature);

    MethodSubject outlineMethod =
        compileResult
            .inspector()
            .clazz(compileResult.getSyntheticItems().syntheticOutlineClass(TestClass.class, 0))
            .uniqueMethod();
    assertThat(outlineMethod, isPresent());

    // Check that parts of method1, ..., method4 in FeatureClass was not outlined.
    CodeInspector featureInspector = new CodeInspector(featureCode);
    ClassSubject featureClass = featureInspector.clazz(FeatureClass.class);
    assertThat(featureClass, isPresent());
    assertThat(
        featureClass.uniqueMethodWithOriginalName("method1"), not(invokesMethod(outlineMethod)));
    assertThat(
        featureClass.uniqueMethodWithOriginalName("method2"), not(invokesMethod(outlineMethod)));
    assertThat(
        featureClass.uniqueMethodWithOriginalName("method3"), not(invokesMethod(outlineMethod)));
    assertThat(
        featureClass.uniqueMethodWithOriginalName("method4"), not(invokesMethod(outlineMethod)));

    // Run the code without the feature code present.
    compileResult.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput("12");

    // Run the code with the feature code present.
    compileResult
        .addRunClasspathFiles(featureCode)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("123456");
  }

  private void configure(InternalOptions options) {
    options.getBottomUpOutlinerOptions().enable = false;
    options.outline.threshold = 2;
  }

  public static class TestClass {

    public static void main(String[] args) throws Exception {
      method1(1, 2);
      if (FeatureAPI.hasFeature()) {
        FeatureAPI.feature(3);
      }
    }

    public static void method1(int i1, int i2) {
      System.out.print(i1 + "" + i2);
    }

    public static void method2(int i1, int i2) {
      System.out.print(i1 + "" + i2);
    }
  }

  public static class FeatureAPI {
    private static String featureClassName() {
      String packageName =
          System.currentTimeMillis() > 0
              ? "com.android.tools.r8.ir.optimize.outliner.b149971007"
              : null;
      return packageName + ".B149971007$FeatureClass";
    }

    public static boolean hasFeature() {
      try {
        Class.forName(featureClassName());
      } catch (ClassNotFoundException e) {
        return false;
      }
      return true;
    }

    public static void feature(int i)
        throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException {
      Class<?> featureClass = Class.forName(featureClassName());
      Method featureMethod = featureClass.getMethod("feature", int.class);
      featureMethod.invoke(null, i);
    }
  }

  public static class FeatureClass {
    private int i;

    FeatureClass(int i) {
      this.i = i;
    }

    public int getI() {
      return i;
    }

    public static void feature(int i) {
      method1(i, i + 1);
      method3(new FeatureClass(i + 2), new FeatureClass(i + 3));
    }

    public static void method1(int i1, int i2) {
      System.out.print(i1 + "" + i2);
    }

    public static void method2(int i1, int i2) {
      System.out.print(i1 + "" + i2);
    }

    public static void method3(FeatureClass fc1, FeatureClass fc2) {
      System.out.print(fc1.getI() + "" + fc2.getI());
    }

    public static void method4(FeatureClass fc1, FeatureClass fc2) {
      System.out.print(fc1.getI() + "" + fc2.getI());
    }
  }
}
