package desugarer;

/**
 * Framework extension point for additional method call rewrites.
 *
 * <p>Implementations can be registered via {@link java.util.ServiceLoader}
 * to contribute extra desugaring rules without modifying the core tool.
 */
public interface MethodTransform {

    /**
     * Attempts to rewrite a method invocation.
     *
     * @return {@code true} if the invocation was handled and emitted.
     */
    boolean transform(MethodDesugarer desugarer, int opcode, String owner,
                      String name, String descriptor, boolean isInterface);
}
