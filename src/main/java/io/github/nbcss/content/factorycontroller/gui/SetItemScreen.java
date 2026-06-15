package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.content.factorycontroller.block.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import io.github.nbcss.content.factorycontroller.compat.fluids.FluidCompat;
import io.github.nbcss.content.factorycontroller.packet.GaugeSetItemPacket;
import io.github.nbcss.content.factorycontroller.render.FluidGuiRender;
import net.neoforged.neoforge.fluids.FluidStack;
import net.createmod.catnip.gui.element.GuiGameElement;
import io.github.nbcss.CreateFactoryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Set-item configuration screen for a virtual gauge — a replica of Create's
 * {@code FactoryPanelSetItemScreen}. A separate screen that <b>shares the controller's
 * {@link FactoryControllerMenu}</b> (no server container swap) and draws the live controller board as
 * a dimmed backdrop. The filter is a real ghost {@code Slot} on the shared menu (vanilla renders its
 * icon + hover highlight): click it with a held item, shift-click an inventory item, or drop a JEI
 * ingredient. Confirm/Escape commits and returns. Extends the same base as the controller so the
 * {@code renderBg}/{@code renderForeground} flow is identical and proven.
 */
@OnlyIn(Dist.CLIENT)
public class SetItemScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> implements PanelSyncListener {

    private static final ResourceLocation PLAYER_INVENTORY_TEX =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/player_inventory.png");
    private static final int INV_TEX_W = 176, INV_TEX_H = 108;
    /** Y of the hotbar slot row within player_inventory.png (matches the controller's constant). */
    private static final int INV_TEX_HOTBAR_Y = 76;

    private final FactoryControllerScreen controller;
    private final VirtualPanelPosition gaugePos;

    private IconButton confirm;
    private IconButton relocateButton;
    private List<Rect2i> extraAreas = Collections.emptyList();
    // Set-item panel top-left, and the player-inventory background top-left — centered in the GUI rect.
    private int panelX, panelY;
    private int invBgX, invBgY;


