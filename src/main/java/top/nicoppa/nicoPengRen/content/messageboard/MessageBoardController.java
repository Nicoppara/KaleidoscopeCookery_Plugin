package top.nicoppa.nicoPengRen.content.messageboard;

import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBox;
import net.momirealms.craftengine.core.entity.furniture.tick.FurnitureTicker;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.InteractEntityContext;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 留言板控制器 维护每玩家留言展示状态，按 tick 检测视角朝向决定显隐，并读写家具 NBT 中的留言列表
 * TODO：正式版删除 临时项目
 */

// TODO 写了一坨屎在这里，能拆出去好几个东西，若以后常驻需要重写
public class MessageBoardController extends FurnitureController {
    private static final String MESSAGES_KEY = "messages";
    private static final int MAX_COMMENTS = 5;
    private static final int CHECK_INTERVAL = 4;

    private final Map<UUID, PlayerCommentState> playerStates = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public MessageBoardController(Furniture furniture) {
        super(furniture);
    }

    @Override
    public void preRemove(@Nullable Player player) {
        for (Player trackedPlayer : furniture.getTrackedBy()) {
            PlayerCommentState state = playerStates.get(trackedPlayer.uuid());
            if (state != null && state.element != null) {
                state.element.hide(trackedPlayer);
                state.element.deactivate();
            }
        }
        playerStates.clear();
    }

