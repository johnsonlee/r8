// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class ProguardCompatibilityTestBase extends TestBase {

  protected Path proguardMap;

  public enum Shrinker {
    R8_COMPAT,
    R8_COMPAT_CF,
    R8,
    R8_CF;

    public boolean isR8() {
      return this == R8_COMPAT
          || this == R8_COMPAT_CF
          || this == R8
          || this == R8_CF;
    }

    public boolean isCompatR8() {
      return this == R8_COMPAT || this == R8_COMPAT_CF;
    }

    public boolean isR8Dex() {
      return this == R8_COMPAT || this == R8;
    }

    public boolean isFullModeR8() {
      return this == R8
          || this == R8_CF;
    }

    public boolean generatesDex() {
      return this == R8_COMPAT || this == R8;
    }

    public boolean generatesCf() {
      return this == R8_COMPAT_CF || this == R8_CF;
    }

    public Backend toBackend() {
      if (generatesDex()) {
        return Backend.DEX;
      }
      assert generatesCf();
      return Backend.CF;
    }
  }

  protected AndroidApp runShrinker(
      Shrinker mode, List<Class<?>> programClasses, Iterable<String> proguardConfigs)
      throws Exception {
    return runShrinker(
        mode, programClasses, String.join(System.lineSeparator(), proguardConfigs), null);
  }

  protected AndroidApp runShrinker(
      Shrinker mode,
      List<Class<?>> programClasses,
      Iterable<String> proguardConfigs,
      Consumer<InternalOptions> configure)
      throws Exception {
    return runShrinker(
        mode, programClasses, String.join(System.lineSeparator(), proguardConfigs), configure);
  }

  protected AndroidApp runShrinker(
      Shrinker mode,
      List<Class<?>> programClasses,
      String proguardConfig,
      Consumer<InternalOptions> configure)
      throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    switch (mode) {
      case R8_COMPAT:
        return runR8Compat(programClasses, proguardConfig, proguardMap, configure, Backend.DEX);
      case R8_COMPAT_CF:
        return runR8Compat(programClasses, proguardConfig, proguardMap, configure, Backend.CF);
      case R8:
        return runR8(programClasses, proguardConfig, proguardMap, configure, Backend.DEX);
      case R8_CF:
        return runR8(programClasses, proguardConfig, proguardMap, configure, Backend.CF);
    }
    throw new IllegalArgumentException("Unknown shrinker: " + mode);
  }

  protected CodeInspector inspectAfterShrinking(
      Shrinker mode, List<Class<?>> programClasses, List<String> proguardConfigs) throws Exception {
    return inspectAfterShrinking(mode, programClasses, proguardConfigs, null);
  }

  protected CodeInspector inspectAfterShrinking(
      Shrinker mode,
      List<Class<?>> programClasses,
      List<String> proguardConfigs,
      Consumer<InternalOptions> configure)
      throws Exception {
    return inspectAfterShrinking(
        mode, programClasses, String.join(System.lineSeparator(), proguardConfigs), configure);
  }

  protected CodeInspector inspectAfterShrinking(
      Shrinker mode,
      List<Class<?>> programClasses,
      String proguardConfig,
      Consumer<InternalOptions> configure)
      throws Exception {
    switch (mode) {
      case R8_COMPAT:
        return inspectR8CompatResult(programClasses, proguardConfig, configure, Backend.DEX);
      case R8_COMPAT_CF:
        return inspectR8CompatResult(programClasses, proguardConfig, configure, Backend.CF);
      case R8:
        return inspectR8Result(programClasses, proguardConfig, configure, Backend.DEX);
      case R8_CF:
        return inspectR8Result(programClasses, proguardConfig, configure, Backend.CF);
    }
    throw new IllegalArgumentException("Unknown shrinker: " + mode);
  }

  protected AndroidApp runR8(
      List<Class<?>> programClasses, String proguardConfig, Path proguardMap, Backend backend)
      throws Exception {
    return runR8(programClasses, proguardConfig, proguardMap, null, backend);
  }

  protected AndroidApp runR8(
      List<Class<?>> programClasses,
      String proguardConfig,
      Path proguardMap,
      Consumer<InternalOptions> configure,
      Backend backend)
      throws Exception {
    AndroidApp app = readClassesAndRuntimeJar(programClasses, backend);
    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(app, emptyConsumer(backend));
    ToolHelper.allowTestProguardOptions(builder);
    builder.addProguardConfiguration(
        ImmutableList.of(proguardConfig, toPrintMappingRule(proguardMap)), Origin.unknown());
    return ToolHelper.runR8(builder.build(), configure);
  }

  protected CodeInspector inspectR8Result(
      List<Class<?>> programClasses,
      String proguardConfig,
      Consumer<InternalOptions> configure,
      Backend backend)
      throws Exception {
    return new CodeInspector(runR8(programClasses, proguardConfig, null, configure, backend));
  }

  protected AndroidApp runR8Compat(
      List<Class<?>> programClasses,
      String proguardConfig,
      Path proguardMap,
      Consumer<InternalOptions> configure,
      Backend backend)
      throws Exception {
    CompatProguardCommandBuilder builder = new CompatProguardCommandBuilder(true);
    builder.setProguardMapConsumer(StringConsumer.emptyConsumer());
    ToolHelper.allowTestProguardOptions(builder);
    builder.addProguardConfiguration(
        ImmutableList.of(proguardConfig, toPrintMappingRule(proguardMap)), Origin.unknown());
    programClasses.forEach(
        clazz -> builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz)));
    if (backend == Backend.DEX) {
      builder.addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()));
      builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    } else {
      assert backend == Backend.CF;
      builder.addLibraryFiles(ToolHelper.getJava8RuntimeJar());
      builder.setProgramConsumer(ClassFileConsumer.emptyConsumer());
    }
    return ToolHelper.runR8(builder.build(), configure);
  }

  protected CodeInspector inspectR8CompatResult(
      List<Class<?>> programClasses,
      String proguardConfig,
      Consumer<InternalOptions> configure,
      Backend backend)
      throws Exception {
    return new CodeInspector(runR8Compat(programClasses, proguardConfig, null, configure, backend));
  }

  protected void verifyClassesPresent(
      CodeInspector codeInspector, Class<?>... classesOfInterest) {
    for (Class klass : classesOfInterest) {
      ClassSubject c = codeInspector.clazz(klass);
      assertThat(c, isPresent());
    }
  }

  protected void verifyClassesAbsent(
      CodeInspector codeInspector, Class<?>... classesOfInterest) {
    for (Class klass : classesOfInterest) {
      ClassSubject c = codeInspector.clazz(klass);
      assertThat(c, not(isPresent()));
    }
  }

  private String toPrintMappingRule(Path proguardMap) {
    return proguardMap == null ? "" : "-printmapping " + proguardMap.toAbsolutePath();
  }
}
