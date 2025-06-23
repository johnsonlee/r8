// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.utils.DexVersion.Layout.CONTAINER_DEX;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.ByteBufferProvider;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.dex.FileWriter.ByteBufferResult;
import com.android.tools.r8.dex.FileWriter.DexContainerSection;
import com.android.tools.r8.dex.FileWriter.MapItem;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.utils.BitUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.android.tools.r8.utils.timing.TimingMerger;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

class ApplicationWriterContainer extends ApplicationWriter {
  protected ApplicationWriterContainer(
      AppView<?> appView, Marker marker, DexIndexedConsumer consumer) {
    super(appView, marker, consumer);
  }

  @Override
  protected void writeVirtualFiles(
      ExecutorService executorService,
      List<VirtualFile> virtualFiles,
      List<DexString> forcedStrings,
      Timing timing) {
    TimingMerger merger = timing.beginMerger("Write files", executorService);
    Collection<Timing> timings;
    Map<FeatureSplit, List<VirtualFile>> virtualFilesForContainers = new HashMap<>();
    List<VirtualFile> virtualFilesOutsideContainer = new ArrayList<>();
    virtualFiles.forEach(
        virtualFile -> {
          // Only use container format for OutputMode DexIndexed. Create one container per feature.
          if (virtualFile.getPrimaryClassDescriptor() != null) {
            virtualFilesOutsideContainer.add(virtualFile);
          } else {
            FeatureSplit split = virtualFile.getFeatureSplitOrBase();
            virtualFilesForContainers
                .computeIfAbsent(split, ignoreKey(ArrayList::new))
                .add(virtualFile);
          }
        });
    virtualFiles = null;

    timings = new ArrayList<>();
    // Write non container virtual files.
    for (VirtualFile virtualFile : virtualFilesOutsideContainer) {
      Timing fileTiming = Timing.create("VirtualFile " + virtualFile.getId(), options);
      writeVirtualFile(virtualFile, fileTiming, forcedStrings);
      fileTiming.end();
      timings.add(fileTiming);
    }
    // Write container virtual files.
    virtualFilesForContainers.forEach(
        (split, virtualFilesForContainer) ->
            writeContainer(forcedStrings, timings, virtualFilesForContainer));

    merger.add(timings);
    merger.end();
  }

  private void writeContainer(
      List<DexString> forcedStrings, Collection<Timing> timings, List<VirtualFile> virtualFiles) {
    ProgramConsumer consumer;
    ByteBufferProvider byteBufferProvider;
    if (programConsumer != null) {
      consumer = programConsumer;
      byteBufferProvider = programConsumer;
    } else if (virtualFiles.get(0).getFeatureSplit() != null) {
      ProgramConsumer featureConsumer = virtualFiles.get(0).getFeatureSplit().getProgramConsumer();
      assert featureConsumer instanceof DexIndexedConsumer;
      consumer = featureConsumer;
      byteBufferProvider = (DexIndexedConsumer) featureConsumer;
    } else {
      consumer = options.getDexIndexedConsumer();
      byteBufferProvider = options.getDexIndexedConsumer();
    }

    DexOutputBuffer dexOutputBuffer = new DexOutputBuffer(byteBufferProvider);
    byte[] tempForAssertions = new byte[] {};

    int offset = 0;
    List<DexContainerSection> sections = new ArrayList<>();

    for (int i = 0; i < virtualFiles.size(); i++) {
      VirtualFile virtualFile = virtualFiles.get(i);
      Timing fileTiming = Timing.create("VirtualFile " + virtualFile.getId(), options);
      if (virtualFile.isEmpty()) {
        continue;
      }
      DexContainerSection section =
          writeVirtualFileSection(
              virtualFile,
              fileTiming,
              forcedStrings,
              offset,
              dexOutputBuffer,
              i == virtualFiles.size() - 1);

      if (InternalOptions.assertionsEnabled()) {
        // Check that writing did not modify already written sections.
        byte[] outputSoFar = dexOutputBuffer.asArray();
        for (int j = 0; j < offset; j++) {
          assert tempForAssertions[j] == outputSoFar[j];
        }
        // Copy written sections including the one just written
        tempForAssertions = new byte[section.getLayout().getEndOfFile()];
        for (int j = 0; j < section.getLayout().getEndOfFile(); j++) {
          tempForAssertions[j] = outputSoFar[j];
        }
      }

      offset = section.getLayout().getEndOfFile();
      assert BitUtils.isAligned(4, offset);
      sections.add(section);
      fileTiming.end();
      timings.add(fileTiming);
    }

    if (globalsSyntheticsConsumer != null) {
      globalsSyntheticsConsumer.finished(appView);
    } else if (options.hasGlobalSyntheticsConsumer()) {
      // Make sure to also call finished even if no global output was generated.
      options.getGlobalSyntheticsConsumer().finished(appView.reporter());
    }

    if (sections.isEmpty()) {
      return;
    }

    updateStringIdsSizeAndOffset(dexOutputBuffer, sections);

    ByteBufferResult result =
        new ByteBufferResult(
            dexOutputBuffer.stealByteBuffer(),
            sections.get(sections.size() - 1).getLayout().getEndOfFile());
    ByteDataView data =
        new ByteDataView(result.buffer.array(), result.buffer.arrayOffset(), result.length);
    // TODO(b/249922554): Add timing of passing to consumer.
    if (consumer instanceof DexFilePerClassFileConsumer) {
      assert false;
    } else {
      ((DexIndexedConsumer) consumer).accept(0, data, Sets.newIdentityHashSet(), options.reporter);
    }
  }

