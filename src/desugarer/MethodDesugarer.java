package desugarer;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.ArrayList;
import java.util.List;

/**
 * ASM {@link MethodVisitor} that rewrites Java 9–11 API calls to equivalent
 * calls on the j9compat backport library.
 *
 * <h2>Remapping strategy</h2>
 * <ul>
 *   <li><b>Static interface methods</b> ({@code List.of}, {@code Set.of},
 *       {@code Map.of}, etc.) keep the same descriptor; only the owner and
 *       method name change, and {@code isInterface} becomes {@code false}.</li>
 *   <li><b>Instance methods redirected to static helpers</b>
 *       ({@code Stream.takeWhile}, {@code Optional.or}, etc.): the receiver
 *       object is already on the operand stack immediately below the other
 *       arguments, so we change {@code INVOKEVIRTUAL}/{@code INVOKEINTERFACE}
 *       to {@code INVOKESTATIC} and prepend the receiver type to the
 *       descriptor.</li>
 * </ul>
 *
 * <h2>Covered Java 9–11 APIs</h2>
 * <ul>
 *   <li>{@code List.of}, {@code Set.of}, {@code Map.of}, {@code Map.ofEntries},
 *       {@code Map.entry}, {@code List.copyOf}, {@code Set.copyOf},
 *       {@code Map.copyOf}</li>
 *   <li>{@code Stream.takeWhile}, {@code Stream.dropWhile},
 *       {@code Stream.ofNullable}, {@code Stream.iterate(seed,hasNext,f)}</li>
 *   <li>{@code IntStream/LongStream/DoubleStream.takeWhile},
 *       {@code dropWhile}, {@code iterate(seed,hasNext,f)}</li>
 *   <li>{@code Collectors.filtering}, {@code Collectors.flatMapping}</li>
 *   <li>{@code Optional.ifPresentOrElse}, {@code Optional.or},
 *       {@code Optional.stream}</li>
 *   <li>{@code OptionalInt/Long/Double.ifPresentOrElse},
 *       {@code OptionalInt/Long/Double.stream}</li>
 *   <li>{@code InputStream.transferTo}, {@code InputStream.readAllBytes},
 *       {@code InputStream.readNBytes}</li>
 *   <li>{@code Process.toHandle}</li>
 *   <li>{@code Objects.requireNonNullElse}, {@code Objects.requireNonNullElseGet},
 *       {@code Objects.checkIndex}</li>
 *   <li>{@code CompletableFuture.orTimeout},
 *       {@code CompletableFuture.completeOnTimeout},
 *       {@code CompletableFuture.failedFuture},
 *       {@code CompletableFuture.completedStage},
 *       {@code CompletableFuture.failedStage},
 *       {@code CompletableFuture.minimalCompletionStage},
 *       {@code CompletableFuture.newIncompleteFuture}</li>
 *   <li>{@code Class.getModule} (module system)</li>
 *   <li>{@code MethodHandles.Lookup.findVarHandle},
 *       {@code MethodHandles.Lookup.findStaticVarHandle},
 *       {@code MethodHandles.arrayElementVarHandle}</li>
 *   <li>{@code Collectors.toUnmodifiableList/Set/Map} (Java 10)</li>
 *   <li>{@code Optional.orElseThrow()} (Java 10)</li>
 *   <li>{@code String.isBlank}, {@code String.lines}, {@code String.strip},
 *       {@code String.stripLeading}, {@code String.stripTrailing},
 *       {@code String.repeat} (Java 11)</li>
 *   <li>{@code Optional.isEmpty} and optional primitive {@code isEmpty} (Java 11)</li>
 *   <li>{@code Collection.toArray(IntFunction)} (Java 11)</li>
 *   <li>{@code Files.readString}, {@code Files.writeString} (Java 11)</li>
 *   <li>{@code Path.of} (Java 11)</li>
 *   <li>{@code Predicate.not} (Java 11)</li>
 * </ul>
 */
public class MethodDesugarer extends LocalVariablesSorter {

