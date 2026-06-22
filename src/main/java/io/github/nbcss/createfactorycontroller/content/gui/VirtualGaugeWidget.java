package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.packet.GaugeSetItemPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveComponentPacket;
import io.github.nbcss.createfactorycontroller.content.render.FluidGuiRender;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A single gauge on the canvas: renders it, builds its hover tooltip, and handles clicks on it.
 *
 * <p>Created once per gauge and kept in {@link FactoryControllerScreen}'s position-indexed store
 * (rebuilt only when the panel state syncs), not per frame. All drawing is in canvas-world
 * coordinates — the screen pushes a world→screen pose around the whole canvas, so a cell is just
 * {@code CELL} world px at {@code (position * CELL)} and this class never deals with zoom/pan.</p>
 */
@OnlyIn(Dist.CLIENT)
public record VirtualGaugeWidget(VirtualGaugeBehaviour behaviour) implements VirtualComponentWidget {

    private static final int CELL = 16;

    /**
     * A dispatched package has a 3×3 (9-slot) buffer; a request that needs more can't be carried.
     */
    private static final int MAX_PACKAGE_SLOTS = 9;

    /**
     * Status indicator light, drawn top-right within the cell and tinted by gauge state.
     */
    private static final ResourceLocation INDICATOR =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/factory_gauge/indicator");

    // Indicator bulb colours (Create's factory_panel light models): green normally, red when the gauge
    // is misconfigured (missing address) or redstone-powered. Brightness tracks the bulb glow, never
    // fully dark so an understocked gauge still reads as a dim ("dark green") light.
    private static final int BULB_GREEN = 0x9EFF7F;
    private static final int BULB_RED = 0xFF5555;
    private static final float BULB_MIN_BRIGHTNESS = 0.35f;

    public VirtualPanelPosition position() {
        return behaviour.position();
    }

    // ── Render ───────────────────────────────────────────────────────────────

    /**
     * Back layer: the gauge's {@code back} sprite. Rendered before connections so the arrows
     * (whose heads tuck into the cell) sit above the back but below the front.
     */
    @Override
    public void renderBack(GuiGraphics gfx) {
        int x0 = behaviour.position().x() * CELL;
        int y0 = behaviour.position().y() * CELL;
        gfx.blitSprite(behaviour.getTexture().withSuffix("/back"), x0, y0, CELL, CELL);
    }

    /**
     * Front layer: the gauge's {@code front} sprite plus the status dot and selection outline.
     * Rendered after connections so the front frame covers the arrowheads. (Hover feedback is the
     * {@code target} reticle drawn by the screen.)
     */
    @Override
    public void renderFront(GuiGraphics gfx, double mouseX, double mouseY, float glow, boolean showCount) {
        int x0 = behaviour.position().x() * CELL;
        int y0 = behaviour.position().y() * CELL;

        gfx.blitSprite(behaviour.getTexture().withSuffix("/front"), x0, y0, CELL, CELL);

        if (!behaviour.filter.isEmpty()) {
            gfx.pose().pushPose();
            gfx.pose().translate(x0 + 4.0, y0 + 4.0, 0);   // 4-px inset centres the half-size icon
            gfx.pose().scale(0.5f, 0.5f, 0.5f);            // uniform so item lighting stays correct
            FluidGuiRender.filterIcon(gfx, behaviour.filter, 0, 0);
            gfx.pose().popPose();
        }

        // Indicator bulb — shown once the gauge is active.
        if (behaviour.isActive()) {
            boolean invalid = behaviour.isMissingAddress() || behaviour.redstonePowered;
            int base = invalid ? BULB_RED : BULB_GREEN;
            float b = BULB_MIN_BRIGHTNESS + (1f - BULB_MIN_BRIGHTNESS) * Mth.clamp(glow, 0f, 1f);
            RenderSystem.enableBlend();
            gfx.setColor(((base >> 16) & 0xFF) / 255f * b, ((base >> 8) & 0xFF) / 255f * b,
                    (base & 0xFF) / 255f * b, 0.9f);
            gfx.blitSprite(INDICATOR, x0, y0, CELL, CELL);
            gfx.setColor(1f, 1f, 1f, 1f);
        }

        // Count overlay — shown for every gauge in "full overlay" mode, otherwise only for the hovered one.
        Component label = showCount ? behaviour.getCountLabel() : Component.empty();
        if (!label.getString().isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int w = font.width(label);
            float scale = 0.5f;

            gfx.pose().pushPose();
            gfx.pose().translate(x0 + CELL - 1, y0 + CELL - 1, 200);
            gfx.pose().scale(scale, scale, 1);
            // Full 8-direction outline, like Create's in-world value-box labels (not a drop shadow).
            Matrix4f matrix = gfx.pose().last().pose();
            font.drawInBatch8xOutline(label.getVisualOrderText(), -w, -font.lineHeight,
                    0xFFFFFFFF, 0x000000,
                    matrix, gfx.bufferSource(), LightTexture.FULL_BRIGHT);
            gfx.flush();
            gfx.pose().popPose();
        }
    }

