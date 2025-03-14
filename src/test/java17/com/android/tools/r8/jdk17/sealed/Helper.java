package com.android.tools.r8.jdk17.sealed;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class Helper {

  public static List<Class<?>> getSealedClasses() {
    return ImmutableList.of(Compiler.class, D8Compiler.class, R8Compiler.class, Main.class);
  }
}