    // Backport class internal names (JVM-style, '/' not '.')
    private static final String BP_COLLECTION  = "j9compat/CollectionBackport";
    private static final String BP_STREAM      = "j9compat/StreamBackport";
    private static final String BP_INT_STREAM  = "j9compat/IntStreamBackport";
    private static final String BP_LONG_STREAM = "j9compat/LongStreamBackport";
    private static final String BP_DOUBLE_STREAM = "j9compat/DoubleStreamBackport";
    private static final String BP_OPTIONAL    = "j9compat/OptionalBackport";
    private static final String BP_OPTIONAL_INT = "j9compat/OptionalIntBackport";
    private static final String BP_OPTIONAL_LONG = "j9compat/OptionalLongBackport";
    private static final String BP_OPTIONAL_DOUBLE = "j9compat/OptionalDoubleBackport";
    private static final String BP_IO          = "j9compat/IOBackport";
    private static final String BP_OBJECTS     = "j9compat/ObjectsBackport";
    private static final String BP_CF          = "j9compat/CompletableFutureBackport";
    private static final String BP_COLLECTORS  = "j9compat/CollectorsBackport";
    private static final String BP_PROCESS_HANDLE = "j9compat/ProcessHandle";
    private static final String BP_MODULE_BACKPORT = "j9compat/ModuleBackport";
    private static final String BP_REFLECTION = "j9compat/ReflectionBackport";
    private static final String BP_METHOD_HANDLES = "j9compat/MethodHandlesBackport";
    private static final List<MethodTransform> EXTRA_TRANSFORMS =
            MethodTransformRegistry.load();

    private final Java9ToJava8Desugarer.Stats stats;
    private final ClassHierarchy hierarchy;

    public MethodDesugarer(int access, String descriptor, MethodVisitor mv,
                           Java9ToJava8Desugarer.Stats stats,
                           ClassHierarchy hierarchy) {
        super(Opcodes.ASM9, access, descriptor, mv);
        this.stats = stats;
        this.hierarchy = hierarchy;
    }

