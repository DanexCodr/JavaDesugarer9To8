package j9compat;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Java 8-compatible backport of {@link java.lang.StackWalker}.
 *
 * <p>This implementation relies on {@link Thread#getStackTrace()} and applies
 * best-effort filtering for hidden/reflect frames while resolving bytecode
 * indices from class debug metadata when available.
 */
public final class StackWalker {

    public enum Option {
        RETAIN_CLASS_REFERENCE,
        SHOW_REFLECT_FRAMES,
        SHOW_HIDDEN_FRAMES
    }

    private final boolean retainClassRef;
    private final int maxDepth;
    private final boolean showReflectFrames;
    private final boolean showHiddenFrames;

    private StackWalker(Set<Option> options, int maxDepth) {
        this.retainClassRef = options != null && options.contains(Option.RETAIN_CLASS_REFERENCE);
        this.showReflectFrames = options != null && options.contains(Option.SHOW_REFLECT_FRAMES);
        this.showHiddenFrames = options != null && options.contains(Option.SHOW_HIDDEN_FRAMES);
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
            if (!showReflectFrames && isReflectFrame(className)) {
                continue;
            }
            if (!showHiddenFrames && isHiddenFrame(className)) {
                continue;
            }
            frames.add(new StackFrameImpl(element, retainClassRef));
            if (frames.size() >= maxDepth) {
                break;
            }
        }
        return frames;
    }

    private boolean isReflectFrame(String className) {
        return className.startsWith("java.lang.reflect.")
                || className.startsWith("sun.reflect.")
                || className.startsWith("jdk.internal.reflect.");
    }

    private boolean isHiddenFrame(String className) {
        return className.startsWith("java.lang.invoke.")
                || className.startsWith("sun.invoke.")
                || className.startsWith("jdk.internal.invoke.")
                || className.startsWith("java.util.concurrent.CompletableFuture$");
    }

    public interface StackFrame {
        String getClassName();
        String getMethodName();
        Class<?> getDeclaringClass();

        MethodType getMethodType();
        String getDescriptor();

        int getByteCodeIndex();
        String getFileName();
        int getLineNumber();
        boolean isNativeMethod();
        StackTraceElement toStackTraceElement();
    }

    private static final class StackFrameImpl implements StackFrame {
        private final StackTraceElement element;
        private final boolean retainClassRef;
        private final int byteCodeIndex;

        StackFrameImpl(StackTraceElement element, boolean retainClassRef) {
            this.element = element;
            this.retainClassRef = retainClassRef;
            this.byteCodeIndex = MethodDescriptorResolver.resolveByteCodeIndex(element);
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
        public MethodType getMethodType() {
            String descriptor = MethodDescriptorResolver.resolveDescriptor(element);
            ClassLoader loader = MethodDescriptorResolver.resolveClassLoader(element.getClassName());
            return MethodType.fromMethodDescriptorString(descriptor, loader);
        }

        @Override
        public String getDescriptor() {
            return MethodDescriptorResolver.resolveDescriptor(element);
        }

        @Override
        public int getByteCodeIndex() {
            return byteCodeIndex;
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

    private static final class MethodDescriptorResolver {
        private static final String CACHE_LIMIT_PROPERTY = "j9compat.stackwalker.cache.size";
        private static final int DEFAULT_CACHE_LIMIT = 256;
        private static final int MAX_CACHE_LIMIT = 65536;
        private static final int CACHE_LIMIT = resolveCacheLimit();
        private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
        private static final Map<String, List<MethodInfo>> CACHE = Collections.synchronizedMap(
                new LinkedHashMap<String, List<MethodInfo>>(CACHE_LIMIT, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, List<MethodInfo>> eldest) {
                        return size() > CACHE_LIMIT;
                    }
                });

        private MethodDescriptorResolver() {}

        private static int resolveCacheLimit() {
            String value = System.getProperty(CACHE_LIMIT_PROPERTY);
            if (value == null) {
                return DEFAULT_CACHE_LIMIT;
            }
            String trimmed = value.trim();
            try {
                int parsed = Integer.parseInt(trimmed);
                if (parsed <= 0) {
                    return DEFAULT_CACHE_LIMIT;
                }
                return Math.min(parsed, MAX_CACHE_LIMIT);
            } catch (NumberFormatException ignored) {
                return DEFAULT_CACHE_LIMIT;
            }
        }

        static String resolveDescriptor(StackTraceElement element) {
            String className = element.getClassName();
            String methodName = element.getMethodName();
            int lineNumber = element.getLineNumber();
            ClassLoader loader = resolveClassLoader(className);
            List<MethodInfo> methods = getMethods(className, loader);
            MethodInfo match = findMethod(methods, methodName, lineNumber);
            if (match == null) {
                match = resolveViaReflection(className, loader, methodName);
            }
            if (match == null) {
                throw new IllegalStateException("Cannot resolve descriptor for " + element
                        + " (compile with -g for debug info, or method may be overloaded)");
            }
            return match.descriptor;
        }

        static int resolveByteCodeIndex(StackTraceElement element) {
            int lineNumber = element.getLineNumber();
            if (lineNumber < 0) {
                return -1;
            }
            String className = element.getClassName();
            String methodName = element.getMethodName();
            ClassLoader loader = resolveClassLoader(className);
            List<MethodInfo> methods = getMethods(className, loader);
            MethodInfo match = findMethod(methods, methodName, lineNumber);
            if (match == null) {
                return -1;
            }
            return match.byteCodeIndex(lineNumber);
        }

        static ClassLoader resolveClassLoader(String className) {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            Class<?> loaded = tryLoad(className, context);
            if (loaded != null) {
                return loaded.getClassLoader();
            }
            loaded = tryLoad(className, StackWalker.class.getClassLoader());
            return loaded != null ? loaded.getClassLoader() : context;
        }

        private static Class<?> tryLoad(String className, ClassLoader loader) {
            try {
                return Class.forName(className, false, loader);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static List<MethodInfo> getMethods(String className, ClassLoader loader) {
            String key = cacheKey(className, loader);
            List<MethodInfo> cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            List<MethodInfo> parsed = parseClassFile(className, loader);
            CACHE.put(key, parsed);
            return parsed;
        }

        private static String cacheKey(String className, ClassLoader loader) {
            String loaderId = loader == null ? "bootstrap" : String.valueOf(System.identityHashCode(loader));
            return className + "@" + loaderId;
        }

        private static List<MethodInfo> parseClassFile(String className, ClassLoader loader) {
            String resource = className.replace('.', '/') + ".class";
            InputStream raw = loader != null
                    ? loader.getResourceAsStream(resource)
                    : ClassLoader.getSystemResourceAsStream(resource);
            if (raw == null) {
                return Collections.emptyList();
            }
            try (InputStream rawStream = raw;
                 DataInputStream in = new DataInputStream(new BufferedInputStream(rawStream))) {
                if (in.readInt() != CLASS_FILE_MAGIC) {
                    return Collections.emptyList();
                }
                in.readUnsignedShort(); // minor
                in.readUnsignedShort(); // major
                String[] utf8 = readConstantPool(in, className);
                in.readUnsignedShort(); // access
                in.readUnsignedShort(); // this
                in.readUnsignedShort(); // super
                int interfaces = in.readUnsignedShort();
                for (int i = 0; i < interfaces; i++) {
                    in.readUnsignedShort();
                }
                int fields = in.readUnsignedShort();
                for (int i = 0; i < fields; i++) {
                    skipMember(in, utf8);
                }
                int methods = in.readUnsignedShort();
                List<MethodInfo> infos = new ArrayList<MethodInfo>(methods);
                for (int i = 0; i < methods; i++) {
                    infos.add(readMethodInfo(in, utf8));
                }
                return infos;
            } catch (IOException ignored) {
                return Collections.emptyList();
            }
        }

        private static String[] readConstantPool(DataInputStream in, String className) throws IOException {
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1:
                        utf8[i] = in.readUTF();
                        break;
                    case 3:
                    case 4:
                        in.readInt();
                        break;
                    case 5:
                    case 6:
                        in.readLong();
                        i++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        in.readUnsignedShort();
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 18:
                        in.readUnsignedShort();
                        in.readUnsignedShort();
                        break;
                    case 15:
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                        break;
                    default:
                        throw new IOException("Unknown constant pool tag " + tag + " in "
                                + className + " (expected supported tags 1,3-12,15-16,18-20)");
                }
            }
            return utf8;
        }

        private static void skipMember(DataInputStream in, String[] utf8) throws IOException {
            in.readUnsignedShort();
            in.readUnsignedShort();
            in.readUnsignedShort();
            int attrs = in.readUnsignedShort();
            skipAttributes(in, attrs);
        }

        private static MethodInfo readMethodInfo(DataInputStream in, String[] utf8) throws IOException {
            in.readUnsignedShort();
            String name = utf8[in.readUnsignedShort()];
            String descriptor = utf8[in.readUnsignedShort()];
            int attrs = in.readUnsignedShort();
            int minLine = -1;
            int maxLine = -1;
            List<LineInfo> lines = new ArrayList<LineInfo>();
            for (int i = 0; i < attrs; i++) {
                String attrName = utf8[in.readUnsignedShort()];
                int len = in.readInt();
                if ("Code".equals(attrName)) {
                    in.readUnsignedShort();
                    in.readUnsignedShort();
                    int codeLen = in.readInt();
                    skipFully(in, codeLen);
                    int exceptionTableLen = in.readUnsignedShort();
                    skipFully(in, exceptionTableLen * 8);
                    int codeAttrs = in.readUnsignedShort();
                    for (int j = 0; j < codeAttrs; j++) {
                        String codeAttrName = utf8[in.readUnsignedShort()];
                        int codeAttrLen = in.readInt();
                        if ("LineNumberTable".equals(codeAttrName)) {
                            int linesCount = in.readUnsignedShort();
                            for (int k = 0; k < linesCount; k++) {
                                int bci = in.readUnsignedShort();
                                int line = in.readUnsignedShort();
                                if (minLine == -1 || line < minLine) {
                                    minLine = line;
                                }
                                if (line > maxLine) {
                                    maxLine = line;
                                }
                                lines.add(new LineInfo(line, bci));
                            }
                        } else {
                            skipFully(in, codeAttrLen);
                        }
                    }
                } else {
                    skipFully(in, len);
                }
            }
            return new MethodInfo(name, descriptor, minLine, maxLine, lines);
        }

        private static void skipAttributes(DataInputStream in, int count) throws IOException {
            for (int i = 0; i < count; i++) {
                in.readUnsignedShort();
                int len = in.readInt();
                skipFully(in, len);
            }
        }

        private static void skipFully(InputStream in, int len) throws IOException {
            int remaining = len;
            while (remaining > 0) {
                long skipped = in.skip(remaining);
                if (skipped <= 0) {
                    if (in.read() == -1) {
                        break;
                    }
                    skipped = 1;
                }
                remaining -= skipped;
            }
        }

        private static MethodInfo findMethod(List<MethodInfo> methods,
                                             String methodName,
                                             int lineNumber) {
            MethodInfo nameMatch = null;
            for (MethodInfo info : methods) {
                if (!methodName.equals(info.name)) {
                    continue;
                }
                if (lineNumber > 0 && info.matchesLine(lineNumber)) {
                    return info;
                }
                if (nameMatch == null) {
                    nameMatch = info;
                } else {
                    nameMatch = MethodInfo.AMBIGUOUS_MATCH;
                }
            }
            return nameMatch == MethodInfo.AMBIGUOUS_MATCH ? null : nameMatch;
        }

        private static MethodInfo resolveViaReflection(String className,
                                                       ClassLoader loader,
                                                       String methodName) {
            if ("<clinit>".equals(methodName)) {
                return new MethodInfo("<clinit>", "()V", -1, -1, Collections.<LineInfo>emptyList());
            }
            Class<?> clazz = tryLoad(className, loader);
            if (clazz == null) {
                return null;
            }
            MethodInfo candidate = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                MethodInfo info = new MethodInfo(methodName,
                        methodDescriptor(method.getParameterTypes(), method.getReturnType()),
                        -1, -1, Collections.<LineInfo>emptyList());
                if (candidate != null) {
                    return null;
                }
                candidate = info;
            }
            if (candidate != null) {
                return candidate;
            }
            if ("<init>".equals(methodName)) {
                Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                if (constructors.length == 1) {
                    Constructor<?> ctor = constructors[0];
                    return new MethodInfo("<init>",
                            methodDescriptor(ctor.getParameterTypes(), void.class),
                            -1, -1, Collections.<LineInfo>emptyList());
                }
            }
            return null;
        }

        private static String methodDescriptor(Class<?>[] params, Class<?> returnType) {
            StringBuilder descriptor = new StringBuilder();
            descriptor.append('(');
            for (Class<?> param : params) {
                descriptor.append(typeDescriptor(param));
            }
            descriptor.append(')');
            descriptor.append(typeDescriptor(returnType));
            return descriptor.toString();
        }

        private static String typeDescriptor(Class<?> type) {
            if (type.isPrimitive()) {
                if (type == void.class) return "V";
                if (type == boolean.class) return "Z";
                if (type == byte.class) return "B";
                if (type == char.class) return "C";
                if (type == short.class) return "S";
                if (type == int.class) return "I";
                if (type == long.class) return "J";
                if (type == float.class) return "F";
                if (type == double.class) return "D";
            }
            if (type.isArray()) {
                return type.getName().replace('.', '/');
            }
            return "L" + type.getName().replace('.', '/') + ";";
        }
    }

    private static final class MethodInfo {
        static final MethodInfo AMBIGUOUS_MATCH =
                new MethodInfo("", "", -1, -1, Collections.<LineInfo>emptyList());
        final String name;
        final String descriptor;
        final int minLine;
        final int maxLine;
        final List<LineInfo> lines;

        MethodInfo(String name, String descriptor, int minLine, int maxLine, List<LineInfo> lines) {
            this.name = name;
            this.descriptor = descriptor;
            this.minLine = minLine;
            this.maxLine = maxLine;
            this.lines = lines == null ? Collections.<LineInfo>emptyList()
                    : Collections.unmodifiableList(new ArrayList<LineInfo>(lines));
        }

        boolean matchesLine(int line) {
            return minLine != -1 && line >= minLine && line <= maxLine;
        }

        int byteCodeIndex(int lineNumber) {
            if (lineNumber < 0 || lines.isEmpty()) {
                return -1;
            }
            int bestLine = -1;
            int bestBci = -1;
            for (LineInfo info : lines) {
                if (info.line == lineNumber) {
                    return info.bci;
                }
                if (info.line < lineNumber && info.line > bestLine) {
                    bestLine = info.line;
                    bestBci = info.bci;
                }
            }
            return bestBci;
        }
    }

    private static final class LineInfo {
        final int line;
        final int bci;

        LineInfo(int line, int bci) {
            this.line = line;
            this.bci = bci;
        }
    }
}
