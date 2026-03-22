# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

- Added a Temurin-only runtime check and updated docs to focus on Android 11–15
  compatibility for Java 9 inputs.
- Added native incremental mode with a persistent cache for unchanged JAR
  entries.
- Made `ProcessHandle` avoid direct `java.lang.management` dependencies for
  Android compatibility.
- Added a `--class-path` option so the desugarer can resolve `InputStream`
  subclasses from dependency jars/directories when remapping Java 9 stream IO
  calls.
- Added Java 10 API coverage for `Collectors.toUnmodifiable*` and
  `Optional.orElseThrow()` backports.
- Added a framework-style `MethodTransform` extension hook for custom remapping
  rules.

---

## [1.0.0] - 2026-03-17

### Added

- **`Java9ToJava8Desugarer`** – main entry point that opens an input JAR,
  passes every `.class` entry through the transformation pipeline, and writes
  a Java 8-compatible output JAR. Optionally bundles compiled `j9compat`
  backport classes into the output JAR.
- **`ClassDesugarer`** – ASM `ClassVisitor` that:
  - Downgrades the class-file major version from 53 (Java 9) to 52 (Java 8).
  - Removes `ACC_PRIVATE` from interface methods so the Java 8 verifier
    accepts them.
  - Delegates every method body to `MethodDesugarer`.
- **`MethodDesugarer`** – ASM `MethodVisitor` that intercepts Java 9-only
  API call sites and rewrites them to equivalent `j9compat` backport calls,
  covering:
  - `List.of`, `Set.of`, `Map.of`, `Map.ofEntries`, `Map.entry`, and all
    `copyOf` variants → `CollectionBackport`
  - `Stream.takeWhile`, `dropWhile`, `ofNullable`, 3-arg `iterate` →
    `StreamBackport`
  - `Optional.ifPresentOrElse`, `or`, `stream` → `OptionalBackport`
  - `InputStream.transferTo`, `readAllBytes`, `readNBytes` → `IOBackport`
  - `Objects.requireNonNullElse`, `requireNonNullElseGet`, `checkIndex`,
    `checkFromToIndex`, `checkFromIndexSize` → `ObjectsBackport`
  - `CompletableFuture.orTimeout`, `completeOnTimeout`, `failedFuture`,
    `completedStage`, `failedStage`, `copy` → `CompletableFutureBackport`
- **`j9compat` runtime backport library** – pure Java 8 implementations of
  every Java 9 API redirected by the desugarer:
  - `CollectionBackport` – immutable-collection factory methods with
    null-rejection and duplicate-rejection semantics matching Java 9.
  - `StreamBackport` – `takeWhile`, `dropWhile`, `ofNullable`, and the
    three-argument `iterate` with a termination predicate.
  - `OptionalBackport` – `ifPresentOrElse`, `or`, and `stream`.
  - `IOBackport` – `transferTo`, `readAllBytes`, and `readNBytes`.
  - `ObjectsBackport` – `requireNonNullElse`, `requireNonNullElseGet`,
    `checkIndex`, `checkFromToIndex`, and `checkFromIndexSize`.
  - `CompletableFutureBackport` – `orTimeout`, `completeOnTimeout`,
    `failedFuture`, `completedStage`, `failedStage`, and `copy`, using a
    shared daemon-thread scheduled executor for timeout support.
- **Backport unit-test suite** (`src/test/`) – lightweight test runner and
  one test class per backport component
  (`CollectionBackportTest`, `StreamBackportTest`, `OptionalBackportTest`,
  `IOBackportTest`, `ObjectsBackportTest`, `CompletableFutureBackportTest`).
- **GitHub Actions CI pipeline** (`.github/workflows/desugar-java9-to-java8.yml`):
  - `build-desugarer` job – compiles the tool and backport library with
    `-source 8 -target 8` and packages a fat JAR (ASM bundled).
  - `test-backport` job – compiles and runs the backport unit tests on Java 8.
  - `self-test` job – compiles a synthetic Java 9 sample class, desugars it,
    and verifies the output is at class-file major version 52 with `javap`.
  - `desugar-release-jar` job – triggered on `workflow_dispatch` to download
    and desugar a JAR attached to the latest GitHub release.
- **ASM 9.4 libraries** bundled in the repository root
  (`asm-9.4.jar`, `asm-commons-9.4.jar`, `asm-tree-9.4.jar`).
