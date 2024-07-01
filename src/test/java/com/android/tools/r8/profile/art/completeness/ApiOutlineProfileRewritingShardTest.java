// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiOutlineProfileRewritingShardTest extends TestBase {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.M;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public AndroidApiLevel getApiLevelForRuntime() {
    return parameters.isCfRuntime()
        ? AndroidApiLevel.B
        : parameters.getRuntime().maxSupportedApiLevel();
  }

  public boolean isLibraryClassAlwaysPresent() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(classApiLevel);
  }

  public boolean isLibraryClassPresentInCurrentRuntime() {
    return getApiLevelForRuntime().isGreaterThanOrEqualTo(classApiLevel);
  }

  @Test
  public void testD8() throws Exception {
    D8TestCompileResult intermediateCompileResult = compileToIntermediateDex(Main.class);
    D8TestCompileResult otherIntermediateCompileResult = compileToIntermediateDex(OtherMain.class);
    mergeDex(intermediateCompileResult, otherIntermediateCompileResult);
  }

  private D8TestCompileResult compileToIntermediateDex(Class<?> mainClass) throws Exception {
    return testForD8(parameters.getBackend())
        .addProgramClasses(mainClass)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addArtProfileForRewriting(getArtProfile(mainClass))
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .release()
        .setIntermediate(true)
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(
            (profileInspector, inspector) ->
                inspectIntermediateProfile(profileInspector, inspector, mainClass));
  }

  private void mergeDex(
      D8TestCompileResult intermediateCompileResult,
      D8TestCompileResult otherIntermediateCompileResult)
      throws Exception {
    testForD8(parameters.getBackend())
        .addProgramFiles(
            intermediateCompileResult.writeToZip(), otherIntermediateCompileResult.writeToZip())
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addArtProfileForRewriting(
            buildIntermediateArtProfile(intermediateCompileResult, otherIntermediateCompileResult))
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .release()
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectMergedProfile)
        .applyIf(
            isLibraryClassPresentInCurrentRuntime(),
            testBuilder -> testBuilder.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::inspectRunResult);
  }

  private ExternalArtProfile getArtProfile(Class<?> mainClass) {
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(mainClass))
        .build();
  }

  private ExternalArtProfile buildIntermediateArtProfile(
      D8TestCompileResult intermediateCompileResult,
      D8TestCompileResult otherIntermediateCompileResult) {
    ExternalArtProfile.Builder intermediateArtProfileBuilder = ExternalArtProfile.builder();
    intermediateCompileResult
        .getResidualArtProfile()
        .forEach(intermediateArtProfileBuilder::addRule, intermediateArtProfileBuilder::addRule);
    otherIntermediateCompileResult
        .getResidualArtProfile()
        .forEach(intermediateArtProfileBuilder::addRule, intermediateArtProfileBuilder::addRule);
    return intermediateArtProfileBuilder.build();
  }

  private void inspectIntermediateProfile(
      ArtProfileInspector profileInspector, CodeInspector inspector, Class<?> mainClass)
      throws Exception {
    // Verify that outlining happened.
    verifyThat(inspector, parameters, LibraryClass.class)
        .applyIf(
            isLibraryClassAlwaysPresent(),
            verifier ->
                verifier.hasNotConstClassOutlinedFrom(mainClass.getMethod("main", String[].class)),
            verifier ->
                verifier.hasConstClassOutlinedFrom(mainClass.getMethod("main", String[].class)));

    // Check outline was added to program.
    ClassSubject apiOutlineClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticApiOutlineClass(mainClass, 0));
    assertThat(apiOutlineClassSubject, isAbsentIf(isLibraryClassAlwaysPresent()));

    MethodSubject apiOutlineMethodSubject = apiOutlineClassSubject.uniqueMethod();
    assertThat(apiOutlineMethodSubject, isAbsentIf(isLibraryClassAlwaysPresent()));

    // Verify the residual profile contains the outline method and its holder when present.
    profileInspector
        .assertContainsClassRule(Reference.classFromClass(mainClass))
        .assertContainsMethodRule(MethodReferenceUtils.mainMethod(mainClass))
        .applyIf(
            !isLibraryClassAlwaysPresent(),
            i ->
                i.assertContainsClassRule(apiOutlineClassSubject)
                    .assertContainsMethodRule(apiOutlineMethodSubject))
        .assertContainsNoOtherRules();
  }

  private void inspectMergedProfile(ArtProfileInspector profileInspector, CodeInspector inspector) {
    // Due to deduplication of the synthetic outlines we expect only three classes when outlining.
    assertEquals(
        2 + BooleanUtils.intValue(!isLibraryClassAlwaysPresent()), inspector.allClasses().size());

    ClassSubject apiOutlineClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticApiOutlineClass(Main.class, 0));
    assertThat(apiOutlineClassSubject, isAbsentIf(isLibraryClassAlwaysPresent()));

    MethodSubject apiOutlineMethodSubject = apiOutlineClassSubject.uniqueMethod();
    assertThat(apiOutlineMethodSubject, isAbsentIf(isLibraryClassAlwaysPresent()));

    ClassSubject otherApiOutlineClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticApiOutlineClass(OtherMain.class, 0));
    assertThat(otherApiOutlineClassSubject, isAbsent());

    // Verify the residual profile contains the outline method and its holder when present.
    profileInspector
        .assertContainsClassRules(
            Reference.classFromClass(Main.class), Reference.classFromClass(OtherMain.class))
        .assertContainsMethodRules(
            MethodReferenceUtils.mainMethod(Main.class),
            MethodReferenceUtils.mainMethod(OtherMain.class))
        .applyIf(
            !isLibraryClassAlwaysPresent(),
            i ->
                i.assertContainsClassRule(apiOutlineClassSubject)
                    .assertContainsMethodRule(apiOutlineMethodSubject))
        .assertContainsNoOtherRules();
  }

  private void inspectRunResult(SingleTestRunResult<?> runResult) {
    runResult.applyIf(
        isLibraryClassPresentInCurrentRuntime(),
        ignore -> runResult.assertSuccessWithOutputLines("class " + typeName(LibraryClass.class)),
        ignore -> runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(LibraryClass.class);
    }
  }

  public static class OtherMain {

    public static void main(String[] args) {
      System.out.println(LibraryClass.class);
    }
  }

  // Only present from api 23.
  public static class LibraryClass {}
}
