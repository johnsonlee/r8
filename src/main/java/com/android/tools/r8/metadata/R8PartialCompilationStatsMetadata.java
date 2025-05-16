// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

public interface R8PartialCompilationStatsMetadata {

  /**
   * The sum of the bytecode sizes of the DEX code objects in the excldued part that is not subject
   * to optimization.
   */
  int getDexCodeSizeOfExcludedClassesInBytes();

  /**
   * The sum of the bytecode sizes of the DEX code objects in the included part that is subject to
   * optimization.
   */
  int getDexCodeSizeOfIncludedClassesInBytes();

  int getNumberOfExcludedClassesInInput();

  int getNumberOfIncludedClassesInInput();

  int getNumberOfIncludedClassesInOutput();
}
