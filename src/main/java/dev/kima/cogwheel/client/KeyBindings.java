package dev.kima.cogwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kima.cogwheel.CogwheelConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CogwheelConstants.MODID, value = Dist.CLIENT)
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.cogwheel";

    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.cogwheel.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    private KeyBindings() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR);
    }
}
