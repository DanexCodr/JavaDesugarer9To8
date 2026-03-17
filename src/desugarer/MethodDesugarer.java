package desugarer;

import org.objectweb.asm.*;

/**
 * ASM {@link MethodVisitor} that rewrites Java 9-only API calls to equivalent
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
 * <h2>Covered Java 9 APIs</h2>
 * <ul>
 *   <li>{@code List.of}, {@code Set.of}, {@code Map.of}, {@code Map.ofEntries},
 *       {@code Map.entry}, {@code List.copyOf}, {@code Set.copyOf},
 *       {@code Map.copyOf}</li>
 *   <li>{@code Stream.takeWhile}, {@code Stream.dropWhile},
 *       {@code Stream.ofNullable}, {@code Stream.iterate(seed,hasNext,f)}</li>
 *   <li>{@code Optional.ifPresentOrElse}, {@code Optional.or},
 *       {@code Optional.stream}</li>
 *   <li>{@code InputStream.transferTo}, {@code InputStream.readAllBytes},
 *       {@code InputStream.readNBytes}</li>
 *   <li>{@code Objects.requireNonNullElse}, {@code Objects.requireNonNullElseGet},
 *       {@code Objects.checkIndex}</li>
 *   <li>{@code CompletableFuture.orTimeout},
 *       {@code CompletableFuture.completeOnTimeout},
 *       {@code CompletableFuture.failedFuture},
 *       {@code CompletableFuture.completedStage},
 *       {@code CompletableFuture.failedStage}</li>
 * </ul>
 */
public class MethodDesugarer extends MethodVisitor {

    // Backport class internal names (JVM-style, '/' not '.')
    private static final String BP_COLLECTION  = "j9compat/CollectionBackport";
    private static final String BP_STREAM      = "j9compat/StreamBackport";
    private static final String BP_OPTIONAL    = "j9compat/OptionalBackport";
    private static final String BP_IO          = "j9compat/IOBackport";
    private static final String BP_OBJECTS     = "j9compat/ObjectsBackport";
    private static final String BP_CF          = "j9compat/CompletableFutureBackport";

    private final Java9ToJava8Desugarer.Stats stats;

    public MethodDesugarer(MethodVisitor mv, Java9ToJava8Desugarer.Stats stats) {
        super(Opcodes.ASM9, mv);
        this.stats = stats;
    }

    // ────────────────────────────────────────────────────────────────────────

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
                                 String descriptor, boolean isInterface) {

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

        // ── java.io.InputStream additions ────────────────────────────────────
        if ("java/io/InputStream".equals(owner)) {
            if ("transferTo".equals(name)) {
                remapInstanceToStatic(BP_IO, "transferTo",
                        "java/io/InputStream", descriptor); return;
            }
            if ("readAllBytes".equals(name)) {
                remapInstanceToStatic(BP_IO, "readAllBytes",
                        "java/io/InputStream", descriptor); return;
            }
            if ("readNBytes".equals(name)) {
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
            if ("copy".equals(name)) {
                remapInstanceToStatic(BP_CF, "copy",
                        "java/util/concurrent/CompletableFuture", descriptor); return;
            }
        }

        // No remapping needed – emit unchanged
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Emits an INVOKESTATIC to the given backport class with the *same*
     * descriptor (used for static-interface-method replacements).
     */
    private void remap(String newOwner, String newName, String descriptor) {
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
    private void remapInstanceToStatic(String newOwner, String newName,
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
}
