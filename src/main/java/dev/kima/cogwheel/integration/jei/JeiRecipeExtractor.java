package dev.kima.cogwheel.integration.jei;

import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.RecipeEntry;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Converts JEI's virtual recipes into Cogwheel's {@link RecipeEntry} model.
 *
 * <p>Currently handles brewing and anvil. Composting is skipped — its "output" is a composter
 * fill-level, not an item, so it doesn't fit the input→output graph cleanly. Furnace fueling is
 * also skipped — burn time is a property, not a transformation.
 *
 * <p>Imports JEI types directly; only loaded when JEI is present in the runtime classpath (via
 * @JeiPlugin annotation scan on {@link CogwheelJeiPlugin}).
 */
final class JeiRecipeExtractor {
    private static final ResourceLocation BREWING_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "brewing");
    private static final ResourceLocation ANVIL_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "anvil");

    private JeiRecipeExtractor() {}

    static List<RecipeEntry> extractAll(IJeiRuntime runtime) {
        IRecipeManager rm = runtime.getRecipeManager();
        List<RecipeEntry> result = new ArrayList<>();
        extractBrewing(rm, result);
        extractAnvil(rm, result);
        return result;
    }

    private static void extractBrewing(IRecipeManager rm, List<RecipeEntry> result) {
        rm.createRecipeLookup(RecipeTypes.BREWING).get().forEach(brewing -> {
            try {
                RecipeEntry entry = brewingToEntry(brewing);
                if (entry != null) result.add(entry);
            } catch (Throwable t) {
                // Skip any recipe we can't translate — one bad mod recipe shouldn't poison the whole index.
            }
        });
    }

    private static void extractAnvil(IRecipeManager rm, List<RecipeEntry> result) {
        rm.createRecipeLookup(RecipeTypes.ANVIL).get().forEach(anvil -> {
            try {
                RecipeEntry entry = anvilToEntry(anvil);
                if (entry != null) result.add(entry);
            } catch (Throwable t) {
                // Skip.
            }
        });
    }

    private static RecipeEntry brewingToEntry(IJeiBrewingRecipe brewing) {
        List<IngredientStack> inputs = new ArrayList<>(2);
        IngredientStack potionInput = stacksToIngredient(brewing.getPotionInputs());
        IngredientStack ingredient = stacksToIngredient(brewing.getIngredients());
        if (!potionInput.isEmpty()) inputs.add(potionInput);
        if (!ingredient.isEmpty()) inputs.add(ingredient);

        ItemStack out = brewing.getPotionOutput();
        if (out == null || out.isEmpty()) return null;

        return new RecipeEntry(
                brewing.getUid(),
                BREWING_TYPE_ID,
                inputs,
                List.of(out),
                Optional.empty(), Optional.empty(),
                0,
                Optional.empty());
    }

    private static RecipeEntry anvilToEntry(IJeiAnvilRecipe anvil) {
        List<IngredientStack> inputs = new ArrayList<>(2);
        IngredientStack left = stacksToIngredient(anvil.getLeftInputs());
        IngredientStack right = stacksToIngredient(anvil.getRightInputs());
        if (!left.isEmpty()) inputs.add(left);
        if (!right.isEmpty()) inputs.add(right);

        List<ItemStack> outputs = anvil.getOutputs();
        if (outputs == null || outputs.isEmpty()) return null;
        ItemStack first = outputs.get(0);
        if (first == null || first.isEmpty()) return null;

        return new RecipeEntry(
                anvil.getUid(),
                ANVIL_TYPE_ID,
                inputs,
                List.of(first),
                Optional.empty(), Optional.empty(),
                0,
                Optional.empty());
    }

    /**
     * JEI exposes alternatives as a flat {@code List<ItemStack>}. We collapse those to an
     * {@link IngredientStack} with the deduplicated set of items as the matchingItems list and a
     * count of 1 — the standard "one of these" semantics.
     */
    private static IngredientStack stacksToIngredient(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return IngredientStack.EMPTY;
        Set<Item> items = new LinkedHashSet<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            items.add(stack.getItem());
        }
        if (items.isEmpty()) return IngredientStack.EMPTY;
        return new IngredientStack(List.copyOf(items), Optional.empty(), 1);
    }
}
