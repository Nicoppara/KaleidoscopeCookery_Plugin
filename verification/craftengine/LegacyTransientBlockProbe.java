import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import net.kaleidoscope.cookery.block.listener.SteamerTransientBlockListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Checks that the new packet guard is inactive on pre-26.3 servers. */
public final class LegacyTransientBlockProbe extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::verify, 100);
    }

    private void verify() {
        Throwable failure = null;
        try {
            check(Bukkit.getMinecraftVersion().equals("1.21.11"), "legacy Minecraft 1.21.11");
            check(Bukkit.getPluginManager().getPlugin("CraftEngine").getDescription().getVersion().equals("26.9.2"), "legacy CraftEngine 26.9.2");
            var cookery = Bukkit.getPluginManager().getPlugin("KaleidoscopeCookeryPlugin");
            check(cookery.isEnabled() && cookery.getDescription().getVersion().equals("1.2.3"), "legacy Cookery 1.2.3 enabled");
            boolean absent = false;
            try { Class.forName("net.minecraft.network.protocol.game.ClientboundAddTransientBlockPacket"); }
            catch (ClassNotFoundException expected) { absent = true; }
            check(absent, "legacy server has no transient block packet class");
            var field = cookery.getClass().getDeclaredField("steamerTransients");
            field.setAccessible(true);
            Object live = field.get(cookery);
            var bindings = SteamerTransientBlockListener.class.getDeclaredField("bindings");
            bindings.setAccessible(true);
            check(bindings.get(live) == null, "legacy packet bindings stay inactive");
            var install = SteamerTransientBlockListener.class.getDeclaredMethod("install", Channel.class);
            install.setAccessible(true);
            EmbeddedChannel channel = new EmbeddedChannel();
            channel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
            try {
                install.invoke(live, channel);
                check(channel.pipeline().get("kaleidoscopecookery_transient_steamer") == null, "legacy channel remains unchanged");
                Object packet = new Object();
                channel.writeOutbound(packet);
                check(channel.readOutbound() == packet, "legacy outgoing object remains unchanged");
            } finally { channel.finishAndReleaseAll(); }
            SteamerTransientBlockListener separate = new SteamerTransientBlockListener(cookery);
            separate.close();
            separate.close();
            check(true, "legacy guard closes safely");
        } catch (Throwable error) {
            failure = error;
            getLogger().log(java.util.logging.Level.SEVERE, "LEGACY_TRANSIENT_PROBE_FAILED", error);
        }
        try {
            String labels = String.join(",", checks.stream().map(s -> "\"" + s + "\"").toList());
            String error = failure == null ? "null" : "\"" + failure.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            Files.writeString(Path.of("legacy-transient-result.json"), "{\"success\":" + (failure == null) + ",\"minecraft\":\"" + Bukkit.getMinecraftVersion() + "\",\"craftengine\":\"26.9.2\",\"checks\":[" + labels + "],\"error\":" + error + "}");
        } catch (Exception error) { getLogger().log(java.util.logging.Level.SEVERE, "Cannot save legacy result", error); }
        if (failure == null) getLogger().info("LEGACY_TRANSIENT_PROBE_PASSED");
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 10);
    }

    private void check(boolean success, String label) {
        if (!success) throw new AssertionError(label);
        checks.add(label);
    }
}
