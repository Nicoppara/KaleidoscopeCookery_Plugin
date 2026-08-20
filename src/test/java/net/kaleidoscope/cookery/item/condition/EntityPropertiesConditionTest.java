package net.kaleidoscope.cookery.item.condition;

import net.kaleidoscope.cookery.api.EntityProperties;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.ContextKey;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPropertiesConditionTest {
    @Test
    void 内置状态支持正反匹配() {
        EntityPropertiesCondition<Context> burning = condition(Map.of("is_on_fire", true));
        EntityPropertiesCondition<Context> notBurning = condition(Map.of("is_on_fire", false));

        assertTrue(burning.test(context(entity(20, 1))));
        assertFalse(burning.test(context(entity(0, 1))));
        assertTrue(notBurning.test(context(entity(0, 1))));
        assertFalse(notBurning.test(context(entity(20, 1))));
    }

    @Test
    void API可注册命名空间状态() {
        Key id = Key.of("test:is_special");
        assertTrue(EntityProperties.instance().register(id, entity -> entity.getEntityId() == 7));
        try {
            EntityPropertiesCondition<Context> condition = condition(Map.of("test:is_special", true));
            assertTrue(condition.test(context(entity(0, 7))));
            assertFalse(condition.test(context(entity(0, 8))));
        } finally {
            assertTrue(EntityProperties.instance().unregister(id));
        }
    }

    @Test
    void 未注册状态在解析期报错() {
        assertThrows(IllegalArgumentException.class,
                () -> condition(Map.of("test:missing", true)));
    }

    private static EntityPropertiesCondition<Context> condition(Map<String, Boolean> flags) {
        return EntityPropertiesCondition.<Context>factory().create(ConfigSection.ofRoot(Map.of(
                "predicate", Map.of("flags", flags))));
    }

    private static Context context(Entity platformEntity) {
        net.momirealms.craftengine.core.entity.Entity entity =
                (net.momirealms.craftengine.core.entity.Entity) Proxy.newProxyInstance(
                        net.momirealms.craftengine.core.entity.Entity.class.getClassLoader(),
                        new Class<?>[]{net.momirealms.craftengine.core.entity.Entity.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("platformEntity")) {
                                return platformEntity;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
        return new Context() {
            @Override
            public ContextHolder contexts() {
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<T> getOptionalParameter(ContextKey<T> key) {
                return key == DirectContextParameters.ENTITY
                        ? Optional.of((T) entity)
                        : Optional.empty();
            }
        };
    }

    private static Entity entity(int fireTicks, int entityId) {
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFireTicks" -> fireTicks;
                    case "getEntityId" -> entityId;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
