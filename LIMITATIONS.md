# Limitations

All Java 9–11 APIs referenced in desugared bytecode (including module system and
VarHandle usage) are remapped to Java 8-compatible `j9compat` backports, and
reflective/MethodHandle lookups are redirected to those backports. Java 12+
APIs are not yet desugared; those can be added in future sessions. The backport
test suite exercises every remapped API and currently passes.

Known gaps:

- The HTTP client backport uses `HttpURLConnection` under the hood and only
  supports HTTP/1.1-style request/response handling. HTTP/2 features such as
  server push (push promise handlers) are ignored.
- HTTP request/response bodies are buffered in memory. Only the built-in
  byte-array/string body publishers are supported, and response handlers are
  limited to in-memory string/byte array/InputStream variants (no streaming or
  file-based handlers).
- HTTP client settings like request priority, expect-continue, SSL parameters,
  and HTTP/2 version selection are accepted but not enforced due to the
  `HttpURLConnection` implementation.
- `StackWalker` relies on `Thread.getStackTrace()` and does not expose hidden
  frames, bytecode indices (`StackFrame.getByteCodeIndex()` always returns `-1`),
  or local variables. Method-descriptor resolution depends on debug metadata and
  may fail for overloaded methods compiled without `-g`.
- Module metadata is informational only: `ModuleDescriptor.read` captures module
  names but ignores requires/exports/opens/uses/provides, and module access
  checks are not enforced (`addReads`/`addExports`/`addOpens` are no-ops and
  `canRead`/`canUse` always return `true`).
- `ProcessHandle` only tracks the current process and handles created via
  `ProcessHandle.fromProcess()`. `allProcesses()`, `children()`, and
  `descendants()` do not enumerate OS processes.
- `VarHandle` fences are no-ops and access modes are implemented with
  synchronized blocks rather than lock-free atomic instructions.

Internal bounds:

- `StackWalker` method-descriptor caching defaults to 256 entries. Set the
  `j9compat.stackwalker.cache.size` system property (read on first use) to a
  positive integer to adjust the cache size (capped at 65,536 entries).
- `SubmissionPublisher` rounds buffer capacities up to a power of two and caps
  the value at `1 << 30` (matching the JDK behavior).
