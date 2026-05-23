package dev.kima.cogwheel;

import dev.kima.cogwheel.recipe.adapter.CookingAdapter;
import dev.kima.cogwheel.recipe.adapter.RecipeAdapterRegistry;
import dev.kima.cogwheel.recipe.adapter.create.CreateAdapterBundle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = CogwheelConstants.MODID, dist = Dist.CLIENT)
public class Cogwheel {
    public Cogwheel(IEventBus modEventBus) {
        modEventBus.addListener(Cogwheel::onClientSetup);
        CogwheelConstants.LOG.info("Cogwheel: mod constructed");
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        registerAdapters();
        CogwheelConstants.LOG.info("Cogwheel: client setup complete");
    }

    private static void registerAdapters() {
        RecipeAdapterRegistry registry = RecipeAdapterRegistry.get();
        registry.register(new CookingAdapter());
        if (ModList.get().isLoaded("create")) {
            CogwheelConstants.LOG.info("Cogwheel: Create detected — registering Create adapters");
            CreateAdapterBundle.register(registry);
        } else {
            CogwheelConstants.LOG.info("Cogwheel: Create not present — skipping Create adapters");
        }
        // GenericAdapter is the always-last fallback inside the registry — no need to register here.
    }
}
