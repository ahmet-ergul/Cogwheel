package dev.kima.cogwheel.integration.jei;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.recipe.RecipeEntry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Cogwheel's JEI plugin. Pulls "virtual" recipe categories (brewing, anvil) that vanilla doesn't
 * expose through {@code RecipeManager}, converts them to Cogwheel's {@link RecipeEntry}, and hands
 * them to {@link JeiBridge}.
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
        try {
            List<RecipeEntry> entries = JeiRecipeExtractor.extractAll(runtime);
            JeiBridge.setEntries(entries);
            CogwheelConstants.LOG.info("Cogwheel: JEI bridge contributed {} virtual recipes", entries.size());
        } catch (Throwable t) {
            CogwheelConstants.LOG.error("Cogwheel: JEI bridge extraction failed: {}", t.toString(), t);
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiBridge.setEntries(List.of());
    }
}
