package j9compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Java 8-compatible backport of {@link java.lang.ModuleLayer}.
 */
public final class ModuleLayer {

    private static final ModuleLayer EMPTY = new ModuleLayer(Configuration.empty(),
            Collections.<ModuleLayer>emptyList(),
            Collections.<String, Module>emptyMap(),
            Collections.<String, ClassLoader>emptyMap(),
            Module.unnamed(null, null, Collections.<String>emptySet()));

    private static final ModuleLayer BOOT = createBootLayer();

    private final Configuration configuration;
    private final List<ModuleLayer> parents;
    private final Map<String, Module> modules;
    private final Map<String, ClassLoader> loaders;
    private final Module unnamedModule;

    private ModuleLayer(Configuration configuration, List<ModuleLayer> parents,
                        Map<String, Module> modules, Map<String, ClassLoader> loaders,
                        Module unnamedModule) {
        this.configuration = configuration == null ? Configuration.empty() : configuration;
        this.parents = parents == null ? Collections.<ModuleLayer>emptyList()
                : Collections.unmodifiableList(new ArrayList<ModuleLayer>(parents));
        this.modules = modules == null ? Collections.<String, Module>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, Module>(modules));
        this.loaders = loaders == null ? Collections.<String, ClassLoader>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, ClassLoader>(loaders));
        this.unnamedModule = unnamedModule;
    }

    public ModuleLayer defineModulesWithOneLoader(Configuration configuration, ClassLoader loader) {
        return defineModules(configuration, new Function<String, ClassLoader>() {
            @Override
            public ClassLoader apply(String name) {
                return loader;
            }
        });
    }

    public ModuleLayer defineModulesWithManyLoaders(Configuration configuration, ClassLoader loader) {
        return defineModules(configuration, new Function<String, ClassLoader>() {
            @Override
            public ClassLoader apply(String name) {
                return loader;
            }
        });
    }

    public ModuleLayer defineModules(Configuration configuration,
                                     Function<String, ClassLoader> loaderFunction) {
        return buildLayer(configuration, parents, loaderFunction);
    }

    public static Controller defineModulesWithOneLoader(Configuration configuration,
                                                        List<ModuleLayer> parents,
                                                        ClassLoader loader) {
        ModuleLayer layer = buildLayer(configuration, parents, new Function<String, ClassLoader>() {
            @Override
            public ClassLoader apply(String name) {
                return loader;
            }
        });
        return new Controller(layer);
    }

    public static Controller defineModulesWithManyLoaders(Configuration configuration,
                                                         List<ModuleLayer> parents,
                                                         ClassLoader loader) {
        ModuleLayer layer = buildLayer(configuration, parents, new Function<String, ClassLoader>() {
            @Override
            public ClassLoader apply(String name) {
                return loader;
            }
        });
        return new Controller(layer);
    }

    public static Controller defineModules(Configuration configuration,
                                           List<ModuleLayer> parents,
                                           Function<String, ClassLoader> loaderFunction) {
        return new Controller(buildLayer(configuration, parents, loaderFunction));
    }

    public Configuration configuration() {
        return configuration;
    }

    public List<ModuleLayer> parents() {
        return parents;
    }

    public Set<Module> modules() {
        return new HashSet<Module>(modules.values());
    }

    public Optional<Module> findModule(String name) {
        if (name == null) {
            return Optional.<Module>empty();
        }
        Module module = modules.get(name);
        if (module != null) {
            return Optional.<Module>of(module);
        }
        for (ModuleLayer parent : parents) {
            Optional<Module> found = parent.findModule(name);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.<Module>empty();
    }

    public ClassLoader findLoader(String name) {
        if (name == null) {
            return null;
        }
        ClassLoader loader = loaders.get(name);
        if (loader != null) {
            return loader;
        }
        for (ModuleLayer parent : parents) {
            ClassLoader parentLoader = parent.findLoader(name);
            if (parentLoader != null) {
                return parentLoader;
            }
        }
        return null;
    }

    public Module unnamedModule() {
        return unnamedModule;
    }

    @Override
    public String toString() {
        return "ModuleLayer[" + modules.keySet() + "]";
    }

    public static ModuleLayer empty() {
        return EMPTY;
    }

    public static ModuleLayer boot() {
        return BOOT;
    }

    private static ModuleLayer createBootLayer() {
        Module unnamed = Module.unnamed(ClassLoader.getSystemClassLoader(), null, Collections.<String>emptySet());
        return new ModuleLayer(Configuration.empty(),
                Collections.<ModuleLayer>emptyList(),
                Collections.<String, Module>emptyMap(),
                Collections.<String, ClassLoader>emptyMap(),
                unnamed);
    }

    private static ModuleLayer buildLayer(Configuration configuration,
                                          List<ModuleLayer> parents,
                                          Function<String, ClassLoader> loaderFunction) {
        Map<String, Module> modules = new HashMap<String, Module>();
        Map<String, ClassLoader> loaders = new HashMap<String, ClassLoader>();
        Module unnamed = Module.unnamed(ClassLoader.getSystemClassLoader(), null, Collections.<String>emptySet());
        if (configuration != null) {
            for (ResolvedModule resolved : configuration.modules()) {
                ModuleDescriptor descriptor = resolved.descriptor();
                String name = descriptor != null ? descriptor.name() : null;
                if (name != null) {
                    ClassLoader loader = loaderFunction != null ? loaderFunction.apply(name) : null;
                    loaders.put(name, loader);
                    modules.put(name, Module.named(descriptor, loader, null));
                }
            }
        }
        ModuleLayer layer = new ModuleLayer(configuration, parents, modules, loaders, unnamed);
        for (Module module : modules.values()) {
            if (module.getLayer() == null) {
                modules.put(module.getName(),
                        new Module(module.getName(), module.getClassLoader(), module.getDescriptor(),
                                layer, module.getPackages()));
            }
        }
        return layer;
    }

    public static final class Controller {
        private final ModuleLayer layer;

        Controller(ModuleLayer layer) {
            this.layer = layer;
        }

        public ModuleLayer layer() {
            return layer;
        }

        public Controller addReads(Module source, Module target) {
            if (source != null) {
                source.addReads(target);
            }
            return this;
        }

        public Controller addExports(Module source, String pkg, Module target) {
            if (source != null) {
                source.addExports(pkg, target);
            }
            return this;
        }

        public Controller addOpens(Module source, String pkg, Module target) {
            if (source != null) {
                source.addOpens(pkg, target);
            }
            return this;
        }
    }
}
