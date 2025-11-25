// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.isArchive;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@KeepForApi
public class ArchiveResourceProvider implements ProgramResourceProvider, DataResourceProvider {

  final Origin origin;
  final FilteredClassPath archive;
  final boolean ignoreDexInArchive;

  public static ArchiveResourceProvider fromArchive(Path archive, boolean ignoreDexInArchive) {
    return new ArchiveResourceProvider(FilteredClassPath.unfiltered(archive), ignoreDexInArchive);
  }

  ArchiveResourceProvider(FilteredClassPath archive, boolean ignoreDexInArchive) {
    this(archive, ignoreDexInArchive, new PathOrigin(archive.getPath()));
  }

  ArchiveResourceProvider(FilteredClassPath archive, boolean ignoreDexInArchive, Origin origin) {
    assert isArchive(archive.getPath());
    this.archive = archive;
    this.ignoreDexInArchive = ignoreDexInArchive;
    this.origin = origin;
  }

  public Origin getOrigin() {
    return origin;
  }

  private void readArchive(Consumer<ProgramResource> consumer) throws ResourceException {
    List<Kind> seenKinds;
    try {
      seenKinds =
          ArchiveResourceProviderUtils.readArchive(
              archive.getPath(),
              origin,
              (name, kind) -> archive.matchesFile(name) && (kind == Kind.CF || !ignoreDexInArchive),
              consumer);
    } catch (ZipException e) {
      throw new CompilationError(
          "Zip error while reading '" + archive + "': " + e.getMessage(), e);
    } catch (IOException e) {
      throw new ResourceException(origin, e);
    }
    if (seenKinds.size() == 2) {
      assert seenKinds.contains(Kind.CF);
      assert seenKinds.contains(Kind.DEX);
      throw new CompilationError(
          "Cannot create android app from an archive '"
              + archive
              + "' containing both DEX and Java-bytecode content",
          origin);
    }
  }

  @Override
  public Collection<ProgramResource> getProgramResources() throws ResourceException {
    List<ProgramResource> programResources = new ArrayList<>();
    readArchive(programResources::add);
    return programResources;
  }

  @Override
  public void getProgramResources(Consumer<ProgramResource> consumer) throws ResourceException {
    readArchive(consumer);
  }

  @Override
  public DataResourceProvider getDataResourceProvider() {
    return this;
  }

  @Override
  public void accept(Visitor resourceBrowser) throws ResourceException {
    try (ZipFile zipFile =
        FileUtils.createZipFile(archive.getPath().toFile(), StandardCharsets.UTF_8)) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (archive.matchesFile(name) && !isProgramResourceName(name)) {
          if (entry.isDirectory()) {
            resourceBrowser.visit(DataDirectoryResource.fromZip(zipFile, entry));
          } else {
            resourceBrowser.visit(DataEntryResource.fromZip(zipFile, entry));
          }
        }
      }
    } catch (ZipException e) {
      throw new ResourceException(origin, new CompilationError(
          "Zip error while reading '" + archive + "': " + e.getMessage(), e));
    } catch (IOException e) {
      throw new ResourceException(origin, new CompilationError(
          "I/O exception while reading '" + archive + "': " + e.getMessage(), e));
    }
  }

  boolean isProgramResourceName(String name) {
    return ZipUtils.isClassFile(name) || (ZipUtils.isDexFile(name) && !ignoreDexInArchive);
  }

  @Deprecated
  public void accept(Consumer<ProgramResource> visitor) throws ResourceException {
    new ArchiveResourceProviderHelper(this).accept(visitor, alwaysTrue());
  }

  public static class ArchiveResourceProviderHelper extends ArchiveResourceProvider {

    ArchiveResourceProviderHelper(ArchiveResourceProvider provider) {
      super(provider.archive, provider.ignoreDexInArchive, provider.origin);
    }

    public void accept(Consumer<ProgramResource> consumer, Predicate<ZipEntry> predicate)
        throws ResourceException {
      try (ZipFile zipFile =
          FileUtils.createZipFile(archive.getPath().toFile(), StandardCharsets.UTF_8)) {
        final Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          String name = entry.getName();
          if (archive.matchesFile(name) && isProgramResourceName(name) && predicate.test(entry)) {
            Origin entryOrigin = new ArchiveEntryOrigin(name, origin);
            try (InputStream stream = zipFile.getInputStream(entry)) {
              if (ZipUtils.isDexFile(name)) {
                OneShotByteResource resource =
                    OneShotByteResource.create(
                        entryOrigin, Kind.DEX, ByteStreams.toByteArray(stream), null);
                consumer.accept(resource);
              } else if (ZipUtils.isClassFile(name)) {
                OneShotByteResource resource =
                    OneShotByteResource.create(
                        entryOrigin,
                        Kind.CF,
                        ByteStreams.toByteArray(stream),
                        Collections.singleton(DescriptorUtils.guessTypeDescriptor(name)));
                consumer.accept(resource);
              }
            }
          }
        }
      } catch (ZipException e) {
        throw new ResourceException(
            origin,
            new CompilationError(
                "Zip error while reading '" + archive + "': " + e.getMessage(), e));
      } catch (IOException e) {
        throw new ResourceException(
            origin,
            new CompilationError(
                "I/O exception while reading '" + archive + "': " + e.getMessage(), e));
      }
    }
  }
}
