package test;

import j9compat.PredicateBackport;

import java.util.function.Predicate;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.PredicateBackport}.
 */
public final class PredicateBackportTest {

    static void run() {
        section("PredicateBackport");
        testNot();
    }

    private static void testNot() {
        Predicate<String> nonEmpty = s -> s != null && !s.isEmpty();
        Predicate<String> empty = PredicateBackport.not(nonEmpty);
        assertTrue(empty.test(""), "not: predicate is negated");
        assertTrue(!empty.test("value"), "not: negated predicate returns false");
        assertThrows(NullPointerException.class,
                () -> PredicateBackport.not(null),
                "not(null): throws NPE");
    }
}
