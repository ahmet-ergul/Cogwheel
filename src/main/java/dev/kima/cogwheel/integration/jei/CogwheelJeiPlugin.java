package dev.kima.cogwheel.integration.jei;

import dev.kima.cogwheel.CogwheelConstants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Cogwheel's JEI plugin. On runtime-available, walks every JEI category via
 * {@link JeiRecipeEnumerator} so that JEI's synthetic categories (Automated Packing, Bulk Washing,
 * Mechanical Crafter, anvil, brewing, …) become Cogwheel recipe nodes alongside the recipes
 * sourced from the vanilla {@code RecipeManager}.
 *
 * <p>This class is loaded only when JEI's annotation scanner discovers it — never from main mod
 * code paths. If JEI is absent, the entire {@code integration.jei} package is invisible to the JVM.
 */
@JeiPlugin
public final class CogwheelJeiPlugin implements IModPlugin {
    public static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(CogwheelConstants.MODID, "jei_bridge");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiBridge.setRuntime(runtime);
        // Defer enumeration to the next client tick: at this point JEI is in the SEND_RUNTIME
        // callback phase, but its Internal singleton (which categories like minecraft:grindstone
        // need to lay out slots) is only assigned AFTER all plugin callbacks return. Calling
        // createRecipeLayoutDrawable now spams "Jei Client Configs have not been created yet"
        // through every category that touches Internal.getJeiRuntime() inside setRecipe.
        Minecraft.getInstance().execute(() -> runEnumeration(runtime));
    }

    private static void runEnumeration(IJeiRuntime runtime) {
        try {
            JeiRecipeEnumerator.Result result = JeiRecipeEnumerator.extractAll(runtime);
            JeiBridge.setJeiSourcedRecipes(result.recipeByEntryId());
            JeiBridge.setEntries(result.entries());
            CogwheelConstants.LOG.info("Cogwheel: JEI bridge contributed {} entries", result.entries().size());
        } catch (Throwable t) {
            CogwheelConstants.LOG.error("Cogwheel: JEI bridge extraction failed: {}", t.toString(), t);
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiBridge.setRuntime(null);
        JeiBridge.setEntries(List.of());
        JeiBridge.setJeiSourcedRecipes(Map.of());
    }
}
