// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Computes the set of virtual methods for which we can use a monomorphic method state as well as
 * the mapping from virtual methods to their representative root methods.
 */
public class VirtualRootMethodsAnalysisBase extends DepthFirstTopDownClassHierarchyTraversal {

  protected static class VirtualRootMethod {

    private final VirtualRootMethod parent;
    private final ProgramMethod method;

    private Set<VirtualRootMethod> overrides = Collections.emptySet();
    private List<VirtualRootMethod> siblings = Collections.emptyList();
    private boolean mayDispatchOutsideProgram = false;

    VirtualRootMethod(ProgramMethod root) {
      this(root, null);
    }

    VirtualRootMethod(ProgramMethod method, VirtualRootMethod parent) {
      assert method != null;
      this.parent = parent;
      this.method = method;
    }

    void addOverride(VirtualRootMethod override) {
      assert !override.getMethod().isStructurallyEqualTo(method);
      assert override.getMethod().getReference().match(method.getReference());
      if (overrides.isEmpty()) {
        overrides = Sets.newIdentityHashSet();
      }
      overrides.add(override);
      if (hasParent()) {
        getParent().addOverride(override);
      }
    }

    void addSibling(VirtualRootMethod sibling) {
      if (siblings.isEmpty()) {
        siblings = new ArrayList<>(1);
      }
      siblings.add(sibling);
    }

    void setMayDispatchOutsideProgram() {
      mayDispatchOutsideProgram = true;
    }

    boolean hasParent() {
      return parent != null;
    }

    boolean hasSiblings() {
      return !siblings.isEmpty();
    }

    VirtualRootMethod getParent() {
      return parent;
    }

    VirtualRootMethod getRoot() {
      return hasParent() ? getParent().getRoot() : this;
    }

    ProgramMethod getMethod() {
      return method;
    }

    VirtualRootMethod getSingleDispatchTarget() {
      assert !hasParent();
      if (isMayDispatchOutsideProgramSet()) {
        return null;
      }
      VirtualRootMethod singleDispatchTarget = isAbstract() ? null : this;
      for (VirtualRootMethod override : overrides) {
        if (!override.isAbstract()) {
          if (singleDispatchTarget != null) {
            // Not a single non-abstract method.
            return null;
          }
          singleDispatchTarget = override;
        }
      }
      assert singleDispatchTarget == null || !singleDispatchTarget.isAbstract();
      return singleDispatchTarget;
    }

    void forEach(Consumer<VirtualRootMethod> consumer) {
      consumer.accept(this);
      overrides.forEach(consumer);
    }

    boolean hasOverrides() {
      return !overrides.isEmpty();
    }

    boolean isAbstract() {
      return method.getAccessFlags().isAbstract();
    }

    boolean isMayDispatchOutsideProgramSet() {
      return mayDispatchOutsideProgram;
    }

    boolean isOverriddenBy(VirtualRootMethod other) {
      return overrides.contains(other);
    }
  }

  private final Map<DexProgramClass, DexMethodSignatureMap<VirtualRootMethod>>
      virtualRootMethodsPerClass = new IdentityHashMap<>();

  protected final ProgramMethodSet monomorphicVirtualRootMethods = ProgramMethodSet.create();
  protected final ProgramMethodSet monomorphicVirtualNonRootMethods = ProgramMethodSet.create();

  protected final Map<DexMethod, DexMethod> virtualRootMethods = new IdentityHashMap<>();

  protected VirtualRootMethodsAnalysisBase(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    super(appView, immediateSubtypingInfo);
  }

  @Override
  public void visit(DexProgramClass clazz) {
    DexMethodSignatureMap<VirtualRootMethod> state = computeVirtualRootMethodsState(clazz);
    virtualRootMethodsPerClass.put(clazz, state);
  }

