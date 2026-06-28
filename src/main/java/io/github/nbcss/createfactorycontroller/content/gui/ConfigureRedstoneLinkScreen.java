package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualRedstoneLinkBehaviour;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureRedstoneLinkPacket;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

import java.util.List;

/**
 * Full-configuration overlay for a Redstone Link component — its two type frequencies (Red/Blue) and Send/Receive
 * mode. Shares the controller's {@link FactoryControllerMenu} (no container swap) and draws the live board as a dimmed
 * backdrop, with the player inventory shown so items can be grabbed onto the frequency slots (or dropped from JEI).
 *
 * <p>Edits are staged locally ({@link #red}/{@link #blue}/{@link #receive}) and committed once via
 * {@link ConfigureRedstoneLinkPacket} when the screen closes — by the confirm/relocate buttons, ESC, or any other
 * path (see {@link #removed()}). Frequency items are filters: they're copied count-1 and never consumed.</p>
 */
@OnlyIn(Dist.CLIENT)
public class ConfigureRedstoneLinkScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {

    private static final ResourceLocation PANEL_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/redstone_link.png");
    private static final int PANEL_W = 200, PANEL_H = 103;

    private static final ResourceLocation PLAYER_INVENTORY_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/player_inventory.png");
    private static final int INV_TEX_W = 176, INV_TEX_H = 108, INV_TEX_HOTBAR_Y = 76;

