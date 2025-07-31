// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.listiteration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ListIterationRewriterTest extends TestBase {
  interface TestCase {
    String[] expectedOutput();

    void run();

    default String getName() {
      // Some runtimes prefix the outer class, and some do not.
      return getClass().getSimpleName().replaceFirst(".*\\$", "");
    }
  }

  public static class ArrayListMethodWithTryCatch implements TestCase {
    @NeverInline
    private static void helper(ArrayList<Integer> list) {
      try {
        for (Integer x : list) {
          System.out.println(x);
        }
      } catch (Throwable t) {
        System.out.println("error");
      }
    }

    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      helper(list);
    }
  }

  public static class ArrayListMethodWithSubsequentLoop implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "1", "1", "1", "2", "3"};
    }

    // Use ArrayList<Object> to avoid check-cast instructions.
    @NeverInline
    private static void helper(ArrayList<Object> list) {
      long now = System.currentTimeMillis();
      int i = 0;
      for (Object x : list) {
        for (; i < Math.min(3, now); ++i) {
          System.out.println(x);
        }
        System.out.println(x);
      }
    }

    @Override
    public void run() {
      ArrayList<Object> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      helper(list);
    }
  }

  public static class ArrayListMethodWithSubsequentLoopAndTryCatch implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "1", "1", "1", "2", "3"};
    }

    // Use ArrayList<Object> to avoid check-cast instructions.
    @NeverInline
    private static void helper(ArrayList<Object> list) {
      long now = System.currentTimeMillis();
      int i = 0;
      try {
        for (Object x : list) {
          for (; i < Math.min(3, now); ++i) {
            System.out.println(x);
          }
          System.out.println(x);
        }
      } catch (Throwable t) {
        System.out.println("error");
      }
    }

    @Override
    public void run() {
      ArrayList<Object> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      helper(list);
    }
  }

  public static class ArrayListMethod implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @NeverInline
    private static void helper(ArrayList<Integer> list) {
      for (Integer x : list) {
        System.out.println(x);
      }
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      helper(list);
    }
  }

  public static class ArrayListLocal implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      for (Integer x : list) {
        System.out.println(x);
      }
    }
  }

  public static class ArrayListLocalNoOutValue implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "1", "1"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      for (Integer x : list) {
        System.out.println("1");
      }
    }
  }

  public static class NestedLocalArrayList implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"7", "8", "9", "1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      for (int x : list) {
        for (int y : list) {
          x += y;
        }
        System.out.println(x);
      }
      // Also test sibling loop.
      for (int x : list) {
        System.out.println(x);
      }
    }
  }

  public static class LocalArrayListWithExtraConstInstr implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3", "true"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      boolean found = false;
      while (it.hasNext()) {
        Integer x = it.next();
        if (x == 3) {
          found = true;
        }
        System.out.println(x);
      }
      System.out.println(found);
    }
  }

  public static class LocalArrayListWithInvertedIfCondition implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (true) {
        // This will be an "if NEZ" rather than the normal "if EQZ".
        if (!it.hasNext()) {
          return;
        }
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class LoopWithContinue implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @NeverInline
    private static List<Integer> getList() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      return list;
    }

    @Override
    public void run() {
      Collection<Integer> list = getList();
      for (Integer x : list) {
        if (x == null) {
          continue;
        }
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeCopyOnWrite implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      for (int x : list) {
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeLinkedList implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      LinkedList<Integer> list = new LinkedList<>();
      Collections.addAll(list, 1, 2, 3);
      for (int x : list) {
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeNonLoop implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      if (it.hasNext()) {
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeCustomExtraUsers implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (it.hasNext()) {
        Integer x = it.next();
        System.out.println(x);
      }
      if (it.hasNext()) {
        throw new RuntimeException();
      }
    }
  }

  public static class DoNotOptimizeCustomLoopWithExtraInstruction implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"01", "02", "03"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (it.hasNext()) {
        System.out.print("0");
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizePhiIterator implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = System.currentTimeMillis() <= 0 ? null : list.iterator();
      while (it.hasNext()) {
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeExtraConditionLoop implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (it.hasNext() && System.currentTimeMillis() > 0) {
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeOnlyNoOutValue implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2"};
    }

    @NeverInline
    private static void noIteratorOutValue(ArrayList<Integer> list) {
      list.iterator();
      System.out.println("1");
    }

    @NeverInline
    private static void noHasNextOutValue(ArrayList<Integer> list) {
      Iterator<Integer> it = list.iterator();
      it.hasNext();
      System.out.println("2");
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      noIteratorOutValue(list);
      noHasNextOutValue(list);
    }
  }

  public static class DoNotOptimizeWrongReceiver implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3", "4"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it0 = list.iterator();
      Iterator<Integer> it1 = list.iterator();
      while (it0.hasNext()) {
        Integer x = it0.next();
        System.out.println(x);
      }
      if (it1.hasNext()) {
        System.out.println(4);
      }
    }
  }

  public static class DoNotOptimizeExtraNextPredecessors implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (it.hasNext()) {
        Integer x;
        for (; ; ) {
          x = it.next();
          if (x != null) {
            break;
          }
        }
        System.out.println(x);
      }
    }
  }

  public static class CustomArrayListNoIterator implements TestCase {
    static class CustomArrayList extends ArrayList<Integer> {}

    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new CustomArrayList();
      Collections.addAll(list, 1, 2, 3);
      for (Object x : list) {
        System.out.println(x);
      }
    }
  }

  public static class CustomArrayListWithIterator {
    public static String[] EXPECTED_OUTPUT = new String[] {"1", "2", "3"};

    static class CustomArrayListBase extends ArrayList<Integer> {}

    static class CustomArrayList extends CustomArrayListBase {
      @Override
      public Iterator<Integer> iterator() {
        return Arrays.asList(1, 2, 3).iterator();
      }
    }

    public static void main(String[] args) {
      ArrayList<Integer> list = new CustomArrayList();
      for (Object x : list) {
        System.out.println(x);
      }
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Optimization does affected by API / runtime.
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  @Parameter public TestParameters parameters;

  public static class RewritesMain {
    // Comment out cases from this list to debug individual tests.
    static final TestCase[] CASES =
        new TestCase[] {
          new ArrayListLocal(),
          new ArrayListLocalNoOutValue(),
          new NestedLocalArrayList(),
          new ArrayListMethod(),
          new ArrayListMethodWithTryCatch(),
          new ArrayListMethodWithSubsequentLoop(),
          new ArrayListMethodWithSubsequentLoopAndTryCatch(),
          new LocalArrayListWithExtraConstInstr(),
          new LocalArrayListWithInvertedIfCondition(),
          new LoopWithContinue(),
          new CustomArrayListNoIterator(),
        };

    @NeverInline
    public static void main(String[] args) {
      for (TestCase test : CASES) {
        System.out.println(test.getName());
        test.run();
      }
    }
  }

  private static String[] getExpectedTestOutput(TestCase[] testCases) {
    return Arrays.stream(testCases)
        .flatMap(x -> Stream.concat(Stream.of(x.getName()), Arrays.stream(x.expectedOutput())))
        .toArray(String[]::new);
  }

  @Test
  public void testRewritesD8() throws Exception {
    parameters.assumeDexRuntime();
    // Run all test classes in one compilation for faster tests compared to compiling each one
    // separately.
    testForD8(parameters)
        .addProgramClassesAndInnerClasses(ListUtils.map(RewritesMain.CASES, TestCase::getClass))
        .addProgramClasses(RewritesMain.class, TestCase.class)
        .addOptionsModification(
            o -> {
              assertFalse(o.testing.listIterationRewritingRewriteCustomIterators);
              o.testing.listIterationRewritingRewriteCustomIterators = true;
              assertFalse(o.testing.listIterationRewritingRewriteInterfaces);
              o.testing.listIterationRewritingRewriteInterfaces = true;
            })
        .release()
        .compile()
        .run(parameters.getRuntime(), RewritesMain.class)
        .assertSuccessWithOutputLines(getExpectedTestOutput(RewritesMain.CASES))
        .inspect(
            inspector -> inspector.forAllClasses(ListIterationRewriterTest::checkNoIteratorInvoke));
  }

  @Test
  public void testRewritesR8() throws Exception {
    // Run all test classes in one compilation for faster tests compared to compiling each one
    // separately.
    testForR8(parameters)
        .addProgramClassesAndInnerClasses(ListUtils.map(RewritesMain.CASES, TestCase::getClass))
        .addProgramClasses(RewritesMain.class, TestCase.class)
        .addKeepMainRule(RewritesMain.class)
        .addDontObfuscate()
        .noHorizontalClassMerging()
        .enableInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), RewritesMain.class)
        .assertSuccessWithOutputLines(getExpectedTestOutput(RewritesMain.CASES))
        .inspect(
            inspector -> inspector.forAllClasses(ListIterationRewriterTest::checkNoIteratorInvoke));
  }

  public static class NoRewritesMain {
    // Comment out cases from this list to debug individual tests.
    static final TestCase[] CASES =
        new TestCase[] {
          new DoNotOptimizeCopyOnWrite(),
          new DoNotOptimizeLinkedList(),
          new DoNotOptimizeNonLoop(),
          new DoNotOptimizeCustomExtraUsers(),
          new DoNotOptimizeCustomLoopWithExtraInstruction(),
          new DoNotOptimizePhiIterator(),
          new DoNotOptimizeExtraConditionLoop(),
          new DoNotOptimizeOnlyNoOutValue(),
          new DoNotOptimizeWrongReceiver(),
          new DoNotOptimizeExtraNextPredecessors(),
        };

    @NeverInline
    public static void main(String[] args) {
      for (TestCase test : CASES) {
        System.out.println(test.getName());
        test.run();
      }
    }
  }

  private static boolean isIteratorInvoke(InstructionSubject ins) {
    return ins.isInvoke() && ins.getMethod().name.toString().equals("iterator");
  }

  private static void checkIteratorInvokeExists(FoundClassSubject classSubject) {
    if (classSubject.isImplementing(TestCase.class)) {
      assertTrue(
          "No iterator() found in " + classSubject,
          classSubject.allMethods().stream()
              .flatMap(m -> m.streamInstructions())
              .anyMatch(ListIterationRewriterTest::isIteratorInvoke));
    }
  }

  private static void checkNoIteratorInvoke(FoundClassSubject classSubject) {
    if (classSubject.isImplementing(TestCase.class)) {
      assertTrue(
          "Found iterator() in " + classSubject,
          classSubject.allMethods().stream()
              .flatMap(m -> m.streamInstructions())
              .noneMatch(ListIterationRewriterTest::isIteratorInvoke));
    }
  }

  @Test
  public void testNoRewritesD8() throws Exception {
    parameters.assumeDexRuntime();
    // Run all test classes in one compilation for faster tests compared to compiling each one
    // separately.
    testForD8(parameters)
        .addOptionsModification(
            o -> {
              // Ensure tests do not pass due to the optimization being disabled.
              assertFalse(o.testing.listIterationRewritingRewriteCustomIterators);
              o.testing.listIterationRewritingRewriteCustomIterators = true;
              assertFalse(o.testing.listIterationRewritingRewriteInterfaces);
              o.testing.listIterationRewritingRewriteInterfaces = true;
            })
        .addProgramClassesAndInnerClasses(ListUtils.map(NoRewritesMain.CASES, TestCase::getClass))
        .addProgramClasses(NoRewritesMain.class, TestCase.class)
        .release()
        .compile()
        .run(parameters.getRuntime(), NoRewritesMain.class)
        .assertSuccessWithOutputLines(getExpectedTestOutput(NoRewritesMain.CASES))
        .inspect(
            inspector ->
                inspector.forAllClasses(ListIterationRewriterTest::checkIteratorInvokeExists));
  }

  @Test
  public void testNoRewritesR8() throws Exception {
    testForR8(parameters)
        .addOptionsModification(
            o -> {
              // Ensure tests do not pass due to the optimization being disabled.
              o.testing.listIterationRewritingRewriteCustomIterators = true;
            })
        .addProgramClassesAndInnerClasses(ListUtils.map(NoRewritesMain.CASES, TestCase::getClass))
        .addProgramClasses(NoRewritesMain.class, TestCase.class)
        .addKeepMainRule(NoRewritesMain.class)
        .addDontObfuscate()
        .enableInliningAnnotations()
        .noHorizontalClassMerging()
        .compile()
        .run(parameters.getRuntime(), NoRewritesMain.class)
        .assertSuccessWithOutputLines(getExpectedTestOutput(NoRewritesMain.CASES))
        .inspect(
            inspector ->
                inspector.forAllClasses(ListIterationRewriterTest::checkIteratorInvokeExists));
  }

  @Test
  public void testCustomIteratorThatDisablesOptimization() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassesAndInnerClasses(CustomArrayListWithIterator.class)
        .addKeepMainRule(CustomArrayListWithIterator.class)
        .compile()
        .run(parameters.getRuntime(), CustomArrayListWithIterator.class)
        .assertSuccessWithOutputLines(CustomArrayListWithIterator.EXPECTED_OUTPUT)
        .inspect(
            inspector ->
                inspector.forAllClasses(ListIterationRewriterTest::checkIteratorInvokeExists));
  }
}
