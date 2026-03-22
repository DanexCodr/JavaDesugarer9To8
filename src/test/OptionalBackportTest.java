package test;

import j9compat.OptionalBackport;

import java.util.*;
import java.util.stream.*;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.OptionalBackport}.
 */
public final class OptionalBackportTest {

    static void run() {
        section("OptionalBackport");

        testIfPresentOrElse();
        testOr();
        testStream();
        testOrElseThrow();
    }

    // ── ifPresentOrElse ───────────────────────────────────────────────────────

    static void testIfPresentOrElse() {
        // Value present: action called with value
        String[] box = {null};
        OptionalBackport.ifPresentOrElse(
                Optional.of("hello"),
                s -> box[0] = s,
                () -> box[0] = "empty");
        assertEquals("hello", box[0], "ifPresentOrElse: present → action called with value");

        // Value absent: emptyAction called
        box[0] = null;
        OptionalBackport.ifPresentOrElse(
                Optional.<String>empty(),
                s -> box[0] = s,
                () -> box[0] = "empty");
        assertEquals("empty", box[0], "ifPresentOrElse: absent → emptyAction called");

        // Both lambdas are mutually exclusive
        boolean[] actionCalled = {false};
        boolean[] emptyCalled = {false};
        OptionalBackport.ifPresentOrElse(
                Optional.of(42),
                v -> actionCalled[0] = true,
                () -> emptyCalled[0] = true);
        assertTrue(actionCalled[0], "ifPresentOrElse: action called when present");
        assertTrue(!emptyCalled[0], "ifPresentOrElse: emptyAction NOT called when present");

        actionCalled[0] = false;
        emptyCalled[0] = false;
        OptionalBackport.ifPresentOrElse(
                Optional.empty(),
                v -> actionCalled[0] = true,
                () -> emptyCalled[0] = true);
        assertTrue(!actionCalled[0], "ifPresentOrElse: action NOT called when absent");
        assertTrue(emptyCalled[0], "ifPresentOrElse: emptyAction called when absent");

        // Null parameter checks
        assertThrows(NullPointerException.class,
                () -> OptionalBackport.ifPresentOrElse(null, v -> {}, () -> {}),
                "ifPresentOrElse(null optional): throws NPE");
        assertThrows(NullPointerException.class,
                () -> OptionalBackport.ifPresentOrElse(Optional.of("x"), null, () -> {}),
                "ifPresentOrElse(null action): throws NPE");
        assertThrows(NullPointerException.class,
                () -> OptionalBackport.ifPresentOrElse(Optional.of("x"), v -> {}, null),
                "ifPresentOrElse(null emptyAction): throws NPE");
    }

    // ── or ────────────────────────────────────────────────────────────────────

    static void testOr() {
        Optional<String> present = Optional.of("original");
        Optional<String> result = OptionalBackport.or(present, () -> Optional.of("fallback"));
        assertEquals("original", result.get(), "or: present optional returned as-is");

        Optional<String> absent = Optional.empty();
        Optional<String> fallback = OptionalBackport.or(absent, () -> Optional.of("fallback"));
        assertEquals("fallback", fallback.get(), "or: absent returns supplier's optional");

        // Supplier returns empty optional (still valid)
        Optional<String> emptyFallback = OptionalBackport.or(absent, Optional::empty);
        assertTrue(!emptyFallback.isPresent(), "or: supplier returning empty is allowed");

        // Null checks
        assertThrows(NullPointerException.class,
                () -> OptionalBackport.or(null, () -> Optional.empty()),
                "or(null optional): throws NPE");
        assertThrows(NullPointerException.class,
                () -> OptionalBackport.or(present, null),
                "or(null supplier): throws NPE");
        assertThrows(NullPointerException.class,
                () -> OptionalBackport.or(absent, () -> null),
                "or: supplier returning null throws NPE");

        // Supplier must NOT be called when optional is present
        boolean[] supplierCalled = {false};
        OptionalBackport.or(Optional.of("x"), () -> {
            supplierCalled[0] = true;
            return Optional.of("y");
        });
        assertTrue(!supplierCalled[0], "or: supplier not called when value present");
    }

    // ── stream ────────────────────────────────────────────────────────────────

    static void testStream() {
        // Present → single-element stream
        List<String> presentList = OptionalBackport.stream(Optional.of("value"))
                .collect(Collectors.toList());
        assertEquals(1, presentList.size(), "stream(present): size 1");
        assertEquals("value", presentList.get(0), "stream(present): element is the value");

        // Absent → empty stream
        List<String> absentList = OptionalBackport.<String>stream(Optional.empty())
                .collect(Collectors.toList());
        assertTrue(absentList.isEmpty(), "stream(absent): empty stream");

        // Null check
        assertThrows(NullPointerException.class,
                () -> OptionalBackport.stream(null),
                "stream(null): throws NPE");

        // Can be used in flat-map scenarios: flatten list of optionals
        List<Optional<String>> opts = new ArrayList<>();
        opts.add(Optional.of("a"));
        opts.add(Optional.empty());
        opts.add(Optional.of("b"));
        opts.add(Optional.empty());
        opts.add(Optional.of("c"));

        List<String> flat = opts.stream()
                .flatMap(o -> OptionalBackport.stream(o))
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("a", "b", "c"), flat,
                "stream: flat-map over list of optionals filters empties");
    }

    // ── orElseThrow ───────────────────────────────────────────────────────────

    static void testOrElseThrow() {
        assertEquals("value", OptionalBackport.orElseThrow(Optional.of("value")),
                "orElseThrow: returns value when present");
        assertThrows(NoSuchElementException.class,
                () -> OptionalBackport.orElseThrow(Optional.empty()),
                "orElseThrow: empty optional throws NoSuchElementException");
        assertThrows(NullPointerException.class,
                () -> OptionalBackport.orElseThrow(null),
                "orElseThrow(null): throws NPE");
    }
}
