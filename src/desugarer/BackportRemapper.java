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
        TYPE_MAP.put("java/lang/Module", "j9compat/Module");
        TYPE_MAP.put("java/lang/ModuleLayer", "j9compat/ModuleLayer");
        TYPE_MAP.put("java/lang/ModuleLayer$Controller", "j9compat/ModuleLayer$Controller");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor", "j9compat/ModuleDescriptor");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Builder", "j9compat/ModuleDescriptor$Builder");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Modifier", "j9compat/ModuleDescriptor$Modifier");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Requires", "j9compat/ModuleDescriptor$Requires");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Requires$Modifier", "j9compat/ModuleDescriptor$Requires$Modifier");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Exports", "j9compat/ModuleDescriptor$Exports");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Exports$Modifier", "j9compat/ModuleDescriptor$Exports$Modifier");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Opens", "j9compat/ModuleDescriptor$Opens");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Opens$Modifier", "j9compat/ModuleDescriptor$Opens$Modifier");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Provides", "j9compat/ModuleDescriptor$Provides");
        TYPE_MAP.put("java/lang/module/ModuleDescriptor$Version", "j9compat/ModuleDescriptor$Version");
        TYPE_MAP.put("java/lang/module/Configuration", "j9compat/Configuration");
        TYPE_MAP.put("java/lang/module/ModuleFinder", "j9compat/ModuleFinder");
        TYPE_MAP.put("java/lang/module/ModuleReference", "j9compat/ModuleReference");
        TYPE_MAP.put("java/lang/module/ModuleReader", "j9compat/ModuleReader");
        TYPE_MAP.put("java/lang/module/ResolvedModule", "j9compat/ResolvedModule");
        TYPE_MAP.put("java/lang/invoke/VarHandle", "j9compat/VarHandle");
        TYPE_MAP.put("java/lang/invoke/VarHandle$AccessMode", "j9compat/VarHandle$AccessMode");
        TYPE_MAP.put("java/net/http/HttpClient", "j9compat/HttpClient");
        TYPE_MAP.put("java/net/http/HttpClient$Builder", "j9compat/HttpClient$Builder");
        TYPE_MAP.put("java/net/http/HttpClient$Redirect", "j9compat/HttpClient$Redirect");
        TYPE_MAP.put("java/net/http/HttpClient$Version", "j9compat/HttpClient$Version");
        TYPE_MAP.put("java/net/http/HttpHeaders", "j9compat/HttpHeaders");
        TYPE_MAP.put("java/net/http/HttpRequest", "j9compat/HttpRequest");
        TYPE_MAP.put("java/net/http/HttpRequest$BodyPublisher", "j9compat/HttpRequest$BodyPublisher");
        TYPE_MAP.put("java/net/http/HttpRequest$BodyPublishers", "j9compat/HttpRequest$BodyPublishers");
        TYPE_MAP.put("java/net/http/HttpRequest$Builder", "j9compat/HttpRequest$Builder");
        TYPE_MAP.put("java/net/http/HttpResponse", "j9compat/HttpResponse");
        TYPE_MAP.put("java/net/http/HttpResponse$BodyHandler", "j9compat/HttpResponse$BodyHandler");
        TYPE_MAP.put("java/net/http/HttpResponse$BodyHandlers", "j9compat/HttpResponse$BodyHandlers");
        TYPE_MAP.put("java/net/http/HttpResponse$BodySubscriber", "j9compat/HttpResponse$BodySubscriber");
        TYPE_MAP.put("java/net/http/HttpResponse$ResponseInfo", "j9compat/HttpResponse$ResponseInfo");
        TYPE_MAP.put("java/net/http/HttpResponse$PushPromiseHandler", "j9compat/HttpResponse$PushPromiseHandler");
    }

    @Override
    public String map(String internalName) {
        String mapped = TYPE_MAP.get(internalName);
        return mapped != null ? mapped : internalName;
    }
}
