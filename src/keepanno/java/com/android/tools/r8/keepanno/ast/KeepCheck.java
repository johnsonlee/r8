// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public class KeepCheck extends KeepDeclaration {

  public enum KeepCheckKind {
    REMOVED,
    OPTIMIZED_OUT
  }

  public static class Builder {

    private KeepEdgeMetaInfo metaInfo = KeepEdgeMetaInfo.none();
    private KeepCheckKind kind = KeepCheckKind.REMOVED;
    private KeepBindings bindings = KeepBindings.none();
    private KeepItemReference itemReference;

    public Builder setMetaInfo(KeepEdgeMetaInfo metaInfo) {
      this.metaInfo = metaInfo;
      return this;
    }

    public Builder setKind(KeepCheckKind kind) {
      this.kind = kind;
      return this;
    }

    public Builder setBindings(KeepBindings bindings) {
      this.bindings = bindings;
      return this;
    }

    public Builder setItemReference(KeepItemReference itemReference) {
      this.itemReference = itemReference;
      return this;
    }

    public KeepCheck build() {
      if (itemReference == null) {
        throw new KeepEdgeException("KeepCheck must have an item pattern.");
      }
      bindings.verify(itemReference);
      return new KeepCheck(metaInfo, kind, bindings, itemReference);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final KeepEdgeMetaInfo metaInfo;
  private final KeepCheckKind kind;
  private final KeepBindings bindings;
  private final KeepItemReference itemReference;

  private KeepCheck(
      KeepEdgeMetaInfo metaInfo,
      KeepCheckKind kind,
      KeepBindings bindings,
      KeepItemReference itemReference) {
    this.metaInfo = metaInfo;
    this.kind = kind;
    this.bindings = bindings;
    this.itemReference = itemReference;
  }

  @Override
  public KeepCheck asKeepCheck() {
    return this;
  }

  public KeepEdgeMetaInfo getMetaInfo() {
    return metaInfo;
  }

  public KeepCheckKind getKind() {
    return kind;
  }

  public KeepBindings getBindings() {
    return bindings;
  }

  public KeepItemReference getItemReference() {
    return itemReference;
  }

  public KeepItemPattern getItemPattern() {
    if (itemReference.isBindingReference()) {
      return bindings.get(itemReference.asBindingReference()).getItem();
    }
    return itemReference.asItemPattern();
  }

  @Override
  public String toString() {
    return "KeepCheck{kind=" + kind + ", item=" + itemReference + "}";
  }
}
