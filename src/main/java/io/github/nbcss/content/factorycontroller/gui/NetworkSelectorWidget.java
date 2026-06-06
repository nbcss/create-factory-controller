package io.github.nbcss.content.factorycontroller.gui;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import io.github.nbcss.content.factorycontroller.ComponentRegistry;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Vertical network picker shown in the controller canvas.
 *
 * <p>A "no network" (barrier) entry is always on top, followed by the known networks sorted by
 * component count (desc); while holding a valid component item tuned to a frequency the controller
 * doesn't know yet, a "new network" entry (green "+") is appended at the bottom. Known entries show
 * their component count as a bottom-right decoration.</p>
 *
 * <p>There is always a selected entry. While holding a component item the selection mirrors that
 * item's tuned frequency and scrolling <b>re-tunes the item</b> (clearing it on "no network");
 * otherwise the selection is a free-standing choice that scrolling moves. The screen reads
 * {@link #getSelectedNetwork()} to highlight that network's components.</p>
 */
@OnlyIn(Dist.CLIENT)
public class NetworkSelectorWidget extends AbstractWidget {

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP  = 2;
    private static final int PADDING   = 2;
    public static final int WIDGET_W   = PADDING + SLOT_SIZE + PADDING;

    private static final int SELECT_COLOR   = 0xFF66CCFF;
    private static final int SLOT_BG        = 0xFF404040;
    private static final int NEW_PLUS_COLOR = 0xFF55FF55;   // green "+"

    /** Applies a re-tune of the carried item: {@code clear} untunes it, else tune to {@code network}. */
    @FunctionalInterface
    public interface RetuneHandler { void retune(boolean clear, @Nullable UUID network); }

    private enum Kind { NO_NETWORK, KNOWN, NEW_NETWORK }
    private record Entry(Kind kind, @Nullable UUID network) {}

    private final FactoryControllerMenu menu;
    private final RetuneHandler retune;

    // Persistent selection (used when not holding a component; mirrored from the held item otherwise).
    private Kind selKind = Kind.NO_NETWORK;
    @Nullable private UUID selNet = null;
    private boolean wasHolding = false;

    public NetworkSelectorWidget(int x, int y, FactoryControllerMenu menu, RetuneHandler retune) {
        super(x, y, WIDGET_W, 0, Component.empty());
        this.menu = menu;
        this.retune = retune;
    }

    public void setPosition(int x, int y) { setX(x); setY(y); }

    /** The network whose components should be highlighted, or {@code null} for "no network". */
    @Nullable
    public UUID getSelectedNetwork() {
        syncSelection();
        return selKind == Kind.NO_NETWORK ? null : selNet;
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

    /**
     * While holding a component the selection mirrors its tuned frequency; when the cursor changes
     * from a component to no component it resets to "no network"; otherwise the selection persists.
     */
    private void syncSelection() {
        ItemStack held = heldComponent();
        boolean holding = !held.isEmpty();
        if (holding) {
            UUID freq = freqOf(held);
            if (freq == null) { selKind = Kind.NO_NETWORK; selNet = null; }
            else if (menu.knownNetworks.contains(freq)) { selKind = Kind.KNOWN; selNet = freq; }
            else { selKind = Kind.NEW_NETWORK; selNet = freq; }
        } else if (wasHolding) {
            selKind = Kind.NO_NETWORK; selNet = null;   // component → no component: back to "no network"
        }
        wasHolding = holding;
    }

    private List<Entry> buildEntries() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(Kind.NO_NETWORK, null));                 // always top

        List<UUID> known = new ArrayList<>(menu.knownNetworks);
        known.sort(Comparator.comparingInt(menu::componentCountIn).reversed());
        for (UUID u : known) list.add(new Entry(Kind.KNOWN, u));

        UUID heldFreq = freqOf(heldComponent());
        if (heldFreq != null && !menu.knownNetworks.contains(heldFreq))
            list.add(new Entry(Kind.NEW_NETWORK, heldFreq));        // bottom, only while holding such an item
        return list;
    }

    private int selectedIndex(List<Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (selKind == Kind.NO_NETWORK ? e.kind == Kind.NO_NETWORK
                                           : selNet != null && selNet.equals(e.network))
                return i;
        }
        return 0;   // fall back to "no network"
    }

    private static int heightFor(int n) {
        return n == 0 ? 0 : PADDING + n * SLOT_SIZE + (n - 1) * SLOT_GAP + PADDING;
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    public boolean isMouseOver(double mx, double my) {
        int h = heightFor(buildEntries().size());
        return visible && mx >= getX() && mx < getX() + WIDGET_W && my >= getY() && my < getY() + h;
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
        selKind = e.kind;
        selNet = e.network;
        // While holding a component the scroll re-tunes it; otherwise it just moves the selection.
        if (!heldComponent().isEmpty())
            retune.retune(e.kind == Kind.NO_NETWORK, e.network);
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 1.5f));
        return true;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        syncSelection();
        List<Entry> entries = buildEntries();
        this.height = heightFor(entries.size());
        if (entries.isEmpty()) return;

        int sel = selectedIndex(entries);
        Font font = Minecraft.getInstance().font;

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int slotX = getX() + PADDING;
            int slotY = getY() + PADDING + i * (SLOT_SIZE + SLOT_GAP);

            if (i == sel)
                gfx.fill(slotX - 1, slotY - 1, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE + 1, SELECT_COLOR);
            gfx.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_BG);

            ItemStack icon = e.kind == Kind.NO_NETWORK
                ? new ItemStack(Items.BARRIER)
                : new ItemStack(AllBlocks.STOCK_LINK.get());
            gfx.renderItem(icon, slotX + 1, slotY + 1);

            if (e.kind == Kind.KNOWN)
                drawDecoration(gfx, font, slotX + 1, slotY + 1, String.valueOf(menu.componentCountIn(e.network)), 0xFFFFFFFF);
            else if (e.kind == Kind.NEW_NETWORK)
                drawDecoration(gfx, font, slotX + 1, slotY + 1, "+", NEW_PLUS_COLOR);
        }
    }

    /** Bottom-right corner decoration over a 16-px item icon, like a vanilla stack count. */
    private static void drawDecoration(GuiGraphics gfx, Font font, int itemX, int itemY, String text, int color) {
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 200);
        gfx.drawString(font, text, itemX + 17 - font.width(text), itemY + 9, color, true);
        gfx.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
