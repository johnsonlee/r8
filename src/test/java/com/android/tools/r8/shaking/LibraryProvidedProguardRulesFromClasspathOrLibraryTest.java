// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.OriginMatcher.hasPart;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryProvidedProguardRulesFromClasspathOrLibraryTest
    extends LibraryProvidedProguardRulesTestBase {

  @interface Keep {}

  public interface Interface {}

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public LibraryType libraryType;

  @Parameter(2)
  public boolean isClasspath;

  @Parameters(name = "{0}, libraryType: {1}, isClasspath {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build(),
        LibraryType.values(),
        BooleanUtils.values());
  }

  private Path buildLibrary(List<String> rules) throws Exception {
    return buildLibrary(libraryType, ImmutableList.of(Interface.class, Keep.class), rules);
  }

  private CodeInspector runTest(List<String> rules) throws Exception {
    Path library = buildLibrary(rules);
    return testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class)
        .applyIf(
            isClasspath,
            b -> b.addClasspathFiles(library),
            b ->
                b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
                    .addLibraryFiles(library))
        .setMinApi(parameters)
        .apply(b -> ToolHelper.setReadEmbeddedRulesFromClasspathAndLibrary(b.getBuilder(), true))
        .compile()
        .inspector();
  }

  private CodeInspector runTest(String rules) throws Exception {
    return runTest(ImmutableList.of(rules));
  }

  @Test
  public void providedKeepRuleImplements() throws Exception {
    CodeInspector inspector =
        runTest("-keep class * implements " + Interface.class.getTypeName() + " { *; }");
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assertThat(inspector.clazz(A.class), notIf(isPresent(), libraryType.isAar()));
    assertThat(inspector.clazz(B.class), not(isPresent()));
  }

  @Test
  public void providedKeepRuleAnnotated() throws Exception {
    CodeInspector inspector = runTest("-keep @" + Keep.class.getTypeName() + " class * { *; }");
    assertThat(inspector.clazz(A.class), not(isPresent()));
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assertThat(inspector.clazz(B.class), notIf(isPresent(), libraryType.isAar()));
  }

  @Test
  public void providedKeepRuleImplementsOrAnnotated() throws Exception {
    CodeInspector inspector =
        runTest(
            ImmutableList.of(
                "-keep class * implements " + Interface.class.getTypeName() + " { *; }",
                "-keep @" + Keep.class.getTypeName() + " class * { *; }"));
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assertThat(inspector.clazz(A.class), notIf(isPresent(), libraryType.isAar()));
    assertThat(inspector.clazz(B.class), notIf(isPresent(), libraryType.isAar()));
  }

  @Test
  public void providedKeepRuleSyntaxError() {
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assumeTrue(!libraryType.isAar());
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramFiles(buildLibrary(ImmutableList.of("error")))
                .setMinApi(parameters)
                .apply(
                    b ->
                        ToolHelper.setReadEmbeddedRulesFromClasspathAndLibrary(
                            b.getBuilder(), true))
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorThatMatches(
                            allOf(
                                diagnosticMessage(containsString("Expected char '-'")),
                                diagnosticOrigin(hasPart("META-INF/proguard/jar.rules")),
                                diagnosticOrigin(instanceOf(ArchiveEntryOrigin.class))))));
  }

  @Test
  public void providedKeepRuleInjarsError() {
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assumeTrue(!libraryType.isAar());
    assertThrows(
        CompilationFailedException.class,
        () -> {
          Path library = buildLibrary(ImmutableList.of("-injars some.jar"));
          testForR8(parameters.getBackend())
              .applyIf(
                  isClasspath,
                  b -> b.addClasspathFiles(library),
                  b ->
                      b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
                          .addLibraryFiles(library))
              .setMinApi(parameters)
              .apply(
                  b -> ToolHelper.setReadEmbeddedRulesFromClasspathAndLibrary(b.getBuilder(), true))
              .compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorThatMatches(
                          diagnosticMessage(
                              containsString("Options with file names are not supported"))));
        });
  }

  @Test
  public void providedKeepRuleIncludeError() {
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assumeTrue(!libraryType.isAar());
    assertThrows(
        CompilationFailedException.class,
        () -> {
          Path library = buildLibrary(ImmutableList.of("-include other.rules"));
          testForR8(parameters.getBackend())
              .applyIf(
                  isClasspath,
                  b -> b.addClasspathFiles(library),
                  b ->
                      b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
                          .addLibraryFiles(library))
              .setMinApi(parameters)
              .apply(
                  b -> ToolHelper.setReadEmbeddedRulesFromClasspathAndLibrary(b.getBuilder(), true))
              .compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorThatMatches(
                          diagnosticMessage(
                              containsString("Options with file names are not supported"))));
        });
  }

  static class TestProvider implements ClassFileResourceProvider, DataResourceProvider {

    @Override
    public ProgramResource getProgramResource(String descriptor) {
      byte[] bytes;
      try {
        bytes = ByteStreams.toByteArray(Keep.class.getResourceAsStream("K.class"));
      } catch (IOException e) {
        return null;
      }
      return ProgramResource.fromBytes(
          Origin.unknown(),
          Kind.CF,
          bytes,
          Collections.singleton(DescriptorUtils.javaTypeToDescriptor(Keep.class.getTypeName())));
    }

    @Override
    public Set<String> getClassDescriptors() {
      return null;
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return this;
    }

    @Override
    public void accept(Visitor visitor) throws ResourceException {
      throw new ResourceException(Origin.unknown(), "Cannot provide data resources after all");
    }
  }

  @Test
  public void throwingDataResourceProvider() {
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assumeTrue(!libraryType.isAar());
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .applyIf(
                    isClasspath,
                    b -> b.addClasspathResourceProviders(new TestProvider()),
                    b ->
                        b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
                            .addLibraryResourceProviders(new TestProvider()))
                .setMinApi(parameters)
                .apply(
                    b ->
                        ToolHelper.setReadEmbeddedRulesFromClasspathAndLibrary(
                            b.getBuilder(), true))
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorThatMatches(
                            allOf(
                                diagnosticMessage(
                                    containsString("Cannot provide data resources after all")),
                                diagnosticOrigin(is(Origin.unknown()))))));
  }

  static class A implements Interface {}

  @Keep
  static class B {}
}
