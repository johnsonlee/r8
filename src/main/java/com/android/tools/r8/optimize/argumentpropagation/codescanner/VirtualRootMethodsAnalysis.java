// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;


import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorCodeScanner;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Computes the set of virtual methods for which we can use a monomorphic method state as well as
 * the mapping from virtual methods to their representative root methods.
 *
 * <p>The analysis can be used to easily mark effectively final classes and methods as final, and
 * therefore does this as a side effect.
 */
public class VirtualRootMethodsAnalysis extends VirtualRootMethodsAnalysisBase {

  public VirtualRootMethodsAnalysis(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    super(appView, immediateSubtypingInfo);
  }

  public void initializeVirtualRootMethods(
      Collection<DexProgramClass> stronglyConnectedComponent,
      ArgumentPropagatorCodeScanner codeScanner) {
    // Find all the virtual root methods in the strongly connected component.
    run(stronglyConnectedComponent);

    // Commit the result to the code scanner.
    List<DexMethod> monomorphicVirtualMethodReferences =
        new ArrayList<>(
            monomorphicVirtualRootMethods.size() + monomorphicVirtualNonRootMethods.size());
    for (ProgramMethod method :
        Iterables.concat(monomorphicVirtualRootMethods, monomorphicVirtualNonRootMethods)) {
      monomorphicVirtualMethodReferences.add(method.getReference());
    }
    codeScanner.addMonomorphicVirtualMethods(monomorphicVirtualMethodReferences);
    codeScanner.addVirtualRootMethods(virtualRootMethods);
  }

  @Override
  protected void acceptVirtualMethod(ProgramMethod method, VirtualRootMethod virtualRootMethod) {
    promoteToFinalIfPossible(method, virtualRootMethod);
  }

  @Override
  public void forEachSubClass(DexProgramClass clazz, Consumer<DexProgramClass> consumer) {
    List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(clazz);
    if (subclasses.isEmpty()) {
      promoteToFinalIfPossible(clazz);
    } else {
      subclasses.forEach(consumer);
    }
  }

  private void promoteToFinalIfPossible(DexProgramClass clazz) {
    if (!appView.testing().disableMarkingClassesFinal
        && !clazz.isAbstract()
        && !clazz.isInterface()
        && appView.getKeepInfo(clazz).isOptimizationAllowed(appView.options())) {
      clazz.getAccessFlags().promoteToFinal();
    }
  }

  private void promoteToFinalIfPossible(ProgramMethod method, VirtualRootMethod virtualRootMethod) {
    if (!appView.testing().disableMarkingMethodsFinal
        && !method.getHolder().isInterface()
        && !method.getAccessFlags().isAbstract()
        && !virtualRootMethod.hasOverrides()
        && appView.getKeepInfo(method).isOptimizationAllowed(appView.options())) {
      method.getAccessFlags().promoteToFinal();
    }
  }
}
