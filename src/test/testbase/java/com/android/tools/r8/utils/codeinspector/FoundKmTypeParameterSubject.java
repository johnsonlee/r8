package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.kotlin.KotlinFlagUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kotlin.metadata.KmTypeParameter;
import kotlin.metadata.KmVariance;

public class FoundKmTypeParameterSubject extends KmTypeParameterSubject {

  private final CodeInspector codeInspector;
  private final KmTypeParameter kmTypeParameter;

  public FoundKmTypeParameterSubject(CodeInspector codeInspector, KmTypeParameter kmTypeParameter) {
    this.codeInspector = codeInspector;
    this.kmTypeParameter = kmTypeParameter;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public int getId() {
    return kmTypeParameter.getId();
  }

  @Override
  public Map<String, Object> getFlags() {
    return KotlinFlagUtils.extractFlags(kmTypeParameter);
  }

  @Override
  public KmVariance getVariance() {
    return kmTypeParameter.getVariance();
  }

  @Override
  public List<KmTypeSubject> upperBounds() {
    return kmTypeParameter.getUpperBounds().stream()
        .map(kmType -> new KmTypeSubject(codeInspector, kmType))
        .collect(Collectors.toList());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FoundKmTypeParameterSubject)) {
      return false;
    }
    KmTypeParameter other = ((FoundKmTypeParameterSubject) obj).kmTypeParameter;
    if (!kmTypeParameter.getName().equals(other.getName())
        || kmTypeParameter.getId() != other.getId()
        || !(KotlinFlagUtils.extractFlags(kmTypeParameter)
            .equals(KotlinFlagUtils.extractFlags(other)))
        || kmTypeParameter.getVariance() != other.getVariance()) {
      return false;
    }
    if (kmTypeParameter.getUpperBounds().size() != other.getUpperBounds().size()) {
      return false;
    }
    for (int i = 0; i < kmTypeParameter.getUpperBounds().size(); i++) {
      if (!KmTypeSubject.areEqual(
          kmTypeParameter.getUpperBounds().get(i), other.getUpperBounds().get(i), true)) {
        return false;
      }
    }
    return true;
  }
}
