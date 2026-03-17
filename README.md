# Java 9 → Java 8 Desugarer

A bytecode-level tool that transforms a JAR compiled with **Java 9** into a
fully **Java 8-compatible** JAR, including a bundled runtime backport library
(`j9compat`) that provides Java 8 implementations of every Java 9 API used.

**Compatibility focus:** runs on **Eclipse Temurin (Adoptium)** and targets
**Android 11–15 (API 30–34)** deployments with Java 9 bytecode input.

---

## What it does

| Transformation | Description |
|----------------|-------------|
| **Class-file version downgrade** | Changes class file major version from 53 (Java 9) to 52 (Java 8). |
| **Module-info retention** | Downgrades `module-info.class` so module metadata is preserved even though Java 8 ignores JPMS. |
| **Private interface methods** | Java 9 allows `private` methods in interfaces. This tool makes them package-private so the Java 8 verifier accepts them. |
| **String concatenation** | Rewrites `invokedynamic` StringConcatFactory concatenation to `StringBuilder` bytecode. |
| **Collection factory methods** | Redirects `List.of()`, `Set.of()`, `Map.of()`, `Map.ofEntries()`, `Map.entry()`, and all `copyOf()` variants to `j9compat.CollectionBackport`. |
| **Stream API additions** | Redirects `takeWhile()`, `dropWhile()`, `ofNullable()`, and the three-argument `iterate()` to `j9compat.StreamBackport`. |
| **Primitive Stream additions** | Redirects `IntStream/LongStream/DoubleStream.takeWhile()`, `dropWhile()`, and the three-argument `iterate()` to `j9compat.*StreamBackport`. |
| **Collectors additions** | Redirects `Collectors.filtering()` and `Collectors.flatMapping()` to `j9compat.CollectorsBackport`. |
| **Optional API additions** | Redirects `ifPresentOrElse()`, `or()`, and `stream()` to `j9compat.OptionalBackport`. |
| **Optional primitive additions** | Redirects `OptionalInt/Long/Double.ifPresentOrElse()` and `.stream()` to `j9compat.Optional*Backport`. |
| **InputStream additions** | Redirects `transferTo()`, `readAllBytes()`, and `readNBytes()` to `j9compat.IOBackport`. |
| **Objects additions** | Redirects `requireNonNullElse()`, `requireNonNullElseGet()`, `checkIndex()`, `checkFromToIndex()`, and `checkFromIndexSize()` to `j9compat.ObjectsBackport`. |
| **CompletableFuture additions** | Redirects `orTimeout()`, `completeOnTimeout()`, `failedFuture()`, `completedStage()`, `failedStage()`, `minimalCompletionStage()`, `newIncompleteFuture()`, and `copy()` to `j9compat.CompletableFutureBackport`. |
| **Process/Stack/Flow types** | Remaps `ProcessHandle`, `StackWalker`, `Flow`, and `SubmissionPublisher` to Java 8-compatible `j9compat` implementations. |

---

## Repository layout

```
.
├── asm-9.4.jar                         ASM core (bytecode library)
├── asm-commons-9.4.jar                 ASM commons
├── asm-tree-9.4.jar                    ASM tree API
│
├── src/
│   ├── desugarer/
│   │   ├── Java9ToJava8Desugarer.java  Main tool – processes JARs
│   │   ├── ClassDesugarer.java         ClassVisitor (version + interface priv)
│   │   └── MethodDesugarer.java        MethodVisitor (API call remapping)
│   │
│   └── j9compat/                       Runtime backport library (Java 8)
│       ├── CollectionBackport.java     List/Set/Map factory methods
│       ├── StreamBackport.java         Stream additions
│       ├── IntStreamBackport.java      IntStream additions
│       ├── LongStreamBackport.java     LongStream additions
│       ├── DoubleStreamBackport.java   DoubleStream additions
│       ├── OptionalBackport.java       Optional additions
│       ├── OptionalIntBackport.java    OptionalInt additions
│       ├── OptionalLongBackport.java   OptionalLong additions
│       ├── OptionalDoubleBackport.java OptionalDouble additions
│       ├── IOBackport.java             InputStream additions
│       ├── ObjectsBackport.java        Objects additions
│       ├── CollectorsBackport.java     Collectors additions
│       ├── CompletableFutureBackport.java  CompletableFuture additions
│       ├── ProcessHandle.java          ProcessHandle backport
│       ├── StackWalker.java            StackWalker backport
│       ├── Flow.java                   Flow (Reactive Streams) interfaces
│       └── SubmissionPublisher.java    Reactive Streams publisher implementation
│
└── .github/workflows/
    └── desugar-java9-to-java8.yml      CI pipeline
```

