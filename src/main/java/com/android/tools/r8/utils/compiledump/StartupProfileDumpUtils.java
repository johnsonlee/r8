// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.compiledump;

import com.android.tools.r8.D8Command;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.UTF8TextInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class StartupProfileDumpUtils {
  public static void addStartupProfiles(List<Path> inputs, R8Command.Builder builder) {
    builder.addStartupProfileProviders(createProviders(inputs));
  }

  public static void addStartupProfiles(List<Path> inputs, D8Command.Builder builder) {
    builder.addStartupProfileProviders(createProviders(inputs));
  }

  private static List<StartupProfileProvider> createProviders(List<Path> inputs) {
    return inputs.stream()
        .map(StartupProfileDumpUtils::createStartupProfileProvider)
        .collect(Collectors.toList());
  }

  private static StartupProfileProvider createStartupProfileProvider(Path path) {
    return new StartupProfileProvider() {
      @Override
      public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
        try {
          startupProfileBuilder.addHumanReadableArtProfile(
              new UTF8TextInputStream(path), builder -> {});
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Origin getOrigin() {
        return new PathOrigin(path);
      }
    };
  }
}
