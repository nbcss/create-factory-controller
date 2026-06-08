package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import io.github.nbcss.content.factorycontroller.ComponentRegistry;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
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

import static net.minecraft.client.gui.screens.Screen.hasShiftDown;

/**
 * Vertical network picker shown in the controller canvas, drawn with the {@code network_selector}
 * sprites: a nine-slice {@code panel} background, a {@code selection_box} around the selected entry,
 * {@code network}/{@code no_network} row icons, and {@code arrow_up}/{@code arrow_down} indicators
 * when entries are scrolled off the top/bottom.
 *
 * <p>A "no network" entry is always on top, then known networks sorted by component count (desc), and
 * — while holding a component tuned to an unknown frequency — a "new network" entry (green "+") at the
 * bottom. At most {@value #VISIBLE} rows show at once, windowed around the selection. While holding a
 * component the selection mirrors that item's tuned frequency and scrolling re-tunes the item;
 * otherwise scrolling just moves the selection. The screen reads {@link #getSelectedNetwork()} to
 * highlight that network's components.</p>
 *
 * <p>Only the layout offsets below are hardcoded; every sprite's pixel size is read from the GUI
 * sprite manager at render time so the art can change size without touching this class.</p>
 */
@OnlyIn(Dist.CLIENT)
public class NetworkSelectorWidget extends AbstractWidget {

    // Hardcoded layout (top-left offsets relative to the panel origin); sizes come from the sprites.
    private static final int ICON_X         = 5;    // icon top-left X within the panel
    private static final int FIRST_ICON_Y   = 5;    // first icon top-left Y within the panel
    private static final int ROW_PITCH      = 22;   // vertical distance between icon rows
    private static final int SELECTION_X    = 0;    // selection-box top-left X within the panel
    private static final int SELECTION_DY   = -5;   // selection-box top-left Y relative to its icon
    private static final int ARROW_X        = 9;    // arrow top-left X within the panel
    private static final int VISIBLE        = 5;    // max rows shown at once

    private static final int NEW_PLUS_COLOR = 0xFF55FF55;   // green "+"

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath("createfactorycontroller",
                "factory_controller/network_selector/" + name);
    }
    private static final ResourceLocation PANEL        = sprite("panel");
    private static final ResourceLocation SELECTION    = sprite("selection_box");
    private static final ResourceLocation NETWORK      = sprite("network");
    private static final ResourceLocation NO_NETWORK   = sprite("no_network");
    private static final ResourceLocation ARROW_UP     = sprite("arrow_up");
    private static final ResourceLocation ARROW_DOWN   = sprite("arrow_down");

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
        super(x, y, 0, 0, Component.empty());   // width/height are set from the panel sprite each render
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
        ItemStack carried = menu.getCarried();
        return ComponentRegistry.containsItem(carried) ? carried : ItemStack.EMPTY;
    }

    @Nullable
    private static UUID freqOf(ItemStack stack) {
        return LogisticallyLinkedBlockItem.isTuned(stack) ? LogisticallyLinkedBlockItem.networkFromStack(stack) : null;
    }

    /** A stable RGB tint for a network: the last 6 hex digits of its UUID (a valid 0xRRGGBB value). */
    static int networkColor(UUID network) {
        String s = network.toString();
        try {
            return Integer.parseInt(s.substring(s.length() - 6), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
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
        list.add(new Entry(Type.NO_NETWORK, null));                 // always top

        List<UUID> known = new ArrayList<>(menu.knownNetworks);
        known.sort(Comparator.comparingInt(menu::componentCountIn).reversed());
        for (UUID u : known) list.add(new Entry(Type.KNOWN, u));

        UUID heldFreq = freqOf(heldComponent());
        if (heldFreq != null && !menu.knownNetworks.contains(heldFreq))
            list.add(new Entry(Type.NEW_NETWORK, heldFreq));        // bottom, only while holding such an item
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

    /**
     * First entry index of the {@value #VISIBLE}-row window: with ≤5 entries everything shows; else the
     * selection stays two rows from the top (centre) where possible, clamped to the ends.
     */
    private static int windowStart(int selected, int n) {
        return n <= VISIBLE ? 0 : Mth.clamp(selected - VISIBLE / 2, 0, n - VISIBLE);
    }

    /**
     * Rendered panel height for {@code rows} visible icons. The panel sprite is sized for two rows, so
     * each row beyond the second adds one pitch (and a single row shrinks it by one pitch).
     */
    private int panelHeight(int rows) {
        return rows <= 0 ? 0 : spriteH(PANEL) + (rows - 2) * ROW_PITCH;
    }

    private int iconY(int slot) { return getY() + FIRST_ICON_Y + slot * ROW_PITCH; }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    public boolean isMouseOver(double mx, double my) {
        int vis = Math.min(buildEntries().size(), VISIBLE);
        return visible && mx >= getX() && mx < getX() + spriteW(PANEL) && my >= getY() && my < getY() + panelHeight(vis);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!isMouseOver(mx, my)) return false;
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
        int n = entries.size();
        int vis = Math.min(n, VISIBLE);
        int panelH = panelHeight(vis);
        this.width = spriteW(PANEL);
        this.height = panelH;
        if (n == 0) return;

        int sel = selectedIndex(entries);
        int start = windowStart(sel, n);
        Font font = Minecraft.getInstance().font;

        RenderSystem.enableBlend();

        // Nine-slice panel background.
        gfx.blitSprite(PANEL, getX(), getY(), spriteW(PANEL), panelH);

        // Selection box behind the selected icon (always inside the window).
        gfx.blitSprite(SELECTION, getX() + SELECTION_X, iconY(sel - start) + SELECTION_DY,
                spriteW(SELECTION), spriteH(SELECTION));

        // Row icons + decorations. Network icons are tinted by a per-network colour (no_network isn't).
        for (int slot = 0; slot < vis; slot++) {
            Entry e = entries.get(start + slot);
            ResourceLocation icon = e.type == Type.NO_NETWORK ? NO_NETWORK : NETWORK;
            int ix = getX() + ICON_X;
            int iy = iconY(slot);
            if (e.network != null) {
                int c = networkColor(e.network);
                gfx.setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
                gfx.blitSprite(icon, ix, iy, spriteW(icon), spriteH(icon));
                gfx.setColor(1f, 1f, 1f, 1f);
            } else {
                gfx.blitSprite(icon, ix, iy, spriteW(icon), spriteH(icon));
            }
            if (e.type == Type.KNOWN)
                drawDecoration(gfx, font, ix, iy, spriteW(icon), spriteH(icon),
                        String.valueOf(menu.componentCountIn(e.network)), 0xFFFFFFFF);
            else if (e.type == Type.NEW_NETWORK)
                drawDecoration(gfx, font, ix, iy, spriteW(icon), spriteH(icon), "+", NEW_PLUS_COLOR);
        }

        RenderSystem.enableBlend();
        // Scroll arrows when entries are hidden above/below the window.
        int ax = getX() + ARROW_X;
        if (start > 0)
            gfx.blitSprite(ARROW_UP, ax, getY() - 7, spriteW(ARROW_UP), spriteH(ARROW_UP));
        if (start + vis < n)
            gfx.blitSprite(ARROW_DOWN, ax, getY() + panelH - 2, spriteW(ARROW_DOWN), spriteH(ARROW_DOWN));
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