---

## Building

```bash
# 1. Compile the j9compat backport library
mkdir -p build/backport
javac -source 8 -target 8 \
  -cp asm-9.4.jar:asm-commons-9.4.jar:asm-tree-9.4.jar \
  -d build/backport \
  src/j9compat/CollectionBackport.java \
  src/j9compat/StreamBackport.java \
  src/j9compat/IntStreamBackport.java \
  src/j9compat/LongStreamBackport.java \
  src/j9compat/DoubleStreamBackport.java \
  src/j9compat/OptionalBackport.java \
  src/j9compat/OptionalIntBackport.java \
  src/j9compat/OptionalLongBackport.java \
  src/j9compat/OptionalDoubleBackport.java \
  src/j9compat/IOBackport.java \
  src/j9compat/ObjectsBackport.java \
  src/j9compat/CompletableFutureBackport.java \
  src/j9compat/CollectorsBackport.java \
  src/j9compat/ProcessHandle.java \
  src/j9compat/StackWalker.java \
  src/j9compat/Flow.java \
  src/j9compat/SubmissionPublisher.java

# 2. Compile the desugarer tool
mkdir -p build/desugarer
javac -source 8 -target 8 \
  -cp asm-9.4.jar:asm-commons-9.4.jar:asm-tree-9.4.jar \
  -d build/desugarer \
  src/desugarer/Java9ToJava8Desugarer.java \
  src/desugarer/ClassDesugarer.java \
  src/desugarer/MethodDesugarer.java \
  src/desugarer/BackportRemapper.java

# 3. Build a fat JAR (ASM + desugarer classes in one JAR)
mkdir -p build/fatjar
cd build/fatjar
jar xf ../../asm-9.4.jar
jar xf ../../asm-commons-9.4.jar
jar xf ../../asm-tree-9.4.jar
cp -r ../desugarer/* .
cd ../..
jar cfe build/desugar9to8.jar desugarer.Java9ToJava8Desugarer -C build/fatjar .
```

---

## Usage

```
java -jar build/desugar9to8.jar [--incremental] [--cache-dir <dir>] <input-java9.jar> <output-java8.jar> [backport-classes-dir]
```

| Argument | Description |
|----------|-------------|
| `<input-java9.jar>` | JAR compiled with Java 9 (class version 53). |
| `<output-java8.jar>` | Path for the Java 8-compatible output JAR. |
| `[backport-classes-dir]` | Optional: directory containing compiled `j9compat/*.class` files. If provided, the backport classes are bundled into the output JAR. |
| `--incremental` | Enable incremental mode (reuse unchanged output entries). |
| `--cache-dir <dir>` | Cache directory for incremental mode (default: `build/.desugar-cache`). |

### Example

```bash
java -jar build/desugar9to8.jar \
  my-app-java9.jar \
  my-app-java8.jar \
  build/backport
```

The output JAR will:
- Have all class files at version 52 (Java 8).
- Contain the `j9compat.*` backport classes.
- Redirect every Java 9 API call to the corresponding backport method.

### Incremental mode

Incremental mode reuses the output bytes for unchanged JAR entries and writes
an on-disk cache alongside the output.

```bash
java -jar build/desugar9to8.jar --incremental --cache-dir build/.desugar-cache \
  my-app-java9.jar \
  my-app-java8.jar \
  build/backport
```

---

## Java 9 features covered

### Collection factory methods
All Java 9 immutable-collection factory methods are replaced with Java 8
`Collections.unmodifiableList/Set/Map` wrappers with identical null-rejection
and duplicate-rejection semantics.

```java
// Java 9                          // Desugared to
List.of("a", "b")              --> CollectionBackport.listOf("a", "b")
Set.of(1, 2, 3)                --> CollectionBackport.setOf(1, 2, 3)
Map.of("k", "v")               --> CollectionBackport.mapOf("k", "v")
Map.ofEntries(Map.entry(k, v)) --> CollectionBackport.mapOfEntries(...)
List.copyOf(coll)              --> CollectionBackport.listCopyOf(coll)
```

### Stream API
```java
stream.takeWhile(p)            --> StreamBackport.takeWhile(stream, p)
stream.dropWhile(p)            --> StreamBackport.dropWhile(stream, p)
Stream.ofNullable(t)           --> StreamBackport.ofNullable(t)
Stream.iterate(s, hasNext, f)  --> StreamBackport.iterate(s, hasNext, f)
```

### Primitive Stream API
```java
intStream.takeWhile(p)         --> IntStreamBackport.takeWhile(intStream, p)
longStream.dropWhile(p)        --> LongStreamBackport.dropWhile(longStream, p)
DoubleStream.iterate(s, h, f)  --> DoubleStreamBackport.iterate(s, h, f)
```

