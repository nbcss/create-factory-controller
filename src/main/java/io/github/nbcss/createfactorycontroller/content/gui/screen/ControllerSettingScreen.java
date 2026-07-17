package io.github.nbcss.createfactorycontroller.content.gui.screen;

import io.github.nbcss.createfactorycontroller.content.gui.widget.ScrollListWindow;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only overlay for picking the controller's board background texture.
 */
@OnlyIn(Dist.CLIENT)
public class ControllerSettingScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> {

    private static final ResourceLocation PANEL_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/settings.png");
    private static final int PANEL_W = 205, PANEL_H = 88;

    /** The resource folder (under assets/<namespace>/) scanned for background textures. */
    private static final String BACKGROUND_DIR = "textures/gui/controller_background";

    // Selector box + preview slot geometry (panel-relative), derived from the settings.png recesses.
    private static final int PREVIEW_X = 22, PREVIEW_Y = 28;
    private static final int SELECTOR_X = 46, SELECTOR_Y = 29, SELECTOR_W = 138, SELECTOR_H = 16;

    private final FactoryControllerScreen controller;

    /** Available background names (no path prefix / .png suffix); just the current selection until the async
     *  scan (see {@link #scanOptionsAsync}) lands the full sorted set. */
    private final List<String> options = new ArrayList<>();
    private int selected;
    /** The applied background's name, tracked independently of {@link #options} so a scan landing after the
     *  player has already scrolled/reset can still re-resolve the right index. */
    private String currentName;
    /** Set on {@link #removed()} so a scan that lands after the screen is gone doesn't touch state. */
    private boolean disposed = false;

    private int panelX, panelY;
    private TooltipIconButton closeButton;
    private TooltipIconButton resetButton;

