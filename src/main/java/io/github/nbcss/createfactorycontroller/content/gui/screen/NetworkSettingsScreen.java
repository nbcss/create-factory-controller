package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.packet.SetNetworkSettingsPacket;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Overlay for editing a network's <b>shared</b> settings (custom name + icon), opened by clicking the
 * network selector empty-handed. Shares the controller's {@link FactoryControllerMenu} (no server
 * container swap) and draws the live board as a dimmed backdrop, mirroring {@link ControllerSettingScreen}.
 * The icon is a ghost item (never consumed) like {@link SetItemScreen}. Commit (confirm / Escape) sends a
 * {@link SetNetworkSettingsPacket}; blank name + empty icon reverts the network to its defaults.
 */
@OnlyIn(Dist.CLIENT)
public class NetworkSettingsScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {

    private static final ResourceLocation PANEL_SPRITE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "network_settings/frame");
    private static final ResourceLocation PANEL_BOTTOM_SPRITE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "common/bottom_bar");
    private static final ResourceLocation PANEL_BOTTOM_VDIV =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "common/bottom_bar_vdiv");
    private static final int PANEL_W = 204, PANEL_H = 89, PANEL_BOTTOM_H = 30; //129

    private static final ResourceLocation ELEMENT_BORDER_SPRITE =
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "network_settings/element_border");
    private static final ResourceLocation NETWORK_SLOT_SPRITE =
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "common/network_slot");
    private static final ResourceLocation INPUT_FIELD_SPRITE =
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "common/input_field");

    private static final ResourceLocation PLAYER_INVENTORY_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/player_inventory.png");
    private static final ResourceLocation NETWORK = ResourceLocation.fromNamespaceAndPath(
            "createfactorycontroller", "factory_controller/network_selector/network");
    private static final int INV_TEX_W = 176, INV_TEX_H = 108;
    /** Y of the hotbar slot row within player_inventory.png (matches SetItemScreen). */
    private static final int INV_TEX_HOTBAR_Y = 76;

    // Icon slot + name box geometry (panel-relative), reusing settings.png's slot/selector recesses.
    private static final int ICON_X = 22, ICON_Y = 28, ICON_SIZE = 16;
    private static final int NAME_X = 46, NAME_Y = 28, NAME_W = 138, NAME_H = 16;

    private final FactoryControllerScreen controller;
    private final UUID network;

    /** Staged icon copied into the network settings on commit (empty ⇒ default icon). */
    private ItemStack icon = ItemStack.EMPTY;
    private EditBox nameBox;
    private TooltipIconButton clearButton;
    private TooltipIconButton confirmButton;

    private int panelX, panelY;
    private int invBgX, invBgY;

    public NetworkSettingsScreen(FactoryControllerScreen controller, UUID network) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
              Component.translatable("createfactorycontroller.gui.network_settings"));
        this.controller = controller;
        this.network = network;
        this.icon = controller.getMenu().networkIcon(network).copy();
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_OPEN.get(), 1f));
    }

    /** The default name shown when no custom name is set — matches the selector's fallback label. */
    private Component defaultName() {
        return NetworkSettings.defaultFor(network).displayName();
    }

    @Override
    protected void init() {
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        // Centre the panel + player-inventory block together (like SetItemScreen), so the player can drop
        // an inventory item onto the icon slot.
        int blockH = PANEL_H + INV_TEX_H;
        panelX = leftPos + (imageWidth - PANEL_W) / 2;
        panelY = topPos + (imageHeight - blockH) / 2;

        // Inventory background below the panel; the slot grid is derived from it so slots line up with the
        // texture recesses (hotbar row at INV_TEX_HOTBAR_Y).
        invBgX = panelX + (PANEL_W - INV_TEX_W) / 2;
        invBgY = panelY + PANEL_H + 6;
        int originX = invBgX + 8 - leftPos;                    // slot grid origin
        int hotbarY = (invBgY + INV_TEX_HOTBAR_Y) - topPos;    // hotbar row
        menu.repositionSlots(originX, hotbarY, true);

        // Two buttons at the top-left: clear (reset to default) then confirm.
        clearButton = new TooltipIconButton(panelX + PANEL_W - 47, panelY + PANEL_H - 24, AllIcons.I_TRASH);
        clearButton.withCallback(this::clearToDefault);
        clearButton.setToolTip(CreateLang.translate("gui.factory_panel.reset").component());
        addWidget(clearButton);

        confirmButton = new TooltipIconButton(panelX + PANEL_W - 25, panelY + PANEL_H - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::returnToController);
        confirmButton.setToolTip(CommonComponents.GUI_DONE);
        addWidget(confirmButton);

        // Name box in the selector recess; empty shows the default name as a hint. Plain font ⇒ drawn with shadow.
        String currentName = nameBox != null ? nameBox.getValue() : menu.networkSettings(network).name();
        nameBox = new EditBox(font,
            panelX + NAME_X + 4, panelY + NAME_Y + (NAME_H - 8) / 2, NAME_W - 8, 10, Component.empty());
        nameBox.setMaxLength(FactoryControllerBlockEntity.MAX_NAME_LENGTH);
        nameBox.setBordered(false);
        nameBox.setTextColor(0xFFFFFF);
        nameBox.setHint(defaultName());
        nameBox.setValue(currentName);
        addWidget(nameBox);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickBulbs();   // keep the background board's indicator bulbs animating
    }

    @Override
    public void onPanelSync() {
        controller.onPanelSync();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox.isFocused() && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            nameBox.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    /** Resets the staged name + icon to empty (default) without closing the window. */
    private void clearToDefault() {
        nameBox.setValue("");
        nameBox.setFocused(false);
        icon = ItemStack.EMPTY;
        playClickSound();
    }

    private void returnToController() {
        commit();
        Minecraft.getInstance().setScreen(controller);
    }

    /** Sends the staged settings to the server, with an optimistic mirror update so the UI is instant.
     *  No-ops when nothing changed, so merely opening/closing doesn't resync every controller on the network. */
    private void commit() {
        NetworkSettings staged = new NetworkSettings(network, nameBox.getValue().strip(), icon.copy());
        if (staged.matches(menu.networkSettings(network))) return;   // unchanged — don't resync everyone
        menu.networkSettings.put(network, staged);   // optimistic; the server resync corrects/authorizes
        PacketDistributor.sendToServer(
            new SetNetworkSettingsPacket(menu.controllerPos, network, staged.name(), staged.icon()));
    }

    private void setIconFromCarried(ItemStack source, boolean allowClear) {
        if (source.isEmpty()) {
            if (allowClear) icon = ItemStack.EMPTY;   // click with empty hand clears back to the default icon
            return;
        }
        icon = plainIcon(source);   // ghost — the held stack is not consumed
    }

    /** Icon is a pure item-type token — strip any NBT/data components so it can't drag along, e.g., a
     *  specific enchantment or custom name. */
    private static ItemStack plainIcon(ItemStack source) {
        return new ItemStack(source.getItem());
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        if (overIcon(mouseX, mouseY)) {
            if (!icon.isEmpty()) gfx.renderTooltip(font, icon, mouseX, mouseY);
            else gfx.renderTooltip(font, Component.translatable("createfactorycontroller.gui.network_settings.icon_tip")
                    .withStyle(ChatFormatting.GRAY), mouseX, mouseY);
        } else if (overNameBox(mouseX, mouseY) && !nameBox.isFocused()) {
            gfx.renderTooltip(font, Component.translatable("createfactorycontroller.gui.network_settings.name_tip")
                    .withStyle(ChatFormatting.GRAY), mouseX, mouseY);
        }
        renderTooltip(gfx, mouseX, mouseY);   // hovered inventory item
        TooltipIconButton.renderFirstTooltip(gfx, font, mouseX, mouseY, clearButton, confirmButton);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        TiledSpriteRenderer.create(PANEL_SPRITE).render(gfx, panelX, panelY, PANEL_W, PANEL_H - PANEL_BOTTOM_H + 1);
        TiledSpriteRenderer.create(PANEL_BOTTOM_SPRITE).render(gfx, panelX, panelY + PANEL_H - PANEL_BOTTOM_H, PANEL_W, PANEL_BOTTOM_H);
        TiledSpriteRenderer.create(PANEL_BOTTOM_VDIV).render(gfx, panelX + PANEL_W - 53, panelY + PANEL_H - PANEL_BOTTOM_H, 2, PANEL_BOTTOM_H);
        gfx.blit(PLAYER_INVENTORY_TEX, invBgX, invBgY, 0, 0, INV_TEX_W, INV_TEX_H);

        // Icon slot: the staged custom item, or the selector's default tinted network icon.
        renderNetworkIcon(gfx, new NetworkSettings(network, "", icon), panelX + ICON_X, panelY + ICON_Y);

        if (overIcon(mouseX, mouseY))
            gfx.fill(panelX + ICON_X, panelY + ICON_Y, panelX + ICON_X + ICON_SIZE, panelY + ICON_Y + ICON_SIZE, 0x80FFFFFF);

        // Name box
        TiledSpriteRenderer.create(ELEMENT_BORDER_SPRITE).render(gfx, panelX + NAME_X - 2, panelY + NAME_Y - 2, NAME_W + 4, NAME_H + 4);
        TiledSpriteRenderer.create(INPUT_FIELD_SPRITE).render(gfx, panelX + NAME_X - 1 , panelY + NAME_Y - 1, NAME_W + 2, NAME_H + 2);

        clearButton.render(gfx, mouseX, mouseY, partialTick);
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
        nameBox.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderNetworkIcon(GuiGraphics gfx, NetworkSettings settings, int x, int y) {
        TiledSpriteRenderer.create(ELEMENT_BORDER_SPRITE).render(gfx, x - 2, y - 2, ICON_SIZE + 4, ICON_SIZE + 4);
        gfx.fill(x - 1, y - 1, x + ICON_SIZE + 1, y + ICON_SIZE + 1,
                settings.hasCustomIcon() ? 0xFF8B8B8B : settings.backgroundColor());
        RenderSystem.enableBlend();
        gfx.blitSprite(NETWORK_SLOT_SPRITE, x - 1, y - 1, ICON_SIZE + 2, ICON_SIZE + 2);
        if (settings.hasCustomIcon()) {
            gfx.renderItem(settings.icon(), x, y);
        } else {
            int rgb = settings.color();
            gfx.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
            gfx.blitSprite(NETWORK, x, y, ICON_SIZE, ICON_SIZE);
            gfx.setColor(1f, 1f, 1f, 1f);
        }
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

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overIcon(mouseX, mouseY)) {
            setIconFromCarried(menu.getCarried(), true);
            playClickSound();
            return true;
        }
        if (overNameBox(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, button);
            if (button == 1) {
                nameBox.setValue("");
                nameBox.setFocused(true);
                setFocused(nameBox);
                playClickSound();
            }
            return true;
        }
        nameBox.setFocused(false);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(@NotNull Slot slot, int slotId, int mouseButton, @NotNull ClickType type) {
        // Shift-click an inventory item → set it as the icon (a ghost copy; the item stays in the inventory).
        if (type == ClickType.QUICK_MOVE && slot.hasItem()) {
            icon = plainIcon(slot.getItem());
            playClickSound();
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
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }

    private boolean overIcon(double mx, double my) {
        return mx >= panelX + ICON_X && mx < panelX + ICON_X + ICON_SIZE
            && my >= panelY + ICON_Y && my < panelY + ICON_Y + ICON_SIZE;
    }

    private boolean overNameBox(double mx, double my) {
        return mx >= panelX + NAME_X && mx < panelX + NAME_X + NAME_W
            && my >= panelY + NAME_Y && my < panelY + NAME_Y + NAME_H;
    }

    // ── JEI/EMI ghost-slot hooks ──
    public Rect2i ghostSlotArea() { return new Rect2i(panelX + ICON_X, panelY + ICON_Y, ICON_SIZE, ICON_SIZE); }
    public void setGhostFromJei(ItemStack stack) { if (!stack.isEmpty()) icon = plainIcon(stack); }
}
