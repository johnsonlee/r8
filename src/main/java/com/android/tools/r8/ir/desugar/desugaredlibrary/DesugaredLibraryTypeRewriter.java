// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.google.common.collect.Iterables;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class DesugaredLibraryTypeRewriter {

  public static DesugaredLibraryTypeRewriter empty() {
    return new EmptyTypeRewriter();
  }

  public abstract void rewriteType(DexType type, DexType rewrittenType);

  public abstract DexType rewrittenType(DexType type);

  public abstract DexType rewrittenContextType(DexType type);

  public boolean hasRewrittenType(DexType type) {
    return rewrittenType(type) != null;
  }

  public boolean hasRewrittenTypeInSignature(DexProto proto) {
    return hasRewrittenType(proto.getReturnType())
        || Iterables.any(proto.getParameters(), this::hasRewrittenType);
  }

  public abstract boolean isRewriting();

  public abstract void forEachRewrittenType(Consumer<DexType> consumer);

  public static class MachineTypeRewriter extends DesugaredLibraryTypeRewriter {

    private final DexItemFactory factory;
    private final Map<DexType, DexType> rewriteType;
    private final Map<DexType, DexType> rewriteDerivedTypeOnly;

    public MachineTypeRewriter(
        DexItemFactory factory, MachineDesugaredLibrarySpecification specification) {
      this.factory = factory;
      this.rewriteType = new ConcurrentHashMap<>(specification.getRewriteType());
      this.rewriteDerivedTypeOnly = specification.getRewriteDerivedTypeOnly();
    }

    @Override
    public DexType rewrittenType(DexType type) {
      if (type.isArrayType()) {
        DexType rewrittenBaseType = rewrittenType(type.toBaseType(factory));
        return rewrittenBaseType != null ? type.replaceBaseType(rewrittenBaseType, factory) : null;
      }
      return rewriteType.get(type);
    }

    @Override
    public DexType rewrittenContextType(DexType context) {
      assert !context.isArrayType();
      if (rewriteType.containsKey(context)) {
        return rewriteType.get(context);
      }
      return rewriteDerivedTypeOnly.get(context);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void rewriteType(DexType type, DexType rewrittenType) {
      rewriteType.compute(
          type,
          (t, val) -> {
            assert val == null || val == rewrittenType;
            return rewrittenType;
          });
    }

    @Override
    public boolean isRewriting() {
      return true;
    }

    @Override
    public void forEachRewrittenType(Consumer<DexType> consumer) {
      rewriteType.keySet().forEach(consumer);
    }
  }

  public static class EmptyTypeRewriter extends DesugaredLibraryTypeRewriter {

    @Override
    public DexType rewrittenType(DexType type) {
      return null;
    }

    @Override
    public DexType rewrittenContextType(DexType type) {
      return null;
    }

    @Override
    public void rewriteType(DexType type, DexType rewrittenType) {}

    @Override
    public boolean isRewriting() {
      return false;
    }

    @Override
    public void forEachRewrittenType(Consumer<DexType> consumer) {}
  }
}
