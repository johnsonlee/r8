// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinClassMetadataReader.toKotlinClassMetadata;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.VERSION_1_4_0;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getInvalidKotlinInfo;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;
import static com.android.tools.r8.kotlin.KotlinMetadataWriter.kotlinMetadataToString;
import static com.android.tools.r8.utils.ConsumerUtils.emptyBiConsumer;
import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import kotlin.metadata.jvm.JvmMetadataVersion;

public class KotlinMetadataRewriter {

  private static final class WriteMetadataFieldInfo {
    final boolean writeKind;
    final boolean writeMetadataVersion;
    final boolean writeData1;
    final boolean writeData2;
    final boolean writeExtraString;
    final boolean writePackageName;
    final boolean writeExtraInt;

    private WriteMetadataFieldInfo(
        boolean writeKind,
        boolean writeMetadataVersion,
        boolean writeData1,
        boolean writeData2,
        boolean writeExtraString,
        boolean writePackageName,
        boolean writeExtraInt) {
      this.writeKind = writeKind;
      this.writeMetadataVersion = writeMetadataVersion;
      this.writeData1 = writeData1;
      this.writeData2 = writeData2;
      this.writeExtraString = writeExtraString;
      this.writePackageName = writePackageName;
      this.writeExtraInt = writeExtraInt;
    }

    private static WriteMetadataFieldInfo rewriteAll() {
      return new WriteMetadataFieldInfo(true, true, true, true, true, true, true);
    }
  }

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final Kotlin kotlin;

