package j9compat;

import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

/**
 * Java 8-compatible backport of {@link java.util.stream.LongStream} methods
 * added in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code LongStream.takeWhile(LongPredicate)}</li>
 *   <li>{@code LongStream.dropWhile(LongPredicate)}</li>
 *   <li>{@code LongStream.iterate(seed, hasNext, f)}</li>
 * </ul>
 */
public final class LongStreamBackport {

    private LongStreamBackport() {}

    public static LongStream takeWhile(LongStream stream, LongPredicate predicate) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(predicate, "predicate");

        Spliterator.OfLong source = stream.spliterator();
        Spliterator.OfLong spliterator = new TakeWhileSpliterator(source, predicate);
        return StreamSupport.longStream(spliterator, stream.isParallel())
                .onClose(stream::close);
    }

    public static LongStream dropWhile(LongStream stream, LongPredicate predicate) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(predicate, "predicate");

        Spliterator.OfLong source = stream.spliterator();
        Spliterator.OfLong spliterator = new DropWhileSpliterator(source, predicate);
        return StreamSupport.longStream(spliterator, stream.isParallel())
                .onClose(stream::close);
    }

    public static LongStream iterate(long seed,
                                     LongPredicate hasNext,
                                     LongUnaryOperator f) {
        Objects.requireNonNull(hasNext, "hasNext");
        Objects.requireNonNull(f, "f");

        Spliterator.OfLong spliterator = new Spliterators.AbstractLongSpliterator(
                Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.IMMUTABLE) {

            private long next = seed;
            private boolean started = false;

            @Override
            public boolean tryAdvance(LongConsumer action) {
                long val;
                if (!started) {
                    started = true;
                    val = next;
                } else {
                    val = f.applyAsLong(next);
                    next = val;
                }
                if (!hasNext.test(val)) return false;
                next = val;
                action.accept(val);
                return true;
            }
        };

        return StreamSupport.longStream(spliterator, false);
    }

    private static final class TakeWhileSpliterator extends Spliterators.AbstractLongSpliterator {
        private final Spliterator.OfLong source;
        private final LongPredicate predicate;
        private boolean taking = true;

        TakeWhileSpliterator(Spliterator.OfLong source, LongPredicate predicate) {
            super(source.estimateSize(), trimmedCharacteristics(source));
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(LongConsumer action) {
            if (!taking) {
                return false;
            }
            final boolean[] emitted = {false};
            boolean advanced = source.tryAdvance((long value) -> {
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

    private static final class DropWhileSpliterator extends Spliterators.AbstractLongSpliterator {
        private final Spliterator.OfLong source;
        private final LongPredicate predicate;
        private boolean dropping = true;

        DropWhileSpliterator(Spliterator.OfLong source, LongPredicate predicate) {
            super(source.estimateSize(), trimmedCharacteristics(source));
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(LongConsumer action) {
            if (!dropping) {
                return source.tryAdvance(action);
            }
            boolean[] emitted = {false};
            while (dropping && !emitted[0]) {
                boolean advanced = source.tryAdvance((long value) -> {
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
