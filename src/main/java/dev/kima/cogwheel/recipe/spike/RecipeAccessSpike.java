package dev.kima.cogwheel.recipe.spike;

import dev.kima.cogwheel.CogwheelConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Phase 1a spike: which client-side recipe-access path works in 1.21.1, and at which timing point
 * does it start returning real data?
 *
 * The design doc claimed {@code ClientLevel#recipeAccess()} — that method does NOT exist in 1.21.1;
 * the actual name is {@code getRecipeManager()} on both {@code Level} and {@code ClientPacketListener}.
 * We try both paths from each trigger and log which returns non-empty recipes.
 *
 * Delete this package once Phase 1a's findings are encoded in {@code VanillaRecipeSource} /
 * {@code RebuildTrigger} for Phase 1b.
 */
public final class RecipeAccessSpike {
    private RecipeAccessSpike() {}

    public static void probe(String trigger) {
        Minecraft mc = Minecraft.getInstance();

        ClientLevel level = mc.level;
        RecipeManager fromLevel = level != null ? level.getRecipeManager() : null;

        ClientPacketListener connection = mc.getConnection();
        RecipeManager fromConnection = connection != null ? connection.getRecipeManager() : null;

        boolean sameInstance = fromLevel != null && fromLevel == fromConnection;

        int smeltingFromLevel       = countOrMinusOne(fromLevel, RecipeType.SMELTING);
        int craftingFromLevel       = countOrMinusOne(fromLevel, RecipeType.CRAFTING);
        int smeltingFromConnection  = countOrMinusOne(fromConnection, RecipeType.SMELTING);
        int craftingFromConnection  = countOrMinusOne(fromConnection, RecipeType.CRAFTING);
        int totalFromLevel          = fromLevel != null ? fromLevel.getRecipes().size() : -1;
        int totalFromConnection     = fromConnection != null ? fromConnection.getRecipes().size() : -1;

        CogwheelConstants.LOG.info(
                "[spike:{}] level={} connection={} sameInstance={} "
                        + "level{{smelt={}, craft={}, total={}}} "
                        + "connection{{smelt={}, craft={}, total={}}}",
                trigger,
                fromLevel != null ? "present" : "null",
                fromConnection != null ? "present" : "null",
                sameInstance,
                smeltingFromLevel, craftingFromLevel, totalFromLevel,
                smeltingFromConnection, craftingFromConnection, totalFromConnection);
    }

    private static int countOrMinusOne(RecipeManager rm, RecipeType<?> type) {
        if (rm == null) return -1;
        return rm.getAllRecipesFor(cast(type)).size();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RecipeType cast(RecipeType<?> type) {
        return type;
    }
}
