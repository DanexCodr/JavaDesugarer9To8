package desugarer;

/**
 * Java 11 API remapping rules layered on top of the Java 9–11 desugarer.
 */
final class Java11MethodTransform implements MethodTransform {

    private static final String BP_COLLECTION = "j9compat/CollectionBackport";
    private static final String BP_STRING = "j9compat/StringBackport";
    private static final String BP_FILES = "j9compat/FilesBackport";
    private static final String BP_PATH = "j9compat/PathBackport";
    private static final String BP_PREDICATE = "j9compat/PredicateBackport";
    private static final String BP_OPTIONAL = "j9compat/OptionalBackport";
    private static final String BP_OPTIONAL_INT = "j9compat/OptionalIntBackport";
    private static final String BP_OPTIONAL_LONG = "j9compat/OptionalLongBackport";
    private static final String BP_OPTIONAL_DOUBLE = "j9compat/OptionalDoubleBackport";

    @Override
    public boolean transform(MethodDesugarer desugarer, int opcode, String owner,
                             String name, String descriptor, boolean isInterface) {
        if ("java/lang/String".equals(owner)) {
            if ("isBlank".equals(name) && "()Z".equals(descriptor)) {
                desugarer.remapInstanceToStatic(BP_STRING, "isBlank",
                        "java/lang/String", descriptor);
                return true;
            }
            if ("lines".equals(name) && "()Ljava/util/stream/Stream;".equals(descriptor)) {
                desugarer.remapInstanceToStatic(BP_STRING, "lines",
                        "java/lang/String", descriptor);
                return true;
            }
            if ("strip".equals(name) && "()Ljava/lang/String;".equals(descriptor)) {
                desugarer.remapInstanceToStatic(BP_STRING, "strip",
                        "java/lang/String", descriptor);
                return true;
            }
            if ("stripLeading".equals(name) && "()Ljava/lang/String;".equals(descriptor)) {
                desugarer.remapInstanceToStatic(BP_STRING, "stripLeading",
                        "java/lang/String", descriptor);
                return true;
            }
            if ("stripTrailing".equals(name) && "()Ljava/lang/String;".equals(descriptor)) {
                desugarer.remapInstanceToStatic(BP_STRING, "stripTrailing",
                        "java/lang/String", descriptor);
                return true;
            }
            if ("repeat".equals(name) && "(I)Ljava/lang/String;".equals(descriptor)) {
                desugarer.remapInstanceToStatic(BP_STRING, "repeat",
                        "java/lang/String", descriptor);
                return true;
            }
        }

        if ("java/util/Optional".equals(owner) && "isEmpty".equals(name)
                && "()Z".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_OPTIONAL, "isEmpty",
                    "java/util/Optional", descriptor);
            return true;
        }

        if ("java/util/OptionalInt".equals(owner) && "isEmpty".equals(name)
                && "()Z".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_OPTIONAL_INT, "isEmpty",
                    "java/util/OptionalInt", descriptor);
            return true;
        }

        if ("java/util/OptionalLong".equals(owner) && "isEmpty".equals(name)
                && "()Z".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_OPTIONAL_LONG, "isEmpty",
                    "java/util/OptionalLong", descriptor);
            return true;
        }

        if ("java/util/OptionalDouble".equals(owner) && "isEmpty".equals(name)
                && "()Z".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_OPTIONAL_DOUBLE, "isEmpty",
                    "java/util/OptionalDouble", descriptor);
            return true;
        }

        if (("java/util/Collection".equals(owner)
                || "java/util/List".equals(owner)
                || "java/util/Set".equals(owner))
                && "toArray".equals(name)
                && "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;".equals(descriptor)) {
            desugarer.remapInstanceToStatic(BP_COLLECTION, "toArray",
                    "java/util/Collection", descriptor);
            return true;
        }

        if ("java/nio/file/Files".equals(owner)) {
            if ("readString".equals(name)
                    && "(Ljava/nio/file/Path;)Ljava/lang/String;".equals(descriptor)) {
                desugarer.remap(BP_FILES, "readString", descriptor);
                return true;
            }
            if ("readString".equals(name)
                    && "(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/lang/String;".equals(descriptor)) {
                desugarer.remap(BP_FILES, "readString", descriptor);
                return true;
            }
            if ("writeString".equals(name)
                    && "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;".equals(descriptor)) {
                desugarer.remap(BP_FILES, "writeString", descriptor);
                return true;
            }
            if ("writeString".equals(name)
                    && "(Ljava/nio/file/Path;Ljava/lang/CharSequence;Ljava/nio/charset/Charset;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;".equals(descriptor)) {
                desugarer.remap(BP_FILES, "writeString", descriptor);
                return true;
            }
        }

        if ("java/nio/file/Path".equals(owner) && "of".equals(name)) {
            if ("(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;".equals(descriptor)
                    || "(Ljava/net/URI;)Ljava/nio/file/Path;".equals(descriptor)) {
                desugarer.remap(BP_PATH, "of", descriptor);
                return true;
            }
        }

        if ("java/util/function/Predicate".equals(owner)
                && "not".equals(name)
                && "(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;".equals(descriptor)) {
            desugarer.remap(BP_PREDICATE, "not", descriptor);
            return true;
        }

        return false;
    }
}
