package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.gauge.CcbGasGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.gauge.CflFluidGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.gauge.FluidGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.widget.HelpButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Per-gauge-type look of the gauge screens.
 */
@OnlyIn(Dist.CLIENT)
public record GaugeScreenStyle(TextureRegion setFilterBg, Recipe recipe, HelpButton.ColorPalette helpPalette) {

    /** Region at ({@code u},{@code v}) of a {@code sheetW}×{@code sheetH} texture, drawn at the screen's panel size. */
    public record TextureRegion(ResourceLocation texture, int u, int v, int sheetW, int sheetH) {
        static TextureRegion of(AllGuiTextures t) {
            return new TextureRegion(t.location, t.getStartX(), t.getStartY(), 256, 256);
        }

        boolean installed() {
            return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
        }

        public void blit(GuiGraphics gfx, int x, int y, int width, int height) {
            gfx.blit(texture, x, y, u, v, width, height, sheetW, sheetH);
        }
    }

    /** Request-timer charge-up animation: {@code frames} stacked {@code width}×{@code height} frames, drawn at panel-relative ({@code x},{@code y}). */
    public record ArrowAnimation(ResourceLocation texture, int x, int y, int width, int height, int frames) {}

    /**
     * Recipe screen background and what's drawn on it. The interval label is drawn right-aligned at panel-relative
     * ({@code intervalRightX}, {@code intervalY}); {@code intervalArea} is the panel-relative hover/scroll hitbox
     * that shows and adjusts it. {@code promiseBox} is the item id of the package drawn in the open-promise slot.
     */
    public record Recipe(TextureRegion bg, ArrowAnimation arrow, Rect2i intervalArea, int intervalRightX, int intervalY,
                         @Nullable ResourceLocation promiseBox) {

        /** The open-promise slot's package: {@code promiseBox} if registered, else Create's default box. */
        public ItemStack promiseBoxStack() {
            return promiseBox != null && BuiltInRegistries.ITEM.containsKey(promiseBox)
                    ? new ItemStack(BuiltInRegistries.ITEM.get(promiseBox)) : PackageStyles.getDefaultBox();
        }
    }

    public static final GaugeScreenStyle DEFAULT = new GaugeScreenStyle(
            TextureRegion.of(AllGuiTextures.FACTORY_GAUGE_SET_ITEM),
            new Recipe(new TextureRegion(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID,
                    "textures/gui/configure_recipe.png"), 0, 0, 200, 184),
                    new ArrowAnimation(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID,
                            "textures/gui/gauge_arrow_animation.png"), 140, 47, 15, 17, 15),
                    new Rect2i(140, 47, 15, 17), 154, 58, null),
            HelpButton.ColorPalette.LOGISTICS);

    private static final Map<String, GaugeScreenStyle> BY_TYPE = Map.of(
            // Repackaged's own screen keeps Create's box; its fluid actually travels in copper bottles.
            FluidGaugeBehaviour.TYPE.id(), new GaugeScreenStyle(DEFAULT.setFilterBg,
                    new Recipe(DEFAULT.recipe.bg, DEFAULT.recipe.arrow, DEFAULT.recipe.intervalArea,
                            DEFAULT.recipe.intervalRightX, DEFAULT.recipe.intervalY,
                            ResourceLocation.fromNamespaceAndPath("repackaged", "copper_bottle_8x12")),
                    DEFAULT.helpPalette),
            CflFluidGaugeBehaviour.TYPE.id(), new GaugeScreenStyle(
                    new TextureRegion(ResourceLocation.fromNamespaceAndPath("fluidlogistics", "textures/gui/requester.png"),
                            16, 160, 256, 256),
                    // Default layout recoloured (title band only), so the default arrow, label and hitbox fit.
                    new Recipe(new TextureRegion(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID,
                            "textures/gui/fluid_configure_recipe.png"), 0, 0, 200, 184),
                            DEFAULT.recipe.arrow, DEFAULT.recipe.intervalArea,
                            DEFAULT.recipe.intervalRightX, DEFAULT.recipe.intervalY,
                            // CFL's own gauge screen shows its fluid package (falls back to Create's box where CFL lacks it)
                            ResourceLocation.fromNamespaceAndPath("fluidlogistics", "fluid_package")),
                    HelpButton.ColorPalette.FLUID),
            CcbGasGaugeBehaviour.TYPE.id(), new GaugeScreenStyle(
                    new TextureRegion(ResourceLocation.fromNamespaceAndPath("createcraftedbeginning",
                            "textures/gui/gas_factory_gauge_set_gas.png"), 0, 0, 256, 256),
                    // Arrow frame bottom-aligned on the static arrow; hitbox = arrow head + label.
                    new Recipe(new TextureRegion(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID,
                            "textures/gui/gas_configure_recipe.png"), 0, 0, 200, 184),
                            new ArrowAnimation(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID,
                                    "textures/gui/gas_gauge_arrow_animation.png"), 134, 64, 42, 22, 22),
                            new Rect2i(134, 68, 42, 19), 162, 78,
                            // CCB's own gauge screen shows this balloon (BalloonStyles.createDefaultBalloon)
                            ResourceLocation.fromNamespaceAndPath("createcraftedbeginning", "balloon_10x12")),
                    HelpButton.ColorPalette.GAS));

    /** The style for {@code gauge}'s type with uninstalled parts replaced by the default's. */
    public static GaugeScreenStyle of(@Nullable VirtualGaugeBehaviour gauge) {
        GaugeScreenStyle l = gauge == null ? DEFAULT : BY_TYPE.getOrDefault(gauge.typeId(), DEFAULT);
        if (l == DEFAULT) return l;
        return new GaugeScreenStyle(
                l.setFilterBg.installed() ? l.setFilterBg : DEFAULT.setFilterBg,
                l.recipe.bg.installed() ? l.recipe : DEFAULT.recipe,
                l.helpPalette);
    }
}
