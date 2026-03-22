package test;

import j9compat.StringBackport;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.StringBackport}.
 */
public final class StringBackportTest {

    static void run() {
        section("StringBackport");
        testIsBlank();
        testStrip();
        testRepeat();
        testLines();
    }

    private static void testIsBlank() {
        assertTrue(StringBackport.isBlank(""), "isBlank: empty string");
        assertTrue(StringBackport.isBlank(" \t\n"), "isBlank: whitespace only");
        assertTrue(!StringBackport.isBlank(" a "), "isBlank: non-blank");
        assertThrows(NullPointerException.class,
                () -> StringBackport.isBlank(null),
                "isBlank(null): throws NPE");
    }

    private static void testStrip() {
        assertEquals("hi",
                StringBackport.strip("  hi  "),
                "strip: trims leading and trailing whitespace");
        assertEquals("hi",
                StringBackport.stripLeading("   hi"),
                "stripLeading: trims leading whitespace");
        assertEquals("hi",
                StringBackport.stripTrailing("hi   "),
                "stripTrailing: trims trailing whitespace");
        assertEquals("", StringBackport.strip(" \u2003 "),
                "strip: unicode whitespace returns empty");
        assertThrows(NullPointerException.class,
                () -> StringBackport.strip(null),
                "strip(null): throws NPE");
    }

    private static void testRepeat() {
        assertEquals("aaa", StringBackport.repeat("a", 3),
                "repeat: repeats string");
        assertEquals("", StringBackport.repeat("a", 0),
                "repeat: zero count returns empty");
        assertEquals("hi", StringBackport.repeat("hi", 1),
                "repeat: count 1 returns original");
        assertThrows(IllegalArgumentException.class,
                () -> StringBackport.repeat("x", -1),
                "repeat: negative count throws");
        assertThrows(NullPointerException.class,
                () -> StringBackport.repeat(null, 2),
                "repeat(null): throws NPE");
    }

    private static void testLines() {
        List<String> lines = StringBackport.lines("a\nb\r\nc\r")
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("a", "b", "c"), lines,
                "lines: splits line terminators");
        List<String> trailing = StringBackport.lines("a\nb\n")
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("a", "b"), trailing,
                "lines: trailing newline not included");
        assertThrows(NullPointerException.class,
                () -> StringBackport.lines(null),
                "lines(null): throws NPE");
    }
}
