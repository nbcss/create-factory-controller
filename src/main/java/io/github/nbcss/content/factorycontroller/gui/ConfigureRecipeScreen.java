package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.VirtualComponentBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualGaugeBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualPanelConnection;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import io.github.nbcss.content.factorycontroller.packet.ConfigureRecipePacket;
import io.github.nbcss.content.factorycontroller.packet.RemoveConnectionPacket;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe-configuration overlay for a virtual gauge — a replica of Create's {@code FactoryPanelScreen}
 * in recipe mode (the virtual board only ever has recipe-mode gauges). Like {@link SetItemScreen} it
 * is a separate screen that <b>shares the controller's {@link FactoryControllerMenu}</b> and draws the
 * live board as a dimmed backdrop. It edits, for the gauge: the produced output count, each incoming
 * connection's ingredient amount, the recipe (packager) address, and the promise-clearing interval;
 * confirm saves, trash resets. Backed by the provided {@code factory_gauge.png} panel texture.
 */
@OnlyIn(Dist.CLIENT)
public class ConfigureRecipeScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> {

    private static final ResourceLocation PANEL_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/factory_gauge.png");
    private static final int PANEL_W = 200, PANEL_H = 184;

    private final FactoryControllerScreen controller;
    private final VirtualPanelPosition gaugePos;

    // Panel top-left, centred in the GUI rect (same approach as SetItemScreen).
    private int panelX, panelY;

    // Editable state (committed on confirm). Initialised from the gauge's client snapshot.
    private int outputCount = 1;
    private final List<VirtualPanelPosition> inputPositions = new ArrayList<>();
    private final List<Integer> inputAmounts = new ArrayList<>();

    private EditBox addressBox;
    private ScrollInput promiseExpiration;
    private IconButton confirmButton;
    private IconButton deleteButton;

