package dev.kima.cogwheel.recipe.adapter.create;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.kima.cogwheel.recipe.FluidIngredientStack;
import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.adapter.RecipeAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.Optional;

/**
 * Adapter for Create's {@code ProcessingRecipe} family — mixing, milling, pressing, crushing,
 * cutting, deploying, filling, emptying, compacting, haunting, splashing, sandpaper polishing,
 * basin, and any future Create recipe that extends {@code ProcessingRecipe}.
 *
 * <p>Captures Create-only metadata that {@link dev.kima.cogwheel.recipe.adapter.GenericAdapter}
 * loses: processing duration (ticks), per-recipe rollable results with chance (we keep the items,
 * dropping the chance — Phase 9 polish), and fluid inputs/outputs (Create / NeoForge mB units).
 *
 * <p>Heat condition ({@code getRequiredHeat()}) is currently ignored — design note: surface as a
 * node chip ("🔥 superheated") in Phase 9 once we have a slot for ad-hoc recipe metadata on
 * {@link RecipeEntry}.
 *
 * <p>Sequenced Assembly is deliberately NOT handled here — its multi-stage shape doesn't fit the
 * single-entry {@code RecipeEntry} cleanly. Falls through to the generic adapter (loses the
 * sub-recipe sequence; the recipe still appears in the index by output).
 */
public final class ProcessingRecipeAdapter implements RecipeAdapter {
    @Override
    public boolean matches(RecipeType<?> type) {
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id != null && "create".equals(id.getNamespace());
    }

    @Override
    public Optional<RecipeEntry> adapt(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        if (!(holder.value() instanceof ProcessingRecipe<?, ?> processing)) {
            // create:mechanical_crafting, create:sequenced_assembly, etc. — let the chain continue
            // so GenericAdapter picks them up with imperfect but non-empty data.
            return Optional.empty();
        }
        ResourceLocation id = holder.id();
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(processing.getType());
        if (typeId == null) {
            return Optional.empty();
        }

        List<IngredientStack> inputs = processing.getIngredients().stream()
                .map(IngredientStack::fromIngredient)
                .filter(stack -> !stack.isEmpty())
                .toList();

        List<ItemStack> outputs = processing.getRollableResults().stream()
                .map(ProcessingOutput::getStack)
                .filter(stack -> !stack.isEmpty())
                .toList();

        Optional<FluidIngredientStack> inputFluid = firstFluidInput(processing.getFluidIngredients());
        Optional<FluidStack> outputFluid = firstFluidOutput(processing.getFluidResults());

        return Optional.of(new RecipeEntry(
                id, typeId,
                inputs, outputs,
                inputFluid, outputFluid,
                processing.getProcessingDuration(),
                Optional.empty()));
    }

    private static Optional<FluidIngredientStack> firstFluidInput(NonNullList<SizedFluidIngredient> ingredients) {
        if (ingredients.isEmpty()) return Optional.empty();
        SizedFluidIngredient first = ingredients.get(0);
        FluidStack[] fluids = first.getFluids();
        if (fluids.length == 0) return Optional.empty();
        FluidStack representative = fluids[0].copyWithAmount(first.amount());
        return Optional.of(new FluidIngredientStack(representative));
    }

    private static Optional<FluidStack> firstFluidOutput(NonNullList<FluidStack> outputs) {
        if (outputs.isEmpty() || outputs.get(0).isEmpty()) return Optional.empty();
        return Optional.of(outputs.get(0));
    }
}
