// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.ClassReferenceUtils.getClassReferenceComparator;
import static com.android.tools.r8.utils.TypeReferenceUtils.getTypeReferenceComparator;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import java.util.Comparator;

public class FieldReferenceUtils {

  private static final Comparator<FieldReference> COMPARATOR =
      (field, other) -> {
        CompareResult holderClassCompareResult =
            CompareResult.compare(
                field.getHolderClass(), other.getHolderClass(), getClassReferenceComparator());
        if (!holderClassCompareResult.isEqual()) {
          return holderClassCompareResult.getComparisonResult();
        }
        CompareResult fieldNameCompareResult =
            CompareResult.compare(field.getFieldName(), other.getFieldName());
        if (!fieldNameCompareResult.isEqual()) {
          return fieldNameCompareResult.getComparisonResult();
        }
        return getTypeReferenceComparator().compare(field.getFieldType(), other.getFieldType());
      };

  public static int compare(FieldReference fieldReference, ClassReference other) {
    return ClassReferenceUtils.compare(other, fieldReference) * -1;
  }

  public static int compare(FieldReference fieldReference, FieldReference other) {
    return getFieldReferenceComparator().compare(fieldReference, other);
  }

  public static int compare(FieldReference fieldReference, MethodReference other) {
    int comparisonResult =
        ClassReferenceUtils.compare(fieldReference.getHolderClass(), other.getHolderClass());
    return comparisonResult != 0 ? comparisonResult : -1;
  }

  public static FieldReference fieldFromField(Class<?> clazz, String name) {
    try {
      return Reference.fieldFromField(clazz.getDeclaredField(name));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  public static Comparator<FieldReference> getFieldReferenceComparator() {
    return COMPARATOR;
  }

  public static FieldReference parseSmaliString(String classAndFieldDescriptor) {
    int arrowStartIndex = classAndFieldDescriptor.indexOf("->");
    if (arrowStartIndex >= 0) {
      return parseSmaliString(classAndFieldDescriptor, arrowStartIndex);
    }
    return null;
  }

  public static FieldReference parseSmaliString(
      String classAndFieldDescriptor, int arrowStartIndex) {
    String classDescriptor = classAndFieldDescriptor.substring(0, arrowStartIndex);
    ClassReference fieldHolder = ClassReferenceUtils.parseClassDescriptor(classDescriptor);
    if (fieldHolder == null) {
      return null;
    }
    int fieldNameStartIndex = arrowStartIndex + 2;
    String fieldNameAndType = classAndFieldDescriptor.substring(fieldNameStartIndex);
    int fieldNameEndIndex = fieldNameAndType.indexOf(':');
    if (fieldNameEndIndex <= 0) {
      return null;
    }
    String fieldName = fieldNameAndType.substring(0, fieldNameEndIndex);
    String fieldTypeDescriptor = fieldNameAndType.substring(fieldNameEndIndex + 1);
    TypeReference fieldType = Reference.returnTypeFromDescriptor(fieldTypeDescriptor);
    return Reference.field(fieldHolder, fieldName, fieldType);
  }

  public static String toSourceString(FieldReference fieldReference) {
    return fieldReference.getFieldType().getTypeName()
        + " "
        + fieldReference.getHolderClass().getTypeName()
        + "."
        + fieldReference.getFieldName();
  }
}
