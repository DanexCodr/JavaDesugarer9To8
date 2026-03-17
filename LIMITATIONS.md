# Limitations

## Unsupported Java 9 APIs (manual migration required)

- **`VarHandle`** – not automatically replaced; requires manual migration to
  `AtomicFieldUpdater` or `sun.misc.Unsafe`.
- **`ProcessHandle`** / **`StackWalker`** / **`Flow`** – complex new APIs that
  are not automatically replaced. Applications using these must be migrated
  manually.
- **Module system APIs** – Calls to `Class.getModule()`, `java.lang.Module`,
  `java.lang.ModuleLayer`, and related types are not remapped. The desugarer
  preserves `module-info.class` as metadata, but Java 8 has no JPMS runtime, so
  these APIs will still fail on Java 8.

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

## Version coverage notes

- **`List/Set/Map.copyOf()` are Java 10 additions** – These methods are
  remapped to `CollectionBackport`, so coverage extends slightly beyond Java 9
  for these three methods.
