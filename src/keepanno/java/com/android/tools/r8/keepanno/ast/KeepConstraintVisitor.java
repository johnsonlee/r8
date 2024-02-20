// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepConstraint.Annotation;
import com.android.tools.r8.keepanno.ast.KeepConstraint.ClassInstantiate;
import com.android.tools.r8.keepanno.ast.KeepConstraint.ClassOpenHierarchy;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldGet;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldReplace;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldSet;
import com.android.tools.r8.keepanno.ast.KeepConstraint.Lookup;
import com.android.tools.r8.keepanno.ast.KeepConstraint.MethodInvoke;
import com.android.tools.r8.keepanno.ast.KeepConstraint.MethodReplace;
import com.android.tools.r8.keepanno.ast.KeepConstraint.Name;
import com.android.tools.r8.keepanno.ast.KeepConstraint.NeverInline;
import com.android.tools.r8.keepanno.ast.KeepConstraint.VisibilityRelax;
import com.android.tools.r8.keepanno.ast.KeepConstraint.VisibilityRestrict;

public abstract class KeepConstraintVisitor {

  public abstract void onLookup(Lookup constraint);

  public abstract void onName(Name constraint);

  public abstract void onVisibilityRelax(VisibilityRelax constraint);

  public abstract void onVisibilityRestrict(VisibilityRestrict constraint);

  public abstract void onNeverInline(NeverInline constraint);

  public abstract void onClassInstantiate(ClassInstantiate constraint);

  public abstract void onClassOpenHierarchy(ClassOpenHierarchy constraint);

  public abstract void onMethodInvoke(MethodInvoke constraint);

  public abstract void onMethodReplace(MethodReplace constraint);

  public abstract void onFieldGet(FieldGet constraint);

  public abstract void onFieldSet(FieldSet constraint);

  public abstract void onFieldReplace(FieldReplace constraint);

  public abstract void onAnnotation(Annotation constraint);
}
