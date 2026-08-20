package net.kaleidoscope.cookery.item.condition;

import net.kaleidoscope.cookery.api.EntityProperties;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.condition.ConditionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class EntityPropertiesCondition<CTX extends Context> implements Condition<CTX> {
    private final List<ExpectedProperty> properties;

    private EntityPropertiesCondition(List<ExpectedProperty> properties) {
        this.properties = properties;
    }

    @Override
    public boolean test(CTX ctx) {
        Object platformEntity = ctx.getOptionalParameter(DirectContextParameters.ENTITY)
                .map(net.momirealms.craftengine.core.entity.Entity::platformEntity)
                .orElse(null);
        if (!(platformEntity instanceof Entity entity)) {
            return false;
        }
        EntityProperties registry = EntityProperties.instance();
        for (ExpectedProperty property : this.properties) {
            Predicate<Entity> predicate = registry.predicate(property.id());
            if (predicate == null || predicate.test(entity) != property.expected()) {
                return false;
            }
        }
        return true;
    }

    public static <CTX extends Context> ConditionFactory<CTX, EntityPropertiesCondition<CTX>> factory() {
        return EntityPropertiesCondition::create;
    }

    private static <CTX extends Context> EntityPropertiesCondition<CTX> create(ConfigSection section) {
        ConfigSection predicate = section.getSection("predicate");
        ConfigSection flags = predicate == null
                ? section.getNonNullSection("flags")
                : predicate.getNonNullSection("flags");
        if (flags.size() == 0) {
            throw new IllegalArgumentException("entity_properties 至少需要一个 flag");
        }

        EntityProperties registry = EntityProperties.instance();
        List<ExpectedProperty> properties = new ArrayList<>(flags.size());
        for (String name : flags.keySet()) {
            Key id = EntityProperties.key(name);
            if (!registry.isRegistered(id)) {
                throw new IllegalArgumentException("未知的实体属性 " + id.asString());
            }
            properties.add(new ExpectedProperty(id, flags.getNonNullBoolean(name)));
        }
        return new EntityPropertiesCondition<>(List.copyOf(properties));
    }

    private record ExpectedProperty(Key id, boolean expected) {}
}
