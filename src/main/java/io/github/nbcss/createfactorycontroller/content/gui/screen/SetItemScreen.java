package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.FontHelper;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
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
 * Set-item configuration screen for a virtual gauge.
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

    private TooltipIconButton confirm;
    private TooltipIconButton relocateButton;
    /** Two-button mode toggle (an exact copy of Create's brass-filter respect/ignore-data buttons): respect
     *  vs ignore the filter item's NBT/data. Hidden entirely for a fluid filter. */
    private TooltipIconButton respectDataButton;
    private TooltipIconButton ignoreDataButton;
    /** Whether the gauge should monitor/consume the filter item ignoring its NBT/components. */
    private boolean ignoreData;
    // Tooltip text reused verbatim from Create's FilterScreen (same lang keys).
    private final Component respectDataName = CreateLang.translateDirect("gui.filter.respect_data");
    private final Component respectDataDesc = CreateLang.translateDirect("gui.filter.respect_data.description");
    private final Component ignoreDataName = CreateLang.translateDirect("gui.filter.ignore_data");
    private final Component ignoreDataDesc = CreateLang.translateDirect("gui.filter.ignore_data.description");
    private List<Rect2i> extraAreas = Collections.emptyList();

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
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        AllGuiTextures bg = AllGuiTextures.FACTORY_GAUGE_SET_ITEM;
        int blockW = bg.getWidth();
        int blockH = bg.getHeight() + INV_TEX_H;
        panelX = leftPos + (imageWidth - blockW) / 2;
        panelY = topPos + (imageHeight - blockH) / 2;

        invBgX = panelX + (blockW - INV_TEX_W) / 2;
        invBgY = panelY + bg.getHeight() + 6;
        int originX = invBgX + 8 - leftPos;                                   // slot grid origin
        int hotbarY = (invBgY + INV_TEX_HOTBAR_Y) - topPos;                   // hotbar row

        menu.repositionSlots(originX, hotbarY, true);

        int buttonY = panelY + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getHeight() - 25;

        // why create's confirm button doesn't have tooltip here? it is inconsistent :/
        confirm = new TooltipIconButton(panelX + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth() - 40,
                buttonY, AllIcons.I_CONFIRM);
        confirm.withCallback(this::returnToController);
        confirm.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());
        addWidget(confirm);

        relocateButton = new TooltipIconButton(panelX + 3, buttonY, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> {
            PacketDistributor.sendToServer(new GaugeSetItemPacket(
                    menu.controllerPos, gaugePos, filter.copy(), ignoreData));
            controller.beginRelocateMode(gaugePos);
            Minecraft.getInstance().setScreen(controller);
        });
        relocateButton.setToolTip(Component.translatable("createfactorycontroller.gui.action_relocate"));
        addWidget(relocateButton);

        // Respect-/ignore-data toggle
        int ignoreDataX = panelX + 97;
        respectDataButton = new TooltipIconButton(ignoreDataX, buttonY, AllIcons.I_RESPECT_NBT);
        respectDataButton.withCallback(() -> { ignoreData = false; updateIgnoreDataButtons(); });
        respectDataButton.withDeferredTooltip(() -> dataButtonTooltip(respectDataName, respectDataDesc));
        addWidget(respectDataButton);
        ignoreDataButton = new TooltipIconButton(ignoreDataX + 18, buttonY, AllIcons.I_IGNORE_NBT);
        ignoreDataButton.withCallback(() -> { ignoreData = true; updateIgnoreDataButtons(); });
        ignoreDataButton.withDeferredTooltip(() -> dataButtonTooltip(ignoreDataName, ignoreDataDesc));
        addWidget(ignoreDataButton);
        updateIgnoreDataButtons();

        extraAreas = List.of(new Rect2i(panelX + bg.getWidth(),
                panelY + bg.getHeight() - 30, 40, 20));
    }

    private void updateIgnoreDataButtons() {
        boolean noIgnoreData = !behaviour.filterResolver().supportsIgnoreData() || FluidCompat.isFluidFilter(filter);
        if (noIgnoreData) ignoreData = false;   // can't ignore data; the server clamps this too
        respectDataButton.visible = !noIgnoreData;
        ignoreDataButton.visible = !noIgnoreData;
        respectDataButton.green = !noIgnoreData && !ignoreData;
        ignoreDataButton.green = !noIgnoreData && ignoreData;
    }

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
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    // ── Layout (tunable to the FACTORY_GAUGE_SET_ITEM texture) ────────────────
    private int filterX()   { return panelX + AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth() / 2 - 18; }
    private int filterY()   { return panelY + 28; }

    @Override
    public void onPanelSync() {
        controller.onPanelSync();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickBulbs();
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
        } else if (overFilter(mouseX, mouseY)) {
            gfx.renderComponentTooltip(font, filterEmptyTooltip(), mouseX, mouseY);
        } else renderTooltip(gfx, mouseX, mouseY);
        TooltipIconButton.renderFirstTooltip(gfx, font, mouseX, mouseY,
                confirm, relocateButton, respectDataButton, ignoreDataButton);
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
        returnToController();
    }

    @Override
    public void removed() {
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

    /** Empty-filter-slot hint */
    private List<Component> filterEmptyTooltip() {
        boolean fluidCandidate = behaviour.filterResolver().acceptsItemDrop()
                && behaviour.filterResolver().acceptsFluidDrop()
                && !FluidCompat.fluidInContainer(menu.getCarried()).isEmpty();
        if (fluidCandidate) {
            return List.of(
                    Component.translatable("createfactorycontroller.gui.set_item.filter_tip_item")
                            .withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("createfactorycontroller.gui.set_item.filter_tip_fluid")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return List.of(Component.translatable("createfactorycontroller.gui.set_item.filter_tip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    public List<Rect2i> extraGuiAreas() {
        return extraAreas;
    }
}
