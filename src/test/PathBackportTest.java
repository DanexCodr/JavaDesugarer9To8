package test;

import j9compat.PathBackport;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.PathBackport}.
 */
public final class PathBackportTest {

    static void run() {
        section("PathBackport");
        testOf();
    }

    private static void testOf() {
        Path path = PathBackport.of("root", "child");
        assertEquals(Paths.get("root", "child").toString(), path.toString(),
                "Path.of(String...): matches Paths.get");

        URI uri = new File("sample.txt").toURI();
        Path uriPath = PathBackport.of(uri);
        assertEquals(Paths.get(uri).toString(), uriPath.toString(),
                "Path.of(URI): matches Paths.get");

        assertThrows(NullPointerException.class,
                () -> PathBackport.of((String) null),
                "Path.of(null String): throws NPE");
        assertThrows(NullPointerException.class,
                () -> PathBackport.of((URI) null),
                "Path.of(null URI): throws NPE");
    }
}
