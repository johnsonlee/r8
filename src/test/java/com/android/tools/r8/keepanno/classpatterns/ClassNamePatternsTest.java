// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.classpatterns;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.ClassNamePattern;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.TypePattern;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassNamePatternsTest extends TestBase {

  static final Class<?> A1 = com.android.tools.r8.keepanno.classpatterns.pkg1.A.class;
  static final Class<?> B1 = com.android.tools.r8.keepanno.classpatterns.pkg1.B.class;
  static final Class<?> A2 = com.android.tools.r8.keepanno.classpatterns.pkg2.A.class;
  static final Class<?> B2 = com.android.tools.r8.keepanno.classpatterns.pkg2.B.class;

  static final String EXPECTED_ALL =
      StringUtils.lines(
          "pkg1.A",
          "pkg1.A: pkg1.A",
          "pkg1.B",
          "pkg1.B: pkg1.B",
          "pkg2.A",
          "pkg2.A: pkg2.A",
          "pkg2.B",
          "pkg2.B: pkg2.B");

  static final String EXPECTED_ALL_NON_VOID =
      StringUtils.lines("pkg1.A", "pkg1.B", "pkg2.A", "pkg2.B");
  static final String EXPECTED_PKG = StringUtils.lines("pkg1.A", "pkg1.B");
  static final String EXPECTED_NAME = StringUtils.lines("pkg1.B", "pkg2.B");
  static final String EXPECTED_SINGLE = StringUtils.lines("pkg2.A");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  public ClassNamePatternsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getBaseInputClasses())
        .addProgramClasses(TestAll.class)
        .run(parameters.getRuntime(), TestAll.class)
        .assertSuccessWithOutput(EXPECTED_ALL);
  }

  private void runTestR8(Class<?> mainClass, String expected) throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getBaseInputClasses())
        .addProgramClasses(mainClass)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(expected);
  }

  @Test
  public void testAllR8() throws Exception {
    runTestR8(TestAll.class, EXPECTED_ALL);
  }

  @Test
  public void testAllNoVoidR8() throws Exception {
    runTestR8(TestAllNoVoid.class, EXPECTED_ALL_NON_VOID);
  }

  @Test
  public void testPkgR8() throws Exception {
    runTestR8(TestPkg.class, EXPECTED_PKG);
  }

  @Test
  public void testNameR8() throws Exception {
    runTestR8(TestName.class, EXPECTED_NAME);
  }

  @Test
  public void testSingleR8() throws Exception {
    runTestR8(TestSingle.class, EXPECTED_SINGLE);
  }

  @Test
  public void testSingleNonExactR8() throws Exception {
    runTestR8(TestSingleWithNonExactReturnTypeClassPattern.class, EXPECTED_SINGLE);
  }

  public List<Class<?>> getBaseInputClasses() {
    return ImmutableList.of(Util.class, A1, B1, A2, B2);
  }

  static class Util {
    private static void lookupClassesAndInvokeMethods() {
      for (String pkg : Arrays.asList("pkg1", "pkg2")) {
        for (String name : Arrays.asList("A", "B")) {
          String type = "com.android.tools.r8.keepanno.classpatterns." + pkg + "." + name;
          try {
            Class<?> clazz = Class.forName(type);
            System.out.println(clazz.getDeclaredMethod("foo").invoke(null));
            clazz.getDeclaredMethod("foo", String.class).invoke(null, pkg + "." + name);
          } catch (ClassNotFoundException ignored) {
          } catch (IllegalAccessException ignored) {
          } catch (InvocationTargetException ignored) {
          } catch (NoSuchMethodException ignored) {
          }
        }
      }
    }
  }

  static class TestAll {

    @UsesReflection({
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_METHODS,
          // The empty class pattern is equivalent to "any class".
          classNamePattern = @ClassNamePattern(),
          methodName = "foo",
          // The empty type pattern used in a return-type context will match 'void'.
          methodReturnTypePattern = @TypePattern())
    })
    public void foo() throws Exception {
      Util.lookupClassesAndInvokeMethods();
    }

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new TestAll().foo();
    }
  }

  static class TestAllNoVoid {

    @UsesReflection({
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_METHODS,
          methodName = "foo",
          // Matching any class does not include 'void'.
          methodReturnTypePattern = @TypePattern(classNamePattern = @ClassNamePattern()))
    })
    public void foo() throws Exception {
      Util.lookupClassesAndInvokeMethods();
    }

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new TestAllNoVoid().foo();
    }
  }

  static class TestPkg {

    @UsesReflection({
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_METHODS,
          classNamePattern =
              @ClassNamePattern(packageName = "com.android.tools.r8.keepanno.classpatterns.pkg1"),
          methodName = "foo",
          methodReturnTypeConstant = String.class)
    })
    public void foo() throws Exception {
      Util.lookupClassesAndInvokeMethods();
    }

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new TestPkg().foo();
    }
  }

  static class TestName {

    @UsesReflection({
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_METHODS,
          classNamePattern = @ClassNamePattern(unqualifiedName = "B"),
          methodName = "foo",
          methodReturnTypeConstant = String.class)
    })
    public void foo() throws Exception {
      Util.lookupClassesAndInvokeMethods();
    }

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new TestName().foo();
    }
  }

  static class TestSingle {

    @UsesReflection(
        @KeepTarget(
            kind = KeepItemKind.CLASS_AND_METHODS,
            classNamePattern =
                @ClassNamePattern(
                    unqualifiedName = "A",
                    packageName = "com.android.tools.r8.keepanno.classpatterns.pkg2"),
            methodName = "foo",
            methodReturnTypePattern =
                @TypePattern(
                    classNamePattern =
                        @ClassNamePattern(packageName = "java.lang", unqualifiedName = "String"))))
    public void foo() throws Exception {
      Util.lookupClassesAndInvokeMethods();
    }

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new TestSingle().foo();
    }
  }

  static class TestSingleWithNonExactReturnTypeClassPattern {

    @UsesReflection(
        @KeepTarget(
            kind = KeepItemKind.CLASS_AND_METHODS,
            classNamePattern =
                @ClassNamePattern(
                    unqualifiedName = "A",
                    packageName = "com.android.tools.r8.keepanno.classpatterns.pkg2"),
            methodName = "foo",
            methodReturnTypePattern =
                @TypePattern(classNamePattern = @ClassNamePattern(unqualifiedName = "String"))))
    public void foo() throws Exception {
      Util.lookupClassesAndInvokeMethods();
    }

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new TestSingleWithNonExactReturnTypeClassPattern().foo();
    }
  }
}
