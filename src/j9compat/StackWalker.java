package j9compat;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Java 8-compatible backport of {@link java.lang.StackWalker}.
 *
 * <p>This implementation relies on {@link Thread#getStackTrace()} and does not
 * expose JVM stack frames, hidden frames, or local variables.
 */
public final class StackWalker {

    public enum Option {
        RETAIN_CLASS_REFERENCE,
        SHOW_REFLECT_FRAMES,
        SHOW_HIDDEN_FRAMES
    }

    private final boolean retainClassRef;
    private final int maxDepth;

    private StackWalker(Set<Option> options, int maxDepth) {
        this.retainClassRef = options != null && options.contains(Option.RETAIN_CLASS_REFERENCE);
        this.maxDepth = maxDepth <= 0 ? Integer.MAX_VALUE : maxDepth;
    }

    public static StackWalker getInstance() {
        return new StackWalker(Collections.<Option>emptySet(), Integer.MAX_VALUE);
    }

    public static StackWalker getInstance(Option option) {
        return new StackWalker(EnumSet.of(option), Integer.MAX_VALUE);
    }

    public static StackWalker getInstance(Set<Option> options) {
        return new StackWalker(options, Integer.MAX_VALUE);
    }

    public static StackWalker getInstance(Set<Option> options, int maxDepth) {
        return new StackWalker(options, maxDepth);
    }

    public <T> T walk(Function<? super Stream<StackFrame>, ? extends T> walker) {
        List<StackFrame> frames = captureFrames();
        return walker.apply(frames.stream());
    }

    public void forEach(Consumer<? super StackFrame> action) {
        walk(stream -> {
            stream.forEach(action);
            return null;
        });
    }

    public Class<?> getCallerClass() {
        List<StackFrame> frames = captureFrames();
        if (frames.isEmpty()) {
            throw new IllegalStateException("No caller frame available");
        }
        return frames.get(0).getDeclaringClass();
    }

    private List<StackFrame> captureFrames() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        List<StackFrame> frames = new ArrayList<>();
        boolean sawStackWalker = false;
        for (StackTraceElement element : elements) {
            String className = element.getClassName();
            if (className.equals(StackWalker.class.getName())) {
                sawStackWalker = true;
                continue;
            }
            if (!sawStackWalker) {
                continue;
            }
            if (className.equals(Thread.class.getName())) {
                continue;
            }
            frames.add(new StackFrameImpl(element, retainClassRef));
            if (frames.size() >= maxDepth) {
                break;
            }
        }
        return frames;
    }

    public interface StackFrame {
        String getClassName();
        String getMethodName();
        Class<?> getDeclaringClass();

        default MethodType getMethodType() {
            throw new UnsupportedOperationException("Method type not retained in backport");
        }

        default String getDescriptor() {
            throw new UnsupportedOperationException("Descriptor not retained in backport");
        }

        int getByteCodeIndex();
        String getFileName();
        int getLineNumber();
        boolean isNativeMethod();
        StackTraceElement toStackTraceElement();
    }

    private static final class StackFrameImpl implements StackFrame {
        private final StackTraceElement element;
        private final boolean retainClassRef;

        StackFrameImpl(StackTraceElement element, boolean retainClassRef) {
            this.element = element;
            this.retainClassRef = retainClassRef;
        }

        @Override
        public String getClassName() {
            return element.getClassName();
        }

        @Override
        public String getMethodName() {
            return element.getMethodName();
        }

        @Override
        public Class<?> getDeclaringClass() {
            if (!retainClassRef) {
                throw new UnsupportedOperationException(
                        "StackWalker.Option.RETAIN_CLASS_REFERENCE not enabled");
            }
            try {
                return Class.forName(element.getClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Cannot resolve declaring class", e);
            }
        }

        @Override
        public int getByteCodeIndex() {
            return -1;
        }

        @Override
        public String getFileName() {
            return element.getFileName();
        }

        @Override
        public int getLineNumber() {
            return element.getLineNumber();
        }

        @Override
        public boolean isNativeMethod() {
            return element.isNativeMethod();
        }

        @Override
        public StackTraceElement toStackTraceElement() {
            return element;
        }
    }
}
