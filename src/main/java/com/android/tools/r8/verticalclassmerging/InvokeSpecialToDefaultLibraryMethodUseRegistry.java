package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.classmerging.ClassMergerMode;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class InvokeSpecialToDefaultLibraryMethodUseRegistry
    extends DefaultUseRegistryWithResult<Boolean, ProgramMethod> {

  private final ClassMergerMode mode;

  public InvokeSpecialToDefaultLibraryMethodUseRegistry(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context, ClassMergerMode mode) {
    super(appView, context, false);
    this.mode = mode;
    assert context.getHolder().isInterface();
  }

  @Override
  public void registerInvokeSpecial(DexMethod method) {
    assert mode.isInitial();
    handleInvokeSpecial(method);
  }

  // Handle invoke-super callbacks in the final round of class merging where we have LIR instead of
  // CF.
  @Override
  public void registerInvokeSuper(DexMethod method) {
    assert mode.isFinal();
    handleInvokeSpecial(method);
  }

  private void handleInvokeSpecial(DexMethod method) {
    ProgramMethod context = getContext();
    if (!method.getHolderType().isIdenticalTo(context.getHolderType())) {
      return;
    }

    DexEncodedMethod definition = context.getHolder().lookupMethod(method);
    if (definition != null && definition.belongsToVirtualPool()) {
      setResult(true);
    }
  }
}
