package test;

import j9compat.StackWalker;

import java.lang.invoke.MethodType;
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

        testCacheLimit();
        testWalk();
        testCallerClass();
        testForEach();
        testFrameDetails();
        testMethodDescriptor();
        testClassReferenceOption();
        testMaxDepth();
    }

    private static void testCacheLimit() {
        String property = "j9compat.stackwalker.cache.size";
        String original = System.getProperty(property);
        System.setProperty(property, "4");
        try {
            Class<?> resolver = Class.forName("j9compat.StackWalker$MethodDescriptorResolver");
            java.lang.reflect.Field cacheLimitField = resolver.getDeclaredField("CACHE_LIMIT");
            cacheLimitField.setAccessible(true);
            int cacheLimit = cacheLimitField.getInt(null);
            assertEquals(4, cacheLimit,
                    "StackWalker descriptor cache limit: honors system property");

            java.lang.reflect.Method resolveMethod = resolver.getDeclaredMethod("resolveCacheLimit");
            resolveMethod.setAccessible(true);

            System.setProperty(property, "0");
            int zeroValue = (Integer) resolveMethod.invoke(null);
            assertEquals(256, zeroValue,
                    "StackWalker descriptor cache limit: zero defaults to 256");
            assertEquals(4, cacheLimitField.getInt(null),
                    "StackWalker descriptor cache limit: initialized once");

            System.setProperty(property, "not-a-number");
            int invalidValue = (Integer) resolveMethod.invoke(null);
            assertEquals(256, invalidValue,
                    "StackWalker descriptor cache limit: invalid value defaults to 256");

            System.setProperty(property, "1000000");
            int cappedValue = (Integer) resolveMethod.invoke(null);
            assertEquals(65536, cappedValue,
                    "StackWalker descriptor cache limit: caps large values");
        } catch (ReflectiveOperationException e) {
            fail("StackWalker descriptor cache limit: reflection failed (" + e.getClass().getSimpleName() + ")");
        } finally {
            if (original == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, original);
            }
        }
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
        assertTrue(frame.getByteCodeIndex() >= 0,
                "StackFrame.getByteCodeIndex: resolved");
        assertTrue(frame.getFileName() != null, "StackFrame.getFileName: available");
        assertTrue(frame.getLineNumber() > 0, "StackFrame.getLineNumber: positive");
        assertTrue(!frame.isNativeMethod(), "StackFrame.isNativeMethod: false for Java frame");
        assertTrue(frame.toStackTraceElement() != null,
                "StackFrame.toStackTraceElement: available");
        assertTrue(frame.getDescriptor() != null, "StackFrame.getDescriptor: available");
        assertTrue(frame.getMethodType() != null, "StackFrame.getMethodType: available");
    }

    private static void testMethodDescriptor() {
        StackWalker.StackFrame frame = frameForDescriptor();
        String descriptor = frame.getDescriptor();
        assertEquals("()Lj9compat/StackWalker$StackFrame;", descriptor,
                "StackFrame.getDescriptor: resolves descriptor for current method");
        MethodType expected = MethodType.fromMethodDescriptorString(
                descriptor, StackWalkerBackportTest.class.getClassLoader());
        assertEquals(expected, frame.getMethodType(),
                "StackFrame.getMethodType: matches descriptor");
    }

    private static StackWalker.StackFrame frameForDescriptor() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(stream -> stream
                .filter(frame -> "frameForDescriptor".equals(frame.getMethodName()))
                .findFirst()
                .get());
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
