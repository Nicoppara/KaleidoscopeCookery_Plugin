package top.nicoppa.nicoPengRen.common.render;

import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundBundlePacketProxy;

import java.util.List;

/**
 * 发包打包工具 把多个数据包合并为一个 Bundle 包一次性下发，空列表则跳过
 */
public final class PacketBundles {
    private PacketBundles() {}

    public static Object of(List<Object> packets) {
        return ClientboundBundlePacketProxy.INSTANCE.newInstance(packets);
    }

    public static void send(Player player, List<Object> packets) {
        if (packets == null || packets.isEmpty()) return;
        player.sendPacket(ClientboundBundlePacketProxy.INSTANCE.newInstance(packets), false);
    }
}
