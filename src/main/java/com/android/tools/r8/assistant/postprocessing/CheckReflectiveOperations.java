// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing;

import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@KeepForApi
public class CheckReflectiveOperations {

  public static void main(String[] args) throws Exception {
    Path logFile = Paths.get(args[0]);
    Path keepInfoDirectory = Paths.get(args[1]);
    DexItemFactory dexItemFactory = new DexItemFactory();
    ReflectiveOperationJsonParser parser = new ReflectiveOperationJsonParser(dexItemFactory);
    List<ReflectiveEvent> reflectiveEvents = parser.parse(logFile);
    KeepInfoCollectionExported keepInfo = KeepInfoCollectionExported.parse(keepInfoDirectory);
    for (ReflectiveEvent event : reflectiveEvents) {
      System.out.println("Event: " + event + ", isKept: " + event.isKeptBy(keepInfo));
    }
  }
}
