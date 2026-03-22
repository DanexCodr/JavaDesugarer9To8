package j9compat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Java 8-compatible backport of {@link java.nio.file.Files} methods added in
 * Java 11.
 */
public final class FilesBackport {

    private FilesBackport() {}

    public static String readString(Path path) throws IOException {
        return readString(path, StandardCharsets.UTF_8);
    }

    public static String readString(Path path, Charset charset) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(charset, "charset");
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, charset);
    }

    public static Path writeString(Path path, CharSequence csq, OpenOption... options)
            throws IOException {
        return writeString(path, csq, StandardCharsets.UTF_8, options);
    }

    public static Path writeString(Path path, CharSequence csq, Charset charset,
                                   OpenOption... options) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(csq, "csq");
        Objects.requireNonNull(charset, "charset");
        byte[] bytes = csq.toString().getBytes(charset);
        return Files.write(path, bytes, options);
    }
}
