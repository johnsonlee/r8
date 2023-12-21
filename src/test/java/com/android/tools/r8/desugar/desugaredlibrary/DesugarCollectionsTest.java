// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugarCollectionsTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public DesugarCollectionsTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testDesugarCollectionsTest() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  private String getExpectedResult() {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      result.add("item");
    }
    for (int i = 0; i < 6; i++) {
      result.add("k v");
    }
    for (int i = 0; i < 6; i++) {
      result.add("exception");
    }
    result.addAll(ImmutableList.of("one", "v", "k", "k=v"));
    return StringUtils.lines(result);
  }

  public static class Main {

    public static void main(String[] args) {
      successTest();
      exceptionTest();
      apiTest();
    }

    private static void apiTest() {
      Vector<String> strings = new Vector<>();
      strings.add("one");
      strings.subList(0, 1).forEach(System.out::println);
      Hashtable<String, String> table = new Hashtable<>();
      table.put("k", "v");
      table.values().forEach(System.out::println);
      table.keySet().forEach(System.out::println);
      table.entrySet().forEach(System.out::println);
    }

    private static void successTest() {
      SortedSet<String> set = new TreeSet<>();
      set.add("item");
      Collections.unmodifiableCollection(set).forEach(System.out::println);
      Collections.unmodifiableSet(set).forEach(System.out::println);
      Collections.unmodifiableSortedSet(set).forEach(System.out::println);
      Collections.synchronizedCollection(set).forEach(System.out::println);
      Collections.synchronizedSet(set).forEach(System.out::println);
      Collections.synchronizedSortedSet(set).forEach(System.out::println);
      Collections.checkedCollection(set, String.class).forEach(System.out::println);
      Collections.checkedSet(set, String.class).forEach(System.out::println);
      Collections.checkedSortedSet(set, String.class).forEach(System.out::println);
      SortedMap<String, String> map = new TreeMap<>();
      map.put("k", "v");
      Collections.unmodifiableMap(map).forEach((k, v) -> System.out.println(k + " " + v));
      Collections.unmodifiableSortedMap(map).forEach((k, v) -> System.out.println(k + " " + v));
      Collections.synchronizedMap(map).forEach((k, v) -> System.out.println(k + " " + v));
      Collections.synchronizedSortedMap(map).forEach((k, v) -> System.out.println(k + " " + v));
      Collections.checkedMap(map, String.class, String.class)
          .forEach((k, v) -> System.out.println(k + " " + v));
      Collections.checkedSortedMap(map, String.class, String.class)
          .forEach((k, v) -> System.out.println(k + " " + v));
    }

    private static void exceptionTest() {
      try {
        Collections.unmodifiableCollection(new ImmutableList<>()).spliterator();
        System.out.println("working");
      } catch (UnsupportedOperationException e) {
        System.out.println("exception");
      }
      try {
        Collections.unmodifiableList(new ImmutableList<>()).spliterator();
        System.out.println("working");
      } catch (UnsupportedOperationException e) {
        System.out.println("exception");
      }
      try {
        Collections.synchronizedList(new ImmutableList<>()).spliterator();
        System.out.println("working");
      } catch (UnsupportedOperationException e) {
        System.out.println("exception");
      }
      try {
        Collections.synchronizedList(new ImmutableList<>()).spliterator();
        System.out.println("working");
      } catch (UnsupportedOperationException e) {
        System.out.println("exception");
      }
      try {
        Collections.checkedList(new ImmutableList<>(), Vector.class).spliterator();
        System.out.println("working");
      } catch (UnsupportedOperationException e) {
        System.out.println("exception");
      }
      try {
        Collections.checkedList(new ImmutableList<>(), Vector.class).spliterator();
        System.out.println("working");
      } catch (UnsupportedOperationException e) {
        System.out.println("exception");
      }
    }

    static class ImmutableList<T> implements List<T> {

      @Override
      public int size() {
        return 0;
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public boolean contains(Object o) {
        return false;
      }

      @Override
      public Iterator<T> iterator() {
        return null;
      }

      @Override
      public Object[] toArray() {
        return new Object[0];
      }

      @Override
      public <T1> T1[] toArray(T1[] a) {
        return null;
      }

      @Override
      public boolean add(T t) {
        return false;
      }

      @Override
      public boolean remove(Object o) {
        return false;
      }

      @Override
      public boolean containsAll(Collection<?> c) {
        return false;
      }

      @Override
      public boolean addAll(Collection<? extends T> c) {
        return false;
      }

      @Override
      public boolean addAll(int index, Collection<? extends T> c) {
        return false;
      }

      @Override
      public boolean removeAll(Collection<?> c) {
        return false;
      }

      @Override
      public boolean retainAll(Collection<?> c) {
        return false;
      }

      @Override
      public void clear() {}

      @Override
      public T get(int index) {
        return null;
      }

      @Override
      public T set(int index, T element) {
        return null;
      }

      @Override
      public void add(int index, T element) {}

      @Override
      public T remove(int index) {
        return null;
      }

      @Override
      public int indexOf(Object o) {
        return 0;
      }

      @Override
      public int lastIndexOf(Object o) {
        return 0;
      }

      @Override
      public ListIterator<T> listIterator() {
        return null;
      }

      @Override
      public ListIterator<T> listIterator(int index) {
        return null;
      }

      @Override
      public List<T> subList(int fromIndex, int toIndex) {
        return null;
      }

      @Override
      public Spliterator<T> spliterator() {
        throw new UnsupportedOperationException();
      }
    }
  }
}
