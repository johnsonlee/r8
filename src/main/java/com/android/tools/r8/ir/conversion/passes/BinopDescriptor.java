// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.utils.BitUtils.ALL_BITS_SET_MASK;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Shl;
import com.android.tools.r8.ir.code.Shr;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Ushr;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.Xor;

/**
 * A Binop descriptor describes left and right identity and absorbing element of binop. <code>
 *  In a space K, for a binop *:
 * - i is left identity if for each x in K, i * x = x.
 * - i is right identity if for each x in K, x * i = x.
 * - a is left absorbing if for each x in K, a * x = a.
 * - a is right absorbing if for each x in K, x * a = a.
 * In a space K, a binop * is associative if for each x,y,z in K, (x * y) * z = x * (y * z).
 * </code>
 */
enum BinopDescriptor {
  ADD(true) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return Add.create(numericType, dest, left, right);
    }

    @Override
    Integer leftIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    int evaluate(int left, int right) {
      return left + right;
    }

    @Override
    long evaluate(long left, long right) {
      return left + right;
    }
  },
  SUB(false) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return new Sub(numericType, dest, left, right);
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    int evaluate(int left, int right) {
      return left - right;
    }

    @Override
    long evaluate(long left, long right) {
      return left - right;
    }
  },
  MUL(true) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return Mul.create(numericType, dest, left, right);
    }

    @Override
    Integer leftIdentity(boolean isBooleanValue) {
      return 1;
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 1;
    }

    @Override
    Integer leftAbsorbing(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer rightAbsorbing(boolean isBooleanValue) {
      return 0;
    }

    @Override
    int evaluate(int left, int right) {
      return left * right;
    }

    @Override
    long evaluate(long left, long right) {
      return left * right;
    }
  },
  // The following two can be improved if we handle ZeroDivide.
  DIV(false) {
    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 1;
    }
  },
  REM(false),
  AND(true) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return And.create(numericType, dest, left, right);
    }

    @Override
    Integer leftIdentity(boolean isBooleanValue) {
      return allBitsSet(isBooleanValue);
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return allBitsSet(isBooleanValue);
    }

    @Override
    Integer leftAbsorbing(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer rightAbsorbing(boolean isBooleanValue) {
      return 0;
    }

    @Override
    int evaluate(int left, int right) {
      return left & right;
    }

    @Override
    long evaluate(long left, long right) {
      return left & right;
    }
  },
  OR(true) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return Or.create(numericType, dest, left, right);
    }

    @Override
    Integer leftIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer leftAbsorbing(boolean isBooleanValue) {
      return allBitsSet(isBooleanValue);
    }

    @Override
    Integer rightAbsorbing(boolean isBooleanValue) {
      return allBitsSet(isBooleanValue);
    }

    @Override
    int evaluate(int left, int right) {
      return left | right;
    }

    @Override
    long evaluate(long left, long right) {
      return left | right;
    }
  },
  XOR(true) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return Xor.create(numericType, dest, left, right);
    }

    @Override
    Integer leftIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    int evaluate(int left, int right) {
      return left ^ right;
    }

    @Override
    long evaluate(long left, long right) {
      return left ^ right;
    }
  },
  SHL(false) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return new Shl(numericType, dest, left, right);
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer leftAbsorbing(boolean isBooleanValue) {
      return 0;
    }

    @Override
    boolean isShift() {
      return true;
    }
  },
  SHR(false) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return new Shr(numericType, dest, left, right);
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer leftAbsorbing(boolean isBooleanValue) {
      return 0;
    }

    @Override
    boolean isShift() {
      return true;
    }
  },
  USHR(false) {
    @Override
    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      return new Ushr(numericType, dest, left, right);
    }

    @Override
    Integer rightIdentity(boolean isBooleanValue) {
      return 0;
    }

    @Override
    Integer leftAbsorbing(boolean isBooleanValue) {
      return 0;
    }

    @Override
    boolean isShift() {
      return true;
    }
  };

  final boolean associativeAndCommutative;

  BinopDescriptor(boolean associativeAndCommutative) {
    this.associativeAndCommutative = associativeAndCommutative;
  }

  Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
    throw new Unreachable();
  }

  Integer allBitsSet(boolean isBooleanValue) {
    return isBooleanValue ? 1 : ALL_BITS_SET_MASK;
  }

  Integer leftIdentity(boolean isBooleanValue) {
    return null;
  }

  Integer rightIdentity(boolean isBooleanValue) {
    return null;
  }

  Integer leftAbsorbing(boolean isBooleanValue) {
    return null;
  }

  Integer rightAbsorbing(boolean isBooleanValue) {
    return null;
  }

  int evaluate(int left, int right) {
    throw new Unreachable();
  }

  long evaluate(long left, long right) {
    throw new Unreachable();
  }

  boolean isShift() {
    return false;
  }
}
