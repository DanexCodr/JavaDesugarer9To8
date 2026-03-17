package test;

import j9compat.StackWalker;

import java.util.ArrayList;
import java.util.EnumSet;
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
        testForEach();
        testFrameDetails();
        testClassReferenceOption();
        testMaxDepth();
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

    private static void testForEach() {
        StackWalker walker = StackWalker.getInstance();
        List<String> methods = new ArrayList<>();
        walker.forEach(frame -> methods.add(frame.getMethodName()));
        assertTrue(methods.contains("testForEach"),
                "StackWalker.forEach: includes current method");
    }

    private static void testFrameDetails() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        List<StackWalker.StackFrame> frames = walker.walk(stream -> stream
                .limit(1)
                .collect(Collectors.toList()));
        assertTrue(!frames.isEmpty(), "StackWalker.walk: returns at least one frame");
        StackWalker.StackFrame frame = frames.get(0);
        assertEquals(StackWalkerBackportTest.class.getName(), frame.getClassName(),
                "StackFrame.getClassName: matches test class");
        assertEquals(StackWalkerBackportTest.class, frame.getDeclaringClass(),
                "StackFrame.getDeclaringClass: resolves with RETAIN_CLASS_REFERENCE");
        assertEquals(-1, frame.getByteCodeIndex(),
                "StackFrame.getByteCodeIndex: not retained");
        assertTrue(frame.getFileName() != null, "StackFrame.getFileName: available");
        assertTrue(frame.getLineNumber() > 0, "StackFrame.getLineNumber: positive");
        assertTrue(!frame.isNativeMethod(), "StackFrame.isNativeMethod: false for Java frame");
        assertTrue(frame.toStackTraceElement() != null,
                "StackFrame.toStackTraceElement: available");
        assertThrows(UnsupportedOperationException.class, frame::getMethodType,
                "StackFrame.getMethodType: unsupported");
        assertThrows(UnsupportedOperationException.class, frame::getDescriptor,
                "StackFrame.getDescriptor: unsupported");
    }

    private static void testClassReferenceOption() {
        StackWalker walker = StackWalker.getInstance();
        StackWalker.StackFrame frame = walker.walk(stream -> stream
                .limit(1)
                .collect(Collectors.toList()))
                .get(0);
        assertThrows(UnsupportedOperationException.class, frame::getDeclaringClass,
                "StackFrame.getDeclaringClass: requires RETAIN_CLASS_REFERENCE");
    }

    private static void testMaxDepth() {
        StackWalker walker = StackWalker.getInstance(
                EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), 1);
        List<StackWalker.StackFrame> frames = walker.walk(stream -> stream
                .collect(Collectors.toList()));
        assertEquals(1, frames.size(), "StackWalker maxDepth: limits frames");
    }
}
