package dev.kima.cogwheel.recipe;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Cogwheel's normalized view of a single recipe input slot.
 *
 * <p>Wraps a vanilla {@link Ingredient} plus a required count. In 1.21.1 vanilla, {@code Ingredient}
 * is final and exposes its alternatives via an internal {@code Value[]} (one of {@code ItemValue} or
 * {@code TagValue}) and the materialized {@link Ingredient#getItems()} array. We extract:
 *
 * <ul>
 *   <li>the concrete matching items (for solver / display);</li>
 *   <li>an optional {@link TagKey} when at least one alternative is tag-backed (for UI display —
 *       so the port can say "#c:plates/iron" rather than enumerating every plate);</li>
 *   <li>a count, defaulting to 1 for vanilla recipes. Mod adapters that wrap NeoForge's
 *       {@code SizedIngredient} (Create, etc.) are expected to provide the real count.</li>
 * </ul>
 *
 * <p>Fluids and Create-style sized inputs are handled separately — see {@link FluidIngredientStack}
 * and the Create-specific adapter in Phase 2.
 */
public record IngredientStack(
        List<Item> matchingItems,
        Optional<TagKey<Item>> tag,
        int count
) {
    public static final IngredientStack EMPTY = new IngredientStack(List.of(), Optional.empty(), 0);

    public boolean isEmpty() {
        return count <= 0 || matchingItems.isEmpty();
    }

    /**
     * Builds an {@code IngredientStack} from a vanilla {@link Ingredient} with the given count.
     * Falls back to {@link #EMPTY} for empty/no-items ingredients.
     */
    public static IngredientStack fromIngredient(Ingredient ingredient, int count) {
        if (ingredient == null || ingredient.isEmpty() || ingredient.hasNoItems()) {
            return EMPTY;
        }
        ItemStack[] stacks = ingredient.getItems();
        List<Item> items = Arrays.stream(stacks)
                .map(ItemStack::getItem)
                .distinct()
                .toList();

        Optional<TagKey<Item>> tag = findTag(ingredient);

        return new IngredientStack(items, tag, count);
    }

    /**
     * Convenience: count = 1.
     */
    public static IngredientStack fromIngredient(Ingredient ingredient) {
        return fromIngredient(ingredient, 1);
    }

    /**
     * Walks the ingredient's {@code Value[]} looking for a {@code TagValue}. Returns the first tag
     * found, if any. We don't bother trying to reconstruct the tag from the materialized item list
     * — once Mojang resolves a tag into items, the tag identity is gone unless we read it from the
     * Value directly.
     *
     * <p>The {@code Value} interface and its {@code TagValue}/{@code ItemValue} record subclasses
     * are package-private in vanilla, so we identify them by simple-name. Brittle, but the
     * alternative is a mixin / access transformer for what is currently a UI-display nicety.
     */
    private static Optional<TagKey<Item>> findTag(Ingredient ingredient) {
        Ingredient.Value[] values = ingredient.getValues();
        for (Ingredient.Value value : values) {
            if (value == null) continue;
            String simple = value.getClass().getSimpleName();
            if (!"TagValue".equals(simple)) continue;
            // The TagValue record's accessor is `tag()` per Mojang's class shape, but since the
            // type is package-private we can't call it directly. Reflection is fine here — this
            // is one-off display metadata, not a hot path.
            try {
                var method = value.getClass().getDeclaredMethod("tag");
                method.setAccessible(true);
                Object result = method.invoke(value);
                if (result instanceof TagKey<?> tk) {
                    @SuppressWarnings("unchecked")
                    TagKey<Item> itemTag = (TagKey<Item>) tk;
                    return Optional.of(itemTag);
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through — return empty.
            }
        }
        return Optional.empty();
    }
}
