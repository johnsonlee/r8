// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.synthesis;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.ArchiveBuilder;
import com.android.tools.r8.utils.DirectoryBuilder;
import com.android.tools.r8.utils.OutputBuilder;
import com.android.tools.r8.utils.StringDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GlobalSyntheticsUtils {

  private GlobalSyntheticsUtils() {}

  public static GlobalSyntheticsConsumer determineGlobalSyntheticsConsumer(
      boolean intermediate,
      Path output,
      GlobalSyntheticsConsumer consumer,
      ProgramConsumer programConsumer) {
    assert output == null || consumer == null;
    if (!intermediate) {
      return null;
    }
    if (consumer != null) {
      return consumer;
    }
    if (output == null) {
      return null;
    }

    // Output is non-null, and we must create a consumer compatible with the program consumer.
    OutputBuilder builder =
        Files.isDirectory(output) ? new DirectoryBuilder(output) : new ArchiveBuilder(output);
    builder.open();

    if (programConsumer instanceof DexIndexedConsumer) {
      return new GlobalSyntheticsConsumer() {
        boolean written = false;

        @Override
        public synchronized void accept(
            ByteDataView data, ClassReference context, DiagnosticsHandler handler) {
          assert context == null;
          if (written) {
            String msg = "Attempt to write multiple global-synthetics files in dex-indexed mode.";
            handler.error(new StringDiagnostic(msg));
            throw new RuntimeException(msg);
          }
          builder.addFile("classes.globals", data, handler);
          builder.close(handler);
          written = true;
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
          // If not global info was written, close the builder with empty content.
          if (!written) {
            builder.close(handler);
            written = true;
          }
        }
      };
    }

    if (programConsumer instanceof DexFilePerClassFileConsumer) {
      return new GlobalSyntheticsConsumer() {

        @Override
        public void accept(ByteDataView data, ClassReference context, DiagnosticsHandler handler) {
          builder.addFile(context.getBinaryName() + ".globals", data, handler);
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
          builder.close(handler);
        }
      };
    }

    if (programConsumer instanceof ClassFileConsumer) {
      return new GlobalSyntheticsConsumer() {
        @Override
        public void accept(ByteDataView data, ClassReference context, DiagnosticsHandler handler) {
          builder.addFile(context.getBinaryName() + ".globals", data, handler);
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
          builder.close(handler);
        }
      };
    }

    throw new Unreachable("Unexpected program consumer type");
  }
}
