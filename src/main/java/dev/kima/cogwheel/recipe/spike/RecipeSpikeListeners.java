package dev.kima.cogwheel.recipe.spike;

import dev.kima.cogwheel.CogwheelConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;

/**
 * Triggers the Phase 1a {@link RecipeAccessSpike} at the two automatic timing points:
 * world login and post-recipe-sync. The third point (EditorScreen open) is wired directly into the
 * screen's init(); the fourth (manual on-demand) is the {@code /cogwheel debug recipes} command.
 */
@EventBusSubscriber(modid = CogwheelConstants.MODID, value = Dist.CLIENT)
public final class RecipeSpikeListeners {
    private RecipeSpikeListeners() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        RecipeAccessSpike.probe("LoggingIn");
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        RecipeAccessSpike.probe("RecipesUpdated");
    }
}
