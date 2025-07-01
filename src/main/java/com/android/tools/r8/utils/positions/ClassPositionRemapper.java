// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.positions;

import com.android.tools.r8.ResourceException;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.KotlinSourceDebugExtensionParserResult;
import com.android.tools.r8.utils.CfLineToMethodMapper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import java.util.Map;
import java.util.Map.Entry;

// PositionRemapper is a stateful function which takes a position (represented by a
// DexDebugPositionState) and returns a remapped Position.
public interface ClassPositionRemapper {

  MethodPositionRemapper createMethodPositionRemapper();

  class IdentityPositionRemapper
      implements AppPositionRemapper, ClassPositionRemapper, MethodPositionRemapper {

    @Override
    public ClassPositionRemapper createClassPositionRemapper(DexProgramClass clazz) {
      return this;
    }

    @Override
    public MethodPositionRemapper createMethodPositionRemapper() {
      return this;
    }

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      // If we create outline calls we have to map them.
      assert position.getOutlineCallee() == null;
      return new Pair<>(position, position);
    }
  }

  class OptimizingPositionRemapper implements AppPositionRemapper, ClassPositionRemapper {

    private final int maxLineDelta;

    OptimizingPositionRemapper(InternalOptions options) {
      // TODO(b/113198295): For dex using "Constants.DBG_LINE_RANGE + Constants.DBG_LINE_BASE"
      //  instead of 1 creates a ~30% smaller map file but the dex files gets larger due to reduced
      //  debug info canonicalization.
      maxLineDelta = options.isGeneratingClassFiles() ? Integer.MAX_VALUE : 1;
    }

    @Override
    public ClassPositionRemapper createClassPositionRemapper(DexProgramClass clazz) {
      return this;
    }

    @Override
    public MethodPositionRemapper createMethodPositionRemapper() {
      return new OptimizingMethodPositionRemapper();
    }

    class OptimizingMethodPositionRemapper implements MethodPositionRemapper {

      private DexMethod previousMethod = null;
      private int previousSourceLine = -1;
      private int nextOptimizedLineNumber = 1;

      @Override
      public Pair<Position, Position> createRemappedPosition(Position position) {
        assert position.getMethod() != null;
        if (position.getMethod().isIdenticalTo(previousMethod)) {
          assert previousSourceLine >= 0;
          if (position.getLine() > previousSourceLine
              && position.getLine() - previousSourceLine <= maxLineDelta) {
            nextOptimizedLineNumber += (position.getLine() - previousSourceLine) - 1;
          }
        }

        Position newPosition =
            position
                .builderWithCopy()
                .setLine(nextOptimizedLineNumber++)
                .setCallerPosition(null)
                .build();
        previousSourceLine = position.getLine();
        previousMethod = position.getMethod();
        return new Pair<>(position, newPosition);
      }
    }
  }

  class KotlinInlineFunctionAppPositionRemapper implements AppPositionRemapper {

    private final AppView<?> appView;
    private final AppPositionRemapper baseRemapper;
    private final DexItemFactory factory;
    private final CfLineToMethodMapper lineToMethodMapper;

    KotlinInlineFunctionAppPositionRemapper(
        AppView<?> appView,
        AppPositionRemapper baseRemapper,
        CfLineToMethodMapper lineToMethodMapper) {
      this.appView = appView;
      this.baseRemapper = baseRemapper;
      this.factory = appView.dexItemFactory();
      this.lineToMethodMapper = lineToMethodMapper;
    }

    @Override
    public ClassPositionRemapper createClassPositionRemapper(DexProgramClass clazz) {
      ClassPositionRemapper baseClassRemapper = baseRemapper.createClassPositionRemapper(clazz);
      assert baseClassRemapper == baseRemapper;
      return new KotlinInlineFunctionClassPositionRemapper(clazz, baseClassRemapper);
    }

    class KotlinInlineFunctionClassPositionRemapper implements ClassPositionRemapper {

      private final ClassPositionRemapper baseRemapper;
      private final KotlinSourceDebugExtensionParserResult kotlinSourceDebugExtension;

      private KotlinInlineFunctionClassPositionRemapper(
          DexProgramClass clazz, ClassPositionRemapper baseRemapper) {
        this.baseRemapper = baseRemapper;
        this.kotlinSourceDebugExtension =
            KotlinSourceDebugExtensionParser.parse(appView.getSourceDebugExtensionForType(clazz));
      }

      @Override
      public MethodPositionRemapper createMethodPositionRemapper() {
        MethodPositionRemapper baseMethodRemapper = baseRemapper.createMethodPositionRemapper();
        return new KotlinInlineFunctionMethodPositionRemapper(this, baseMethodRemapper);
      }

      class KotlinInlineFunctionMethodPositionRemapper implements MethodPositionRemapper {

        private final KotlinInlineFunctionClassPositionRemapper classRemapper;
        private final MethodPositionRemapper baseRemapper;

        private KotlinInlineFunctionMethodPositionRemapper(
            KotlinInlineFunctionClassPositionRemapper classRemapper,
            MethodPositionRemapper baseRemapper) {
          this.classRemapper = classRemapper;
          this.baseRemapper = baseRemapper;
        }

        @Override
        public Pair<Position, Position> createRemappedPosition(Position position) {
          int line = position.getLine();
          KotlinSourceDebugExtensionParserResult parsedData =
              classRemapper.kotlinSourceDebugExtension;
          if (parsedData == null) {
            return baseRemapper.createRemappedPosition(position);
          }
          Map.Entry<Integer, KotlinSourceDebugExtensionParser.Position> inlinedPosition =
              parsedData.lookupInlinedPosition(line);
          if (inlinedPosition == null) {
            return baseRemapper.createRemappedPosition(position);
          }
          int inlineeLineDelta = line - inlinedPosition.getKey();
          int originalInlineeLine = inlinedPosition.getValue().getRange().from + inlineeLineDelta;
          try {
            String binaryName = inlinedPosition.getValue().getSource().getPath();
            String nameAndDescriptor =
                lineToMethodMapper.lookupNameAndDescriptor(binaryName, originalInlineeLine);
            if (nameAndDescriptor == null) {
              return baseRemapper.createRemappedPosition(position);
            }
            String clazzDescriptor = DescriptorUtils.getDescriptorFromClassBinaryName(binaryName);
            String methodName = CfLineToMethodMapper.getName(nameAndDescriptor);
            String methodDescriptor = CfLineToMethodMapper.getDescriptor(nameAndDescriptor);
            String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(methodDescriptor);
            String[] argumentDescriptors =
                DescriptorUtils.getArgumentTypeDescriptors(methodDescriptor);
            DexString[] argumentDexStringDescriptors = new DexString[argumentDescriptors.length];
            for (int i = 0; i < argumentDescriptors.length; i++) {
              argumentDexStringDescriptors[i] = factory.createString(argumentDescriptors[i]);
            }
            DexMethod inlinee =
                factory.createMethod(
                    factory.createString(clazzDescriptor),
                    factory.createString(methodName),
                    factory.createString(returnTypeDescriptor),
                    argumentDexStringDescriptors);
            if (!inlinee.equals(position.getMethod())) {
              // We have an inline from a different method than the current position.
              Entry<Integer, KotlinSourceDebugExtensionParser.Position> calleePosition =
                  parsedData.lookupCalleePosition(line);
              if (calleePosition != null) {
                // Take the first line as the callee position
                int calleeLine = Math.max(0, calleePosition.getValue().getRange().from);
                position = position.builderWithCopy().setLine(calleeLine).build();
              }
              return baseRemapper.createRemappedPosition(
                  SourcePosition.builder()
                      .setLine(originalInlineeLine)
                      .setMethod(inlinee)
                      .setCallerPosition(position)
                      .build());
            }
            // This is the same position, so we should really not mark this as an inline position.
            // Fall through to the default case.
          } catch (ResourceException ignored) {
            // Intentionally left empty. Remapping of kotlin functions utility is a best effort
            // mapping.
          }
          return baseRemapper.createRemappedPosition(position);
        }
      }
    }
  }
}