  private DexMethodSignatureMap<VirtualRootMethod> computeVirtualRootMethodsState(
      DexProgramClass clazz) {
    DexMethodSignatureMap<VirtualRootMethod> virtualRootMethodsForClass =
        DexMethodSignatureMap.create();
    immediateSubtypingInfo.forEachImmediateProgramSuperClass(
        clazz,
        superclass -> {
          DexMethodSignatureMap<VirtualRootMethod> virtualRootMethodsForSuperclass =
              virtualRootMethodsPerClass.get(superclass);
          virtualRootMethodsForSuperclass.forEach(
              (signature, info) -> {
                virtualRootMethodsForClass.compute(
                    signature,
                    (ignore, existing) -> {
                      if (existing == null || existing == info) {
                        return info;
                      } else {
                        // We iterate the immediate supertypes in-order using
                        // forEachImmediateProgramSuperClass. Therefore, the current method is
                        // guaranteed to be an interface method when existing != null.
                        assert info.getMethod().getHolder().isInterface();
                        // Add the existing as a sibling of the current method (or vice versa).
                        // Despite the fact that these two methods come from two different
                        // extends/implements edges, the two methods may already be related by
                        // overriding, as in the following example.
                        //
                        //   interface I { void m(); }
                        //   interface J extends I { @Override default void m() { ... } }
                        //   abstract class A implements I {}
                        //   class B extends A implements J {}
                        //
                        // When processing the extends edge B->A, we will pull down the definition
                        // of I.m(). Next, when processing the implements edge B->J we will pull
                        // down the definition of J.m(). Since J.m() is an override of I.m() we
                        // should avoid marking the two methods as siblings.
                        if (info.isOverriddenBy(existing)) {
                          return existing;
                        }
                        if (existing.isOverriddenBy(info)) {
                          return info;
                        }
                        if (!existing.isAbstract()) {
                          existing.addSibling(info);
                          info.addOverride(existing);
                          return existing;
                        }
                        if (existing.getMethod().getHolder().isInterface() && !info.isAbstract()) {
                          info.addSibling(existing);
                          existing.addOverride(info);
                          return info;
                        }
                        return existing;
                      }
                    });
                if (!clazz.isInterface() && superclass.isInterface()) {
                  DexClassAndMethod resolvedMethod =
                      appView.appInfo().resolveMethodOnClass(clazz, signature).getResolutionPair();
                  if (resolvedMethod != null
                      && !resolvedMethod.isProgramMethod()
                      && !resolvedMethod.getAccessFlags().isAbstract()) {
                    info.setMayDispatchOutsideProgram();
                  }
                }
              });
        });
    clazz.forEachProgramVirtualMethod(
        method ->
            virtualRootMethodsForClass.compute(
                method,
                (ignore, parent) -> {
                  if (parent == null) {
                    return new VirtualRootMethod(method);
                  } else {
                    VirtualRootMethod override = new VirtualRootMethod(method, parent);
                    parent.addOverride(override);
                    return override;
                  }
                }));
    return virtualRootMethodsForClass;
  }

  @Override
  public void prune(DexProgramClass clazz) {
    // Record the overrides for each virtual method that is rooted at this class.
    DexMethodSignatureMap<VirtualRootMethod> virtualRootMethodsForClass =
        virtualRootMethodsPerClass.remove(clazz);
    clazz.forEachProgramVirtualMethod(
        rootCandidate -> {
          VirtualRootMethod virtualRootMethod =
              virtualRootMethodsForClass.remove(rootCandidate.getMethodSignature());
          acceptVirtualMethod(rootCandidate, virtualRootMethod);
          if (virtualRootMethod.hasParent()
              || !rootCandidate.isStructurallyEqualTo(virtualRootMethod.getMethod())) {
            return;
          }
          if (!virtualRootMethod.hasOverrides()
              && !virtualRootMethod.hasSiblings()
              && !virtualRootMethod.isMayDispatchOutsideProgramSet()) {
            monomorphicVirtualRootMethods.add(rootCandidate);
            return;
          }
          if (!rootCandidate.getHolder().isInterface()) {
            VirtualRootMethod singleDispatchTarget = virtualRootMethod.getSingleDispatchTarget();
            if (singleDispatchTarget != null) {
              virtualRootMethod.forEach(
                  method -> setRootMethod(method, virtualRootMethod, singleDispatchTarget));
              monomorphicVirtualNonRootMethods.add(singleDispatchTarget.getMethod());
              return;
            }
          }
          virtualRootMethod.forEach(
              method -> setRootMethod(method, virtualRootMethod, virtualRootMethod));
        });
  }

  private void setRootMethod(
      VirtualRootMethod method, VirtualRootMethod currentRoot, VirtualRootMethod root) {
    // Since the same method can have multiple roots due to interface methods, we only allow
    // controlling the virtual root of methods that are rooted at the current root. Otherwise, we
    // would be setting the virtual root of the same method multiple times, which could lead to
    // non-determinism in the result (i.e., the `virtualRootMethods` map).
    if (method.getRoot() == currentRoot) {
      DexMethod rootReference = root.getMethod().getReference();
      DexMethod previous = virtualRootMethods.put(method.getMethod().getReference(), rootReference);
      assert previous == null || previous.isIdenticalTo(rootReference);
    }
  }

  protected void acceptVirtualMethod(ProgramMethod method, VirtualRootMethod virtualRootMethod) {
    // Intentionally empty.
  }
}
