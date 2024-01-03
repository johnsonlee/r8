// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.gson;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.ProguardTestBuilder;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;

public class GsonTestBase extends TestBase {

  public static String SERIALIZED_NAME_CLASS_NAME = "com.google.gson.annotations.SerializedName";

  public void serializedNamePresentAndRenamed(CodeInspector inspector) {
    assertThat(inspector.clazz(SERIALIZED_NAME_CLASS_NAME), isPresentAndRenamed());
  }

  static void addRuntimeLibrary(TestShrinkerBuilder builder, TestParameters parameters) {
    // Gson use java.lang.ReflectiveOperationException.
    builder.addDefaultRuntimeLibraryWithReflectiveOperationException(parameters);
  }

  static void addGsonLibraryAndKeepRules(R8FullTestBuilder builder) {
    builder
        .addProgramResourceProviders(ArchiveProgramResourceProvider.fromArchive(ToolHelper.GSON))
        .addKeepRuleFiles(ToolHelper.GSON_KEEP_RULES)
        .allowUnusedProguardConfigurationRules();
  }

  static void addGsonLibraryAndKeepRules(ProguardTestBuilder builder) {
    builder.addProgramFiles(ToolHelper.GSON).addKeepRuleFiles(ToolHelper.GSON_KEEP_RULES);
  }
}
