package dev.kima.cogwheel.recipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;

/**
 * Cogwheel's normalized, in-memory view of a single recipe — the unit the graph editor and solver
 * work with. Adapter implementations translate vanilla and mod-specific recipe types into this
 * shape.
 *
 * @param id                  Recipe identifier (e.g. {@code minecraft:iron_ingot_from_iron_ore}).
 * @param typeId              Recipe type identifier (e.g. {@code minecraft:smelting}, {@code create:mixing}).
 * @param inputs              Ordered list of input slots. May be tag-backed (see {@link IngredientStack}).
 * @param outputs             Primary output and byproducts. Empty if the recipe produces no items.
 * @param inputFluid          Optional fluid input (Create / mod recipes).
 * @param outputFluid         Optional fluid output (Create / mod recipes).
 * @param processingTimeTicks Recipe duration, in ticks. {@code 0} for instant / unknown.
 * @param displayName         Optional display name used by the recipe picker UI.
 */
public record RecipeEntry(
        ResourceLocation id,
        ResourceLocation typeId,
        List<IngredientStack> inputs,
        List<ItemStack> outputs,
        Optional<FluidIngredientStack> inputFluid,
        Optional<FluidStack> outputFluid,
        int processingTimeTicks,
        Optional<Component> displayName
) {}
