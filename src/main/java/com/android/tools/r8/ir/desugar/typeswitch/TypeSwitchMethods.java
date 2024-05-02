// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateTypeSwitchMethods.java.
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.typeswitch;

import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;

public final class TypeSwitchMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("[Ljava/lang/Object;");
  }

  public static CfCode TypeSwitchMethods_typeSwitch(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(IfType.NE, ValueType.OBJECT, label2),
            label1,
            new CfConstNumber(-1, ValueType.INT),
            new CfReturn(ValueType.INT),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;"))
                    })),
            new CfLoad(ValueType.INT, 1),
            new CfStore(ValueType.INT, 3),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 3),
            new CfLoad(ValueType.OBJECT, 2),
            new CfArrayLength(),
            new CfIfCmp(IfType.GE, ValueType.INT, label11),
            label4,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.INT, 3),
            new CfArrayLoad(MemberType.OBJECT),
            new CfStore(ValueType.OBJECT, 4),
            label5,
            new CfLoad(ValueType.OBJECT, 4),
            new CfInstanceOf(factory.classType),
            new CfIf(IfType.EQ, ValueType.INT, label8),
            label6,
            new CfLoad(ValueType.OBJECT, 4),
            new CfCheckCast(factory.classType),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("isInstance")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label10),
            label7,
            new CfLoad(ValueType.INT, 3),
            new CfReturn(ValueType.INT),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label10),
            label9,
            new CfLoad(ValueType.INT, 3),
            new CfReturn(ValueType.INT),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType()
                    })),
            new CfIinc(3, 1),
            new CfGoto(label3),
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;"))
                    })),
            new CfConstNumber(-2, ValueType.INT),
            new CfReturn(ValueType.INT),
            label12),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
