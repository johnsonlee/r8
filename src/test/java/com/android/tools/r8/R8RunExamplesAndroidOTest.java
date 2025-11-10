// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner.IgnoreIfVmOlderThan;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.OffOrAuto;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class R8RunExamplesAndroidOTest extends RunExamplesAndroidOTest<R8Command.Builder> {

  private static final ArrayList<String> PROGUARD_OPTIONS = Lists.newArrayList(
      "-keepclasseswithmembers public class * {",
      "    public static void main(java.lang.String[]);",
      "}",
      "-dontobfuscate",
      "-allowaccessmodification"
  );

  private static ArrayList<String> getProguardOptionsNPlus(
      boolean enableProguardCompatibilityMode) {
    return Lists.newArrayList(
        "-keepclasseswithmembers public class * {",
        "    public static void main(java.lang.String[]);",
        "}",
        "-keepclasseswithmembers interface **$AnnotatedInterface { <methods>; }",
        "-neverinline interface **$AnnotatedInterface { static void annotatedStaticMethod(); }",
        "-keepattributes *Annotation*",
        "-dontobfuscate",
        "-allowaccessmodification",
        "-assumevalues class lambdadesugaringnplus.LambdasWithStaticAndDefaultMethods {",
        "  public static boolean isR8() return true;",
        "  public static boolean isProguardCompatibilityMode() return "
            + enableProguardCompatibilityMode
            + ";",
        "}");
  }

  private static Map<DexVm.Version, List<String>> alsoFailsOn =
      ImmutableMap.<DexVm.Version, List<String>>builder()
          .put(
              Version.V4_0_4,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V4_4_4,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V5_1_1,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V6_0_1,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V7_0_0,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V9_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V10_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V12_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V13_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V14_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V15_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V16_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(Version.DEFAULT, ImmutableList.of())
          .build();

  /**
   * Override test in {@link com.android.tools.r8.RunExamplesAndroidOTest} to allow diagnostic
   * warning messages.
   */
  @Test
  @Override
  public void desugarDefaultMethodInAndroidJar25() throws Throwable {
    test("DefaultMethodInAndroidJar25", "desugaringwithandroidjar25", "DefaultMethodInAndroidJar25")
        .withBuilder(
            builder ->
                builder
                    .addOptionsModification(
                        options -> options.interfaceMethodDesugaring = OffOrAuto.Auto)
                    .allowDiagnosticWarningMessages()
                    .setMinApi(AndroidApiLevel.K))
        .withAndroidJar(AndroidApiLevel.O)
        .withKeepAll()
        .run();
  }

  /**
   * Override test in {@link com.android.tools.r8.RunExamplesAndroidOTest} to allow diagnostic
   * warning messages.
   */
  @Test
  @Override
  public void desugarStaticMethodInAndroidJar25() throws Throwable {
    test("StaticMethodInAndroidJar25", "desugaringwithandroidjar25", "StaticMethodInAndroidJar25")
        .withBuilder(
            builder ->
                builder
                    .addOptionsModification(
                        options -> options.interfaceMethodDesugaring = OffOrAuto.Auto)
                    .allowDiagnosticWarningMessages()
                    .setMinApi(AndroidApiLevel.K))
        .withAndroidJar(AndroidApiLevel.O)
        .withKeepAll()
        .run();
  }

  @Test
  public void invokeCustomWithShrinking() throws Throwable {
    test("invokecustom-with-shrinking", "invokecustom", "InvokeCustom")
        .withBuilder(
            builder ->
                builder
                    .addKeepRuleFiles(
                        Paths.get(ToolHelper.EXAMPLES_ANDROID_O_DIR, "invokecustom/keep-rules.txt"))
                    .setMinApi(AndroidApiLevel.O))
        .run();
  }

  @Test
  public void invokeCustom2WithShrinking() throws Throwable {
    test("invokecustom2-with-shrinking", "invokecustom2", "InvokeCustom")
        .withBuilder(
            builder ->
                builder
                    .addKeepRuleFiles(
                        Paths.get(
                            ToolHelper.EXAMPLES_ANDROID_O_DIR, "invokecustom2/keep-rules.txt"))
                    .setMinApi(AndroidApiLevel.O))
        .run();
  }

  @Override
  @Test
  public void lambdaDesugaring() throws Throwable {
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withBuilder(
            builder ->
                builder
                    .addKeepRules(PROGUARD_OPTIONS)
                    .addOptionsModification(options -> options.enableClassInlining = false)
                    .setMinApi(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K)))
        .withDexCheck(
            (inspector, syntheticItems) ->
                checkLambdaCount(inspector, syntheticItems, 3, "lambdadesugaring"))
        .run(Paths.get(ToolHelper.THIRD_PARTY_DIR, "examplesAndroidOLegacy"));

    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withBuilder(
            builder ->
                builder
                    .addKeepRules(PROGUARD_OPTIONS)
                    .setMinApi(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K)))
        .withDexCheck(
            (inspector, syntheticItems) ->
                checkLambdaCount(inspector, syntheticItems, 0, "lambdadesugaring"))
        .run(Paths.get(ToolHelper.THIRD_PARTY_DIR, "examplesAndroidOLegacy"));
  }

  @Test
  public void testMultipleInterfacesLambdaOutValue() throws Throwable {
    // We can only remove trivial check casts for the lambda objects if we keep track all the
    // multiple interfaces we additionally specified for the lambdas
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withBuilder(
            builder ->
                builder
                    .addKeepRules(PROGUARD_OPTIONS)
                    .addKeepRules(
                        "-keep class lambdadesugaring.LambdaDesugaring {",
                        "  void testMultipleInterfaces();",
                        "}")
                    .setMinApi(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K)))
        .withDexCheck(inspector -> checkTestMultipleInterfacesCheckCastCount(inspector, 0))
        .run(Paths.get(ToolHelper.THIRD_PARTY_DIR, "examplesAndroidOLegacy"));
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void lambdaDesugaringWithDefaultMethods() throws Throwable {
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withBuilder(
            builder ->
                builder
                    .addKeepRules(PROGUARD_OPTIONS)
                    .addOptionsModification(options -> options.enableClassInlining = false)
                    .setMinApi(AndroidApiLevel.N))
        .withDexCheck(
            (inspector, syntheticItems) ->
                checkLambdaCount(inspector, syntheticItems, 3, "lambdadesugaring"))
        .run(Paths.get(ToolHelper.THIRD_PARTY_DIR, "examplesAndroidOLegacy"));

    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withBuilder(builder -> builder.addKeepRules(PROGUARD_OPTIONS).setMinApi(AndroidApiLevel.N))
        .withDexCheck(
            (inspector, syntheticItems) ->
                checkLambdaCount(inspector, syntheticItems, 0, "lambdadesugaring"))
        .run(Paths.get(ToolHelper.THIRD_PARTY_DIR, "examplesAndroidOLegacy"));
  }

  @Override
  @Test
  public void lambdaDesugaringNPlus() throws Throwable {
    lambdaDesugaringNPlus(false);
  }

  @Test
  public void lambdaDesugaringNPlusCompat() throws Throwable {
    lambdaDesugaringNPlus(true);
  }

  private void lambdaDesugaringNPlus(boolean enableProguardCompatibilityMode) throws Throwable {
    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withBuilder(
            builder ->
                builder
                    .addKeepRules(getProguardOptionsNPlus(enableProguardCompatibilityMode))
                    .addOptionsModification(
                        options -> {
                          options.enableClassInlining = false;
                          options.interfaceMethodDesugaring = OffOrAuto.Auto;
                        })
                    .enableProguardTestOptions()
                    .setMinApi(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K)))
        .withProguardCompatibilityMode(enableProguardCompatibilityMode)
        .withDexCheck(
            (inspector, syntheticItems) ->
                checkLambdaCount(inspector, syntheticItems, 2, "lambdadesugaringnplus"))
        .run();

    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withBuilder(
            builder ->
                builder
                    .addKeepRules(getProguardOptionsNPlus(enableProguardCompatibilityMode))
                    .addOptionsModification(
                        options -> options.interfaceMethodDesugaring = OffOrAuto.Auto)
                    .enableProguardTestOptions()
                    .setMinApi(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K)))
        .withProguardCompatibilityMode(enableProguardCompatibilityMode)
        .withDexCheck(
            (inspector, syntheticItems) ->
                checkLambdaCount(inspector, syntheticItems, 0, "lambdadesugaringnplus"))
        .run();
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void lambdaDesugaringNPlusWithDefaultMethods() throws Throwable {
    lambdaDesugaringNPlusWithDefaultMethods(false);
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void lambdaDesugaringNPlusWithDefaultMethodsCompat() throws Throwable {
    lambdaDesugaringNPlusWithDefaultMethods(true);
  }

  private void lambdaDesugaringNPlusWithDefaultMethods(boolean enableProguardCompatibilityMode)
      throws Throwable {
    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withBuilder(
            builder ->
                builder
                    .addKeepRules(getProguardOptionsNPlus(enableProguardCompatibilityMode))
                    .addOptionsModification(
                        options -> {
                          options.enableClassInlining = false;
                          options.interfaceMethodDesugaring = OffOrAuto.Auto;
                        })
                    .enableProguardTestOptions()
                    .setMinApi(AndroidApiLevel.N))
        .withProguardCompatibilityMode(enableProguardCompatibilityMode)
        .withDexCheck(
            (inspector, syntheticItems) ->
                checkLambdaCount(inspector, syntheticItems, 1, "lambdadesugaringnplus"))
        .run();

    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withBuilder(
            builder ->
                builder
                    .addKeepRules(getProguardOptionsNPlus(enableProguardCompatibilityMode))
                    .addOptionsModification(
                        options -> options.interfaceMethodDesugaring = OffOrAuto.Auto)
                    .enableProguardTestOptions()
                    .setMinApi(AndroidApiLevel.N))
        .withProguardCompatibilityMode(enableProguardCompatibilityMode)
        .withDexCheck(
            (inspector, syntheticItems) ->
                checkLambdaCount(inspector, syntheticItems, 0, "lambdadesugaringnplus"))
        .run();
  }

  private void checkLambdaCount(
      CodeInspector inspector,
      SyntheticItemsTestUtils syntheticItems,
      int maxExpectedCount,
      String prefix) {
    List<String> found = new ArrayList<>();
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (clazz.isSynthesizedJavaLambdaClass(syntheticItems)
          && clazz.getOriginalTypeName().startsWith(prefix)) {
        found.add(clazz.getOriginalTypeName());
      }
    }
    // lambdadesugaringnplus.LambdasWithStaticAndDefaultMethods$I$1
    // lambdadesugaringnplus.LambdasWithStaticAndDefaultMethods$0
    // expected:<1> but was:<2>
    assertEquals(StringUtils.lines(found), maxExpectedCount, found.size());
  }

  private void checkTestMultipleInterfacesCheckCastCount(
      CodeInspector inspector, int expectedCount) {
    ClassSubject clazz = inspector.clazz("lambdadesugaring.LambdaDesugaring");
    assert clazz.isPresent();
    MethodSubject method = clazz.method("void", "testMultipleInterfaces");
    assert method.isPresent();
    class Count {
      int i = 0;
    }
    final Count count = new Count();
    method
        .iterateInstructions(InstructionSubject::isCheckCast)
        .forEachRemaining(
            instruction -> {
              ++count.i;
            });
    assertEquals(expectedCount, count.i);
  }

  class R8TestRunner extends TestRunner<R8TestRunner> {

    private boolean enableProguardCompatibilityMode = false;
    private final List<Consumer<R8CompatTestBuilder>> testBuilderConsumers = new ArrayList<>();

    R8TestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    R8TestRunner withBuilder(Consumer<R8CompatTestBuilder> consumer) {
      testBuilderConsumers.add(consumer);
      return self();
    }

    @Override
    R8TestRunner withBuilderTransformation(Consumer<Builder> builderTransformation) {
      throw new Unreachable();
    }

    @Override
    R8TestRunner withMinApiLevel(AndroidApiLevel minApiLevel) {
      return withBuilder(builder -> builder.setMinApi(minApiLevel));
    }

    @Override R8TestRunner withKeepAll() {
      return withBuilder(
          builder -> builder.addDontObfuscate().addDontShrink().addKeepAllAttributes());
    }

    public R8TestRunner withProguardCompatibilityMode(boolean enableProguardCompatibilityMode) {
      this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
      return this;
    }

    @Override
    void build(
        Path inputFile, Path out, Box<SyntheticItemsTestUtils> syntheticItemsBox, OutputMode mode)
        throws Throwable {
      Backend backend;
      if (mode == OutputMode.ClassFile) {
        backend = Backend.CF;
      } else {
        assert mode == OutputMode.DexIndexed;
        backend = Backend.DEX;
      }
      testForR8Compat(backend, enableProguardCompatibilityMode)
          .addProgramFiles(inputFile)
          .addOptionsModification(this::combinedOptionConsumer)
          .apply(
              b -> {
                for (Consumer<R8CompatTestBuilder> testBuilderConsumer : testBuilderConsumers) {
                  testBuilderConsumer.accept(b);
                }
                visitFiles(getLegacyClassesRoot(inputFile, packageName), b::addProgramFiles);
                b.addLibraryFiles(
                    ToolHelper.getAndroidJar(
                        androidJarVersion == null
                            ? b.getMinApiLevel()
                            : androidJarVersion.getLevel()));
              })
          .collectSyntheticItems()
          .compile()
          .apply(cr -> syntheticItemsBox.set(cr.getSyntheticItems()))
          .writeToZip(out);
    }

    @Override
    R8TestRunner self() {
      return this;
    }
  }

  @Override
  R8TestRunner test(String testName, String packageName, String mainClass) {
    return new R8TestRunner(testName, packageName, mainClass);
  }

  @Override
  boolean expectedToFail(String name) {
    return super.expectedToFail(name) || failsOn(alsoFailsOn, name);
  }
}
