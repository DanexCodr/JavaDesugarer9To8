# Java 9 → Java 8 Desugarer

A bytecode-level tool that transforms a JAR compiled with **Java 9** into a
fully **Java 8-compatible** JAR, including a bundled runtime backport library
(`j9compat`) that provides Java 8 implementations of every Java 9 API used.

---

## What it does

| Transformation | Description |
|----------------|-------------|
| **Class-file version downgrade** | Changes class file major version from 53 (Java 9) to 52 (Java 8). |
| **Module-info removal** | Strips `module-info.class` entries; the JPMS module system has no Java 8 equivalent. |
| **Private interface methods** | Java 9 allows `private` methods in interfaces. This tool makes them package-private so the Java 8 verifier accepts them. |
| **Collection factory methods** | Redirects `List.of()`, `Set.of()`, `Map.of()`, `Map.ofEntries()`, `Map.entry()`, and all `copyOf()` variants to `j9compat.CollectionBackport`. |
| **Stream API additions** | Redirects `takeWhile()`, `dropWhile()`, `ofNullable()`, and the three-argument `iterate()` to `j9compat.StreamBackport`. |
| **Optional API additions** | Redirects `ifPresentOrElse()`, `or()`, and `stream()` to `j9compat.OptionalBackport`. |
| **InputStream additions** | Redirects `transferTo()`, `readAllBytes()`, and `readNBytes()` to `j9compat.IOBackport`. |
| **Objects additions** | Redirects `requireNonNullElse()`, `requireNonNullElseGet()`, `checkIndex()`, `checkFromToIndex()`, and `checkFromIndexSize()` to `j9compat.ObjectsBackport`. |
| **CompletableFuture additions** | Redirects `orTimeout()`, `completeOnTimeout()`, `failedFuture()`, `completedStage()`, `failedStage()`, and `copy()` to `j9compat.CompletableFutureBackport`. |

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
│       ├── OptionalBackport.java       Optional additions
│       ├── IOBackport.java             InputStream additions
│       ├── ObjectsBackport.java        Objects additions
│       └── CompletableFutureBackport.java  CompletableFuture additions
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
  src/j9compat/OptionalBackport.java \
  src/j9compat/IOBackport.java \
  src/j9compat/ObjectsBackport.java \
  src/j9compat/CompletableFutureBackport.java