### Collectors API
```java
Collectors.filtering(p, c)     --> CollectorsBackport.filtering(p, c)
Collectors.flatMapping(f, c)   --> CollectorsBackport.flatMapping(f, c)
```

### Optional API
```java
opt.ifPresentOrElse(a, e)      --> OptionalBackport.ifPresentOrElse(opt, a, e)
opt.or(supplier)               --> OptionalBackport.or(opt, supplier)
opt.stream()                   --> OptionalBackport.stream(opt)
```

### Optional primitive API
```java
optInt.ifPresentOrElse(a, e)   --> OptionalIntBackport.ifPresentOrElse(optInt, a, e)
optLong.stream()               --> OptionalLongBackport.stream(optLong)
optDouble.stream()             --> OptionalDoubleBackport.stream(optDouble)
```

### InputStream API
```java
in.transferTo(out)             --> IOBackport.transferTo(in, out)
in.readAllBytes()              --> IOBackport.readAllBytes(in)
in.readNBytes(buf, off, len)   --> IOBackport.readNBytes(in, buf, off, len)
```

### Objects API
```java
Objects.requireNonNullElse(obj, def)        --> ObjectsBackport.requireNonNullElse(...)
Objects.requireNonNullElseGet(obj, supplier)--> ObjectsBackport.requireNonNullElseGet(...)
Objects.checkIndex(idx, len)                --> ObjectsBackport.checkIndex(...)
Objects.checkFromToIndex(from, to, len)     --> ObjectsBackport.checkFromToIndex(...)
Objects.checkFromIndexSize(from, size, len) --> ObjectsBackport.checkFromIndexSize(...)
```

### CompletableFuture API
```java
cf.orTimeout(1, SECONDS)             --> CompletableFutureBackport.orTimeout(cf, 1, SECONDS)
cf.completeOnTimeout(v, 1, SECONDS)  --> CompletableFutureBackport.completeOnTimeout(cf, v, 1, SECONDS)
CompletableFuture.failedFuture(ex)   --> CompletableFutureBackport.failedFuture(ex)
CompletableFuture.completedStage(v)  --> CompletableFutureBackport.completedStage(v)
CompletableFuture.failedStage(ex)    --> CompletableFutureBackport.failedStage(ex)
cf.minimalCompletionStage()          --> CompletableFutureBackport.minimalCompletionStage(cf)
cf.newIncompleteFuture()             --> CompletableFutureBackport.newIncompleteFuture(cf)
cf.copy()                            --> CompletableFutureBackport.copy(cf)
```

### Process/Stack/Flow types
```java
ProcessHandle.current()        --> j9compat.ProcessHandle.current()
StackWalker.getInstance()      --> j9compat.StackWalker.getInstance()
Flow.Publisher<T>              --> j9compat.Flow.Publisher<T>
SubmissionPublisher<T>         --> j9compat.SubmissionPublisher<T>
```

---

## How it works

The desugarer is built on **ASM 9.4** (ObjectWeb ASM bytecode library).

1. **`Java9ToJava8Desugarer`** opens the input JAR, iterates over every entry,
   and passes `.class` files through the transformation pipeline.
2. **`ClassDesugarer`** (a `ClassVisitor`) downgrades the class-file version and
   strips `ACC_PRIVATE` from interface methods.
3. **`MethodDesugarer`** (a `MethodVisitor`) intercepts every
   `visitMethodInsn()` call and rewrites matching Java 9 API calls:
   - *Static-to-static*: same descriptor, new owner/name, `isInterface=false`.
   - *Instance-to-static*: the receiver is prepended to the descriptor, and
     `INVOKEVIRTUAL`/`INVOKEINTERFACE` becomes `INVOKESTATIC`. The operand
     stack already holds the receiver below the arguments, so no extra
     instructions are needed.
4. The optional **backport directory** is bundled into the output JAR so the
   desugared code can resolve the `j9compat.*` classes at runtime.

---

## CI / GitHub Actions

The workflow in `.github/workflows/desugar-java9-to-java8.yml` runs on every
push and pull request to `main`.  It:

1. Compiles the desugarer tool and backport library with `javac -source 8 -target 8`.
2. Packages a fat JAR including ASM.
3. Compiles a small Java 9 sample class and desugars it.
4. Verifies the output class file is at major version 52 using `javap`.
5. Exposes the fat JAR and the sample output as build artefacts.

On manual (`workflow_dispatch`) runs you can supply a release-asset filename to
desugar any JAR attached to the latest release.

---

## Limitations

For a full list of known limitations, see [LIMITATIONS.md](LIMITATIONS.md).
