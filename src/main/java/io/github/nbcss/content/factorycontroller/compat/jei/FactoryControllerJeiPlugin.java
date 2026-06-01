package io.github.nbcss.content.factorycontroller.compat.jei;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.gui.SetItemScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
    }

    /** Offers the set-item screen's ghost slot as a drop target for item ingredients. */
    private static class SetItemGhostHandler implements IGhostIngredientHandler<SetItemScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(SetItemScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            Optional<ItemStack> stack = ingredient.getItemStack();
            if (stack.isEmpty()) return targets;          // items only
            Rect2i area = screen.ghostSlotArea();
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I dropped) {
                    screen.setGhostFromJei(stack.get());
                }
            });
            return targets;
        }

        @Override
        public void onComplete() {}
    }
}
