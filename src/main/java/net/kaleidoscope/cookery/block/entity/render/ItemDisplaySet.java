package net.kaleidoscope.cookery.block.entity.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.bukkit.util.EntityUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 展示实体槽集合 管理一组假 ItemDisplay 的 entityId/uuid 与已构建的生成/元数据包
public final class ItemDisplaySet {
    private final int[] entityIds;
    private final UUID[] uuids;
    private final Object[] removePackets;
    private final Object removeAllPacket;

    // 数组元素的原地写没有 happens-before 别的 region 线程可能永远读到陈旧值
    // 所以改画面一律新建数组加一次 volatile 写 rebuild 不在热路径 这份拷贝可忽略
    private volatile Slot[] frame;

    // 每个玩家已收到的各槽帧号 0 表示还没发过 共用一份会让后进视野的玩家收不到 spawn
    private final Map<UUID, int[]> sent = new ConcurrentHashMap<>();

    // 玩家退出登录时 CE 不发忘记区块包 也就不会走 hide 这条清理路径
    // 反向记一份 玩家出现在哪些集合里 退出时按它去清 整条以玩家为键 退出即整条摘掉 不会无界增长
    private static final Map<UUID, Set<ItemDisplaySet>> BY_PLAYER = new ConcurrentHashMap<>();

    public static void forgetPlayer(UUID playerId) {
        Set<ItemDisplaySet> sets = BY_PLAYER.remove(playerId);
        if (sets != null) {
            sets.forEach(set -> set.sent.remove(playerId));
        }
    }

    public static void clearAll() {
        BY_PLAYER.clear();
    }

    private int[] seenOf(Player player) {
        UUID playerId = player.uuid();
        int[] seen = this.sent.get(playerId);
        if (seen != null) {
            return seen;
        }
        int[] created = new int[size()];
        int[] existing = this.sent.putIfAbsent(playerId, created);
        if (existing != null) {
            return existing;
        }
        BY_PLAYER.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(this);
        return created;
    }

    // 帧号从 1 起 让 0 专门表示没发过
    private record Slot(Object spawn, Object meta, int version) {
        private static final Slot EMPTY = new Slot(null, null, 1);

        Slot next(Object spawnPacket, Object metaPacket) {
            return new Slot(spawnPacket, metaPacket, this.version + 1);
        }
    }

    public ItemDisplaySet(int size) {
        this.entityIds = new int[size];
        this.uuids = new UUID[size];
        this.removePackets = new Object[size];
        this.frame = new Slot[size];
        Arrays.fill(this.frame, Slot.EMPTY);
        IntArrayList all = new IntArrayList(size);
        for (int i = 0; i < size; i++) {
            this.entityIds[i] = EntityUtils.ENTITY_COUNTER.incrementAndGet();
            this.uuids[i] = UUID.randomUUID();
            all.add(this.entityIds[i]);
            IntArrayList single = new IntArrayList(1);
            single.add(this.entityIds[i]);
            this.removePackets[i] = ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(single);
        }
        this.removeAllPacket = ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(all);
    }

    public int size() {
        return entityIds.length;
    }

    public int id(int index) {
        return entityIds[index];
    }

    public UUID uuid(int index) {
        return uuids[index];
    }

    public void setPackets(int index, Object spawnPacket, Object metaPacket) {
        Slot[] next = this.frame.clone();
        next[index] = next[index].next(spawnPacket, metaPacket);
        this.frame = next;
    }

    public Object spawn(int index) {
        return this.frame[index].spawn();
    }

    public Object meta(int index) {
        return this.frame[index].meta();
    }

    public void clear(int index) {
        setPackets(index, null, null);
    }

    // 若该槽已构建生成包 则向玩家发送 生成包 + 元数据包
    public void showSlot(Player player, int index) {
        Slot slot = this.frame[index];
        if (slot.spawn() != null) {
            player.sendPackets(List.of(slot.spawn(), slot.meta()), false);
        }
    }

    public void removeSlot(Player player, int index) {
        player.sendPacket(removePackets[index], false);
    }

    public void removeAll(Player player) {
        player.sendPacket(removeAllPacket, false);
    }


    // 有内容的槽整帧发出 合成一个 Bundle 客户端才会在同一帧一起应用
    public void show(Player player) {
        Slot[] current = this.frame;
        int[] seen = seenOf(player);
        List<Object> packets = new ArrayList<>(current.length * 2);
        for (int index = 0; index < current.length; index++) {
            Slot slot = current[index];
            if (slot.spawn() == null) {
                continue;
            }
            packets.add(slot.spawn());
            packets.add(slot.meta());
            seen[index] = slot.version();
        }
        PacketBundles.send(player, packets);
    }

    // 按槽差异补包 已生成的只补元数据 重发 spawn 会让客户端重建实体丢插值
    // 帧号没变的槽一个包都不发 邻居更新会把整帧重建一遍 内容其实大多没动
    public void update(Player player) {
        Slot[] current = this.frame;
        int[] seen = seenOf(player);
        List<Object> packets = new ArrayList<>(current.length * 2);
        for (int index = 0; index < current.length; index++) {
            Slot slot = current[index];
            if (slot.spawn() == null) {
                if (seen[index] != 0) {
                    packets.add(removePackets[index]);
                    seen[index] = 0;
                }
                continue;
            }
            if (seen[index] == slot.version()) {
                continue;
            }
            if (seen[index] == 0) {
                packets.add(slot.spawn());
            }
            packets.add(slot.meta());
            seen[index] = slot.version();
        }
        PacketBundles.send(player, packets);
    }

    public void hide(Player player) {
        removeAll(player);
        UUID playerId = player.uuid();
        if (this.sent.remove(playerId) != null) {
            Set<ItemDisplaySet> sets = BY_PLAYER.get(playerId);
            if (sets != null) {
                sets.remove(this);
            }
        }
    }
}
