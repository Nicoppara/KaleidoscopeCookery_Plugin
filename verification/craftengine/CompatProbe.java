import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;
import net.kaleidoscope.cookery.block.behavior.SteamerBehavior;
import net.kaleidoscope.cookery.block.entity.SteamerController;
import net.kaleidoscope.cookery.block.entity.EnamelBasinController;
import net.kaleidoscope.cookery.block.entity.OilPotController;
import net.kaleidoscope.cookery.nms.NmsBridgeProvider;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CompatProbe extends JavaPlugin implements Listener {
    private final List<String> checks = new ArrayList<>();
    private boolean cancelFall = true;
    private World world;
    private CEWorld ceWorld;
    private Object level;
    private static final Key STEAMER = Key.of("kaleidoscopecookery:steamer");
    private static final String[] APPLIANCES = {"pot", "stockpot", "teapot", "chopping_board", "enamel_basin", "oil_pot", "steamer"};
    private static final String[] CONTROLLERS = {"PotController", "StockpotController", "TeapotController", "ChoppingBoardController", "EnamelBasinController", "OilPotController", "SteamerController"};
    private final List<BlockEntityController> appliances = new ArrayList<>();

    private String craftEngineVersion() {
        return Bukkit.getPluginManager().getPlugin("CraftEngine").getDescription().getVersion();
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, this::start, 100);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void cancelSourceFall(EntityChangeBlockEvent event) {
        if (cancelFall && event.getEntity() instanceof FallingBlock
                && event.getBlock().getX() == 8 && event.getBlock().getY() == 100
                && event.getBlock().getZ() == 8) {
            event.setCancelled(true);
        }
    }

    private void start() {
        try {
            check(Bukkit.getPluginManager().isPluginEnabled("KaleidoscopeCookeryPlugin"), "plugin enabled");
            String expectedVersion = System.getProperty("cookery.probe.craftengine");
            check(expectedVersion != null && expectedVersion.equals(craftEngineVersion()), "CraftEngine " + expectedVersion);
            check(NmsBridgeProvider.bridge().litBlockStateProperty() != null, "Paper 26.3 NMS bridge");
            Class<?> actionType = Class.forName("net.minecraft.network.protocol.game.ServerboundClientCommandPacket$Action");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object respawnAction = Enum.valueOf((Class) actionType, "PERFORM_RESPAWN");
            Object respawnPacket = Class.forName("net.minecraft.network.protocol.game.ServerboundClientCommandPacket")
                    .getConstructor(actionType).newInstance(respawnAction);
            check(NmsBridgeProvider.bridge().isPerformRespawnPacket(respawnPacket), "respawn packet binding");
            Object spectatePacket = Class.forName("net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket")
                    .getConstructor(java.util.UUID.class).newInstance(java.util.UUID.randomUUID());
            check(NmsBridgeProvider.bridge().isSpectatePacket(spectatePacket), "spectate packet binding");
            check(RecipeMenuStyle.instance().title(MenuScreen.HOME_BROWSE) != null, "shared Adventure API");
            check(BukkitItemManager.instance().loadedItems().keySet().containsAll(
                    List.of("kaleidoscopecookery:apple_platter",
                    "kaleidoscopecookery:baozi_plate",
                    "kaleidoscopecookery:berry_platter",
                    "kaleidoscopecookery:blaze_lamb_chop",
                    "kaleidoscopecookery:braised_fish",
                    "kaleidoscopecookery:braised_pork_ribs",
                    "kaleidoscopecookery:brown_mushroom_pot_soup",
                    "kaleidoscopecookery:buddha_jumps_over_the_wall",
                    "kaleidoscopecookery:candied_potato",
                    "kaleidoscopecookery:chili_ristra",
                    "kaleidoscopecookery:chopping_board",
                    "kaleidoscopecookery:chorus_fried_egg",
                    "kaleidoscopecookery:chorus_fruit_platter",
                    "kaleidoscopecookery:cold_cut_ham_slices",
                    "kaleidoscopecookery:cold_roasted_meat",
                    "kaleidoscopecookery:cold_style_sashimi",
                    "kaleidoscopecookery:crimson_fungus_pot_soup",
                    "kaleidoscopecookery:crystal_lamb_chop",
                    "kaleidoscopecookery:dark_cuisine",
                    "kaleidoscopecookery:desert_style_sashimi",
                    "kaleidoscopecookery:dongpo_pork",
                    "kaleidoscopecookery:dough_drop_soup",
                    "kaleidoscopecookery:empty_cup",
                    "kaleidoscopecookery:enamel_basin",
                    "kaleidoscopecookery:end_style_sashimi",
                    "kaleidoscopecookery:farmer_boots",
                    "kaleidoscopecookery:farmer_chest_plate",
                    "kaleidoscopecookery:farmer_leggings",
                    "kaleidoscopecookery:flour",
                    "kaleidoscopecookery:fondant_pie",
                    "kaleidoscopecookery:fondant_spider_eye",
                    "kaleidoscopecookery:four_joy_meatball_soup",
                    "kaleidoscopecookery:fried_caterpillar",
                    "kaleidoscopecookery:fried_spring_roll",
                    "kaleidoscopecookery:frost_lamb_chop",
                    "kaleidoscopecookery:fruit_basket",
                    "kaleidoscopecookery:golden_salad",
                    "kaleidoscopecookery:green_chili",
                    "kaleidoscopecookery:kitchen_shovel_has_oil",
                    "kaleidoscopecookery:kitchen_shovel_no_oil",
                    "kaleidoscopecookery:kitchenware_racks",
                    "kaleidoscopecookery:mantou",
                    "kaleidoscopecookery:nether_style_sashimi",
                    "kaleidoscopecookery:new_millstone",
                    "kaleidoscopecookery:numbing_spicy_chicken",
                    "kaleidoscopecookery:oil",
                    "kaleidoscopecookery:oil_block",
                    "kaleidoscopecookery:oil_pot",
                    "kaleidoscopecookery:oil_pot_empty",
                    "kaleidoscopecookery:oil_splashed_fish",
                    "kaleidoscopecookery:pan_seared_knight_steak",
                    "kaleidoscopecookery:pot",
                    "kaleidoscopecookery:qingtuan_plate",
                    "kaleidoscopecookery:raw_dough",
                    "kaleidoscopecookery:raw_noodles",
                    "kaleidoscopecookery:raw_zongzi",
                    "kaleidoscopecookery:recipe_item_has_recipe",
                    "kaleidoscopecookery:recipe_item_no_recipe",
                    "kaleidoscopecookery:red_chili",
                    "kaleidoscopecookery:red_mushroom_pot_soup",
                    "kaleidoscopecookery:rice",
                    "kaleidoscopecookery:rice_panicle",
                    "kaleidoscopecookery:sakura_fubuki",
                    "kaleidoscopecookery:scarecrow",
                    "kaleidoscopecookery:shawarma_spit",
                    "kaleidoscopecookery:shengjian_mantou",
                    "kaleidoscopecookery:shengjian_mantou_plate",
                    "kaleidoscopecookery:sickle",
                    "kaleidoscopecookery:slime_ball_meal",
                    "kaleidoscopecookery:spicy_blood_stew",
                    "kaleidoscopecookery:spicy_chicken",
                    "kaleidoscopecookery:spicy_rabbit_head",
                    "kaleidoscopecookery:stargazy_pie",
                    "kaleidoscopecookery:steamer",
                    "kaleidoscopecookery:sticky_candy_plate",
                    "kaleidoscopecookery:sticky_rice_cake_plate",
                    "kaleidoscopecookery:stockpot",
                    "kaleidoscopecookery:stockpot_lid",
                    "kaleidoscopecookery:stove",
                    "kaleidoscopecookery:straw_block",
                    "kaleidoscopecookery:strung_mushrooms",
                    "kaleidoscopecookery:stuffed_dough_food",
                    "kaleidoscopecookery:stuffed_tiger_skin_pepper",
                    "kaleidoscopecookery:suspicious_stir_fry",
                    "kaleidoscopecookery:sweet_and_sour_ender_pearls",
                    "kaleidoscopecookery:teacup_coaster",
                    "kaleidoscopecookery:teapot",
                    "kaleidoscopecookery:tomato_platter",
                    "kaleidoscopecookery:transmutation_lunch_bag",
                    "kaleidoscopecookery:transmutation_lunch_bag_eating",
                    "kaleidoscopecookery:trashcan",
                    "kaleidoscopecookery:trashcan_helmet",
                    "kaleidoscopecookery:tundra_style_sashimi",
                    "kaleidoscopecookery:warped_fungus_pot_soup",
                    "kaleidoscopecookery:watermelon_platter",
                    "kaleidoscopecookery:wild_rice",
                    "kaleidoscopecookery:yakitori",
                    "kaleidoscopecookery:zongzi_plate",
                    "show:chopping_board",
                    "show:fruit_basket",
                    "show:kitchenware_racks",
                    "show:recipe_block",
                    "show:scarecrow_body",
                    "show:scarecrow_headless",
                    "show:scarecrow_lantern",
                    "show:scarecrow_soul_lantern",
                    "show:tea_coaster").stream().map(Key::of).toList()), "107 static pack items loaded");
            getLogger().info("FULL_PACK_ITEM_COUNT: " + BukkitItemManager.instance().loadedItems().size());
            check(CraftEngineBlocks.byId(STEAMER) != null, "custom steamer behavior loaded");
            world = Bukkit.getWorlds().getFirst();
            world.getChunkAt(0, 0).load();
            world.setChunkForceLoaded(0, 0, true);
            world.getEntities().stream().filter(e -> e instanceof FallingBlock).forEach(org.bukkit.entity.Entity::remove);
            for (int y = 91; y <= 102; y++) world.getBlockAt(8, y, 8).setType(Material.AIR, false);
            ceWorld = BukkitWorldManager.instance().getWorld(world.getUID()).storageWorld();
            level = ceWorld.world().minecraftWorld();
            world.getBlockAt(8, 90, 8).setType(Material.MAGMA_BLOCK, false);
            world.getBlockAt(8, 99, 8).setType(Material.CAMPFIRE, false);
            check(HeatSourceUtils.isHeatSource(level, LocationUtils.toBlockPos(8, 99, 8)), "heat source property binding");
            check(CraftEngineBlocks.place(world.getBlockAt(8, 100, 8).getLocation(), STEAMER, false), "steamer placed");
            placeAppliances();
            Bukkit.getScheduler().runTaskLater(this, this::exerciseFall, 10);
        } catch (Throwable error) {
            finish(error);
        }
    }

    private void placeAppliances() {
        for (int i = 0; i < APPLIANCES.length; i++) {
            int x = 20 + i * 2;
            world.getChunkAt(x >> 4, 0).load();
            world.setChunkForceLoaded(x >> 4, 0, true);
            for (int y = 100; y <= 102; y++) world.getBlockAt(x, y, 8).setType(Material.AIR, false);
            world.getBlockAt(x, 99, 8).setType(Material.MAGMA_BLOCK, false);
            check(CraftEngineBlocks.place(world.getBlockAt(x, 100, 8).getLocation(), Key.of("kaleidoscopecookery:" + APPLIANCES[i]), false), APPLIANCES[i] + " placed");
        }
    }

    private void verifyAppliances() throws Exception {
        for (int i = 0; i < APPLIANCES.length; i++) {
            BlockEntity entity = ceWorld.getBlockEntityAtIfLoaded(new BlockPos(20 + i * 2, 100, 8));
            check(entity != null, APPLIANCES[i] + " block entity created");
            BlockEntityController controller = entity.controller.getAt(BlockEntityController.class, 0);
            check(controller != null && controller.getClass().getSimpleName().equals(CONTROLLERS[i]), APPLIANCES[i] + " controller created");
            if (i == 3) {
                CompoundTag data = new CompoundTag();
                data.putInt("data_version", VersionHelper.WORLD_VERSION);
                data.putInt("stage", 1);
                BlockEntityNbt.putItem(data, "item", InventoryUtils.createOrEmpty(Key.of("minecraft:potato")));
                CompoundTag input = new CompoundTag();
                input.put("kaleidoscopecookery:chopping_board", data);
                controller.loadCustomData(input);
            } else if (controller instanceof EnamelBasinController basin) {
                basin.addOil(7);
                basin.setClosed(false);
                check(basin.getOilCount() == 7 && !basin.isClosed(), "basin oil and block state updated");
            } else if (controller instanceof OilPotController oilPot) {
                check(oilPot.addOil(7) == 7 && oilPot.oilCount() == 7, "oil pot amount and block state updated");
            }
            CompoundTag saved = new CompoundTag();
            controller.saveCustomData(saved);
            check(!saved.isEmpty(), APPLIANCES[i] + " data saved");
            controller.loadCustomData(saved);
            CompoundTag restored = new CompoundTag();
            controller.saveCustomData(restored);
            check(saved.equals(restored), APPLIANCES[i] + " NBT round trip");
            appliances.add(controller);
        }
        // No direct ticker invocation: these values must be updated by CE's scheduler.
        check(((SteamerController) appliances.get(6)).getLitLevel() > 0, "scheduled steamer heat tick");
        var teapotHeat = appliances.get(2).getClass().getDeclaredField("heatedCache");
        teapotHeat.setAccessible(true);
        check(teapotHeat.getBoolean(appliances.get(2)), "scheduled teapot heat tick");
    }

    private void exerciseFall() {
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(world.getBlockAt(8, 100, 8));
            SteamerBehavior behavior = state.behavior().getFirst(SteamerBehavior.class);
            SteamerController controller = controllerAt(100, behavior);
            check(controller != null, "steamer controller created");
            Item potato = InventoryUtils.createOrEmpty(Key.of("minecraft:potato"));
            check(!potato.isEmpty(), "vanilla food wrapped");
            CompoundTag stored = new CompoundTag();
            controller.saveCustomData(stored);
            stored.getCompound(SteamerController.DATA_KEY).put("items", BlockEntityNbt.saveItems(new Item[]{potato}, 1));
            controller.loadCustomData(stored);
            check(controller.getItemCount() == 1, "steamer food serialized");
            TransientBlockProbe.verify(this, state, world, this::check);
            world.getBlockAt(8, 99, 8).setType(Material.AIR, false);
            Object[] args = {state.customBlockState().minecraftState(), level, LocationUtils.toBlockPos(8, 100, 8)};
            behavior.tick(null, args);
            check(CraftEngineBlocks.getCustomBlockState(world.getBlockAt(8, 100, 8)) != null, "cancelled fall keeps block");
            check(controller.getItemCount() == 1 && !controller.getItems()[0].isEmpty(), "cancelled fall keeps food");
            check(SteamerBehavior.pendingData.isEmpty(), "cancelled fall leaves no pending entity");
            cancelFall = false;
            behavior.tick(null, args);
            check(CraftEngineBlocks.getCustomBlockState(world.getBlockAt(8, 100, 8)) == null, "successful fall removes source");
            check(SteamerBehavior.pendingData.size() == 1, "successful fall tracks data");
            check(SteamerBehavior.pendingData.values().iterator().next().tag.getCompound(SteamerController.DATA_KEY).getList("items").size() == 1, "falling entity keeps food NBT");
            Bukkit.getScheduler().runTaskLater(this, () -> verifyLanding(behavior), 80);
        } catch (Throwable error) {
            finish(error);
        }
    }

    private SteamerController controllerAt(int y, SteamerBehavior behavior) {
        BlockEntity block = ceWorld.getBlockEntityAtIfLoaded(new BlockPos(8, y, 8));
        return block == null ? null : block.controller.get(SteamerController.class, behavior.getControllerId());
    }

    private void verifyLanding(SteamerBehavior behavior) {
        try {
            getLogger().info("FALL_DIAGNOSTIC: pending=" + SteamerBehavior.pendingData.size()
                    + " entities=" + world.getEntities().stream().filter(e -> e instanceof FallingBlock)
                    .map(e -> e.getLocation().toString()).toList());
            ImmutableBlockState landed = CraftEngineBlocks.getCustomBlockState(world.getBlockAt(8, 91, 8));
            check(landed != null && landed.owner().value().id().equals(STEAMER), "steamer lands on supported block");
            SteamerController restored = controllerAt(91, behavior);
            getLogger().info("RESTORE_DIAGNOSTIC: controller=" + restored
                    + " count=" + (restored == null ? -1 : restored.getItemCount()));
            check(restored != null && restored.getItemCount() == 1 && !restored.getItems()[0].isEmpty(), "landing restores food");
            check(SteamerBehavior.pendingData.isEmpty(), "landing clears pending data");
            verifyAppliances();
            finish(null);
        } catch (Throwable error) {
            finish(error);
        }
    }

    private void check(boolean success, String label) {
        if (!success) throw new AssertionError(label);
        checks.add(label);
        getLogger().info("COMPAT_CHECK_PASS: " + label);
    }

    private void finish(Throwable error) {
        try {
            String quotedChecks = String.join(",", checks.stream().map(s -> "\"" + s + "\"").toList());
            String details = error == null ? "null" : "\"" + error.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            Files.writeString(Path.of("ce-compatibility-result.json"), "{\"success\":" + (error == null) + ",\"server\":\"" + Bukkit.getBukkitVersion() + "\",\"craftengine\":\"" + craftEngineVersion() + "\",\"checks\":[" + quotedChecks + "],\"error\":" + details + "}");
        } catch (Exception failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to save verification result", failure);
        }
        if (error == null) getLogger().info("COMPAT_PROBE_PASSED");
        else getLogger().log(java.util.logging.Level.SEVERE, "COMPAT_PROBE_FAILED", error);
        // The outer runner stops the server after resource pack generation completes.
    }
}
