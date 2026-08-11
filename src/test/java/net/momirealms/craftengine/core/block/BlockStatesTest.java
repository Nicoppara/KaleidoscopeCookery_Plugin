package net.momirealms.craftengine.core.block;

import net.kaleidoscope.cookery.util.BlockStates;
import net.momirealms.craftengine.core.block.property.BooleanProperty;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.registry.Holder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStatesTest {

    @Test
    void readsPropertyFromCurrentGeneration() {
        BooleanProperty current = BooleanProperty.create("lit", true);
        ImmutableBlockState state = state(current);

        assertTrue(BlockStates.value(state, current, false));
    }

    @Test
    void resolvesSamePropertyFromPreviousReloadGeneration() {
        BooleanProperty stale = BooleanProperty.create("lit", false);
        BooleanProperty current = BooleanProperty.create("lit", true);
        ImmutableBlockState state = state(current);

        assertTrue(BlockStates.value(state, stale, false));

        ImmutableBlockState updated = BlockStates.with(state, stale, false);
        assertFalse(updated.get(current));
    }

    @Test
    void ignoresMissingOrIncompatibleProperties() {
        BooleanProperty current = BooleanProperty.create("lit", true);
        ImmutableBlockState state = state(current);
        BooleanProperty missing = BooleanProperty.create("powered", false);
        IntegerProperty wrongType = IntegerProperty.create("lit", 0, 2, 0);

        assertFalse(BlockStates.value(state, missing, false));
        assertSame(state, BlockStates.with(state, missing, true));
        assertSame(state, BlockStates.with(state, wrongType, 1));
    }

    @Test
    void rejectsValueOutsideCurrentReloadGeneration() {
        IntegerProperty stale = IntegerProperty.create("level", 0, 5, 0);
        IntegerProperty current = IntegerProperty.create("level", 0, 2, 2);
        ImmutableBlockState state = state(current);

        assertSame(state, BlockStates.with(state, stale, 5));
    }

    private static ImmutableBlockState state(Property<?> property) {
        Holder.Reference<BlockDefinition> owner = new Holder.Reference<>(new Holder.Owner<>() {}, null, null);
        BlockStateVariantProvider provider = new BlockStateVariantProvider(
                owner,
                ImmutableBlockState::new,
                Map.of(property.name(), property)
        );
        return provider.getDefaultState();
    }
}
