// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.ResourceException;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepSpecVersion;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.Declaration;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.KeepSpec;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public abstract class KeepSpecificationSource {

  public static KeepSpecificationSource fromFile(Path path) {
    return new KeepSpecificationFile(path);
  }

  public static KeepSpecificationSource fromBytes(Origin origin, byte[] bytes) {
    return new KeepSpecificationBytes(origin, bytes);
  }

  private final Origin origin;

  private KeepSpecificationSource(Origin origin) {
    this.origin = origin;
  }

  public Origin getOrigin() {
    return origin;
  }

  public void parse(Consumer<KeepDeclaration> consumer) throws ResourceException {
    KeepSpec spec = read();
    KeepSpecVersion version = KeepSpecVersion.fromProto(spec.getVersion());
    if (version == KeepSpecVersion.UNKNOWN) {
      throw new ResourceException(getOrigin(), "Unknown keepspec version " + spec.getVersion());
    }
    for (Declaration declaration : spec.getDeclarationsList()) {
      KeepDeclaration parsedDeclaration = KeepDeclaration.fromProto(declaration, version);
      if (parsedDeclaration == null) {
        throw new ResourceException(getOrigin(), "Unable to parse declaration " + declaration);
      } else {
        consumer.accept(parsedDeclaration);
      }
    }
  }

  abstract KeepSpec read() throws ResourceException;

  private static class KeepSpecificationFile extends KeepSpecificationSource {

    private final Path path;

    private KeepSpecificationFile(Path path) {
      super(new PathOrigin(path));
      this.path = path;
    }

    @Override
    KeepSpec read() throws ResourceException {
      try (InputStream stream = Files.newInputStream(path)) {
        return KeepSpec.parseFrom(stream);
      } catch (IOException e) {
        throw new ResourceException(getOrigin(), e);
      }
    }
  }

  private static class KeepSpecificationBytes extends KeepSpecificationSource {

    private final byte[] content;

    public KeepSpecificationBytes(Origin origin, byte[] bytes) {
      super(origin);
      this.content = bytes;
    }

    @Override
    KeepSpec read() throws ResourceException {
      try {
        return KeepSpec.parseFrom(content);
      } catch (InvalidProtocolBufferException e) {
        throw new ResourceException(getOrigin(), e);
      }
    }
  }
}
