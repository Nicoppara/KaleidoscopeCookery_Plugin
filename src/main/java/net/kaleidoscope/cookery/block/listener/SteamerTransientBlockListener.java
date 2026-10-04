package net.kaleidoscope.cookery.block.listener;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.kaleidoscope.cookery.block.behavior.SteamerBehavior;
import net.momirealms.craftengine.bukkit.plugin.network.BukkitNetworkManager;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.plugin.network.NetWorkUser;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.BundlePacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundBundlePacketProxy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Remaps the 26.3 falling-block source packet that older CE builds leave untouched. */
public final class SteamerTransientBlockListener implements Listener, AutoCloseable {
    private static final String HANDLER = "kaleidoscopecookery_transient_steamer";
    private final Bindings bindings;
    private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public SteamerTransientBlockListener(Plugin plugin) {
        this.bindings = Bindings.find();
        if (bindings != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getOnlinePlayers().forEach(this::install);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        install(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Channel channel = BukkitNetworkManager.instance().getChannel(event.getPlayer());
        if (channel != null) uninstall(channel);
    }

    private void install(Player player) {
        Channel channel = BukkitNetworkManager.instance().getChannel(player);
        if (channel != null) install(channel);
    }

    private void install(Channel channel) {
        onChannel(channel, () -> {
            if (closed || bindings == null || channel.pipeline().get(HANDLER) != null
                    || channel.pipeline().get("packet_handler") == null) return;
            channel.pipeline().addBefore("packet_handler", HANDLER, new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
                    NetWorkUser user = BukkitNetworkManager.instance().getUser(context.channel());
                    boolean customClient = user != null && user.clientCustomBlockEnabled();
                    context.write(remap(packet, customClient), promise);
                }
            });
            channels.add(channel);
            channel.closeFuture().addListener(future -> channels.remove(channel));
        });
    }

    private void uninstall(Channel channel) {
        onChannel(channel, () -> {
            if (channel.pipeline().get(HANDLER) != null) channel.pipeline().remove(HANDLER);
            channels.remove(channel);
        });
    }

    private Object remap(Object packet, boolean customClient) throws ReflectiveOperationException {
        if (bindings.packetClass.isInstance(packet)) {
            Object state = bindings.state.invoke(packet);
            int stateId = BlockStateUtils.blockStateToId(state);
            if (BlockStateUtils.isVanillaBlock(stateId)) return packet;
            var custom = BlockStateUtils.getNullableCustomBlockState(state);
            if (custom == null || custom.behavior().getFirst(SteamerBehavior.class) == null) return packet;
            int mappedId = BukkitNetworkManager.instance().remapBlockState(stateId, customClient);
            if (mappedId == stateId) return packet;
            return bindings.constructor.newInstance(bindings.pos.invoke(packet), BlockStateUtils.idToBlockState(mappedId));
        }
        if (ClientboundBundlePacketProxy.CLASS.isInstance(packet)) {
            ArrayList<Object> packets = new ArrayList<>();
            boolean changed = false;
            for (Object child : BundlePacketProxy.INSTANCE.getPackets(packet)) {
                Object mapped = remap(child, customClient);
                changed |= mapped != child;
                packets.add(mapped);
            }
            return changed ? ClientboundBundlePacketProxy.INSTANCE.newInstance(packets) : packet;
        }
        return packet;
    }

    private static void onChannel(Channel channel, Runnable action) {
        if (channel.eventLoop().inEventLoop()) action.run();
        else channel.eventLoop().execute(action);
    }

    @Override
    public void close() {
        closed = true;
        HandlerList.unregisterAll(this);
        for (Channel channel : channels.toArray(Channel[]::new)) uninstall(channel);
    }

    private record Bindings(Class<?> packetClass, Method pos, Method state, Constructor<?> constructor) {
        private static Bindings find() {
            try {
                Class<?> type = Class.forName("net.minecraft.network.protocol.game.ClientboundAddTransientBlockPacket");
                Method pos = type.getMethod("getPos");
                Method state = type.getMethod("getBlockState");
                return new Bindings(type, pos, state, type.getConstructor(pos.getReturnType(), state.getReturnType()));
            } catch (ClassNotFoundException absentOnOlderServers) {
                return null;
            } catch (ReflectiveOperationException incompatiblePacket) {
                throw new IllegalStateException("Cannot bind the transient steamer packet remapper", incompatiblePacket);
            }
        }
    }
}
