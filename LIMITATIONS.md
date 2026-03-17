# Limitations

## Unsupported Java 9 APIs (manual migration required)

The desugarer only rewrites the explicit APIs listed in the README. Any other
Java 9+ additions still reference missing classes or methods on Java 8 and must
be migrated manually.

- **`VarHandle`** – not automatically replaced; requires manual migration to
  `AtomicFieldUpdater` or `sun.misc.Unsafe`.
- **`ProcessHandle`** / **`StackWalker`** / **`Flow`** – complex new APIs that
  are not automatically replaced. Applications using these must be migrated
  manually.
- **Module system APIs** – Calls to `Class.getModule()`, `java.lang.Module`,
  `java.lang.ModuleLayer`, and related types are not remapped. The desugarer
  preserves `module-info.class` as metadata, but Java 8 has no JPMS runtime, so
  these APIs will still fail on Java 8.
- **Primitive streams and Optional primitives** – Java 9 additions on
  `IntStream` / `LongStream` / `DoubleStream` and `OptionalInt` / `OptionalLong`
  / `OptionalDouble` are not remapped. Migrate to boxed `Stream`/`Optional` APIs
  or provide custom helpers.
- **Reflection/`MethodHandle` lookups are not remapped** – The desugarer only
  rewrites direct bytecode method invocations. Reflective or `MethodHandle`
  lookups of Java 9 APIs will still resolve the original methods and need
  manual migration.

## Bytecode / behavior differences

- **`takeWhile` / `dropWhile` spliterators are non-splittable** – The backport
  is now lazy, but its spliterators do not split, so parallel streams will not
  parallelize and `SIZED`/`SUBSIZED` characteristics are cleared.
- **String concatenation performance** – `invokedynamic` string concatenation
  is rewritten to `StringBuilder` bytecode. The output is functionally correct
  but does not use `StringConcatFactory` optimizations.
- **InputStream remapping is signature-based** – Any virtual call named
  `transferTo`, `readAllBytes`, or `readNBytes` with the JDK signatures will be
  rewritten, even if the receiver type is not actually an `InputStream`. This
  is rare but can mis-remap custom APIs with identical method signatures.
- **Private interface methods become package-private** – Java 8 rejects private
  interface methods, so the desugarer strips `ACC_PRIVATE`. This makes those
  methods package-private, which can allow same-package access and changes
  reflection visibility.
- **Classes that fail desugaring remain Java 9+ class files** – If ASM cannot transform a
  class (for example, due to missing dependencies needed to compute frames),
  the original class bytes are kept. The output JAR may still contain Java 9
  class files in that case.

## Version coverage notes

- **`List/Set/Map.copyOf()` are Java 10 additions** – These methods are
  remapped to `CollectionBackport`, so coverage extends slightly beyond Java 9
  for these three methods.
