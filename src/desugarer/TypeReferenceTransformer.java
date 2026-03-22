package desugarer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TypeReferenceTransformer implements SourceTransformer {
    private static final TypeMapping[] TYPE_MAPPINGS = {
            new TypeMapping("java.lang.ProcessHandle", "j9compat.ProcessHandle"),
            new TypeMapping("java.lang.StackWalker", "j9compat.StackWalker"),
            new TypeMapping("java.lang.Module", "j9compat.Module"),
            new TypeMapping("java.lang.ModuleLayer", "j9compat.ModuleLayer"),
            new TypeMapping("java.lang.module.ModuleDescriptor", "j9compat.ModuleDescriptor"),
            new TypeMapping("java.lang.module.Configuration", "j9compat.Configuration"),
            new TypeMapping("java.lang.module.ModuleFinder", "j9compat.ModuleFinder"),
            new TypeMapping("java.lang.module.ModuleReference", "j9compat.ModuleReference"),
            new TypeMapping("java.lang.module.ModuleReader", "j9compat.ModuleReader"),
            new TypeMapping("java.lang.module.ResolvedModule", "j9compat.ResolvedModule"),
            new TypeMapping("java.lang.invoke.VarHandle", "j9compat.VarHandle"),
            new TypeMapping("java.util.concurrent.Flow", "j9compat.Flow"),
            new TypeMapping("java.util.concurrent.SubmissionPublisher", "j9compat.SubmissionPublisher"),
            new TypeMapping("java.net.http.HttpClient", "j9compat.HttpClient"),
            new TypeMapping("java.net.http.HttpHeaders", "j9compat.HttpHeaders"),
            new TypeMapping("java.net.http.HttpRequest", "j9compat.HttpRequest"),
            new TypeMapping("java.net.http.HttpResponse", "j9compat.HttpResponse")
    };

    private static final Pattern TYPE_DECLARATION_PATTERN = Pattern.compile(
            "\\b(class|interface|enum|@interface)\\s+([\\p{L}\\p{N}_$]+)");

    @Override
    public String transform(String source, SourceContext context) {
        Set<String> declaredTypes = findDeclaredTypes(source);
        List<PreparedMapping> prepared = prepareMappings(context.getImports(), declaredTypes);
        return SourceDesugarer.transformCodeSegments(source,
                (code, ctx) -> transformCode(code, prepared), context);
    }

    private List<PreparedMapping> prepareMappings(ImportAnalyzer imports, Set<String> declaredTypes) {
        List<PreparedMapping> prepared = new ArrayList<PreparedMapping>();
        for (TypeMapping mapping : TYPE_MAPPINGS) {
            String explicitImport = imports.getExplicitImport(mapping.simpleName);
            boolean hasExplicitOriginal = mapping.originalFqn.equals(explicitImport);
            boolean hasExplicitOther = explicitImport != null && !hasExplicitOriginal;
            boolean declared = declaredTypes.contains(mapping.simpleName);
            boolean replaceSimple = !hasExplicitOriginal && !hasExplicitOther && !declared;
            prepared.add(new PreparedMapping(mapping, replaceSimple));
        }
        return prepared;
    }

    private String transformCode(String code, List<PreparedMapping> prepared) {
        String updated = code;
        updated = replaceWildcardImport(updated, "java.net.http", "j9compat");
        updated = replaceWildcardImport(updated, "java.lang.module", "j9compat");

        for (PreparedMapping mapping : prepared) {
            updated = replaceQualified(updated, mapping.mapping.originalFqn, mapping.mapping.backportFqn);
        }
        for (PreparedMapping mapping : prepared) {
            if (mapping.replaceSimple) {
                updated = replaceSimpleName(updated, mapping.mapping.simpleName, mapping.mapping.backportFqn);
            }
        }
        return updated;
    }

    private String replaceWildcardImport(String code, String originalPackage, String replacementPackage) {
        Pattern pattern = Pattern.compile("(?m)^\\s*import\\s+"
                + Pattern.quote(originalPackage)
                + "\\s*\\.\\s*\\*\\s*;");
        Matcher matcher = pattern.matcher(code);
        if (!matcher.find()) {
            return code;
        }
        matcher.reset();
        return matcher.replaceAll("import " + replacementPackage + ".*;");
    }

    private String replaceQualified(String code, String originalFqn, String backportFqn) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(originalFqn) + "\\b");
        Matcher matcher = pattern.matcher(code);
        if (!matcher.find()) {
            return code;
        }
        matcher.reset();
        return matcher.replaceAll(backportFqn);
    }

    private String replaceSimpleName(String code, String simpleName, String backportFqn) {
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}_$.])" + Pattern.quote(simpleName) + "\\b");
        Matcher matcher = pattern.matcher(code);
        if (!matcher.find()) {
            return code;
        }
        matcher.reset();
        return matcher.replaceAll(backportFqn);
    }

    private Set<String> findDeclaredTypes(String source) {
        Set<String> declared = new HashSet<String>();
        List<SourceDesugarer.Segment> segments = SourceDesugarer.splitSegments(source);
        for (SourceDesugarer.Segment segment : segments) {
            if (!segment.isCode) {
                continue;
            }
            Matcher matcher = TYPE_DECLARATION_PATTERN.matcher(segment.text);
            while (matcher.find()) {
                declared.add(matcher.group(2));
            }
        }
        return declared;
    }

    private static final class TypeMapping {
        private final String originalFqn;
        private final String backportFqn;
        private final String simpleName;

        private TypeMapping(String originalFqn, String backportFqn) {
            this.originalFqn = originalFqn;
            this.backportFqn = backportFqn;
            int lastDot = originalFqn.lastIndexOf('.');
            this.simpleName = lastDot >= 0 ? originalFqn.substring(lastDot + 1) : originalFqn;
        }
    }

    private static final class PreparedMapping {
        private final TypeMapping mapping;
        private final boolean replaceSimple;

        private PreparedMapping(TypeMapping mapping, boolean replaceSimple) {
            this.mapping = mapping;
            this.replaceSimple = replaceSimple;
        }
    }
}
