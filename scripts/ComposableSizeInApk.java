// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.optimize.compose.ComposeReferences;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.nio.file.Paths;

public class ComposableSizeInApk {

  public static void main(String[] args) throws IOException {
    AndroidApp androidApp = AndroidApp.builder().addProgramFile(Paths.get(args[0])).build();
    InternalOptions options = new InternalOptions();
    ComposeReferences composeReferences = new ComposeReferences(options.dexItemFactory());
    int size =
        new ApplicationReader(androidApp, options, Timing.empty())
            .read().classes().stream()
                .flatMap(c -> Streams.stream(c.programMethods()))
                .filter(m -> m.getAnnotations().hasAnnotation(composeReferences.composableType))
                .filter(m -> m.getDefinition().hasCode())
                .mapToInt(m -> m.getDefinition().getCode().asDexCode().codeSizeInBytes())
                .sum();
    if (size == 0) {
      String name = "androidx.compose.runtime.Composable";
      System.err.println("Warning: To collect the size of @Composable functions, ");
      System.err.println("all @Composable annotations must be retained.");
      System.err.println("");
      System.err.println("This can be achieved using the following keep annotation:");
      System.err.println("");
      System.err.println("@KeepEdge(");
      System.err.println("consequences =");
      System.err.println("    @KeepTarget(");
      System.err.println("        kind = KeepItemKind.ONLY_METHODS,");
      System.err.println("        methodAnnotatedByClassName = \"" + name + "\",");
      System.err.println("        constraints = {},");
      System.err.println("        constrainAnnotations =");
      System.err.println("            @AnnotationPattern(");
      System.err.println("                name = \"" + name + "\",");
      System.err.println("                retention = RetentionPolicy.CLASS)))");
    }
    System.out.println(size);
  }
}
