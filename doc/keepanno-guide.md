[comment]: <> (DO NOT EDIT - GENERATED FILE)
[comment]: <> (Changes should be made in doc/keepanno-guide.template.md)

# Guide to Keep Annotations

## Disclaimer

The annotation library described here is in development and considered to be in
its prototype phase. As such it is not yet feature complete, but we are actively
working on supporting all of the use cases we know of. Once the design exits the
prototype phase, it is intended to move to an R8 independent library as part of
androidx. All feedback: criticism, comments and suggestions are very welcome!

[File new feature requests and
bugs](https://issuetracker.google.com/issues/new?component=326788) in the
[R8 component](https://issuetracker.google.com/issues?q=status:open%20componentid:326788).


## Table of contents

- [Introduction](#introduction)
- [Build configuration](#build-configuration)
- [Annotating code using reflection](#using-reflection)
- [Annotating code used by reflection (or via JNI)](#used-by-reflection)
- [Annotating APIs](#apis)
- [Migrating rules to annotations](#migrating-rules)
- [My use case is not covered!](#other-uses)
- [Troubleshooting](#troubleshooting)



## Introduction<a id="introduction"></a>

When using a Java/Kotlin shrinker such as R8 or Proguard, developers must inform
the shrinker about parts of the program that are used either externally from the
program itself or internally via reflection and therefore must be kept.

Traditionally these aspects would be kept by writing keep rules in a
configuration file and passing that to the shrinker.

The keep annotations described in this document represent an alternative method
using Java annotations. The motivation for using these annotations is foremost
to place the description of what to keep closer to the program point using
reflective behavior. Doing so more directly connects the reflective code with
the keep specification and makes it easier to maintain as the code develops. In
addition, the annotations are defined independent from keep rules and have a
hopefully more clear and direct meaning.


## Build configuration<a id="build-configuration"></a>

To use the keep annotations your build must include the library of
annotations. It is currently built as part of each R8 build and if used with R8,
you should use the matching version. You can find all archived builds at:

```
https://storage.googleapis.com/r8-releases/raw/<version>/keepanno-annotations.jar
```

Thus you may obtain version `8.2.34` by running:

```
wget https://storage.googleapis.com/r8-releases/raw/8.2.34/keepanno-annotations.jar
```

You will then need to set the system property
`com.android.tools.r8.enableKeepAnnotations` to instruct R8 to make use of the
annotations when shrinking:

```
java -Dcom.android.tools.r8.enableKeepAnnotations=1 \
  -cp r8.jar com.android.tools.r8.R8 \
  # ... the rest of your R8 compilation command here ...
```

## Annotating code using reflection<a id="using-reflection"></a>

The keep annotation library defines a family of annotations depending on your
use case. You should generally prefer [@UsesReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsesReflection.html) where applicable.

For example, if your program is reflectively invoking a method, you
should annotate the method that is doing the reflection. The annotation must describe the
assumptions the reflective code makes.

In the following example, the method `foo` is looking up the method with the name
`hiddenMethod` on objects that are instances of `BaseClass`. It is then invoking the method with
no other arguments than the receiver.

The assumptions the code makes are that all methods with the name
`hiddenMethod` and the empty list of parameters must remain valid for `getDeclaredMethod` if they
are objects that are instances of the class `BaseClass` or subclasses thereof.


```
static class MyClass {

  @UsesReflection({
    @KeepTarget(
        instanceOfClassConstant = BaseClass.class,
        methodName = "hiddenMethod",
        methodParameters = {})
  })
  public void foo(BaseClass base) throws Exception {
    base.getClass().getDeclaredMethod("hiddenMethod").invoke(base);
  }
}
```



## Annotating code used by reflection (or via JNI)<a id="used-by-reflection"></a>

TODO


## Annotating APIs<a id="apis"></a>

TODO


## Migrating rules to annotations<a id="migrating-rules"></a>

There is no automatic migration of keep rules. Keep annotations often invert the
direction and rules have no indication of where the reflection is taking
place or why. Thus, migrating existing keep rules requires user involvement.
Keep rules also have a tendency to be very general, matching a large
number of classes and members. Often the rules are much too broad and are
keeping more than needed which will have a negative impact on the shrinkers
ability to reduce size.

First step in converting a rule is to determine the purpose of the rule. Is it
API surface or is it reflection? Note that a very general rule may be covering
several use cases and even a mix of both reflection and API usage.

When migrating it is preferable to use [@UsesReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsesReflection.html) instead of
[@UsedByReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByReflection.html). For very general rules it might not be easy or worth it to
migrate without completely reevaluating the rule. If one still wants to replace
it by annotations, the general [@KeepEdge](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepEdge.html) can be used to define a context
independent keep annotation.

For example, to keep all main methods in the program one could use:


```
@KeepEdge(
    consequences = {
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          methodName = "main",
          methodReturnType = "void",
          methodParameters = {"java.lang.String[]"},
          methodAccess = {MethodAccessFlags.PUBLIC, MethodAccessFlags.STATIC})
    })
public class SomeClass {
  // ...
}
```



## My use case is not covered!<a id="other-uses"></a>

The annotation library is in active development and not all use cases are
described here or supported. Reach out to the R8 team by
[filing a new issue in our tracker](https://issuetracker.google.com/issues/new?component=326788).
Describe your use case and we will look at how best to support it.


## Troubleshooting<a id="troubleshooting"></a>

If an annotation is not working as expected it may be helpful to inspect the
rules that have been extracted for the annotation. This can be done by
inspecting the configuration output of the shrinker. For R8 you can use the
command line argument `--pg-conf-output <path>` to emit the full configuration
used by R8.
