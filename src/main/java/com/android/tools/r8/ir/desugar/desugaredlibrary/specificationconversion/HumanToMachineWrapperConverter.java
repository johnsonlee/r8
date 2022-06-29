// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.WrapperDescriptor;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class HumanToMachineWrapperConverter {

  private final MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();
  private final AppInfoWithClassHierarchy appInfo;
  private final Set<DexType> missingClasses = Sets.newIdentityHashSet();

  public HumanToMachineWrapperConverter(AppInfoWithClassHierarchy appInfo) {
    this.appInfo = appInfo;
  }

  public void convertWrappers(
      HumanRewritingFlags rewritingFlags,
      MachineRewritingFlags.Builder builder,
      BiConsumer<String, Set<? extends DexReference>> warnConsumer) {
    Map<DexType, WrapperDescriptorBuilder> descriptors = initializeDescriptors(rewritingFlags);
    fillDescriptors(rewritingFlags, descriptors);
    // The descriptors have to be ordered so that when processing a type, subtypes have been
    // processed before.
    LinkedHashMap<DexType, WrapperDescriptorBuilder> orderedDescriptors =
        orderDescriptors(descriptors);
    finalizeWrapperDescriptors(orderedDescriptors, builder);
    warnConsumer.accept("The following types to wrap are missing: ", missingClasses);
  }

  private static class WrapperDescriptorBuilder {
    private final List<DexMethod> methods = new ArrayList<>();
    private final List<DexType> subwrappers = new ArrayList<>();

    public WrapperDescriptorBuilder() {}

    public List<DexMethod> getMethods() {
      return methods;
    }

    public List<DexType> getSubwrappers() {
      return subwrappers;
    }

    public void addSubwrapper(DexType type) {
      subwrappers.add(type);
    }

    public WrapperDescriptor toWrapperDescriptor() {
      methods.sort(DexMethod::compareTo);
      subwrappers.sort(DexType::compareTo);
      return new WrapperDescriptor(
          ImmutableList.copyOf(methods), ImmutableList.copyOf(subwrappers));
    }
  }

  private Map<DexType, WrapperDescriptorBuilder> initializeDescriptors(
      HumanRewritingFlags rewritingFlags) {
    Map<DexType, WrapperDescriptorBuilder> descriptors = new IdentityHashMap<>();
    for (DexType wrapperType : rewritingFlags.getWrapperConversions().keySet()) {
      descriptors.put(wrapperType, new WrapperDescriptorBuilder());
    }
    return descriptors;
  }

  private void fillDescriptors(
      HumanRewritingFlags rewritingFlags, Map<DexType, WrapperDescriptorBuilder> descriptors) {
    rewritingFlags
        .getWrapperConversions()
        .forEach(
            (wrapperType, excludedMethods) -> {
              DexClass wrapperClass = appInfo.definitionFor(wrapperType);
              if (wrapperClass == null) {
                missingClasses.add(wrapperType);
                descriptors.remove(wrapperType);
                return;
              }
              WrapperDescriptorBuilder descriptor = descriptors.get(wrapperType);
              fillDescriptors(wrapperClass, excludedMethods, descriptor, descriptors);
            });
  }

  private LinkedHashMap<DexType, WrapperDescriptorBuilder> orderDescriptors(
      Map<DexType, WrapperDescriptorBuilder> descriptors) {
    LinkedHashMap<DexType, WrapperDescriptorBuilder> orderedDescriptors = new LinkedHashMap<>();
    List<DexType> preOrdered = new ArrayList<>(descriptors.keySet());
    preOrdered.sort(DexType::compareTo);
    LinkedList<DexType> workList = new LinkedList<>(preOrdered);
    while (!workList.isEmpty()) {
      DexType dexType = workList.removeFirst();
      WrapperDescriptorBuilder descriptor = descriptors.get(dexType);
      List<DexType> subwrappers = descriptor.getSubwrappers();
      if (Iterables.all(subwrappers, orderedDescriptors::containsKey)) {
        orderedDescriptors.put(dexType, descriptor);
      } else {
        workList.addLast(dexType);
      }
    }
    return orderedDescriptors;
  }

  private void finalizeWrapperDescriptors(
      LinkedHashMap<DexType, WrapperDescriptorBuilder> descriptors,
      MachineRewritingFlags.Builder builder) {
    descriptors.forEach(
        (type, descriptor) -> {
          LinkedList<DexType> workList = new LinkedList<>(descriptor.getSubwrappers());
          while (!workList.isEmpty()) {
            DexType dexType = workList.removeFirst();
            List<DexType> subwrappers = descriptors.get(dexType).getSubwrappers();
            descriptor.getSubwrappers().removeAll(subwrappers);
            workList.addAll(subwrappers);
          }
          builder.addWrapper(type, descriptor.toWrapperDescriptor());
        });
  }

  private void fillDescriptors(
      DexClass wrapperClass,
      Set<DexMethod> excludedMethods,
      WrapperDescriptorBuilder descriptor,
      Map<DexType, WrapperDescriptorBuilder> descriptors) {
    HashSet<Wrapper<DexMethod>> wrappers = new HashSet<>();
    for (DexMethod excludedMethod : excludedMethods) {
      wrappers.add(equivalence.wrap(excludedMethod));
    }
    LinkedList<DexClass> workList = new LinkedList<>();
    List<DexMethod> implementedMethods = descriptor.getMethods();
    workList.add(wrapperClass);
    while (!workList.isEmpty()) {
      DexClass dexClass = workList.removeFirst();
      if (dexClass != wrapperClass && descriptors.containsKey(dexClass.type)) {
        descriptors.get(dexClass.type).addSubwrapper(wrapperClass.type);
      }
      if (!wrapperClass.isEnum()) {
        for (DexEncodedMethod virtualMethod : dexClass.virtualMethods()) {
          if (!virtualMethod.isPrivateMethod()
              // Don't include hashCode and equals overrides, as hashCode and equals are added to
              // all wrappers regardless.
              && (!appInfo.dexItemFactory().objectMembers.hashCode.match(virtualMethod))
              && (!appInfo.dexItemFactory().objectMembers.equals.match(virtualMethod))) {
            assert virtualMethod.isProtectedMethod() || virtualMethod.isPublicMethod();
            boolean alreadyAdded =
                wrappers.contains(equivalence.wrap(virtualMethod.getReference()));
            // This looks quadratic but given the size of the collections met in practice for
            // desugared libraries (Max ~15) it does not matter.
            if (!alreadyAdded) {
              for (DexMethod alreadyImplementedMethod : implementedMethods) {
                if (alreadyImplementedMethod.match(virtualMethod.getReference())) {
                  alreadyAdded = true;
                  break;
                }
              }
            }
            if (!alreadyAdded) {
              assert !virtualMethod.isFinal()
                  : "Cannot wrap final method " + virtualMethod + " while wrapping " + wrapperClass;
              implementedMethods.add(virtualMethod.getReference());
            }
          }
        }
      }
      for (DexType itf : dexClass.interfaces.values) {
        DexClass itfClass = appInfo.definitionFor(itf);
        if (itfClass != null) {
          workList.add(itfClass);
        }
      }
      if (dexClass.superType != appInfo.dexItemFactory().objectType) {
        DexClass superClass = appInfo.definitionFor(dexClass.superType);
        assert superClass != null
            : "Missing supertype " + dexClass.superType + " while wrapping " + wrapperClass;
        workList.add(superClass);
      }
    }
  }
}
