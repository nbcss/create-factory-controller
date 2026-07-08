package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Single-slot network picker shown in the controller canvas, drawn with the {@code selection_frame}
 * sprite. Only the <b>currently selected</b> network (or "no network", or — while holding a component
 * tuned to an unknown frequency — a "new network" entry) is shown at any time; there is no list,
 * window, or scroll arrows.
 *
 * <p>The underlying selection model is unchanged: a "no network" entry, the known networks sorted by
 * component count (desc), and a trailing "new network" entry while holding a freshly-tuned component.
 * Scrolling moves through that ordered set (re-tuning the held item when one is held); the screen reads
 * {@link #getSelectedNetwork()} to highlight that network's components.</p>
 *
 * <p>The slot is painted in four layers (back to front): a high-contrast background fill auto-derived
 * from the icon colour, the {@code selection_frame}, the network icon tinted by its per-network colour
 * (last 6 hex digits of the UUID), and the component-count (or "+") label.</p>
 */
@OnlyIn(Dist.CLIENT)
public class NetworkSelectorWidget extends AbstractWidget {

    private static final int NEW_PLUS_COLOR = 0xFF55FF55;   // green "+"
    /** Neutral slot fill for the "no network" entry (its icon carries no per-network colour). */
    private static final int NO_NETWORK_COLOR = 0xFF6E6E6E;
    private static final int ICON_NETWORK_BACKGROUND = 0xAAAAAAAA;

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath("createfactorycontroller",
                "factory_controller/network_selector/" + name);
    }
    private static final ResourceLocation SELECTION_FRAME = sprite("selection_frame");
    private static final ResourceLocation NETWORK         = sprite("network");
    private static final ResourceLocation NO_NETWORK      = sprite("no_network");

    private static int spriteW(ResourceLocation loc) {
        return Minecraft.getInstance().getGuiSprites().getSprite(loc).contents().width();
    }
    private static int spriteH(ResourceLocation loc) {
        return Minecraft.getInstance().getGuiSprites().getSprite(loc).contents().height();
    }

    /** Applies a re-tune of the carried item: {@code clear} untunes it, else tune to {@code network}. */
    @FunctionalInterface
    public interface RetuneHandler { void retune(boolean clear, @Nullable UUID network); }

    private enum Type { NO_NETWORK, KNOWN, NEW_NETWORK }
    private record Entry(Type type, @Nullable UUID network) {}

    private final FactoryControllerMenu menu;
    private final RetuneHandler retune;
    private final java.util.function.Consumer<UUID> onSelect;   // notified (network or null) when the selection changes

    // Persistent selection (used when not holding a component; mirrored from the held item otherwise).
    private Type selectType = Type.NO_NETWORK;
    @Nullable private UUID selectNetwork = null;
    private boolean wasHolding = false;

    public NetworkSelectorWidget(int x, int y, FactoryControllerMenu menu, RetuneHandler retune,
                                 java.util.function.Consumer<UUID> onSelect) {
        super(x, y, 0, 0, Component.empty());   // width/height are set from the frame sprite each render
        this.menu = menu;
        this.retune = retune;
        this.onSelect = onSelect;
    }

    public void setPosition(int x, int y) { setX(x); setY(y); }

    /** The network whose components should be highlighted, or {@code null} for "no network". */
    @Nullable
    public UUID getSelectedNetwork() {
        syncSelection();
        return selectType == Type.NO_NETWORK ? null : selectNetwork;
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    private ItemStack heldComponent() {
        // Only network-bound components (gauges) drive the selector; a held redstone link is networkless,
        // so it must NOT be re-tuned by scroll or trigger a "network selected" prompt when picked up.
        ItemStack carried = menu.getCarried();
        return ComponentRegistry.containsNetworkItem(carried) ? carried : ItemStack.EMPTY;
    }

    @Nullable
    private static UUID freqOf(ItemStack stack) {
        return LogisticallyLinkedBlockItem.isTuned(stack) ? LogisticallyLinkedBlockItem.networkFromStack(stack) : null;
    }

    /**
     * While holding a component the selection mirrors its tuned frequency; when the cursor changes
     * from a component to no component it resets to "no network"; otherwise the selection persists.
     */
    private void syncSelection() {
        ItemStack held = heldComponent();
        boolean holding = !held.isEmpty();
        if (holding) {
            UUID freq = freqOf(held);
            if (freq == null) {
                selectType = Type.NO_NETWORK;
                selectNetwork = null;
            } else if (menu.knownNetworks.contains(freq)) {
                selectType = Type.KNOWN;
                selectNetwork = freq;
            } else {
                selectType = Type.NEW_NETWORK;
                selectNetwork = freq;
            }
            if (!wasHolding) {
                onSelect.accept(selectType == Type.NO_NETWORK ? null : selectNetwork);
            }
        } else if (wasHolding) {
            selectType = Type.NO_NETWORK;
            selectNetwork = null;   // component → no component: back to "no network"
        }
        wasHolding = holding;
    }

    private List<Entry> buildEntries() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(Type.NO_NETWORK, null));                 // always first

        List<UUID> known = new ArrayList<>(menu.knownNetworks);
        known.sort(Comparator.comparingInt(menu::componentCountIn).reversed());
        for (UUID u : known) list.add(new Entry(Type.KNOWN, u));

        UUID heldFreq = freqOf(heldComponent());
        if (heldFreq != null && !menu.knownNetworks.contains(heldFreq))
            list.add(new Entry(Type.NEW_NETWORK, heldFreq));        // last, only while holding such an item
        return list;
    }

    private int selectedIndex(List<Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (selectType == Type.NO_NETWORK ?
                    e.type == Type.NO_NETWORK :
                    selectNetwork != null && selectNetwork.equals(e.network))
                return i;
        }
        return 0;   // fall back to "no network"
    }

    // ── Tooltip ─────────────────────────────────────────────────────────────────

    private Component entryName(Entry e) {
        // A new network uses the same "Network #XXXX"/nickname naming as a known one (it just isn't tracked
        // yet); only the "no network" entry has a dedicated label.
        return e.type == Type.NO_NETWORK
                ? Component.translatable("createfactorycontroller.network.none")
                : menu.networkName(e.network);
    }

    /**
     * Hover tooltip: a "Network Selector" header, a windowed list of entries with the selected one marked
     * {@code "-> "} (others {@code "> "}), {@code "> ..."} rows when entries are hidden above/below, and
     * Create's scroll-to-select hint.
     *
     */
    public List<Component> getTooltipLines() {
        syncSelection();
        List<Entry> entries = buildEntries();
        int state = selectedIndex(entries);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("createfactorycontroller.gui.network_selector")
                .withColor(ScrollInput.HEADER_RGB.getRGB()));

        // Fixed-height centred window with "> ..." markers for hidden rows (see ScrollListWindow).
        for (int i : ScrollListWindow.rows(entries.size(), state)) {
            if (i == ScrollListWindow.MARKER) {
                lines.add(Component.literal("> ...").withStyle(ChatFormatting.GRAY));
                continue;
            }
            Entry e = entries.get(i);
            boolean selected = i == state;
            // The "new network" entry is coloured green for the whole line (it would create a fresh network);
            // otherwise the selected row is white and the rest gray.
            ChatFormatting color = e.type == Type.NEW_NETWORK ? ChatFormatting.GREEN
                    : selected ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            lines.add(Component.literal(selected ? "-> " : "> ").append(entryName(e)).withStyle(color));
        }

        if (menu.knownNetworks.isEmpty())
            lines.add(Component.translatable("createfactorycontroller.gui.network_selector_no_network_tip")
                    .withStyle(ChatFormatting.RED));

        if (heldComponent().isEmpty()) {
            // Empty-handed over a real (known) network → the whole slot is clickable to configure it.
            if (entries.get(state).type == Type.KNOWN)
                lines.add(Component.translatable("createfactorycontroller.gui.action_configure")
                        .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("createfactorycontroller.gui.network_selector_scroll_highlight_tip")
                    .withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
        } else {
            lines.add(Component.translatable("createfactorycontroller.gui.network_selector_scroll_tune_tip")
                    .withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
        }

        return lines;
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    public boolean isMouseOver(double mx, double my) {
        return visible && mx >= getX() && mx < getX() + spriteW(SELECTION_FRAME)
                && my >= getY() && my < getY() + spriteH(SELECTION_FRAME);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!isMouseOver(mx, my)) return false;
        scrollSelection(scrollY);
        return true;
    }

    /**
     * Advances the selection by the scroll direction (re-tuning a held component), independent of cursor
     * position — so the screen can route a shift+scroll anywhere over the canvas here. Returns {@code true}
     * if it consumed the scroll (always; a clamp boundary is still "handled").
     */
    public boolean scrollSelection(double scrollY) {
        syncSelection();
        List<Entry> entries = buildEntries();
        int cur = selectedIndex(entries);
        int next = Mth.clamp(cur - (int) Math.signum(scrollY), 0, entries.size() - 1);
        if (next == cur) return true;   // at a clamp boundary — nothing changes
        Entry e = entries.get(next);
        selectType = e.type;
        selectNetwork = e.network;
        onSelect.accept(e.type == Type.NO_NETWORK ? null : e.network);   // prompt the player
        // While holding a component the scroll re-tunes it; otherwise it just moves the selection.
        if (!heldComponent().isEmpty())
            retune.retune(e.type == Type.NO_NETWORK, e.network);
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 1.5f));
        return true;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        syncSelection();
        List<Entry> entries = buildEntries();
        int frameW = spriteW(SELECTION_FRAME);
        int frameH = spriteH(SELECTION_FRAME);
        this.width = frameW;
        this.height = frameH;
        if (entries.isEmpty()) return;

        Entry e = entries.get(selectedIndex(entries));
        Font font = Minecraft.getInstance().font;
        NetworkSettings settings = e.network != null ? menu.networkSettings(e.network) : null;
        int rgb = settings != null ? settings.color() : NO_NETWORK_COLOR;
        int backgroundColor = settings != null ? settings.backgroundColor() : NetworkSettings.LIGHT_FILL;

        // A known network may carry a shared custom icon item; it is drawn plain (no tint) over a neutral fill.
        ItemStack customIcon = e.type == Type.KNOWN && settings != null ? settings.icon() : ItemStack.EMPTY;
        boolean hasCustomIcon = !customIcon.isEmpty();

        RenderSystem.enableBlend();

        // A custom item icon still gets a filled slot backdrop — just a neutral one, not the network tint.
        gfx.fill(getX() + 4, getY() + 4, getX() + 22, getY() + 22,
                hasCustomIcon ? ICON_NETWORK_BACKGROUND : backgroundColor);

        RenderSystem.enableBlend();
        gfx.blitSprite(SELECTION_FRAME, getX(), getY(), frameW, frameH);

        ResourceLocation icon = e.type == Type.NO_NETWORK ? NO_NETWORK : NETWORK;
        int iconW = spriteW(icon);
        int iconH = spriteH(icon);
        int ix = getX() + 5;
        int iy = getY() + 5;

        if (hasCustomIcon) {
            gfx.renderItem(customIcon, ix, iy);
        } else {
            gfx.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
            gfx.blitSprite(icon, ix, iy, iconW, iconH);
            gfx.setColor(1f, 1f, 1f, 1f);
        }

        // 4. Component-count label (known networks) or the green "+" (new network).
        if (e.type == Type.KNOWN)
            drawDecoration(gfx, font, ix, iy, iconW, iconH,
                    String.valueOf(menu.componentCountIn(e.network)), 0xFFFFFFFF);
        else if (e.type == Type.NEW_NETWORK)
            drawDecoration(gfx, font, ix, iy, iconW, iconH, "+", NEW_PLUS_COLOR);
    }

    /** Bottom-right corner decoration over an icon, drawn with an 8-direction outline (like the gauge count). */
    private static void drawDecoration(GuiGraphics gfx, Font font, int iconX, int iconY, int iconW, int iconH,
                                       String text, int color) {
        int x = iconX + iconW + 1 - font.width(text);
        int y = iconY + iconH - 7;
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 200);
        Matrix4f matrix = gfx.pose().last().pose();
        font.drawInBatch8xOutline(Component.literal(text).getVisualOrderText(), x, y, color, 0x000000,
                matrix, gfx.bufferSource(), LightTexture.FULL_BRIGHT);
        gfx.flush();
        gfx.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
