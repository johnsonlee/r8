// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.partial.predicate.R8PartialPredicateCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ExperimentalApiPatternParseTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testPackagePrefix() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    for (String pattern :
        new String[] {
          "a.**,b.**", "a.**, b.**", " a.**,b.** ", "\ta.**\t,\nb.**\u2000\u2001\u2002"
        }) {
      R8PartialCompilationConfiguration config =
          R8PartialCompilationConfiguration.fromIncludeExcludePatterns(pattern, pattern);
      for (R8PartialPredicateCollection predicate :
          new R8PartialPredicateCollection[] {
            config.getIncludePredicates(), config.getExcludePredicates()
          }) {
        assertTrue(predicate.test(factory.createString("La/A;")));
        assertTrue(predicate.test(factory.createString("La/a/A;")));
        assertTrue(predicate.test(factory.createString("Lb/A;")));
        assertTrue(predicate.test(factory.createString("Lb/a/A;")));
      }
    }
  }

  @Test
  public void testClassPrefix() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    for (String pattern :
        new String[] {"a.*,b.A*", "a.*, b.A*", " a.*,b.A* ", "\ta.*\t,\nb.A*\u2000\u2001\u2002"}) {
      R8PartialCompilationConfiguration config =
          R8PartialCompilationConfiguration.fromIncludeExcludePatterns(pattern, pattern);
      for (R8PartialPredicateCollection predicate :
          new R8PartialPredicateCollection[] {
            config.getIncludePredicates(), config.getExcludePredicates()
          }) {
        assertTrue(predicate.test(factory.createString("La/A;")));
        assertTrue(predicate.test(factory.createString("La/Aa;")));
        assertFalse(predicate.test(factory.createString("La/a/A;")));
        assertTrue(predicate.test(factory.createString("Lb/A;")));
        assertTrue(predicate.test(factory.createString("Lb/Aa;")));
        assertFalse(predicate.test(factory.createString("Lb/Aa/a;")));
      }
    }
  }
}
