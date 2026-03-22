package test;

import j9compat.ReflectionBackport;

import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.ReflectionBackport}.
 */
public final class ReflectionBackportTest {

    static void run() {
        section("ReflectionBackport");

        testStreamTakeWhile();
        testStringIsBlank();
        testOptionalIsEmpty();
        testPredicateNot();
    }

    private static void testStreamTakeWhile() {
        try {
            Method method = ReflectionBackport.getMethod(Stream.class, "takeWhile", Predicate.class);
            Stream<Integer> input = Stream.of(1, 2, 3, 0, 4);
            @SuppressWarnings("unchecked")
            Stream<Integer> output = (Stream<Integer>) ReflectionBackport.invoke(
                    method,
                    input,
                    (Predicate<Integer>) value -> value > 0);
            long count = output.count();
            assertEquals(3L, count, "ReflectionBackport.invoke: takeWhile works");
        } catch (Exception e) {
            fail("ReflectionBackport.invoke threw exception: " + e.getMessage());
        }
    }

    private static void testStringIsBlank() {
        try {
            Method method = ReflectionBackport.getMethod(String.class, "isBlank");
            boolean result = (Boolean) ReflectionBackport.invoke(method, " \t");
            assertTrue(result, "ReflectionBackport.invoke: String.isBlank works");
        } catch (Exception e) {
            fail("ReflectionBackport.invoke String.isBlank threw: " + e.getMessage());
        }
    }

    private static void testOptionalIsEmpty() {
        try {
            Method method = ReflectionBackport.getMethod(java.util.Optional.class, "isEmpty");
            boolean result = (Boolean) ReflectionBackport.invoke(method, java.util.Optional.empty());
            assertTrue(result, "ReflectionBackport.invoke: Optional.isEmpty works");
        } catch (Exception e) {
            fail("ReflectionBackport.invoke Optional.isEmpty threw: " + e.getMessage());
        }
    }

    private static void testPredicateNot() {
        try {
            Method method = ReflectionBackport.getMethod(Predicate.class, "not", Predicate.class);
            @SuppressWarnings("unchecked")
            Predicate<String> predicate = (Predicate<String>) ReflectionBackport.invoke(
                    method, null, (Predicate<String>) value -> value.isEmpty());
            assertTrue(predicate.test("value"),
                    "ReflectionBackport.invoke: Predicate.not works");
        } catch (Exception e) {
            fail("ReflectionBackport.invoke Predicate.not threw: " + e.getMessage());
        }
    }
}