  private void updateStringIdsSizeAndOffset(
      DexOutputBuffer dexOutputBuffer, List<DexContainerSection> sections) {
    // The last section has the shared string_ids table. Now it is written the final size and
    // offset is known and the remaining sections can be updated to point to the shared table.
    DexContainerSection lastSection = ListUtils.last(sections);
    int stringIdsSize = lastSection.getFileWriter().getMixedSectionOffsets().getStringData().size();
    int stringIdsOffset = lastSection.getLayout().stringIdsOffset;
    int containerSize = lastSection.getLayout().getEndOfFile();
    for (DexContainerSection section : sections) {
      // Update container size in all sections.
      dexOutputBuffer.moveTo(section.getLayout().headerOffset + Constants.CONTAINER_SIZE_OFFSET);
      dexOutputBuffer.putInt(containerSize);
      if (section != lastSection) {
        // Update the string_ids size and offset in the header.
        dexOutputBuffer.moveTo(section.getLayout().headerOffset + Constants.STRING_IDS_SIZE_OFFSET);
        dexOutputBuffer.putInt(stringIdsSize);
        dexOutputBuffer.putInt(stringIdsOffset);
        // Write the map. The map is sorted by offset, so write all entries after setting
        // string_ids and sorting.
        dexOutputBuffer.moveTo(section.getLayout().getMapOffset());
        List<MapItem> mapItems =
            section
                .getLayout()
                .generateMapInfo(
                    section.getFileWriter(),
                    section.getLayout().headerOffset,
                    stringIdsSize,
                    stringIdsOffset,
                    lastSection.getLayout().getStringDataOffsets());
        int originalSize = dexOutputBuffer.getInt();
        int size = 0;
        for (MapItem mapItem : mapItems) {
          size += mapItem.write(dexOutputBuffer);
        }
        assert originalSize == size;
        // Calculate signature and checksum after the map is written.
        section.getFileWriter().writeSignature(section.getLayout(), dexOutputBuffer);
        section.getFileWriter().writeChecksum(section.getLayout(), dexOutputBuffer);
      } else {
        dexOutputBuffer.moveTo(section.getLayout().getMapOffset());
        List<MapItem> mapItems =
            section
                .getLayout()
                .generateMapInfo(
                    section.getFileWriter(),
                    section.getLayout().headerOffset,
                    stringIdsSize,
                    stringIdsOffset,
                    lastSection.getLayout().getStringDataOffsets());
        int originalSize = dexOutputBuffer.getInt();
        int size = 0;
        for (MapItem mapItem : mapItems) {
          size += mapItem.write(dexOutputBuffer);
        }
        assert originalSize == size;
        // Calculate signature and checksum after the map is written.
        section.getFileWriter().writeSignature(section.getLayout(), dexOutputBuffer);
        section.getFileWriter().writeChecksum(section.getLayout(), dexOutputBuffer);
      }
    }
  }

  private DexContainerSection writeVirtualFileSection(
      VirtualFile virtualFile,
      Timing timing,
      List<DexString> forcedStrings,
      int offset,
      DexOutputBuffer outputBuffer,
      boolean last) {
    assert !virtualFile.isEmpty();
    assert BitUtils.isAligned(4, offset);
    printItemUseInfo(virtualFile);

    timing.begin("Reindex for lazy strings");
    ObjectToOffsetMapping objectMapping = virtualFile.getObjectMapping();
    objectMapping.computeAndReindexForLazyDexStrings(forcedStrings);
    timing.end();

    timing.begin("Write bytes");
    DexContainerSection section =
        writeDexFile(objectMapping, outputBuffer, virtualFile, timing, offset, last);
    timing.end();
    return section;
  }

  protected DexContainerSection writeDexFile(
      ObjectToOffsetMapping objectMapping,
      DexOutputBuffer dexOutputBuffer,
      VirtualFile virtualFile,
      Timing timing,
      int offset,
      boolean includeStringData) {
    FileWriter fileWriter =
        new FileWriter(
            appView,
            dexOutputBuffer,
            objectMapping,
            getDesugaredLibraryCodeToKeep(),
            virtualFile,
            includeStringData);
    // Collect the non-fixed sections.
    timing.time("collect", fileWriter::collect);
    // Generate and write the bytes.
    return timing.time("generate", () -> fileWriter.generate(offset, CONTAINER_DEX));
  }
}
