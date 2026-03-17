package test;

import j9compat.CollectorsBackport;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.CollectorsBackport}.
 */
public final class CollectorsBackportTest {

    static void run() {
        section("CollectorsBackport – filtering");
        testFiltering();

        section("CollectorsBackport – flatMapping");
        testFlatMapping();
    }

    static void testFiltering() {
        List<Integer> evens = Stream.of(1, 2, 3, 4, 5, 6)
                .collect(CollectorsBackport.filtering(n -> n % 2 == 0, Collectors.toList()));
        assertEquals(Arrays.asList(2, 4, 6), evens, "filtering: keeps matching elements");

        long count = Stream.of("a", "", "b", "")
                .collect(CollectorsBackport.filtering(s -> !s.isEmpty(), Collectors.counting()));
        assertEquals(2L, count, "filtering: downstream collector executes for matches only");

        assertThrows(NullPointerException.class,
                () -> CollectorsBackport.filtering(null, Collectors.toList()),
                "filtering(null predicate): throws NPE");
        assertThrows(NullPointerException.class,
                () -> CollectorsBackport.filtering(s -> true, null),
                "filtering(null downstream): throws NPE");
    }

    static void testFlatMapping() {
        List<String> words = Stream.of("a b", "c", "d e")
                .collect(CollectorsBackport.flatMapping(
                        s -> Arrays.stream(s.split(" ")),
                        Collectors.toList()));
        assertEquals(Arrays.asList("a", "b", "c", "d", "e"),
                words, "flatMapping: flattens mapped streams");

        long count = Stream.of(1, 2, 3)
                .collect(CollectorsBackport.flatMapping(
                        n -> Stream.of(n, n * 10),
                        Collectors.counting()));
        assertEquals(6L, count, "flatMapping: downstream sees all mapped elements");

        assertThrows(NullPointerException.class,
                () -> CollectorsBackport.flatMapping(null, Collectors.toList()),
                "flatMapping(null mapper): throws NPE");
        assertThrows(NullPointerException.class,
                () -> CollectorsBackport.flatMapping(Stream::of, null),
                "flatMapping(null downstream): throws NPE");
        assertThrows(NullPointerException.class,
                () -> Stream.of("x")
                        .collect(CollectorsBackport.flatMapping(s -> null, Collectors.toList())),
                "flatMapping(null stream): throws NPE");
    }
}
