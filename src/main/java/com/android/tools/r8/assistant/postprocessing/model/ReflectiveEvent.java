// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.Arrays;

public abstract class ReflectiveEvent {

  private final ReflectiveEventType eventType;
  private final String[] stack;

  protected static DexType toTypeOrTripleStar(String javaType, DexItemFactory factory) {
    if (javaType == null) {
      return factory.createType("***");
    }
    return toType(javaType, factory);
  }

  protected static DexType toType(String javaType, DexItemFactory factory) {
    return factory.createType(DescriptorUtils.javaTypeToDescriptor(javaType));
  }

  protected ReflectiveEvent(ReflectiveEventType eventType, String[] stack) {
    this.eventType = eventType;
    this.stack = stack;
  }

  public ReflectiveEventType getEventType() {
    return eventType;
  }

  public boolean isAtomicFieldUpdaterNewUpdater() {
    return false;
  }

  public AtomicFieldUpdaterNewUpdater asAtomicFieldUpdaterNewUpdater() {
    return null;
  }

  public boolean isClassGetName() {
    return false;
  }

  public ClassGetName asClassGetName() {
    return null;
  }

  public boolean isServiceLoaderLoad() {
    return false;
  }

  public ServiceLoaderLoad asServiceLoaderLoad() {
    return null;
  }

  public boolean isProxyNewProxyInstance() {
    return false;
  }

  public ProxyNewProxyInstance asProxyNewProxyInstance() {
    return null;
  }

  public boolean isClassGetMember() {
    return false;
  }

  public ClassGetMember asClassGetMember() {
    return null;
  }

  public boolean isClassNewInstance() {
    return false;
  }

  public ClassNewInstance asClassNewInstance() {
    return null;
  }

  public boolean isClassFlagEvent() {
    return false;
  }

  public ClassFlagEvent asClassFlagEvent() {
    return null;
  }

  public boolean isClassGetMembers() {
    return false;
  }

  public ClassGetMembers asClassGetMembers() {
    return null;
  }

  public boolean isClassForName() {
    return false;
  }

  public ClassForName asClassForName() {
    return null;
  }

  public abstract String getContentsString();

  public abstract boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported);

  @Override
  public String toString() {
    return eventType + (stack != null ? "[s]" : "") + "(" + getContentsString() + ")";
  }

  public static ReflectiveEvent instantiate(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    switch (eventType) {
      case CLASS_NEW_INSTANCE:
        return new ClassNewInstance(eventType, stack, args, factory);
      case CLASS_GET_DECLARED_METHOD:
      case CLASS_GET_DECLARED_FIELD:
      case CLASS_GET_DECLARED_CONSTRUCTOR:
      case CLASS_GET_METHOD:
      case CLASS_GET_FIELD:
      case CLASS_GET_CONSTRUCTOR:
        return new ClassGetMember(eventType, stack, args, factory);
      case CLASS_GET_DECLARED_METHODS:
      case CLASS_GET_DECLARED_FIELDS:
      case CLASS_GET_DECLARED_CONSTRUCTORS:
      case CLASS_GET_METHODS:
      case CLASS_GET_FIELDS:
      case CLASS_GET_CONSTRUCTORS:
        return new ClassGetMembers(eventType, stack, args, factory);
      case CLASS_GET_NAME:
        return new ClassGetName(eventType, stack, args, factory);
      case CLASS_FOR_NAME:
        return new ClassForName(eventType, stack, args);
      case CLASS_GET_COMPONENT_TYPE:
        break;
      case CLASS_GET_PACKAGE:
        break;
      case CLASS_IS_ASSIGNABLE_FROM:
        break;
      case CLASS_GET_SUPERCLASS:
        break;
      case CLASS_AS_SUBCLASS:
        break;
      case CLASS_IS_INSTANCE:
        break;
      case CLASS_CAST:
        break;
      case CLASS_FLAG:
        return new ClassFlagEvent(eventType, stack, args, factory);
      case ATOMIC_FIELD_UPDATER_NEW_UPDATER:
        return new AtomicFieldUpdaterNewUpdater(eventType, stack, args, factory);
      case SERVICE_LOADER_LOAD:
        return new ServiceLoaderLoad(eventType, stack, args, factory);
      case PROXY_NEW_PROXY_INSTANCE:
        return new ProxyNewProxyInstance(eventType, stack, args, factory);
    }
    return new ReflectiveEvent(eventType, stack) {
      @Override
      public String getContentsString() {
        return Arrays.toString(args);
      }

      @Override
      public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
        return false;
      }
    };
  }
}
