package io.github.nbcss.createfactorycontroller;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import io.github.nbcss.createfactorycontroller.content.render.ProductionPatternRenderer;
import io.github.nbcss.createfactorycontroller.content.gui.screen.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ProductionOrdersTab;
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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import io.github.nbcss.createfactorycontroller.content.compat.DeployerCompat;
import net.liukrast.deployer.lib.helper.ClientRegisterHelpers;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;


@Mod(value = CreateFactoryController.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CreateFactoryController.MODID, value = Dist.CLIENT)
public class CreateFactoryControllerClient {

    /** Cycles the hovered component's connection arrow-bend mode. Rebindable from Options ▸ Controls; defaults to R. */
    public static final KeyMapping CYCLE_ARROW_MODE = new KeyMapping(
            "key.createfactorycontroller.cycle_arrow_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.createfactorycontroller");

    /** Cycles the hovered component's operation mode. Rebindable from Options ▸ Controls; defaults to T. */
    public static final KeyMapping CYCLE_OPERATION_MODE = new KeyMapping(
            "key.createfactorycontroller.cycle_operation_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F,
            "key.categories.createfactorycontroller");

    /** Starts connection mode from the hovered component. Unbound by default. */
    public static final KeyMapping START_CONNECTION = new KeyMapping(
            "key.createfactorycontroller.start_connection",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C,
            "key.categories.createfactorycontroller");

    /** Starts relocate mode for the hovered component. Unbound by default. */
    public static final KeyMapping RELOCATE_COMPONENT = new KeyMapping(
            "key.createfactorycontroller.relocate_component",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q,
            "key.categories.createfactorycontroller");

    /**
     * Drag this button on the controller canvas to pan the view. Rebindable from Options ▸ Controls;
     * defaults to the middle mouse button (the original hard-wired behaviour).
     */
    public static final KeyMapping PAN_VIEW = new KeyMapping(
            "key.createfactorycontroller.pan_view",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT,
            "key.categories.createfactorycontroller");

    public static final KeyMapping DRAG_SELECTION = new KeyMapping(
            "key.createfactorycontroller.drag_selection",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT,
            "key.categories.createfactorycontroller");

    /**
     * Toggles "always show label" (every gauge shows its count label vs. only the hovered one). Handled inside
     * {@code FactoryControllerScreen#keyPressed}, so it only fires while the controller GUI is open — never
     * in-world or on another screen. Rebindable from Options ▸ Controls; defaults to Left Alt.
     */
    public static final KeyMapping TOGGLE_ALWAYS_SHOW_LABEL = new KeyMapping(
            "key.createfactorycontroller.toggle_always_show_label",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.createfactorycontroller");

    /**
     * Held (not toggled) to put the controller GUI into "selection mode": rubber-band drag selects components and
     * left-clicking a component toggles its selection. Polled directly from the window (like {@link #PAN_VIEW}'s
     * keyboard companions) since {@link KeyMapping#isDown()} doesn't update while a screen is open. Rebindable from
     * Options ▸ Controls; defaults to Left Control.
     */
    public static final KeyMapping SELECTION_MODE = new KeyMapping(
            "key.createfactorycontroller.selection_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL,
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
            // Deployer is optional: only register the keeper TAB when it's present (this also keeps the JVM from
            // loading ProductionOrdersTab, which extends a Deployer class). Without Deployer the Production Orders
            // page is reached via a button on the Stock Keeper instead — see StockKeeperRequestScreenMixin.
            if (DeployerCompat.isLoaded())
                ClientRegisterHelpers.registerStockKeeperTab(ProductionOrdersTab::new);
            // Shift/Ctrl item summary for the controller, driven by Create's tooltip pipeline (reads the
            // <item>.tooltip.summary / .condition*/.behaviour* lang keys). Reuses Create's ItemDescription.
            Item controllerItem = CreateFactoryController.FACTORY_CONTROLLER_ITEM.get();
            TooltipModifier.REGISTRY.register(controllerItem,
                new ItemDescription.Modifier(controllerItem, FontHelper.Palette.STANDARD_CREATE));
        });
    }

    @SubscribeEvent
    static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Drop the session-only controller camera cache when leaving a world/server.
        FactoryControllerScreen.clearViewCache();
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_ARROW_MODE);
        event.register(CYCLE_OPERATION_MODE);
        event.register(START_CONNECTION);
        event.register(RELOCATE_COMPONENT);
        event.register(PAN_VIEW);
        event.register(DRAG_SELECTION);
        event.register(TOGGLE_ALWAYS_SHOW_LABEL);
        event.register(SELECTION_MODE);
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
