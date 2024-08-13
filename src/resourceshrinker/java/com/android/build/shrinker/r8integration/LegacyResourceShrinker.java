// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.build.shrinker.r8integration;

import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.build.shrinker.NoDebugReporter;
import com.android.build.shrinker.ResourceShrinkerImplKt;
import com.android.build.shrinker.ResourceTableUtilKt;
import com.android.build.shrinker.graph.ProtoResourcesGraphBuilder;
import com.android.build.shrinker.obfuscation.ProguardMappingsRecorder;
import com.android.build.shrinker.r8integration.R8ResourceShrinkerState.R8ResourceShrinkerModel;
import com.android.build.shrinker.usages.DexFileAnalysisCallback;
import com.android.build.shrinker.usages.ProtoAndroidManifestUsageRecorderKt;
import com.android.build.shrinker.usages.R8ResourceShrinker;
import com.android.build.shrinker.usages.ToolsAttributeUsageRecorderKt;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.resources.usage.ResourceStore;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import com.android.tools.r8.FeatureSplit;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class LegacyResourceShrinker {
  private final Map<String, byte[]> dexInputs;
  private final Collection<PathAndBytes> resFolderInputs;
  private final Collection<PathAndBytes> xmlInputs;
  private List<String> proguardMapStrings;
  private final List<PathAndBytes> manifest;
  private final Map<PathAndBytes, FeatureSplit> resourceTables;

  public static class Builder {

    private final Map<String, byte[]> dexInputs = new HashMap<>();
    private final Map<Path, PathAndBytes> resFolderInputs = new HashMap<>();
    private final Map<Path, PathAndBytes> xmlInputs = new HashMap<>();

    private final List<PathAndBytes> manifests = new ArrayList<>();
    private final Map<PathAndBytes, FeatureSplit> resourceTables = new HashMap<>();
    private List<String> proguardMapStrings;

    private Builder() {}

    public Builder addManifest(Path path, byte[] bytes) {
      manifests.add(new PathAndBytes(bytes, path));
      return this;
    }

    public Builder addResourceTable(Path path, byte[] bytes, FeatureSplit featureSplit) {
      resourceTables.put(new PathAndBytes(bytes, path), featureSplit);
      try {
        ResourceTable resourceTable = ResourceTable.parseFrom(bytes);
        System.currentTimeMillis();
      } catch (InvalidProtocolBufferException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    public Builder addDexInput(String classesLocation, byte[] bytes) {
      dexInputs.put(classesLocation, bytes);
      return this;
    }

    public Builder addResFolderInput(Path path, byte[] bytes) {
      PathAndBytes existing = resFolderInputs.get(path);
      if (existing != null) {
        assert Arrays.equals(existing.getBytes(), bytes);
      } else {
        resFolderInputs.put(path, new PathAndBytes(bytes, path));
      }
      return this;
    }

    public Builder addXmlInput(Path path, byte[] bytes) {
      PathAndBytes existing = xmlInputs.get(path);
      if (existing != null) {
        assert Arrays.equals(existing.getBytes(), bytes);
      } else {
        xmlInputs.put(path, new PathAndBytes(bytes, path));
      }
      return this;
    }

    public LegacyResourceShrinker build() {
      assert manifests != null && resourceTables != null;
      return new LegacyResourceShrinker(
          dexInputs,
          resFolderInputs.values(),
          manifests,
          resourceTables,
          xmlInputs.values(),
          proguardMapStrings);
    }

    public void setProguardMapStrings(List<String> proguardMapStrings) {
      this.proguardMapStrings = proguardMapStrings;
    }
  }

  private LegacyResourceShrinker(
      Map<String, byte[]> dexInputs,
      Collection<PathAndBytes> resFolderInputs,
      List<PathAndBytes> manifests,
      Map<PathAndBytes, FeatureSplit> resourceTables,
      Collection<PathAndBytes> xmlInputs,
      List<String> proguardMapStrings) {
    this.dexInputs = dexInputs;
    this.resFolderInputs = resFolderInputs;
    this.manifest = manifests;
    this.resourceTables = resourceTables;
    this.xmlInputs = xmlInputs;
    this.proguardMapStrings = proguardMapStrings;
  }

  public static Builder builder() {
    return new Builder();
  }

  public ShrinkerResult run() throws IOException, ParserConfigurationException, SAXException {
    R8ResourceShrinkerModel model = new R8ResourceShrinkerModel(NoDebugReporter.INSTANCE, true);
    for (PathAndBytes pathAndBytes : resourceTables.keySet()) {
      ResourceTable loadedResourceTable = ResourceTable.parseFrom(pathAndBytes.bytes);
      model.instantiateFromResourceTable(loadedResourceTable, false);
    }
    if (proguardMapStrings != null) {
      new ProguardMappingsRecorder(proguardMapStrings).recordObfuscationMappings(model);
      proguardMapStrings = null;
    }
    for (Entry<String, byte[]> entry : dexInputs.entrySet()) {
      // The analysis needs an origin for the dex files, synthesize an easy recognizable one.
      Path inMemoryR8 = Paths.get("in_memory_r8_" + entry.getKey() + ".dex");
      R8ResourceShrinker.runResourceShrinkerAnalysis(
          entry.getValue(), inMemoryR8, new DexFileAnalysisCallback(inMemoryR8, model));
    }
    for (PathAndBytes pathAndBytes : manifest) {
      ProtoAndroidManifestUsageRecorderKt.recordUsagesFromNode(
          XmlNode.parseFrom(pathAndBytes.bytes), model);
    }
    for (PathAndBytes xmlInput : xmlInputs) {
      if (xmlInput.path.startsWith("res/raw")) {
        ToolsAttributeUsageRecorderKt.processRawXml(getUtfReader(xmlInput.getBytes()), model);
      }
    }

    ImmutableMap<String, PathAndBytes> resFolderMappings =
        new ImmutableMap.Builder<String, PathAndBytes>()
            .putAll(
                xmlInputs.stream()
                    .collect(Collectors.toMap(PathAndBytes::getPathWithoutRes, a -> a)))
            .putAll(
                resFolderInputs.stream()
                    .collect(Collectors.toMap(PathAndBytes::getPathWithoutRes, a -> a)))
            .build();
    for (PathAndBytes pathAndBytes : resourceTables.keySet()) {
      ResourceTable resourceTable = ResourceTable.parseFrom(pathAndBytes.bytes);
      new ProtoResourcesGraphBuilder(
              pathInRes -> resFolderMappings.get(pathInRes).getBytes(), unused -> resourceTable)
          .buildGraph(model);
    }
    ResourceStore resourceStore = model.getResourceStore();
    resourceStore.processToolsAttributes();
    model.keepPossiblyReferencedResources();
    // Transitively mark the reachable resources in the model.
    // Finds unused resources in provided resources collection.
    // Marks all used resources as 'reachable' in original collection.
    List<Resource> unusedResources =
        ResourcesUtil.findUnusedResources(model.getResourceStore().getResources(), x -> {});
    ImmutableSet.Builder<String> resEntriesToKeep = new ImmutableSet.Builder<>();
    for (PathAndBytes xmlInput : Iterables.concat(xmlInputs, resFolderInputs)) {
      if (ResourceShrinkerImplKt.isJarPathReachable(resourceStore, xmlInput.path.toString())) {
        resEntriesToKeep.add(xmlInput.path.toString());
      }
    }
    List<Integer> resourceIdsToRemove = getResourceIdsToRemove(unusedResources);
    Map<FeatureSplit, ResourceTable> shrunkenTables = new HashMap<>();
    for (Entry<PathAndBytes, FeatureSplit> entry : resourceTables.entrySet()) {
      ResourceTable shrunkenResourceTable =
          ResourceTableUtilKt.nullOutEntriesWithIds(
              ResourceTable.parseFrom(entry.getKey().bytes), resourceIdsToRemove);
      shrunkenTables.put(entry.getValue(), shrunkenResourceTable);
    }
    return new ShrinkerResult(resEntriesToKeep.build(), shrunkenTables);
  }

  private static List<Integer> getResourceIdsToRemove(List<Resource> unusedResources) {
      return unusedResources.stream()
          .filter(s -> s.type != ResourceType.ID)
          .map(resource -> resource.value)
          .collect(Collectors.toList());
  }

  // Lifted from com/android/utils/XmlUtils.java which we can't easily update internal dependency
  // for.
  /**
   * Returns a character reader for the given bytes, which must be a UTF encoded file.
   *
   * <p>The reader does not need to be closed by the caller (because the file is read in full in one
   * shot and the resulting array is then wrapped in a byte array input stream, which does not need
   * to be closed.)
   */
  public static Reader getUtfReader(byte[] bytes) throws IOException {
    int length = bytes.length;
    if (length == 0) {
      return new StringReader("");
    }

    switch (bytes[0]) {
      case (byte) 0xEF:
        {
          if (length >= 3 && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            // UTF-8 BOM: EF BB BF: Skip it
            return new InputStreamReader(new ByteArrayInputStream(bytes, 3, length - 3), UTF_8);
          }
          break;
        }
      case (byte) 0xFE:
        {
          if (length >= 2 && bytes[1] == (byte) 0xFF) {
            // UTF-16 Big Endian BOM: FE FF
            return new InputStreamReader(new ByteArrayInputStream(bytes, 2, length - 2), UTF_16BE);
          }
          break;
        }
      case (byte) 0xFF:
        {
          if (length >= 2 && bytes[1] == (byte) 0xFE) {
            if (length >= 4 && bytes[2] == (byte) 0x00 && bytes[3] == (byte) 0x00) {
              // UTF-32 Little Endian BOM: FF FE 00 00
              return new InputStreamReader(
                  new ByteArrayInputStream(bytes, 4, length - 4), "UTF-32LE");
            }

            // UTF-16 Little Endian BOM: FF FE
            return new InputStreamReader(new ByteArrayInputStream(bytes, 2, length - 2), UTF_16LE);
          }
          break;
        }
      case (byte) 0x00:
        {
          if (length >= 4
              && bytes[0] == (byte) 0x00
              && bytes[1] == (byte) 0x00
              && bytes[2] == (byte) 0xFE
              && bytes[3] == (byte) 0xFF) {
            // UTF-32 Big Endian BOM: 00 00 FE FF
            return new InputStreamReader(
                new ByteArrayInputStream(bytes, 4, length - 4), "UTF-32BE");
          }
          break;
        }
    }

    // No byte order mark: Assume UTF-8 (where the BOM is optional).
    return new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8);
  }

  public static class ShrinkerResult {
    private final Set<String> resFolderEntriesToKeep;
    private final Map<FeatureSplit, ResourceTable> resourceTableInProtoFormat;

    public ShrinkerResult(
        Set<String> resFolderEntriesToKeep,
        Map<FeatureSplit, ResourceTable> resourceTableInProtoFormat) {
      this.resFolderEntriesToKeep = resFolderEntriesToKeep;
      this.resourceTableInProtoFormat = resourceTableInProtoFormat;
    }

    public byte[] getResourceTableInProtoFormat(FeatureSplit featureSplit) {
      return resourceTableInProtoFormat.get(featureSplit).toByteArray();
    }

    public Set<String> getResFolderEntriesToKeep() {
      return resFolderEntriesToKeep;
    }
  }

  private static class PathAndBytes {
    private final byte[] bytes;
    private final Path path;

    private PathAndBytes(byte[] bytes, Path path) {
      this.bytes = bytes;
      this.path = path;
    }

    public Path getPath() {
      return path;
    }

    public String getPathWithoutRes() {
      assert path.toString().startsWith("res/");
      return path.toString().substring(4);
    }

    public byte[] getBytes() {
      return bytes;
    }
  }
}
