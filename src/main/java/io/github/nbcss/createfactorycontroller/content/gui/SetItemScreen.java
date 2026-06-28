package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.FontHelper;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.packet.GaugeSetItemPacket;
import io.github.nbcss.createfactorycontroller.content.render.FluidGuiRender;
import net.neoforged.neoforge.fluids.FluidStack;
import net.createmod.catnip.gui.element.GuiGameElement;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
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

import java.util.ArrayList;
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
    private static final int FILTER_SLOT_SIZE = 16;

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition gaugePos;
    private final VirtualGaugeBehaviour behaviour;
    /** Staged filter copied into the gauge when the screen closes. */
    private ItemStack filter = ItemStack.EMPTY;

    private IconButton confirm;
    private IconButton relocateButton;
    /** Two-button mode toggle (an exact copy of Create's brass-filter respect/ignore-data buttons): respect
     *  vs ignore the filter item's NBT/data. Hidden entirely for a fluid filter. */
    private IconButton respectDataButton;
    private IconButton ignoreDataButton;
    /** Whether the gauge should monitor/consume the filter item ignoring its NBT/components. */
    private boolean ignoreData;
    // Tooltip text reused verbatim from Create's FilterScreen (same lang keys).
    private final Component respectDataName = CreateLang.translateDirect("gui.filter.respect_data");
    private final Component respectDataDesc = CreateLang.translateDirect("gui.filter.respect_data.description");
    private final Component ignoreDataName = CreateLang.translateDirect("gui.filter.ignore_data");
    private final Component ignoreDataDesc = CreateLang.translateDirect("gui.filter.ignore_data.description");
    private List<Rect2i> extraAreas = Collections.emptyList();
    // Set-item panel top-left, and the player-inventory background top-left — centered in the GUI rect.
    private int panelX, panelY;
    private int invBgX, invBgY;


    public SetItemScreen(FactoryControllerScreen controller, VirtualGaugeBehaviour behaviour) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
              CreateLang.translate("gui.factory_panel.place_item_to_monitor").component());
        this.controller = controller;
        this.behaviour = behaviour;
        this.gaugePos = behaviour.position();
        this.ignoreData = false;
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

        menu.repositionSlots(originX, hotbarY, true);

        int buttonY = panelY + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getHeight() - 25;

        // Real confirm button (like Create's set-item screen) instead of a hit-tested icon.
        confirm = new IconButton(panelX + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth() - 40,
                buttonY, AllIcons.I_CONFIRM);
        confirm.withCallback(this::returnToController);
        addWidget(confirm);

        // Relocate button, left of the filter slot — commits the chosen item (so a placed selection
        // isn't lost), then hands off to the controller's relocate mode where the next empty cell
        // clicked becomes this gauge's new position. Mirrors ConfigureRecipeScreen's relocate button.
        relocateButton = new IconButton(panelX + 3, buttonY, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> {
            PacketDistributor.sendToServer(new GaugeSetItemPacket(
                    menu.controllerPos, gaugePos, filter.copy(), ignoreData));
            controller.beginRelocateMode(gaugePos);
            Minecraft.getInstance().setScreen(controller);
        });
        // No self-tooltip (it would draw during renderBg and be covered by the slots/items); drawn last in render().
        addWidget(relocateButton);

        // Respect-/ignore-data toggle (reuses Create's brass-filter icons), in the bottom button row.
        int ignoreDataX = panelX + 98;
        respectDataButton = new IconButton(ignoreDataX, buttonY, AllIcons.I_RESPECT_NBT);
        respectDataButton.withCallback(() -> { ignoreData = false; updateIgnoreDataButtons(); });
        addWidget(respectDataButton);
        ignoreDataButton = new IconButton(ignoreDataX + 18, buttonY, AllIcons.I_IGNORE_NBT);
        ignoreDataButton.withCallback(() -> { ignoreData = true; updateIgnoreDataButtons(); });
        addWidget(ignoreDataButton);
        updateIgnoreDataButtons();

        extraAreas = List.of(new Rect2i(panelX + bg.getWidth(),
                panelY + bg.getHeight() - 30, 40, 20));
    }

    /** Refreshes the data-toggle buttons on a filter/mode change: hidden for a fluid filter (ignore-data is
     *  moot there), otherwise the current mode glows green. The tooltips are NOT stored on the buttons (that
     *  would self-render during renderBg and be covered by the slots/items drawn afterwards); they're drawn
     *  last in {@link #render} instead. */
    private void updateIgnoreDataButtons() {
        // Ignore-data is an item-gauge concept; hide it for any non-item gauge (fluid/energy have no NBT variants),
        // and for an item gauge once its chosen filter is a fluid.
        boolean noIgnoreData = !behaviour.filterResolver().supportsIgnoreData() || FluidCompat.isFluidFilter(filter);
        if (noIgnoreData) ignoreData = false;   // can't ignore data; the server clamps this too
        respectDataButton.visible = !noIgnoreData;
        ignoreDataButton.visible = !noIgnoreData;
        // green highlights the current mode (Create's handleIndicators: green = !isButtonEnabled).
        respectDataButton.green = !noIgnoreData && !ignoreData;
        ignoreDataButton.green = !noIgnoreData && ignoreData;
    }

    /** Create's filter-button tooltip: name line + hold-shift hint, with the cut description appended while
     *  Shift is held (mirrors {@code AbstractFilterScreen#handleTooltips}/{@code fillToolTip}). */
    private List<Component> dataButtonTooltip(Component name, Component desc) {
        boolean shift = hasShiftDown();
        List<Component> tip = new ArrayList<>();
        tip.add(name);
        tip.add(TooltipHelper.holdShift(FontHelper.Palette.YELLOW, shift));
        if (shift) tip.addAll(TooltipHelper.cutTextComponent(desc, FontHelper.Palette.ALL_GRAY));
        return tip;
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
        if (overFilter(mouseX, mouseY) && !filter.isEmpty()) {
            if (FluidCompat.isFluidFilter(filter))
                gfx.renderComponentTooltip(font, FluidCompat.fluidTooltip(FluidCompat.getFilterFluid(filter),
                        minecraft.options.advancedItemTooltips), mouseX, mouseY);
            else
                gfx.renderTooltip(font, filter, mouseX, mouseY);
        } else renderTooltip(gfx, mouseX, mouseY);
        // Draw the icon-button tooltips LAST so the menu slots/items (drawn after renderBg) can't cover them.
        // Use isMouseOver (not isHoveredOrFocused) so a focused-but-not-hovered button doesn't trail the cursor.
        if (confirm.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, CreateLang.translate("gui.factory_panel.save_and_close").component(), mouseX, mouseY);
        else if (relocateButton.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, CreateLang.translate("gui.factory_panel.relocate").component(), mouseX, mouseY);
        else if (respectDataButton.visible && respectDataButton.isMouseOver(mouseX, mouseY))
            gfx.renderComponentTooltip(font, dataButtonTooltip(respectDataName, respectDataDesc), mouseX, mouseY);
        else if (ignoreDataButton.visible && ignoreDataButton.isMouseOver(mouseX, mouseY))
            gfx.renderComponentTooltip(font, dataButtonTooltip(ignoreDataName, ignoreDataDesc), mouseX, mouseY);
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
        respectDataButton.render(gfx, mouseX, mouseY, partialTick);
        ignoreDataButton.render(gfx, mouseX, mouseY, partialTick);

        renderFilter(gfx, mouseX, mouseY);

        // Decorative gauge model.
        GuiGameElement.of(behaviour.getItem()).scale(3)
                .render(gfx, panelX + 180, panelY + 48);
    }

    private void renderFilter(GuiGraphics gfx, int mouseX, int mouseY) {
        FluidStack fluid = FluidCompat.getFilterFluid(filter);
        if (!fluid.isEmpty()) FluidGuiRender.icon(gfx, fluid, filterX(), filterY(), FILTER_SLOT_SIZE);
        else gfx.renderItem(filter, filterX(), filterY());
        if (overFilter(mouseX, mouseY))
            gfx.fill(filterX(), filterY(), filterX() + FILTER_SLOT_SIZE, filterY() + FILTER_SLOT_SIZE, 0x80FFFFFF);
    }

    @Override
    protected void renderForeground(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(font, title,
                panelX + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth() / 2 - font.width(title) / 2 - 5,
                panelY + 4, 0x3D3C48, false);
        gfx.drawString(font, playerInventoryTitle, invBgX + 8, invBgY + 6, 0x404040, false);
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overFilter(mouseX, mouseY)) {
            setFilterFromCarried(menu.getCarried(), button, true);
            updateIgnoreDataButtons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(@NotNull Slot slot, int slotId, int mouseButton, @NotNull ClickType type) {
        if (type == ClickType.QUICK_MOVE && slot.hasItem()) {
            setFilterFromCarried(slot.getItem(), mouseButton, false);
            updateIgnoreDataButtons();
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
                menu.controllerPos, gaugePos, filter.copy(), ignoreData));
        Minecraft.getInstance().setScreen(controller);
    }

    private void setFilterFromCarried(ItemStack source, int mouseButton, boolean allowClear) {
        if (source.isEmpty()) {
            if (allowClear) setFilter(ItemStack.EMPTY);
            return;
        }
        ItemStack resolved = behaviour.filterResolver().fromCarried(source, mouseButton);
        if (!resolved.isEmpty()) setFilter(resolved);
    }

    private void setFilter(ItemStack stack) {
        filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    // ── JEI hooks (used by the handlers registered on this screen) ──
    public Rect2i ghostSlotArea() { return new Rect2i(filterX(), filterY(), 16, 16); }
    public boolean acceptsJeiItems() { return behaviour.filterResolver().acceptsItemDrop(); }
    public boolean acceptsJeiFluids() { return behaviour.filterResolver().acceptsFluidDrop(); }
    /** JEI drop. For a fluid gauge the handler passes an already-built fluid-filter token (from a dragged fluid); set
     *  it only if it's a valid fluid filter (a stray item drop is refused). An item gauge takes the stack directly. */
    public void setGhostFromJei(ItemStack stack) {
        if (behaviour.filterResolver().acceptsFilter(stack)) setFilter(stack);
        updateIgnoreDataButtons();
    }

    public void setFluidFromJei(FluidStack fluid) {
        ItemStack stack = behaviour.filterResolver().fromFluid(fluid);
        if (!stack.isEmpty()) setGhostFromJei(stack);
    }

    private boolean overFilter(double mx, double my) {
        return mx >= filterX() && mx < filterX() + FILTER_SLOT_SIZE
                && my >= filterY() && my < filterY() + FILTER_SLOT_SIZE;
    }

    public List<Rect2i> extraGuiAreas() {
        return extraAreas;
    }
}
