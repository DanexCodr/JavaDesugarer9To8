package desugarer;

import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Remaps Java 9-only types to j9compat backport equivalents.
 */
public final class BackportRemapper extends Remapper {

    private static final Map<String, String> TYPE_MAP = new HashMap<String, String>();

    static {
        TYPE_MAP.put("java/lang/ProcessHandle", "j9compat/ProcessHandle");
        TYPE_MAP.put("java/lang/ProcessHandle$Info", "j9compat/ProcessHandle$Info");
        TYPE_MAP.put("java/lang/StackWalker", "j9compat/StackWalker");
        TYPE_MAP.put("java/lang/StackWalker$Option", "j9compat/StackWalker$Option");
        TYPE_MAP.put("java/lang/StackWalker$StackFrame", "j9compat/StackWalker$StackFrame");
        TYPE_MAP.put("java/util/concurrent/Flow", "j9compat/Flow");
        TYPE_MAP.put("java/util/concurrent/Flow$Publisher", "j9compat/Flow$Publisher");
        TYPE_MAP.put("java/util/concurrent/Flow$Subscriber", "j9compat/Flow$Subscriber");
        TYPE_MAP.put("java/util/concurrent/Flow$Subscription", "j9compat/Flow$Subscription");
        TYPE_MAP.put("java/util/concurrent/Flow$Processor", "j9compat/Flow$Processor");
        TYPE_MAP.put("java/util/concurrent/SubmissionPublisher", "j9compat/SubmissionPublisher");
    }

    @Override
    public String map(String internalName) {
        String mapped = TYPE_MAP.get(internalName);
        return mapped != null ? mapped : internalName;
    }
}