    public ConfigureRecipeScreen(FactoryControllerScreen controller, VirtualPanelPosition gaugePos) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
              CreateLang.translate("gui.factory_panel.title_as_recipe").component());
        this.controller = controller;
        this.gaugePos = gaugePos;
    }

    @Override
    protected void init() {
        // Match the controller's GUI rect so the backdrop fills and JEI's layout stays consistent.
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        // We show no inventory here — push all shared-menu slots off-screen (the controller re-lays
        // them out in its own init() when we return).
        menu.repositionSlots(-10000, -10000, false);

        panelX = leftPos + (imageWidth - PANEL_W) / 2;
        panelY = topPos + (imageHeight - PANEL_H) / 2;

        snapshotFromGauge();

        addressBox = new EditBox(font, panelX + 36, panelY + PANEL_H - 51, 108, 10, Component.empty());
        addressBox.setBordered(false);
        addressBox.setMaxLength(64);
        addressBox.setTextColor(0x555555);
        VirtualGaugeBehaviour g = gauge();
        addressBox.setValue(g == null ? "" : g.recipeAddress);
        addWidget(addressBox);

        promiseExpiration = new ScrollInput(panelX + 97, panelY + PANEL_H - 24, 28, 16)
            .withRange(-1, 31)
            .titled(CreateLang.translate("gui.factory_panel.promises_expire_title").component());
        promiseExpiration.setState(g == null ? -1 : g.promiseClearingInterval);
        addWidget(promiseExpiration);

        confirmButton = new IconButton(panelX + PANEL_W - 33, panelY + PANEL_H - 25, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::confirmAndReturn);
        confirmButton.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());
        addWidget(confirmButton);

        deleteButton = new IconButton(panelX + PANEL_W - 55, panelY + PANEL_H - 25, AllIcons.I_TRASH);
        deleteButton.withCallback(this::deleteAndReturn);
        deleteButton.setToolTip(CreateLang.translate("gui.factory_panel.reset").component());
        addWidget(deleteButton);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    // ── State helpers ────────────────────────────────────────────────────────

    @Nullable
    private VirtualGaugeBehaviour gauge() {
        for (VirtualComponentBehaviour c : menu.components)
            if (c instanceof VirtualGaugeBehaviour g && g.position().equals(gaugePos))
                return g;
        return null;
    }

    private ItemStack ingredientOf(VirtualPanelPosition pos) {
        for (VirtualComponentBehaviour c : menu.components)
            if (c instanceof VirtualGaugeBehaviour g && g.position().equals(pos))
                return g.filter;
        return ItemStack.EMPTY;
    }

    private void snapshotFromGauge() {
        inputPositions.clear();
        inputAmounts.clear();
        VirtualGaugeBehaviour g = gauge();
        if (g == null) return;
        outputCount = Math.max(1, g.recipeOutput);
        for (VirtualPanelConnection conn : g.targetedBy().values()) {
            inputPositions.add(conn.from);
            inputAmounts.add(Math.max(1, conn.amount));
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // Live board backdrop (dimmed); renderBoard clears depth + dims when inOverlay is true.
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        RenderSystem.enableBlend();
        gfx.blit(PANEL_TEX, panelX, panelY, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        VirtualGaugeBehaviour g = gauge();

        // INPUTS — ingredient items of incoming connections, 3 per row, with their amounts.
        for (int i = 0; i < inputPositions.size(); i++) {
            int ix = panelX + 68 + (i % 3) * 20;
            int iy = panelY + 28 + (i / 3) * 20;
            ItemStack stack = ingredientOf(inputPositions.get(i));
            gfx.renderItem(stack, ix, iy);
            if (!stack.isEmpty())
                gfx.renderItemDecorations(font, stack, ix, iy, String.valueOf(inputAmounts.get(i)));
        }

        // OUTPUT — the gauge's filter and produced count.
        if (g != null && !g.filter.isEmpty()) {
            int ox = panelX + 160;
            int oy = panelY + 48;
            gfx.renderItem(g.filter, ox, oy);
            gfx.renderItemDecorations(font, g.filter, ox, oy, String.valueOf(outputCount));
        }

        // 3D gauge preview + the filter floating in front of it (matches Create's right-side preview).
        GuiGameElement.of(AllBlocks.FACTORY_GAUGE.asStack())
            .scale(4).at(0, 0, -200).render(gfx, panelX + 195, panelY + 55);
        if (g != null && !g.filter.isEmpty())
            GuiGameElement.of(g.filter).scale(1.625).at(0, 0, 100).render(gfx, panelX + 214, panelY + 68);

        // Widgets (added via addWidget for event routing; drawn manually on top of the panel).
        addressBox.render(gfx, mouseX, mouseY, partialTick);
        promiseExpiration.render(gfx, mouseX, mouseY, partialTick);
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
        deleteButton.render(gfx, mouseX, mouseY, partialTick);

        // Promise-interval label over the scroll box.
        int state = promiseExpiration.getState();
        String label = state == -1 ? " /" : state == 0 ? "30s" : state + "m";
        gfx.drawString(font, label, promiseExpiration.getX() + 3, promiseExpiration.getY() + 4, 0xFFEEEEEE, true);
    }

    @Override
    protected void renderForeground(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(font, title, panelX + 97 - font.width(title) / 2, panelY + 4, 0x3D3C48, false);
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Left-click an input slot → disconnect that ingredient (mirrors Create).
        for (int i = 0; i < inputPositions.size(); i++) {
            int ix = panelX + 68 + (i % 3) * 20;
            int iy = panelY + 28 + (i / 3) * 20;
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                PacketDistributor.sendToServer(
                    new RemoveConnectionPacket(menu.controllerPos, inputPositions.get(i), gaugePos));
                inputPositions.remove(i);
                inputAmounts.remove(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = hasShiftDown() ? 10 : 1;

        for (int i = 0; i < inputPositions.size(); i++) {
            int ix = panelX + 68 + (i % 3) * 20;
            int iy = panelY + 28 + (i / 3) * 20;
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                inputAmounts.set(i, Mth.clamp((int) (inputAmounts.get(i) + Math.signum(scrollY) * step), 1, 64));
                return true;
            }
        }

        int ox = panelX + 160, oy = panelY + 48;
        if (mouseX >= ox && mouseX < ox + 16 && mouseY >= oy && mouseY < oy + 16) {
            outputCount = Mth.clamp((int) (outputCount + Math.signum(scrollY) * step), 1, 64);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        confirmAndReturn();   // ESC saves and returns to the controller (Create saves on close too)
    }

    // ── Commit ───────────────────────────────────────────────────────────────

    private void confirmAndReturn() {
        PacketDistributor.sendToServer(new ConfigureRecipePacket(
            menu.controllerPos, gaugePos, addressBox.getValue(), outputCount,
            promiseExpiration.getState(), new ArrayList<>(inputPositions),
            new ArrayList<>(inputAmounts), false));
        Minecraft.getInstance().setScreen(controller);
    }

    private void deleteAndReturn() {
        PacketDistributor.sendToServer(new ConfigureRecipePacket(
            menu.controllerPos, gaugePos, "", 1, -1, new ArrayList<>(), new ArrayList<>(), true));
        Minecraft.getInstance().setScreen(controller);
    }
}
