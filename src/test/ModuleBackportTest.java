package test;

import j9compat.Module;
import j9compat.ModuleBackport;
import j9compat.ModuleDescriptor;
import j9compat.ModuleLayer;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static test.BackportTestRunner.*;

/**
 * Tests for module backports.
 */
public final class ModuleBackportTest {

    static void run() {
        section("ModuleBackport");

        Module module = ModuleBackport.getModule(ModuleBackportTest.class);
        assertTrue(module != null, "ModuleBackport.getModule: returns module");
        assertTrue(!module.isNamed(), "ModuleBackport.getModule: unnamed module");
        assertEquals(ModuleLayer.boot().unnamedModule(), module,
                "ModuleBackport.getModule: boot unnamed module");

        ModuleDescriptor descriptor = ModuleDescriptor.newModule("demo")
                .requires("java.base")
                .exports("demo.pkg")
                .build();
        assertEquals("demo", descriptor.name(), "ModuleDescriptor builder: name set");
        assertTrue(descriptor.requires().size() == 1,
                "ModuleDescriptor builder: requires populated");

        testAccessChecks();
    }

    private static void testAccessChecks() {
        ModuleDescriptor alphaDescriptor = ModuleDescriptor.newModule("alpha")
                .exports("alpha.pkg", Collections.singleton("beta"))
                .uses(DemoService.class.getName())
                .build();
        ModuleDescriptor betaDescriptor = ModuleDescriptor.newModule("beta")
                .requires("alpha")
                .build();
        ModuleDescriptor gammaDescriptor = ModuleDescriptor.newModule("gamma").build();

        Module alpha = createModule(alphaDescriptor, new HashSet<String>(Collections.singleton("alpha.pkg")));
        Module beta = createModule(betaDescriptor, new HashSet<String>(Collections.singleton("beta.pkg")));
        Module gamma = createModule(gammaDescriptor, new HashSet<String>(Collections.singleton("gamma.pkg")));

        assertTrue(beta.canRead(alpha), "Module.canRead: requires establishes readability");
        assertTrue(alpha.isExported("alpha.pkg", beta),
                "Module.isExported: qualified export allows target module");
        assertTrue(!alpha.isExported("alpha.pkg", gamma),
                "Module.isExported: qualified export blocks other modules");

        assertTrue(!gamma.canRead(alpha), "Module.canRead: not readable before addReads");
        gamma.addReads(alpha);
        assertTrue(gamma.canRead(alpha), "Module.addReads: grants readability");

        assertTrue(alpha.canUse(DemoService.class),
                "Module.canUse: registered uses are allowed");
    }

    private static Module createModule(ModuleDescriptor descriptor, Set<String> packages) {
        try {
            Constructor<Module> ctor = Module.class.getDeclaredConstructor(String.class,
                    ClassLoader.class, ModuleDescriptor.class, ModuleLayer.class, Set.class);
            ctor.setAccessible(true);
            return ctor.newInstance(descriptor.name(),
                    ModuleBackportTest.class.getClassLoader(), descriptor, ModuleLayer.boot(), packages);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class DemoService {}
}