# 2. Compile the desugarer tool
mkdir -p build/desugarer
javac -source 8 -target 8 \
  -cp asm-9.4.jar:asm-commons-9.4.jar:asm-tree-9.4.jar \
  -d build/desugarer \
  src/desugarer/Java9ToJava8Desugarer.java \
  src/desugarer/ClassDesugarer.java \
  src/desugarer/MethodDesugarer.java

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
java -jar build/desugar9to8.jar <input-java9.jar> <output-java8.jar> [backport-classes-dir]
```

| Argument | Description |
|----------|-------------|
| `<input-java9.jar>` | JAR compiled with Java 9 (class version 53). |
| `<output-java8.jar>` | Path for the Java 8-compatible output JAR. |
| `[backport-classes-dir]` | Optional: directory containing compiled `j9compat/*.class` files. If provided, the backport classes are bundled into the output JAR. |

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

### Optional API
```java
opt.ifPresentOrElse(a, e)      --> OptionalBackport.ifPresentOrElse(opt, a, e)
opt.or(supplier)               --> OptionalBackport.or(opt, supplier)
opt.stream()                   --> OptionalBackport.stream(opt)
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
cf.copy()                            --> CompletableFutureBackport.copy(cf)
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

- **`var` keyword** – syntactic sugar resolved at compile time; no bytecode
  change needed.
- **JPMS modules** – `module-info.class` entries are removed. Code that relies
  on *module exports/opens* at runtime will need additional configuration.
- **`VarHandle`** – not automatically replaced; requires manual migration to
  `AtomicFieldUpdater` or `sun.misc.Unsafe`.
- **`ProcessHandle`** / **`StackWalker`** / **`Flow`** – complex new APIs that
  are not automatically replaced. Applications using these must be migrated
  manually.
- **`@SafeVarargs` on private instance methods** – handled implicitly; the
  annotation is kept and the method is made package-private.
- **`invokedynamic` string concatenation** – Java 9+ compilers (`javac 9+`)
  emit `INVOKEDYNAMIC` instructions backed by
  `java.lang.invoke.StringConcatFactory` for `+` string concatenation.
  `StringConcatFactory` does not exist on Java 8. The desugarer does not
  replace these bootstrap methods, so class files compiled with a Java 9+
  `javac` (without `-source 8 -target 8`) will throw `NoClassDefFoundError`
  for `StringConcatFactory` at runtime on Java 8.
- **Multi-release JARs (MRJARs)** – JARs with `META-INF/versions/9/` (or
  higher) entries are not processed specially. Only the base entries are
  desugared; the version-specific entries under `META-INF/versions/` are
  passed through unchanged. The resulting JAR may therefore contain
  undesugared class files at the version-specific paths.
- **Collection iteration order** – `Set.of()` and `Map.of()` in Java 9
  deliberately randomise iteration order across JVM runs to reduce
  hash-collision DoS exposure. The `CollectionBackport` uses
  `LinkedHashSet`/`LinkedHashMap`, which preserves insertion order. Code
  that (incorrectly) depends on non-deterministic ordering may observe
  different behaviour after desugaring.
- **`Class.getModule()` and module-layer APIs** – Calls to
  `java.lang.Class.getModule()`, `java.lang.Module`,
  `java.lang.ModuleLayer`, and related APIs introduced in Java 9 are not
  remapped. These will result in `NoSuchMethodError` or
  `ClassNotFoundException` when the desugared JAR is run on Java 8.
- **InputStream subtype calls not remapped** – The API remapping for
  `transferTo()`, `readAllBytes()`, and `readNBytes()` only fires when the
  bytecode call-site owner is exactly `java/io/InputStream`. If the declared
  type at the call site is a concrete subclass (e.g., `FileInputStream`,
  `BufferedInputStream`, `ByteArrayInputStream`), the call will **not** be
  rewritten and will throw `NoSuchMethodError` on Java 8.
- **`takeWhile` and `dropWhile` are eager and sequential** – The backport
  implementations (`StreamBackport.takeWhile` / `StreamBackport.dropWhile`)
  eagerly collect elements into a `List` before returning a new stream.
  Consequences: (a) an infinite input stream will block indefinitely or
  exhaust heap memory; (b) parallel streams are silently converted to
  sequential; (c) stream characteristics such as `SORTED` and `DISTINCT` are
  not preserved in the output stream.
- **`Collectors.filtering()` and `Collectors.flatMapping()` not covered** –
  These `java.util.stream.Collectors` factory methods added in Java 9 are not
  remapped. Using them in code compiled with Java 9 will result in
  `NoSuchMethodError` when the desugared JAR runs on Java 8.
- **`CompletableFuture.minimalCompletionStage()` and `newIncompleteFuture()`
  not covered** – These two Java 9 additions to `CompletableFuture` are not
  remapped and will throw `NoSuchMethodError` on Java 8.
- **`orTimeout` / `completeOnTimeout` timer tasks are not cancelled on early
  completion** – In Java 9, when the future completes before the timeout the
  associated timer task is cancelled. The backport schedules the task but never
  cancels it; the task fires at the deadline and becomes a no-op (since the
  future is already done). In scenarios with many short-lived futures the
  scheduler queue may accumulate un-cancelled tasks until each one fires.
- **`completedStage()` returns a full `CompletableFuture`** – Java 9's
  `CompletableFuture.completedStage(v)` returns a *minimal-stage* object that
  restricts the public interface to `CompletionStage`, so casting it to
  `CompletableFuture` throws `ClassCastException`. The backport returns a
  plain `CompletableFuture`, which allows such a cast to succeed. Code that
  relies on the cast failing will behave differently after desugaring.
- **`List/Set/Map.copyOf()` are Java 10 additions** – These methods are
  correctly remapped to `CollectionBackport`, but they were introduced in
  Java 10, not Java 9. The tool's coverage therefore extends slightly beyond
  Java 9 for these three methods.
