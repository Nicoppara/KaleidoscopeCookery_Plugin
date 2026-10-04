import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import net.kaleidoscope.cookery.block.listener.SteamerTransientBlockListener;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.BundlePacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundBundlePacketProxy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMapper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundAddTransientBlockPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Actual 26.3 packet codec plus a vanilla-only state registry, without a graphical client. */
public final class TransientBlockProbe {
    public static void verify(JavaPlugin probe, ImmutableBlockState source, World world, BiConsumer<Boolean, String> check) throws Exception {
        int vanillaCount = BlockStateUtils.vanillaBlockStateCount();
        IdMapper<BlockState> vanilla = new IdMapper<>(vanillaCount);
        for (int id = 0; id < vanillaCount; id++) vanilla.addMapping(Block.BLOCK_STATE_REGISTRY.byId(id), id);
        BlockState customState = (BlockState) source.customBlockState().minecraftState();
        int rawId = BlockStateUtils.blockStateToId(customState);
        BlockPos pos = new BlockPos(8, 100, 8);
        ClientboundAddTransientBlockPacket original = new ClientboundAddTransientBlockPacket(pos, customState);
        check.accept(rawId >= vanillaCount, "transient source uses server-only state");
        boolean rejected = false;
        try {
            decodeAsVanilla(original, world, vanilla);
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().equals("No value with id " + rawId);
        }
        check.accept(rejected, "unmapped transient packet reproduces vanilla decoder failure");
        probe.getLogger().info("TRANSIENT_PACKET_DIAGNOSTIC: serverState=" + rawId + " vanillaStates=" + vanillaCount);

        var cookery = Bukkit.getPluginManager().getPlugin("KaleidoscopeCookeryPlugin");
        var field = cookery.getClass().getDeclaredField("steamerTransients");
        field.setAccessible(true);
        Object liveListener = field.get(cookery);
        check.accept(liveListener != null, "transient listener registered by plugin");
        Method install = SteamerTransientBlockListener.class.getDeclaredMethod("install", Channel.class);
        Method uninstall = SteamerTransientBlockListener.class.getDeclaredMethod("uninstall", Channel.class);
        Method remap = SteamerTransientBlockListener.class.getDeclaredMethod("remap", Object.class, boolean.class);
        install.setAccessible(true);
        uninstall.setAccessible(true);
        remap.setAccessible(true);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
        try {
            install.invoke(liveListener, channel);
            check.accept(channel.pipeline().get("kaleidoscopecookery_transient_steamer") != null, "transient outbound handler installed");
            install.invoke(liveListener, channel);
            check.accept(channel.pipeline().names().stream().filter("kaleidoscopecookery_transient_steamer"::equals).count() == 1, "transient repeated install is idempotent");
            channel.writeOutbound(original);
            ClientboundAddTransientBlockPacket rewritten = channel.readOutbound();
            check.accept(rewritten != original, "transient steamer packet replaced");
            check.accept(rewritten.getPos().equals(pos), "transient packet preserves source position");
            check.accept(BlockStateUtils.isVanillaBlock(BlockStateUtils.blockStateToId(rewritten.getBlockState())), "transient packet uses vanilla state");
            check.accept(decodeAsVanilla(rewritten, world, vanilla) == rewritten.getBlockState(), "transient rewritten payload decodes with vanilla registry");
            check.accept(original.getBlockState() == customState, "transient original packet remains unmodified");
            check.accept(CraftEngineBlocks.getCustomBlockState(world.getBlockAt(8, 100, 8)).equals(source), "transient remapping preserves server steamer state");
            check.accept(remap.invoke(liveListener, original, true) == original, "transient CE mod client keeps custom state");

            ClientboundAddTransientBlockPacket sand = new ClientboundAddTransientBlockPacket(pos, Blocks.SAND.defaultBlockState());
            channel.writeOutbound(sand);
            check.accept(channel.readOutbound() == sand, "transient vanilla falling block unchanged");
            ImmutableBlockState stockpot = CraftEngineBlocks.getCustomBlockState(world.getBlockAt(22, 100, 8));
            ClientboundAddTransientBlockPacket other = new ClientboundAddTransientBlockPacket(pos, (BlockState) stockpot.customBlockState().minecraftState());
            channel.writeOutbound(other);
            check.accept(channel.readOutbound() == other, "transient other custom block unchanged");

            Object bundle = ClientboundBundlePacketProxy.INSTANCE.newInstance(List.of(sand, original));
            channel.writeOutbound(bundle);
            Object mappedBundle = channel.readOutbound();
            List<Object> children = new ArrayList<>();
            BundlePacketProxy.INSTANCE.getPackets(mappedBundle).forEach(children::add);
            check.accept(mappedBundle != bundle && children.size() == 2, "transient bundle keeps all children");
            check.accept(children.get(0) == sand, "transient bundle preserves other packet identity and order");
            check.accept(decodeAsVanilla((ClientboundAddTransientBlockPacket) children.get(1), world, vanilla) != null, "transient bundled payload decodes with vanilla registry");
            check.accept(BundlePacketProxy.INSTANCE.getPackets(bundle).iterator().next() == sand && original.getBlockState() == customState, "transient original bundle remains unmodified");
            Object untouchedBundle = ClientboundBundlePacketProxy.INSTANCE.newInstance(List.of(sand));
            channel.writeOutbound(untouchedBundle);
            check.accept(channel.readOutbound() == untouchedBundle, "transient unaffected bundle unchanged");
            uninstall.invoke(liveListener, channel);
            check.accept(channel.pipeline().get("kaleidoscopecookery_transient_steamer") == null, "transient handler removed on uninstall");
        } finally {
            uninstall.invoke(liveListener, channel);
            channel.finishAndReleaseAll();
        }

        // A separate instance exercises shutdown without disabling the plugin's live guard.
        SteamerTransientBlockListener lifecycle = new SteamerTransientBlockListener(cookery);
        EmbeddedChannel shutdownChannel = new EmbeddedChannel();
        shutdownChannel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
        try {
            install.invoke(lifecycle, shutdownChannel);
            lifecycle.close();
            check.accept(shutdownChannel.pipeline().get("kaleidoscopecookery_transient_steamer") == null, "transient shutdown removes handler");
            install.invoke(lifecycle, shutdownChannel);
            check.accept(shutdownChannel.pipeline().get("kaleidoscopecookery_transient_steamer") == null, "transient closed listener cannot reinstall");
        } finally {
            lifecycle.close();
            shutdownChannel.finishAndReleaseAll();
        }
    }

    private static BlockState decodeAsVanilla(ClientboundAddTransientBlockPacket packet, World world, IdMapper<BlockState> vanilla) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), ((CraftWorld) world).getHandle().registryAccess());
        try {
            ClientboundAddTransientBlockPacket.STREAM_CODEC.encode(buffer, packet);
            if (!buffer.readBlockPos().equals(packet.getPos())) throw new AssertionError("source position codec");
            BlockState state = ByteBufCodecs.idMapper(vanilla).decode(buffer);
            if (buffer.isReadable()) throw new AssertionError("unexpected trailing transient packet bytes");
            return state;
        } finally {
            buffer.release();
        }
    }
}
