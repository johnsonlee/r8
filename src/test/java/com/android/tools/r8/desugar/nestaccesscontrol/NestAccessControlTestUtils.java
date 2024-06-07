// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;

import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class NestAccessControlTestUtils {

  public static final Path JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA11_JAR_DIR).resolve("nesthostexample" + JAR_EXTENSION);
  public static final Path CLASSES_PATH =
      Paths.get(ToolHelper.getExamplesJava11BuildDir()).resolve("nesthostexample/");

  public static final List<String> CLASS_NAMES =
      ImmutableList.of(
          "BasicNestHostWithInnerClassFields",
          "BasicNestHostWithInnerClassFields$BasicNestedClass",
          "BasicNestHostWithInnerClassMethods",
          "BasicNestHostWithInnerClassMethods$BasicNestedClass",
          "BasicNestHostWithInnerClassConstructors",
          "BasicNestHostWithInnerClassConstructors$BasicNestedClass",
          "BasicNestHostWithInnerClassConstructors$UnInstantiatedClass",
          "BasicNestHostWithAnonymousInnerClass",
          "BasicNestHostWithAnonymousInnerClass$1",
          "BasicNestHostWithAnonymousInnerClass$InterfaceForAnonymousClass",
          "BasicNestHostClassMerging",
          "BasicNestHostClassMerging$MiddleInner",
          "BasicNestHostClassMerging$MiddleOuter",
          "BasicNestHostClassMerging$InnerMost",
          "BasicNestHostTreePruning",
          "BasicNestHostTreePruning$Pruned",
          "BasicNestHostTreePruning$NotPruned",
          "NestHostInlining",
          "NestHostInlining$InnerWithPrivAccess",
          "NestHostInlining$InnerNoPrivAccess",
          "NestHostInlining$EmptyNoPrivAccess",
          "NestHostInlining$EmptyWithPrivAccess",
          "NestHostInliningSubclasses",
          "NestHostInliningSubclasses$InnerWithPrivAccess",
          "NestHostInliningSubclasses$InnerNoPrivAccess",
          "OutsideInliningNoAccess",
          "OutsideInliningWithAccess",
          "NestPvtMethodCallInlined",
          "NestPvtMethodCallInlined$Inner",
          "NestPvtMethodCallInlined$InnerInterface",
          "NestPvtMethodCallInlined$InnerInterfaceImpl",
          "NestPvtMethodCallInlined$InnerSub",
          "NestPvtFieldPropagated",
          "NestPvtFieldPropagated$Inner",
          "NestHostExample",
          "NestHostExample$NestMemberInner",
          "NestHostExample$NestMemberInner$NestMemberInnerInner",
          "NestHostExample$StaticNestMemberInner",
          "NestHostExample$StaticNestMemberInner$StaticNestMemberInnerInner",
          "NestHostExample$StaticNestInterfaceInner",
          "NestHostExample$ExampleEnumCompilation");
  public static final ImmutableMap<String, String> MAIN_CLASSES =
      ImmutableMap.<String, String>builder()
          .put("fields", "BasicNestHostWithInnerClassFields")
          .put("methods", "BasicNestHostWithInnerClassMethods")
          .put("constructors", "BasicNestHostWithInnerClassConstructors")
          .put("anonymous", "BasicNestHostWithAnonymousInnerClass")
          .put("all", "NestHostExample")
          .put("merge", "BasicNestHostClassMerging")
          .put("prune", "BasicNestHostTreePruning")
          .put("inlining", "NestHostInlining")
          .put("inliningSub", "NestHostInliningSubclasses")
          .put("pvtCallInlined", "NestPvtMethodCallInlined")
          .put("memberPropagated", "NestPvtFieldPropagated")
          .build();

  public static String getMainClass(String id) {
    return "nesthostexample." + MAIN_CLASSES.get(id);
  }


}
