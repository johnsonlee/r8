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
  - [Invoking methods](#using-reflection-methods)
  - [Accessing fields](#using-reflection-fields)
- [Annotating code used by reflection (or via JNI)](#used-by-reflection)
- [Annotating APIs](#apis)
- [Migrating rules to annotations](#migrating-rules)
- [My use case is not covered!](#other-uses)
- [Troubleshooting](#troubleshooting)



## Introduction<a name="introduction"></a>

When using a Java/Kotlin shrinker such as R8 or Proguard, developers must inform
the shrinker about parts of the program that are used either externally from the
program itself or internally via reflection and therefore must be kept.

Traditionally these aspects would be kept by writing keep rules in a
configuration file and passing that to the shrinker.

The keep annotations described in this document represent an alternative method
using Java annotations. The motivation for using these annotations is foremost
to place the description of what to keep closer to the program point using
reflective behavior. Doing so more directly connects the reflective code with
the keep specification and makes it easier to maintain as the code develops.
Often the keep annotations are only in effect if the annotated method is used,
allowing more precise shrinking.  In addition, the annotations are defined
independent from keep rules and have a hopefully more clear and direct meaning.


## Build configuration<a name="build-configuration"></a>

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


## Annotating code using reflection<a name="using-reflection"></a>

The keep annotation library defines a family of annotations depending on your
use case. You should generally prefer [@UsesReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsesReflection.html) where applicable.
Common uses of reflection are to lookup fields and methods on classes. Examples
of such use cases are detailed below.


### Invoking methods<a name="using-reflection-methods"></a>

For example, if your program is reflectively invoking a method, you
should annotate the method that is doing the reflection. The annotation must describe the
assumptions the reflective code makes.

In the following example, the method `callHiddenMethod` is looking up the method with the name
`hiddenMethod` on objects that are instances of `BaseClass`. It is then invoking the method with
no other arguments than the receiver.

The assumptions the code makes are that all methods with the name
`hiddenMethod` and the empty list of parameters must remain valid for `getDeclaredMethod` if they
are objects that are instances of the class `BaseClass` or subclasses thereof.


```
public class MyHiddenMethodCaller {

  @UsesReflection({
    @KeepTarget(
        instanceOfClassConstant = BaseClass.class,
        methodName = "hiddenMethod",
        methodParameters = {})
  })
  public void callHiddenMethod(BaseClass base) throws Exception {
    base.getClass().getDeclaredMethod("hiddenMethod").invoke(base);
  }
}
```



### Accessing fields<a name="using-reflection-fields"></a>

For example, if your program is reflectively accessing the fields on a class, you should
annotate the method that is doing the reflection.

In the following example, the `printFieldValues` method takes in an object of
type `PrintableFieldInterface` and then looks for all the fields declared on the class
of the object.

The [@KeepTarget](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepTarget.html) describes these field targets. Since the printing only cares about preserving
the fields, the [@KeepTarget.kind](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepTarget.html#kind()) is set to [KeepItemKind.ONLY_FIELDS](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepItemKind.html#ONLY_FIELDS). Also, since printing
the field names and values only requires looking up the field, printing its name and getting
its value the [@KeepTarget.constraints](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepTarget.html#constraints()) are set to just [KeepConstraint.LOOKUP](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepConstraint.html#LOOKUP),
[KeepConstraint.NAME](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepConstraint.html#NAME) and [KeepConstraint.FIELD_GET](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepConstraint.html#FIELD_GET).


```
public class MyFieldValuePrinter {

  @UsesReflection({
    @KeepTarget(
        instanceOfClassConstant = PrintableFieldInterface.class,
        kind = KeepItemKind.ONLY_FIELDS,
        constraints = {KeepConstraint.LOOKUP, KeepConstraint.NAME, KeepConstraint.FIELD_GET})
  })
  public void printFieldValues(PrintableFieldInterface objectWithFields) throws Exception {
    for (Field field : objectWithFields.getClass().getDeclaredFields()) {
      System.out.println(field.getName() + " = " + field.get(objectWithFields));
    }
  }
}
```



## Annotating code used by reflection (or via JNI)<a name="used-by-reflection"></a>

Sometimes reflecting code cannot be annotated. For example, the reflection can
be done in native code or in a library outside your control. In such cases you
can annotate the code that is being used by reflection with either
[@UsedByReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByReflection.html) or [@UsedByNative](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByNative.html). These two annotations are equivalent.
Use the one that best matches why the annotation is needed.

Let's consider some code with reflection outside our control.
For example, the same field printing as in the above example might be part of a library.

In this example, the `MyClassWithFields` is a class you are passing to the
field-printing utility of the library. Since the library is reflectively accessing each field
we annotate them with the [@UsedByReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByReflection.html) annotation.

We could additionally add the [@UsedByReflection.constraints](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByReflection.html#constraints()) property as we did previously.
We elide it here for brevity.


```
public class MyClassWithFields implements PrintableFieldInterface {
  @UsedByReflection final int intField = 42;

  @UsedByReflection String stringField = "Hello!";
}

public static void run() throws Exception {
  new FieldValuePrinterLibrary().printFieldValues(new MyClassWithFields());
}
```


Rather than annotate the individual fields we can annotate the holder and add a specification
similar to the [@KeepTarget](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepTarget.html). The [@UsedByReflection.kind](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByReflection.html#kind()) specifies that only the fields are
used reflectively. In particular, the "field printer" example we are considering here does not
make reflective assumptions about the holder class, so we should not constrain it.

To be more precise let's add the [@UsedByReflection.constraints](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByReflection.html#constraints()) property now. This specifies
that the fields are looked up, their names are used/assumed and their values are read.


```
@UsedByReflection(
    kind = KeepItemKind.ONLY_FIELDS,
    constraints = {KeepConstraint.LOOKUP, KeepConstraint.NAME, KeepConstraint.FIELD_GET})
public class MyClassWithFields implements PrintableFieldInterface {
  final int intField = 42;
  String stringField = "Hello!";
}
```


Our use of [@UsedByReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByReflection.html) is still not as flexible as the original [@UsesReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsesReflection.html). In
particular, if we change our code to no longer have any call to the library method
`printFieldValues` the shrinker will still keep all of the fields on our annotated class.

This is because the [@UsesReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsesReflection.html) implicitly encodes as a precondition that the annotated
method is actually used in the program. If not, the [@UsesReflection](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsesReflection.html) annotation is not
"active".

Luckily we can specify the same precondition using [@UsedByReflection.preconditions](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/UsedByReflection.html#preconditions()).


```
@UsedByReflection(
    preconditions = {
      @KeepCondition(
          classConstant = FieldValuePrinterLibrary.class,
          methodName = "printFieldValues")
    },
    kind = KeepItemKind.ONLY_FIELDS,
    constraints = {KeepConstraint.LOOKUP, KeepConstraint.NAME, KeepConstraint.FIELD_GET})
public class MyClassWithFields implements PrintableFieldInterface {
  final int intField = 42;
  String stringField = "Hello!";
}
```



## Annotating APIs<a name="apis"></a>

If your code is being shrunk before release as a library, or if you have an API
surface that is used via dynamic loading at runtime, then you need to keep the
API surface. For that you should use the [@KeepForApi](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepForApi.html) annotation.

When annotating a class the default for [@KeepForApi](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepForApi.html) is to keep the class as well as all of its
public and protected members:


```
@KeepForApi
public class MyApi {
  public void thisPublicMethodIsKept() {
    /* ... */
  }

  protected void thisProtectedMethodIsKept() {
    /* ... */
  }

  void thisPackagePrivateMethodIsNotKept() {
    /* ... */
  }

  private void thisPrivateMethodIsNotKept() {
    /* ... */
  }
}
```


The default can be changed using the [@KeepForApi.memberAccess](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepForApi.html#memberAccess()) property:


```
@KeepForApi(
    memberAccess = {
      MemberAccessFlags.PUBLIC,
      MemberAccessFlags.PROTECTED,
      MemberAccessFlags.PACKAGE_PRIVATE
    })
```


The [@KeepForApi](https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/com/android/tools/r8/keepanno/annotations/KeepForApi.html) annotation can also be placed directly on members and avoid keeping
unannotated members. The holder class is implicitly kept. When annotating the members
directly, the access does not matter as illustrated here by annotating a package private method:


```
public class MyOtherApi {

  public void notKept() {
    /* ... */
  }

  @KeepForApi
  void isKept() {
    /* ... */
  }
}
```



## Migrating rules to annotations<a name="migrating-rules"></a>

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



## My use case is not covered!<a name="other-uses"></a>

The annotation library is in active development and not all use cases are
described here or supported. Reach out to the R8 team by
[filing a new issue in our tracker](https://issuetracker.google.com/issues/new?component=326788).
Describe your use case and we will look at how best to support it.


## Troubleshooting<a name="troubleshooting"></a>

If an annotation is not working as expected it may be helpful to inspect the
rules that have been extracted for the annotation. This can be done by
inspecting the configuration output of the shrinker. For R8 you can use the
command line argument `--pg-conf-output <path>` to emit the full configuration
used by R8.