    public SetItemScreen(FactoryControllerScreen controller, VirtualPanelPosition gaugePos) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
              CreateLang.translate("gui.factory_panel.place_item_to_monitor").component());
        this.controller = controller;
        this.gaugePos = gaugePos;
        this.menu.setGhostFilter(ItemStack.EMPTY);
        /** Chime when the overlay opens — played client-side for this player only. */
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_OPEN.get(), 1f));
    }

    @Override
    protected void init() {
        // Match the controller's GUI rect so JEI's layout (item list, exclusion zone) is unchanged.
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        AllGuiTextures bg = AllGuiTextures.FACTORY_GAUGE_SET_ITEM;
        int blockW = bg.getWidth();
        int blockH = bg.getHeight() + INV_TEX_H;
        panelX = leftPos + (imageWidth - blockW) / 2;
        panelY = topPos + (imageHeight - blockH) / 2;

        // Inventory background sits just below the panel; the slot grid is derived from it so the
        // slots always line up with the texture's recesses (hotbar row at INV_TEX_HOTBAR_Y).
        invBgX = panelX + (blockW - INV_TEX_W) / 2;
        invBgY = panelY + bg.getHeight() + 6;
        int originX = invBgX + 8 - leftPos;                                   // slot grid origin
        int hotbarY = (invBgY + INV_TEX_HOTBAR_Y) - topPos;                   // hotbar row

        menu.showGhostSlot(filterX() - leftPos, filterY() - topPos, originX, hotbarY);

        // Real confirm button (like Create's set-item screen) instead of a hit-tested icon.
        confirm = new IconButton(panelX + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth() - 40
                , panelY + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getHeight() - 25, AllIcons.I_CONFIRM);
        confirm.withCallback(this::returnToController);
        addWidget(confirm);

        // Relocate button, left of the filter slot — commits the chosen item (so a placed selection
        // isn't lost), then hands off to the controller's relocate mode where the next empty cell
        // clicked becomes this gauge's new position. Mirrors ConfigureRecipeScreen's relocate button.
        relocateButton = new IconButton(filterX() - 67, filterY() - 1, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> {
            PacketDistributor.sendToServer(new GaugeSetItemPacket(
                    menu.controllerPos, gaugePos, menu.getGhostFilter().copy()));
            menu.setGhostFilter(ItemStack.EMPTY);
            controller.beginRelocateMode(gaugePos);
            Minecraft.getInstance().setScreen(controller);
        });
        relocateButton.setToolTip(CreateLang.translate("gui.factory_panel.relocate").component());
        addWidget(relocateButton);

        extraAreas = List.of(new Rect2i(panelX + bg.getWidth(),
                panelY + bg.getHeight() - 30, 40, 20));
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        // Re-lay-out the controller too so its (backdrop) geometry tracks the new window size; then
        // our init() runs and re-applies the set-item slot layout over the controller's.
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    // ── Layout (tunable to the FACTORY_GAUGE_SET_ITEM texture) ────────────────
    private int filterX()   { return panelX + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth() / 2 - 18; }
    private int filterY()   { return panelY + 28; }

    // Sub-screen renders the controller board as its background (renderBg → controller.renderBoard),
    // so refresh the parent's gauge-widget cache when a sync lands while this screen is open.
    @Override
    public void onPanelSync() {
        controller.onPanelSync();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickBulbs();     // keep the background board's indicator bulbs animating
    }

    // ── Render ───────────────────────────────────────────────────────────────


    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        AllGuiTextures bg = AllGuiTextures.FACTORY_GAUGE_SET_ITEM;
        RenderSystem.enableBlend();
        bg.render(gfx, panelX - 5, panelY);
        gfx.blit(PLAYER_INVENTORY_TEX, invBgX, invBgY, 0, 0, INV_TEX_W, INV_TEX_H);

        confirm.render(gfx, mouseX, mouseY, partialTick);
        relocateButton.render(gfx, mouseX, mouseY, partialTick);

        // Decorative gauge model; the configured filter shows in the real ghost slot (vanilla-drawn).
        GuiGameElement.of(AllBlocks.FACTORY_GAUGE.asStack()).scale(3)
                .render(gfx, panelX + 180, panelY + 48);
    }

    @Override
    protected void renderForeground(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(font, title,
                panelX + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth() / 2 - font.width(title) / 2 - 5,
                panelY + 4, 0x3D3C48, false);
        gfx.drawString(font, playerInventoryTitle, invBgX + 8, invBgY + 6, 0x404040, false);
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    /** True only for our ghost filter slot (the sole slot backed by the menu's ghost inventory). The wrapper
     *  filter item can also sit in the player's inventory as a normal item, so the fluid form is scoped to here. */
    private boolean isGhostSlot(Slot slot) {
        return slot.container == menu.ghostInventory;
    }

    // In the ghost filter slot a fluid filter (CFL/CreateFluid's wrapper item) renders/names itself as the wrapper,
    // so we draw the fluid itself instead — its icon (replacing the slot content) and its name. Real wrapper items
    // in the player inventory keep their normal item icon/tooltip.
    @Override
    protected void renderSlotContents(GuiGraphics gfx, ItemStack stack, Slot slot, String countString) {
        if (isGhostSlot(slot)) {
            FluidStack fluid = FluidCompat.getFilterFluid(stack);
            if (!fluid.isEmpty()) { FluidGuiRender.icon(gfx, fluid, slot.x, slot.y, 16); return; }
        }
        super.renderSlotContents(gfx, stack, slot, countString);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        if (hoveredSlot != null && isGhostSlot(hoveredSlot) && FluidCompat.isFluidFilter(stack))
            return FluidCompat.fluidTooltip(FluidCompat.getFilterFluid(stack),
                    minecraft.options.advancedItemTooltips);
        return super.getTooltipFromContainerItem(stack);
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    protected void slotClicked(@NotNull Slot slot, int slotId, int mouseButton, @NotNull ClickType type) {
        if (slotId == menu.ghostSlotIndex()) {
            if (type == ClickType.QUICK_MOVE) menu.setGhostFilter(ItemStack.EMPTY);
            else menu.setGhostFilter(filterFromCarried(menu.getCarried(), mouseButton));
            return;
        }
        if (type == ClickType.QUICK_MOVE && slot.hasItem()) {
            menu.setGhostFilter(slot.getItem());   // shift-click an inventory item → set the filter
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    public void onClose() {
        returnToController();   // return to the controller without closing the shared container
    }

    @Override
    public void removed() {
        // Chime on every exit path (confirm button, Escape) as the overlay returns to the controller.
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }

    private void returnToController() {
        PacketDistributor.sendToServer(new GaugeSetItemPacket(
                menu.controllerPos, gaugePos, menu.getGhostFilter().copy()));
        menu.setGhostFilter(ItemStack.EMPTY);
        Minecraft.getInstance().setScreen(controller);
    }

    /**
     * The filter a carried item produces: normally the item itself, but with a fluid-logistics addon installed a
     * right-click (mouseButton 1) on a filled fluid container uses its stored fluid as a fluid filter instead.
     * Without an addon, or on left-click, the container item is the filter (requirement: both clicks set item).
     */
    private ItemStack filterFromCarried(ItemStack carried, int mouseButton) {
        if (FluidCompat.isLoaded() && mouseButton == 1) {
            FluidStack fluid = FluidCompat.fluidInContainer(carried);
            if (!fluid.isEmpty()) return FluidCompat.makeFluidFilter(fluid);
        }
        return carried;
    }

    // ── JEI hooks (used by the handlers registered on this screen) ──
    public Rect2i ghostSlotArea() { return new Rect2i(filterX(), filterY(), 16, 16); }
    public void setGhostFromJei(ItemStack stack) { menu.setGhostFilter(stack); }

    public List<Rect2i> extraGuiAreas() {
        return extraAreas;
    }
}
