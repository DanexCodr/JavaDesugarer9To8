package desugarer;

/**
 * Java 10 API remapping rules layered on top of the Java 9 desugarer.
 */
final class Java10MethodTransform implements MethodTransform {

    private static final String BP_COLLECTORS = "j9compat/CollectorsBackport";
    private static final String BP_OPTIONAL = "j9compat/OptionalBackport";
    private static final String BP_OPTIONAL_INT = "j9compat/OptionalIntBackport";
    private static final String BP_OPTIONAL_LONG = "j9compat/OptionalLongBackport";
    private static final String BP_OPTIONAL_DOUBLE = "j9compat/OptionalDoubleBackport";

    @Override
    public boolean transform(MethodDesugarer desugarer, int opcode, String owner,
                             String name, String descriptor, boolean isInterface) {
        if ("java/util/stream/Collectors".equals(owner)) {
            if ("toUnmodifiableList".equals(name)) {
                desugarer.remap(BP_COLLECTORS, "toUnmodifiableList", descriptor);
                return true;
            }
            if ("toUnmodifiableSet".equals(name)) {
                desugarer.remap(BP_COLLECTORS, "toUnmodifiableSet", descriptor);
                return true;
            }
            if ("toUnmodifiableMap".equals(name)) {
                desugarer.remap(BP_COLLECTORS, "toUnmodifiableMap", descriptor);
                return true;
            }
        }

        if ("java/util/Optional".equals(owner)
                && "orElseThrow".equals(name)
                && "()Ljava/lang/Object;".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_OPTIONAL, "orElseThrow",
                    "java/util/Optional", descriptor);
            return true;
        }

        if ("java/util/OptionalInt".equals(owner)
                && "orElseThrow".equals(name)
                && "()I".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_OPTIONAL_INT, "orElseThrow",
                    "java/util/OptionalInt", descriptor);
            return true;
        }

        if ("java/util/OptionalLong".equals(owner)
                && "orElseThrow".equals(name)
                && "()J".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_OPTIONAL_LONG, "orElseThrow",
                    "java/util/OptionalLong", descriptor);
            return true;
        }

        if ("java/util/OptionalDouble".equals(owner)
                && "orElseThrow".equals(name)
                && "()D".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_OPTIONAL_DOUBLE, "orElseThrow",
                    "java/util/OptionalDouble", descriptor);
            return true;
        }

        return false;
    }
}
