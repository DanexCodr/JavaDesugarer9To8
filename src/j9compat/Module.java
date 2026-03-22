package j9compat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Java 8-compatible backport of {@link java.lang.Module}.
 */
public final class Module implements AnnotatedElement {

    private final String name;
    private final ClassLoader classLoader;
    private final ModuleDescriptor descriptor;
    private final ModuleLayer layer;
    private final Set<String> packages;
    private final Set<String> readableModules;
    private final Set<String> exportedPackages;
    private final Map<String, Set<String>> qualifiedExports;
    private final Set<String> openPackages;
    private final Map<String, Set<String>> qualifiedOpens;
    private final Set<String> uses;
    private final boolean openAll;

    Module(String name, ClassLoader classLoader, ModuleDescriptor descriptor,
           ModuleLayer layer, Set<String> packages) {
        this.name = name;
        this.classLoader = classLoader;
        this.descriptor = descriptor;
        this.layer = layer;
        this.packages = packages == null ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new HashSet<String>(packages));
        this.readableModules = Collections.synchronizedSet(new HashSet<String>());
        this.exportedPackages = Collections.synchronizedSet(new HashSet<String>());
        this.qualifiedExports = Collections.synchronizedMap(new HashMap<String, Set<String>>());
        this.openPackages = Collections.synchronizedSet(new HashSet<String>());
        this.qualifiedOpens = Collections.synchronizedMap(new HashMap<String, Set<String>>());
        this.uses = Collections.synchronizedSet(new HashSet<String>());
        this.openAll = descriptor != null && descriptor.isOpen();
        initializeAccess();
    }

    public boolean isNamed() {
        return name != null && name.length() > 0;
    }

    public String getName() {
        return name;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ModuleDescriptor getDescriptor() {
        return descriptor;
    }

    public ModuleLayer getLayer() {
        return layer != null ? layer : ModuleLayer.boot();
    }

    public boolean canRead(Module other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }
        if (!isNamed()) {
            return true;
        }
        if (!other.isNamed()) {
            return true;
        }
        return readableModules.contains(other.getName());
    }

    public Module addReads(Module other) {
        if (other != null && other.getName() != null) {
            readableModules.add(other.getName());
        }
        return this;
    }

    public boolean isExported(String pkg, Module other) {
        if (pkg == null) {
            return false;
        }
        if (!isExported(pkg)) {
            return false;
        }
        Set<String> targets = qualifiedExports.get(pkg);
        if (targets == null || targets.isEmpty()) {
            return true;
        }
        return other != null && targets.contains(other.getName());
    }

    public boolean isOpen(String pkg, Module other) {
        if (pkg == null) {
            return false;
        }
        if (!isOpen(pkg)) {
            return false;
        }
        Set<String> targets = qualifiedOpens.get(pkg);
        if (targets == null || targets.isEmpty()) {
            return true;
        }
        return other != null && targets.contains(other.getName());
    }

    public boolean isExported(String pkg) {
        if (pkg == null) {
            return false;
        }
        if (!isNamed()) {
            return packages.isEmpty() || packages.contains(pkg);
        }
        if (!packages.isEmpty() && !packages.contains(pkg)) {
            return false;
        }
        return exportedPackages.contains(pkg) || qualifiedExports.containsKey(pkg);
    }

    public boolean isOpen(String pkg) {
        if (pkg == null) {
            return false;
        }
        if (!isNamed()) {
            return packages.isEmpty() || packages.contains(pkg);
        }
        if (!packages.isEmpty() && !packages.contains(pkg)) {
            return false;
        }
        return openAll || openPackages.contains(pkg) || qualifiedOpens.containsKey(pkg);
    }

    public Module addExports(String pkg, Module other) {
        if (pkg == null) {
            return this;
        }
        if (other == null || other.getName() == null) {
            exportedPackages.add(pkg);
            qualifiedExports.remove(pkg);
        } else {
            Set<String> targets = qualifiedExports.get(pkg);
            if (targets == null) {
                targets = new HashSet<String>();
                qualifiedExports.put(pkg, targets);
            }
            targets.add(other.getName());
        }
        return this;
    }

    public Module addOpens(String pkg, Module other) {
        if (pkg == null) {
            return this;
        }
        if (other == null || other.getName() == null) {
            openPackages.add(pkg);
            qualifiedOpens.remove(pkg);
        } else {
            Set<String> targets = qualifiedOpens.get(pkg);
            if (targets == null) {
                targets = new HashSet<String>();
                qualifiedOpens.put(pkg, targets);
            }
            targets.add(other.getName());
        }
        return this;
    }

    public Module addUses(Class<?> service) {
        if (service != null) {
            uses.add(service.getName());
        }
        return this;
    }

    public boolean canUse(Class<?> service) {
        if (service == null) {
            return false;
        }
        if (!isNamed()) {
            return true;
        }
        return uses.contains(service.getName());
    }

    public Set<String> getPackages() {
        return packages;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    @Override
    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    public InputStream getResourceAsStream(String name) throws IOException {
        if (name == null) {
            return null;
        }
        if (classLoader != null) {
            return classLoader.getResourceAsStream(name);
        }
        return ClassLoader.getSystemResourceAsStream(name);
    }

    @Override
    public String toString() {
        return isNamed() ? "module " + name : "unnamed module";
    }

    static Module unnamed(ClassLoader loader, ModuleLayer layer, Set<String> packages) {
        return new Module(null, loader, ModuleDescriptor.unnamed(), layer, packages);
    }

    static Module named(ModuleDescriptor descriptor, ClassLoader loader, ModuleLayer layer) {
        String name = descriptor != null ? descriptor.name() : null;
        Set<String> packages = descriptor != null ? descriptor.packages() : Collections.<String>emptySet();
        return new Module(name, loader, descriptor, layer, packages);
    }

    private void initializeAccess() {
        if (descriptor == null) {
            return;
        }
        for (ModuleDescriptor.Requires requires : descriptor.requires()) {
            if (requires.name() != null) {
                readableModules.add(requires.name());
            }
        }
        for (ModuleDescriptor.Exports exports : descriptor.exports()) {
            if (exports.source() == null) {
                continue;
            }
            if (exports.targets().isEmpty()) {
                exportedPackages.add(exports.source());
            } else {
                qualifiedExports.put(exports.source(), new HashSet<String>(exports.targets()));
            }
        }
        for (ModuleDescriptor.Opens opens : descriptor.opens()) {
            if (opens.source() == null) {
                continue;
            }
            if (opens.targets().isEmpty()) {
                openPackages.add(opens.source());
            } else {
                qualifiedOpens.put(opens.source(), new HashSet<String>(opens.targets()));
            }
        }
        uses.addAll(descriptor.uses());
    }
}
