// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.NewlyLiveClassEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveFieldEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveMethodEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyReachableFieldEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyTargetedMethodEnqueuerAnalysis;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.EnqueuerDefinitionSupplier;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.Set;

public class GenericSignatureEnqueuerAnalysis
    implements NewlyLiveClassEnqueuerAnalysis,
        NewlyLiveFieldEnqueuerAnalysis,
        NewlyLiveMethodEnqueuerAnalysis,
        NewlyReachableFieldEnqueuerAnalysis,
        NewlyTargetedMethodEnqueuerAnalysis {

  private final EnqueuerDefinitionSupplier enqueuerDefinitionSupplier;
  private final Set<DexReference> processedSignatures = Sets.newIdentityHashSet();

  public GenericSignatureEnqueuerAnalysis(EnqueuerDefinitionSupplier enqueuerDefinitionSupplier) {
    this.enqueuerDefinitionSupplier = enqueuerDefinitionSupplier;
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      EnqueuerDefinitionSupplier enqueuerDefinitionSupplier,
      EnqueuerAnalysisCollection.Builder builder) {
    // TODO(b/323816623): This check does not include presence of keep declarations.
    //  We should consider if we should always run the signature analysis and just not emit them
    //  in the end?
    InternalOptions options = appView.options();
    if (options.hasProguardConfiguration()
        && options.getProguardConfiguration().getKeepAttributes().signature) {
      GenericSignatureEnqueuerAnalysis analysis =
          new GenericSignatureEnqueuerAnalysis(enqueuerDefinitionSupplier);
      builder
          .addNewlyLiveClassAnalysis(analysis)
          .addNewlyLiveFieldAnalysis(analysis)
          .addNewlyLiveMethodAnalysis(analysis)
          .addNewlyReachableFieldAnalysis(analysis)
          .addNewlyTargetedMethodAnalysis(analysis);
    }
  }

  @Override
  public void processNewlyLiveClass(DexProgramClass clazz, EnqueuerWorklist worklist) {
    processSignature(clazz, clazz.getContext());
  }

  @Override
  public void processNewlyLiveField(
      ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {
    processSignature(field, context);
  }

  @Override
  public void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {
    processSignature(method, context);
  }

  @Override
  public void processNewlyReachableField(ProgramField field, EnqueuerWorklist worklist) {
    processSignature(field, field.getContext());
  }

  @Override
  public void processNewlyTargetedMethod(ProgramMethod method, EnqueuerWorklist worklist) {
    processSignature(method, method.getContext());
  }

  private void processSignature(ProgramDefinition signatureHolder, ProgramDefinition context) {
    if (!processedSignatures.add(signatureHolder.getReference())) {
      return;
    }
    GenericSignatureTypeVisitor genericSignatureTypeVisitor =
        new GenericSignatureTypeVisitor(context, enqueuerDefinitionSupplier::definitionFor);
    if (signatureHolder.isClass()) {
      genericSignatureTypeVisitor.visitClassSignature(
          signatureHolder.asClass().getClassSignature());
    } else if (signatureHolder.isMethod()) {
      genericSignatureTypeVisitor.visitMethodSignature(
          signatureHolder.asMethod().getDefinition().getGenericSignature());
    } else {
      assert signatureHolder.isField();
      genericSignatureTypeVisitor.visitFieldTypeSignature(
          signatureHolder.asField().getDefinition().getGenericSignature());
    }
  }
}
