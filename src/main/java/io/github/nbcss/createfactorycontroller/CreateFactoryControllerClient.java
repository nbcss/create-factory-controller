package io.github.nbcss.createfactorycontroller;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import io.github.nbcss.createfactorycontroller.content.render.ProductionPatternRenderer;
import io.github.nbcss.createfactorycontroller.content.gui.ProductionOrdersTab;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.liukrast.deployer.lib.helper.ClientRegisterHelpers;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;


@Mod(value = CreateFactoryController.MODID, dist = Dist.CLIENT)
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

    /**
     * Toggles "full overlay" (every gauge shows its count label vs. only the hovered one). Handled inside
     * {@code FactoryControllerScreen#keyPressed}, so it only fires while the controller GUI is open — never
     * in-world or on another screen. Rebindable from Options ▸ Controls; defaults to Left Alt.
     */
    public static final KeyMapping TOGGLE_FULL_OVERLAY = new KeyMapping(
            "key.createfactorycontroller.toggle_full_overlay",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.createfactorycontroller");

    public CreateFactoryControllerClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientRegisterHelpers.registerStockKeeperTab(ProductionOrdersTab::new);
            // Shift/Ctrl item summary for the controller, driven by Create's tooltip pipeline (reads the
            // <item>.tooltip.summary / .condition*/.behaviour* lang keys). Reuses Create's ItemDescription.
            Item controllerItem = CreateFactoryController.FACTORY_CONTROLLER_ITEM.get();
            TooltipModifier.REGISTRY.register(controllerItem,
                new ItemDescription.Modifier(controllerItem, FontHelper.Palette.STANDARD_CREATE));
        });
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_ARROW);
        event.register(PAN_VIEW);
        event.register(TOGGLE_FULL_OVERLAY);
    }

    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private final BlockEntityWithoutLevelRenderer renderer = new ProductionPatternRenderer();
            @Override
            public @NotNull BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        }, CreateFactoryController.PRODUCTION_PATTERN.get());
    }
}