    @Override
    public InteractionResult useOnFurniture(FurnitureHitBox hitBox, InteractEntityContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.SUCCESS_AND_CANCEL;

        org.bukkit.entity.Player bukkitPlayer = ((net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer) player).platformPlayer();
        if (bukkitPlayer == null) return InteractionResult.SUCCESS_AND_CANCEL;

        List<MessageData> messages = getMessages();

        io.papermc.paper.registry.data.dialog.DialogInstancesProvider provider = io.papermc.paper.registry.data.dialog.DialogInstancesProvider.instance();

        io.papermc.paper.registry.data.dialog.DialogBase dialogBase = provider.dialogBaseBuilder(
                        net.kyori.adventure.text.Component.text("留言板")
                )
                .body(messages.isEmpty()
                                ? List.of(provider.plainMessageDialogBody(net.kyori.adventure.text.Component.text("暂无留言")))
                                : messages.stream().map(msg ->
                                provider.plainMessageDialogBody(net.kyori.adventure.text.Component.text(
                                        msg.playerName + ": " + msg.message
                                ))
                        ).toList()
                )
                .inputs(List.of(
                        provider.textBuilder("message_input", net.kyori.adventure.text.Component.text("输入留言"))
                                .maxLength(100)
                                .build()
                ))
                .build();

        io.papermc.paper.registry.data.dialog.action.DialogAction.CustomClickAction action = provider.register(
                (view, audience) -> {
                    String message = view.getText("message_input");
                    if (message != null && !message.isEmpty()) {
                        addMessage(bukkitPlayer.getName(), message);
                        bukkitPlayer.sendMessage(net.kyori.adventure.text.Component.text("留言已添加"));
                        refreshAllPlayerComments();
                    }
                },
                net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()
        );

        io.papermc.paper.registry.data.dialog.ActionButton sendButton = provider.actionButtonBuilder(
                net.kyori.adventure.text.Component.text("发送")
        ).action(action).build();

        io.papermc.paper.registry.data.dialog.ActionButton cancelButton = provider.actionButtonBuilder(
                net.kyori.adventure.text.Component.text("取消")
        ).build();

        io.papermc.paper.registry.data.dialog.type.ConfirmationType dialogType = provider.confirmation(sendButton, cancelButton);

        io.papermc.paper.dialog.Dialog dialog = io.papermc.paper.registry.data.InlinedRegistryBuilderProvider.instance().createDialog(builder -> {
            io.papermc.paper.registry.data.dialog.DialogRegistryEntry.Builder dialogBuilder = builder.empty();
            dialogBuilder.base(dialogBase).type(dialogType);
        });

        bukkitPlayer.showDialog(dialog);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private List<MessageData> getMessages() {
        CompoundTag data = (CompoundTag) furniture.persistentData().getTag(FurniturePersistentData.CUSTOM_DATA);
        if (data == null) {
            data = new CompoundTag();
        }
        List<MessageData> messages = new ArrayList<>();
        ListTag list = data.getList(MESSAGES_KEY);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                messages.add(new MessageData(
                        tag.getString("playerName"),
                        tag.getString("message")
                ));
            }
        }
        return messages;
    }

    private void addMessage(String playerName, String message) {
        List<MessageData> messages = getMessages();
        messages.add(new MessageData(playerName, message));
        if (messages.size() > MAX_COMMENTS) {
            messages.remove(0);
        }
        saveMessages(messages);
    }

    private void saveMessages(List<MessageData> messages) {
        CompoundTag data = (CompoundTag) furniture.persistentData().getTag(FurniturePersistentData.CUSTOM_DATA);
        if (data == null) {
            data = new CompoundTag();
        }
        ListTag list = new ListTag();
        for (MessageData msg : messages) {
            CompoundTag tag = new CompoundTag();
            tag.putString("playerName", msg.playerName);
            tag.putString("message", msg.message);
            list.add(tag);
        }
        data.put(MESSAGES_KEY, list);
        furniture.persistentData().addTag(FurniturePersistentData.CUSTOM_DATA, data);
        furniture.persistentData().markUnsaved();
    }

    private void refreshAllPlayerComments() {
        for (Player player : furniture.getTrackedBy()) {
            PlayerCommentState state = playerStates.get(player.uuid());
            if (state != null) {
                state.needsRefresh = true;
            }
        }
    }

    @Override
    public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return (furniture, controller) -> {
            MessageBoardController ctrl = (MessageBoardController) controller;
            ctrl.tickCounter++;
            if (ctrl.tickCounter % CHECK_INTERVAL != 0) return;
            List<MessageData> messages = ctrl.getMessages();
            if (messages.isEmpty()) {
                for (Player player : furniture.getTrackedBy()) {
                    ctrl.hideCommentsForPlayer(player);
                }
                return;
            }
            for (Player player : furniture.getTrackedBy()) {
                ctrl.updatePlayerCommentState(player, messages);
            }
        };
    }

    private void updatePlayerCommentState(Player player, List<MessageData> messages) {
        UUID playerUuid = player.uuid();
        org.bukkit.entity.Player bukkitPlayer = ((net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer) player).platformPlayer();
        if (bukkitPlayer == null) return;

        boolean isLooking = isPlayerLookingAtFurniture(bukkitPlayer);
        PlayerCommentState state = playerStates.computeIfAbsent(playerUuid, k -> new PlayerCommentState());

        if (isLooking) {
            if (!state.isVisible || state.needsRefresh) {
                showCommentsForPlayer(player, messages, bukkitPlayer, state);
                state.isVisible = true;
                state.needsRefresh = false;
            } else {
                updateCommentPositions(player, bukkitPlayer, messages, state);
            }
        } else {
            if (state.isVisible) {
                hideCommentsForPlayer(player);
                state.isVisible = false;
            }
        }
    }

    private boolean isPlayerLookingAtFurniture(org.bukkit.entity.Player bukkitPlayer) {
        WorldPosition thisPos = this.furniture.position();
        org.bukkit.Location playerLoc = bukkitPlayer.getLocation();

        double distance = Math.sqrt(
                Math.pow(playerLoc.getX() - thisPos.x, 2) +
                        Math.pow(playerLoc.getY() - thisPos.y, 2) +
                        Math.pow(playerLoc.getZ() - thisPos.z, 2)
        );
        if (distance > 5.0) return false;

        net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture furniture = CraftEngineFurniture.rayTrace(bukkitPlayer, 5.0);
        if (furniture == null) return false;
        if (!furniture.id().equals(this.furniture.id())) return false;

        WorldPosition furniturePos = furniture.position();
        double furnitureDistance = Math.sqrt(
                Math.pow(furniturePos.x - thisPos.x, 2) +
                        Math.pow(furniturePos.y - thisPos.y, 2) +
                        Math.pow(furniturePos.z - thisPos.z, 2)
        );
        return furnitureDistance < 2.0;
    }

    private void showCommentsForPlayer(Player player, List<MessageData> messages,
                                       org.bukkit.entity.Player bukkitPlayer, PlayerCommentState state) {
        if (state.element != null) {
            state.element.hide(player);
            state.element.deactivate();
            state.element = null;
        }

        WorldPosition furniturePos = furniture.position();
        float playerYaw = bukkitPlayer.getLocation().getYaw();
        double rightX = -Math.cos(Math.toRadians(playerYaw));
        double rightZ = -Math.sin(Math.toRadians(playerYaw));

        double offsetX = rightX * 1.2;
        double offsetZ = rightZ * 1.2;
        double offsetY = 0;

        StringBuilder sb = new StringBuilder();
        int count = Math.min(messages.size(), MAX_COMMENTS);
        for (int i = 0; i < MAX_COMMENTS; i++) {
            if (i < count) {
                sb.append(messages.get(i).playerName()).append(": ").append(messages.get(i).message());
            }
            if (i < MAX_COMMENTS - 1) sb.append("\n\n");
        }

        MessageBoardCommentElement element = new MessageBoardCommentElement(
                furniturePos.x + offsetX,
                furniturePos.y + offsetY,
                furniturePos.z + offsetZ,
                sb.toString(),
                playerYaw
        );
        element.activate();
        element.showInternal(player);
        state.element = element;
    }

    private void updateCommentPositions(Player player, org.bukkit.entity.Player bukkitPlayer,
                                        List<MessageData> messages, PlayerCommentState state) {
        if (state.element == null) return;
        WorldPosition furniturePos = furniture.position();
        float playerYaw = bukkitPlayer.getLocation().getYaw();
        double rightX = -Math.cos(Math.toRadians(playerYaw));
        double rightZ = -Math.sin(Math.toRadians(playerYaw));

        state.element.updatePosition(player,
                furniturePos.x + rightX * 1.2,
                furniturePos.y + 0,
                furniturePos.z + rightZ * 1.2,
                playerYaw);
    }


    private void hideCommentsForPlayer(Player player) {
        PlayerCommentState state = playerStates.get(player.uuid());
        if (state != null && state.element != null) {
            state.element.hide(player);
            state.element.deactivate();
            state.element = null;
        }
    }

    @Override
    public void onPlayerTrack(Player player) {
    }

    @Override
    public void onPlayerUntrack(Player player) {
        hideCommentsForPlayer(player);
        playerStates.remove(player.uuid());
    }

    private static class PlayerCommentState {
        @Nullable MessageBoardCommentElement element = null;
        boolean isVisible = false;
        boolean needsRefresh = false;
    }

    private record MessageData(String playerName, String message) {}
}
