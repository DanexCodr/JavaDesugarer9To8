package desugarer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks class hierarchy information for classes in the input JAR and uses
 * best-effort reflection for platform classes to answer assignability queries.
 */
final class ClassHierarchy {

    private static final String INPUT_STREAM = "java/io/InputStream";
    private static final Class<?> INPUT_STREAM_CLASS = java.io.InputStream.class;

    private final Map<String, ClassInfo> classes = new HashMap<String, ClassInfo>();

    void record(String name, String superName, String[] interfaces) {
        if (name == null) {
            return;
        }
        classes.put(name, new ClassInfo(superName, interfaces));
    }

    boolean isInputStreamOwner(String owner) {
        if (owner == null) {
            return false;
        }
        Set<String> visited = new HashSet<String>();
        Deque<String> stack = new ArrayDeque<String>();
        stack.push(owner);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (INPUT_STREAM.equals(current)) {
                return true;
            }
            ClassInfo info = classes.get(current);
            if (info == null) {
                Class<?> loaded = tryLoad(current);
                if (loaded != null && INPUT_STREAM_CLASS.isAssignableFrom(loaded)) {
                    return true;
                }
                continue;
            }
            if (info.superName != null) {
                stack.push(info.superName);
            }
            for (String iface : info.interfaces) {
                stack.push(iface);
            }
        }
        return false;
    }

    private static Class<?> tryLoad(String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false,
                    ClassHierarchy.class.getClassLoader());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class ClassInfo {
        private final String superName;
        private final String[] interfaces;

        private ClassInfo(String superName, String[] interfaces) {
            this.superName = superName;
            this.interfaces = interfaces != null ? interfaces : new String[0];
        }
    }
}
