package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.ItemMatcher;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.loot.LootTable;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// 右键收割的公共规则 成熟判定 黑名单 掉落 音效 重置年龄
// 多段作物的段间处理由各行为自己接 这里只管单点的那一份
public final class CropHarvestRules {
    private static final String[] RESET_AGE = {"reset_age", "reset-age", "harvest_reset_age", "harvest-reset-age"};
    private static final String[] BLACKLIST = {"blacklist", "black_list", "black-list", "harvest_blacklist", "harvest-blacklist"};
    private static final String[] SOUND = {"sound", "harvest_sound", "harvest-sound"};

    private static final String DEFAULT_SOUND = "minecraft:block.crop.break";
    // 镰刀有自己的范围收割 再走右键收割会掉两份
    private static final List<String> DEFAULT_BLACKLIST = List.of("#kaleidoscopecookery:range_harvest_tool");

    private final IntegerProperty ageProperty;
    private final int resetAge;
    private final ItemMatcher blacklist;
    private final Key sound;
    private final LootTable loot;

    private CropHarvestRules(IntegerProperty ageProperty, int resetAge, ItemMatcher blacklist, Key sound, LootTable loot) {
        this.ageProperty = ageProperty;
        this.resetAge = Math.max(ageProperty.min, Math.min(resetAge, ageProperty.max));
        this.blacklist = blacklist;
        this.sound = sound;
        this.loot = loot;
    }

    public static CropHarvestRules fromConfig(BlockDefinition block, ConfigSection section, int defaultResetAge) {
        ConfigSection lootSection = section.getSection("loot");
        String soundId = BehaviorConfig.getString(section, DEFAULT_SOUND, SOUND);
        return new CropHarvestRules(
                (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class),
                BehaviorConfig.getInt(section, defaultResetAge, RESET_AGE),
                ItemMatcher.fromConfig(section, DEFAULT_BLACKLIST, BLACKLIST),
                soundId == null || soundId.isEmpty() ? null : Key.of(soundId),
                lootSection == null ? null : LootTable.fromConfig(lootSection));
    }

    public IntegerProperty ageProperty() {
        return this.ageProperty;
    }

    public int resetAge() {
        return this.resetAge;
    }

    public boolean isMature(ImmutableBlockState state) {
        return state.get(this.ageProperty) >= this.ageProperty.max;
    }

    // 手里拿着黑名单里的东西就别按右键收割走
    public boolean isBlocked(@Nullable Item item) {
        return !ItemUtils.isEmpty(item) && this.blacklist.matches(item);
    }

    // 创造模式不掉落 与全局约定一致
    public void dropLoot(World world, WorldPosition position, ImmutableBlockState state, Player player) {
        if (player.canInstabuild()) {
            return;
        }
        Item itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        ContextHolder.Builder builder = ContextHolder.builder()
                .withParameter(DirectContextParameters.POSITION, position)
                .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, state)
                .withOptionalParameter(DirectContextParameters.PLAYER, player)
                .withOptionalParameter(DirectContextParameters.ITEM_IN_HAND,
                        ItemUtils.isEmpty(itemInHand) ? null : itemInHand);
        DropUtils.dropAll(world, position, this.loot == null
                ? state.getDrops(builder, world, player)
                : this.loot.getRandomItems(builder.build(), world, player));
    }

    // 收割是直接改方块状态 不发事件的话 CoreProtect 之类一条记录都拿不到
    public void logBreak(World world, BlockPos pos, Player player) {
        if (!(world.platformWorld() instanceof org.bukkit.World bukkitWorld)
                || !(player.platformPlayer() instanceof org.bukkit.entity.Player bukkitPlayer)) {
            return;
        }
        EventUtils.logBlockBreak(bukkitWorld.getBlockAt(pos.x(), pos.y(), pos.z()), bukkitPlayer);
    }

    public void playSound(World world, WorldPosition position) {
        if (this.sound != null) {
            world.playBlockSound(position, this.sound, 1.0f, 1.0f);
        }
    }
}
