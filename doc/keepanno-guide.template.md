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


[[[TOC]]]


## [Introduction](introduction)

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


## [Build configuration](build-configuration)

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


## [Annotating code using reflection](using-reflection)

The keep annotation library defines a family of annotations depending on your
use case. You should generally prefer `@UsesReflection` where applicable.
Common uses of reflection are to lookup fields and methods on classes. Examples
of such use cases are detailed below.


### [Invoking methods](using-reflection-methods)

[[[INCLUDE DOC:UsesReflectionOnVirtualMethod]]]

[[[INCLUDE CODE:UsesReflectionOnVirtualMethod]]]


### [Accessing fields](using-reflection-fields)

[[[INCLUDE DOC:UsesReflectionFieldPrinter]]]

[[[INCLUDE CODE:UsesReflectionFieldPrinter]]]

### [Accessing annotations](using-reflection-annotations)

[[[INCLUDE DOC:UsesReflectionOnAnnotations]]]

[[[INCLUDE CODE:UsesReflectionOnAnnotations]]]

If the annotations that need to be kept are not runtime
visible annotations, then you must specify that by including the `RetentionPolicy.CLASS` value in the
`@AnnotationPattern#retention` property.
An annotation is runtime visible if its definition is explicitly annotated with
`Retention(RetentionPolicy.RUNTIME)`.



## [Annotating code used by reflection (or via JNI)](used-by-reflection)

Sometimes reflecting code cannot be annotated. For example, the reflection can
be done in native code or in a library outside your control. In such cases you
can annotate the code that is being used by reflection with either
`@UsedByReflection` or `@UsedByNative`. These two annotations are equivalent.
Use the one that best matches why the annotation is needed.

Let's consider some code with reflection outside our control.
[[[INCLUDE DOC:UsedByReflectionFieldPrinterOnFields]]]

[[[INCLUDE CODE:UsedByReflectionFieldPrinterOnFields]]]

[[[INCLUDE DOC:UsedByReflectionFieldPrinterOnClass]]]

[[[INCLUDE CODE:UsedByReflectionFieldPrinterOnClass]]]

[[[INCLUDE DOC:UsedByReflectionFieldPrinterConditional]]]

[[[INCLUDE CODE:UsedByReflectionFieldPrinterConditional]]]


## [Annotating APIs](apis)

If your code is being shrunk before release as a library, then you need to keep
the API surface. For that you should use the `@KeepForApi` annotation.

[[[INCLUDE DOC:ApiClass]]]

[[[INCLUDE CODE:ApiClass]]]

[[[INCLUDE DOC:ApiClassMemberAccess]]]

[[[INCLUDE CODE:ApiClassMemberAccess]]]

[[[INCLUDE DOC:ApiMember]]]

[[[INCLUDE CODE:ApiMember]]]


## [Constraints](constraints)

When an item is kept (e.g., items matched by `@KeepTarget` or annotated by
`@UsedByReflection` or `@KeepForApi`) you can additionally specify constraints
about what properties of that item must be kept. Typical constraints are to keep
the items *name* or its ability to be reflectively *looked up*. You may also be
interested in keeping the generic signature of an item or annotations associated
with it.

### [Defaults](constraints-defaults)

By default the constraints are to retain the item's name, its ability to be
looked-up as well as its normal usage. Its normal usage is:

- to be instantiated, for class items;
- to be invoked, for method items; and
- to be get and/or set, for field items.

[[[INCLUDE DOC:UsesReflectionFieldPrinterWithConstraints]]]

[[[INCLUDE CODE:UsesReflectionFieldPrinterWithConstraints]]]


### [Generic signatures](constraints-signatures)

The generic signature information of an item is not kept by default, and
requires adding constraints to the targeted items.

[[[INCLUDE DOC:GenericSignaturePrinter]]]

[[[INCLUDE CODE:GenericSignaturePrinter]]]


## [Migrating rules to annotations](migrating-rules)

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

When migrating it is preferable to use `@UsesReflection` instead of
`@UsedByReflection`. For very general rules it might not be easy or worth it to
migrate without completely reevaluating the rule. If one still wants to replace
it by annotations, the general `@KeepEdge` can be used to define a context
independent keep annotation.

[[[INCLUDE DOC:KeepMainMethods]]]

[[[INCLUDE CODE:KeepMainMethods]]]


## [My use case is not covered!](other-uses)

The annotation library is in active development and not all use cases are
described here or supported. Reach out to the R8 team by
[filing a new issue in our tracker](https://issuetracker.google.com/issues/new?component=326788).
Describe your use case and we will look at how best to support it.


## [Troubleshooting](troubleshooting)

If an annotation is not working as expected it may be helpful to inspect the
rules that have been extracted for the annotation. This can be done by
inspecting the configuration output of the shrinker. For R8 you can use the
command line argument `--pg-conf-output <path>` to emit the full configuration
used by R8.
