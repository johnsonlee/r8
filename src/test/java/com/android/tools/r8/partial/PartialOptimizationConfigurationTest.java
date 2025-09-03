// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.partial.R8PartialCompilationConfiguration.Builder;
import com.android.tools.r8.partial.predicate.R8PartialPredicateCollection;
import com.android.tools.r8.references.Reference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialOptimizationConfigurationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testAddClass() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    Builder builder = R8PartialCompilationConfiguration.builder();
    builder.addClass(Reference.classFromTypeName("A"));
    builder.addClass(Reference.classFromTypeName("a.a.A"));
    builder.addClass(Reference.classFromTypeName("aa.bb.cc.DD"));
    R8PartialCompilationConfiguration configuration = builder.build();
    R8PartialPredicateCollection predicates = configuration.getIncludePredicates();
    assertEquals(3, predicates.size());
    assertTrue(configuration.getExcludePredicates().isEmpty());
    assertTrue(predicates.test(factory.createString("LA;")));
    assertFalse(predicates.test(factory.createString("La;")));
    assertFalse(predicates.test(factory.createString("La/A;")));
    assertTrue(predicates.test(factory.createString("La/a/A;")));
    assertFalse(predicates.test(factory.createString("La/b/B;")));
    assertFalse(predicates.test(factory.createString("La/a/a/A;")));
    assertFalse(predicates.test(factory.createString("Laa/A;")));
    assertFalse(predicates.test(factory.createString("Laa/bb;")));
    assertFalse(predicates.test(factory.createString("Laa/bb/CC;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/cc/DD;")));
    assertFalse(predicates.test(factory.createString("Laa/bb/cc/dd/EE;")));
  }

  @Test
  public void testAddPackage() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    Builder builder = R8PartialCompilationConfiguration.builder();
    builder.addPackage(Reference.packageFromString("a"));
    builder.addPackage(Reference.packageFromString("aa.bb"));
    R8PartialCompilationConfiguration configuration = builder.build();
    R8PartialPredicateCollection predicates = configuration.getIncludePredicates();
    assertEquals(2, predicates.size());
    assertTrue(configuration.getExcludePredicates().isEmpty());
    assertFalse(predicates.test(factory.createString("LA;")));
    assertFalse(predicates.test(factory.createString("La;")));
    assertTrue(predicates.test(factory.createString("La/A;")));
    assertFalse(predicates.test(factory.createString("La/a/A;")));
    assertFalse(predicates.test(factory.createString("La/b/B;")));
    assertFalse(predicates.test(factory.createString("La/a/a/A;")));
    assertFalse(predicates.test(factory.createString("Laa/A;")));
    assertFalse(predicates.test(factory.createString("Laa/bb;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/CC;")));
    assertFalse(predicates.test(factory.createString("Laa/bb/cc/DD;")));
    assertFalse(predicates.test(factory.createString("Laa/bb/cc/dd/EE;")));
  }

  @Test
  public void testAddPackagePackageAndSubpackages() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    Builder builder = R8PartialCompilationConfiguration.builder();
    builder.addPackageAndSubPackages(Reference.packageFromString("a"));
    builder.addPackageAndSubPackages(Reference.packageFromString("aa.bb"));
    R8PartialCompilationConfiguration configuration = builder.build();
    R8PartialPredicateCollection predicates = configuration.getIncludePredicates();
    assertEquals(2, predicates.size());
    assertTrue(configuration.getExcludePredicates().isEmpty());
    assertFalse(predicates.test(factory.createString("LA;")));
    assertFalse(predicates.test(factory.createString("La;")));
    assertTrue(predicates.test(factory.createString("La/A;")));
    assertTrue(predicates.test(factory.createString("La/a/A;")));
    assertTrue(predicates.test(factory.createString("La/b/B;")));
    assertTrue(predicates.test(factory.createString("La/a/a/A;")));
    assertFalse(predicates.test(factory.createString("Laa/A;")));
    assertFalse(predicates.test(factory.createString("Laa/bb;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/CC;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/cc/DD;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/cc/dd/EE;")));
  }

  @Test
  public void testMix() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    Builder builder = R8PartialCompilationConfiguration.builder();
    builder.addClass(Reference.classFromTypeName("aa.A"));
    builder.addClass(Reference.classFromTypeName("a.b.B"));
    builder.addPackage(Reference.packageFromString("a"));
    builder.addPackageAndSubPackages(Reference.packageFromString("aa.bb"));
    R8PartialCompilationConfiguration configuration = builder.build();
    R8PartialPredicateCollection predicates = configuration.getIncludePredicates();
    assertEquals(4, predicates.size());
    assertTrue(configuration.getExcludePredicates().isEmpty());
    assertFalse(predicates.test(factory.createString("LA;")));
    assertFalse(predicates.test(factory.createString("La;")));
    assertTrue(predicates.test(factory.createString("La/A;")));
    assertFalse(predicates.test(factory.createString("La/a/A;")));
    assertTrue(predicates.test(factory.createString("La/b/B;")));
    assertFalse(predicates.test(factory.createString("La/a/a/A;")));
    assertTrue(predicates.test(factory.createString("Laa/A;")));
    assertFalse(predicates.test(factory.createString("Laa/bb;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/CC;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/cc/DD;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/cc/dd/EE;")));
  }

  @Test
  public void testUnnamedPackage() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    Builder builder = R8PartialCompilationConfiguration.builder();
    builder.addPackage(Reference.packageFromString(""));
    R8PartialCompilationConfiguration configuration = builder.build();
    R8PartialPredicateCollection predicates = configuration.getIncludePredicates();
    assertEquals(1, predicates.size());
    assertTrue(configuration.getExcludePredicates().isEmpty());
    assertTrue(predicates.test(factory.createString("LA;")));
    assertTrue(predicates.test(factory.createString("La;")));
    assertFalse(predicates.test(factory.createString("La/A;")));
    assertFalse(predicates.test(factory.createString("La/a/A;")));
    assertFalse(predicates.test(factory.createString("La/b/A;")));
    assertFalse(predicates.test(factory.createString("La/a/a/A;")));
    assertFalse(predicates.test(factory.createString("Laa/A;")));
    assertFalse(predicates.test(factory.createString("Laa/bb;")));
    assertFalse(predicates.test(factory.createString("Laa/bb/CC;")));
    assertFalse(predicates.test(factory.createString("Laa/bb/cc/DD;")));
    assertFalse(predicates.test(factory.createString("Laa/bb/cc/dd/EE;")));
  }

  @Test
  public void testUnnamedPackageAndSubpackages() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    Builder builder = R8PartialCompilationConfiguration.builder();
    builder.addPackageAndSubPackages(Reference.packageFromString(""));
    R8PartialCompilationConfiguration configuration = builder.build();
    R8PartialPredicateCollection predicates = configuration.getIncludePredicates();
    assertEquals(1, predicates.size());
    assertTrue(configuration.getExcludePredicates().isEmpty());
    assertTrue(predicates.test(factory.createString("LA;")));
    assertTrue(predicates.test(factory.createString("La;")));
    assertTrue(predicates.test(factory.createString("La/A;")));
    assertTrue(predicates.test(factory.createString("La/a/A;")));
    assertTrue(predicates.test(factory.createString("La/b/A;")));
    assertTrue(predicates.test(factory.createString("La/a/a/A;")));
    assertTrue(predicates.test(factory.createString("Laa/A;")));
    assertTrue(predicates.test(factory.createString("Laa/bb;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/CC;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/cc/DD;")));
    assertTrue(predicates.test(factory.createString("Laa/bb/cc/dd/EE;")));
  }
}
