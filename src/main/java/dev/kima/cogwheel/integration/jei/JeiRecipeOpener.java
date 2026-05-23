package dev.kima.cogwheel.integration.jei;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.source.RebuildTrigger;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Opens JEI's full recipe screen focused on the output item of a given Cogwheel recipe entry. Used
 * by the recipe picker's right-click gesture so users can browse the recipe at full size in JEI's
 * own UI rather than being limited to the picker's compact row.
 *
 * <p>JEI 19.27's public {@code IRecipesGui} API doesn't expose a "land directly on this exact
 * recipe instance" hook — its {@code show(IFocus)} navigates to all recipes matching the focus.
 * Focusing on the recipe's primary output as {@link RecipeIngredientRole#OUTPUT} reliably lands
 * the user in the right category with the right recipe in view.
 *
 * <p>Imports JEI types — only class-loaded from {@link JeiBridge#showRecipeInJei} which gates on
 * {@code jeiRuntime != null}.
 */
final class JeiRecipeOpener {
    private JeiRecipeOpener() {}

    /** Open JEI focused on the item as either OUTPUT (R / recipes-producing) or INPUT (U /
     *  recipes-using). */
    @SuppressWarnings("removal")
    static boolean tryShowFocus(Object runtimeObj, ItemStack stack, boolean producing) {
        try {
            IJeiRuntime runtime = (IJeiRuntime) runtimeObj;
            IIngredientManager ingredientManager = runtime.getIngredientManager();
            Optional<?> typed = ingredientManager.createTypedIngredient(
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack);
            if (typed.isEmpty()) return false;
            IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
            RecipeIngredientRole role = producing ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
            @SuppressWarnings({"unchecked", "rawtypes"})
            IFocus<?> focus = focusFactory.createFocus(role,
                    (mezz.jei.api.ingredients.ITypedIngredient) typed.get());
            runtime.getRecipesGui().show(focus);
            return true;
        } catch (Throwable t) {
            CogwheelConstants.LOG.debug("JeiRecipeOpener.tryShowFocus failed for {}: {}", stack, t.toString());
            return false;
        }
    }

    @SuppressWarnings("removal") // IIngredientManager#createTypedIngredient is marked for removal in
    // newer JEI builds but is still the documented API in 19.27.x for 1.21.1. Swap when we bump JEI.
    static boolean tryShow(Object runtimeObj, ResourceLocation typeId, ResourceLocation entryId) {
        try {
            IJeiRuntime runtime = (IJeiRuntime) runtimeObj;

            RecipeEntry entry = RebuildTrigger.index().byId(entryId);
            if (entry == null || entry.outputs().isEmpty()) {
                CogwheelConstants.LOG.debug("JeiRecipeOpener: no entry / no outputs for {}", entryId);
                return false;
            }
            ItemStack focusItem = entry.outputs().get(0);
            if (focusItem.isEmpty()) return false;

            IIngredientManager ingredientManager = runtime.getIngredientManager();
            Optional<?> typed = ingredientManager.createTypedIngredient(
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, focusItem);
            if (typed.isEmpty()) {
                CogwheelConstants.LOG.debug("JeiRecipeOpener: ITypedIngredient lookup empty for {}", focusItem);
                return false;
            }

            IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
            @SuppressWarnings({"unchecked", "rawtypes"})
            IFocus<?> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT,
                    (mezz.jei.api.ingredients.ITypedIngredient) typed.get());

            IRecipesGui recipesGui = runtime.getRecipesGui();
            recipesGui.show(focus);
            return true;
        } catch (Throwable t) {
            CogwheelConstants.LOG.debug("JeiRecipeOpener: failed for {} ({}): {}", entryId, typeId, t.toString());
            return false;
        }
    }
}
