package j9compat;

import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * Java 8-compatible backport of {@link java.util.stream.IntStream} methods
 * added in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code IntStream.takeWhile(IntPredicate)}</li>
 *   <li>{@code IntStream.dropWhile(IntPredicate)}</li>
 *   <li>{@code IntStream.iterate(seed, hasNext, f)}</li>
 * </ul>
 */
public final class IntStreamBackport {

    private IntStreamBackport() {}

    public static IntStream takeWhile(IntStream stream, IntPredicate predicate) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(predicate, "predicate");

        Spliterator.OfInt source = stream.spliterator();
        Spliterator.OfInt spliterator = new TakeWhileSpliterator(source, predicate);
        return StreamSupport.intStream(spliterator, stream.isParallel())
                .onClose(stream::close);
    }

    public static IntStream dropWhile(IntStream stream, IntPredicate predicate) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(predicate, "predicate");

        Spliterator.OfInt source = stream.spliterator();
        Spliterator.OfInt spliterator = new DropWhileSpliterator(source, predicate);
        return StreamSupport.intStream(spliterator, stream.isParallel())
                .onClose(stream::close);
    }

    public static IntStream iterate(int seed,
                                    IntPredicate hasNext,
                                    IntUnaryOperator f) {
        Objects.requireNonNull(hasNext, "hasNext");
        Objects.requireNonNull(f, "f");

        Spliterator.OfInt spliterator = new Spliterators.AbstractIntSpliterator(
                Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.IMMUTABLE) {

            private int next = seed;
            private boolean started = false;

            @Override
            public boolean tryAdvance(IntConsumer action) {
                int val;
                if (!started) {
                    started = true;
                    val = next;
                } else {
                    val = f.applyAsInt(next);
                    next = val;
                }
                if (!hasNext.test(val)) return false;
                next = val;
                action.accept(val);
                return true;
            }
        };

        return StreamSupport.intStream(spliterator, false);
    }

    private static final class TakeWhileSpliterator extends Spliterators.AbstractIntSpliterator {
        private final Spliterator.OfInt source;
        private final IntPredicate predicate;
        private boolean taking = true;

        TakeWhileSpliterator(Spliterator.OfInt source, IntPredicate predicate) {
            super(source.estimateSize(), trimmedCharacteristics(source));
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            if (!taking) {
                return false;
            }
            final boolean[] emitted = {false};
            boolean advanced = source.tryAdvance((int value) -> {
                if (predicate.test(value)) {
                    emitted[0] = true;
                    action.accept(value);
                } else {
                    taking = false;
                }
            });
            return advanced && emitted[0];
        }
    }

    private static final class DropWhileSpliterator extends Spliterators.AbstractIntSpliterator {
        private final Spliterator.OfInt source;
        private final IntPredicate predicate;
        private boolean dropping = true;

        DropWhileSpliterator(Spliterator.OfInt source, IntPredicate predicate) {
            super(source.estimateSize(), trimmedCharacteristics(source));
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            if (!dropping) {
                return source.tryAdvance(action);
            }
            boolean[] emitted = {false};
            while (dropping && !emitted[0]) {
                boolean advanced = source.tryAdvance((int value) -> {
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
    }

    private static int trimmedCharacteristics(Spliterator<?> source) {
        return source.characteristics() & ~(Spliterator.SIZED | Spliterator.SUBSIZED);
    }
}
