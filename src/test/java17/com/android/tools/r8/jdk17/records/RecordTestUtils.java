// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;

public class RecordTestUtils {

  public static void assertRecordsAreRecords(CodeInspector inspector) {
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (clazz.getDexProgramClass().superType.toString().equals("java.lang.Record")) {
        assertTrue(clazz.getDexProgramClass().isRecord());
      }
    }
  }

  public static void assertNoJavaLangRecord(CodeInspector inspector, TestParameters parameters) {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.V)) {
      assertFalse(inspector.clazz("java.lang.RecordTag").isPresent());
    } else {
      assertFalse(inspector.clazz("java.lang.Record").isPresent());
    }
  }
}
