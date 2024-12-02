// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedCollection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Java21CollectionTest extends DesugaredLibraryTestBase {

  private static final String[] OLD_EXPECTED_RESULT_4 = {
    "class java.lang.NoSuchMethodError",
    "class java.lang.NoSuchMethodError",
    "0",
    "2",
    "class java.lang.NoSuchMethodError"
  };
  private static final String[] OLD_EXPECTED_RESULT_R8_FIXED_4 = {
    "class java.lang.NoSuchMethodError", "class java.lang.NoSuchMethodError", "0", "2", "0", "2"
  };
  private static final String[] OLD_EXPECTED_RESULT_5_PLUS = {
    "class java.lang.NoSuchMethodError",
    "class java.lang.NoClassDefFoundError",
    "0",
    "2",
    "class java.lang.NoClassDefFoundError"
  };
  private static final String[] OLD_EXPECTED_RESULT_R8_FIXED_5_PLUS = {
    "class java.lang.NoSuchMethodError", "class java.lang.NoClassDefFoundError", "0", "2", "0", "2"
  };
  private static final String[] EXPECTED_RESULT = {"[3, 2, 1]", "[3, 2, 1]", "0", "2", "0", "2"};

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesIncludingMaster().withAllApiLevels().build(),
        // Note that JDK8 is completely broken here.
        ImmutableList.of(JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public Java21CollectionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  public String[] getExpectedResult() {
    if (parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V15_0_0)) {
      return EXPECTED_RESULT;
    }
    if (parameters.getDexRuntimeVersion().isOlderThanOrEqual(Version.V4_4_4)) {
      if (compilationSpecification.isProgramShrink()) {
        // R8 repairs the program by rebinding SequencedCollection>>foo to LinkedList>>foo.
        return OLD_EXPECTED_RESULT_R8_FIXED_4;
      }
      return OLD_EXPECTED_RESULT_4;
    }
    if (compilationSpecification.isProgramShrink()) {
      // R8 repairs the program by rebinding SequencedCollection>>foo to LinkedList>>foo.
      return OLD_EXPECTED_RESULT_R8_FIXED_5_PLUS;
    }
    return OLD_EXPECTED_RESULT_5_PLUS;
  }

  @Test
  public void testReference() throws Exception {
    Assume.assumeTrue(
        "Run only once",
        libraryDesugaringSpecification == JDK11 && compilationSpecification == D8_L8DEBUG);
    testForD8()
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines(getExpectedResult());
  }

  @Test
  public void testDesugaredLib() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .enableInliningAnnotations()
        .allowDiagnosticWarningMessages(parameters.getApiLevel().equals(AndroidApiLevel.MAIN))
        .overrideLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.MAIN))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines(getExpectedResult());
  }

  static class Executor {

    public static void main(String[] args) {
      run(External::listReversed);
      run(External::seqColReversed);

      run(External::linkedList);
      run(External::seqList);
    }

    private static void run(Runnable r) {
      try {
        r.run();
      } catch (Throwable t) {
        System.out.println(t.getClass());
      }
    }

    static class External {

      @NeverInline
      static void seqColReversed() {
        SequencedCollection<Integer> seq = getList();
        System.out.println(seq.reversed());
      }

      @NeverInline
      static void listReversed() {
        List<Integer> list = getList();
        System.out.println(list.reversed());
      }

      @NeverInline
      private static List<Integer> getList() {
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        return list;
      }

      @NeverInline
      private static LinkedList<Integer> getLinkedList() {
        LinkedList<Integer> list = new LinkedList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        return list;
      }

      @NeverInline
      static void linkedList() {
        LinkedList<Integer> ll = getLinkedList();
        ll.addFirst(0);
        ll.removeLast();
        System.out.println(ll.getFirst());
        System.out.println(ll.getLast());
      }

      @NeverInline
      static void seqList() {
        SequencedCollection<Integer> ll = getLinkedList();
        ll.addFirst(0);
        ll.removeLast();
        System.out.println(ll.getFirst());
        System.out.println(ll.getLast());
      }
    }
  }
}
