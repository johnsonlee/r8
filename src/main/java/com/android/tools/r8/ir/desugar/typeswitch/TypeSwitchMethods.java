// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateTypeSwitchMethods.java.
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.typeswitch;

import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class TypeSwitchMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Ljava/lang/Enum;");
    factory.createSynthesizedType("Ljava/lang/Number;");
    factory.createSynthesizedType("[Ljava/lang/Object;");
  }

  public static CfCode TypeSwitchMethods_switchEnumEq(DexItemFactory factory, DexMethod method) {
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
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        8,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfArrayLoad(MemberType.OBJECT),
            new CfIf(IfType.NE, ValueType.OBJECT, label11),
            label1,
            new CfConstNull(),
            new CfStore(ValueType.OBJECT, 5),
            label2,
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.classType, factory.stringType),
                    factory.createString("forName")),
                false),
            new CfStore(ValueType.OBJECT, 6),
            label3,
            new CfLoad(ValueType.OBJECT, 6),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.booleanType),
                    factory.createString("isEnum")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label6),
            label4,
            new CfLoad(ValueType.OBJECT, 6),
            new CfStore(ValueType.OBJECT, 7),
            label5,
            new CfLoad(ValueType.OBJECT, 7),
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Enum;"),
                    factory.createProto(
                        factory.createType("Ljava/lang/Enum;"),
                        factory.classType,
                        factory.stringType),
                    factory.createString("valueOf")),
                false),
            new CfStore(ValueType.OBJECT, 5),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfGoto(label8),
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.throwableType)))),
            new CfStore(ValueType.OBJECT, 6),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.OBJECT, 5),
            new CfIf(IfType.NE, ValueType.OBJECT, label9),
            new CfNew(factory.objectType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfGoto(label10),
            label9,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("[Ljava/lang/Object;")),
                        FrameType.intType()))),
            new CfLoad(ValueType.OBJECT, 5),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("[Ljava/lang/Object;")),
                        FrameType.intType(),
                        FrameType.initializedNonNullReference(factory.objectType)))),
            new CfArrayStore(MemberType.OBJECT),
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfArrayLoad(MemberType.OBJECT),
            new CfIfCmp(IfType.NE, ValueType.OBJECT, label12),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label13),
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfConstNumber(0, ValueType.INT),
            label13,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfReturn(ValueType.INT),
            label14),
        ImmutableList.of(
            new CfTryCatch(
                label2, label6, ImmutableList.of(factory.throwableType), ImmutableList.of(label7))),
        ImmutableList.of());
  }

  public static CfCode TypeSwitchMethods_switchIntEq(DexItemFactory factory, DexMethod method) {
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
    return new CfCode(
        method.holder,
        2,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(factory.createType("Ljava/lang/Number;")),
            new CfIf(IfType.EQ, ValueType.INT, label5),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(factory.createType("Ljava/lang/Number;")),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/Number;"),
                    factory.createProto(factory.intType),
                    factory.createString("intValue")),
                false),
            new CfIfCmp(IfType.NE, ValueType.INT, label3),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Number;"))
                    })),
            new CfConstNumber(0, ValueType.INT),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Number;"))
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfReturn(ValueType.INT),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType), FrameType.intType()
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(factory.boxedCharType),
            new CfIf(IfType.EQ, ValueType.INT, label10),
            label6,
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(factory.boxedCharType),
            new CfStore(ValueType.OBJECT, 2),
            label7,
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.charType),
                    factory.createString("charValue")),
                false),
            new CfIfCmp(IfType.NE, ValueType.INT, label8),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label9),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.boxedCharType)
                    })),
            new CfConstNumber(0, ValueType.INT),
            label9,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.boxedCharType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfReturn(ValueType.INT),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType), FrameType.intType()
                    })),
            new CfConstNumber(0, ValueType.INT),
            new CfReturn(ValueType.INT),
            label11),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
