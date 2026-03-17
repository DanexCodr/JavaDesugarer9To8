package j9compat;

import java.util.*;

/**
 * Java 8-compatible backport of the immutable-collection factory methods
 * introduced in Java 9.
 *
 * <h2>Semantics preserved</h2>
 * <ul>
 *   <li>All returned collections are <em>unmodifiable</em>.</li>
 *   <li>{@code null} elements / keys / values throw {@link NullPointerException}
 *       (same as the Java 9 originals).</li>
 *   <li>Duplicate keys in {@code mapOf} throw {@link IllegalArgumentException}
 *       (same as the Java 9 originals).</li>
 *   <li>Duplicate elements in {@code setOf} throw
 *       {@link IllegalArgumentException} (same as the Java 9 originals).</li>
 * </ul>
 *
 * <p>The desugarer's {@link desugarer.MethodDesugarer} redirects all calls to
 * {@code java.util.List.of(…)}, {@code java.util.Set.of(…)},
 * {@code java.util.Map.of(…)}, etc. to the corresponding static methods in
 * this class.
 */
@SuppressWarnings({"unchecked", "varargs"})
public final class CollectionBackport {

    private CollectionBackport() {}

    // ── List.of ─────────────────────────────────────────────────────────────

    public static <E> List<E> listOf() {
        return Collections.emptyList();
    }

    public static <E> List<E> listOf(E e1) {
        requireNonNull(e1, "element");
        return Collections.singletonList(e1);
    }

    public static <E> List<E> listOf(E e1, E e2) {
        return unmodifiableList(e1, e2);
    }

    public static <E> List<E> listOf(E e1, E e2, E e3) {
        return unmodifiableList(e1, e2, e3);
    }

    public static <E> List<E> listOf(E e1, E e2, E e3, E e4) {
        return unmodifiableList(e1, e2, e3, e4);
    }

    public static <E> List<E> listOf(E e1, E e2, E e3, E e4, E e5) {
        return unmodifiableList(e1, e2, e3, e4, e5);
    }

    public static <E> List<E> listOf(E e1, E e2, E e3, E e4, E e5,
                                      E e6) {
        return unmodifiableList(e1, e2, e3, e4, e5, e6);
    }

    public static <E> List<E> listOf(E e1, E e2, E e3, E e4, E e5,
                                      E e6, E e7) {
        return unmodifiableList(e1, e2, e3, e4, e5, e6, e7);
    }

    public static <E> List<E> listOf(E e1, E e2, E e3, E e4, E e5,
                                      E e6, E e7, E e8) {
        return unmodifiableList(e1, e2, e3, e4, e5, e6, e7, e8);
    }

    public static <E> List<E> listOf(E e1, E e2, E e3, E e4, E e5,
                                      E e6, E e7, E e8, E e9) {
        return unmodifiableList(e1, e2, e3, e4, e5, e6, e7, e8, e9);
    }

    public static <E> List<E> listOf(E e1, E e2, E e3, E e4, E e5,
                                      E e6, E e7, E e8, E e9, E e10) {
        return unmodifiableList(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10);
    }

    @SafeVarargs
    public static <E> List<E> listOf(E... elements) {
        E[] copy = elements.clone();
        for (int i = 0; i < copy.length; i++) {
            requireNonNull(copy[i], "element[" + i + "]");
        }
        return Collections.unmodifiableList(Arrays.asList(copy));
    }

    public static <E> List<E> listCopyOf(Collection<? extends E> coll) {
        Objects.requireNonNull(coll, "collection");
        List<E> copy = new ArrayList<>(coll.size());
        for (E e : coll) {
            requireNonNull(e, "element");
            copy.add(e);
        }
        return Collections.unmodifiableList(copy);
    }

    // ── Set.of ──────────────────────────────────────────────────────────────

    public static <E> Set<E> setOf() {
        return Collections.emptySet();
    }

    public static <E> Set<E> setOf(E e1) {
        requireNonNull(e1, "element");
        return Collections.singleton(e1);
    }

    public static <E> Set<E> setOf(E e1, E e2) {
        return unmodifiableSet(e1, e2);
    }

    public static <E> Set<E> setOf(E e1, E e2, E e3) {
        return unmodifiableSet(e1, e2, e3);
    }

    public static <E> Set<E> setOf(E e1, E e2, E e3, E e4) {
        return unmodifiableSet(e1, e2, e3, e4);
    }

    public static <E> Set<E> setOf(E e1, E e2, E e3, E e4, E e5) {
        return unmodifiableSet(e1, e2, e3, e4, e5);
    }

    public static <E> Set<E> setOf(E e1, E e2, E e3, E e4, E e5,
                                    E e6) {
        return unmodifiableSet(e1, e2, e3, e4, e5, e6);
    }

    public static <E> Set<E> setOf(E e1, E e2, E e3, E e4, E e5,
                                    E e6, E e7) {
        return unmodifiableSet(e1, e2, e3, e4, e5, e6, e7);
    }

    public static <E> Set<E> setOf(E e1, E e2, E e3, E e4, E e5,
                                    E e6, E e7, E e8) {
        return unmodifiableSet(e1, e2, e3, e4, e5, e6, e7, e8);
    }

