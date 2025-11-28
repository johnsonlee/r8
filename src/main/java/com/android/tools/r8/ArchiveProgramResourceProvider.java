// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.ArchiveResourceProviderUtils;
import com.android.tools.r8.utils.ArchiveResourceProviderUtils.ProgramResourceFactory;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.OneShotByteResource;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipFile;

/** Provider for archives of program resources. */
@KeepForApi
public class ArchiveProgramResourceProvider implements ProgramResourceProvider {

  @KeepForApi
  public interface ZipFileSupplier {
    ZipFile open() throws IOException;
  }

  public static boolean includeClassFileEntries(String entry) {
    return ZipUtils.isClassFile(entry);
  }

  public static boolean includeDexEntries(String entry) {
    return ZipUtils.isDexFile(entry);
  }

  public static boolean includeClassFileOrDexEntries(String entry) {
    return ZipUtils.isClassFile(entry) || ZipUtils.isDexFile(entry);
  }

  private final Origin origin;
  private final ZipFileSupplier supplier;
  private final Predicate<String> include;

  public static ArchiveProgramResourceProvider fromArchive(Path archive) {
    return fromArchive(archive, ArchiveProgramResourceProvider::includeClassFileOrDexEntries);
  }

  public static ArchiveProgramResourceProvider fromArchive(
      Path archive, Predicate<String> include) {
    return fromSupplier(
        new PathOrigin(archive),
        () -> FileUtils.createZipFile(archive.toFile(), StandardCharsets.UTF_8),
        include);
  }

  public static ArchiveProgramResourceProvider fromSupplier(
      Origin origin, ZipFileSupplier supplier) {
    return fromSupplier(
        origin, supplier, ArchiveProgramResourceProvider::includeClassFileOrDexEntries);
  }

  public static ArchiveProgramResourceProvider fromSupplier(
      Origin origin, ZipFileSupplier supplier, Predicate<String> include) {
    return new ArchiveProgramResourceProvider(origin, supplier, include);
  }

  private ArchiveProgramResourceProvider(
      Origin origin, ZipFileSupplier supplier, Predicate<String> include) {
    assert origin != null;
    assert supplier != null;
    assert include != null;
    this.origin = origin;
    this.supplier = supplier;
    this.include = include;
  }

  @Deprecated
  @Override
  public Collection<ProgramResource> getProgramResources() throws ResourceException {
    List<ProgramResource> programResources = new ArrayList<>();
    internalGetProgramResources(programResources::add, ProgramResource::fromBytes);
    return programResources;
  }

  @Override
  public void getProgramResources(Consumer<ProgramResource> consumer) throws ResourceException {
    internalGetProgramResources(consumer, OneShotByteResource::create);
  }

  private void internalGetProgramResources(
      Consumer<ProgramResource> consumer, ProgramResourceFactory programResourceFactory)
      throws ResourceException {
    List<Kind> seenKinds;
    try {
      seenKinds =
          ArchiveResourceProviderUtils.readArchive(
              supplier,
              origin,
              (name, kind) -> include.test(name),
              consumer,
              programResourceFactory);
    } catch (IOException e) {
      throw new ResourceException(origin, e);
    }
    if (seenKinds.size() == 2) {
      assert seenKinds.contains(Kind.CF);
      assert seenKinds.contains(Kind.DEX);
      throw new CompilationError(
          "Cannot create android app from an archive containing both DEX and Java-bytecode "
              + "content.",
          origin);
    }
  }
}
