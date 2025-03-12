// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.nest;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestInitArgumentContextTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean intermediate;

  @Parameter(2)
  public boolean legacyNestDesugaringIAClasses;

  @Parameters(name = "{0}, intermediate = {1}, legacyNestDesugaringIAClasses = {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void testD8SingleCompilationUnit() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(NestInitArgumentContextClass.class)
        .setMinApi(parameters)
        .setIntermediate(intermediate)
        .addOptionsModification(
            options -> options.legacyNestDesugaringIAClasses = legacyNestDesugaringIAClasses)
        .compile()
        .inspect(
            inspector ->
                assertEquals(
                    legacyNestDesugaringIAClasses ? 5 : 1,
                    inspector.allClasses().stream()
                        .map(ClassSubject::getFinalName)
                        .filter(name -> name.endsWith("-IA"))
                        .count()));
  }

  @Test
  public void testD8SeparateCompilationUnits() throws Exception {
    List<Class<?>> innerClasses =
        ImmutableList.of(
            NestInitArgumentContextClass.Inner1.class,
            NestInitArgumentContextClass.Inner2.class,
            NestInitArgumentContextClass.Inner3.class,
            NestInitArgumentContextClass.Inner4.class);
    List<Path> innerClassesCompiled = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      innerClassesCompiled.add(
          testForD8(parameters.getBackend())
              .addProgramClasses(innerClasses.get(i))
              .addClasspathClasses(NestInitArgumentContextClass.class)
              .addClasspathClasses(innerClasses)
              .setMinApi(parameters)
              .setIntermediate(intermediate)
              .addOptionsModification(
                  options -> options.legacyNestDesugaringIAClasses = legacyNestDesugaringIAClasses)
              .compile()
              .inspect(
                  inspector ->
                      assertEquals(
                          legacyNestDesugaringIAClasses ? 1 : 0,
                          inspector.allClasses().stream()
                              .map(ClassSubject::getFinalName)
                              .filter(name -> name.endsWith("-IA"))
                              .count()))
              .writeToZip());
    }

    Path outerClassCompiled =
        testForD8(parameters.getBackend())
            .addProgramClasses(NestInitArgumentContextClass.class)
            .addClasspathClasses(innerClasses)
            .setMinApi(parameters)
            .setIntermediate(intermediate)
            .addOptionsModification(
                options -> options.legacyNestDesugaringIAClasses = legacyNestDesugaringIAClasses)
            .compile()
            .inspect(
                inspector ->
                    assertEquals(
                        1,
                        inspector.allClasses().stream()
                            .map(ClassSubject::getFinalName)
                            .filter(name -> name.endsWith("-IA"))
                            .count()))
            .writeToZip();

    testForD8(parameters.getBackend())
        .addProgramFiles(innerClassesCompiled)
        .addProgramFiles(outerClassCompiled)
        .setMinApi(parameters)
        .addOptionsModification(
            options -> options.legacyNestDesugaringIAClasses = legacyNestDesugaringIAClasses)
        .compile()
        .inspect(
            inspector ->
                assertEquals(
                    legacyNestDesugaringIAClasses ? 5 : 1,
                    inspector.allClasses().stream()
                        .map(ClassSubject::getFinalName)
                        .filter(name -> name.endsWith("-IA"))
                        .count()));
  }
}
