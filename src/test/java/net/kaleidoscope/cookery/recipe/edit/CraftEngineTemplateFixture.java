package net.kaleidoscope.cookery.recipe.edit;

/** CE's real template implementation only needs Config's async-load flag for its parser constructor. */
final class CraftEngineTemplateFixture {
    private CraftEngineTemplateFixture() {}

    static synchronized void initialize() throws Exception {
        Class<?> configClass = Class.forName("net.momirealms.craftengine.core.plugin.config.Config");
        var instance = configClass.getDeclaredField("instance");
        instance.setAccessible(true);
        if (instance.get(null) != null) return;
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object configuration = unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, configClass);
        instance.set(null, configuration);
    }
}
