package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.TeapotBehavior;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeapotLiquid;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.momirealms.craftengine.core.plugin.config.Config;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class TeapotController extends BlockEntityController {
    public static final int PUT_INGREDIENT = 0;
    public static final int PROCESSING = 1;
    public static final int FINISHED = 2;

    private static final String DATA_KEY = "kaleidoscopecookery:teapot";
    private static final int INGREDIENT_TIME = 200;
    private static final int CHECK_INTERVAL = 23;
    private static final int FINISH_INTERVAL = 11;
    private static final int ANIM_INTERVAL = 3;
    private static final float LID_RISE = 0.5f;
    private static final float BODY_RISE = 0.25f;
    private static final Key CRACKLE = Key.of("minecraft:block.fire.extinguish");
    private static final Key BUCKET_EMPTY = Key.of("minecraft:item.bucket.empty");
    private static final Key BUCKET_EMPTY_LAVA = Key.of("minecraft:item.bucket.empty_lava");
    private static final int TEAPOT_SOUND_COUNT = 5;

    private final TeapotBehavior behavior;
    private final TeapotElement element;

    private int status = PUT_INGREDIENT;
    private Key fluid;
    private Item input = Item.empty();
    private Item result = Item.empty();
    private int servings;
    private int currentTick = -1;

    private boolean boilFlip;
    private int animTick;
    private boolean textShown;
    private boolean creativeBreak;
    private boolean pickedUp;

    public void markCreativeBreak() {
        this.creativeBreak = true;
    }

    public TeapotController(BlockEntity blockEntity, TeapotBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        this.element = new TeapotElement(this);
    }

    public BlockPos getPos() {
        return blockEntity.pos;
    }

    public CEWorld getWorld() {
        return blockEntity.world;
    }

    public float facingYaw() {
        Direction d = blockEntity.blockState.get(behavior.getFacingProperty());
        return switch (d) {
            case SOUTH -> 0f;
            case EAST -> 90f;
            case WEST -> -90f;
            default -> 180f;
        };
    }

    public int getStatus() {
        return status;
    }

    // 满液体桶倒入
    public boolean addFluidBucket(Player player, InteractionHand hand, Item bucket, Key fluidType) {
        if (status != PUT_INGREDIENT || fluid != null) {
            return false;
        }
        fluid = fluidType;
        playSound(fluidType.equals(ItemKeys.LAVA) ? BUCKET_EMPTY_LAVA : BUCKET_EMPTY, 1.0f);
        if (!player.canInstabuild()) {
            InventoryUtils.shrinkHeld(player, bucket, 1);
            InventoryUtils.giveOrHold(player, hand, InventoryUtils.createOrEmpty(ItemKeys.BUCKET));
        }
        markChanged();
        refreshDisplay();
        sendBar(player);
        return true;
    }

    // 空桶取出液体
    public boolean drainToBucket(Player player, InteractionHand hand, Item emptyBucket) {
        if (status != PUT_INGREDIENT || fluid == null || !input.isEmpty()) {
            return false;
        }
        Key back;
        if (fluid.equals(ItemKeys.WATER)) {
            back = ItemKeys.WATER_BUCKET;
        } else if (fluid.equals(ItemKeys.LAVA)) {
            back = ItemKeys.LAVA_BUCKET;
        } else {
            return false;
        }
        fluid = null;
        currentTick = -1;
        if (!player.canInstabuild()) {
            InventoryUtils.shrinkHeld(player, emptyBucket, 1);
            InventoryUtils.giveOrHold(player, hand, InventoryUtils.createOrEmpty(back));
        }
        markChanged();
        refreshDisplay();
        sendBar(player);
        return true;
    }

    public boolean addIngredient(Player player, Item held) {
        if (status != PUT_INGREDIENT || fluid == null || !input.isEmpty()) {
            return false;
        }
        TeapotRecipe recipe = FoodRecipeRegistry.instance().findTeapot(fluid, held.id());
        if (recipe == null) {
            return false;
        }
        // 模糊配方 放多少算多少 上限为配方要求量 产量随后按比例
        int added = Math.min(held.count(), Math.max(1, recipe.ingredientCount()));
        input = held.copyWithCount(added);
        currentTick = INGREDIENT_TIME;
        InventoryUtils.shrinkHeld(player, held, added);
        markChanged();
        refreshDisplay();
        return true;
    }

    public boolean removeIngredient(Player player, InteractionHand hand) {
        if (status != PUT_INGREDIENT || input.isEmpty()) {
            return false;
        }
        InventoryUtils.giveOrHold(player, hand, input.copy());
        input = Item.empty();
        currentTick = -1;
        markChanged();
        refreshDisplay();
        return true;
    }

    // 空手取走整壶
    public boolean takeTeapot(Player player, InteractionHand hand) {
        if (status == PROCESSING) {
            return false;
        }
        Item teapot = buildDroppedTeapot();
        if (ItemUtils.isEmpty(teapot)) {
            return false;
        }
        if (!input.isEmpty()) {
            DropUtils.dropAtCenter(blockEntity, input);
            input = Item.empty();
        }
        pickedUp = true;
        InventoryUtils.giveOrHold(player, hand, teapot);
        removeBlock();
        return true;
    }

    private void removeBlock() {
        World world = (World) blockEntity.world.world().platformWorld();
        Block block = world.getBlockAt(blockEntity.pos.x(), blockEntity.pos.y(), blockEntity.pos.z());
        CraftEngineBlocks.remove(block);
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper((w, pos, state, controller) -> tick());
    }

    private void tick() {
        long gameTime = ((World) blockEntity.world.world().platformWorld()).getGameTime();
        boolean heated = heated();

        if (heated && fluid != null && gameTime % behavior.particleInterval == 0) {
            emitSteam(behavior.particleCount);
        }

        if (status == PUT_INGREDIENT || status == PROCESSING) {
            if (Math.floorMod(gameTime + posStagger(), CHECK_INTERVAL) != 0) {
                return;
            }
            if (fluid == null || !heated) {
                return;
            }
            onProcessingEffects();
            if (status == PUT_INGREDIENT) {
                tickPutIngredient();
            } else {
                tickProcessing();
            }
            return;
        }

        if (status == FINISHED) {
            driveBoiling(heated);
            if (heated && Math.floorMod(gameTime + posStagger(), FINISH_INTERVAL) == 0) {
                onBoilingEffects();
            }
        }
    }

    private void tickPutIngredient() {
        if (input.isEmpty()) {
            return;
        }
        if (currentTick > 0) {
            currentTick = Math.max(-1, currentTick - CHECK_INTERVAL);
            markChanged();
            return;
        }
        TeapotRecipe recipe = FoodRecipeRegistry.instance().findTeapot(fluid, input.id());
        if (recipe != null) {
            result = makeResult(recipe);
            servings = servingsFor(input.count(), recipe.ingredientCount());
            currentTick = recipe.time();
            status = PROCESSING;
            markChanged();
            refreshDisplay();
            return;
        }
        DropUtils.dropAtCenter(blockEntity, input);
        input = Item.empty();
        result = Item.empty();
        currentTick = -1;
        markChanged();
    }

    private void tickProcessing() {
        if (currentTick > 0) {
            currentTick = Math.max(-1, currentTick - CHECK_INTERVAL);
            markChanged();
            return;
        }
        status = FINISHED;
        currentTick = -1;
        input = Item.empty();
        markChanged();
        refreshDisplay();
    }

    // 煮开抖动
    private void driveBoiling(boolean heated) {
        if (!heated) {
            if (animTick != 0) {
                animTick = 0;
                broadcast(element.lidBounceMeta(0f, ANIM_INTERVAL));
                broadcast(element.bodyBounceMeta(0f, ANIM_INTERVAL));
            }
            return;
        }
        if (++animTick % ANIM_INTERVAL != 0) {
            return;
        }
        boilFlip = !boilFlip;
        broadcast(element.lidBounceMeta(boilFlip ? LID_RISE : 0f, ANIM_INTERVAL));
        broadcast(element.bodyBounceMeta(boilFlip ? 0f : BODY_RISE, ANIM_INTERVAL));
    }

    private void broadcast(Object meta) {
        for (Player p : TrackedPlayers.snapshotInRange(blockEntity, behavior.animChunkRadius)) {
            p.sendPacket(meta, false);
        }
    }

    private void broadcastAll(Object packet) {
        TrackedPlayers.forEach(blockEntity, p -> p.sendPacket(packet, false));
    }

    private Item makeResult(TeapotRecipe recipe) {
        Item item = BukkitItemManager.instance().createWrappedItem(recipe.result(), null);
        if (item == null) {
            return Item.empty();
        }
        return item.copyWithCount(Math.max(1, recipe.resultCount()));
    }

    // 模糊配方产量 加入量/要求量 * 满格 整数除法向下取整 有加就最少 1 格
    private static int servingsFor(int added, int required) {
        if (required <= 0) {
            return TeapotBar.CELLS;
        }
        return Math.max(1, TeapotBar.CELLS * added / required);
    }

    private boolean heated() {
        Object level = blockEntity.world.world().minecraftWorld();
        Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(blockEntity.pos));
        return HeatSourceUtils.isHeatSource(level, belowPos);
    }

    private int posStagger() {
        return blockEntity.pos.x() * 31 + blockEntity.pos.z();
    }

    private void markChanged() {
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }


    private void onProcessingEffects() {
        playSound(Key.of("kaleidoscopecookery:teapot_" + ThreadLocalRandom.current().nextInt(TEAPOT_SOUND_COUNT)), 0.6f);
    }

    private void onBoilingEffects() {
        playSound(CRACKLE, 0.4f);
    }

    private void playSound(Key sound, float volume) {
        float pitch = 0.8f + ThreadLocalRandom.current().nextFloat() * 0.2f;
        blockEntity.world.world().playSound(Vec3d.atCenterOf(blockEntity.pos), sound, volume, pitch, SoundSource.BLOCK);
    }

    private void emitSteam(int count) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double x = blockEntity.pos.x() + 0.5 + (r.nextDouble() - 0.5) * 0.4;
        double y = blockEntity.pos.y() + 0.9 + r.nextDouble() / 3;
        double z = blockEntity.pos.z() + 0.5 + (r.nextDouble() - 0.5) * 0.4;
        Particles.emit(blockEntity.world, Particle.CLOUD, x, y, z, count, 0.05, 0.05, 0.05, 0.02, null);
    }

    private void sendBar(Player player) {
        player.sendActionBar(AdventureHelper.miniMessage().deserialize(buildBar()));
    }

    private String buildBar() {
        if (status == FINISHED && !result.isEmpty()) {
            return TeapotBar.build(fluid, servings);
        }
        return TeapotBar.build(fluid);
    }

    private String statusMsg() {
        return switch (status) {
            case PROCESSING -> behavior.msgProcessing;
            case FINISHED -> behavior.msgFinished;
            default -> behavior.msgPut;
        };
    }

    private Component statusComponent() {
        Component head = AdventureHelper.miniMessage().deserialize(statusMsg());
        if (status == FINISHED) {
            return head.append(Component.newline()).append(itemComponent(result, servings));
        }
        Component line = fluidComponent();
        if (!input.isEmpty()) {
            line = line.append(Component.text(" ")).append(itemComponent(input, input.count()));
        }
        return head.append(Component.newline()).append(line);
    }

    private Component fluidComponent() {
        if (fluid == null) {
            return Component.text("空");
        }
        TeapotLiquid liquid = FoodRecipeRegistry.instance().getTeapotLiquid(fluid);
        if (liquid != null && liquid.displayName() != null && !liquid.displayName().isEmpty()) {
            return Component.text(liquid.displayName());
        }
        return Component.text(fluid.value());
    }

    private Component itemComponent(Item item, int count) {
        if (item.isEmpty()) {
            return Component.text("无");
        }
        Component name = item.hoverNameComponent().orElse(Component.text(item.id().value()));
        return Component.empty().append(name).append(Component.text(" x" + count));
    }

    private void initDisplay() {
        boolean visible = fluid != null || status != PUT_INGREDIENT;
        element.setText(visible ? statusComponent() : Component.empty(), visible);
        textShown = visible;
    }

    private void refreshDisplay() {
        boolean visible = fluid != null || status != PUT_INGREDIENT;
        Object data = element.setText(visible ? statusComponent() : Component.empty(), visible);
        if (blockEntity.world == null) {
            textShown = visible;
            return;
        }
        if (visible) {
            if (!textShown) {
                broadcastAll(element.textSpawnPacket());
                textShown = true;
            }
            broadcastAll(data);
        } else if (textShown) {
            broadcastAll(element.textRemovePacket());
            textShown = false;
        }
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(element);
    }

    @Override
    public void onRemove() {
        if (pickedUp) {
            return;
        }
        if (!input.isEmpty()) {
            DropUtils.dropAtCenter(blockEntity, input);
        }
        if (creativeBreak) {
            return;
        }
        Item teapot = buildDroppedTeapot();
        if (!ItemUtils.isEmpty(teapot)) {
            DropUtils.dropAtCenter(blockEntity, teapot);
        }
    }

    private Item buildDroppedTeapot() {
        Key blockId = blockEntity.blockState.owner().value().id();
        Item teapot = InventoryUtils.createOrEmpty(blockId);
        if (ItemUtils.isEmpty(teapot)) {
            return teapot;
        }
        CompoundTag data = new CompoundTag();
        if (status != PROCESSING && fluid != null) {
            data.putString("fluid", fluid.asString());
        }
        String barStr;
        if (status == FINISHED && !result.isEmpty()) {
            data.putInt("status", FINISHED);
            data.put("result", ItemStackUtils.saveMinecraftItemStackAsTag(result.minecraftItem()));
            data.putInt("servings", servings);
            barStr = TeapotBar.build(fluid, servings);
        } else if (status != PROCESSING && fluid != null) {
            barStr = TeapotBar.build(fluid);
        } else {
            barStr = TeapotBar.build(null);
        }
        teapot.setSparrowTag(data, TeapotBar.ITEM_DATA_KEY);
        teapot.loreJson(List.of(AdventureHelper.componentToJson(
                AdventureHelper.miniMessage().deserialize("<!i>" + barStr))));
        return teapot;
    }

    @Override
    public void loadCustomDataFromItem(Item item) {
        Tag tag = item.getSparrowTag(TeapotBar.ITEM_DATA_KEY);
        if (tag instanceof CompoundTag data) {
            String fluidStr = data.getString("fluid");
            fluid = (fluidStr == null || fluidStr.isEmpty()) ? null : Key.of(fluidStr);
            status = data.getInt("status", PUT_INGREDIENT);
            servings = data.getInt("servings", 0);
            Tag resultTag = data.get("result");
            if (resultTag != null) {
                Object nms = ItemStackUtils.parseMinecraftItem(resultTag, Config.itemDataFixerUpperFallbackVersion());
                result = nms == null ? Item.empty() : ItemStackUtils.wrap(nms);
            }
        }
        initDisplay();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt("status", status);
        data.putInt("current_tick", currentTick);
        if (fluid != null) {
            data.putString("fluid", fluid.asString());
        }
        if (!input.isEmpty()) {
            data.put("input", ItemStackUtils.saveMinecraftItemStackAsTag(input.minecraftItem()));
        }
        if (!result.isEmpty()) {
            data.put("result", ItemStackUtils.saveMinecraftItemStackAsTag(result.minecraftItem()));
        }
        data.putInt("servings", servings);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) {
            return;
        }
        status = data.getInt("status", PUT_INGREDIENT);
        currentTick = data.getInt("current_tick", -1);
        String fluidStr = data.getString("fluid");
        fluid = (fluidStr == null || fluidStr.isEmpty()) ? null : Key.of(fluidStr);
        servings = data.getInt("servings", 0);
        int version = Config.itemDataFixerUpperFallbackVersion();
        input = loadItem(data, "input", version);
        result = loadItem(data, "result", version);
        initDisplay();
    }

    private Item loadItem(CompoundTag data, String key, int version) {
        Tag tag = data.get(key);
        if (tag == null) {
            return Item.empty();
        }
        Object nms = ItemStackUtils.parseMinecraftItem(tag, version);
        return nms == null ? Item.empty() : ItemStackUtils.wrap(nms);
    }
}