  public KotlinMetadataRewriter(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.kotlin = factory.kotlin();
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean isNotKotlinMetadata(DexAnnotation annotation, DexType kotlinMetadataType) {
    return annotation.annotation.type != kotlinMetadataType;
  }

  public void runForR8(ExecutorService executorService) throws ExecutionException {
    AppView<? extends AppInfoWithClassHierarchy> appView = this.appView.withClassHierarchy();
    GraphLens graphLens = appView.graphLens();
    GraphLens kotlinMetadataLens = appView.getKotlinMetadataLens();
    DexType rewrittenMetadataType =
        graphLens.lookupClassType(factory.kotlinMetadataType, kotlinMetadataLens);
    // The Kotlin metadata may be present in the input but pruned away in the final tree shaking.
    DexClass kotlinMetadata =
        appView.appInfo().definitionForWithoutExistenceAssert(rewrittenMetadataType);
    WriteMetadataFieldInfo writeMetadataFieldInfo =
        kotlinMetadata == null
            ? WriteMetadataFieldInfo.rewriteAll()
            : new WriteMetadataFieldInfo(
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata().kind),
                kotlinMetadataFieldExists(
                    kotlinMetadata, appView, kotlin.metadata().metadataVersion),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata().data1),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata().data2),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata().extraString),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata().packageName),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata().extraInt));
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          KotlinClassLevelInfo kotlinInfo = clazz.getKotlinInfo();
          if (kotlinInfo == getInvalidKotlinInfo()) {
            // Maintain invalid kotlin info for classes.
            return;
          }
          DexAnnotation oldMeta = clazz.annotations().getFirstMatching(rewrittenMetadataType);
          // TODO(b/181103083): Consider removing if rewrittenMetadataType
          //  != factory.kotlinMetadataType
          if (oldMeta == null
              || kotlinInfo == getNoKotlinInfo()
              || !appView.getKeepInfo().isPinned(clazz, appView.options())) {
            // Remove @Metadata in DexAnnotation when there is no kotlin info and the type is not
            // missing.
            if (oldMeta != null) {
              clazz.setAnnotations(
                  clazz
                      .annotations()
                      .keepIf(anno -> isNotKotlinMetadata(anno, rewrittenMetadataType)));
            }
            return;
          }
          writeKotlinInfoToAnnotation(clazz, kotlinInfo, oldMeta, writeMetadataFieldInfo);
        },
        appView.options().getThreadingModule(),
        executorService);
    if (appView.options().partialSubCompilationConfiguration != null) {
      appView
          .options()
          .partialSubCompilationConfiguration
          .asR8()
          .commitDexingOutputClasses(appView);
      processClassesInD8(
          appView.options().partialSubCompilationConfiguration.asR8().getDexingOutputClasses(),
          executorService);
      appView
          .options()
          .partialSubCompilationConfiguration
          .asR8()
          .uncommitDexingOutputClasses(appView);
    }
    appView.setKotlinMetadataLens(appView.graphLens());
  }

  public void runForD8(ExecutorService executorService) throws ExecutionException {
    if (appView.getNamingLens().isIdentityLens()) {
      return;
    }
    processClassesInD8(appView.appInfo().classes(), executorService);
  }

  private void processClassesInD8(
      Collection<DexProgramClass> classes, ExecutorService executorService)
      throws ExecutionException {
    BooleanBox reportedUnknownMetadataVersion = new BooleanBox();
    WriteMetadataFieldInfo writeMetadataFieldInfo = WriteMetadataFieldInfo.rewriteAll();
    ThreadUtils.processItems(
        classes,
        clazz -> processClassInD8(clazz, reportedUnknownMetadataVersion, writeMetadataFieldInfo),
        appView.options().getThreadingModule(),
        executorService);
  }

  private void processClassInD8(
      DexProgramClass clazz,
      BooleanBox reportedUnknownMetadataVersion,
      WriteMetadataFieldInfo writeMetadataFieldInfo) {
    DexAnnotation metadata = clazz.annotations().getFirstMatching(factory.kotlinMetadataType);
    if (metadata == null) {
      return;
    }
    // In D8 of R8 partial the kotlin.Metadata annotations have already been read during trace
    // references.
    KotlinClassLevelInfo kotlinInfo;
    if (appView.options().partialSubCompilationConfiguration != null) {
      assert verifyKotlinInfoIsSet(clazz, metadata);
      kotlinInfo = clazz.getKotlinInfo();
    } else {
      kotlinInfo =
          KotlinClassMetadataReader.getKotlinInfoFromAnnotation(
              appView, clazz, metadata, emptyConsumer(), reportedUnknownMetadataVersion::getAndSet);
    }
    if (kotlinInfo == getNoKotlinInfo()) {
      return;
    }
    assert recordMissingClassesInR8Partial(clazz, kotlinInfo);
    writeKotlinInfoToAnnotation(clazz, kotlinInfo, metadata, writeMetadataFieldInfo);
  }

  private boolean verifyKotlinInfoIsSet(DexProgramClass clazz, DexAnnotation metadata) {
    assert appView.options().partialSubCompilationConfiguration != null;
    KotlinClassLevelInfo classInfo =
        KotlinClassMetadataReader.getKotlinInfoFromAnnotation(
            appView, clazz, metadata, emptyConsumer(), () -> false, emptyBiConsumer());
    assert classInfo.isNoKotlinInformation() || !clazz.getKotlinInfo().isNoKotlinInformation();
    return true;
  }

  private boolean recordMissingClassesInR8Partial(
      DexProgramClass clazz, KotlinClassLevelInfo kotlinInfo) {
    if (appView.options().partialSubCompilationConfiguration != null) {
      assert appView.options().partialSubCompilationConfiguration.isR8();
      KotlinMetadataUseRegistry registry =
          type -> {
            DexClass result = appView.appInfo().definitionForWithoutExistenceAssert(type);
            if (result == null) {
              appView
                  .options()
                  .partialSubCompilationConfiguration
                  .asR8()
                  .d8MissingClasses
                  .add(type);
            }
          };
      kotlinInfo.trace(registry);
      clazz.forEachProgramMember(member -> member.getDefinition().getKotlinInfo().trace(registry));
    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private void writeKotlinInfoToAnnotation(
      DexClass clazz,
      KotlinClassLevelInfo kotlinInfo,
      DexAnnotation oldMeta,
      WriteMetadataFieldInfo writeMetadataFieldInfo) {
    try {
      Pair<kotlin.Metadata, Boolean> kotlinMetadata = kotlinInfo.rewrite(clazz, appView);
      // TODO(b/185756596): Remove when special handling is no longer needed.
      if (!kotlinMetadata.getSecond() && appView.options().testing.keepMetadataInR8IfNotRewritten) {
        // No rewrite occurred and the data is the same as before.
        assert appView.checkForTesting(
            () ->
                verifyRewrittenMetadataIsEquivalent(
                    clazz.annotations().getFirstMatching(factory.kotlinMetadataType),
                    createKotlinMetadataAnnotation(
                        kotlinMetadata.getFirst(),
                        kotlinInfo.getPackageName(),
                        computeMetadataVersion(kotlinInfo.getMetadataVersion()),
                        writeMetadataFieldInfo)));
        return;
      }
      DexAnnotation newMeta =
          createKotlinMetadataAnnotation(
              kotlinMetadata.getFirst(),
              kotlinInfo.getPackageName(),
              computeMetadataVersion(kotlinInfo.getMetadataVersion()),
              writeMetadataFieldInfo);
      clazz.setAnnotations(clazz.annotations().rewrite(anno -> anno == oldMeta ? newMeta : anno));
    } catch (Throwable t) {
      assert appView.checkForTesting(
          () -> {
            throw appView
                .options()
                .reporter
                .fatalError(KotlinMetadataDiagnostic.unexpectedErrorWhenRewriting(clazz.type, t));
          });
      appView
          .options()
          .reporter
          .warning(KotlinMetadataDiagnostic.unexpectedErrorWhenRewriting(clazz.type, t));
    }
  }

  @SuppressWarnings("EmptyCatch")
  private boolean verifyRewrittenMetadataIsEquivalent(
      DexAnnotation original, DexAnnotation rewritten) {
    try {
      String originalMetadata =
          kotlinMetadataToString("", toKotlinClassMetadata(kotlin, original.annotation));
      String rewrittenMetadata =
          kotlinMetadataToString("", toKotlinClassMetadata(kotlin, rewritten.annotation));
      assert originalMetadata.equals(rewrittenMetadata) : "The metadata should be equivalent";
    } catch (KotlinMetadataException ignored) {

    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean kotlinMetadataFieldExists(
      DexClass kotlinMetadata, AppView<?> appView, DexString fieldName) {
    if (!appView.appInfo().hasLiveness()) {
      return true;
    }
    if (kotlinMetadata == null || kotlinMetadata.isNotProgramClass()) {
      return true;
    }
    return kotlinMetadata
        .methods(method -> method.getReference().name == fieldName)
        .iterator()
        .hasNext();
  }

  private DexAnnotation createKotlinMetadataAnnotation(
      kotlin.Metadata metadata,
      String packageName,
      JvmMetadataVersion metadataVersion,
      WriteMetadataFieldInfo writeMetadataFieldInfo) {
    List<DexAnnotationElement> elements = new ArrayList<>();
    if (writeMetadataFieldInfo.writeMetadataVersion) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata().metadataVersion,
              createIntArray(KotlinJvmMetadataVersionUtils.toIntArray(metadataVersion))));
    }
    if (writeMetadataFieldInfo.writeKind) {
      elements.add(
          new DexAnnotationElement(kotlin.metadata().kind, DexValueInt.create(metadata.k())));
    }
    if (writeMetadataFieldInfo.writeData1) {
      elements.add(
          new DexAnnotationElement(kotlin.metadata().data1, createStringArray(metadata.d1())));
    }
    if (writeMetadataFieldInfo.writeData2) {
      elements.add(
          new DexAnnotationElement(kotlin.metadata().data2, createStringArray(metadata.d2())));
    }
    if (writeMetadataFieldInfo.writePackageName && packageName != null && !packageName.isEmpty()) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata().packageName,
              new DexValueString(factory.createString(packageName))));
    }
    if (writeMetadataFieldInfo.writeExtraString && !metadata.xs().isEmpty()) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata().extraString,
              new DexValueString(factory.createString(metadata.xs()))));
    }
    if (writeMetadataFieldInfo.writeExtraInt && metadata.xi() != 0) {
      elements.add(
          new DexAnnotationElement(kotlin.metadata().extraInt, DexValueInt.create(metadata.xi())));
    }
    DexEncodedAnnotation encodedAnnotation =
        new DexEncodedAnnotation(
            factory.kotlinMetadataType, elements.toArray(DexAnnotationElement.EMPTY_ARRAY));
    return new DexAnnotation(DexAnnotation.VISIBILITY_RUNTIME, encodedAnnotation);
  }

  private DexValueArray createIntArray(int[] data) {
    DexValue[] values = new DexValue[data.length];
    for (int i = 0; i < data.length; i++) {
      values[i] = DexValueInt.create(data[i]);
    }
    return new DexValueArray(values);
  }

  private DexValueArray createStringArray(String[] data) {
    DexValue[] values = new DexValue[data.length];
    for (int i = 0; i < data.length; i++) {
      values[i] = new DexValueString(factory.createString(data[i]));
    }
    return new DexValueArray(values);
  }

  // Due to a bug with nested classes and the lookup of RequirementVersion, we bump all metadata
  // versions to 1.4 if compiled with kotlin 1.3 (1.1.16). For more information, see b/161885097.
  private JvmMetadataVersion computeMetadataVersion(JvmMetadataVersion other) {
    if (VERSION_1_4_0.compareTo(other) >= 0) {
      return VERSION_1_4_0;
    }
    return other;
  }
}
