# Limitations

There are no known functional limitations in Java 9–11 API coverage. All Java
9–11 APIs referenced in desugared bytecode (including module system and VarHandle
usage) are remapped to Java 8-compatible `j9compat` backports, and
reflective/MethodHandle lookups are redirected to those backports. Java 12+
APIs are not yet desugared; those can be added in future sessions. The backport
test suite exercises every remapped API and currently passes.

Internal bounds:

- `StackWalker` method-descriptor caching defaults to 256 entries. Set the
  `j9compat.stackwalker.cache.size` system property (read on first use) to a
  positive integer to adjust the cache size (capped at 65,536 entries).
- `SubmissionPublisher` rounds buffer capacities up to a power of two and caps
  the value at `1 << 30` (matching the JDK behavior).
