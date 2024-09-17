// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.build.shrinker.r8integration.R8ResourceShrinkerState;
import com.android.tools.r8.errors.FinalRClassEntriesWithOptimizedShrinkingDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.IdentityHashMap;
import java.util.Map;

public class ResourceAccessAnalysis implements EnqueuerFieldAccessAnalysis {

  private final R8ResourceShrinkerState resourceShrinkerState;
  private final Map<DexType, RClassFieldToValueStore> fieldToValueMapping = new IdentityHashMap<>();
  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Enqueuer enqueuer;

  private ResourceAccessAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.resourceShrinkerState = appView.getResourceShrinkerState();
    this.enqueuer = enqueuer;
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    if (fieldAccessAnalysisEnabled(appView, enqueuer)) {
      enqueuer.registerFieldAccessAnalysis(new ResourceAccessAnalysis(appView, enqueuer));
    }
    if (liveFieldAnalysisEnabled(appView, enqueuer)) {
      enqueuer.registerAnalysis(
          new EnqueuerAnalysis() {
            @Override
            public void processNewlyLiveField(
                ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {
              DexEncodedField definition = field.getDefinition();
              if (field.getAccessFlags().isStatic()
                  && definition.hasExplicitStaticValue()
                  && definition.getStaticValue().isDexValueResourceNumber()) {
                appView
                    .getResourceShrinkerState()
                    .trace(definition.getStaticValue().asDexValueResourceNumber().getValue());
              }
            }
          });
    }
  }

  @Override
  public void done(Enqueuer enqueuer) {
    EnqueuerFieldAccessAnalysis.super.done(enqueuer);
  }

  private static boolean liveFieldAnalysisEnabled(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    return appView.options().androidResourceProvider != null
        && appView.options().isOptimizedResourceShrinking()
        && enqueuer.getMode().isFinalTreeShaking();
  }

  private static boolean fieldAccessAnalysisEnabled(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    return appView.options().androidResourceProvider != null
        && appView.options().resourceShrinkerConfiguration.isOptimizedShrinking()
        // Only run this in the first round, we explicitly trace the resource values
        // with ResourceConstNumber in the optimizing pipeline.
        && enqueuer.getMode().isInitialTreeShaking();
  }

  @Override
  public void traceStaticFieldRead(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    ProgramField resolvedField = resolutionResult.getProgramField();
    if (resolvedField == null) {
      return;
    }
    if (enqueuer.isRClass(resolvedField.getHolder())) {
      DexType holderType = resolvedField.getHolderType();
      if (!fieldToValueMapping.containsKey(holderType)) {
        populateRClassValues(resolvedField);
      }
      assert fieldToValueMapping.containsKey(holderType);
      RClassFieldToValueStore rClassFieldToValueStore = fieldToValueMapping.get(holderType);
      IntList integers = rClassFieldToValueStore.valueMapping.get(field);
      // The R class can have fields injected, e.g., by jacoco, we don't have resource values for
      // these.
      if (integers != null) {
        for (Integer integer : integers) {
          resourceShrinkerState.trace(integer);
        }
      }
    }
  }

  private void populateRClassValues(ProgramField field) {
    // TODO(287398085): Pending discussions with the AAPT2 team, we might need to harden this
    // to not fail if we wrongly classify an unrelated class as R class in our heuristic..
    RClassFieldToValueStore.Builder rClassValueBuilder = new RClassFieldToValueStore.Builder();
    analyzeStaticFields(field, rClassValueBuilder);
    ProgramMethod programClassInitializer = field.getHolder().getProgramClassInitializer();
    if (programClassInitializer != null) {
      analyzeClassInitializer(rClassValueBuilder, programClassInitializer);
    }
    warnOnFinalIdFields(field.getHolder());
    fieldToValueMapping.put(field.getHolderType(), rClassValueBuilder.build());
  }

  private void warnOnFinalIdFields(DexProgramClass holder) {
    if (!appView.options().isOptimizedResourceShrinking()) {
      return;
    }
    for (DexEncodedField field : holder.fields()) {
      if (field.isStatic()
          && field.isFinal()
          && field.hasExplicitStaticValue()
          && field.getType().isIntType()) {
        appView
            .reporter()
            .warning(
                new FinalRClassEntriesWithOptimizedShrinkingDiagnostic(
                    holder.origin, field.getReference()));
      }
    }
  }

  private void analyzeClassInitializer(
      RClassFieldToValueStore.Builder rClassValueBuilder, ProgramMethod programClassInitializer) {
    IRCode code = programClassInitializer.buildIR(appView, MethodConversionOptions.nonConverting());

    // We handle two cases:
    //  - Simple integer field assigments.
    //  - Assigments of integer arrays to fields.
    for (StaticPut staticPut : code.<StaticPut>instructions(Instruction::isStaticPut)) {
      Value value = staticPut.value();
      if (value.isPhi()) {
        continue;
      }
      IntList values;
      Instruction definition = staticPut.value().definition;
      if (definition.isConstNumber()) {
        values = new IntArrayList(1);
        values.add(definition.asConstNumber().getIntValue());
      } else if (definition.isResourceConstNumber()) {
        throw new Unreachable("Only running ResourceAccessAnalysis in initial tree shaking");
      } else if (definition.isNewArrayEmpty()) {
        NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
        values = new IntArrayList();
        for (Instruction uniqueUser : newArrayEmpty.outValue().uniqueUsers()) {
          if (uniqueUser.isArrayPut()) {
            Value constValue = uniqueUser.asArrayPut().value();
            if (constValue.isConstNumber()) {
              values.add(constValue.getDefinition().asConstNumber().getIntValue());
            } else if (constValue.isConstResourceNumber()) {
              throw new Unreachable("Only running ResourceAccessAnalysis in initial tree shaking");
            }
          } else {
            assert uniqueUser == staticPut;
          }
        }
      } else if (definition.isNewArrayFilled()) {
        values = new IntArrayList();
        for (Value inValue : definition.asNewArrayFilled().inValues()) {
          if (value.isPhi()) {
            continue;
          }
          Instruction valueDefinition = inValue.definition;
          if (valueDefinition.isConstNumber()) {
            values.add(valueDefinition.asConstNumber().getIntValue());
          } else if (valueDefinition.isResourceConstNumber()) {
            throw new Unreachable("Only running ResourceAccessAnalysis in initial tree shaking");
          }
        }
      } else {
        continue;
      }
      rClassValueBuilder.addMapping(staticPut.getField(), values);
    }
  }

  private void analyzeStaticFields(
      ProgramField field, RClassFieldToValueStore.Builder rClassValueBuilder) {
    for (DexEncodedField staticField :
        field.getHolder().staticFields(DexEncodedField::hasExplicitStaticValue)) {
      DexValue staticValue = staticField.getStaticValue();
      if (staticValue.isDexValueInt()) {
        IntList values = new IntArrayList(1);
        values.add(staticValue.asDexValueInt().getValue());
        rClassValueBuilder.addMapping(staticField.getReference(), values);
      }
    }
  }

  private static class RClassFieldToValueStore {
    private Map<DexField, IntList> valueMapping;

    private RClassFieldToValueStore(Map<DexField, IntList> valueMapping) {
      this.valueMapping = valueMapping;
    }

    public static class Builder {
      private final Map<DexField, IntList> valueMapping = new IdentityHashMap<>();

      public void addMapping(DexField field, IntList values) {
        assert !valueMapping.containsKey(field);
        valueMapping.put(field, values);
      }

      public RClassFieldToValueStore build() {
        return new RClassFieldToValueStore(valueMapping);
      }
    }
  }
}
