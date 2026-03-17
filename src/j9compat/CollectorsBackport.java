package j9compat;

import java.util.Objects;
import java.util.function.BiConsumer;
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
}
