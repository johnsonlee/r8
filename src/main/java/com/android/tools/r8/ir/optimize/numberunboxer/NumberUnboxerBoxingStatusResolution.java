// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import static com.android.tools.r8.ir.optimize.numberunboxer.NumberUnboxerBoxingStatusResolution.MethodBoxingStatusResult.BoxingStatusResult.NO_UNBOX;
import static com.android.tools.r8.ir.optimize.numberunboxer.NumberUnboxerBoxingStatusResolution.MethodBoxingStatusResult.BoxingStatusResult.TO_PROCESS;
import static com.android.tools.r8.ir.optimize.numberunboxer.NumberUnboxerBoxingStatusResolution.MethodBoxingStatusResult.BoxingStatusResult.UNBOX;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.optimize.numberunboxer.NumberUnboxerBoxingStatusResolution.MethodBoxingStatusResult.BoxingStatusResult;
import com.android.tools.r8.ir.optimize.numberunboxer.TransitiveDependency.MethodArg;
import com.android.tools.r8.ir.optimize.numberunboxer.TransitiveDependency.MethodRet;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.WorkList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class NumberUnboxerBoxingStatusResolution {

  // TODO(b/307872552): Add threshold to NumberUnboxing options.
  private static final int UNBOX_DELTA_THRESHOLD = 0;
  private final Map<DexMethod, MethodBoxingStatus> methodBoxingStatus;
  private final Map<DexMethod, MethodBoxingStatusResult> boxingStatusResultMap =
      new IdentityHashMap<>();

  public NumberUnboxerBoxingStatusResolution(
      Map<DexMethod, MethodBoxingStatus> methodBoxingStatus) {
    this.methodBoxingStatus = methodBoxingStatus;
  }

  static class MethodBoxingStatusResult {

    public static MethodBoxingStatusResult create(DexMethod method) {
      return new MethodBoxingStatusResult(method, TO_PROCESS);
    }

    MethodBoxingStatusResult(DexMethod method, BoxingStatusResult init) {
      this.ret = init;
      this.args = new BoxingStatusResult[method.getArity()];
      Arrays.fill(args, init);
    }

    enum BoxingStatusResult {
      TO_PROCESS,
      UNBOX,
      NO_UNBOX
    }

    BoxingStatusResult ret;
    BoxingStatusResult[] args;

    public void setRet(BoxingStatusResult ret) {
      this.ret = ret;
    }

    public BoxingStatusResult getRet() {
      return ret;
    }

    public void setArg(BoxingStatusResult arg, int i) {
      this.args[i] = arg;
    }

    public BoxingStatusResult getArg(int i) {
      return args[i];
    }

    public BoxingStatusResult[] getArgs() {
      return args;
    }

    public boolean isNoneUnboxable() {
      return ret == NO_UNBOX && ArrayUtils.all(args, NO_UNBOX);
    }

    public boolean shouldUnboxArg(int i) {
      assert args[i] != TO_PROCESS;
      return args[i] == UNBOX;
    }

    public boolean shouldUnboxRet() {
      assert ret != TO_PROCESS;
      return ret == UNBOX;
    }
  }

  private MethodBoxingStatusResult getMethodBoxingStatusResult(DexMethod method) {
    assert methodBoxingStatus.containsKey(method);
    return boxingStatusResultMap.computeIfAbsent(method, MethodBoxingStatusResult::create);
  }

  BoxingStatusResult get(TransitiveDependency transitiveDependency) {
    assert transitiveDependency.isMethodDependency();
    DexMethod method = transitiveDependency.asMethodDependency().getMethod();
    if (!methodBoxingStatus.containsKey(method)) {
      return NO_UNBOX;
    }
    MethodBoxingStatusResult methodBoxingStatusResult = getMethodBoxingStatusResult(method);
    if (transitiveDependency.isMethodRet()) {
      return methodBoxingStatusResult.getRet();
    }
    assert transitiveDependency.isMethodArg();
    return methodBoxingStatusResult.getArg(transitiveDependency.asMethodArg().getParameterIndex());
  }

  void register(TransitiveDependency transitiveDependency, BoxingStatusResult boxingStatusResult) {
    assert transitiveDependency.isMethodDependency();
    DexMethod method = transitiveDependency.asMethodDependency().getMethod();
    if (boxingStatusResult == NO_UNBOX) {
      if (!methodBoxingStatus.containsKey(method)) {
        // Nothing to unbox, nothing to register.
        return;
      }
    }
    MethodBoxingStatusResult methodBoxingStatusResult = getMethodBoxingStatusResult(method);
    if (transitiveDependency.isMethodRet()) {
      methodBoxingStatusResult.setRet(boxingStatusResult);
      return;
    }
    assert transitiveDependency.isMethodArg();
    methodBoxingStatusResult.setArg(
        boxingStatusResult, transitiveDependency.asMethodArg().getParameterIndex());
  }

  public Map<DexMethod, MethodBoxingStatusResult> resolve() {
    assert allProcessedAndUnboxable(methodBoxingStatus);
    List<DexMethod> methods = ListUtils.sort(methodBoxingStatus.keySet(), DexMethod::compareTo);
    for (DexMethod method : methods) {
      MethodBoxingStatus status = methodBoxingStatus.get(method);
      assert !status.isNoneUnboxable();
      MethodBoxingStatusResult methodBoxingStatusResult = getMethodBoxingStatusResult(method);
      if (status.getReturnStatus().isNotUnboxable()) {
        methodBoxingStatusResult.setRet(NO_UNBOX);
      } else {
        if (methodBoxingStatusResult.getRet() == TO_PROCESS) {
          resolve(new MethodRet(method));
        }
      }
      for (int i = 0; i < status.getArgStatuses().length; i++) {
        ValueBoxingStatus argStatus = status.getArgStatus(i);
        if (argStatus.isNotUnboxable()) {
          methodBoxingStatusResult.setArg(NO_UNBOX, i);
        } else {
          if (methodBoxingStatusResult.getArg(i) == TO_PROCESS) {
            resolve(new MethodArg(i, method));
          }
        }
      }
    }
    assert noResultForNoneUnboxable();
    assert allProcessed();
    clearNoneUnboxable();
    return boxingStatusResultMap;
  }

  private boolean noResultForNoneUnboxable() {
    boxingStatusResultMap.forEach(
        (k, v) -> {
          assert methodBoxingStatus.containsKey(k);
        });
    return true;
  }

  private boolean allProcessedAndUnboxable(Map<DexMethod, MethodBoxingStatus> methodBoxingStatus) {
    methodBoxingStatus.forEach(
        (k, v) -> {
          assert !v.isNoneUnboxable() : v + " registered for " + k;
          assert !v.isUnprocessedCandidate() : v + " registered for " + k;
        });
    return true;
  }

  private void clearNoneUnboxable() {
    boxingStatusResultMap.values().removeIf(MethodBoxingStatusResult::isNoneUnboxable);
  }

  private boolean allProcessed() {
    boxingStatusResultMap.forEach(
        (k, v) -> {
          assert v.getRet() != TO_PROCESS;
          for (BoxingStatusResult arg : v.getArgs()) {
            assert arg != TO_PROCESS;
          }
        });
    return true;
  }

  private ValueBoxingStatus getValueBoxingStatus(TransitiveDependency dep) {
    // Later we will implement field dependencies.
    assert dep.isMethodDependency();
    MethodBoxingStatus status = methodBoxingStatus.get(dep.asMethodDependency().getMethod());
    if (status == null) {
      // Nothing was recorded because nothing was unboxable.
      return ValueBoxingStatus.NOT_UNBOXABLE;
    }
    if (dep.isMethodRet()) {
      return status.getReturnStatus();
    }
    assert dep.isMethodArg();
    return status.getArgStatus(dep.asMethodArg().getParameterIndex());
  }

  private void resolve(TransitiveDependency dep) {
    WorkList<TransitiveDependency> workList = WorkList.newIdentityWorkList(dep);
    int delta = 0;
    while (workList.hasNext()) {
      TransitiveDependency next = workList.next();
      BoxingStatusResult boxingStatusResult = get(next);
      if (boxingStatusResult == UNBOX) {
        delta++;
        continue;
      }
      ValueBoxingStatus valueBoxingStatus = getValueBoxingStatus(next);
      if (boxingStatusResult == NO_UNBOX || valueBoxingStatus.isNotUnboxable()) {
        // TODO(b/307872552): Unbox when a non unboxable non null dependency is present.
        // If a dependency is not unboxable, we need to prove it's non-null, else we cannot unbox.
        // In this first version we bail out by setting a negative delta.
        delta = -1;
        break;
      }
      assert boxingStatusResult == TO_PROCESS;
      workList.addIfNotSeen(valueBoxingStatus.getTransitiveDependencies());
      // Each dependency has been pessimistically marked as requiring extra boxing operation.
      // TODO(b/307872552): Test and re-evaluate the delta computation.
      delta += valueBoxingStatus.getTransitiveDependencies().size();
      delta += valueBoxingStatus.getBoxingDelta();
    }
    BoxingStatusResult boxingStatusResult = delta > UNBOX_DELTA_THRESHOLD ? UNBOX : NO_UNBOX;
    for (TransitiveDependency transitiveDependency : workList.getSeenSet()) {
      assert transitiveDependency.isMethodDependency();
      register(transitiveDependency.asMethodDependency(), boxingStatusResult);
    }
  }
}
