// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlib;

import static com.android.tools.r8.ToolHelper.DESUGARED_JDK_8_LIB_JAR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LATEST;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProgramRewritingTest extends DesugaredLibraryTestBase {

  private static final Class<?> TEST_CLASS = ProgramRewritingTestClass.class;

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    LibraryDesugaringSpecification jdk8CoreLambdaStubs =
        new LibraryDesugaringSpecification(
            "JDK8_CL",
            ImmutableSet.of(
                DESUGARED_JDK_8_LIB_JAR,
                ToolHelper.getDesugarLibConversions(LEGACY),
                ToolHelper.getCoreLambdaStubs()),
            JDK8.getSpecification(),
            ImmutableSet.of(ToolHelper.getAndroidJar(AndroidApiLevel.O)),
            LibraryDesugaringSpecification.JDK8_DESCRIPTOR,
            "");
    LibraryDesugaringSpecification jdk11CoreLambdaStubs =
        new LibraryDesugaringSpecification(
            "JDK11_CL",
            ImmutableSet.of(
                LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar(),
                ToolHelper.getDesugarLibConversions(LATEST),
                ToolHelper.getCoreLambdaStubs()),
            JDK11.getSpecification(),
            ImmutableSet.of(ToolHelper.getAndroidJar(AndroidApiLevel.R)),
            LibraryDesugaringSpecification.JDK11_DESCRIPTOR,
            "");
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK8, JDK11, jdk8CoreLambdaStubs, jdk11CoreLambdaStubs),
        DEFAULT_SPECIFICATIONS);
  }

  public ProgramRewritingTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testRewriting() throws Throwable {
    Box<String> keepRules = new Box<>();
    SingleTestRunResult<?> run =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepMainRule(TEST_CLASS)
            .compile()
            .inspect(this::checkRewrittenInvokes)
            .inspectKeepRules(
                kr -> {
                  if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
                    keepRules.set(String.join("\n", kr));
                  } else {
                    assert kr == null || kr.isEmpty();
                    keepRules.set("");
                  }
                })
            .run(parameters.getRuntime(), TEST_CLASS);
    assertResultIsCorrect(run.getStdOut(), run.getStdErr(), keepRules.get());
  }

  private void assertResultIsCorrect(String stdOut, String stdErr, String keepRules) {
    if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
      if (compilationSpecification.isL8Shrink()) {
        assertGeneratedKeepRulesAreCorrect(keepRules);
      } else {
        // When shrinking the class names are not printed correctly anymore due to minification.
        assertLines2By2Correct(stdOut);
      }
    }
    if (parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      // Flaky: There might be a missing method on lambda deserialization.
      assertTrue(
          !stdErr.contains("Could not find method")
              || stdErr.contains("Could not find method java.lang.invoke.SerializedLambda"));
    } else {
      assertFalse(stdErr.contains("Could not find method"));
    }
  }

  private void checkRewrittenInvokes(CodeInspector inspector) {
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      return;
    }
    ClassSubject classSubject = inspector.clazz(TEST_CLASS);
    assertThat(classSubject, isPresent());
    List<InstructionSubject> invokes =
        classSubject
            .uniqueMethodWithOriginalName("main")
            .streamInstructions()
            .filter(
                instr ->
                    instr.isInvokeInterface() || instr.isInvokeStatic() || instr.isInvokeVirtual())
            .filter(
                instr -> {
                  String holder = instr.getMethod().getHolderType().getTypeName();
                  return !holder.equals("java.lang.Class")
                      && !holder.equals("java.lang.Object")
                      && !holder.equals("java.io.PrintStream");
                })
            .collect(Collectors.toList());
    assertEquals(22, invokes.size());
    assertInvokeStaticMatching(invokes, 0, "Set$-EL;spliterator");
    assertInvokeStaticMatching(invokes, 1, "Collection$-EL;stream");
    if (compilationSpecification.isProgramShrink()) {
      assertInvokeVirtualMatching(invokes, 2, "HashSet;iterator");
    } else {
      assertInvokeInterfaceMatching(invokes, 2, "Set;iterator");
    }
    assertInvokeStaticMatching(invokes, 3, "Collection$-EL;stream");
    assertInvokeStaticMatching(invokes, 4, "Set$-EL;spliterator");
    assertInvokeInterfaceMatching(invokes, 8, "Iterator;remove");
    assertInvokeStaticMatching(invokes, 9, "DesugarArrays;spliterator");
    assertInvokeStaticMatching(invokes, 10, "DesugarArrays;spliterator");
    assertInvokeStaticMatching(invokes, 11, "DesugarArrays;stream");
    assertInvokeStaticMatching(invokes, 12, "DesugarArrays;stream");
    assertInvokeStaticMatching(invokes, 13, "Collection$-EL;stream");
    assertInvokeStaticMatching(invokes, 14, "IntStream$-CC;range");
    assertInvokeStaticMatching(invokes, 16, "Comparator$-CC;comparingInt");
    assertInvokeStaticMatching(invokes, 17, "List$-EL;sort");
    assertInvokeStaticMatching(invokes, 19, "Comparator$-CC;comparingInt");
    assertInvokeStaticMatching(invokes, 20, "List$-EL;sort");
    assertInvokeStaticMatching(invokes, 21, "Collection$-EL;stream");
  }

  private void assertInvokeInterfaceMatching(List<InstructionSubject> invokes, int i, String s) {
    assertTrue(invokes.get(i).isInvokeInterface());
    assertTrue(invokes.get(i).toString().contains(s));
  }

  private void assertInvokeStaticMatching(List<InstructionSubject> invokes, int i, String s) {
    assertTrue(invokes.get(i).isInvokeStatic());
    assertTrue(invokes.get(i).toString().contains(s));
  }

  private void assertInvokeVirtualMatching(List<InstructionSubject> invokes, int i, String s) {
    assertTrue(invokes.get(i).isInvokeVirtual());
    assertTrue(invokes.get(i).toString().contains(s));
  }

  private void assertGeneratedKeepRulesAreCorrect(String keepRules) {
    String prefix = libraryDesugaringSpecification.functionPrefix(parameters);
    String expectedResult =
        StringUtils.lines(
            "-keep class j$.util.Collection$-EL {",
            "  public static j$.util.stream.Stream stream(java.util.Collection);",
            "}",
            "-keep class j$.util.Comparator$-CC {",
            "  public static java.util.Comparator comparingInt("
                + prefix
                + ".util.function.ToIntFunction);",
            "}",
            "-keep class j$.util.DesugarArrays {",
            "  public static j$.util.Spliterator spliterator(java.lang.Object[]);",
            "  public static j$.util.Spliterator spliterator(java.lang.Object[], int, int);",
            "  public static j$.util.stream.Stream stream(java.lang.Object[]);",
            "  public static j$.util.stream.Stream stream(java.lang.Object[], int, int);",
            "}",
            "-keep class j$.util.List$-EL {",
            "  public static void sort(java.util.List, java.util.Comparator);",
            "}",
            "-keep class j$.util.Set$-EL {",
            "  public static j$.util.Spliterator spliterator(java.util.Set);",
            "}",
            "-keep interface j$.util.Spliterator {",
            "}");
    if (prefix.equals("j$")) {
      expectedResult +=
          StringUtils.lines(
              "-keep interface j$.util.function.ToIntFunction {",
              "  public int applyAsInt(java.lang.Object);",
              "}");
    }
    expectedResult +=
        StringUtils.lines(
            "-keep class j$.util.stream.IntStream$-CC {",
            "  public static j$.util.stream.IntStream range(int, int);",
            "}",
            "-keep interface j$.util.stream.IntStream {",
            "}",
            "-keep interface j$.util.stream.Stream {",
            "}");
    if (prefix.equals("java")) {
      expectedResult +=
          StringUtils.lines("-keep interface java.util.function.ToIntFunction {", "}");
    }
    assertEquals(expectedResult.trim(), keepRules.trim());
  }

  public static class ProgramRewritingTestClass {

    // Each print to the console is immediately followed by the expected result so the tests
    // can assert the results by checking the lines 2 by 2.
    public static void main(String[] args) {
      Set<Object> set = new HashSet<>();
      List<Object> list = new ArrayList<>();
      ArrayList<Object> aList = new ArrayList<>();
      Queue<Object> queue = new LinkedList<>();
      LinkedHashSet<Object> lhs = new LinkedHashSet<>();
      // Following should be rewritten to invokeStatic to the dispatch class.
      System.out.println(set.spliterator().getClass().getName());
      System.out.println("j$.util.Spliterators$IteratorSpliterator");
      // Following should be rewritten to invokeStatic to Collection dispatch class.
      System.out.println(set.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      // Following should not be rewritten.
      System.out.println(set.iterator().getClass().getName());
      System.out.println("java.util.HashMap$KeyIterator");
      // Following should be rewritten to invokeStatic to Collection dispatch class.
      System.out.println(queue.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      // Following should be rewritten as retarget core lib member.
      System.out.println(lhs.spliterator().getClass().getName());
      System.out.println("j$.util.Spliterators$IteratorSpliterator");
      // Remove follows the don't rewrite rule.
      list.add(new Object());
      Iterator iterator = list.iterator();
      iterator.next();
      iterator.remove();
      // Static methods (same name, different signatures).
      System.out.println(Arrays.spliterator(new Object[] {new Object()}).getClass().getName());
      System.out.println("j$.util.Spliterators$ArraySpliterator");
      System.out.println(
          Arrays.spliterator(new Object[] {new Object()}, 0, 0).getClass().getName());
      System.out.println("j$.util.Spliterators$ArraySpliterator");
      System.out.println(Arrays.stream(new Object[] {new Object()}).getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      System.out.println(Arrays.stream(new Object[] {new Object()}, 0, 0).getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      // Following should be rewritten to invokeStatic to dispatch class.
      System.out.println(list.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      // Following should call companion method (desugared library class).
      System.out.println(IntStream.range(0, 5).getClass().getName());
      System.out.println("j$.util.stream.IntPipeline$Head");
      // Following should call List dispatch (sort), rewritten from invoke interface.
      // Comparator.comparingInt should call companion method (desugared library class).
      Collections.addAll(list, new Object(), new Object());
      list.sort(Comparator.comparingInt(Object::hashCode));
      // Following  should call List dispatch (sort), rewritten from invoke virtual.
      // Comparator.comparingInt should call companion method (desugared library class).
      Collections.addAll(aList, new Object(), new Object());
      aList.sort(Comparator.comparingInt(Object::hashCode));
      // Following should be rewritten to invokeStatic to Collection dispatch class.
      System.out.println(list.stream().getClass().getName());
      System.out.println("j$.util.stream.ReferencePipeline$Head");
      // Following should call companion method (desugared library class) [Java 9].
      // System.out.println(Stream.iterate(0,x->x<10,x->x+1).getClass().getName());
    }
  }
}
