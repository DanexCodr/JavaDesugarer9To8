package j9compat;

import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.DoubleConsumer;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.StreamSupport;

/**
 * Java 8-compatible backport of {@link java.util.stream.DoubleStream} methods
 * added in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code DoubleStream.takeWhile(DoublePredicate)}</li>
 *   <li>{@code DoubleStream.dropWhile(DoublePredicate)}</li>
 *   <li>{@code DoubleStream.iterate(seed, hasNext, f)}</li>
 * </ul>
 */
public final class DoubleStreamBackport {

    private DoubleStreamBackport() {}

    public static DoubleStream takeWhile(DoubleStream stream, DoublePredicate predicate) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(predicate, "predicate");

        Spliterator.OfDouble source = stream.spliterator();
        Spliterator.OfDouble spliterator = new TakeWhileSpliterator(source, predicate);
        return StreamSupport.doubleStream(spliterator, stream.isParallel())
                .onClose(stream::close);
    }

    public static DoubleStream dropWhile(DoubleStream stream, DoublePredicate predicate) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(predicate, "predicate");

        Spliterator.OfDouble source = stream.spliterator();
        Spliterator.OfDouble spliterator = new DropWhileSpliterator(source, predicate);
        return StreamSupport.doubleStream(spliterator, stream.isParallel())
                .onClose(stream::close);
    }

    public static DoubleStream iterate(double seed,
                                       DoublePredicate hasNext,
                                       DoubleUnaryOperator f) {
        Objects.requireNonNull(hasNext, "hasNext");
        Objects.requireNonNull(f, "f");

        Spliterator.OfDouble spliterator = new Spliterators.AbstractDoubleSpliterator(
                Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.IMMUTABLE) {

            private double next = seed;
            private boolean started = false;

            @Override
            public boolean tryAdvance(DoubleConsumer action) {
                double val;
                if (!started) {
                    started = true;
                    val = next;
                } else {
                    val = f.applyAsDouble(next);
                    next = val;
                }
                if (!hasNext.test(val)) return false;
                next = val;
                action.accept(val);
                return true;
            }
        };

        return StreamSupport.doubleStream(spliterator, false);
    }

    private static final class TakeWhileSpliterator extends Spliterators.AbstractDoubleSpliterator {
        private final Spliterator.OfDouble source;
        private final DoublePredicate predicate;
        private boolean taking = true;

        TakeWhileSpliterator(Spliterator.OfDouble source, DoublePredicate predicate) {
            super(source.estimateSize(), trimmedCharacteristics(source));
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(DoubleConsumer action) {
            if (!taking) {
                return false;
            }
            final boolean[] emitted = {false};
            boolean advanced = source.tryAdvance((double value) -> {
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

    private static final class DropWhileSpliterator extends Spliterators.AbstractDoubleSpliterator {
        private final Spliterator.OfDouble source;
        private final DoublePredicate predicate;
        private boolean dropping = true;

        DropWhileSpliterator(Spliterator.OfDouble source, DoublePredicate predicate) {
            super(source.estimateSize(), trimmedCharacteristics(source));
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(DoubleConsumer action) {
            if (!dropping) {
                return source.tryAdvance(action);
            }
            boolean[] emitted = {false};
            while (dropping && !emitted[0]) {
                boolean advanced = source.tryAdvance((double value) -> {
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
