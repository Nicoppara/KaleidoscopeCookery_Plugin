package net.kaleidoscope.cookery.api;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime registry for entities that can pull a millstone.
 *
 * <p>Plugins can register vanilla entity types directly or add a provider for
 * custom entities from plugins such as MythicMobs.</p>
 */
@SuppressWarnings("unused")
public final class MillstoneAnimals {

    /**
     * Millstone pull profile for an entity.
     *
     * @param secondsPerRevolution seconds required for one full revolution
     * @param allowed whether the entity can pull a millstone
     * @param forceLeash whether the plugin should force a leash for normally unsupported entities
     * @param interactionDisabled whether right-click interaction is disabled while pulling
     * @param orbitRadius distance from the millstone center while walking
     */
    public record Profile(double secondsPerRevolution, boolean allowed, boolean forceLeash,
                          boolean interactionDisabled, double orbitRadius) {
        public Profile(double secondsPerRevolution, boolean allowed, boolean forceLeash) {
            this(secondsPerRevolution, allowed, forceLeash, true, DEFAULT_ORBIT_RADIUS);
        }
    }

    /**
     * Extension hook for plugins that need dynamic entity profile resolution.
     */
    public interface Provider {
        Profile resolve(Entity entity);
    }

    public static final LoadingStage MILLSTONE_ANIMALS = new LoadingStage("millstone animals");

    /**
     * Default seconds per revolution for player-pulled millstones.
     */
    public static final double PLAYER_SECONDS = 7.5;
    public static final double BOOST_SECONDS = 6.0;
    public static final double DEFAULT_ORBIT_RADIUS = 2.5;

    private static final MillstoneAnimals INSTANCE = new MillstoneAnimals();

    private final Map<EntityType, Profile> vanilla = new ConcurrentHashMap<>();
    private final List<Provider> providers = new CopyOnWriteArrayList<>();

    private MillstoneAnimals() {
        loadDefaults();
    }

    public static MillstoneAnimals instance() {
        return INSTANCE;
    }

    public void register(EntityType type, Profile profile) {
        vanilla.put(type, profile);
    }

    /**
     * Registers a simple pull profile with default interaction and orbit settings.
     *
     * @param type entity type
     * @param secondsPerRevolution seconds required for one full revolution
     * @param allowed whether this entity can pull
     * @param forceLeash whether the plugin should force a leash
     */
    public void register(EntityType type, double secondsPerRevolution, boolean allowed, boolean forceLeash) {
        register(type, new Profile(secondsPerRevolution, allowed, forceLeash));
    }

    /**
     * Registers a complete pull profile.
     *
     * @param type entity type
     * @param secondsPerRevolution seconds required for one full revolution
     * @param allowed whether this entity can pull
     * @param forceLeash whether the plugin should force a leash
     * @param interactionDisabled whether right-click interaction is disabled while pulling
     * @param orbitRadius distance from the millstone center while walking
     */
    public void register(EntityType type, double secondsPerRevolution, boolean allowed,
                         boolean forceLeash, boolean interactionDisabled, double orbitRadius) {
        register(type, new Profile(secondsPerRevolution, allowed, forceLeash, interactionDisabled, orbitRadius));
    }

    /**
     * Adds a dynamic profile provider.
     *
     * @param provider provider to query before the built-in type table
     */
    public void addProvider(Provider provider) {
        providers.add(provider);
    }
    /**
     * Resolves the pull profile for an entity.
     *
     * @param entity entity to test
     * @return the resolved profile, or {@code null} if the entity cannot pull
     */
    public Profile resolve(Entity entity) {
        for (Provider provider : providers) {
            Profile profile = provider.resolve(entity);
            if (profile != null) {
                return profile;
            }
        }
        return vanilla.get(entity.getType());
    }

    /**
     * Returns the registered profile for a Bukkit entity type.
     *
     * <p>This does not query custom providers because no entity instance is
     * available.</p>
     *
     * @param type entity type
     * @return the registered profile, or {@code null}
     */
    public Profile profileForType(EntityType type) {
        return vanilla.get(type);
    }

    public boolean canPull(Entity entity) {
        if (!isAdult(entity)) {
            return false;
        }
        Profile profile = resolve(entity);
        return profile != null && profile.allowed();
    }

    public static boolean isAdult(Entity entity) {
        return !(entity instanceof Ageable ageable) || ageable.isAdult();
    }

    /**
     * Converts seconds per revolution to degrees per tick.
     *
     * @param secondsPerRevolution seconds required for one full revolution
     * @return degrees advanced per server tick
     */
    public static float anglePerTick(double secondsPerRevolution) {
        return (float) (360.0 / (secondsPerRevolution * 20.0));
    }

    public void loadDefaults() {
        vanilla.clear();
        register(EntityType.MULE, new Profile(6, true, false));
        register(EntityType.VILLAGER, new Profile(7.5, true, true));
        register(EntityType.DONKEY, new Profile(10, true, false));
        register(EntityType.HORSE, new Profile(25, true, false));
        register(EntityType.SKELETON_HORSE, new Profile(25, true, false));
        register(EntityType.LLAMA, new Profile(30, true, false));
        register(EntityType.TRADER_LLAMA, new Profile(30, true, false));
        register(EntityType.COW, new Profile(40, true, false));
        register(EntityType.MOOSHROOM, new Profile(40, true, false));
        register(EntityType.SHEEP, new Profile(50, true, false));
        register(EntityType.GOAT, new Profile(50, true, false));
    }

    public static void registerParser() {
        CraftEngine.instance().packManager().registerConfigSectionParser(new AnimalsParser());
    }

    private static final class AnimalsParser extends SectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"millstone_animals", "millstone-animals"};
        }

        @Override
        public LoadingStage loadingStage() {
            return MILLSTONE_ANIMALS;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of();
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            INSTANCE.loadDefaults();
        }

        @Override
        protected void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String typeName : section.keySet()) {
                ConfigSection sub = section.getSection(typeName);
                if (sub == null) {
                    continue;
                }
                EntityType type;
                try {
                    type = EntityType.valueOf(typeName.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            "[millstone] 未知生物类型 " + typeName + " 已跳过");
                    continue;
                }
                Profile def = INSTANCE.vanilla.getOrDefault(type, new Profile(PLAYER_SECONDS, true, false));
                double seconds = sub.getDouble("seconds", def.secondsPerRevolution());
                boolean allowed = sub.getBoolean("allowed", def.allowed());
                boolean forceLeash = sub.getBoolean("force_leash", def.forceLeash());
                boolean interactionDisabled = sub.getBoolean("interaction_disabled", def.interactionDisabled());
                double orbitRadius = sub.getDouble("orbit_radius", def.orbitRadius());
                INSTANCE.register(type, new Profile(seconds, allowed, forceLeash, interactionDisabled, orbitRadius));
                count++;
            }
        }
    }
}
