package net.kaleidoscope.cookery.api;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.util.ConsoleMessages;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

// 拉磨生物注册表
// 维护每种生物的拉磨档案 并留出 Provider 白名单接口给外部插件 比如接入 MythicMobs 的自定义生物
/**
 * Runtime registry for entities that can pull a millstone.
 *
 * <p>Plugins can register vanilla entity types directly or add a provider for
 * custom entities from plugins such as MythicMobs.</p>
 */
@SuppressWarnings("unused")
public final class MillstoneAnimals {

    // 拉磨档案
    // secondsPerRevolution 拉一圈秒数 allowed 是否允许拉磨 forceLeash 原版不能被拴时是否强制拴绳
    // interactionDisabled 拉磨时是否禁用对该生物的右键 orbitRadius 绕磨半径 即起始与行走位置离磨心的距离
    // 三参构造保留向后兼容 默认禁用右键 半径 2.5 外部 Provider 可用全参构造覆盖更多设置
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
        /**
         * Creates a profile with default interaction and orbit settings.
         *
         * @param secondsPerRevolution seconds required for one full revolution
         * @param allowed whether the entity can pull a millstone
         * @param forceLeash whether the plugin should force a leash
         */
        public Profile(double secondsPerRevolution, boolean allowed, boolean forceLeash) {
            this(secondsPerRevolution, allowed, forceLeash, true, DEFAULT_ORBIT_RADIUS);
        }
    }

    // 外部插件扩展接口 返回 null 表示不接管该生物 交回原版表判断
    /**
     * Extension hook for plugins that need dynamic entity profile resolution.
     */
    public interface Provider {
        /**
         * Resolves a profile for an entity.
         *
         * @param entity the entity being tested
         * @return a profile to override normal lookup, or {@code null} to fall back
         */
        Profile resolve(Entity entity);
    }

    /**
     * CraftEngine loading stage used by the millstone animal parser.
     */
    public static final LoadingStage MILLSTONE_ANIMALS = new LoadingStage("millstone animals");

    // 玩家与村民同速 被打时加速到骡子的速度
    /**
     * Default seconds per revolution for player-pulled millstones.
     */
    public static final double PLAYER_SECONDS = 7.5;

    /**
     * Seconds per revolution used by the temporary boost effect.
     */
    public static final double BOOST_SECONDS = 6.0;

    /**
     * Default distance from the millstone center while walking.
     */
    public static final double DEFAULT_ORBIT_RADIUS = 2.5;

    private static final MillstoneAnimals INSTANCE = new MillstoneAnimals();

    private final Map<EntityType, Profile> vanilla = new EnumMap<>(EntityType.class);
    private final List<Provider> providers = new CopyOnWriteArrayList<>();

    private MillstoneAnimals() {
        loadDefaults();
    }

    /**
     * Returns the shared registry.
     *
     * @return the singleton registry
     */
    public static MillstoneAnimals instance() {
        return INSTANCE;
    }

    /**
     * Registers or replaces the profile for a Bukkit entity type.
     *
     * @param type entity type
     * @param profile pull profile
     */
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

    // 留给外部插件的白名单接口 注册后即可让其自定义生物参与拉磨
    /**
     * Adds a dynamic profile provider.
     *
     * @param provider provider to query before the built-in type table
     */
    public void addProvider(Provider provider) {
        providers.add(provider);
    }

    // 先问外部 provider 再查原版表 都没有返回 null 表示该生物不能拉磨
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

    // 按生物类型取档案 刷怪蛋等还没有实体时用 不走外部 Provider
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

    /**
     * Checks whether an entity can pull a millstone.
     *
     * @param entity entity to test
     * @return {@code true} if the resolved profile allows pulling
     */
    public boolean canPull(Entity entity) {
        Profile profile = resolve(entity);
        return profile != null && profile.allowed();
    }

    // 秒每圈 换算成每 tick 角度 一圈 360 度 一秒 20 tick
    /**
     * Converts seconds per revolution to degrees per tick.
     *
     * @param secondsPerRevolution seconds required for one full revolution
     * @return degrees advanced per server tick
     */
    public static float anglePerTick(double secondsPerRevolution) {
        return (float) (360.0 / (secondsPerRevolution * 20.0));
    }

    /**
     * Restores the built-in vanilla entity profiles.
     */
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

    /**
     * Registers the CraftEngine config parser for millstone animal profiles.
     */
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
            return super.dependencies();
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
                            ConsoleMessages.t("millstone.unknown_entity_type", typeName));
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
