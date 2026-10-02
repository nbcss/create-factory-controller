package io.github.nbcss.createfactorycontroller.content.gui.screen.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.createfactorycontroller.content.item.terminal.RemoteControllerLink;
import io.github.nbcss.createfactorycontroller.content.item.FactoryControllerTerminalItem;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import io.github.nbcss.createfactorycontroller.registry.CFCItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class TerminalTabStrip extends AbstractWidget {
    private static final int TAB_W = 32;
    private static final int TAB_H = 28;

    private static final ResourceLocation TAB_SELECTED =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tab_selected");
    private static final ResourceLocation TAB_UNSELECTED =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tab_unselected");

    private final ItemStack icon = new ItemStack(CFCItems.FACTORY_CONTROLLER.get());
    private final IntConsumer onSelect;
    private final IntConsumer onUnlink;
    /** Live name of the selected controller (follows renames; a link's cached name only updates on retarget). */
    private final Supplier<Component> activeName;

    private List<RemoteControllerLink> links = List.of();
    private int activeIndex;

    public TerminalTabStrip(IntConsumer onSelect, IntConsumer onUnlink, Supplier<Component> activeName) {
        super(0, 0, TAB_W, 0, Component.empty());
        this.onSelect = onSelect;
        this.onUnlink = onUnlink;
        this.activeName = activeName;
    }

    /** Anchors the strip to the board window's top-left corner, as the advancement tabs are to theirs. */
    public void anchor(int leftPos, int topPos) {
        setPosition(leftPos - TAB_W + 3, topPos + 20);
    }

    /** Shows one tab per link (hidden with none), highlighting {@code activeIndex}. */
    public void setLinks(List<RemoteControllerLink> links, int activeIndex) {
        this.links = links;
        this.activeIndex = activeIndex;
        this.height = TAB_H * links.size();
        this.visible = this.active = !links.isEmpty();
    }

    private int tabY(int index) {
        return getY() + TAB_H * index;
    }

    /** Index of the tab under the cursor, or -1. */
    public int tabAt(double mouseX, double mouseY) {
        if (!visible || !isMouseOver(mouseX, mouseY)) return -1;
        int index = (int) ((mouseY - getY()) / TAB_H);
        return index >= 0 && index < links.size() ? index : -1;
    }

    /** Tooltip for the hovered tab (the controller's name), or null when no tab is hovered. */
    @Nullable
    /** Tooltip of the hovered tab: the controller's name and the unlink hint (empty when no tab is hovered). */
    public List<Component> tooltipAt(double mouseX, double mouseY) {
        int tab = tabAt(mouseX, mouseY);
        if (tab < 0) return List.of();
        return List.of(nameOf(tab),
                Component.translatable("createfactorycontroller.terminal.tab.remove").withStyle(ChatFormatting.DARK_GRAY));
    }

    private Component nameOf(int tab) {
        return tab == activeIndex ? activeName.get()
                : FactoryControllerTerminalItem.controllerDisplayName(links.get(tab).cachedName());
    }

    /** Screen area the strip occupies (for JEI exclusion). */
    public Rect2i area() {
        return new Rect2i(getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int tab = tabAt(mouseX, mouseY);
        if (tab < 0) return false;
        if (Screen.hasShiftDown()) onUnlink.accept(tab);
        else if (tab != activeIndex) onSelect.accept(tab);
        return true;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        for (int i = 0; i < links.size(); i++) {
            if (i == activeIndex) {
                TiledSpriteRenderer.create(TAB_SELECTED).render(graphics, getX(), tabY(i), TAB_W, TAB_H);
            } else {
                TiledSpriteRenderer.create(TAB_UNSELECTED).render(graphics, getX() + 3, tabY(i), TAB_W - 4, TAB_H);
            }
        }
        RenderSystem.disableBlend();
        for (int i = 0; i < links.size(); i++)
            graphics.renderFakeItem(icon, getX() + 10, tabY(i) + 8);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
        if (!links.isEmpty())
            output.add(NarratedElementType.TITLE, nameOf(activeIndex));
    }
}
