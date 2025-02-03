// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.annotations.MethodAccessFlags;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepAccessVisibilityFlagsTest extends KeepAnnoTestBase {

  static final String EXPECTED =
      StringUtils.lines(
          "=== non-private fields:",
          "packagePrivateField",
          "protectedField",
          "publicField",
          "=== non-package-private methods:",
          "privateMethod",
          "protectedMethod",
          "publicMethod",
          "=== private and package-private fields:",
          "packagePrivateField",
          "privateField",
          "=== private and package-private methods:",
          "packagePrivateMethod",
          "privateMethod");

  static final String UNEXPECTED_PG =
      StringUtils.lines(
          "=== non-private fields:",
          "packagePrivateField",
          "protectedField",
          "publicField",
          "=== non-package-private methods:",
          "privateMethod",
          "protectedMethod",
          "publicMethod",
          "=== private and package-private fields:",
          // PG will rename and privatize the unused public and protected field!?
          "a",
          "b",
          "packagePrivateField",
          "privateField",
          "=== private and package-private methods:",
          "packagePrivateMethod",
          "privateMethod");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(TestClass.class)
        .allowAccessModification()
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(parameters.isPG() ? UNEXPECTED_PG : EXPECTED)
        .applyIf(parameters.isR8(), r -> r.inspect(this::checkOutput));
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(
        TestClass.class,
        A.class,
        FieldRuleTarget.class,
        MethodRuleTarget.class,
        MemberRuleTarget.class);
  }

  private static List<String> allMembers =
      ImmutableList.of(
          "publicField",
          "protectedField",
          "packagePrivateField",
          "privateField",
          "publicMethod",
          "protectedMethod",
          "packagePrivateMethod",
          "privateMethod");

  private void checkOutput(CodeInspector inspector) {
    assertPresent(
        inspector, FieldRuleTarget.class, "publicField", "protectedField", "packagePrivateField");
    assertPresent(
        inspector, MethodRuleTarget.class, "publicMethod", "protectedMethod", "privateMethod");
    assertPresent(
        inspector,
        MemberRuleTarget.class,
        "packagePrivateField",
        "privateField",
        "packagePrivateMethod",
        "privateMethod");
  }

  private void assertPresent(CodeInspector inspector, Class<?> clazz, String... members) {
    ClassSubject subject = inspector.clazz(clazz);
    assertThat(subject, isPresent());
    Set<String> expectedPresent = ImmutableSet.copyOf(members);
    for (String member : allMembers) {
      if (member.endsWith("Field")) {
        assertThat(
            subject.uniqueFieldWithOriginalName(member),
            expectedPresent.contains(member) ? isPresent() : isAbsent());
      } else {
        assertThat(
            subject.uniqueMethodWithOriginalName(member),
            expectedPresent.contains(member) ? isPresent() : isAbsent());
      }
    }
  }

  abstract static class FieldRuleTarget {
    public String publicField = "public";
    protected String protectedField = "protected";
    private String privateField = "private";
    String packagePrivateField = "package-private";

    public void publicMethod() {}

    protected void protectedMethod() {}

    private void privateMethod() {}

    void packagePrivateMethod() {}
  }

  abstract static class MethodRuleTarget {
    public String publicField = "public";
    protected String protectedField = "protected";
    private String privateField = "private";
    String packagePrivateField = "package-private";

    public void publicMethod() {}

    protected void protectedMethod() {}

    private void privateMethod() {}

    void packagePrivateMethod() {}
  }

  abstract static class MemberRuleTarget {
    public String publicField = "public";
    protected String protectedField = "protected";
    private String privateField = "private";
    String packagePrivateField = "package-private";

    public void publicMethod() {}

    protected void protectedMethod() {}

    private void privateMethod() {}

    void packagePrivateMethod() {}
  }

  static class A {

    @UsesReflection({
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          classConstant = FieldRuleTarget.class,
          constraintAdditions = {KeepConstraint.VISIBILITY_INVARIANT},
          fieldAccess = {FieldAccessFlags.NON_PRIVATE}),
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          classConstant = MethodRuleTarget.class,
          constraintAdditions = {KeepConstraint.VISIBILITY_INVARIANT},
          methodAccess = {MethodAccessFlags.NON_PACKAGE_PRIVATE}),
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          classConstant = MemberRuleTarget.class,
          constraintAdditions = {KeepConstraint.VISIBILITY_INVARIANT},
          memberAccess = {MemberAccessFlags.PACKAGE_PRIVATE, MemberAccessFlags.PRIVATE}),
    })
    void foo() {
      // Print all non-private fields.
      {
        System.out.println("=== non-private fields:");
        List<String> nonPrivateFields = new ArrayList<>();
        for (Field field : FieldRuleTarget.class.getDeclaredFields()) {
          int mod = field.getModifiers();
          if (!Modifier.isPrivate(mod)) {
            nonPrivateFields.add(field.getName());
          }
        }
        printSorted(nonPrivateFields);
      }
      // Print all non-package-private methods.
      {
        System.out.println("=== non-package-private methods:");
        List<String> nonPackagePrivateMethods = new ArrayList<>();
        for (Method method : MethodRuleTarget.class.getDeclaredMethods()) {
          int mod = method.getModifiers();
          if (Modifier.isPublic(mod) || Modifier.isProtected(mod) || Modifier.isPrivate(mod)) {
            nonPackagePrivateMethods.add(method.getName());
          }
        }
        printSorted(nonPackagePrivateMethods);
      }
      // Print all private and package-private members.
      {
        System.out.println("=== private and package-private fields:");
        List<String> privateOrPackagePrivateFields = new ArrayList<>();
        for (Field field : MemberRuleTarget.class.getDeclaredFields()) {
          int mod = field.getModifiers();
          if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) {
            privateOrPackagePrivateFields.add(field.getName());
          }
        }
        printSorted(privateOrPackagePrivateFields);
      }
      {
        System.out.println("=== private and package-private methods:");
        List<String> privateOrPackagePrivateMethods = new ArrayList<>();
        for (Method method : MemberRuleTarget.class.getDeclaredMethods()) {
          int mod = method.getModifiers();
          if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) {
            privateOrPackagePrivateMethods.add(method.getName());
          } else {
            // TODO(b/131130038): The package-private method should not be publicized.
            if (method.getName().equals("packagePrivateMethod")) {
              privateOrPackagePrivateMethods.add(method.getName());
            }
          }
        }
        printSorted(privateOrPackagePrivateMethods);
      }
    }

    // The order of methods and fields is different on stock JDKs depending on linux or windows
    // hosts. It is also different once compiled to DEX where the pools are split. Sort the
    // names lexicographically to avoid differences in output.
    private static void printSorted(List<String> strings) {
      strings.sort(String::compareTo);
      for (String string : strings) {
        System.out.println(string);
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
