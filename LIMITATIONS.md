# Limitations

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
