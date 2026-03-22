package test;

import j9compat.OptionalDoubleBackport;
import j9compat.OptionalIntBackport;
import j9compat.OptionalLongBackport;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static test.BackportTestRunner.*;

/**
 * Tests for Optional primitive backports.
 */
public final class OptionalPrimitiveBackportTest {

    static void run() {
        section("OptionalPrimitiveBackport – OptionalInt");
        testOptionalInt();

        section("OptionalPrimitiveBackport – OptionalLong");
        testOptionalLong();

        section("OptionalPrimitiveBackport – OptionalDouble");
        testOptionalDouble();
    }

    private static void testOptionalInt() {
        int[] box = {0};
        boolean[] empty = {false};
        OptionalIntBackport.ifPresentOrElse(OptionalInt.of(5),
                v -> box[0] = v,
                () -> empty[0] = true);
        assertEquals(5, box[0], "OptionalInt.ifPresentOrElse: value passed to action");
        assertTrue(!empty[0], "OptionalInt.ifPresentOrElse: emptyAction not called when present");

        box[0] = 0;
        empty[0] = false;
        OptionalIntBackport.ifPresentOrElse(OptionalInt.empty(),
                v -> box[0] = v,
                () -> empty[0] = true);
        assertTrue(empty[0], "OptionalInt.ifPresentOrElse: emptyAction called when absent");

        int sum = OptionalIntBackport.stream(OptionalInt.of(3)).sum();
        assertEquals(3, sum, "OptionalInt.stream: single element stream");
        assertEquals(0, OptionalIntBackport.stream(OptionalInt.empty()).sum(),
                "OptionalInt.stream: empty stream");

        assertEquals(9, OptionalIntBackport.orElseThrow(OptionalInt.of(9)),
                "OptionalInt.orElseThrow: returns value when present");
        assertThrows(java.util.NoSuchElementException.class,
                () -> OptionalIntBackport.orElseThrow(OptionalInt.empty()),
                "OptionalInt.orElseThrow: empty optional throws");
    }

    private static void testOptionalLong() {
        long[] box = {0L};
        boolean[] empty = {false};
        OptionalLongBackport.ifPresentOrElse(OptionalLong.of(7L),
                v -> box[0] = v,
                () -> empty[0] = true);
        assertEquals(7L, box[0], "OptionalLong.ifPresentOrElse: value passed to action");
        assertTrue(!empty[0], "OptionalLong.ifPresentOrElse: emptyAction not called when present");

        box[0] = 0L;
        empty[0] = false;
        OptionalLongBackport.ifPresentOrElse(OptionalLong.empty(),
                v -> box[0] = v,
                () -> empty[0] = true);
        assertTrue(empty[0], "OptionalLong.ifPresentOrElse: emptyAction called when absent");

        long sum = OptionalLongBackport.stream(OptionalLong.of(4L)).sum();
        assertEquals(4L, sum, "OptionalLong.stream: single element stream");
        assertEquals(0L, OptionalLongBackport.stream(OptionalLong.empty()).sum(),
                "OptionalLong.stream: empty stream");

        assertEquals(11L, OptionalLongBackport.orElseThrow(OptionalLong.of(11L)),
                "OptionalLong.orElseThrow: returns value when present");
        assertThrows(java.util.NoSuchElementException.class,
                () -> OptionalLongBackport.orElseThrow(OptionalLong.empty()),
                "OptionalLong.orElseThrow: empty optional throws");
    }

    private static void testOptionalDouble() {
        double[] box = {0.0};
        boolean[] empty = {false};
        OptionalDoubleBackport.ifPresentOrElse(OptionalDouble.of(2.5),
                v -> box[0] = v,
                () -> empty[0] = true);
        assertEquals(2.5, box[0], "OptionalDouble.ifPresentOrElse: value passed to action");
        assertTrue(!empty[0], "OptionalDouble.ifPresentOrElse: emptyAction not called when present");

        box[0] = 0.0;
        empty[0] = false;
        OptionalDoubleBackport.ifPresentOrElse(OptionalDouble.empty(),
                v -> box[0] = v,
                () -> empty[0] = true);
        assertTrue(empty[0], "OptionalDouble.ifPresentOrElse: emptyAction called when absent");

        double sum = OptionalDoubleBackport.stream(OptionalDouble.of(1.5)).sum();
        assertEquals(1.5, sum, "OptionalDouble.stream: single element stream");
        assertEquals(0.0, OptionalDoubleBackport.stream(OptionalDouble.empty()).sum(),
                "OptionalDouble.stream: empty stream");

        assertEquals(7.5, OptionalDoubleBackport.orElseThrow(OptionalDouble.of(7.5)),
                "OptionalDouble.orElseThrow: returns value when present");
        assertThrows(java.util.NoSuchElementException.class,
                () -> OptionalDoubleBackport.orElseThrow(OptionalDouble.empty()),
                "OptionalDouble.orElseThrow: empty optional throws");
    }
}
