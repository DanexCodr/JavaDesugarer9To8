# Limitations

There are currently no known limitations. All Java 9 APIs referenced in
desugared bytecode (including module system and VarHandle usage) are remapped
to Java 8-compatible `j9compat` backports, and reflective/MethodHandle lookups
are redirected to those backports. The backport test suite exercises every
remapped API and currently passes.
