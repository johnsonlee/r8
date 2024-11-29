// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import static com.android.tools.r8.ir.desugar.records.RecordFullInstructionDesugaring.EQUALS_RECORD_METHOD_NAME;
import static com.android.tools.r8.ir.desugar.records.RecordFullInstructionDesugaring.GET_FIELDS_AS_OBJECTS_METHOD_NAME;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.ifThen;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordProfileRewritingTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe", "42", "true", "true", "true", "false", "false", "false", "false");

  private static final ClassReference MAIN_REFERENCE = Reference.classFromClass(SimpleRecord.class);
  private static final ClassReference PERSON_REFERENCE = Reference.classFromClass(Person.class);
  private static final ClassReference RECORD_REFERENCE =
      Reference.classFromTypeName("java.lang.Record");
  private static final ClassReference OBJECT_REFERENCE = Reference.classFromClass(Object.class);
  private static final ClassReference STRING_REFERENCE = Reference.classFromClass(String.class);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(runtimeWithRecordsSupport(parameters.getRuntime()));
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), MAIN_REFERENCE.getTypeName())
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    D8TestCompileResult compileResult =
        testForD8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .addArtProfileForRewriting(getArtProfile())
            .setMinApi(parameters)
            .compile();
    compileResult
        .inspectResidualArtProfile(
            profileInspector ->
                compileResult.inspectWithOptions(
                    inspector -> inspectD8(profileInspector, inspector),
                    options -> options.testing.disableRecordApplicationReaderMap = true))
        .run(parameters.getRuntime(), MAIN_REFERENCE.getTypeName())
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(ClassNotFoundException.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(runtimeWithRecordsSupport(parameters.getRuntime()) || parameters.isDexRuntime());
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepMainRule(MAIN_REFERENCE.getTypeName())
            .addKeepRules(
                "-neverpropagatevalue class " + PERSON_REFERENCE.getTypeName() + " { <fields>; }")
            .addArtProfileForRewriting(getArtProfile())
            .addOptionsModification(InlinerOptions::disableInlining)
            .applyIf(
                parameters.isCfRuntime(),
                testBuilder -> testBuilder.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
            .enableProguardTestOptions()
            .noHorizontalClassMergingOfSynthetics()
            .setMinApi(parameters)
            .compile();
    compileResult
        .inspectResidualArtProfile(
            profileInspector ->
                compileResult.inspectWithOptions(
                    inspector -> inspectR8(profileInspector, inspector),
                    options -> options.testing.disableRecordApplicationReaderMap = true))
        .run(parameters.getRuntime(), MAIN_REFERENCE.getTypeName())
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(MAIN_REFERENCE))
        .addClassRule(PERSON_REFERENCE)
        .addMethodRule(
            MethodReferenceUtils.instanceConstructor(
                PERSON_REFERENCE, STRING_REFERENCE, Reference.INT))
        .addMethodRule(
            Reference.method(PERSON_REFERENCE, "name", Collections.emptyList(), STRING_REFERENCE))
        .addMethodRule(
            Reference.method(PERSON_REFERENCE, "age", Collections.emptyList(), Reference.INT))
        .addMethodRule(
            Reference.method(
                PERSON_REFERENCE, "equals", ImmutableList.of(OBJECT_REFERENCE), Reference.BOOL))
        .addMethodRule(
            Reference.method(PERSON_REFERENCE, "hashCode", Collections.emptyList(), Reference.INT))
        .addMethodRule(
            Reference.method(
                PERSON_REFERENCE, "toString", Collections.emptyList(), STRING_REFERENCE))
        .build();
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(
        profileInspector,
        inspector,
        SyntheticItemsTestUtils.syntheticRecordTagClass(),
        false,
        false,
        parameters.canUseNestBasedAccessesWhenDesugaring(),
        !isRecordsFullyDesugaredForD8(parameters),
        false);
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(
        profileInspector,
        inspector,
        RECORD_REFERENCE,
        parameters.canHaveNonReboundConstructorInvoke(),
        true,
        parameters.canUseNestBasedAccesses(),
        !isRecordsFullyDesugaredForR8(parameters),
        parameters.isCfRuntime());
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      ClassReference recordClassReference,
      boolean canHaveNonReboundConstructorInvoke,
      boolean canMergeRecordTag,
      boolean canUseNestBasedAccesses,
      boolean partialDesugaring,
      boolean recordDesugaringIsOff) { // Record desugaring is partial or full.
    ClassSubject mainClassSubject = inspector.clazz(MAIN_REFERENCE);
    assertThat(mainClassSubject, isPresent());

    MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());

    ClassSubject recordTagClassSubject = inspector.clazz(recordClassReference);
    assertThat(recordTagClassSubject, isAbsentIf(canMergeRecordTag || partialDesugaring));
    if (recordTagClassSubject.isPresent()) {
      assertEquals(1, recordTagClassSubject.allMethods().size());
    }

    MethodSubject recordTagInstanceInitializerSubject = recordTagClassSubject.init();
    assertThat(recordTagInstanceInitializerSubject, isPresentIf(recordTagClassSubject.isPresent()));

    ClassSubject personRecordClassSubject = inspector.clazz(PERSON_REFERENCE);
    assertThat(personRecordClassSubject, isPresent());
    assertEquals(
        partialDesugaring
            ? inspector.getTypeSubject(RECORD_REFERENCE.getTypeName())
            : canMergeRecordTag
                ? inspector.getTypeSubject(Object.class.getTypeName())
                : recordTagClassSubject.asTypeSubject(),
        personRecordClassSubject.getSuperType());
    assertEquals(
        recordDesugaringIsOff ? 6 : (canMergeRecordTag && !partialDesugaring) ? 9 : 8,
        personRecordClassSubject.allMethods().size());

    MethodSubject personDefaultInstanceInitializerSubject = personRecordClassSubject.init();
    assertThat(
        personDefaultInstanceInitializerSubject,
        isPresentIf(canMergeRecordTag && !partialDesugaring));

    MethodSubject personInstanceInitializerSubject =
        canMergeRecordTag
            ? personRecordClassSubject.init(String.class.getTypeName())
            : personRecordClassSubject.init(String.class.getTypeName(), "int");
    assertThat(personInstanceInitializerSubject, isPresent());

    // Name getters.
    MethodSubject nameMethodSubject = personRecordClassSubject.uniqueMethodWithOriginalName("name");
    assertThat(nameMethodSubject, isPresent());

    // Age getters.
    MethodSubject ageMethodSubject = personRecordClassSubject.uniqueMethodWithOriginalName("age");
    assertThat(ageMethodSubject, isPresent());

    // boolean equals(Object)
    MethodSubject getFieldsAsObjectsMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName(GET_FIELDS_AS_OBJECTS_METHOD_NAME);
    assertThat(getFieldsAsObjectsMethodSubject, isAbsentIf(recordDesugaringIsOff));

    MethodSubject equalsHelperMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName(EQUALS_RECORD_METHOD_NAME);
    assertThat(equalsHelperMethodSubject, isAbsentIf(recordDesugaringIsOff));

    MethodSubject equalsMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName("equals");
    assertThat(equalsMethodSubject, isPresent());
    assertThat(
        equalsMethodSubject,
        ifThen(!recordDesugaringIsOff, invokesMethod(equalsHelperMethodSubject)));

    // Objects.hashCode(Object) (used by helper generated for record hashCode).
    ClassSubject objectsHashCodeClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticBackportClass(PERSON_REFERENCE, 0));
    assertThat(
        objectsHashCodeClassSubject,
        isAbsentIf(recordDesugaringIsOff || canUseJavaUtilObjects(parameters)));
    MethodSubject objectsHashCodeMethodSubject = objectsHashCodeClassSubject.uniqueMethod();
    assertThat(objectsHashCodeMethodSubject, isAbsentIf(objectsHashCodeClassSubject.isAbsent()));

    // Objects.equals(Object, Object) (used by helper generated for record compare).
    ClassSubject objectsEqualsClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticBackportClass(PERSON_REFERENCE, 1));
    assertThat(
        objectsEqualsClassSubject,
        isAbsentIf(recordDesugaringIsOff || canUseJavaUtilObjects(parameters)));
    MethodSubject objectsEqualsMethodSubject = objectsEqualsClassSubject.uniqueMethod();
    assertThat(objectsEqualsMethodSubject, isAbsentIf(objectsEqualsClassSubject.isAbsent()));
    assertEquals(objectsHashCodeClassSubject.isPresent(), objectsEqualsClassSubject.isPresent());

    // int hashCode()
    ClassSubject hashCodeHelperClassSubject =
        inspector.clazz(
            SyntheticItemsTestUtils.syntheticRecordHelperClass(
                PERSON_REFERENCE, objectsHashCodeClassSubject.isAbsent() ? 0 : 2));
    assertThat(hashCodeHelperClassSubject, isAbsentIf(recordDesugaringIsOff));

    MethodSubject hashCodeHelperMethodSubject = hashCodeHelperClassSubject.uniqueMethod();
    assertThat(hashCodeHelperMethodSubject, isAbsentIf(recordDesugaringIsOff));

    MethodSubject hashCodeMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName("hashCode");
    assertThat(hashCodeMethodSubject, isPresent());
    assertThat(
        hashCodeMethodSubject,
        ifThen(!recordDesugaringIsOff, invokesMethod(hashCodeHelperMethodSubject)));

    // String toString()
    ClassSubject toStringHelperClassSubject =
        inspector.clazz(
            SyntheticItemsTestUtils.syntheticRecordHelperClass(
                PERSON_REFERENCE, objectsHashCodeClassSubject.isAbsent() ? 1 : 3));
    assertThat(toStringHelperClassSubject, isAbsentIf(recordDesugaringIsOff));

    MethodSubject toStringHelperMethodSubject = toStringHelperClassSubject.uniqueMethod();
    assertThat(toStringHelperMethodSubject, isAbsentIf(recordDesugaringIsOff));

    MethodSubject toStringMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName("toString");
    assertThat(toStringMethodSubject, isPresent());
    assertThat(
        toStringMethodSubject,
        ifThen(!recordDesugaringIsOff, invokesMethod(getFieldsAsObjectsMethodSubject)));
    assertThat(
        toStringMethodSubject,
        ifThen(!recordDesugaringIsOff, invokesMethod(toStringHelperMethodSubject)));

    profileInspector
        .assertContainsClassRules(mainClassSubject, personRecordClassSubject)
        .assertContainsMethodRules(
            mainMethodSubject,
            personInstanceInitializerSubject,
            nameMethodSubject,
            ageMethodSubject,
            equalsMethodSubject,
            hashCodeMethodSubject,
            toStringMethodSubject)
        .applyIf(
            !recordDesugaringIsOff && !canUseJavaUtilObjects(parameters),
            i ->
                i.assertContainsClassRules(objectsHashCodeClassSubject, objectsEqualsClassSubject)
                    .assertContainsMethodRules(
                        objectsHashCodeMethodSubject, objectsEqualsMethodSubject))
        .applyIf(
            canMergeRecordTag && !partialDesugaring,
            i -> i.assertContainsMethodRule(personDefaultInstanceInitializerSubject))
        .applyIf(
            !canMergeRecordTag && !partialDesugaring,
            j ->
                j.assertContainsClassRules(recordTagClassSubject)
                    .assertContainsMethodRule(recordTagInstanceInitializerSubject))
        .applyIf(
            !recordDesugaringIsOff,
            i ->
                i.assertContainsClassRules(hashCodeHelperClassSubject, toStringHelperClassSubject)
                    .assertContainsMethodRules(
                        equalsHelperMethodSubject,
                        getFieldsAsObjectsMethodSubject,
                        hashCodeHelperMethodSubject,
                        toStringHelperMethodSubject))
        .assertContainsNoOtherRules();
  }

  record Person(String name, int age) {}

  public class SimpleRecord {

    public static void main(String[] args) {
      Person janeDoe = new Person("Jane Doe", 42);
      System.out.println(janeDoe.name());
      System.out.println(janeDoe.age());

      // Test equals with self.
      System.out.println(janeDoe.equals(janeDoe));

      // Test equals with structurally equals Person.
      Person otherJaneDoe = new Person("Jane Doe", 42);
      System.out.println(janeDoe.equals(otherJaneDoe));
      System.out.println(otherJaneDoe.equals(janeDoe));

      // Test equals with not-structually equals Person.
      Person johnDoe = new Person("John Doe", 42);
      System.out.println(janeDoe.equals(johnDoe));
      System.out.println(johnDoe.equals(janeDoe));

      // Test equals with Object and null.
      System.out.println(janeDoe.equals(new Object()));
      System.out.println(janeDoe.equals(null));
    }
  }
}
