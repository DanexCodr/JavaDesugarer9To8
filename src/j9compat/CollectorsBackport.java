package j9compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * Java 8-compatible backport of {@link java.util.stream.Collectors} additions
 * introduced in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code Collectors.filtering(predicate, downstream)}</li>
 *   <li>{@code Collectors.flatMapping(mapper, downstream)}</li>
 *   <li>{@code Collectors.toUnmodifiableList/Set/Map}</li>
 * </ul>
 */
public final class CollectorsBackport {

    private CollectorsBackport() {}

    public static <T, A, R> Collector<T, A, R> filtering(
            Predicate<? super T> predicate,
            Collector<? super T, A, R> downstream) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(downstream, "downstream");

        BiConsumer<A, ? super T> accumulator = downstream.accumulator();
        return Collector.of(
                downstream.supplier(),
                (acc, t) -> {
                    if (predicate.test(t)) {
                        accumulator.accept(acc, t);
                    }
                },
                downstream.combiner(),
                downstream.finisher(),
                downstream.characteristics().toArray(new Collector.Characteristics[0]));
    }

    public static <T, U, A, R> Collector<T, A, R> flatMapping(
            Function<? super T, ? extends Stream<? extends U>> mapper,
            Collector<? super U, A, R> downstream) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(downstream, "downstream");

        BiConsumer<A, ? super U> accumulator = downstream.accumulator();
        return Collector.of(
                downstream.supplier(),
                (acc, t) -> {
                    Stream<? extends U> mapped = Objects.requireNonNull(
                            mapper.apply(t), "mapped stream");
                    try (Stream<? extends U> stream = mapped) {
                        stream.sequential().forEach(u -> accumulator.accept(acc, u));
                    }
                },
                downstream.combiner(),
                downstream.finisher(),
                downstream.characteristics().toArray(new Collector.Characteristics[0]));
    }

    public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
        return Collector.<T, List<T>, List<T>>of(
                () -> new ArrayList<T>(),
                (list, element) -> {
                    Objects.requireNonNull(element, "element");
                    list.add(element);
                },
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                list -> Collections.unmodifiableList(new ArrayList<T>(list)));
    }

    public static <T> Collector<T, ?, Set<T>> toUnmodifiableSet() {
        return Collector.<T, Set<T>, Set<T>>of(
                () -> new HashSet<T>(),
                (set, element) -> {
                    Objects.requireNonNull(element, "element");
                    set.add(element);
                },
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                set -> Collections.unmodifiableSet(new HashSet<T>(set)));
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> toUnmodifiableMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper");
        Objects.requireNonNull(valueMapper, "valueMapper");

        return Collector.<T, Map<K, U>, Map<K, U>>of(
                () -> new HashMap<K, U>(),
                (map, element) -> putUnique(map,
                        Objects.requireNonNull(keyMapper.apply(element), "key"),
                        Objects.requireNonNull(valueMapper.apply(element), "value")),
                (left, right) -> {
                    for (Map.Entry<K, U> entry : right.entrySet()) {
                        putUnique(left, entry.getKey(), entry.getValue());
                    }
                    return left;
                },
                map -> Collections.unmodifiableMap(new HashMap<K, U>(map)));
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> toUnmodifiableMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction) {
        Objects.requireNonNull(keyMapper, "keyMapper");
        Objects.requireNonNull(valueMapper, "valueMapper");
        Objects.requireNonNull(mergeFunction, "mergeFunction");

        return Collector.<T, Map<K, U>, Map<K, U>>of(
                () -> new HashMap<K, U>(),
                (map, element) -> mergeValue(map,
                        Objects.requireNonNull(keyMapper.apply(element), "key"),
                        Objects.requireNonNull(valueMapper.apply(element), "value"),
                        mergeFunction),
                (left, right) -> {
                    for (Map.Entry<K, U> entry : right.entrySet()) {
                        mergeValue(left, entry.getKey(), entry.getValue(), mergeFunction);
                    }
                    return left;
                },
                map -> Collections.unmodifiableMap(new HashMap<K, U>(map)));
    }

    private static <K, U> void putUnique(Map<K, U> map, K key, U value) {
        U existing = map.putIfAbsent(key, value);
        if (existing != null) {
            throw new IllegalStateException("Duplicate key: " + key);
        }
    }

    private static <K, U> void mergeValue(Map<K, U> map, K key, U value,
                                          BinaryOperator<U> mergeFunction) {
        if (!map.containsKey(key)) {
            map.put(key, value);
            return;
        }
        U existing = map.get(key);
        U merged = Objects.requireNonNull(mergeFunction.apply(existing, value),
                "mergeFunction result");
        map.put(key, merged);
    }
}
