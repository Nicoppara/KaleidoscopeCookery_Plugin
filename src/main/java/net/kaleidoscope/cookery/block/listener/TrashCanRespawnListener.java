package net.kaleidoscope.cookery.block.listener;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.kaleidoscope.cookery.block.entity.TrashCanController;
import net.kaleidoscope.cookery.nms.NmsBridgeProvider;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.plugin.network.BukkitNetworkManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public final class TrashCanRespawnListener {
    private static final String HANDLER = "kaleidoscopecookery_respawn";

    private TrashCanRespawnListener() {}

    public static void registerBukkitEvents(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new BukkitRespawnEvents(), plugin);
    }

    public static void registerFoliaPackets(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new FoliaRespawnPackets(), plugin);
        Bukkit.getOnlinePlayers().forEach(TrashCanRespawnListener::install);
    }

    public static void uninstallAll() {
        Bukkit.getOnlinePlayers().forEach(TrashCanRespawnListener::uninstall);
    }

    private static void install(Player player) {
        Channel channel = BukkitNetworkManager.instance().getChannel(player);
        if (channel == null) {
            return;
        }
        runOnChannel(channel, () -> {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER) != null || pipeline.get("packet_handler") == null) {
                return;
            }
            pipeline.addBefore("packet_handler", HANDLER, new RespawnPacketHandler(player));
        });
    }

    private static void uninstall(Player player) {
        Channel channel = BukkitNetworkManager.instance().getChannel(player);
        if (channel == null) {
            return;
        }
        runOnChannel(channel, () -> {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER) != null) {
                pipeline.remove(HANDLER);
            }
        });
    }

    private static void runOnChannel(Channel channel, Runnable task) {
        if (channel.eventLoop().inEventLoop()) {
            task.run();
        } else {
            channel.eventLoop().execute(task);
        }
    }

    private static final class BukkitRespawnEvents implements Listener {
        @EventHandler
        public void onRespawn(PlayerRespawnEvent event) {
            TrashCanController.handleRespawn(event.getPlayer());
        }
    }

    private static final class FoliaRespawnPackets implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            install(event.getPlayer());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            uninstall(event.getPlayer());
        }
    }

    private static final class RespawnPacketHandler extends ChannelInboundHandlerAdapter {
        private final Player player;

        private RespawnPacketHandler(Player player) {
            this.player = player;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            boolean respawn = NmsBridgeProvider.bridge().isPerformRespawnPacket(msg);
            super.channelRead(ctx, msg);
            if (respawn) {
                BukkitCraftEngine.instance().scheduler().platform().run(
                        () -> {
                            if (player.isOnline()) {
                                TrashCanController.handleRespawn(player);
                            }
                        },
                        null,
                        player
                );
            }
        }
    }
}
