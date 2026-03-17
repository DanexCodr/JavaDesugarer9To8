package test;

import j9compat.DoubleStreamBackport;
import j9compat.IntStreamBackport;
import j9compat.LongStreamBackport;

import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static test.BackportTestRunner.*;

/**
 * Tests for primitive stream backports.
 */
public final class PrimitiveStreamBackportTest {

    static void run() {
        section("PrimitiveStreamBackport – IntStream");
        testIntStream();

        section("PrimitiveStreamBackport – LongStream");
        testLongStream();

        section("PrimitiveStreamBackport – DoubleStream");
        testDoubleStream();
    }

    private static void testIntStream() {
        int[] taken = IntStreamBackport.takeWhile(IntStream.of(1, 2, -1, 3), v -> v > 0)
                .toArray();
        assertTrue(Arrays.equals(new int[]{1, 2}, taken),
                "IntStream.takeWhile: stops at first failing element");

        int[] dropped = IntStreamBackport.dropWhile(IntStream.of(1, 2, -1, 3), v -> v > 0)
                .toArray();
        assertTrue(Arrays.equals(new int[]{-1, 3}, dropped),
                "IntStream.dropWhile: drops matching prefix");

        int[] iterated = IntStreamBackport.iterate(0, v -> v < 3, v -> v + 1).toArray();
        assertTrue(Arrays.equals(new int[]{0, 1, 2}, iterated),
                "IntStream.iterate: emits until predicate fails");
    }

    private static void testLongStream() {
        long[] taken = LongStreamBackport.takeWhile(LongStream.of(1L, 2L, -1L, 3L), v -> v > 0)
                .toArray();
        assertTrue(Arrays.equals(new long[]{1L, 2L}, taken),
                "LongStream.takeWhile: stops at first failing element");

        long[] dropped = LongStreamBackport.dropWhile(LongStream.of(1L, 2L, -1L, 3L), v -> v > 0)
                .toArray();
        assertTrue(Arrays.equals(new long[]{-1L, 3L}, dropped),
                "LongStream.dropWhile: drops matching prefix");

        long[] iterated = LongStreamBackport.iterate(0L, v -> v < 3L, v -> v + 1L).toArray();
        assertTrue(Arrays.equals(new long[]{0L, 1L, 2L}, iterated),
                "LongStream.iterate: emits until predicate fails");
    }

    private static void testDoubleStream() {
        double[] taken = DoubleStreamBackport.takeWhile(DoubleStream.of(1.0, 2.0, -1.0, 3.0), v -> v > 0.0)
                .toArray();
        assertTrue(Arrays.equals(new double[]{1.0, 2.0}, taken),
                "DoubleStream.takeWhile: stops at first failing element");

        double[] dropped = DoubleStreamBackport.dropWhile(DoubleStream.of(1.0, 2.0, -1.0, 3.0), v -> v > 0.0)
                .toArray();
        assertTrue(Arrays.equals(new double[]{-1.0, 3.0}, dropped),
                "DoubleStream.dropWhile: drops matching prefix");

        double[] iterated = DoubleStreamBackport.iterate(0.0, v -> v < 3.0, v -> v + 1.0).toArray();
        assertTrue(Arrays.equals(new double[]{0.0, 1.0, 2.0}, iterated),
                "DoubleStream.iterate: emits until predicate fails");
    }
}
