package j9compat;

import java.util.*;
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

        Iterator<T> iter = Spliterators.iterator(stream.spliterator());
        List<T> result = new ArrayList<>();
        while (iter.hasNext()) {
            T t = iter.next();
            if (!predicate.test(t)) break;
            result.add(t);
        }
        return result.stream();
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

        Iterator<T> iter = Spliterators.iterator(stream.spliterator());
        List<T> result = new ArrayList<>();
        boolean dropping = true;
        while (iter.hasNext()) {
            T t = iter.next();
            if (dropping && predicate.test(t)) {
                continue;
            }
            dropping = false;
            result.add(t);
        }
        return result.stream();
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
}
