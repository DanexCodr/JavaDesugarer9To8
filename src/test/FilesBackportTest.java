package test;

import j9compat.FilesBackport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.FilesBackport}.
 */
public final class FilesBackportTest {

    static void run() throws Exception {
        section("FilesBackport");
        testReadWriteString();
        testReadWriteStringWithCharset();
        testNullChecks();
    }

    private static void testReadWriteString() throws Exception {
        Path temp = Files.createTempFile("j9compat", ".txt");
        try {
            FilesBackport.writeString(temp, "hello");
            String read = FilesBackport.readString(temp);
            assertEquals("hello", read, "readString/writeString: round-trip");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void testReadWriteStringWithCharset() throws Exception {
        Path temp = Files.createTempFile("j9compat", ".txt");
        try {
            String content = "こんにちは";
            FilesBackport.writeString(temp, content, StandardCharsets.UTF_16LE);
            String read = FilesBackport.readString(temp, StandardCharsets.UTF_16LE);
            assertEquals(content, read, "readString/writeString: charset round-trip");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void testNullChecks() {
        assertThrows(NullPointerException.class,
                () -> {
                    try { FilesBackport.readString(null); }
                    catch (java.io.IOException e) { throw new RuntimeException(e); }
                },
                "readString(null): throws NPE");
        assertThrows(NullPointerException.class,
                () -> {
                    try { FilesBackport.writeString((Path) null, "data"); }
                    catch (java.io.IOException e) { throw new RuntimeException(e); }
                },
                "writeString(null path): throws NPE");
        assertThrows(NullPointerException.class,
                () -> {
                    try {
                        Path temp = Files.createTempFile("j9compat", ".txt");
                        try {
                            FilesBackport.writeString(temp, null);
                        } finally {
                            Files.deleteIfExists(temp);
                        }
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                "writeString(null data): throws NPE");
    }
}
