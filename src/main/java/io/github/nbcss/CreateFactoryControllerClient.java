package io.github.nbcss;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CreateFactoryController.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = CreateFactoryController.MODID, value = Dist.CLIENT)
public class CreateFactoryControllerClient {

    /**
     * Cycles a hovered gauge's outgoing arrow-bend mode (the in-GUI action that used to be hard-wired
     * to R). Rebindable from Options ▸ Controls; defaults to R.
     */
    public static final KeyMapping CYCLE_ARROW = new KeyMapping(
            "key.createfactorycontroller.cycle_arrow",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.createfactorycontroller");

    /**
     * Drag this button on the controller canvas to pan the view. Rebindable from Options ▸ Controls;
     * defaults to the middle mouse button (the original hard-wired behaviour).
     */
    public static final KeyMapping PAN_VIEW = new KeyMapping(
            "key.createfactorycontroller.pan_view",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.createfactorycontroller");

    public CreateFactoryControllerClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {

    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_ARROW);
        event.register(PAN_VIEW);
    }
}
