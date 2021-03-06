// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

public interface Opcodes {

  int ADD = 0;
  int ALWAYS_MATERIALIZING_DEFINITION = 1;
  int ALWAYS_MATERIALIZING_NOP = 2;
  int ALWAYS_MATERIALIZING_USER = 3;
  int AND = 4;
  int ARGUMENT = 5;
  int ARRAY_GET = 6;
  int ARRAY_LENGTH = 7;
  int ARRAY_PUT = 8;
  int ASSUME = 9;
  int CHECK_CAST = 10;
  int CMP = 11;
  int CONST_CLASS = 12;
  int CONST_METHOD_HANDLE = 13;
  int CONST_METHOD_TYPE = 14;
  int CONST_NUMBER = 15;
  int CONST_STRING = 16;
  int DEBUG_LOCAL_READ = 17;
  int DEBUG_LOCALS_CHANGE = 18;
  int DEBUG_POSITION = 19;
  int DEX_ITEM_BASED_CONST_STRING = 20;
  int DIV = 21;
  int DUP = 22;
  int DUP2 = 23;
  int GOTO = 24;
  int IF = 25;
  int INC = 26;
  int INIT_CLASS = 27;
  int INSTANCE_GET = 28;
  int INSTANCE_OF = 29;
  int INSTANCE_PUT = 30;
  int INT_SWITCH = 31;
  int INVOKE_CUSTOM = 32;
  int INVOKE_DIRECT = 33;
  int INVOKE_INTERFACE = 34;
  int INVOKE_MULTI_NEW_ARRAY = 35;
  int INVOKE_NEW_ARRAY = 36;
  int INVOKE_POLYMORPHIC = 37;
  int INVOKE_STATIC = 38;
  int INVOKE_SUPER = 39;
  int INVOKE_VIRTUAL = 40;
  int LOAD = 41;
  int MONITOR = 42;
  int MOVE = 43;
  int MOVE_EXCEPTION = 44;
  int MUL = 45;
  int NEG = 46;
  int NEW_ARRAY_EMPTY = 47;
  int NEW_ARRAY_FILLED_DATA = 48;
  int NEW_INSTANCE = 49;
  int NOT = 50;
  int NUMBER_CONVERSION = 51;
  int OR = 52;
  int POP = 53;
  int REM = 54;
  int RETURN = 55;
  int SHL = 56;
  int SHR = 57;
  int STATIC_GET = 58;
  int STATIC_PUT = 59;
  int STORE = 60;
  int STRING_SWITCH = 61;
  int SUB = 62;
  int SWAP = 63;
  int THROW = 64;
  int USHR = 65;
  int XOR = 66;
  int UNINITIALIZED_THIS_LOCAL_READ = 67;
}
