package dev.kima.cogwheel.client;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.client.ui.EditorScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CogwheelConstants.MODID, value = Dist.CLIENT)
public final class CogwheelClient {
    private CogwheelClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (KeyBindings.OPEN_EDITOR.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new EditorScreen());
            }
        }
    }
}
