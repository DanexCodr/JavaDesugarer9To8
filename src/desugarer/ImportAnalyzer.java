package desugarer;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImportAnalyzer {
    private static final String IDENT = "[\\p{L}\\p{N}_$]+";
    private static final String QUALIFIED = IDENT + "(?:\\." + IDENT + ")*";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+(" + QUALIFIED + ")\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?(" + QUALIFIED + "(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);

    private final Map<String, String> explicitImports = new HashMap<>();
    private final Set<String> wildcardImports = new HashSet<>();
    private final Map<String, Set<String>> staticImports = new HashMap<>();
    private final Set<String> staticWildcardImports = new HashSet<>();
    private final Set<String> existingImports = new HashSet<>();
    private final String packageName;

    public ImportAnalyzer(String source) {
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(source);
        this.packageName = packageMatcher.find() ? packageMatcher.group(1) : null;

        Matcher importMatcher = IMPORT_PATTERN.matcher(source);
        while (importMatcher.find()) {
            boolean isStatic = importMatcher.group(1) != null;
            String imported = importMatcher.group(2);
            existingImports.add(imported);
            if (isStatic) {
                if (imported.endsWith(".*")) {
                    staticWildcardImports.add(imported.substring(0, imported.length() - 2));
                } else {
                    int lastDot = imported.lastIndexOf('.');
                    if (lastDot > 0) {
                        String owner = imported.substring(0, lastDot);
                        String member = imported.substring(lastDot + 1);
                        staticImports.computeIfAbsent(member, key -> new HashSet<>()).add(owner);
                    }
                }
            } else {
                if (imported.endsWith(".*")) {
                    wildcardImports.add(imported.substring(0, imported.length() - 2));
                } else {
                    int lastDot = imported.lastIndexOf('.');
                    if (lastDot > 0) {
                        String simpleName = imported.substring(lastDot + 1);
                        explicitImports.put(simpleName, imported);
                    }
                }
            }
        }
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isTypeImported(String simpleName, String fqn) {
        String explicit = explicitImports.get(simpleName);
        if (explicit != null) {
            return explicit.equals(fqn);
        }
        int lastDot = fqn.lastIndexOf('.');
        String pkg = lastDot > 0 ? fqn.substring(0, lastDot) : "";
        if (wildcardImports.contains(pkg)) {
            return !explicitImports.containsKey(simpleName);
        }
        return false;
    }

    public boolean hasStaticImport(String ownerFqn, String member) {
        Set<String> owners = staticImports.get(member);
        if (owners != null && owners.contains(ownerFqn)) {
            return true;
        }
        return staticWildcardImports.contains(ownerFqn);
    }

    public boolean hasExistingImport(String fqn) {
        return existingImports.contains(fqn);
    }

    public Set<String> getExistingImports() {
        return Collections.unmodifiableSet(existingImports);
    }

    public String getExplicitImport(String simpleName) {
        return explicitImports.get(simpleName);
    }

    public static String applyImports(String source, ImportAnalyzer analyzer, Set<String> neededImports) {
        if (neededImports.isEmpty()) {
            return source;
        }
        StringBuilder importBlock = new StringBuilder();
        for (String fqn : neededImports) {
            if (!analyzer.hasExistingImport(fqn)) {
                importBlock.append("import ").append(fqn).append(";\n");
            }
        }
        if (importBlock.length() == 0) {
            return source;
        }

        Matcher importMatcher = IMPORT_PATTERN.matcher(source);
        int lastImportEnd = -1;
        while (importMatcher.find()) {
            lastImportEnd = importMatcher.end();
        }

        if (lastImportEnd >= 0) {
            StringBuilder updated = new StringBuilder();
            updated.append(source, 0, lastImportEnd);
            if (lastImportEnd > 0 && source.charAt(lastImportEnd - 1) != '\n') {
                updated.append('\n');
            }
            updated.append(importBlock);
            updated.append(source.substring(lastImportEnd));
            return updated.toString();
        }

        Matcher packageMatcher = PACKAGE_PATTERN.matcher(source);
        if (packageMatcher.find()) {
            int packageEnd = packageMatcher.end();
            StringBuilder updated = new StringBuilder();
            updated.append(source, 0, packageEnd);
            updated.append("\n\n");
            updated.append(importBlock);
            updated.append(source.substring(packageEnd));
            return updated.toString();
        }

        return importBlock + "\n" + source;
    }
}
