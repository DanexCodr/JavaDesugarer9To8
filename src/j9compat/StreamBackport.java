package j9compat;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Java 8-compatible backport of the {@link java.util.stream.Stream} methods
 * added in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code Stream.takeWhile(Predicate)} – returns elements while predicate
 *       is true; stops (exclusive) at the first failing element.</li>
 *   <li>{@code Stream.dropWhile(Predicate)} – skips elements while predicate
 *       is true; returns all remaining elements starting from the first failing
 *       one.</li>
 *   <li>{@code Stream.ofNullable(T)} – a one-element stream if the value is
 *       non-null, otherwise an empty stream.</li>
 *   <li>{@code Stream.iterate(seed, hasNext, f)} – three-argument iterate with
 *       a termination predicate (analogous to a for-loop).</li>
 * </ul>
 *
 * <p>All methods are {@code static} so the desugarer can redirect both
 * instance-method calls (by prepending the receiver to the argument list) and
 * static-interface-method calls without extra stack manipulation.
 */
public final class StreamBackport {

    private StreamBackport() {}

    // ── Stream.takeWhile ─────────────────────────────────────────────────────

    /**
     * Backport of {@code Stream.takeWhile(predicate)}.
     *
     * <p>Returns a stream consisting of the longest prefix of elements of
     * {@code stream} that match {@code predicate}.
     */
    public static <T> Stream<T> takeWhile(Stream<T> stream, Predicate<? super T> predicate) {
        Objects.requireNonNull(stream,    "stream");
        Objects.requireNonNull(predicate, "predicate");

        Spliterator<T> source = stream.spliterator();
        Spliterator<T> spliterator = new TakeWhileSpliterator<>(source, predicate);
        return StreamSupport.stream(spliterator, stream.isParallel())
                .onClose(stream::close);
    }

    // ── Stream.dropWhile ─────────────────────────────────────────────────────

    /**
     * Backport of {@code Stream.dropWhile(predicate)}.
     *
     * <p>Returns a stream consisting of the remaining elements of {@code stream}
     * after dropping the longest prefix that matches {@code predicate}.
     */
    public static <T> Stream<T> dropWhile(Stream<T> stream, Predicate<? super T> predicate) {
        Objects.requireNonNull(stream,    "stream");
        Objects.requireNonNull(predicate, "predicate");

        Spliterator<T> source = stream.spliterator();
        Spliterator<T> spliterator = new DropWhileSpliterator<>(source, predicate);
        return StreamSupport.stream(spliterator, stream.isParallel())
                .onClose(stream::close);
    }

    // ── Stream.ofNullable ────────────────────────────────────────────────────

    /**
     * Backport of {@code Stream.ofNullable(t)}.
     *
     * <p>Returns a sequential {@link Stream} containing a single element if
     * {@code t} is non-null; otherwise returns an empty stream.
     */
    public static <T> Stream<T> ofNullable(T t) {
        return t == null ? Stream.empty() : Stream.of(t);
    }

    // ── Stream.iterate(seed, hasNext, f) ────────────────────────────────────

    /**
     * Backport of {@code Stream.iterate(seed, hasNext, f)}.
     *
     * <p>Returns a sequential ordered stream produced by iterative application
     * of {@code f} to an initial element {@code seed}, conditioned on
     * satisfying {@code hasNext}.  The stream terminates as soon as
     * {@code hasNext.test(t)} returns {@code false}.
     *
     * <p>This is analogous to:
     * <pre>
     *   for (T t = seed; hasNext.test(t); t = f.apply(t)) { ... }
     * </pre>
     */
    public static <T> Stream<T> iterate(T seed,
                                         Predicate<? super T> hasNext,
                                         UnaryOperator<T> f) {
        Objects.requireNonNull(hasNext, "hasNext");
        Objects.requireNonNull(f,       "f");

        // Build a lazily-evaluated Spliterator so we don't materialise the
        // entire sequence before returning.
        Spliterator<T> spliterator = new Spliterators.AbstractSpliterator<T>(
                Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.IMMUTABLE) {

            private T next = seed;
            private boolean started = false;

            @Override
            public boolean tryAdvance(java.util.function.Consumer<? super T> action) {
                T val;
                if (!started) {
                    started = true;
                    val = next;
                } else {
                    val = f.apply(next);
                    next = val;
                }
                if (!hasNext.test(val)) return false;
                next = val;
                action.accept(val);
                return true;
            }
        };

        return StreamSupport.stream(spliterator, false);
    }

    // ── Spliterators ──────────────────────────────────────────────────────────

    private static final class TakeWhileSpliterator<T> extends Spliterators.AbstractSpliterator<T> {
        private final Spliterator<T> source;
        private final Predicate<? super T> predicate;
        private boolean taking = true;

        TakeWhileSpliterator(Spliterator<T> source, Predicate<? super T> predicate) {
            super(source.estimateSize(), trimmedCharacteristics(source));
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            if (!taking) {
                return false;
            }
            final boolean[] emitted = {false};
            boolean advanced = source.tryAdvance(value -> {
                if (predicate.test(value)) {
                    emitted[0] = true;
                    action.accept(value);
                } else {
                    taking = false;
                }
            });
            return advanced && emitted[0];
        }

        @Override
        public Comparator<? super T> getComparator() {
            return source.getComparator();
        }
    }

    private static final class DropWhileSpliterator<T> extends Spliterators.AbstractSpliterator<T> {
        private final Spliterator<T> source;
        private final Predicate<? super T> predicate;
        private boolean dropping = true;

        DropWhileSpliterator(Spliterator<T> source, Predicate<? super T> predicate) {
            super(source.estimateSize(), trimmedCharacteristics(source));
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            if (!dropping) {
                return source.tryAdvance(action);
            }
            boolean[] emitted = {false};
            while (dropping && !emitted[0]) {
                boolean advanced = source.tryAdvance(value -> {
                    if (dropping && predicate.test(value)) {
                        return;
                    }
                    dropping = false;
                    emitted[0] = true;
                    action.accept(value);
                });
                if (!advanced) {
                    return false;
                }
            }
            return emitted[0];
        }

        @Override
        public Comparator<? super T> getComparator() {
            return source.getComparator();
        }
    }

    private static int trimmedCharacteristics(Spliterator<?> source) {
        return source.characteristics() & ~(Spliterator.SIZED | Spliterator.SUBSIZED);
    }
}
