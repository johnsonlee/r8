// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo.ClassNameMapping.CANONICAL_NAME;
import static com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo.ClassNameMapping.NAME;
import static com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo.ClassNameMapping.SIMPLE_NAME;
import static com.android.tools.r8.utils.DescriptorUtils.INNER_CLASS_SEPARATOR;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.escape.EscapeAnalysis;
import com.android.tools.r8.ir.analysis.escape.EscapeAnalysisConfiguration;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo;
import com.android.tools.r8.shaking.KeepClassInfo;

public class ClassGetNameOptimizer extends CodeRewriterPass<AppInfo> {

  public ClassGetNameOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "StringOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    if (options.enableNameReflectionOptimization
        || options.testing.forceNameReflectionOptimization) {
      return !isDebugMode(code.context());
    }
    return false;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = rewriteClassGetName(code);
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  // Find Class#get*Name() with a constant-class and replace it with a const-string if possible.
  private boolean rewriteClassGetName(IRCode code) {
    boolean hasChanged = false;
    AffectedValues affectedValues = new AffectedValues();
    InstructionListIterator it = code.instructionListIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (!instr.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = instr.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!dexItemFactory.classMethods.isReflectiveNameLookup(invokedMethod)) {
        continue;
      }

      Value out = invoke.outValue();
      // Skip the call if the computed name is already discarded or not used anywhere.
      if (out == null || !out.hasAnyUsers()) {
        continue;
      }

      assert invoke.inValues().size() == 1;
      // In case of handling multiple invocations over the same const-string, all the following
      // usages after the initial one will point to non-null IR (a.k.a. alias), e.g.,
      //
      //   rcv <- invoke-virtual instance, ...#getClass() // Can be rewritten to const-class
      //   x <- invoke-virtual rcv, Class#getName()
      //   non_null_rcv <- non-null rcv
      //   y <- invoke-virtual non_null_rcv, Class#getCanonicalName()
      //   z <- invoke-virtual non_null_rcv, Class#getSimpleName()
      //   ... // or some other usages of the same usage.
      //
      // In that case, we should check if the original source is (possibly rewritten) const-class.
      Value in = invoke.getReceiver().getAliasedValue();
      if (in.definition == null || !in.definition.isConstClass() || in.hasLocalInfo()) {
        continue;
      }

      ConstClass constClass = in.definition.asConstClass();
      DexType type = constClass.getType();
      int arrayDepth = type.getNumberOfLeadingSquareBrackets();
      DexType baseType = type.toBaseType(dexItemFactory);
      // Make sure base type is a class type.
      if (!baseType.isClassType()) {
        continue;
      }
      DexClass holder = appView.definitionFor(baseType);
      if (holder == null) {
        continue;
      }
      boolean mayBeRenamed =
          holder.isProgramClass()
              && appView
                  .getKeepInfoOrDefault(holder.asProgramClass(), KeepClassInfo.top())
                  .isMinificationAllowed(options);
      // b/120138731: Filter out escaping uses. In such case, the result of this optimization will
      // be stored somewhere, which can lead to a regression if the corresponding class is in a deep
      // package hierarchy. For local cases, it is likely a one-time computation, but make sure the
      // result is used reasonably, such as library calls. For example, if a class may be minified
      // while its name is used to compute hash code, which won't be optimized, it's better not to
      // compute the name.
      if (!appView.options().testing.forceNameReflectionOptimization) {
        if (mayBeRenamed) {
          continue;
        }
        if (invokedMethod.isNotIdenticalTo(dexItemFactory.classMethods.getSimpleName)) {
          EscapeAnalysis escapeAnalysis =
              new EscapeAnalysis(appView, StringOptimizerEscapeAnalysisConfiguration.getInstance());
          if (escapeAnalysis.isEscaping(code, out)) {
            continue;
          }
        }
      }

      String descriptor = baseType.toDescriptorString();
      boolean assumeTopLevel = descriptor.indexOf(INNER_CLASS_SEPARATOR) < 0;
      DexItemBasedConstString deferred = null;
      DexString name = null;
      if (invokedMethod.isIdenticalTo(dexItemFactory.classMethods.getName)) {
        if (mayBeRenamed) {
          Value stringValue =
              code.createValue(
                  TypeElement.stringClassType(appView, definitelyNotNull()), invoke.getLocalInfo());
          deferred =
              new DexItemBasedConstString(
                  stringValue, baseType, ClassNameComputationInfo.create(NAME, arrayDepth));
        } else {
          name = NAME.map(descriptor, holder, dexItemFactory, arrayDepth);
        }
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.classMethods.getTypeName)) {
        // TODO(b/119426668): desugar Type#getTypeName
        continue;
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.classMethods.getCanonicalName)) {
        // Always returns null if the target type is local or anonymous class.
        if (holder.isLocalClass() || holder.isAnonymousClass()) {
          ConstNumber constNull = code.createConstNull();
          it.replaceCurrentInstruction(constNull, affectedValues);
        } else {
          // b/119471127: If an outer class is shrunk, we may compute a wrong canonical name.
          // Leave it as-is so that the class's canonical name is consistent across the app.
          if (!assumeTopLevel) {
            continue;
          }
          if (mayBeRenamed) {
            Value stringValue =
                code.createValue(
                    TypeElement.stringClassType(appView, definitelyNotNull()),
                    invoke.getLocalInfo());
            deferred =
                new DexItemBasedConstString(
                    stringValue,
                    baseType,
                    ClassNameComputationInfo.create(CANONICAL_NAME, arrayDepth));
          } else {
            name = CANONICAL_NAME.map(descriptor, holder, dexItemFactory, arrayDepth);
          }
        }
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.classMethods.getSimpleName)) {
        // Always returns an empty string if the target type is an anonymous class.
        if (holder.isAnonymousClass()) {
          name = dexItemFactory.createString("");
        } else {
          // b/120130435: If an outer class is shrunk, we may compute a wrong simple name.
          // Leave it as-is so that the class's simple name is consistent across the app.
          if (!assumeTopLevel) {
            continue;
          }
          if (mayBeRenamed) {
            Value stringValue =
                code.createValue(
                    TypeElement.stringClassType(appView, definitelyNotNull()),
                    invoke.getLocalInfo());
            deferred =
                new DexItemBasedConstString(
                    stringValue,
                    baseType,
                    ClassNameComputationInfo.create(SIMPLE_NAME, arrayDepth));
          } else {
            name = SIMPLE_NAME.map(descriptor, holder, dexItemFactory, arrayDepth);
          }
        }
      }
      if (name != null) {
        Value stringValue =
            code.createValue(
                TypeElement.stringClassType(appView, definitelyNotNull()), invoke.getLocalInfo());
        ConstString constString = new ConstString(stringValue, name);
        it.replaceCurrentInstruction(constString, affectedValues);
        hasChanged = true;
      } else if (deferred != null) {
        it.replaceCurrentInstruction(deferred, affectedValues);
        hasChanged = true;
      }
    }
    // Computed name is not null or literally null (for canonical name of local/anonymous class).
    // In either way, that is narrower information, and thus propagate that.
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    return hasChanged;
  }

  static class StringOptimizerEscapeAnalysisConfiguration implements EscapeAnalysisConfiguration {

    private static final StringOptimizerEscapeAnalysisConfiguration INSTANCE =
        new StringOptimizerEscapeAnalysisConfiguration();

    private StringOptimizerEscapeAnalysisConfiguration() {}

    public static StringOptimizerEscapeAnalysisConfiguration getInstance() {
      return INSTANCE;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isLegitimateEscapeRoute(
        AppView<?> appView,
        EscapeAnalysis escapeAnalysis,
        Instruction escapeRoute,
        ProgramMethod context) {
      if (escapeRoute.isReturn() || escapeRoute.isThrow() || escapeRoute.isStaticPut()) {
        return false;
      }
      if (escapeRoute.isInvokeMethod()) {
        DexMethod invokedMethod = escapeRoute.asInvokeMethod().getInvokedMethod();
        // b/120138731: Only allow known simple operations on const-string
        if (invokedMethod.isIdenticalTo(appView.dexItemFactory().stringMembers.hashCode)
            || invokedMethod.isIdenticalTo(appView.dexItemFactory().stringMembers.isEmpty)
            || invokedMethod.isIdenticalTo(appView.dexItemFactory().stringMembers.length)) {
          return true;
        }
        // Add more cases to filter out, if any.
        return false;
      }
      if (escapeRoute.isArrayPut()) {
        Value array = escapeRoute.asArrayPut().array().getAliasedValue();
        return !array.isPhi() && array.definition.isCreatingArray();
      }
      if (escapeRoute.isInstancePut()) {
        Value instance = escapeRoute.asInstancePut().object().getAliasedValue();
        return !instance.isPhi() && instance.definition.isNewInstance();
      }
      // All other cases are not legitimate.
      return false;
    }
  }
}
