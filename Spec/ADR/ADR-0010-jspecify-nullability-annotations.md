# ADR-0010: JSpecify Nullability Annotations

## Status

Accepted

## Context

Null pointer exceptions remain a significant source of runtime errors in Java applications. While `Objects.requireNonNull()` provides runtime protection, it does not enable static analysis or communicate nullability intent through method signatures.

The Java ecosystem has historically lacked a standard for nullability annotations, leading to fragmentation across libraries (JSR-305, JetBrains annotations, Checker Framework, etc.). JSpecify is a collaborative effort by major industry players (Google, JetBrains, Oracle, and others) to establish a single, well-specified standard for nullability annotations in Java.

JSpecify provides:

- **@NullMarked**: Declares that a scope (package, class, or method) treats unannotated type usages as non-null by default
- **@Nullable**: Explicitly marks type usages that may be null
- **@NullUnmarked**: Opts out of null-marked semantics for a specific scope

This approach enables:

1. Static analysis by IDEs and tools (IntelliJ IDEA, Error Prone, NullAway)
2. Clear API contracts for nullability
3. Gradual adoption with package-level opt-in

## Decision

All Java modules in iDispatchX shall use JSpecify nullability annotations with the following conventions:

### 1. Package-Level @NullMarked

Every package shall have a `package-info.java` file declaring `@NullMarked`:

```java
@NullMarked
package net.pkhapps.idispatchx.cad.domain.model;

import org.jspecify.annotations.NullMarked;
```

This establishes non-null as the default for all type usages within the package.

### 2. Explicit @Nullable for Nullable Types

Parameters, return values, and fields that can legitimately be null shall be annotated with `@Nullable`:

```java
public @Nullable Incident findById(IncidentId id) {
    // May return null if not found
}

public void setNote(@Nullable String note) {
    // Note is optional
}
```

### 3. No Annotation for Non-Null Types

Under `@NullMarked`, unannotated type usages are implicitly non-null. Explicit `@NonNull` is not required and should not be used.

### 4. Maven Dependency

Add JSpecify to the parent POM as a provided dependency:

```xml
<dependency>
    <groupId>org.jspecify</groupId>
    <artifactId>jspecify</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 5. Runtime Checks Remain

`Objects.requireNonNull()` shall continue to be used for runtime validation at public API boundaries. JSpecify annotations are primarily for static analysis and documentation.

## Consequences

### Benefits

- **Compile-time safety**: Static analysis tools can detect potential null pointer exceptions before runtime
- **Clear API contracts**: Method signatures explicitly communicate nullability expectations
- **IDE support**: Enhanced code completion, warnings, and inspections in IntelliJ IDEA and other IDEs
- **Industry standard**: JSpecify is backed by major vendors and is positioned to become the de facto standard
- **Gradual adoption**: Package-level `@NullMarked` allows incremental rollout

### Trade-offs

- **Additional boilerplate**: Every package requires a `package-info.java` file
- **Annotation maintenance**: Developers must remember to annotate nullable types
- **Tool configuration**: Build tools and IDEs may require configuration to leverage annotations
- **No runtime enforcement**: JSpecify annotations have no effect at runtime; they are purely for static analysis

### Migration Path

Existing code shall be migrated incrementally:

1. Add JSpecify dependency to parent POM
2. Create `package-info.java` files with `@NullMarked` for each package
3. Add `@Nullable` annotations to existing nullable parameters and return values
4. Configure static analysis tools (Error Prone, NullAway) as optional quality gates

## Cross-References

- [ADR-0007: Domain Primitives](ADR-0007-domain-primitives.md) — Domain primitives shall follow nullability conventions
- [ADR-0008: CAD Server Ports-and-Adapters Architecture](ADR-0008-cad-server-ports-and-adapters.md) — Port interfaces shall use nullability annotations
- [Implementation README](../../Implementation/README.md) — Build configuration and dependencies
