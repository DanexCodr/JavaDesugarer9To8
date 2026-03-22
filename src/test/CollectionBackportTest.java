package test;

import j9compat.CollectionBackport;

import java.util.*;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.CollectionBackport}.
 *
 * Verifies:
 * <ul>
 *   <li>Returned collections are unmodifiable.</li>
 *   <li>Null elements / keys / values throw {@link NullPointerException}.</li>
 *   <li>Duplicate keys / set elements throw {@link IllegalArgumentException}.</li>
 *   <li>Contents and sizes are as expected for each arity.</li>
 *   <li>copyOf variants work correctly.</li>
 * </ul>
 */
public final class CollectionBackportTest {

    static void run() {
        section("CollectionBackport – List");
        testListOf();
        testListCopyOf();

        section("CollectionBackport – Set");
        testSetOf();
        testSetCopyOf();

        section("CollectionBackport – Map");
        testMapOf();
        testMapEntry();
        testMapOfEntries();
        testMapCopyOf();

        section("CollectionBackport – toArray");
        testToArray();
    }

    // ── List.of ──────────────────────────────────────────────────────────────

    static void testListOf() {
        // Empty
        List<String> empty = CollectionBackport.listOf();
        assertTrue(empty.isEmpty(), "listOf(): empty list");
        assertUnmodifiableList(empty);

        // Single element
        List<String> one = CollectionBackport.listOf("a");
        assertEquals(1, one.size(), "listOf(e1): size 1");
        assertEquals("a", one.get(0), "listOf(e1): element correct");
        assertUnmodifiableList(one);

        // Two elements
        List<String> two = CollectionBackport.listOf("x", "y");
        assertEquals(2, two.size(), "listOf(e1,e2): size 2");
        assertEquals("x", two.get(0), "listOf(e1,e2): first element");
        assertEquals("y", two.get(1), "listOf(e1,e2): second element");

        // Ten elements (boundary for fixed-arity overloads)
        List<Integer> ten = CollectionBackport.listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertEquals(10, ten.size(), "listOf(10 args): size 10");
        assertEquals(7, ten.get(6), "listOf(10 args): seventh element is 7");

        // Varargs overload (> 10 elements)
        List<Integer> large = CollectionBackport.listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertEquals(11, large.size(), "listOf(varargs): size 11");
        assertEquals(11, large.get(10), "listOf(varargs): 11th element");

        // Null element rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.listOf((String) null),
                "listOf(null): single null throws NPE");
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.listOf("a", null),
                "listOf(a, null): null in pair throws NPE");
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.listOf(null, null, null),
                "listOf(varargs nulls): throws NPE");

        // Duplicates are allowed in List.of
        List<String> dups = CollectionBackport.listOf("a", "a", "b");
        assertEquals(3, dups.size(), "listOf with duplicates: size 3");
    }

    static void assertUnmodifiableList(List<?> list) {
        assertThrows(UnsupportedOperationException.class,
                () -> list.add(null),
                "list should be unmodifiable (add)");
    }

    static void testListCopyOf() {
        List<String> src = new ArrayList<>();
        src.add("p");
        src.add("q");
        List<String> copy = CollectionBackport.listCopyOf(src);
        assertEquals(2, copy.size(), "listCopyOf: size matches source");
        assertEquals("p", copy.get(0), "listCopyOf: first element");
        assertEquals("q", copy.get(1), "listCopyOf: second element");
        assertUnmodifiableList(copy);

        // Mutating the source doesn't affect the copy
        src.add("r");
        assertEquals(2, copy.size(), "listCopyOf: copy is independent of source");

        // Null element rejection
        List<String> withNull = new ArrayList<>();
        withNull.add("a");
        withNull.add(null);
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.listCopyOf(withNull),
                "listCopyOf with null element throws NPE");

        // Null collection rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.listCopyOf(null),
                "listCopyOf(null): throws NPE");
    }

    // ── Set.of ───────────────────────────────────────────────────────────────

    static void testSetOf() {
        // Empty
        Set<String> empty = CollectionBackport.setOf();
        assertTrue(empty.isEmpty(), "setOf(): empty set");
        assertUnmodifiableSet(empty);

        // Single element
        Set<String> one = CollectionBackport.setOf("a");
        assertEquals(1, one.size(), "setOf(e1): size 1");
        assertTrue(one.contains("a"), "setOf(e1): contains element");
        assertUnmodifiableSet(one);

        // Two elements
        Set<String> two = CollectionBackport.setOf("x", "y");
        assertEquals(2, two.size(), "setOf(e1,e2): size 2");
        assertTrue(two.contains("x"), "setOf(e1,e2): contains x");
        assertTrue(two.contains("y"), "setOf(e1,e2): contains y");

        // Ten elements
        Set<Integer> ten = CollectionBackport.setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertEquals(10, ten.size(), "setOf(10 args): size 10");
        assertTrue(ten.contains(7), "setOf(10 args): contains 7");

        // Null element rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.setOf((String) null),
                "setOf(null): single null throws NPE");
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.setOf("a", null),
                "setOf(a, null): null in pair throws NPE");

        // Duplicate element rejection
        assertThrows(IllegalArgumentException.class,
                () -> CollectionBackport.setOf("a", "a"),
                "setOf(a, a): duplicate throws IAE");
        assertThrows(IllegalArgumentException.class,
                () -> CollectionBackport.setOf("a", "b", "a"),
                "setOf varargs duplicate throws IAE");
    }

    static void assertUnmodifiableSet(Set<?> set) {
        assertThrows(UnsupportedOperationException.class,
                () -> set.add(null),
                "set should be unmodifiable (add)");
    }

    static void testSetCopyOf() {
        Set<String> src = new LinkedHashSet<>();
        src.add("alpha");
        src.add("beta");
        Set<String> copy = CollectionBackport.setCopyOf(src);
        assertEquals(2, copy.size(), "setCopyOf: size matches source");
        assertTrue(copy.contains("alpha"), "setCopyOf: contains alpha");
        assertTrue(copy.contains("beta"), "setCopyOf: contains beta");
        assertUnmodifiableSet(copy);

        // Null element rejection
        Set<String> withNull = new LinkedHashSet<>();
        withNull.add("x");
        withNull.add(null);
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.setCopyOf(withNull),
                "setCopyOf with null element throws NPE");

        // Null collection rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.setCopyOf(null),
                "setCopyOf(null): throws NPE");

        // Duplicate element rejection
        List<String> dupes = Arrays.asList("dup", "dup");
        assertThrows(IllegalArgumentException.class,
                () -> CollectionBackport.setCopyOf(dupes),
                "setCopyOf with duplicates throws IAE");
    }

    // ── Map.of ───────────────────────────────────────────────────────────────

    static void testMapOf() {
        // Empty
        Map<String, Integer> empty = CollectionBackport.mapOf();
        assertTrue(empty.isEmpty(), "mapOf(): empty map");
        assertUnmodifiableMap(empty);

        // Single entry
        Map<String, Integer> one = CollectionBackport.mapOf("k1", 1);
        assertEquals(1, one.size(), "mapOf(k,v): size 1");
        assertEquals(1, one.get("k1"), "mapOf(k,v): value correct");

        // Two entries
        Map<String, Integer> two = CollectionBackport.mapOf("a", 1, "b", 2);
        assertEquals(2, two.size(), "mapOf(2 pairs): size 2");
        assertEquals(1, two.get("a"), "mapOf(2 pairs): value for a");
        assertEquals(2, two.get("b"), "mapOf(2 pairs): value for b");

        // Ten entries (maximum fixed-arity overload)
        Map<String, Integer> ten = CollectionBackport.mapOf(
                "k1", 1, "k2", 2, "k3", 3, "k4", 4, "k5", 5,
                "k6", 6, "k7", 7, "k8", 8, "k9", 9, "k10", 10);
        assertEquals(10, ten.size(), "mapOf(10 pairs): size 10");
        assertEquals(7, ten.get("k7"), "mapOf(10 pairs): value for k7");

        // Null key rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapOf(null, 1),
                "mapOf(null key): throws NPE");

        // Null value rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapOf("k", null),
                "mapOf(null value): throws NPE");

        // Duplicate key rejection
        assertThrows(IllegalArgumentException.class,
                () -> CollectionBackport.mapOf("k", 1, "k", 2),
                "mapOf(duplicate key): throws IAE");
    }

    static void assertUnmodifiableMap(Map<?, ?> map) {
        assertThrows(UnsupportedOperationException.class,
                () -> map.put(null, null),
                "map should be unmodifiable (put)");
    }

    static void testMapEntry() {
        Map.Entry<String, Integer> e = CollectionBackport.mapEntry("key", 42);
        assertEquals("key", e.getKey(), "mapEntry: key");
        assertEquals(42, e.getValue(), "mapEntry: value");

        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapEntry(null, 1),
                "mapEntry(null key): throws NPE");
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapEntry("k", null),
                "mapEntry(null value): throws NPE");
    }

    @SuppressWarnings("unchecked")
    static void testMapOfEntries() {
        Map.Entry<String, Integer> e1 = CollectionBackport.mapEntry("a", 1);
        Map.Entry<String, Integer> e2 = CollectionBackport.mapEntry("b", 2);

        Map<String, Integer> m = CollectionBackport.mapOfEntries(e1, e2);
        assertEquals(2, m.size(), "mapOfEntries: size 2");
        assertEquals(1, m.get("a"), "mapOfEntries: value for a");
        assertEquals(2, m.get("b"), "mapOfEntries: value for b");
        assertUnmodifiableMap(m);

        // Duplicate key rejection
        Map.Entry<String, Integer> e1dup = CollectionBackport.mapEntry("a", 99);
        assertThrows(IllegalArgumentException.class,
                () -> CollectionBackport.mapOfEntries(e1, e1dup),
                "mapOfEntries(duplicate key): throws IAE");

        // Null entry rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapOfEntries(e1, null),
                "mapOfEntries(null entry): throws NPE");

        // Null entries array rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapOfEntries((Map.Entry<String, Integer>[]) null),
                "mapOfEntries(null array): throws NPE");
    }

    static void testMapCopyOf() {
        Map<String, Integer> src = new LinkedHashMap<>();
        src.put("x", 10);
        src.put("y", 20);
        Map<String, Integer> copy = CollectionBackport.mapCopyOf(src);
        assertEquals(2, copy.size(), "mapCopyOf: size matches source");
        assertEquals(10, copy.get("x"), "mapCopyOf: value for x");
        assertEquals(20, copy.get("y"), "mapCopyOf: value for y");
        assertUnmodifiableMap(copy);

        // Mutating the source doesn't affect the copy
        src.put("z", 30);
        assertEquals(2, copy.size(), "mapCopyOf: copy is independent of source");

        // Null key rejection
        Map<String, Integer> withNullKey = new HashMap<>();
        withNullKey.put(null, 1);
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapCopyOf(withNullKey),
                "mapCopyOf with null key throws NPE");

        // Null value rejection
        Map<String, Integer> withNullValue = new HashMap<>();
        withNullValue.put("k", null);
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapCopyOf(withNullValue),
                "mapCopyOf with null value throws NPE");

        // Null map rejection
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.mapCopyOf(null),
                "mapCopyOf(null): throws NPE");
    }

    // ── Collection.toArray(IntFunction) ──────────────────────────────────────

    static void testToArray() {
        List<String> values = Arrays.asList("a", "b", "c");
        int[] size = {-1};
        String[] result = CollectionBackport.toArray(values, count -> {
            size[0] = count;
            return new String[count];
        });
        assertEquals(3, size[0], "toArray: generator called with size");
        assertEquals(3, result.length, "toArray: result length matches size");
        assertEquals("a", result[0], "toArray: first element");
        assertEquals("c", result[2], "toArray: last element");

        List<String> empty = Collections.emptyList();
        String[] emptyResult = CollectionBackport.toArray(empty, String[]::new);
        assertEquals(0, emptyResult.length, "toArray: empty collection returns empty array");

        assertThrows(NullPointerException.class,
                () -> CollectionBackport.toArray(null, String[]::new),
                "toArray(null collection): throws NPE");
        assertThrows(NullPointerException.class,
                () -> CollectionBackport.toArray(values, null),
                "toArray(null generator): throws NPE");
    }
}
