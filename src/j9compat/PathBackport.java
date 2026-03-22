package j9compat;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Java 8-compatible backport of {@link java.nio.file.Path#of} methods.
 */
public final class PathBackport {

    private PathBackport() {}

    public static Path of(String first, String... more) {
        Objects.requireNonNull(first, "first");
        return Paths.get(first, more);
    }

    public static Path of(URI uri) {
        Objects.requireNonNull(uri, "uri");
        return Paths.get(uri);
    }
}
