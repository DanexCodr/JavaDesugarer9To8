# Java 9–11 → Java 8 Desugarer

A bytecode-level tool that transforms a JAR compiled with **Java 9–11** into a
fully **Java 8-compatible** JAR, including a bundled runtime backport library
(`j9compat`) that provides Java 8 implementations of every Java 9–11 API used.

**Compatibility focus:** runs on **Eclipse Temurin (Adoptium)** and targets
**Android 11–15 (API 30–34)** deployments with Java 9–11 bytecode input.

## What this repository provides

- **Desugarer CLI**: rewrites Java 9–11 bytecode to Java 8 bytecode and optionally
  bundles the backport classes into the output JAR.
- **`j9compat` backport library**: Java 8 implementations of Java 9–11 APIs that
  your desugared code calls at runtime.
- **Source desugaring mode**: converts Java 9–11 source files to Java 8-compatible
  source (with an optional compile step).
- **Incremental processing**: cache-based mode to speed up repeated desugaring
  runs for large JARs.
- **Test suite + CI**: regression tests and GitHub Actions workflow to validate
  transformations and backports.

---

## What it does

| Transformation | Description |
|----------------|-------------|
| **Class-file version downgrade** | Changes class file major version from 53–55 (Java 9–11) to 52 (Java 8). |
| **Module-info retention** | Downgrades `module-info.class` so module metadata is preserved even though Java 8 ignores JPMS. |
| **Private interface methods** | Java 9+ allows `private` methods in interfaces. This tool makes them package-private so the Java 8 verifier accepts them. |
| **String concatenation** | Rewrites `invokedynamic` StringConcatFactory concatenation to `StringBuilder` bytecode. |
| **Collection factory methods** | Redirects `List.of()`, `Set.of()`, `Map.of()`, `Map.ofEntries()`, `Map.entry()`, and all `copyOf()` variants to `j9compat.CollectionBackport`. |
| **Stream API additions** | Redirects `takeWhile()`, `dropWhile()`, `ofNullable()`, and the three-argument `iterate()` to `j9compat.StreamBackport`. |
| **Primitive Stream additions** | Redirects `IntStream/LongStream/DoubleStream.takeWhile()`, `dropWhile()`, and the three-argument `iterate()` to `j9compat.*StreamBackport`. |
| **Collectors additions** | Redirects `Collectors.filtering()`, `Collectors.flatMapping()`, and `Collectors.toUnmodifiable*()` to `j9compat.CollectorsBackport`. |
| **Optional API additions** | Redirects `ifPresentOrElse()`, `or()`, `stream()`, and `orElseThrow()` to `j9compat.OptionalBackport`. |
| **Optional primitive additions** | Redirects `OptionalInt/Long/Double.ifPresentOrElse()`, `.stream()`, and `.orElseThrow()` to `j9compat.Optional*Backport`. |
| **InputStream additions** | Redirects `transferTo()`, `readAllBytes()`, and `readNBytes()` to `j9compat.IOBackport`. |
| **String additions (Java 11)** | Redirects `String.isBlank()`, `String.lines()`, `String.strip*()`, and `String.repeat()` to `j9compat.StringBackport`. |
| **NIO additions (Java 11)** | Redirects `Files.readString()`, `Files.writeString()`, and `Path.of()` to `j9compat.FilesBackport`/`PathBackport`. |
| **Java 11 utilities** | Redirects `Optional.isEmpty()`, `Collection.toArray(IntFunction)`, and `Predicate.not()` to `j9compat` backports. |
| **Objects additions** | Redirects `requireNonNullElse()`, `requireNonNullElseGet()`, `checkIndex()`, `checkFromToIndex()`, and `checkFromIndexSize()` to `j9compat.ObjectsBackport`. |
| **CompletableFuture additions** | Redirects `orTimeout()`, `completeOnTimeout()`, `failedFuture()`, `completedStage()`, `failedStage()`, `minimalCompletionStage()`, `newIncompleteFuture()`, and `copy()` to `j9compat.CompletableFutureBackport`. |
| **Process/Stack/Flow types** | Remaps `ProcessHandle`, `StackWalker`, `Flow`, and `SubmissionPublisher` to Java 8-compatible `j9compat` implementations. |
| **Module system APIs** | Redirects `Class.getModule()` and remaps `Module`, `ModuleLayer`, and related descriptor types to `j9compat` backports. |
| **VarHandle APIs** | Redirects `VarHandle` lookups (including `findVarHandle` and `arrayElementVarHandle`) to `j9compat.VarHandle`. |
| **Reflection/MethodHandle lookups** | Redirects `Class.getMethod` and `MethodHandles.Lookup` lookups of Java 9 APIs to the backport implementations. |

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
│   │   ├── ClassHierarchy.java         Tracks class inheritance/implements
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
  src/j9compat/*.java

# 2. Compile the desugarer tool
mkdir -p build/desugarer
javac -source 8 -target 8 \
  -cp asm-9.4.jar:asm-commons-9.4.jar:asm-tree-9.4.jar \
  -d build/desugarer \
  src/desugarer/*.java

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
java -jar build/desugar9to8.jar [--incremental] [--cache-dir <dir>] [--class-path <path>] <input-java9-11.jar> <output-java8.jar> [backport-classes-dir]
```

| Argument | Description |
|----------|-------------|
| `<input-java9-11.jar>` | JAR compiled with Java 9–11 (class version 53–55). |
| `<output-java8.jar>` | Path for the Java 8-compatible output JAR. |
| `[backport-classes-dir]` | Optional: directory containing compiled `j9compat/*.class` files. If provided, the backport classes are bundled into the output JAR. |
| `--incremental` | Enable incremental mode (reuse unchanged output entries). |
| `--cache-dir <dir>` | Cache directory for incremental mode (default: `build/.desugar-cache`). |
| `--class-path <path>` | Extra classpath entries (jar/dir, separated by your platform path separator) used to resolve `InputStream` subclasses. |

### Example

```bash
java -jar build/desugar9to8.jar \
  my-app-java10.jar \
  my-app-java8.jar \
  build/backport

### Source desugaring (Java 9–11 → Java 8 source)

```
java -jar build/desugar9to8.jar --source MyApp.java --output MyApp.java8.java
```

To compile immediately after desugaring:

```
java -jar build/desugar9to8.jar --source MyApp.java --compile
```
```

The output JAR will:
- Have all class files at version 52 (Java 8).
- Contain the `j9compat.*` backport classes.
- Redirect every Java 9–11 API call to the corresponding backport method.

### Framework-style extensions

The desugarer exposes a lightweight framework hook via `desugarer.MethodTransform`.
Custom transformers can be registered with Java’s `ServiceLoader` mechanism to
add or override method remapping rules without changing the core tool. Place a
`META-INF/services/desugarer.MethodTransform` file on the classpath and list
your implementation class names to have them loaded automatically.

Example invocation with a plugin JAR:

```bash
java -cp build/desugar9to8.jar:my-desugar-plugin.jar \
  desugarer.Java9ToJava8Desugarer \
  my-app-java10.jar my-app-java8.jar build/backport
```

### Incremental mode

Incremental mode reuses the output bytes for unchanged JAR entries and writes
an on-disk cache alongside the output.

```bash
java -jar build/desugar9to8.jar --incremental --cache-dir build/.desugar-cache \
  my-app-java9.jar \
  my-app-java8.jar \
  build/backport
```

### Real-world verification (reflection-java9)

The `com.nerdvision:reflection-java9:3.0.2` artifact is compiled for Java 9
(class version 53) and calls `Class.getModule()`. The steps below desugar it
and run a Java 8 smoke test.

```bash
# Download the Java 9 jar and its Java 8 dependencies
curl -L -o /tmp/reflection-java9-3.0.2.jar \
  https://repo1.maven.org/maven2/com/nerdvision/reflection-java9/3.0.2/reflection-java9-3.0.2.jar
curl -L -o /tmp/reflection-api-3.0.2.jar \
  https://repo1.maven.org/maven2/com/nerdvision/reflection-api/3.0.2/reflection-api-3.0.2.jar
curl -L -o /tmp/agent-api-3.0.2.jar \
  https://repo1.maven.org/maven2/com/nerdvision/agent-api/3.0.2/agent-api-3.0.2.jar

# Desugar the Java 9 jar
java -jar build/desugar9to8.jar \
  /tmp/reflection-java9-3.0.2.jar \
  /tmp/reflection-java9-3.0.2-java8.jar \
  build/backport

# Verify class version is now Java 8 (major 52)
javap -verbose -classpath /tmp/reflection-java9-3.0.2-java8.jar \
  com.nerdvision.agent.reflect.java9.Java9ReflectionImpl | grep "major version"

# Use any Java 8 runtime (download Temurin 8 if needed)
curl -L -o /tmp/temurin8-jre.tar.gz \
  https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u482-b08/OpenJDK8U-jre_x64_linux_hotspot_8u482b08.tar.gz
tar -xzf /tmp/temurin8-jre.tar.gz -C /tmp

cat > /tmp/Java9ReflectionSmokeTest.java << 'JAVA'
public class Java9ReflectionSmokeTest {
    private static class Dummy {
        private String secret = "ok";
    }

    public static void main(String[] args) throws Exception {
        com.nerdvision.agent.reflect.java9.Java9ReflectionImpl impl =
            new com.nerdvision.agent.reflect.java9.Java9ReflectionImpl();
        java.lang.reflect.Field field = Dummy.class.getDeclaredField("secret");
        boolean result = impl.setAccessible(Dummy.class, field);
        System.out.println("setAccessible=" + result);
        System.out.println("value=" + field.get(new Dummy()));
    }
}
JAVA

javac --release 8 \
  -cp /tmp/reflection-java9-3.0.2-java8.jar:/tmp/reflection-api-3.0.2.jar:/tmp/agent-api-3.0.2.jar \
  -d /tmp/java9-smoke-test \
  /tmp/Java9ReflectionSmokeTest.java

/tmp/jdk8u482-b08-jre/bin/java \
  -cp /tmp/reflection-java9-3.0.2-java8.jar:/tmp/reflection-api-3.0.2.jar:/tmp/agent-api-3.0.2.jar:/tmp/java9-smoke-test \
  Java9ReflectionSmokeTest

# Expected output:
# setAccessible=true
# value=ok
```

---

## Java 9–11 features covered

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
Collectors.toUnmodifiableList()--> CollectorsBackport.toUnmodifiableList()
Collectors.toUnmodifiableSet() --> CollectorsBackport.toUnmodifiableSet()
Collectors.toUnmodifiableMap(k,v) --> CollectorsBackport.toUnmodifiableMap(k, v)
```

### Optional API
```java
opt.ifPresentOrElse(a, e)      --> OptionalBackport.ifPresentOrElse(opt, a, e)
opt.or(supplier)               --> OptionalBackport.or(opt, supplier)
opt.stream()                   --> OptionalBackport.stream(opt)
opt.orElseThrow()              --> OptionalBackport.orElseThrow(opt)
opt.isEmpty()                  --> OptionalBackport.isEmpty(opt)
```

### Optional primitive API
```java
optInt.ifPresentOrElse(a, e)   --> OptionalIntBackport.ifPresentOrElse(optInt, a, e)
optLong.stream()               --> OptionalLongBackport.stream(optLong)
optDouble.stream()             --> OptionalDoubleBackport.stream(optDouble)
optInt.orElseThrow()           --> OptionalIntBackport.orElseThrow(optInt)
optLong.isEmpty()              --> OptionalLongBackport.isEmpty(optLong)
```

### InputStream API
```java
in.transferTo(out)             --> IOBackport.transferTo(in, out)
in.readAllBytes()              --> IOBackport.readAllBytes(in)
in.readNBytes(buf, off, len)   --> IOBackport.readNBytes(in, buf, off, len)
```

### String API (Java 11)
```java
" hi ".strip()                 --> StringBackport.strip(" hi ")
" ".isBlank()                  --> StringBackport.isBlank(" ")
"x".repeat(3)                   --> StringBackport.repeat("x", 3)
text.lines()                   --> StringBackport.lines(text)
```

### Files/Path API (Java 11)
```java
Files.readString(path)         --> FilesBackport.readString(path)
Files.writeString(path, text)  --> FilesBackport.writeString(path, text)
Path.of("config.txt")          --> PathBackport.of("config.txt")
```

### Utility additions (Java 11)
```java
Predicate.not(p)               --> PredicateBackport.not(p)
values.toArray(String[]::new)  --> CollectionBackport.toArray(values, String[]::new)
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

### Module system APIs
```java
Class<?> c = ...;
c.getModule()                  --> j9compat.ModuleBackport.getModule(c)
```

### VarHandle APIs
```java
MethodHandles.lookup()
    .findVarHandle(Foo.class, "field", int.class)
    --> j9compat.MethodHandlesBackport.findVarHandle(...)

MethodHandles.arrayElementVarHandle(int[].class)
    --> j9compat.MethodHandlesBackport.arrayElementVarHandle(int[].class)
```

### Reflection / MethodHandle lookups
```java
Class<?> c = Stream.class;
c.getMethod("takeWhile", Predicate.class)
    --> j9compat.ReflectionBackport.getMethod(...)

MethodHandles.lookup()
    .findVirtual(Stream.class, "takeWhile", MethodType.methodType(Stream.class, Predicate.class))
    --> j9compat.MethodHandlesBackport.findVirtual(...)
```

---

## How it works

The desugarer is built on **ASM 9.4** (ObjectWeb ASM bytecode library).

1. **`Java9ToJava8Desugarer`** opens the input JAR, iterates over every entry,
   and passes `.class` files through the transformation pipeline.
2. **`ClassDesugarer`** (a `ClassVisitor`) downgrades the class-file version and
   strips `ACC_PRIVATE` from interface methods.
3. **`MethodDesugarer`** (a `MethodVisitor`) intercepts every
   `visitMethodInsn()` call and rewrites matching Java 9–11 API calls:
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

Java 9–11 API call sites listed above are redirected to the `j9compat` backports.
Java 11+ APIs are not yet desugared. For status details, see
[LIMITATIONS.md](LIMITATIONS.md).