    // ────────────────────────────────────────────────────────────────────────

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
                                 String descriptor, boolean isInterface) {
        for (MethodTransform transform : EXTRA_TRANSFORMS) {
            if (transform.transform(this, opcode, owner, name, descriptor, isInterface)) {
                return;
            }
        }

        // ── java.util.List factory methods ──────────────────────────────────
        if ("java/util/List".equals(owner)) {
            if ("of".equals(name)) {
                remap(BP_COLLECTION, "listOf", descriptor); return;
            }
            if ("copyOf".equals(name)) {
                remap(BP_COLLECTION, "listCopyOf", descriptor); return;
            }
        }

        // ── java.util.Set factory methods ────────────────────────────────────
        if ("java/util/Set".equals(owner)) {
            if ("of".equals(name)) {
                remap(BP_COLLECTION, "setOf", descriptor); return;
            }
            if ("copyOf".equals(name)) {
                remap(BP_COLLECTION, "setCopyOf", descriptor); return;
            }
        }

        // ── java.util.Map factory methods ────────────────────────────────────
        if ("java/util/Map".equals(owner)) {
            if ("of".equals(name)) {
                remap(BP_COLLECTION, "mapOf", descriptor); return;
            }
            if ("ofEntries".equals(name)) {
                remap(BP_COLLECTION, "mapOfEntries", descriptor); return;
            }
            if ("entry".equals(name)) {
                remap(BP_COLLECTION, "mapEntry", descriptor); return;
            }
            if ("copyOf".equals(name)) {
                remap(BP_COLLECTION, "mapCopyOf", descriptor); return;
            }
        }

        // ── java.util.stream.Stream additions ────────────────────────────────
        if ("java/util/stream/Stream".equals(owner)) {
            if ("takeWhile".equals(name)) {
                // INVOKEINTERFACE Stream.takeWhile:(Predicate)Stream
                // → INVOKESTATIC StreamBackport.takeWhile:(Stream,Predicate)Stream
                remapInstanceToStatic(BP_STREAM, "takeWhile",
                        "java/util/stream/Stream", descriptor); return;
            }
            if ("dropWhile".equals(name)) {
                remapInstanceToStatic(BP_STREAM, "dropWhile",
                        "java/util/stream/Stream", descriptor); return;
            }
            if ("ofNullable".equals(name)) {
                // static interface method – same descriptor
                remap(BP_STREAM, "ofNullable", descriptor); return;
            }
            if ("iterate".equals(name)
                    && descriptor.contains("Ljava/util/function/Predicate;")) {
                // 3-arg iterate(seed, hasNext, f) – static interface method
                remap(BP_STREAM, "iterate", descriptor); return;
            }
        }

        // ── java.util.stream.IntStream additions ──────────────────────────────
        if ("java/util/stream/IntStream".equals(owner)) {
            if ("takeWhile".equals(name)) {
                remapInstanceToStatic(BP_INT_STREAM, "takeWhile",
                        "java/util/stream/IntStream", descriptor); return;
            }
            if ("dropWhile".equals(name)) {
                remapInstanceToStatic(BP_INT_STREAM, "dropWhile",
                        "java/util/stream/IntStream", descriptor); return;
            }
            if ("iterate".equals(name)
                    && descriptor.contains("Ljava/util/function/IntPredicate;")) {
                remap(BP_INT_STREAM, "iterate", descriptor); return;
            }
        }

        // ── java.util.stream.LongStream additions ─────────────────────────────
        if ("java/util/stream/LongStream".equals(owner)) {
            if ("takeWhile".equals(name)) {
                remapInstanceToStatic(BP_LONG_STREAM, "takeWhile",
                        "java/util/stream/LongStream", descriptor); return;
            }
            if ("dropWhile".equals(name)) {
                remapInstanceToStatic(BP_LONG_STREAM, "dropWhile",
                        "java/util/stream/LongStream", descriptor); return;
            }
            if ("iterate".equals(name)
                    && descriptor.contains("Ljava/util/function/LongPredicate;")) {
                remap(BP_LONG_STREAM, "iterate", descriptor); return;
            }
        }

        // ── java.util.stream.DoubleStream additions ───────────────────────────
        if ("java/util/stream/DoubleStream".equals(owner)) {
            if ("takeWhile".equals(name)) {
                remapInstanceToStatic(BP_DOUBLE_STREAM, "takeWhile",
                        "java/util/stream/DoubleStream", descriptor); return;
            }
            if ("dropWhile".equals(name)) {
                remapInstanceToStatic(BP_DOUBLE_STREAM, "dropWhile",
                        "java/util/stream/DoubleStream", descriptor); return;
            }
            if ("iterate".equals(name)
                    && descriptor.contains("Ljava/util/function/DoublePredicate;")) {
                remap(BP_DOUBLE_STREAM, "iterate", descriptor); return;
            }
        }

        // ── java.util.stream.Collectors additions ────────────────────────────
        if ("java/util/stream/Collectors".equals(owner)) {
            if ("filtering".equals(name)) {
                remap(BP_COLLECTORS, "filtering", descriptor); return;
            }
            if ("flatMapping".equals(name)) {
                remap(BP_COLLECTORS, "flatMapping", descriptor); return;
            }
        }

        // ── java.util.Optional additions ─────────────────────────────────────
        if ("java/util/Optional".equals(owner)) {
            if ("ifPresentOrElse".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL, "ifPresentOrElse",
                        "java/util/Optional", descriptor); return;
            }
            if ("or".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL, "or",
                        "java/util/Optional", descriptor); return;
            }
            if ("stream".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL, "stream",
                        "java/util/Optional", descriptor); return;
            }
        }

        // ── java.util.OptionalInt/Long/Double additions ───────────────────────
        if ("java/util/OptionalInt".equals(owner)) {
            if ("ifPresentOrElse".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL_INT, "ifPresentOrElse",
                        "java/util/OptionalInt", descriptor); return;
            }
            if ("stream".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL_INT, "stream",
                        "java/util/OptionalInt", descriptor); return;
            }
        }
        if ("java/util/OptionalLong".equals(owner)) {
            if ("ifPresentOrElse".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL_LONG, "ifPresentOrElse",
                        "java/util/OptionalLong", descriptor); return;
            }
            if ("stream".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL_LONG, "stream",
                        "java/util/OptionalLong", descriptor); return;
            }
        }
        if ("java/util/OptionalDouble".equals(owner)) {
            if ("ifPresentOrElse".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL_DOUBLE, "ifPresentOrElse",
                        "java/util/OptionalDouble", descriptor); return;
            }
            if ("stream".equals(name)) {
                remapInstanceToStatic(BP_OPTIONAL_DOUBLE, "stream",
                        "java/util/OptionalDouble", descriptor); return;
            }
        }

        // ── java.io.InputStream additions ────────────────────────────────────
        if (isInputStreamOwner(owner)) {
            if ("transferTo".equals(name)
                    && "(Ljava/io/OutputStream;)J".equals(descriptor)) {
                remapInstanceToStatic(BP_IO, "transferTo",
                        "java/io/InputStream", descriptor); return;
            }
            if ("readAllBytes".equals(name)
                    && "()[B".equals(descriptor)) {
                remapInstanceToStatic(BP_IO, "readAllBytes",
                        "java/io/InputStream", descriptor); return;
            }
            if ("readNBytes".equals(name)
                    && ("([BII)I".equals(descriptor) || "(I)[B".equals(descriptor))) {
                remapInstanceToStatic(BP_IO, "readNBytes",
                        "java/io/InputStream", descriptor); return;
            }
        }

        // ── java.util.Objects additions (Java 9) ─────────────────────────────
        if ("java/util/Objects".equals(owner)) {
            if ("requireNonNullElse".equals(name)) {
                remap(BP_OBJECTS, "requireNonNullElse", descriptor); return;
            }
            if ("requireNonNullElseGet".equals(name)) {
                remap(BP_OBJECTS, "requireNonNullElseGet", descriptor); return;
            }
            if ("checkIndex".equals(name)) {
                remap(BP_OBJECTS, "checkIndex", descriptor); return;
            }
            if ("checkFromToIndex".equals(name)) {
                remap(BP_OBJECTS, "checkFromToIndex", descriptor); return;
            }
            if ("checkFromIndexSize".equals(name)) {
                remap(BP_OBJECTS, "checkFromIndexSize", descriptor); return;
            }
        }

        // ── java.util.concurrent.CompletableFuture additions (Java 9) ────────
        if ("java/util/concurrent/CompletableFuture".equals(owner)) {
            if ("orTimeout".equals(name)) {
                remapInstanceToStatic(BP_CF, "orTimeout",
                        "java/util/concurrent/CompletableFuture", descriptor); return;
            }
            if ("completeOnTimeout".equals(name)) {
                remapInstanceToStatic(BP_CF, "completeOnTimeout",
                        "java/util/concurrent/CompletableFuture", descriptor); return;
            }
            if ("failedFuture".equals(name)) {
                remap(BP_CF, "failedFuture", descriptor); return;
            }
            if ("completedStage".equals(name)) {
                remap(BP_CF, "completedStage", descriptor); return;
            }
            if ("failedStage".equals(name)) {
                remap(BP_CF, "failedStage", descriptor); return;
            }
            if ("minimalCompletionStage".equals(name)) {
                remapInstanceToStatic(BP_CF, "minimalCompletionStage",
                        "java/util/concurrent/CompletableFuture", descriptor); return;
            }
            if ("newIncompleteFuture".equals(name)) {
                remapInstanceToStatic(BP_CF, "newIncompleteFuture",
                        "java/util/concurrent/CompletableFuture", descriptor); return;
            }
            if ("copy".equals(name)) {
                remapInstanceToStatic(BP_CF, "copy",
                        "java/util/concurrent/CompletableFuture", descriptor); return;
            }
        }

        // ── java.lang.Process additions (toHandle) ────────────────────────────
        if ("java/lang/Process".equals(owner)
                && "toHandle".equals(name)
                && "()Ljava/lang/ProcessHandle;".equals(descriptor)) {
            remapInstanceToStatic(BP_PROCESS_HANDLE, "fromProcess",
                    "java/lang/Process", descriptor); return;
        }

        // ── java.lang.Class additions (module system) ─────────────────────────
        if ("java/lang/Class".equals(owner) && "getModule".equals(name)
                && "()Ljava/lang/Module;".equals(descriptor)) {
            remapInstanceToStatic(BP_MODULE_BACKPORT, "getModule",
                    "java/lang/Class", descriptor); return;
        }

        // ── java.lang.Class reflection lookups ───────────────────────────────
        if ("java/lang/Class".equals(owner)) {
            if ("getMethod".equals(name)) {
                remapInstanceToStatic(BP_REFLECTION, "getMethod",
                        "java/lang/Class", descriptor); return;
            }
            if ("getDeclaredMethod".equals(name)) {
                remapInstanceToStatic(BP_REFLECTION, "getDeclaredMethod",
                        "java/lang/Class", descriptor); return;
            }
            if ("getMethods".equals(name)) {
                remapInstanceToStatic(BP_REFLECTION, "getMethods",
                        "java/lang/Class", descriptor); return;
            }
            if ("getDeclaredMethods".equals(name)) {
                remapInstanceToStatic(BP_REFLECTION, "getDeclaredMethods",
                        "java/lang/Class", descriptor); return;
            }
        }

        // ── java.lang.reflect.Method invocation ──────────────────────────────
        if ("java/lang/reflect/Method".equals(owner) && "invoke".equals(name)) {
            remapInstanceToStatic(BP_REFLECTION, "invoke",
                    "java/lang/reflect/Method", descriptor); return;
        }

        // ── MethodHandles.Lookup lookups ─────────────────────────────────────
        if ("java/lang/invoke/MethodHandles$Lookup".equals(owner)) {
            if ("findVirtual".equals(name)) {
                remapInstanceToStatic(BP_METHOD_HANDLES, "findVirtual",
                        "java/lang/invoke/MethodHandles$Lookup", descriptor); return;
            }
            if ("findStatic".equals(name)) {
                remapInstanceToStatic(BP_METHOD_HANDLES, "findStatic",
                        "java/lang/invoke/MethodHandles$Lookup", descriptor); return;
            }
            if ("findSpecial".equals(name)) {
                remapInstanceToStatic(BP_METHOD_HANDLES, "findSpecial",
                        "java/lang/invoke/MethodHandles$Lookup", descriptor); return;
            }
            if ("findConstructor".equals(name)) {
                remapInstanceToStatic(BP_METHOD_HANDLES, "findConstructor",
                        "java/lang/invoke/MethodHandles$Lookup", descriptor); return;
            }
            if ("findVarHandle".equals(name)) {
                remapInstanceToStatic(BP_METHOD_HANDLES, "findVarHandle",
                        "java/lang/invoke/MethodHandles$Lookup", descriptor); return;
            }
            if ("findStaticVarHandle".equals(name)) {
                remapInstanceToStatic(BP_METHOD_HANDLES, "findStaticVarHandle",
                        "java/lang/invoke/MethodHandles$Lookup", descriptor); return;
            }
        }

        if ("java/lang/invoke/MethodHandles".equals(owner)
                && "arrayElementVarHandle".equals(name)) {
            remap(BP_METHOD_HANDLES, "arrayElementVarHandle", descriptor); return;
        }

        // No remapping needed – emit unchanged
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor,
                                       Handle bootstrapMethodHandle,
                                       Object... bootstrapMethodArguments) {
        if (isStringConcatBootstrap(bootstrapMethodHandle, descriptor)) {
            if (emitStringConcat(descriptor, bootstrapMethodHandle,
                    bootstrapMethodArguments)) {
                stats.apiCallsRemapped++;
                return;
            }
        }
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle,
                bootstrapMethodArguments);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Emits an INVOKESTATIC to the given backport class with the *same*
     * descriptor (used for static-interface-method replacements).
     */
    void remap(String newOwner, String newName, String descriptor) {
        System.out.println("  [REMAP]  static  " + newOwner + "." + newName + descriptor);
        stats.apiCallsRemapped++;
        super.visitMethodInsn(Opcodes.INVOKESTATIC, newOwner, newName,
                descriptor, false);
    }

    /**
     * Converts an instance-method call to a static-helper call by prepending
     * the receiver type (L&lt;ownerInternalName&gt;;) to the descriptor.
     *
     * <p>Example: {@code (Predicate)Stream} → {@code (Stream;Predicate)Stream}.
     *
     * <p>Because the receiver object is already on the operand stack just below
     * the regular arguments, the stack state is correct without inserting any
     * extra instructions.
     */
    void remapInstanceToStatic(String newOwner, String newName,
                               String receiverOwner, String descriptor) {
        String newDescriptor = prependReceiver(receiverOwner, descriptor);
        System.out.println("  [REMAP]  instance→static  "
                + newOwner + "." + newName + newDescriptor);
        stats.apiCallsRemapped++;
        super.visitMethodInsn(Opcodes.INVOKESTATIC, newOwner, newName,
                newDescriptor, false);
    }

    /**
     * Transforms a descriptor like {@code (Ljava/util/function/Predicate;)Ljava/util/stream/Stream;}
     * into {@code (Ljava/util/stream/Stream;Ljava/util/function/Predicate;)Ljava/util/stream/Stream;}
     * by inserting "L&lt;receiver&gt;;" right after the opening parenthesis.
     */
    static String prependReceiver(String receiverInternalName, String descriptor) {
        // descriptor starts with '('
        return "(L" + receiverInternalName + ";" + descriptor.substring(1);
    }

    private boolean isInputStreamOwner(String owner) {
        if (hierarchy == null) {
            return true;
        }
        return hierarchy.isInputStreamOwner(owner);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  String concat (invokedynamic) handling
    // ────────────────────────────────────────────────────────────────────────

    private static boolean isStringConcatBootstrap(Handle handle, String descriptor) {
        return "java/lang/invoke/StringConcatFactory".equals(handle.getOwner())
                && ("makeConcatWithConstants".equals(handle.getName())
                    || "makeConcat".equals(handle.getName()))
                && Type.getReturnType(descriptor).getSort() == Type.OBJECT
                && "java/lang/String".equals(Type.getReturnType(descriptor).getInternalName());
    }

    private boolean emitStringConcat(String descriptor, Handle bootstrapMethodHandle,
                                     Object[] bootstrapMethodArguments) {
        Type[] argTypes = Type.getArgumentTypes(descriptor);
        List<ConcatSegment> segments = parseConcatSegments(argTypes,
                bootstrapMethodHandle, bootstrapMethodArguments);
        if (segments == null) {
            return false;
        }

        int[] locals = new int[argTypes.length];
        for (int i = argTypes.length - 1; i >= 0; i--) {
            locals[i] = newLocal(argTypes[i]);
            super.visitVarInsn(argTypes[i].getOpcode(Opcodes.ISTORE), locals[i]);
        }

        int estimatedCapacity = estimateConcatCapacity(segments, argTypes);
        super.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        super.visitInsn(Opcodes.DUP);
        if (estimatedCapacity > 0) {
            super.visitLdcInsn(estimatedCapacity);
            super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder",
                    "<init>", "(I)V", false);
        } else {
            super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder",
                    "<init>", "()V", false);
        }

        for (ConcatSegment segment : segments) {
            if (segment.kind == ConcatSegmentKind.LITERAL) {
                super.visitLdcInsn(segment.literal);
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else if (segment.kind == ConcatSegmentKind.ARG) {
                appendArgument(argTypes[segment.index], locals[segment.index]);
            } else if (segment.kind == ConcatSegmentKind.CONSTANT) {
                appendConstant(segment.constant);
            }
        }

        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "toString", "()Ljava/lang/String;", false);
        System.out.println("  [REMAP]  invokedynamic StringConcatFactory → StringBuilder");
        return true;
    }

    private List<ConcatSegment> parseConcatSegments(Type[] argTypes,
                                                    Handle bootstrapMethodHandle,
                                                    Object[] bootstrapMethodArguments) {
        if ("makeConcat".equals(bootstrapMethodHandle.getName())) {
            List<ConcatSegment> segments = new ArrayList<>();
            for (int i = 0; i < argTypes.length; i++) {
                segments.add(ConcatSegment.argument(i));
            }
            return segments;
        }

        if (bootstrapMethodArguments == null
                || bootstrapMethodArguments.length == 0
                || !(bootstrapMethodArguments[0] instanceof String)) {
            return null;
        }

        String recipe = (String) bootstrapMethodArguments[0];
        List<ConcatSegment> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int argIndex = 0;
        int constIndex = 1;
        for (int i = 0; i < recipe.length(); i++) {
            char c = recipe.charAt(i);
            if (c == '\u0001') {
                if (literal.length() > 0) {
                    segments.add(ConcatSegment.literal(literal.toString()));
                    literal.setLength(0);
                }
                if (argIndex >= argTypes.length) {
                    return null;
                }
                segments.add(ConcatSegment.argument(argIndex++));
            } else if (c == '\u0002') {
                if (literal.length() > 0) {
                    segments.add(ConcatSegment.literal(literal.toString()));
                    literal.setLength(0);
                }
                if (constIndex >= bootstrapMethodArguments.length) {
                    return null;
                }
                segments.add(ConcatSegment.constant(bootstrapMethodArguments[constIndex++]));
            } else {
                literal.append(c);
            }
        }

        if (literal.length() > 0) {
            segments.add(ConcatSegment.literal(literal.toString()));
        }

        if (argIndex != argTypes.length) {
            return null;
        }
        return segments;
    }

    private void appendArgument(Type type, int local) {
        super.visitVarInsn(type.getOpcode(Opcodes.ILOAD), local);
        appendType(type);
    }

    private void appendConstant(Object constant) {
        if (constant instanceof Integer) {
            super.visitLdcInsn(((Integer) constant).intValue());
            appendType(Type.INT_TYPE);
            return;
        }
        if (constant instanceof Long) {
            super.visitLdcInsn(((Long) constant).longValue());
            appendType(Type.LONG_TYPE);
            return;
        }
        if (constant instanceof Float) {
            super.visitLdcInsn(((Float) constant).floatValue());
            appendType(Type.FLOAT_TYPE);
            return;
        }
        if (constant instanceof Double) {
            super.visitLdcInsn(((Double) constant).doubleValue());
            appendType(Type.DOUBLE_TYPE);
            return;
        }
        if (constant instanceof String) {
            super.visitLdcInsn(constant);
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            return;
        }
        super.visitLdcInsn(constant);
        appendType(Type.getType(Object.class));
    }

    private void appendType(Type type) {
        String descriptor;
        switch (type.getSort()) {
            case Type.BOOLEAN:
                descriptor = "(Z)Ljava/lang/StringBuilder;";
                break;
            case Type.CHAR:
                descriptor = "(C)Ljava/lang/StringBuilder;";
                break;
            case Type.BYTE:
                descriptor = "(B)Ljava/lang/StringBuilder;";
                break;
            case Type.SHORT:
                descriptor = "(S)Ljava/lang/StringBuilder;";
                break;
            case Type.INT:
                descriptor = "(I)Ljava/lang/StringBuilder;";
                break;
            case Type.FLOAT:
                descriptor = "(F)Ljava/lang/StringBuilder;";
                break;
            case Type.LONG:
                descriptor = "(J)Ljava/lang/StringBuilder;";
                break;
            case Type.DOUBLE:
                descriptor = "(D)Ljava/lang/StringBuilder;";
                break;
            case Type.ARRAY:
                if (type.getElementType().getSort() == Type.CHAR) {
                    descriptor = "([C)Ljava/lang/StringBuilder;";
                    break;
                }
                descriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                break;
            case Type.OBJECT:
                if ("java/lang/String".equals(type.getInternalName())) {
                    descriptor = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
                } else {
                    descriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                }
                break;
            default:
                descriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
        }
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", descriptor, false);
    }

    private int estimateConcatCapacity(List<ConcatSegment> segments, Type[] argTypes) {
        int capacity = 0;
        for (ConcatSegment segment : segments) {
            if (segment.kind == ConcatSegmentKind.LITERAL && segment.literal != null) {
                capacity += segment.literal.length();
            } else if (segment.kind == ConcatSegmentKind.CONSTANT) {
                capacity += estimateConstant(segment.constant);
            } else if (segment.kind == ConcatSegmentKind.ARG) {
                capacity += estimateType(argTypes[segment.index]);
            }
        }
        return capacity;
    }

    private int estimateConstant(Object constant) {
        if (constant == null) {
            return 4;
        }
        if (constant instanceof String) {
            return ((String) constant).length();
        }
        if (constant instanceof Number) {
            return 16;
        }
        if (constant instanceof Character || constant instanceof Boolean) {
            return 1;
        }
        return 16;
    }

    private int estimateType(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return 5;
            case Type.CHAR:
                return 1;
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return 11;
            case Type.FLOAT:
                return 15;
            case Type.LONG:
                return 20;
            case Type.DOUBLE:
                return 24;
            default:
                return 16;
        }
    }

    private enum ConcatSegmentKind {
        LITERAL,
        ARG,
        CONSTANT
    }

    private static final class ConcatSegment {
        private final ConcatSegmentKind kind;
        private final String literal;
        private final int index;
        private final Object constant;

        private ConcatSegment(ConcatSegmentKind kind, String literal, int index, Object constant) {
            this.kind = kind;
            this.literal = literal;
            this.index = index;
            this.constant = constant;
        }

        static ConcatSegment literal(String value) {
            return new ConcatSegment(ConcatSegmentKind.LITERAL, value, -1, null);
        }

        static ConcatSegment argument(int index) {
            return new ConcatSegment(ConcatSegmentKind.ARG, null, index, null);
        }

        static ConcatSegment constant(Object constant) {
            return new ConcatSegment(ConcatSegmentKind.CONSTANT, null, -1, constant);
        }
    }
}
