package io.github.nbcss.createfactorycontroller.content.compat.jei;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.gui.ConfigureRedstoneLinkScreen;
import io.github.nbcss.createfactorycontroller.content.gui.SetItemScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI integration. Only loaded when JEI is present (JEI scans for {@code @JeiPlugin}); references
 * only JEI API types (a {@code compileOnly} dependency), so a JEI-less client never classloads this.
 *
 * <p>Registers a ghost-ingredient handler on {@link SetItemScreen} so the player can drag an item
 * from JEI's list straight onto the gauge's ghost filter slot — no real item required.</p>
 */
@JeiPlugin
public class FactoryControllerJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(SetItemScreen.class, new SetItemGhostHandler());
        // Keep the item list clear of the decorative gauge that protrudes from the panel.
        registration.addGuiContainerHandler(SetItemScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(SetItemScreen screen) {
                return screen.extraGuiAreas();
            }
        });
        registration.addGhostIngredientHandler(ConfigureRedstoneLinkScreen.class, new RedstoneLinkGhostHandler());
    }

    /** Offers the two frequency slots (Red/Blue) of the redstone-link config screen as JEI drop targets.
     *  Items only — redstone-link frequencies are plain items (no fluid-filter conversion). */
    private static class RedstoneLinkGhostHandler implements IGhostIngredientHandler<ConfigureRedstoneLinkScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(ConfigureRedstoneLinkScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            Optional<ItemStack> stack = ingredient.getItemStack();
            if (stack.isPresent() && !stack.get().isEmpty()) {
                ItemStack freq = stack.get();
                targets.add(dropTarget(screen.redSlotArea(), () -> screen.setRedFromJei(freq)));
                targets.add(dropTarget(screen.blueSlotArea(), () -> screen.setBlueFromJei(freq)));
            }
            return targets;
        }

        @Override
        public void onComplete() {}
    }

    /**
     * Offers the set-item screen's ghost slot as a drop target. Item ingredients set the item filter; with a
     * fluid-logistics addon installed, a dragged fluid is converted to a fluid filter (the addon's wrapper stack).
     */
    private static class SetItemGhostHandler implements IGhostIngredientHandler<SetItemScreen> {
        @Override
        public <I> @NotNull List<Target<I>> getTargetsTyped(SetItemScreen screen,
                                                            @NotNull ITypedIngredient<I> ingredient,
                                                            boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            Rect2i area = screen.ghostSlotArea();

            if (screen.acceptsJeiFluids()) {
                Optional<FluidStack> fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
                if (fluid.isPresent() && !fluid.get().isEmpty()) {
                    FluidStack dropped = fluid.get();
                    targets.add(dropTarget(area, () -> screen.setFluidFromJei(dropped)));
                }
            }

            Optional<ItemStack> stack = ingredient.getItemStack();
            if (screen.acceptsJeiItems() && stack.isPresent()) {
                ItemStack filter = stack.get();
                targets.add(dropTarget(area, () -> screen.setGhostFromJei(filter)));
                return targets;
            }
            return targets;
        }

        @Override
        public void onComplete() {}
    }

    /** A drop target over {@code area} that runs {@code action} on accept (ignoring the dropped value, which
     *  we've already captured as a concrete filter stack). */
    private static <I> IGhostIngredientHandler.Target<I> dropTarget(Rect2i area, Runnable action) {
        return new IGhostIngredientHandler.Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I dropped) {
                action.run();
            }
        };
    }
}
