package desugarer;

import org.objectweb.asm.*;

/**
 * ASM {@link ClassVisitor} that performs per-class desugaring:
 *
 * <ul>
 *   <li>Downgrades the class-file version to Java 8 (52) when it is higher.</li>
 *   <li>Strips {@code ACC_PRIVATE} from interface methods so that the Java 8
 *       verifier accepts them (private interface methods were introduced in
 *       Java 9). The visibility is changed to package-private; callers within
 *       the same package can still reach the method.</li>
 *   <li>Delegates every method body to {@link MethodDesugarer} for API-call
 *       remapping.</li>
 * </ul>
 */
public class ClassDesugarer extends ClassVisitor {

    private static final int JAVA_8_VERSION = Opcodes.V1_8; // 52

    private final Java9ToJava8Desugarer.Stats stats;
    private boolean isInterface;

    public ClassDesugarer(ClassVisitor cv, Java9ToJava8Desugarer.Stats stats) {
        super(Opcodes.ASM9, cv);
        this.stats = stats;
    }

    // ── Class header ────────────────────────────────────────────────────────

    @Override
    public void visit(int version, int access, String name,
                      String signature, String superName, String[] interfaces) {

        this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;

        if (version > JAVA_8_VERSION) {
            System.out.println("  [VER  ]  " + name
                    + "  v" + version + " → v" + JAVA_8_VERSION);
            stats.versionDowngraded++;
            version = JAVA_8_VERSION;
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    // ── Method declarations ──────────────────────────────────────────────────

    @Override
    public MethodVisitor visitMethod(int access, String name,
                                      String descriptor, String signature,
                                      String[] exceptions) {

        // Java 9 allows private (and private static) interface methods.
        // Java 8 does not – remove the private flag to make them
        // package-private so the verifier accepts them.
        if (isInterface && (access & Opcodes.ACC_PRIVATE) != 0) {
            System.out.println("  [IPRV ]  private interface method made package-private: "
                    + name + descriptor);
            stats.privateIfaceMethods++;
            access &= ~Opcodes.ACC_PRIVATE;   // clear ACC_PRIVATE
            // Keep ACC_STATIC if present; otherwise it stays a virtual method
        }

        MethodVisitor mv = super.visitMethod(access, name, descriptor,
                signature, exceptions);
        if (mv == null) return null;

        return new MethodDesugarer(mv, stats);
    }
}
