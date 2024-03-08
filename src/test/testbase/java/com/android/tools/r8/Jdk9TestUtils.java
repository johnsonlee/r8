// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.desugar.LibraryFilesHelper.getJdk9LibraryFiles;

import com.android.tools.r8.desugar.LibraryFilesHelper;
import org.junit.rules.TemporaryFolder;

public class Jdk9TestUtils {

  public static ThrowableConsumer<R8FullTestBuilder> addJdk9LibraryFiles(
      TemporaryFolder temporaryFolder) {
    return builder -> builder.addLibraryFiles(LibraryFilesHelper.getJdk9LibraryFiles(temporaryFolder));
  }

}
