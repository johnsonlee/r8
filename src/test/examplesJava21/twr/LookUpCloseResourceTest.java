// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package twr;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LookUpCloseResourceTest extends TestBase {

  private static final Set<String> CANNOT_FIX =
      ImmutableSet.of(
          "java.net.URLClassLoader",
          "android.net.wifi.p2p.WifiP2pManager$Channel",
          "android.content.res.AssetFileDescriptor$AutoCloseInputStream");
  private static final Set<String> TOO_OLD_TO_FIX =
      ImmutableSet.of("java.nio.channels.FileLock", "android.database.sqlite.SQLiteClosable");
  private static final boolean DEBUG_PRINT = false;
  private static int MAX_PROCESSED_ANDROID_API_LEVEL = 36;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    InternalOptions options = new InternalOptions();
    options
        .getArtProfileOptions()
        .setAllowReadingEmptyArtProfileProvidersMultipleTimesForTesting(true);
    DexItemFactory factory = options.dexItemFactory();
    DexMethod close =
        factory.createMethod(
            factory.autoCloseableType, factory.createProto(factory.voidType), "close");

    Map<DexType, DexType> toSuper = new IdentityHashMap<>();
    AppView<?> appViewForMax = getAppInfo(options, MAX_PROCESSED_ANDROID_API_LEVEL);
    AppInfoWithClassHierarchy appInfoForMax = appViewForMax.appInfoForDesugaring();
    List<DexType> autoCloseableSubclassesWithOverride = new ArrayList<>();
    for (DexProgramClass clazz : appInfoForMax.classes()) {
      OptionalBool contains =
          appInfoForMax.implementedInterfaces(clazz.getType()).contains(factory.autoCloseableType);
      if (contains.isTrue() && clazz.getType() != factory.autoCloseableType) {
        if (clazz.lookupVirtualMethod(close.withHolder(clazz.getType(), factory)) != null) {
          autoCloseableSubclassesWithOverride.add(clazz.getType());
        } else {
          DexClassAndMethod superResult =
              appInfoForMax.lookupSuperTarget(
                  close.withHolder(clazz.getType(), factory), clazz, appViewForMax, appInfoForMax);
          if (superResult == null) {
            superResult =
                appInfoForMax.lookupMaximallySpecificMethod(
                    clazz, close.withHolder(clazz.getType(), factory));
          }
          // Abstract class/itf may directly rely on AutoCloseable#close().
          assert superResult != null || clazz.isAbstract();
          if (superResult != null) {
            toSuper.put(clazz.getType(), superResult.getHolderType());
          }
        }
      }
    }

    Map<DexType, AndroidApiLevel> classIntroducedBeforeClose = new IdentityHashMap<>();
    for (int i = MAX_PROCESSED_ANDROID_API_LEVEL - 1; i > 0; i--) {
      if (ToolHelper.hasAndroidJar(AndroidApiLevel.getAndroidApiLevel(i))) {
        AppView<?> appView = getAppInfo(options, i);
        AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
        for (DexType type : autoCloseableSubclassesWithOverride) {
          DexClass clazz = appInfo.definitionFor(type);
          if (clazz != null
              && clazz.lookupVirtualMethod(close) == null
              && !classIntroducedBeforeClose.containsKey(type)) {
            classIntroducedBeforeClose.put(type, AndroidApiLevel.getAndroidApiLevel(i));
          }
        }
      }
    }

    List<DexMethod> closeBackports = new ArrayList<>();
    BackportedMethodRewriter.generateListOfBackportedMethodsAndFields(
        appInfoForMax.app(),
        options,
        m -> {
          if (m.getName().toString().equals("close")) {
            closeBackports.add(m);
          }
        },
        f -> {});

    Set<DexType> toRemove = Sets.newIdentityHashSet();
    toSuper.forEach(
        (k, v) -> {
          if (!classIntroducedBeforeClose.containsKey(v)) {
            toRemove.add(k);
          }
        });
    for (DexType dexType : toRemove) {
      toSuper.remove(dexType);
    }

    Assert.assertEquals(10, toSuper.size());
    Assert.assertEquals(11, classIntroducedBeforeClose.size());
    Assert.assertEquals(6, closeBackports.size());

    if (DEBUG_PRINT) {
      print(closeBackports, classIntroducedBeforeClose, toSuper, appViewForMax);
    }
  }

  private void print(
      List<DexMethod> closeBackports,
      Map<DexType, AndroidApiLevel> classIntroducedBeforeClose,
      Map<DexType, DexType> toSuper,
      AppView<?> appView) {
    Map<DexType, List<DexType>> toSub = new IdentityHashMap<>();
    toSuper.forEach(
        (sup, sub) -> {
          toSub.computeIfAbsent(sub, k -> new ArrayList<>()).add(sup);
        });
    System.out.println("Classes introduced in android.jar before their close() method override :");
    classIntroducedBeforeClose.forEach(
        (type, api) -> {
          System.out.print(api + " ");
          System.out.print(appView.definitionFor(type).isFinal() ? "f " : "nf ");
          System.out.print(type + " ");
          if (closeBackports.stream().anyMatch(m -> m.getHolderType() == type)) {
            System.out.print("-- backport");
          }
          if (CANNOT_FIX.contains(type.toString())) {
            System.out.print("-- cannotfix");
          }
          if (TOO_OLD_TO_FIX.contains(type.toString())) {
            System.out.print("-- tooOldToFix");
          }
          System.out.println();
          if (toSub.containsKey(type)) {
            System.out.print("[");
            for (DexType sub : toSub.get(type)) {
              System.out.print(sub + ", ");
            }
            System.out.println("] ");
          }
        });
  }

  private AppView<?> getAppInfo(InternalOptions options, int api) throws IOException {
    AndroidApp app = AndroidApp.builder().addProgramFile(ToolHelper.getAndroidJar(api)).build();
    DirectMappedDexApplication libHolder =
        new ApplicationReader(app, options, Timing.empty()).read().toDirect();
    AppInfo initialAppInfo =
        AppInfo.createInitialAppInfo(libHolder, GlobalSyntheticsStrategy.forNonSynthesizing());
    return AppView.createForD8(initialAppInfo, options.getTypeRewriter(), Timing.empty());
  }
}
