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

        section("CollectorsBackport – toUnmodifiableList");
        testToUnmodifiableList();

        section("CollectorsBackport – toUnmodifiableSet");
        testToUnmodifiableSet();

        section("CollectorsBackport – toUnmodifiableMap");
        testToUnmodifiableMap();
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

    static void testToUnmodifiableList() {
        List<String> list = Stream.of("a", "b")
                .collect(CollectorsBackport.toUnmodifiableList());
        assertEquals(Arrays.asList("a", "b"), list, "toUnmodifiableList: collects values");
        assertThrows(UnsupportedOperationException.class,
                () -> list.add("c"),
                "toUnmodifiableList: list is unmodifiable");
        assertThrows(NullPointerException.class,
                () -> Stream.of("a", null).collect(CollectorsBackport.toUnmodifiableList()),
                "toUnmodifiableList: null elements throw NPE");
    }

    static void testToUnmodifiableSet() {
        java.util.Set<String> set = Stream.of("a", "b", "a")
                .collect(CollectorsBackport.toUnmodifiableSet());
        assertTrue(set.contains("a") && set.contains("b"),
                "toUnmodifiableSet: collects values");
        assertThrows(UnsupportedOperationException.class,
                () -> set.add("c"),
                "toUnmodifiableSet: set is unmodifiable");
        assertThrows(NullPointerException.class,
                () -> Stream.of("a", null).collect(CollectorsBackport.toUnmodifiableSet()),
                "toUnmodifiableSet: null elements throw NPE");
    }

    static void testToUnmodifiableMap() {
        java.util.Map<String, Integer> map = Stream.of("a", "bb")
                .collect(CollectorsBackport.toUnmodifiableMap(s -> s, String::length));
        assertEquals(1, map.get("a"), "toUnmodifiableMap: maps key/value");
        assertEquals(2, map.get("bb"), "toUnmodifiableMap: maps key/value");
        assertThrows(UnsupportedOperationException.class,
                () -> map.put("c", 3),
                "toUnmodifiableMap: map is unmodifiable");

        assertThrows(IllegalStateException.class,
                () -> Stream.of("a", "a")
                        .collect(CollectorsBackport.toUnmodifiableMap(s -> s, String::length)),
                "toUnmodifiableMap: duplicate keys throw");
        assertThrows(NullPointerException.class,
                () -> Stream.of("a", null)
                        .collect(CollectorsBackport.toUnmodifiableMap(s -> s, String::length)),
                "toUnmodifiableMap: null values throw NPE");

        java.util.Map<String, Integer> merged = Stream.of("a", "aa")
                .collect(CollectorsBackport.toUnmodifiableMap(
                        s -> "key", String::length, (l, r) -> l + r));
        assertEquals(3, merged.get("key"), "toUnmodifiableMap merge: combines values");

        assertThrows(NullPointerException.class,
                () -> Stream.of("a", "aa")
                        .collect(CollectorsBackport.toUnmodifiableMap(
                                s -> "key", String::length, (l, r) -> null)),
                "toUnmodifiableMap merge: null merge result throws NPE");
    }
}
