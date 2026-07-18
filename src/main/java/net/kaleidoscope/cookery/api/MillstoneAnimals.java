package net.kaleidoscope.cookery.api;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
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
public final class MillstoneAnimals {

    // 拉磨档案
    // secondsPerRevolution 拉一圈秒数 allowed 是否允许拉磨 forceLeash 原版不能被拴时是否强制拴绳
    // interactionDisabled 拉磨时是否禁用对该生物的右键 orbitRadius 绕磨半径 即起始与行走位置离磨心的距离
    // 三参构造保留向后兼容 默认禁用右键 半径 2.5 外部 Provider 可用全参构造覆盖更多设置
    public record Profile(double secondsPerRevolution, boolean allowed, boolean forceLeash,
                          boolean interactionDisabled, double orbitRadius) {
        public Profile(double secondsPerRevolution, boolean allowed, boolean forceLeash) {
            this(secondsPerRevolution, allowed, forceLeash, true, DEFAULT_ORBIT_RADIUS);
        }
    }

    // 外部插件扩展接口 返回 null 表示不接管该生物 交回原版表判断
    public interface Provider {
        Profile resolve(Entity entity);
    }

    public static final LoadingStage MILLSTONE_ANIMALS = new LoadingStage("millstone animals");

    // 玩家与村民同速 被打时加速到骡子的速度
    public static final double PLAYER_SECONDS = 7.5;
    public static final double BOOST_SECONDS = 6.0;
    public static final double DEFAULT_ORBIT_RADIUS = 2.5;

    private static final MillstoneAnimals INSTANCE = new MillstoneAnimals();

    private final Map<EntityType, Profile> vanilla = new EnumMap<>(EntityType.class);
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

    // 留给外部插件的白名单接口 注册后即可让其自定义生物参与拉磨
    public void addProvider(Provider provider) {
        providers.add(provider);
    }

    // 先问外部 provider 再查原版表 都没有返回 null 表示该生物不能拉磨
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
    public Profile profileForType(EntityType type) {
        return vanilla.get(type);
    }

    public boolean canPull(Entity entity) {
        Profile profile = resolve(entity);
        return profile != null && profile.allowed();
    }

    // 秒每圈 换算成每 tick 角度 一圈 360 度 一秒 20 tick
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
