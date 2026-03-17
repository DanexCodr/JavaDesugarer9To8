package test;

import j9compat.StackWalker;

import java.util.List;
import java.util.stream.Collectors;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.StackWalker}.
 */
public final class StackWalkerBackportTest {

    static void run() {
        section("StackWalkerBackport");

        testWalk();
        testCallerClass();
    }

    private static void testWalk() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        List<String> classNames = walker.walk(stream -> stream
                .limit(5)
                .map(StackWalker.StackFrame::getClassName)
                .collect(Collectors.toList()));
        assertTrue(classNames.contains(StackWalkerBackportTest.class.getName()),
                "StackWalker.walk: includes test class in frames");
    }

    private static void testCallerClass() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        Class<?> caller = callerClass(walker);
        assertEquals(StackWalkerBackportTest.class, caller,
                "StackWalker.getCallerClass: returns caller class");
    }

    private static Class<?> callerClass(StackWalker walker) {
        return walker.getCallerClass();
    }
}
