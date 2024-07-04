// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepEdgeAstTest extends TestBase {

  private static String CLASS = "com.example.Foo";

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepEdgeAstTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  public static String extract(KeepEdge edge) {
    StringBuilder builder = new StringBuilder();
    KeepRuleExtractor extractor = new KeepRuleExtractor(builder::append);
    extractor.extract(edge);
    return builder.toString();
  }

  @Test
  public void testKeepAll() {
    BindingsHelper helper = new BindingsHelper();
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        KeepTarget.builder().setItemReference(helper.freshAnyClass()).build())
                    .addTarget(
                        KeepTarget.builder().setItemReference(helper.freshAnyMember()).build())
                    .build())
            .setBindings(helper.build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-keep,allowaccessmodification class ** { void finalize(); }",
            "-keepclassmembers,allowaccessmodification class ** { *; }"),
        extract(edge));
  }

  @Test
  public void testSoftPinViaConstraints() {
    BindingsHelper helper = new BindingsHelper();
    KeepConstraints constraints =
        KeepConstraints.builder()
            .add(KeepConstraint.classInstantiate())
            .add(KeepConstraint.methodInvoke())
            .add(KeepConstraint.fieldGet())
            .add(KeepConstraint.fieldSet())
            .build();
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        KeepTarget.builder()
                            .setItemReference(helper.freshAnyClass())
                            .setConstraints(constraints)
                            .build())
                    .addTarget(
                        KeepTarget.builder()
                            .setItemReference(helper.freshAnyMember())
                            .setConstraints(constraints)
                            .build())
                    .build())
            .setBindings(helper.build())
            .build();
    // Pinning just the use constraints points will issue the full inverse of the known options,
    // e.g., 'allowaccessmodification'.
    List<String> options = ImmutableList.of("shrinking", "obfuscation", "accessmodification");
    String allows = String.join(",allow", options);
    // The "any" item will be split in two rules, one for the targeted types and one for the
    // targeted members.
    assertEquals(
        StringUtils.unixLines(
            "-keep,allow" + allows + " class ** { void finalize(); }",
            "-keepclassmembers,allow" + allows + " class ** { *; }"),
        extract(edge));
  }
  @Test
  public void testKeepClass() {
    BindingsHelper helper = new BindingsHelper();
    KeepTarget target = target(classItem(CLASS), helper);
    KeepConsequences consequences = KeepConsequences.builder().addTarget(target).build();
    KeepEdge edge =
        KeepEdge.builder().setConsequences(consequences).setBindings(helper.build()).build();
    assertEquals(
        StringUtils.unixLines(
            "-keep,allowaccessmodification class " + CLASS + " { void finalize(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInitIfReferenced() {
    BindingsHelper helper = new BindingsHelper();
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(condition(classItem(CLASS), helper))
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        target(
                            buildMemberItem(CLASS, helper)
                                .setMemberPattern(defaultInitializerPattern())
                                .build(),
                            helper))
                    .build())
            .setBindings(helper.build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-keepclassmembers,allowaccessmodification class " + CLASS + " { void <init>(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceIfReferenced() {
    BindingsHelper helper = new BindingsHelper();
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(
                        KeepCondition.builder()
                            .setItemReference(helper.freshClassBinding(classItem(CLASS)))
                            .build())
                    .build())
            .setConsequences(
                KeepConsequences.builder().addTarget(target(classItem(CLASS), helper)).build())
            .setBindings(helper.build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-if class "
                + CLASS
                + " -keep,allowaccessmodification class "
                + CLASS
                + " { void finalize(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceAndInitIfReferenced() {
    BindingsHelper helper = new BindingsHelper();
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(condition(classItem(CLASS), helper))
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(target(classItem(CLASS), helper))
                    .addTarget(
                        target(
                            buildMemberItem(CLASS, helper)
                                .setMemberPattern(defaultInitializerPattern())
                                .build(),
                            helper))
                    .build())
            .setBindings(helper.build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-keepclassmembers,allowaccessmodification class " + CLASS + " { void <init>(); }",
            "-if class "
                + CLASS
                + " -keep,allowaccessmodification class "
                + CLASS
                + " { void finalize(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceAndInitIfReferencedWithBinding() {
    BindingsHelper helper = new BindingsHelper();
    KeepClassBindingReference clazz = helper.freshClassBinding(classItem(CLASS));
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(KeepCondition.builder().setItemReference(clazz).build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(target(clazz))
                    .addTarget(
                        target(
                            KeepMemberItemPattern.builder()
                                .setClassReference(clazz)
                                .setMemberPattern(defaultInitializerPattern())
                                .build(),
                            helper))
                    .build())
            .setBindings(helper.build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-if class "
                + CLASS
                + " -keepclasseswithmembers,allowaccessmodification class "
                + CLASS
                + " { void <init>(); }"),
        extract(edge));
  }

  @Test
  public void testKeepZeroOrMorePackage() {
    BindingsHelper helper = new BindingsHelper();
    KeepClassBindingReference clazz =
        helper.freshClassBinding(
            KeepClassItemPattern.builder()
                .setClassNamePattern(
                    KeepQualifiedClassNamePattern.builder()
                        .setPackagePattern(
                            KeepPackagePattern.builder()
                                .append(KeepPackageComponentPattern.exact("a"))
                                .append(KeepPackageComponentPattern.zeroOrMore())
                                .append(KeepPackageComponentPattern.exact("b"))
                                .build())
                        .build())
                .build());
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(KeepConsequences.builder().addTarget(target(clazz)).build())
            .setBindings(helper.build())
            .build();
    // Extraction fails due to the package structure.
    try {
      extract(edge);
    } catch (KeepEdgeException e) {
      if (e.getMessage().contains("Unsupported use of zero-or-more package pattern")) {
        return;
      }
    }
    fail("Expected extraction to fail");
  }

  private KeepClassBindingReference classItemBinding(KeepBindingSymbol bindingName) {
    return KeepBindingReference.forClass(bindingName);
  }

  private KeepTarget target(KeepItemPattern item, BindingsHelper helper) {
    return target(helper.freshItemBinding(item));
  }

  private KeepTarget target(KeepBindingReference item) {
    return KeepTarget.builder().setItemReference(item).build();
  }

  private KeepCondition condition(KeepItemPattern item, BindingsHelper helper) {
    return condition(helper.freshItemBinding(item));
  }

  private KeepCondition condition(KeepBindingReference item) {
    return KeepCondition.builder().setItemReference(item).build();
  }

  private KeepClassItemPattern classItem(String typeName) {
    return buildClassItem(typeName).build();
  }

  private KeepClassItemPattern.Builder buildClassItem(String typeName) {
    return KeepClassItemPattern.builder()
        .setClassNamePattern(KeepQualifiedClassNamePattern.exact(typeName));
  }

  private KeepMemberItemPattern.Builder buildMemberItem(String holderType, BindingsHelper helper) {
    return buildMemberItem(helper.freshClassBinding(buildClassItem(holderType).build()));
  }

  private static KeepMemberItemPattern.Builder buildMemberItem(KeepClassBindingReference holder) {
    return KeepMemberItemPattern.builder()
        .setClassReference(holder)
        .setMemberPattern(KeepMemberPattern.allMembers());
  }

  private KeepMemberPattern defaultInitializerPattern() {
    return KeepMethodPattern.builder()
        .setNamePattern(KeepMethodNamePattern.instanceInitializer())
        .setParametersPattern(KeepMethodParametersPattern.none())
        .setReturnTypeVoid()
        .build();
  }

  private static class BindingsHelper {
    private final KeepBindings.Builder builder = KeepBindings.builder();

    public KeepClassBindingReference freshClassBinding(KeepClassItemPattern pattern) {
      KeepBindingSymbol symbol = builder.generateFreshSymbol("CLASS");
      builder.addBinding(symbol, pattern);
      return KeepClassBindingReference.forClass(symbol);
    }

    public KeepMemberBindingReference freshMemberBinding(KeepMemberItemPattern pattern) {
      KeepBindingSymbol symbol = builder.generateFreshSymbol("MEMBER");
      builder.addBinding(symbol, pattern);
      return KeepMemberBindingReference.forMember(symbol);
    }

    public KeepBindingReference freshItemBinding(KeepItemPattern pattern) {
      return pattern.apply(this::freshClassBinding, this::freshMemberBinding);
    }

    public KeepClassBindingReference freshAnyClass() {
      return freshClassBinding(KeepItemPattern.anyClass());
    }

    public KeepMemberBindingReference freshAnyMember() {
      return freshMemberBinding(
          buildMemberItem(freshClassBinding(KeepItemPattern.anyClass())).build());
    }

    public KeepBindings build() {
      return builder.build();
    }
  }
}
