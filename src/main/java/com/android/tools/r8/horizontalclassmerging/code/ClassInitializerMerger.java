// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;


import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.utils.CfVersionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ListIterator;
import java.util.Set;

/**
 * Responsible for merging the class initializers in each merge group into a single class
 * initializer.
 */
public class ClassInitializerMerger {

  private final ImmutableList<ProgramMethod> classInitializers;

  private ClassInitializerMerger(ImmutableList<ProgramMethod> classInitializers) {
    this.classInitializers = classInitializers;
  }

  public static ClassInitializerMerger create(HorizontalMergeGroup group) {
    ClassInitializerMerger.Builder builder = new ClassInitializerMerger.Builder();
    group.forEach(
        clazz -> {
          if (clazz.hasClassInitializer()) {
            builder.add(clazz.getProgramClassInitializer());
          }
        });
    return builder.build();
  }

  public boolean isEmpty() {
    return classInitializers.isEmpty();
  }

  public Code getCode() {
    return new IRProvider(classInitializers);
  }

  public CfVersion getCfVersion(InternalOptions options) {
    return options.isGeneratingClassFiles() ? CfVersionUtils.max(classInitializers) : null;
  }

  public boolean isSingleton() {
    return classInitializers.size() == 1;
  }

  public DexEncodedMethod moveSingleton(HorizontalMergeGroup group, DexItemFactory dexItemFactory) {
    assert isSingleton();
    ProgramMethod method = ListUtils.first(classInitializers);
    DexEncodedMethod definition = method.getDefinition();
    if (method.getHolder() == group.getTarget()) {
      return definition;
    }
    DexMethod newReference = method.getReference().withHolder(group.getTarget(), dexItemFactory);
    return definition.toTypeSubstitutedMethodAsInlining(newReference, dexItemFactory);
  }

  public ComputedApiLevel getApiReferenceLevel(AppView<?> appView) {
    assert !classInitializers.isEmpty();
    return ListUtils.fold(
        classInitializers,
        appView.computedMinApiLevel(),
        (accApiLevel, method) -> accApiLevel.max(method.getDefinition().getApiLevel()));
  }

  public void setObsolete() {
    classInitializers.forEach(classInitializer -> classInitializer.getDefinition().setObsolete());
  }

  public int size() {
    return classInitializers.size();
  }

  public static class Builder {

    private final ImmutableList.Builder<ProgramMethod> classInitializers = ImmutableList.builder();

    public void add(ProgramMethod classInitializer) {
      assert classInitializer.getDefinition().isClassInitializer();
      assert classInitializer.getDefinition().hasCode();
      classInitializers.add(classInitializer);
    }

    public ClassInitializerMerger build() {
      return new ClassInitializerMerger(classInitializers.build());
    }
  }

  /**
   * Provides a piece of {@link IRCode} that is the concatenation of a collection of class
   * initializers.
   */
  public static class IRProvider extends Code {

    private final ImmutableList<ProgramMethod> classInitializers;

    private IRProvider(ImmutableList<ProgramMethod> classInitializers) {
      this.classInitializers = classInitializers;
    }

    @Override
    public IRCode buildIR(
        ProgramMethod method,
        AppView<?> appView,
        MutableMethodConversionOptions conversionOptions) {
      assert !classInitializers.isEmpty();

      Position preamblePosition =
          SyntheticPosition.builder()
              .setLine(0)
              .setMethod(method.getReference())
              .setIsD8R8Synthesized(method.getDefinition().isD8R8Synthesized())
              .build();

      IRMetadata metadata = new IRMetadata();
      NumberGenerator blockNumberGenerator = new NumberGenerator();
      NumberGenerator valueNumberGenerator = new NumberGenerator();

      BasicBlock block = new BasicBlock(metadata);
      block.setNumber(blockNumberGenerator.next());

      // Add "invoke-static <clinit>" for each of the class initializers to the exit block.
      for (ProgramMethod classInitializer : classInitializers) {
        block.add(
            InvokeStatic.builder()
                .setMethod(classInitializer.getReference())
                .setPosition(preamblePosition)
                .build(),
            metadata);
      }

      // Add "return-void" to exit block.
      block.add(Return.builder().setPosition(Position.none()).build(), metadata);
      block.setFilled();

      IRCode code =
          new IRCode(
              appView.options(),
              method,
              preamblePosition,
              ListUtils.newLinkedList(block),
              valueNumberGenerator,
              blockNumberGenerator,
              metadata,
              conversionOptions);

      ListIterator<BasicBlock> blockIterator = code.listIterator();
      InstructionListIterator instructionIterator = blockIterator.next().listIterator(code);

      Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
      for (ProgramMethod classInitializer : classInitializers) {
        if (!instructionIterator.hasNext()) {
          instructionIterator = blockIterator.next().listIterator(code);
        }

        InvokeStatic invoke = instructionIterator.next().asInvokeStatic();
        assert invoke != null;

        IRCode inliningIR =
            classInitializer
                .getDefinition()
                .getCode()
                .buildInliningIR(
                    method,
                    classInitializer,
                    appView,
                    appView.codeLens(),
                    valueNumberGenerator,
                    preamblePosition,
                    RewrittenPrototypeDescription.none());
        classInitializer.getDefinition().setObsolete();

        DexProgramClass downcast = null;
        instructionIterator.previous();
        instructionIterator.inlineInvoke(
            appView, code, inliningIR, blockIterator, blocksToRemove, downcast);
      }

      // Cleanup.
      code.removeBlocks(blocksToRemove);
      code.removeAllDeadAndTrivialPhis();
      code.removeRedundantBlocks();

      assert code.isConsistentSSA(appView);

      return code;
    }

    @Override
    protected int computeHashCode() {
      throw new Unreachable();
    }

    @Override
    protected boolean computeEquals(Object other) {
      throw new Unreachable();
    }

    @Override
    public int estimatedDexCodeSizeUpperBoundInBytes() {
      throw new Unreachable();
    }

    @Override
    public boolean isEmptyVoidMethod() {
      throw new Unreachable();
    }

    @Override
    public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
      throw new Unreachable();
    }

    @Override
    public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
      throw new Unreachable();
    }

    @Override
    public String toString() {
      throw new Unreachable();
    }

    @Override
    public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
      throw new Unreachable();
    }
  }
}
