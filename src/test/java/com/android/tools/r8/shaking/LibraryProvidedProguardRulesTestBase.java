// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import java.util.List;

public class LibraryProvidedProguardRulesTestBase extends TestBase {

  enum LibraryType {
    JAR_WITH_RULES,
    AAR_WITH_RULES,
    AAR_WITH_RULES_ONLY_IN_JAR,
    AAR_WITH_RULES_BOTH_IN_JAR_AND_IN_AAR;

    boolean isAar() {
      return this != JAR_WITH_RULES;
    }

    boolean hasRulesInJar() {
      return this != AAR_WITH_RULES;
    }

    boolean hasRulesInAar() {
      return this == AAR_WITH_RULES || this == AAR_WITH_RULES_BOTH_IN_JAR_AND_IN_AAR;
    }
  }

  enum ProviderType {
    API,
    INJARS
  }

  protected Path buildLibrary(LibraryType libraryType, List<Class<?>> classes, List<String> rules)
      throws Exception {
    ZipBuilder jarBuilder =
        ZipBuilder.builder(temp.newFile(libraryType.isAar() ? "classes.jar" : "test.jar").toPath());
    addTestClassesToZip(jarBuilder.getOutputStream(), classes);
    if (libraryType.hasRulesInJar()) {
      for (int i = 0; i < rules.size(); i++) {
        String name = "META-INF/proguard/jar" + (i == 0 ? "" : i) + ".rules";
        jarBuilder.addText(name, rules.get(i));
      }
    }
    if (libraryType.isAar()) {
      Path jar = jarBuilder.build();
      String allRules = StringUtils.lines(rules);
      ZipBuilder aarBuilder = ZipBuilder.builder(temp.newFile("test.aar").toPath());
      aarBuilder.addFilesRelative(jar.getParent(), jar);
      if (libraryType.hasRulesInAar()) {
        aarBuilder.addText("proguard.txt", allRules);
      }
      return aarBuilder.build();
    } else {
      return jarBuilder.build();
    }
  }
}
