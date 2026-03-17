# Limitations

## Unsupported Java 9 APIs (manual migration required)

The desugarer only rewrites the explicit APIs listed in the README. Any other
Java 9+ additions still reference missing classes or methods on Java 8 and must
be migrated manually.

- **`VarHandle`** – not automatically replaced; requires manual migration to
  `AtomicFieldUpdater` or `sun.misc.Unsafe`.
- **Module system APIs** – Calls to `Class.getModule()`, `java.lang.Module`,
  `java.lang.ModuleLayer`, and related types are not remapped. The desugarer
  preserves `module-info.class` as metadata, but Java 8 has no JPMS runtime, so
  these APIs will still fail on Java 8.
- **Reflection/`MethodHandle` lookups are not remapped** – The desugarer only
  rewrites direct bytecode method invocations. Reflective or `MethodHandle`
  lookups of Java 9 APIs will still resolve the original methods and need
  manual migration.

## Bytecode / behavior differences

- **`takeWhile` / `dropWhile` spliterators are non-splittable** – The backports
  are lazy, but their spliterators do not split, so parallel streams will not
  parallelize and `SIZED`/`SUBSIZED` characteristics are cleared.
- **String concatenation performance** – `invokedynamic` string concatenation
  is rewritten to `StringBuilder` bytecode. The output is functionally correct
  but does not use `StringConcatFactory` optimizations.
- **InputStream remapping is type-aware** – Calls are rewritten only when the
  receiver class can be resolved as `InputStream` or a subclass (from the input
  JAR or the runtime classpath). If a dependency class cannot be resolved, the
  call is left unchanged and may need the dependency on the classpath or manual
  migration.
- **Private interface methods become package-private** – Java 8 rejects private
  interface methods, so the desugarer strips `ACC_PRIVATE`. This makes those
  methods package-private, which can allow same-package access and changes
  reflection visibility.
- **Some classes may still skip desugaring on hard ASM failures** – Missing
  dependencies no longer prevent frame computation (the writer falls back to
  `java/lang/Object`), but corrupted bytecode or unsupported constructs can
  still force a fallback to the original class bytes.

## Backport behavior notes

- **`ProcessHandle` is scoped to the current process and spawned child processes** –
  `ProcessHandle.current()` and `Process.toHandle()` are supported, and
  `parent()`, `children()`, and `descendants()` are populated for processes
  created via `Process.toHandle()`/`ProcessHandle.fromProcess()`. The `info()`
  payload only includes best-effort values (command line, start time, and user
  where available).
- **Android runtime metadata is best-effort** – When `java.lang.management`
  classes are unavailable (Android 11–15), `ProcessHandle` falls back to
  reflective checks and may report unknown PIDs or missing start times.
- **`StackWalker` uses stack traces** – Frames are derived from
  `Thread.getStackTrace()` and do not expose hidden frames or bytecode indices.
  `StackFrame.getDeclaringClass()` requires
  `StackWalker.Option.RETAIN_CLASS_REFERENCE`.
- **`Flow` is interface-only** – The backport provides the reactive-stream
  interfaces and default buffer size, but no runtime implementation.

## Version coverage notes

- **`List/Set/Map.copyOf()` are Java 10 additions** – These methods are
  remapped to `CollectionBackport`, so coverage extends slightly beyond Java 9
  for these three methods.