    // ── Tooltip ──────────────────────────────────────────────────────────────

    /** Thousands-grouped count, e.g. {@code 1,000,000} (always comma-grouped, locale-independent). */
    private static String formatCount(int value) {
        return String.format(java.util.Locale.US, "%,d", value);
    }

    /** Stock/promise amount for this gauge: millibuckets in the gauge's own unit (mB/B) for a fluid filter,
     *  else a grouped item count. */
    private String formatAmount(int value) {
        return FluidCompat.isFluidFilter(behaviour.filter)
                ? behaviour.unit.formatInUnit(value)
                : formatCount(value);
    }

    /**
     * Hover tooltip, mirroring Create's factory-panel label/tip (and value-box header colour): the
     * filtered item (or "New factory task") in yellow, the interaction hint in white — "Click with
     * item to set" until a filter exists, then "Click to configure" — and, when inactive, the red
     * reason (no target amount / missing address), matching {@code FactoryPanelBehaviour#getLabel}.
     */
    @Override
    public List<Component> getTooltip(FactoryControllerMenu menu, boolean selected) {
        List<Component> lines = new ArrayList<>();
        if (behaviour.filter.isEmpty()) {
            lines.add(CreateLang.translate("factory_panel.new_factory_task").color(0xFBDC7D).component());
        }else{
            var title = CreateLang.text(FluidCompat.filterName(behaviour.filter).getString()).color(0xFBDC7D);
            if (behaviour.ignoreData) {
                String label = " (" + CreateLang.translate("gui.filter.ignore_data").string() + ")";
                title.add(Component.literal(label));
            }
            lines.add(title.component());
        }
        if (!behaviour.filter.isEmpty()) {
            lines.add(Component.translatable("createfactorycontroller.gui.in_stock",
                            Component.literal(behaviour.isInfiniteStock() ? "∞" : formatAmount(behaviour.stockLevel))
                                    .withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("createfactorycontroller.gui.promised",
                            Component.literal((behaviour.promisedCount > 0 ? "+" : "") + formatAmount(behaviour.promisedCount))
                                    .withStyle(behaviour.promisedCount > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(selected
                ? Component.translatable("createfactorycontroller.gui.drag_to_relocate").withStyle(ChatFormatting.GRAY)
                : (behaviour.filter.isEmpty()
                        ? CreateLang.translate("logistics.filter.click_to_set")
                        : CreateLang.translate("factory_panel.click_to_configure"))
                        .style(ChatFormatting.GRAY).component());
        lines.add(Component.translatable("createfactorycontroller.gui.action_remove_component")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (!behaviour.targetedBy().isEmpty() && !behaviour.isActive())
            lines.add(CreateLang.translate("gui.factory_panel.no_target_amount_set").style(ChatFormatting.RED).component());
        else if (behaviour.isMissingAddress())
            lines.add(CreateLang.translate("gui.factory_panel.address_missing").style(ChatFormatting.RED).component());
        else if (behaviour.redstonePowered)
            lines.add(Component.translatable("createfactorycontroller.gui.gauge_status.redstone_link_paused").withStyle(ChatFormatting.RED));
        return lines;
    }

    // ── Interaction ────────────────────────────────────────────────────────────

    /**
     * Shift-click: remove this gauge from the board (server refunds the item in survival).
     */
    public void remove(FactoryControllerScreen screen) {
        PacketDistributor.sendToServer(
                new RemoveComponentPacket(screen.getMenu().controllerPos, behaviour.position()));
    }

    /**
     * Left/right-click in normal mode: a filter-less gauge takes the carried item as its filter (or
     * opens the set-item overlay with an empty cursor); a configured gauge opens the recipe overlay
     * with an empty cursor. Always consumes the click.
     */
    @Override
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button) {
        FactoryControllerMenu menu = screen.getMenu();
        if (behaviour.filter.isEmpty()) {
            if (carried.isEmpty()) {
                screen.clearSelection();   // entering an overlay clears the selection
                Minecraft.getInstance().setScreen(new SetItemScreen(screen, behaviour.position()));
            } else
                PacketDistributor.sendToServer(
                        new GaugeSetItemPacket(menu.controllerPos, behaviour.position(), filterFromCarried(carried, button), false));
        } else if (carried.isEmpty()) {
            screen.clearSelection();   // entering an overlay clears the selection
            Minecraft.getInstance().setScreen(new ConfigureRecipeScreen(screen, behaviour.position()));
        }
        return true;
    }

    /**
     * The filter a carried item sets on an empty gauge: the item itself on left-click, or (with a
     * fluid-logistics addon installed) the stored fluid as a fluid filter on right-click of a filled container.
     */
    private static ItemStack filterFromCarried(ItemStack carried, int button) {
        if (FluidCompat.isLoaded() && button == 1) {
            var fluid = FluidCompat.fluidInContainer(carried);
            if (!fluid.isEmpty()) return FluidCompat.makeFluidFilter(fluid);
        }
        return carried.copy();
    }
}