    public static <E> Set<E> setOf(E e1, E e2, E e3, E e4, E e5,
                                    E e6, E e7, E e8, E e9) {
        return unmodifiableSet(e1, e2, e3, e4, e5, e6, e7, e8, e9);
    }

    public static <E> Set<E> setOf(E e1, E e2, E e3, E e4, E e5,
                                    E e6, E e7, E e8, E e9, E e10) {
        return unmodifiableSet(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10);
    }

    @SafeVarargs
    public static <E> Set<E> setOf(E... elements) {
        Set<E> set = new LinkedHashSet<>(elements.length * 2);
        for (E e : elements) {
            requireNonNull(e, "element");
            if (!set.add(e)) {
                throw new IllegalArgumentException("Duplicate element: " + e);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public static <E> Set<E> setCopyOf(Collection<? extends E> coll) {
        Objects.requireNonNull(coll, "collection");
        Set<E> set = new LinkedHashSet<>(coll.size() * 2);
        for (E e : coll) {
            requireNonNull(e, "element");
            set.add(e);
        }
        return Collections.unmodifiableSet(set);
    }

    // ── Map.of ──────────────────────────────────────────────────────────────

    public static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        requireNonNull(k1, "key");
        requireNonNull(v1, "value");
        return Collections.singletonMap(k1, v1);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        return buildMap(k1, v1, k2, v2);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2,
                                          K k3, V v3) {
        return buildMap(k1, v1, k2, v2, k3, v3);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2,
                                          K k3, V v3, K k4, V v4) {
        return buildMap(k1, v1, k2, v2, k3, v3, k4, v4);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2,
                                          K k3, V v3, K k4, V v4,
                                          K k5, V v5) {
        return buildMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2,
                                          K k3, V v3, K k4, V v4,
                                          K k5, V v5, K k6, V v6) {
        return buildMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2,
                                          K k3, V v3, K k4, V v4,
                                          K k5, V v5, K k6, V v6,
                                          K k7, V v7) {
        return buildMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2,
                                          K k3, V v3, K k4, V v4,
                                          K k5, V v5, K k6, V v6,
                                          K k7, V v7, K k8, V v8) {
        return buildMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2,
                                          K k3, V v3, K k4, V v4,
                                          K k5, V v5, K k6, V v6,
                                          K k7, V v7, K k8, V v8,
                                          K k9, V v9) {
        return buildMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8, k9, v9);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2,
                                          K k3, V v3, K k4, V v4,
                                          K k5, V v5, K k6, V v6,
                                          K k7, V v7, K k8, V v8,
                                          K k9, V v9, K k10, V v10) {
        return buildMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
    }

    public static <K, V> Map.Entry<K, V> mapEntry(K key, V value) {
        requireNonNull(key, "key");
        requireNonNull(value, "value");
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    @SafeVarargs
    public static <K, V> Map<K, V> mapOfEntries(
            Map.Entry<? extends K, ? extends V>... entries) {
        Objects.requireNonNull(entries, "entries");
        Map<K, V> map = new LinkedHashMap<>(entries.length * 2);
        for (Map.Entry<? extends K, ? extends V> e : entries) {
            Objects.requireNonNull(e, "entry");
            K k = e.getKey();
            V v = e.getValue();
            requireNonNull(k, "key");
            requireNonNull(v, "value");
            if (map.containsKey(k)) {
                throw new IllegalArgumentException("Duplicate key: " + k);
            }
            map.put(k, v);
        }
        return Collections.unmodifiableMap(map);
    }

    public static <K, V> Map<K, V> mapCopyOf(Map<? extends K, ? extends V> m) {
        Objects.requireNonNull(m, "map");
        Map<K, V> copy = new LinkedHashMap<>(m.size() * 2);
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            requireNonNull(e.getKey(),   "key");
            requireNonNull(e.getValue(), "value");
            copy.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    @SafeVarargs
    private static <E> List<E> unmodifiableList(E... elements) {
        for (int i = 0; i < elements.length; i++) {
            requireNonNull(elements[i], "element[" + i + "]");
        }
        return Collections.unmodifiableList(Arrays.asList(elements.clone()));
    }

    @SafeVarargs
    private static <E> Set<E> unmodifiableSet(E... elements) {
        Set<E> set = new LinkedHashSet<>(elements.length * 2);
        for (E e : elements) {
            requireNonNull(e, "element");
            if (!set.add(e)) {
                throw new IllegalArgumentException("Duplicate element: " + e);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    /** Builds a map from alternating key/value pairs. */
    private static <K, V> Map<K, V> buildMap(Object... kvPairs) {
        Map<K, V> map = new LinkedHashMap<>(kvPairs.length);
        for (int i = 0; i < kvPairs.length; i += 2) {
            K k = (K) kvPairs[i];
            V v = (V) kvPairs[i + 1];
            requireNonNull(k, "key");
            requireNonNull(v, "value");
            if (map.containsKey(k)) {
                throw new IllegalArgumentException("Duplicate key: " + k);
            }
            map.put(k, v);
        }
        return Collections.unmodifiableMap(map);
    }

    private static void requireNonNull(Object obj, String label) {
        if (obj == null) {
            throw new NullPointerException(label + " must not be null");
        }
    }
}
