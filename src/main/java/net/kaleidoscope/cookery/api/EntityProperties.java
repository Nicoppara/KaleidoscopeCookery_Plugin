package net.kaleidoscope.cookery.api;

import net.momirealms.craftengine.core.util.Key;
import net.kyori.adventure.util.TriState;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Runtime registry for boolean entity properties used by the
 * {@code kaleidoscopecookery:entity_properties} loot condition.
 * Unqualified config names resolve to the {@code minecraft} namespace.
 */
@SuppressWarnings("unused")
public final class EntityProperties {
    private static final EntityProperties INSTANCE = new EntityProperties();

    private final Map<Key, Predicate<Entity>> properties = new ConcurrentHashMap<>();

    private EntityProperties() {
        registerDefaults();
    }

    public static EntityProperties instance() {
        return INSTANCE;
    }

    /**
     * Registers a namespaced boolean entity property.
     *
     * @param id property id, such as {@code otherplugin:is_charged}
     * @param predicate state resolver called on the entity's owning thread
     * @return {@code true} if the id was not already registered
     */
    public boolean register(@NotNull Key id, @NotNull Predicate<Entity> predicate) {
        return this.properties.putIfAbsent(id, predicate) == null;
    }

    /**
     * Registers a namespaced boolean entity property.
     *
     * @param id property id, such as {@code otherplugin:is_charged}
     * @param predicate state resolver called on the entity's owning thread
     * @return {@code true} if the id was not already registered
     */
    public boolean register(@NotNull String id, @NotNull Predicate<Entity> predicate) {
        return register(key(id), predicate);
    }

    /**
     * Removes a property from the runtime registry.
     *
     * @param id property id
     * @return {@code true} if the property existed
     */
    public boolean unregister(@NotNull Key id) {
        return this.properties.remove(id) != null;
    }

    public boolean unregister(@NotNull String id) {
        return unregister(key(id));
    }

    public boolean isRegistered(@NotNull Key id) {
        return this.properties.containsKey(id);
    }

    public boolean isRegistered(@NotNull String id) {
        return isRegistered(key(id));
    }

    /**
     * Returns an immutable snapshot of registered property ids.
     *
     * @return registered property ids
     */
    public @NotNull Set<Key> keys() {
        return Set.copyOf(this.properties.keySet());
    }

    public @Nullable Predicate<Entity> predicate(@NotNull Key id) {
        return this.properties.get(id);
    }

    public static @NotNull Key key(@NotNull String id) {
        String value = id.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("实体属性 ID 不能为空");
        }
        return Key.of(value.indexOf(':') < 0 ? "minecraft:" + value : value);
    }

    private void registerDefaults() {
        builtin("is_on_fire", entity -> entity.getFireTicks() > 0);
        builtin("is_sneaking", Entity::isSneaking);
        builtin("is_sprinting", entity -> entity instanceof Player player && player.isSprinting());
        builtin("is_swimming", entity -> entity instanceof LivingEntity living && living.isSwimming());
        builtin("is_baby", entity -> entity instanceof Ageable ageable && !ageable.isAdult());
        builtin("is_adult", entity -> entity instanceof Ageable ageable && ageable.isAdult());
        builtin("is_on_ground", Entity::isOnGround);
        builtin("is_in_water", Entity::isInWater);
        builtin("is_under_water", Entity::isUnderWater);
        builtin("is_in_lava", Entity::isInLava);
        builtin("is_in_rain", Entity::isInRain);
        builtin("is_in_water_or_rain", entity -> entity.isInWater() || entity.isInRain());
        builtin("is_in_powdered_snow", entity -> entity instanceof LivingEntity living
                && living.isInPowderedSnow());
        builtin("is_frozen", Entity::isFrozen);
        builtin("is_freeze_ticking_locked", Entity::isFreezeTickingLocked);
        builtin("is_invisible", Entity::isInvisible);
        builtin("is_glowing", Entity::isGlowing);
        builtin("is_invulnerable", Entity::isInvulnerable);
        builtin("is_silent", Entity::isSilent);
        builtin("has_gravity", Entity::hasGravity);
        builtin("has_no_physics", Entity::hasNoPhysics);
        builtin("is_inside_vehicle", Entity::isInsideVehicle);
        builtin("is_empty", Entity::isEmpty);
        builtin("is_dead", Entity::isDead);
        builtin("is_valid", Entity::isValid);
        builtin("is_persistent", Entity::isPersistent);
        builtin("is_custom_name_visible", Entity::isCustomNameVisible);
        builtin("is_visible_by_default", Entity::isVisibleByDefault);
        builtin("is_in_world", Entity::isInWorld);
        builtin("is_ticking", Entity::isTicking);
        builtin("is_visual_fire", entity -> entity.getVisualFire() == TriState.TRUE);
        builtin("is_gliding", entity -> entity instanceof LivingEntity living && living.isGliding());
        builtin("is_riptiding", entity -> entity instanceof LivingEntity living && living.isRiptiding());
        builtin("is_sleeping", entity -> entity instanceof LivingEntity living && living.isSleeping());
        builtin("is_climbing", entity -> entity instanceof LivingEntity living && living.isClimbing());
        builtin("has_ai", entity -> entity instanceof LivingEntity living && living.hasAI());
        builtin("is_collidable", entity -> entity instanceof LivingEntity living && living.isCollidable());
        builtin("is_leashed", entity -> entity instanceof LivingEntity living && living.isLeashed());
        builtin("is_jumping", entity -> entity instanceof LivingEntity living && living.isJumping());
        builtin("has_active_item", entity -> entity instanceof LivingEntity living && living.hasActiveItem());
        builtin("is_hand_raised", entity -> entity instanceof LivingEntity living && living.hasActiveItem());
        builtin("can_breathe_underwater", entity -> entity instanceof LivingEntity living
                && living.canBreatheUnderwater());
        builtin("remove_when_far_away", entity -> entity instanceof LivingEntity living
                && living.getRemoveWhenFarAway());
        builtin("can_breed", entity -> entity instanceof Breedable breedable && breedable.canBreed());
        builtin("is_age_locked", entity -> entity instanceof Breedable breedable && breedable.getAgeLock());
        builtin("is_aware", entity -> entity instanceof Mob mob && mob.isAware());
        builtin("is_aggressive", entity -> entity instanceof Mob mob && mob.isAggressive());
        builtin("is_left_handed", entity -> entity instanceof Mob mob && mob.isLeftHanded());
        builtin("can_pick_up_items", entity -> entity instanceof Mob mob && mob.getCanPickupItems());
        builtin("can_pickup_items", entity -> entity instanceof Mob mob && mob.getCanPickupItems());
        builtin("is_in_daylight", entity -> entity instanceof Mob mob && mob.isInDaylight());
        builtin("is_tamed", entity -> entity instanceof Tameable tameable && tameable.isTamed());
        builtin("is_sitting", entity -> entity instanceof Sittable sittable && sittable.isSitting());
        builtin("is_flying", entity -> entity instanceof Player player && player.isFlying());
        builtin("is_blocking", entity -> entity instanceof Player player && player.isBlocking());
    }

    private void builtin(String id, Predicate<Entity> predicate) {
        this.properties.put(key(id), predicate);
    }
}
