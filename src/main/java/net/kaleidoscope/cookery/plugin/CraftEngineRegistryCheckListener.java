package net.kaleidoscope.cookery.plugin;

import net.kaleidoscope.cookery.util.ConsoleMessages;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CraftEngineRegistryCheckListener implements Listener {
    private static final List<ExpectedBlock> EXPECTED_BLOCKS = List.of(
            block("kaleidoscopecookery:chopping_board", "custom/configuration/appliance/chopping_board.yml"),
            block("kaleidoscopecookery:enamel_basin", "custom/configuration/appliance/enamel_basin.yml"),
            block("kaleidoscopecookery:kitchenware_racks", "custom/configuration/appliance/kitchenware_racks.yml"),
            block("kaleidoscopecookery:pot", "custom/configuration/appliance/pot.yml"),
            block("kaleidoscopecookery:shawarma_spit", "custom/configuration/appliance/shawarma_spit.yml"),
            block("kaleidoscopecookery:steamer", "custom/configuration/appliance/steamer.yml"),
            block("kaleidoscopecookery:stockpot", "custom/configuration/appliance/stockpot.yml"),
            block("kaleidoscopecookery:stove", "custom/configuration/appliance/stove.yml"),
            block("kaleidoscopecookery:fruit_basket", "custom/configuration/furniture/fruit_basket.yml"),
            block("kaleidoscopecookery:teapot", "custom/configuration/furniture/teapot.yml"),
            block("kaleidoscopecookery:teacup_coaster", "custom/configuration/furniture/teapot.yml")
    );

    private final KaleidoscopeCookeryPlugin plugin;

    public CraftEngineRegistryCheckListener(KaleidoscopeCookeryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraftEngineReload(CraftEngineReloadEvent event) {
        ConsoleMessages.reloadFromDisk(plugin);
        checkLoadedBlocks(event.isFirstReload());
    }

    private void checkLoadedBlocks(boolean firstReload) {
        Map<Key, ?> loadedBlocks = CraftEngineBlocks.loadedBlocks();
        List<ExpectedBlock> missing = new ArrayList<>();
        for (ExpectedBlock expected : EXPECTED_BLOCKS) {
            if (!loadedBlocks.containsKey(expected.key())) {
                missing.add(expected);
            }
        }

        String phase = ConsoleMessages.t(firstReload
                ? "craftengine.registry.phase.first"
                : "craftengine.registry.phase.reload");
        if (missing.isEmpty()) {
            plugin.getLogger().info(ConsoleMessages.t("craftengine.registry.blocks.ok",
                    phase, EXPECTED_BLOCKS.size(), EXPECTED_BLOCKS.size()));
            return;
        }

        plugin.getLogger().severe(ConsoleMessages.t("craftengine.registry.blocks.missing",
                phase, missing.size(), EXPECTED_BLOCKS.size()));
        for (ExpectedBlock expected : missing) {
            plugin.getLogger().severe(ConsoleMessages.t("craftengine.registry.blocks.missing.item",
                    expected.key().asString(), expected.configPath()));
        }
        plugin.getLogger().severe(ConsoleMessages.t("craftengine.registry.blocks.hint"));
    }

    private static ExpectedBlock block(String id, String configPath) {
        return new ExpectedBlock(Key.of(id), configPath);
    }

    private record ExpectedBlock(Key key, String configPath) {
    }
}
