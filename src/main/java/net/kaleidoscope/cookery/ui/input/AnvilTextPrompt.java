package net.kaleidoscope.cookery.ui.input;

import net.kaleidoscope.cookery.ui.MenuTasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// 1.21.6 以下没有 dialog 协议 用真铁砧的改名框收文本
@SuppressWarnings("removal")
public final class AnvilTextPrompt implements Listener {
    private static final int SLOT_INPUT = 0;
    private static final int SLOT_RESULT = 2;

    // region 线程与玩家退出事件都会碰它 必须并发容器
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final class Session {
        private final Consumer<String> onConfirm;
        private final Runnable onCancel;
        private final String initial;
        private boolean finished;

        private Session(String initial, Consumer<String> onConfirm, Runnable onCancel) {
            this.initial = initial;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }
    }

    static void open(org.bukkit.entity.Player player, String title, String initial,
                     Consumer<String> onConfirm, Runnable onCancel) {
        SESSIONS.put(player.getUniqueId(), new Session(initial, onConfirm, onCancel));
        InventoryView view = player.openAnvil(null, true);
        if (view == null) {
            SESSIONS.remove(player.getUniqueId());
            onCancel.run();
            return;
        }
        view.setTitle(title);
        // 输入物品的名字就是改名框的初始值 客户端据此预填
        view.getTopInventory().setItem(SLOT_INPUT, namedPaper(initial));
    }

    private static ItemStack namedPaper(String name) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name));
        stack.setItemMeta(meta);
        return stack;
    }

    private static Session sessionOf(HumanEntity human) {
        return SESSIONS.get(human.getUniqueId());
    }

    // 结果槽必须有物品客户端才允许点击 内容无所谓 反正点击一律取消
    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (sessionOf(event.getView().getPlayer()) == null) {
            return;
        }
        AnvilInventory inventory = event.getInventory();
        inventory.setRepairCost(0);
        String text = inventory.getRenameText();
        event.setResult(namedPaper(text == null || text.isEmpty() ? " " : text));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        Session session = sessionOf(event.getWhoClicked());
        if (session == null || !(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() != SLOT_RESULT) {
            return;
        }
        String text = anvil.getRenameText();
        String value = text == null || text.isEmpty() ? session.initial : text;
        finish(event.getWhoClicked(), session, () -> session.onConfirm.accept(value));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (sessionOf(event.getWhoClicked()) != null && event.getInventory() instanceof AnvilInventory) {
            event.setCancelled(true);
        }
    }

    // 关闭事件在 AnvilMenu.removed 之前触发 这里清空容器
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = SESSIONS.get(event.getPlayer().getUniqueId());
        if (session == null || !(event.getInventory() instanceof AnvilInventory)) {
            return;
        }
        clear(event.getInventory());
        SESSIONS.remove(event.getPlayer().getUniqueId());
        if (session.finished) {
            return;
        }
        MenuTasks.reopenFor((org.bukkit.entity.Player) event.getPlayer(), session.onCancel);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    // 会话留到 onClose 再摘 靠 finished 区分确认与取消
    private void finish(HumanEntity human, Session session, Runnable action) {
        session.finished = true;
        human.closeInventory();
        MenuTasks.reopenFor((org.bukkit.entity.Player) human, action);
    }

    private static void clear(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }
    }

    public static void clearAll() {
        SESSIONS.clear();
    }
}
