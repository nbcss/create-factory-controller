package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.VirtualGaugeBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualPanelConnection;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import io.github.nbcss.content.factorycontroller.packet.GaugeSetItemPacket;
import io.github.nbcss.content.factorycontroller.packet.RemoveComponentPacket;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single gauge on the canvas: renders it, builds its hover tooltip, and handles clicks on it.
 *
 * <p>Created once per gauge and kept in {@link FactoryControllerScreen}'s position-indexed store
 * (rebuilt only when the panel state syncs), not per frame. All drawing is in canvas-world
 * coordinates — the screen pushes a world→screen pose around the whole canvas, so a cell is just
 * {@code CELL} world px at {@code (position * CELL)} and this class never deals with zoom/pan.</p>
 */
@OnlyIn(Dist.CLIENT)
public record VirtualGaugeWidget(VirtualGaugeBehaviour behaviour) {

    private static final int CELL = 16;

    /**
     * A dispatched package has a 3×3 (9-slot) buffer; a request that needs more can't be carried.
     */
    private static final int MAX_PACKAGE_SLOTS = 9;

    /**
     * Status indicator light, drawn top-right within the cell and tinted by gauge state.
     */
    private static final ResourceLocation INDICATOR =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/gauge_indicator");

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
    public void renderFront(GuiGraphics gfx, float glow) {
        int x0 = behaviour.position().x() * CELL;
        int y0 = behaviour.position().y() * CELL;

        gfx.blitSprite(behaviour.getTexture().withSuffix("/front"), x0, y0, CELL, CELL);

        if (!behaviour.filter.isEmpty()) {
            gfx.pose().pushPose();
            gfx.pose().translate(x0 + 4.0, y0 + 4.0, 0);   // 4-px inset centres the half-size icon
            gfx.pose().scale(0.5f, 0.5f, 0.5f);            // uniform so item lighting stays correct
            gfx.renderItem(behaviour.filter, 0, 0);
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

        // render count overlay
        Component label = behaviour.getCountLabel();
        if (!label.getSiblings().isEmpty()) {
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

    /**
     * Hover tooltip, mirroring Create's factory-panel label/tip (and value-box header colour): the
     * filtered item (or "New factory task") in yellow, the interaction hint in white — "Click with
     * item to set" until a filter exists, then "Click to configure" — and, when inactive, the red
     * reason (no target amount / missing address), matching {@code FactoryPanelBehaviour#getLabel}.
     */
    public List<Component> getGaugeTooltip(FactoryControllerMenu menu) {
        List<Component> lines = new ArrayList<>();
        lines.add(behaviour.filter.isEmpty()
                ? CreateLang.translate("factory_panel.new_factory_task").color(0xFBDC7D).component()
                : CreateLang.text(behaviour.filter.getHoverName().getString()).color(0xFBDC7D).component());
        if (!behaviour.filter.isEmpty()) {
            lines.add(Component.translatable("createfactorycontroller.gui.in_stock", behaviour.stockLevel)
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add((behaviour.filter.isEmpty()
                ? CreateLang.translate("logistics.filter.click_to_set")
                : CreateLang.translate("factory_panel.click_to_configure"))
                .style(ChatFormatting.WHITE).component());
        lines.add(Component.translatable("createfactorycontroller.gui.action_remove_component")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (!behaviour.targetedBy().isEmpty() && !behaviour.isActive())
            lines.add(CreateLang.translate("gui.factory_panel.no_target_amount_set").style(ChatFormatting.RED).component());
        else if (behaviour.isMissingAddress())
            lines.add(CreateLang.translate("gui.factory_panel.address_missing").style(ChatFormatting.RED).component());
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
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried) {
        FactoryControllerMenu menu = screen.getMenu();
        if (behaviour.filter.isEmpty()) {
            if (carried.isEmpty())
                Minecraft.getInstance().setScreen(new SetItemScreen(screen, behaviour.position()));
            else
                PacketDistributor.sendToServer(
                        new GaugeSetItemPacket(menu.controllerPos, behaviour.position(), carried.copy()));
        } else if (carried.isEmpty()) {
            Minecraft.getInstance().setScreen(new ConfigureRecipeScreen(screen, behaviour.position()));
        }
        return true;
    }
}
