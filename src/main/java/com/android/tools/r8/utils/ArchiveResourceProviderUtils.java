// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ArchiveProgramResourceProvider.ZipFileSupplier;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ArchiveResourceProviderUtils {

  // Flag to disable the use of one-shot byte resources to support using R8 9.x with AGP 8.y.
  private static final boolean enableOneShotByteResource =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.enableOneShotByteResource", true);

  public static List<Kind> readArchive(
      Path archive,
      Origin origin,
      BiPredicate<String, Kind> predicate,
      Consumer<ProgramResource> consumer)
      throws IOException {
    ZipFileSupplier zipFileSupplier =
        () -> FileUtils.createZipFile(archive.toFile(), StandardCharsets.UTF_8);
    return readArchive(zipFileSupplier, origin, predicate, consumer);
  }

  public static List<Kind> readArchive(
      ZipFileSupplier zipFileSupplier,
      Origin origin,
      BiPredicate<String, Kind> predicate,
      Consumer<ProgramResource> consumer)
      throws IOException {
    BooleanBox seenCf = new BooleanBox();
    BooleanBox seenDex = new BooleanBox();
    try (ZipFile zipFile = zipFileSupplier.open()) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        try (InputStream stream = zipFile.getInputStream(entry)) {
          String name = entry.getName();
          Origin entryOrigin = new ArchiveEntryOrigin(name, origin);
          if (ZipUtils.isDexFile(name)) {
            if (predicate.test(name, Kind.DEX)) {
              consumer.accept(createProgramResource(Kind.DEX, entryOrigin, stream, null));
              seenDex.set();
            }
          } else if (ZipUtils.isClassFile(name)) {
            if (predicate.test(name, Kind.CF)) {
              String descriptor = DescriptorUtils.guessTypeDescriptor(name);
              consumer.accept(
                  createProgramResource(
                      Kind.CF, entryOrigin, stream, Collections.singleton(descriptor)));
              seenCf.set();
            }
          }
        }
      }
    }
    List<Kind> seenKinds = new ArrayList<>(seenCf.intValue() + seenDex.intValue());
    if (seenCf.isTrue()) {
      seenKinds.add(Kind.CF);
    }
    if (seenDex.isTrue()) {
      seenKinds.add(Kind.DEX);
    }
    return seenKinds;
  }

  private static ProgramResource createProgramResource(
      Kind kind, Origin origin, InputStream inputStream, Set<String> classDescriptors)
      throws IOException {
    byte[] bytes = ByteStreams.toByteArray(inputStream);
    return enableOneShotByteResource
        ? OneShotByteResource.create(kind, origin, bytes, classDescriptors)
        : ProgramResource.fromBytes(origin, kind, bytes, classDescriptors);
  }
}