    // Mode-button sprites.
    private static final ResourceLocation WIRELESS_TRANSMIT =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "icons/wireless_transmit");
    private static final ResourceLocation WIRELESS_RECEIVE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "icons/wireless_receive");

    // Frequency slots (panel-relative), stacked red-over-blue; items drawn 16×16 at slot+1.
    private static final int RED_X = 87, RED_Y = 26, BLUE_X = 87, BLUE_Y = 44, SLOT = 18;

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition linkPos;

    // Staged config (committed on close).
    private ItemStack red = ItemStack.EMPTY;
    private ItemStack blue = ItemStack.EMPTY;
    private boolean receive = false;
    private boolean committed = false;

    private int panelX, panelY, invBgX, invBgY;
    private IconButton relocateButton, addConnectionButton, modeButton, confirmButton;

    public ConfigureRedstoneLinkScreen(FactoryControllerScreen controller, VirtualComponentPosition linkPos) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
              Component.translatable("createfactorycontroller.gui.redstone_link_settings"));
        this.controller = controller;
        this.linkPos = linkPos;
        if (controller.getMenu().componentAt(linkPos) instanceof VirtualRedstoneLinkBehaviour link) {
            red = link.redFreq.copy();
            blue = link.blueFreq.copy();
            receive = link.receive;
        }
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_OPEN.get(), 1f));
    }

    @Override
    protected void init() {
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        int blockH = PANEL_H + 6 + INV_TEX_H;
        panelX = leftPos + (imageWidth - PANEL_W) / 2;
        panelY = topPos + (imageHeight - blockH) / 2;

        // Player inventory below the panel; slot grid derived from the texture (hotbar at INV_TEX_HOTBAR_Y).
        invBgX = panelX + (PANEL_W - INV_TEX_W) / 2;
        invBgY = panelY + PANEL_H + 6;
        int originX = invBgX + 8 - leftPos;
        int hotbarY = (invBgY + INV_TEX_HOTBAR_Y) - topPos;
        menu.repositionSlots(originX, hotbarY, true);   // full inventory, ghost slot off-screen (we use fake slots)

        relocateButton = new IconButton(panelX + 8, panelY + 79, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> {
            controller.beginRelocateMode(linkPos);
            Minecraft.getInstance().setScreen(controller);   // commit happens in removed()
        });
        // No self-tooltip (it would draw during renderBg and be covered by the slots/items); drawn last in render().
        addWidget(relocateButton);

        // Add-connection: same icon/flow as the recipe screen's "connect input" — start board connection mode from this
        // link. The wire is stored on the link regardless of which side starts it, and its arrow follows the link's
        // Send/Receive mode (see VirtualConnectionRenderer), so this needs no direction choice here.
        addConnectionButton = new IconButton(panelX + 30, panelY + 79, AllIcons.I_ADD);
        addConnectionButton.withCallback(() -> {
            controller.beginConnectionMode(linkPos);
            Minecraft.getInstance().setScreen(controller);   // commit happens in removed()
        });
        addWidget(addConnectionButton);

        ScreenElement modeIcon = (gfx, x, y) -> gfx.blitSprite(receive ? WIRELESS_RECEIVE : WIRELESS_TRANSMIT, x, y, 16, 16);
        modeButton = new IconButton(panelX + 138, panelY + 79, modeIcon);
        modeButton.withCallback(() -> receive = !receive);   // staged; icon lambda reads it live
        addWidget(modeButton);

        confirmButton = new IconButton(panelX + 167, panelY + 79, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> Minecraft.getInstance().setScreen(controller));
        // No self-tooltip (drawn last in render() so the slots/items can't cover it).
        addWidget(confirmButton);
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(new Rect2i(panelX + 195, panelY + 76, 50, 18));
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickBulbs();   // keep the backdrop board's indicator bulbs animating
    }

    @Override
    public void onPanelSync() {
        controller.onPanelSync();
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        // Frequency-slot tooltips.
        if (overRed(mouseX, mouseY) && !red.isEmpty())
            gfx.renderTooltip(font, red, mouseX, mouseY);
        else if (overBlue(mouseX, mouseY) && !blue.isEmpty())
            gfx.renderTooltip(font, blue, mouseX, mouseY);
        else if (modeButton.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, List.of(Component.translatable("createfactorycontroller.gui.redstone_link.mode")
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ScrollInput.HEADER_RGB.getRGB()))
                    .getVisualOrderText(),
                    Component.literal(receive ? "-> " : "> ")
                            .append(Component.translatable("createfactorycontroller.gui.redstone_link.mode.receive"))
                            .withStyle(receive ? ChatFormatting.WHITE : ChatFormatting.GRAY).getVisualOrderText(),
                    Component.literal(!receive ? "-> " : "> ")
                            .append(Component.translatable("createfactorycontroller.gui.redstone_link.mode.send"))
                            .withStyle(!receive ? ChatFormatting.WHITE : ChatFormatting.GRAY).getVisualOrderText(),
                    Component.translatable("createfactorycontroller.gui.request_mode.change_tip")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                            .getVisualOrderText()), mouseX, mouseY);
        // Icon-button tooltips drawn here (last) so the slots/items from super.render can't cover them.
        else if (relocateButton.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, CreateLang.translate("gui.factory_panel.relocate").component(), mouseX, mouseY);
        else if (addConnectionButton.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, CreateLang.translate("gui.factory_panel.connect_input").component(), mouseX, mouseY);
        else if (confirmButton.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, CreateLang.translate("gui.factory_panel.save_and_close").component(), mouseX, mouseY);
        renderTooltip(gfx, mouseX, mouseY);   // hovered inventory item
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        RenderSystem.enableBlend();
        gfx.blit(PANEL_TEX, panelX, panelY, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        gfx.blit(PLAYER_INVENTORY_TEX, invBgX, invBgY, 0, 0, INV_TEX_W, INV_TEX_H);

        // Redstone Link Model
        GuiGameElement.of(AllBlocks.REDSTONE_LINK.asStack()).scale(3.0).at(0, 0, 100)
                .render(gfx, panelX + 203, panelY + 60);

        // Frequency item icons + hover highlight.
        gfx.renderItem(red, panelX + RED_X + 1, panelY + RED_Y + 1);
        gfx.renderItem(blue, panelX + BLUE_X + 1, panelY + BLUE_Y + 1);
        if (overRed(mouseX, mouseY)) highlight(gfx, panelX + RED_X + 1, panelY + RED_Y + 1);
        if (overBlue(mouseX, mouseY)) highlight(gfx, panelX + BLUE_X + 1, panelY + BLUE_Y + 1);

        gfx.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        addConnectionButton.render(gfx, mouseX, mouseY, partialTick);
        modeButton.render(gfx, mouseX, mouseY, partialTick);
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
    }

    private static void highlight(GuiGraphics gfx, int x, int y) {
        gfx.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        Component title = getTitle();
        gfx.drawString(font, title, panelX + PANEL_W / 2 - font.width(title) / 2, panelY + 4, 0x3D3C48, false);
        gfx.drawString(font, playerInventoryTitle, invBgX + 8, invBgY + 6, 0x404040, false);
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}   // no default container labels

    // ── Frequency slots ────────────────────────────────────────────────────────

    private boolean overRed(double mx, double my) { return in(mx, my, panelX + RED_X, panelY + RED_Y); }
    private boolean overBlue(double mx, double my) { return in(mx, my, panelX + BLUE_X, panelY + BLUE_Y); }
    private static boolean in(double mx, double my, int x, int y) {
        return mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
    }

    /** JEI ghost / drop targets (screen coords). */
    public Rect2i redSlotArea()  { return new Rect2i(panelX + RED_X + 1, panelY + RED_Y + 1, 16, 16); }
    public Rect2i blueSlotArea() { return new Rect2i(panelX + BLUE_X + 1, panelY + BLUE_Y + 1, 16, 16); }
    public void setRedFromJei(ItemStack stack)  { red = stack.copyWithCount(1); }
    public void setBlueFromJei(ItemStack stack) { blue = stack.copyWithCount(1); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean onRed = overRed(mouseX, mouseY), onBlue = overBlue(mouseX, mouseY);
        if (onRed || onBlue) {
            // A held item sets the type; an empty cursor clears it. The cursor is never consumed (filters).
            ItemStack carried = menu.getCarried();
            ItemStack set = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
            if (onRed) red = set; else blue = set;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(@NotNull Slot slot, int slotId, int mouseButton, @NotNull ClickType type) {
        // Shift-click an inventory item → fill the first empty type (Red, then Blue). Item isn't moved.
        if (type == ClickType.QUICK_MOVE && slot.hasItem()) {
            ItemStack freq = slot.getItem().copyWithCount(1);
            if (red.isEmpty()) red = freq;
            else if (blue.isEmpty()) blue = freq;
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    // ── Commit / close ─────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(controller);   // return to the controller; commit fires in removed()
    }

    @Override
    public void removed() {
        commit();
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }

    /** Sends the staged config once (idempotent across the several close paths). */
    private void commit() {
        if (committed) return;
        committed = true;
        PacketDistributor.sendToServer(new ConfigureRedstoneLinkPacket(
            menu.controllerPos, linkPos, receive, red, blue));
    }
}
