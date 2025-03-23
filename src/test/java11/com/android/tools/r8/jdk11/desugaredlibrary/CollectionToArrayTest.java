// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk11.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CollectionToArrayTest extends DesugaredLibraryTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameter(2)
  public CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "[one, two]", "Override", "[three, four]", "Override", "[five, six]", "[seven, eight]");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
            .withDexRuntimesAndAllApiLevels()
            .build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        SPECIFICATIONS_WITH_CF2CF);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(libraryDesugaringSpecification == JDK11);
    assumeTrue(compilationSpecification == D8_L8DEBUG);
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testDesugar() throws Exception {
    parameters.assumeDexRuntime();
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class Main {
    public static void main(String[] args) {
      List<String> list = new ArrayList<>();
      list.add("one");
      list.add("two");
      // This default method was added in Android T.
      String[] toArray = list.toArray(String[]::new);
      System.out.println(Arrays.toString(toArray));

      List<String> myList = new MyList<>();
      myList.add("three");
      myList.add("four");
      // This default method was added in Android T.
      String[] toArray2 = myList.toArray(String[]::new);
      System.out.println(Arrays.toString(toArray2));

      try {
        MyCollectionImplWithToArrayOverride<String> myCollectionImplWithToArrayOverride =
            new MyCollectionImplWithToArrayOverride<>();
        // This default method was added in Android T.
        String[] toArray3 = myCollectionImplWithToArrayOverride.toArray(String[]::new);
        System.out.println(Arrays.toString(toArray3));
      } catch (NoSuchMethodError e) {
        System.out.println("NoSuchMethodError");
      }

      MyCollectionImplWithoutToArrayOverride<String> myCollectionImplWithoutToArrayOverride =
          new MyCollectionImplWithoutToArrayOverride<>();
      // This default method was added in Android T.
      String[] toArray4 = myCollectionImplWithoutToArrayOverride.toArray(String[]::new);
      System.out.println(Arrays.toString(toArray4));
    }
  }

  public static class MyList<T> extends ArrayList<T> {

    @Override
    public <T1> T1[] toArray(IntFunction<T1[]> generator) {
      System.out.println("Override");
      return super.toArray(generator);
    }
  }

  public interface MyCollection<T> extends Collection<T> {}

  public static class MyCollectionImplWithToArrayOverride<T> implements MyCollection<T> {

    @Override
    public <T1> T1[] toArray(IntFunction<T1[]> generator) {
      System.out.println("Override");
      return MyCollection.super.toArray(generator);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> T1[] toArray(T1[] ignore) {
      return (T1[]) new String[] {"five", "six"};
    }

    @Override
    public int size() {
      throw new RuntimeException();
    }

    @Override
    public boolean isEmpty() {
      throw new RuntimeException();
    }

    @Override
    public boolean contains(Object o) {
      throw new RuntimeException();
    }

    @Override
    public Iterator<T> iterator() {
      throw new RuntimeException();
    }

    @Override
    public Object[] toArray() {
      throw new RuntimeException();
    }

    @Override
    public boolean add(T t) {
      throw new RuntimeException();
    }

    @Override
    public boolean remove(Object o) {
      throw new RuntimeException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      throw new RuntimeException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
      throw new RuntimeException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      throw new RuntimeException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      throw new RuntimeException();
    }

    @Override
    public void clear() {
      throw new RuntimeException();
    }
  }

  public static class MyCollectionImplWithoutToArrayOverride<T> implements MyCollection<T> {

    @SuppressWarnings("unchecked")
    @Override
    public <T1> T1[] toArray(T1[] ignore) {
      return (T1[]) new String[] {"seven", "eight"};
    }

    @Override
    public int size() {
      throw new RuntimeException();
    }

    @Override
    public boolean isEmpty() {
      throw new RuntimeException();
    }

    @Override
    public boolean contains(Object o) {
      throw new RuntimeException();
    }

    @Override
    public Iterator<T> iterator() {
      throw new RuntimeException();
    }

    @Override
    public Object[] toArray() {
      throw new RuntimeException();
    }

    @Override
    public boolean add(T t) {
      throw new RuntimeException();
    }

    @Override
    public boolean remove(Object o) {
      throw new RuntimeException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      throw new RuntimeException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
      throw new RuntimeException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      throw new RuntimeException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      throw new RuntimeException();
    }

    @Override
    public void clear() {
      throw new RuntimeException();
    }
  }
}
