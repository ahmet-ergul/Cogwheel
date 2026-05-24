package dev.kima.cogwheel.recipe.adapter.create;

import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Calls Create's own {@link ManualApplicationRecipe#asDeploying} converter so we can hand
 * brass-casing-style "right-click apply" recipes to JEI's {@code DeployingCategory}, which
 * hard-casts to {@link DeployerApplicationRecipe}. Without this round-trip our mirror entries
 * crash with a ClassCastException when rendered through the deploying category.
 *
 * <p>Imports Create types directly — only call this from a code path gated on
 * {@code ModList.isLoaded("create")} so the class is never loaded in a Create-less environment.
 */
public final class CreateDeployerJeiWrapper {
    private CreateDeployerJeiWrapper() {}

    /**
     * Convert a {@code RecipeHolder<ManualApplicationRecipe>} into Create's deployer-compatible
     * {@code RecipeHolder<DeployerApplicationRecipe>}. Returns {@code null} if the input isn't a
     * manual application recipe (the caller should fall back to its actual category in that case).
     */
    public static RecipeHolder<?> wrapManualAsDeploying(RecipeHolder<?> holder) {
        if (holder == null || !(holder.value() instanceof ManualApplicationRecipe)) {
            return null;
        }
        try {
            return ManualApplicationRecipe.asDeploying(holder);
        } catch (Throwable t) {
            // Defensive — Create could change the conversion semantics in a future version.
            return null;
        }
    }
}