    public ControllerSettingScreen(FactoryControllerScreen controller) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
              Component.translatable("createfactorycontroller.gui.controller_settings"));
        this.controller = controller;
        currentName = ClientConfig.getControllerBackground();
        options.add(currentName);   // only known option until the scan resolves
        selected = 0;
        scanOptionsAsync();
    }

    /** Lists the .png files under {@link #BACKGROUND_DIR} in our namespace off the render thread (resource-pack
     *  listing does real I/O), then swaps the full sorted set in on the client thread once ready. */
    private void scanOptionsAsync() {
        Util.backgroundExecutor().execute(() -> {
            List<String> found = Minecraft.getInstance().getResourceManager()
                    .listResources(BACKGROUND_DIR, loc ->
                            loc.getNamespace().equals(CreateFactoryController.MODID) &&
                                    loc.getPath().endsWith(".png"))
                    .keySet().stream()
                    .map(ResourceLocation::getPath)
                    .map(p -> p.substring(BACKGROUND_DIR.length() + 1, p.length() - ".png".length()))
                    .sorted()
                    .toList();
            Minecraft.getInstance().execute(() -> applyScannedOptions(found));
        });
    }

    private void applyScannedOptions(List<String> found) {
        if (disposed || found.isEmpty()) return;
        options.clear();
        options.addAll(found);
        selected = Math.max(0, options.indexOf(currentName));
    }

    @Override
    protected void init() {
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        // No inventory on this overlay — push all shared-menu slots off-screen (like ConfigureRecipeScreen).
        menu.repositionSlots(-10000, -10000, false);

        panelX = leftPos + (imageWidth - PANEL_W) / 2;
        panelY = topPos + (imageHeight - PANEL_H) / 2;

        // Reset (left) then close/confirm (right), bottom-right of the panel.
        resetButton = new TooltipIconButton(panelX + 158, panelY + 64, AllIcons.I_CONFIG_RESET);
        resetButton.withCallback(this::resetToDefault);
        resetButton.setToolTip(CreateLang.translate("gui.factory_panel.reset").component());
        addWidget(resetButton);

        closeButton = new TooltipIconButton(panelX + 180, panelY + 64, AllIcons.I_CONFIRM);
        closeButton.withCallback(this::returnToController);
        closeButton.setToolTip(CommonComponents.GUI_DONE);
        addWidget(closeButton);
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

    // ── Selection ─────────────────────────────────────────────────────────────

    private ResourceLocation textureOf(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID,
            BACKGROUND_DIR + "/" + name + ".png");
    }

    /** Applies the current index to the live config (immediate effect on the board behind us). */
    private void applySelection() {
        if (selected >= 0 && selected < options.size()) {
            currentName = options.get(selected);
            ClientConfig.setControllerBackground(currentName);
        }
    }

    /** Scroll the selector: up → previous, down → next, clamped (no wrap), with Create's scroll chime. */
    private void scrollSelection(double scrollY) {
        if (options.isEmpty()) return;
        int next = Mth.clamp(selected - (int) Math.signum(scrollY), 0, options.size() - 1);
        if (next == selected) return;
        selected = next;
        applySelection();
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 1.5f));
    }

    private void resetToDefault() {
        String def = ClientConfig.defaultControllerBackground();
        currentName = def;
        int idx = options.indexOf(def);
        if (idx >= 0) selected = idx;
        ClientConfig.setControllerBackground(def);
    }

    private void returnToController() {
        ClientConfig.save();   // persist the background selection to disk (set() only updates memory) before leaving
        Minecraft.getInstance().setScreen(controller);
    }

    private boolean overSelector(double mx, double my) {
        return mx >= panelX + SELECTOR_X && mx < panelX + SELECTOR_X + SELECTOR_W
            && my >= panelY + SELECTOR_Y && my < panelY + SELECTOR_Y + SELECTOR_H;
    }

    private List<Component> selectorTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("createfactorycontroller.gui.controller_settings")
            .withColor(ScrollInput.HEADER_RGB.getRGB()));

        // Fixed-height centred window with "> ..." markers for hidden rows (see ScrollListWindow).
        for (int i : ScrollListWindow.rows(options.size(), selected)) {
            if (i == ScrollListWindow.MARKER) {
                lines.add(Component.literal("> ...").withStyle(ChatFormatting.GRAY));
                continue;
            }
            boolean sel = i == selected;
            lines.add(Component.literal(sel ? "-> " : "> ").append(options.get(i))
                .withStyle(sel ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }

        lines.add(CreateLang.translate("gui.scrollInput.scrollToSelect")
            .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());
        return lines;
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        if (overSelector(mouseX, mouseY) && !options.isEmpty())
            gfx.renderComponentTooltip(font, selectorTooltip(), mouseX, mouseY);
        renderTooltip(gfx, mouseX, mouseY);
        TooltipIconButton.renderFirstTooltip(gfx, font, mouseX, mouseY, resetButton, closeButton);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        gfx.blit(PANEL_TEX, panelX, panelY, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        // Preview of the current selection (textures are 16px; drawn 16×16 into the slot).
        if (!options.isEmpty())
            gfx.blit(textureOf(options.get(selected)), panelX + PREVIEW_X, panelY + PREVIEW_Y, 0, 0, 16, 16, 16, 16);

        // Current option name, centred in the selector box.
        gfx.enableScissor(panelX + SELECTOR_X, panelY + SELECTOR_Y,
                panelX + SELECTOR_X + SELECTOR_W - 4, panelY + SELECTOR_Y + SELECTOR_H);
        String name = options.isEmpty() ? "—" : options.get(selected);
        int textY = panelY + SELECTOR_Y + (SELECTOR_H - font.lineHeight) / 2 + 1;
        gfx.drawString(font, name, panelX + SELECTOR_X + 4, textY, 0xFFFFFF, true);
        gfx.disableScissor();

        resetButton.render(gfx, mouseX, mouseY, partialTick);
        closeButton.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        // Title centred in the header strip (dark text on the light bar).
        Component title = getTitle();
        gfx.drawString(font, title, panelX + PANEL_W / 2 - font.width(title) / 2, panelY + 4, 0x3D3C48, false);
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}   // no default container labels

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overSelector(mouseX, mouseY)) {
            scrollSelection(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        returnToController();   // return to the controller without closing the shared container
    }

    @Override
    public void removed() {
        disposed = true;   // drop a scan that lands after this screen is gone
        super.removed();
    }
}
