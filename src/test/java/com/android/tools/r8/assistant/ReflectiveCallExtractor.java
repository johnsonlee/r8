// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ReflectiveCallExtractor {

  public static Map<DexType, Collection<DexMethod>> extractReflectiveCalls(
      Path jar, DexItemFactory factory) throws IOException {
    AndroidApp app =
        AndroidApp.builder()
            .addProgramResourceProvider(ArchiveProgramResourceProvider.fromArchive(jar))
            .addLibraryFile(ToolHelper.getAndroidJar(AndroidApiLevel.V))
            .build();
    InternalOptions options = new InternalOptions(factory, new Reporter());
    ApplicationReader applicationReader = new ApplicationReader(app, options, Timing.empty());
    LazyLoadedDexApplication dexApplication =
        applicationReader.read(ThreadUtils.getExecutorService(options));
    AppInfo appInfo =
        AppInfo.createInitialAppInfo(dexApplication, GlobalSyntheticsStrategy.forNonSynthesizing());
    Map<DexType, Collection<DexMethod>> methods = new IdentityHashMap<>();
    for (DexProgramClass programClass : appInfo.classes()) {
      for (DexEncodedMethod method : programClass.methods()) {
        if (method.hasCode()) {
          for (CfInstruction instruction : method.getCode().asCfCode().getInstructions()) {
            if (instruction.isInvoke()) {
              CfInvoke cfInvoke = instruction.asInvoke();
              DexMethod theMethod = cfInvoke.getMethod();
              DexType holderType = theMethod.getHolderType();
              DexClass def = appInfo.definitionFor(holderType);
              if (def != null && def.isLibraryClass()) {
                if (isReflective(theMethod, factory)) {
                  methods.computeIfAbsent(holderType, t -> new TreeSet<>()).add(theMethod);
                }
              }
            }
          }
        }
      }
    }
    return methods;
  }
  private static boolean isReflective(DexMethod method, DexItemFactory factory) {
    if (factory.atomicFieldUpdaterMethods.isFieldUpdater(method)) {
      return true;
    }
    if (factory.serviceLoaderMethods.isLoadMethod(method)) {
      return true;
    }
    if (factory.proxyMethods.newProxyInstance.isIdenticalTo(method)) {
      return true;
    }
    DexType type = method.getHolderType();
    String name = method.getName().toString();
    if (type.isIdenticalTo(factory.classType)) {
      if (name.equals("getResource")
          || name.equals("getResourceAsStream")
          || name.equals("getProtectionDomain")
          || name.equals("getClassLoader")
          || name.equals("hashCode")
          || name.equals("equals")
          || name.equals("toString")) {
        return false;
      }
      return true;
    }
    if (type.isIdenticalTo(factory.unsafeType)) {
      // We assume all offset based methods are called using the right method below to compute the
      // offset in the object, we do not support cases where programmers would compute the
      // offset on their own with their own knowledge of how the vm writes objects in memory.
      if (name.equals("fieldOffset")
          || name.equals("staticFieldBase") // This can be called on a field or a class.
          || name.equals("staticFieldOffset")
          || name.equals("objectFieldOffset")
          || name.equals("shouldBeInitialized")
          || name.equals("ensureClassInitialized")
          || name.equals("allocateInstance")) {
        return true;
      }
    }
    return false;
  }

  public static String printMethods(Map<DexType, Collection<DexMethod>> methods) {
    StringBuilder sb = new StringBuilder();
    List<DexType> types = new ArrayList<>(methods.keySet());
    types.sort(Comparator.naturalOrder());
    for (DexType type : types) {
      Collection<DexMethod> typeMethods = methods.get(type);
      if (!typeMethods.isEmpty()) {
        sb.append("+++ ").append(type).append(" +++").append("\n");
        for (DexMethod dexMethod : typeMethods) {
          sb.append(dexMethod).append("\n");
        }
      }
    }
    return sb.toString();
  }
}
