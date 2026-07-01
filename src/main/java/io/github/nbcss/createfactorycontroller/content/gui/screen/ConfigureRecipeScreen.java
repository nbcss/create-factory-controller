package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import io.github.nbcss.createfactorycontroller.content.render.FluidGuiRender;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import net.createmod.catnip.gui.element.ScreenElement;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.RequestMode;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureRecipePacket;
import io.github.nbcss.createfactorycontroller.content.packet.DisconnectIngredientPacket;
import io.github.nbcss.createfactorycontroller.content.packet.DisconnectLinksPacket;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recipe-configuration overlay for a virtual gauge — a replica of Create's {@code FactoryPanelScreen}
 * (recipe mode): threshold row (filter+stock / count / Item-Stack) like {@code ThresholdSwitchScreen},
 * an open-promise package box, and mechanical-crafting recipe detection. Shares the controller's
 * {@link FactoryControllerMenu} and draws the live board as a dimmed backdrop.
 */
@OnlyIn(Dist.CLIENT)
public class ConfigureRecipeScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> implements PanelSyncListener {
    private static final ResourceLocation PANEL_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/configure_recipe.png");
    private static final int PANEL_W = 200, PANEL_H = 184;

    // Threshold-row geometry (filter slot x24-40, count box x48-112, unit box x118-167, band y≈128-144).
    /** The input arrangement is a 3×3 grid, so at most 9 slots (incl. repeats) can be shown. */
    private static final int MAX_INPUT_SLOTS = 9;
    /** A request's produced output must fit one stack; batch is capped so {@code batch × yield ≤ 64}. */
    private static final int MAX_CRAFT_OUTPUT = 64;
    private static final int THRESH_TOP = 128;
    private static final int FILTER_X = 25;
    private static final int COUNT_X = 51, COUNT_W = 40;
    private static final int UNIT_X = 95, UNIT_W = 50;
    private static final int REQUEST_MODE_BTN_X = 150;
    private static final int THRESH_H = 18;
    private static final int PROMISE_CLEAR_X = 10;
    private static final int PROMISE_TIMEOUT_X = 44, PROMISE_TIMEOUT_W = 32;
    private static final int PROMISE_LIMIT_X = 92, PROMISE_LIMIT_W = 42;
    private static final int LINK_RESET_X = 160, LINK_RESET_Y = 25;

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition gaugePos;

    private int panelX, panelY;

    // Editable / derived state.
    private int outputCount = 1;
    /** Crafts per request in crafting mode (≥1). The output slot shows outputCount × craftBatch. */
    private int craftBatch = 1;
    /** Square crafter-grid size (N→N×N) a recipe is laid out for; defaults to its minimum (max of recipe
     *  width/height), Ctrl-scrollable up to {@link ServerConfig#maxCraftGridSize()}. 0 until a recipe loads. */
    private int craftDimension = 0;
    private int thresholdCount = 0;
    /** Click-to-type editing of the count box (non-passive only): true while typing, with the in-progress digits. */
    private boolean countEditing = false;
    private String countEdit = "";
    private ThresholdUnit mode = ThresholdUnit.ITEMS;
    private RequestMode requestMode = RequestMode.NORMAL;
    /** True when the gauge's filter is a fluid filter: the threshold/output amounts are then
     *  millibuckets, shown/edited in mB/B with fluid scroll steps. Set once per open in {@link #updateConfigs}. */
    private boolean fluidMode = false;
    // One entry per input CONNECTION (not per grid slot): the source gauge and its TOTAL item count.
    // The 3×3 grid layout — full stacks first, one partial last slot, contiguous per connection, packed
    // in connection order — is derived on demand from these via layoutInputSlots().
    private final List<VirtualComponentPosition> inputConnections = new ArrayList<>();
    private final List<Integer> inputTotals = new ArrayList<>();
    private final List<BigItemStack> inputConfig = new ArrayList<>();   // for crafting-recipe search (per connection)

    @Nullable private CraftingRecipe availableCraftingRecipe;
    private boolean craftingActive;
    private List<BigItemStack> craftingIngredients = new ArrayList<>();
    /** Set in renderBg when Ctrl is held over a crafting ingredient, so renderForeground draws the N×M layout. */
    private boolean patternHovered;

    private AddressEditBox addressBox;
    private ScrollInput promiseExpiration;
    private int promiseLimitState = -1;
    private IconButton confirmButton;
    private IconButton deleteButton;
    private IconButton newInputButton;
    private IconButton relocateButton;
    @Nullable private IconButton craftingButton;
    /** Cycles the gauge's {@link RequestMode}; its icon reflects the current mode. */
    @Nullable private IconButton requestModeButton;

    public ConfigureRecipeScreen(FactoryControllerScreen controller, VirtualComponentPosition gaugePos) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
              CreateLang.translate("gui.factory_panel.title_as_recipe").component());
        this.controller = controller;
        this.gaugePos = gaugePos;
        updateConfigs();   // snapshot once (not per init, so edits/crafting toggle survive resize)

        // Chime when the overlay opens — played client-side for this player only.
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_OPEN.get(), 1f));
    }

    @Override
    protected void init() {
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        // No inventory here — push all shared-menu slots off-screen.
        menu.repositionSlots(-10000, -10000, false);

        panelX = leftPos + (imageWidth - PANEL_W) / 2;
        panelY = topPos + (imageHeight - PANEL_H) / 2;

        VirtualGaugeBehaviour g = gauge();

        // Preserve any unsaved edits across re-init (the crafting toggle re-runs init()).
        String address = addressBox != null ? addressBox.getValue() : (g == null ? "" : g.recipeAddress);
        int promiseState = promiseExpiration != null ? promiseExpiration.getState()
                                                     : (g == null ? -1 : g.promiseClearingInterval);
        int limitState = promiseLimitState >= 0 ? promiseLimitState : (g == null ? 0 : g.promiseLimit);

        // Create's address box with frogport-address autocomplete (DestinationSuggestions). It caps
        // length at 25 and renders its own suggestion dropdown; we only style it to match the panel.
        addressBox = new AddressEditBox(this, new NoShadowFontWrapper(font),
            panelX + 36, panelY + PANEL_H - 77, 108, 10, false);
        addressBox.setBordered(false);
        addressBox.setTextColor(0x555555);
        addressBox.setValue(address);
        addWidget(addressBox);

        promiseExpiration = new ScrollInput(panelX + PROMISE_TIMEOUT_X, panelY + PANEL_H - 24, PROMISE_TIMEOUT_W, 16)
            .withRange(-1, 31)
            .titled(CreateLang.translate("gui.factory_panel.promises_expire_title").component());
        promiseExpiration.setState(promiseState);
        addWidget(promiseExpiration);

        promiseLimitState = limitState;

        confirmButton = new IconButton(panelX + PANEL_W - 33, panelY + PANEL_H - 25, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::confirmAndReturn);
        confirmButton.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());
        addWidget(confirmButton);

        deleteButton = new IconButton(panelX + PANEL_W - 55, panelY + PANEL_H - 25, AllIcons.I_TRASH);
        deleteButton.withCallback(this::deleteAndReturn);
        deleteButton.setToolTip(CreateLang.translate("gui.factory_panel.reset").component());
        addWidget(deleteButton);

        newInputButton = new IconButton(panelX + 31, panelY + 47, AllIcons.I_ADD);
        newInputButton.withCallback(() -> {
            sendConfig(false, false);   // commit edits before leaving the screen
            controller.beginConnectionMode(gaugePos);
            Minecraft.getInstance().setScreen(controller);
        });
        newInputButton.setToolTip(CreateLang.translate("gui.factory_panel.connect_input").component());
        addWidget(newInputButton);

        relocateButton = new IconButton(panelX + 31, panelY + 67, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> {
            sendConfig(false, false);   // commit edits (incl. repeated slots) before leaving the screen
            controller.beginRelocateMode(gaugePos);
            Minecraft.getInstance().setScreen(controller);
        });
        relocateButton.setToolTip(CreateLang.translate("gui.factory_panel.relocate").component());
        addWidget(relocateButton);

        // Mechanical-crafting toggle — only when the inputs+output match a crafting recipe.
        craftingButton = null;
        if (availableCraftingRecipe != null) {
            craftingButton = new IconButton(panelX + 31, panelY + 27, AllIcons.I_3x3);
            craftingButton.green = craftingActive;   // glows green while crafting mode is on (like Create)
            craftingButton.withCallback(() -> {
                if (availableCraftingRecipe == null) return;   // recipe vanished (e.g. input removed)
                craftingActive = !craftingActive;
                if (craftingActive) applyCraftingResolution();   // build the square arrangement + clamps
                rebuildWidgets();   // clears + re-runs init() (direct init() would duplicate widgets)
            });
            // No self-tooltip: drawn last in renderForeground (craftingButtonTooltip) so neighbours can't cover it.
            addWidget(craftingButton);
        }

        // Request-mode cycle button — right of the unit box. Its icon reflects the current mode (not coloured).
        requestModeButton = new IconButton(panelX + REQUEST_MODE_BTN_X, panelY + THRESH_TOP - 1, iconFor(requestMode));
        requestModeButton.withCallback(this::cycleRequestMode);
        addWidget(requestModeButton);
    }

    /** The button icon for each request mode. */
    private static ScreenElement iconFor(RequestMode mode) {
        String sprite = switch (mode) {
            case NORMAL -> "icons/set_target";
            case PASSIVE -> "icons/follows_demand";
            case PASSIVE_AND_ALLOW_ORDER -> "icons/follows_demand_with_order";
        };
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, sprite);
        return (gfx, x, y) -> gfx.blitSprite(loc, x, y, 16, 16);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        addressBox.tick();          // drives the address autocomplete (DestinationSuggestions)
        controller.tickBulbs();     // keep the background board's indicator bulbs animating
    }

    // ── State ────────────────────────────────────────────────────────────────

    @Nullable
    private VirtualGaugeBehaviour gauge() {
        return menu.componentAt(gaugePos) instanceof VirtualGaugeBehaviour g ? g : null;
    }

    private ItemStack ingredientOf(VirtualComponentPosition pos) {
        return menu.componentAt(pos) instanceof VirtualGaugeBehaviour g ? g.filter : ItemStack.EMPTY;
    }

    /** Whether input connection {@code c}'s ingredient is a fluid — its amount is then in
     *  millibuckets, occupies a single slot (no stack split), and scrolls with fluid steps. */
    private boolean isFluidConn(int c) {
        return FluidCompat.isFluidFilter(ingredientOf(inputConnections.get(c)));
    }

    /** Per-ingredient millibucket cap (90 B) */
    private static final int FLUID_INGREDIENT_CAP_MB = 90_000;

    /** A grid slot's source: which input connection it belongs to, and how many items sit in it. */
    private record InputSlot(int connectionIndex, int amount) {}

    private static int stackSizeOf(ItemStack stack) {
        return stack.isEmpty() ? 1 : Math.max(1, stack.getMaxStackSize());
    }

    /**
     * Lays the input connections out into grid slots: each connection fills full stacks first then a single
     * partial last slot (so no slot exceeds the item's stack size), its slots contiguous, connections packed
     * in order — capped at {@link #MAX_INPUT_SLOTS}. Recomputed wherever the grid is drawn or hit-tested.
     */
    //FIXME check
    private List<InputSlot> layoutInputSlots() {
        List<InputSlot> slots = new ArrayList<>();
        for (int c = 0; c < inputConnections.size() && slots.size() < MAX_INPUT_SLOTS; c++) {
            int total = Math.max(1, inputTotals.get(c));
            if (isFluidConn(c)) {                       // a fluid ingredient is one slot of mB (never stack-split)
                slots.add(new InputSlot(c, total));
                continue;
            }
            int ss = stackSizeOf(ingredientOf(inputConnections.get(c)));
            int remaining = total;
            do {
                if (slots.size() >= MAX_INPUT_SLOTS) return slots;
                int amt = Math.min(ss, remaining);
                slots.add(new InputSlot(c, amt));
                remaining -= amt;
            } while (remaining > 0);
        }
        return slots;
    }

    /** Slots used by every connection except {@code exceptIndex} (for the remaining-slot budget on scroll). */
    private int slotsUsedExcept(int exceptIndex) {
        int used = 0;
        for (int c = 0; c < inputConnections.size(); c++) {
            if (c == exceptIndex) continue;
            if (isFluidConn(c)) { used += 1; continue; }     // fluid ingredient = one slot
            int ss = stackSizeOf(ingredientOf(inputConnections.get(c)));
            int total = Math.max(1, inputTotals.get(c));
            used += (total + ss - 1) / ss;   // ceil
        }
        return used;
    }

    /**
     * Scrolls a connection's total (shared by all its slots): plain ±1 item, shift ±10 (snapping to the
     * next stack-size boundary when it would otherwise cross one from a non-boundary start), ctrl ±1 full
     * stack. Clamped to ≥1 and to the item count that still fits the free grid slots.
     */
    private void adjustInputTotal(int connectionIndex, int dir, boolean shift, boolean ctrl) {
        if (isFluidConn(connectionIndex)) {              // fluid ingredient: millibuckets, fluid steps, one slot
            int curMb = Math.max(1, inputTotals.get(connectionIndex));
            inputTotals.set(connectionIndex, adjustFluidAmount(curMb, dir, shift, ctrl, 1, FLUID_INGREDIENT_CAP_MB));
            return;
        }
        int ss = stackSizeOf(ingredientOf(inputConnections.get(connectionIndex)));
        int cur = Math.max(1, inputTotals.get(connectionIndex));

        int maxSlots = Math.max(1, MAX_INPUT_SLOTS - slotsUsedExcept(connectionIndex));
        int maxTotal = maxSlots * ss;

        int next;
        if (ctrl) {
            next = snapToStack(cur, dir, ss);            // snap to the next/previous full-stack value
        } else if (shift) {
            next = cur + dir * 10;
            if (cur % ss != 0) {                         // snap across a stack boundary (not from a boundary)
                int boundary = dir > 0 ? (cur / ss + 1) * ss : (cur / ss) * ss;
                if (dir > 0 ? next > boundary : next < boundary) next = boundary;
            }
        } else {
            next = cur + dir;                            // ±1 item
        }
        inputTotals.set(connectionIndex, Mth.clamp(next, 1, maxTotal));
    }

    /**
     * Largest craft batch: the produced item must fit one stack, so {@code batch × yield ≤ 64}. Input slots
     * never cap the batch — re-packaging splits a multi-craft order into one single-craft package each, so any
     * batch is dispatchable as long as a single craft fits (enforced by {@link #craftingFitsPackage}).
     */
    private int maxCraftBatch() {
        return Math.max(1, MAX_CRAFT_OUTPUT / Math.max(1, outputCount));
    }

    /** Max stack size of the produced item (64 when no filter yet). */
    private int outputStackSize() {
        VirtualGaugeBehaviour g = gauge();
        return g == null || g.filter.isEmpty() ? 64 : Math.max(1, g.filter.getMaxStackSize());
    }

    /** Largest free (non-crafting) item output count: at least 64, or 9 full stacks of the produced item — so a
     *  stack-of-64 item allows up to 576, a stack-of-1 item stays at 64. */
    private int maxItemOutput() {
        return Math.max(64, 9 * outputStackSize());
    }

    /** Dark-gray " | &lt;stacks&gt;▤ +&lt;overflow&gt;" suffix breaking {@code count} of a stack-{@code ss} item into full
     *  stacks plus remainder (the {@code +overflow} omitted when {@code count} is a whole number of stacks). */
    private static MutableComponent stackBreakdown(int count, int ss) {
        int stacks = count / ss, overflow = count % ss;
        return Component.literal(" | " + stacks + "▤" + (overflow > 0 ? " +" + overflow : ""))
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Ctrl-scroll snap: move {@code cur} to the next full-stack multiple of {@code ss} in direction {@code dir} —
     *  up rounds to the next higher multiple, down to the next lower (below one stack → 0, then clamped to 1 by the
     *  caller). E.g. ss=64: 100↑→128, 100↓→64, 40↓→0. */
    private static int snapToStack(int cur, int dir, int ss) {
        return dir > 0 ? (cur / ss + 1) * ss : (cur - 1) / ss * ss;
    }

    /** Crafts per request actually used: forced to 1 when an ignore-data ingredient disables batching. */
    private int effectiveBatch() {
        return craftingUsesIgnoreData() ? 1 : Math.max(1, craftBatch);
    }

    /** Crafting mode: change crafts-per-request, capped so the produced output stays within one stack. */
    private void adjustCraftBatch(int delta) {
        int next = Mth.clamp(craftBatch + delta, 1, maxCraftBatch());
        if (next != craftBatch) {
            craftBatch = next;
            playScrollSound();
        }
    }

    /**
     * Ctrl-scroll over the recipe's ingredients: resize the square crafter grid, between the recipe's minimum
     * bounding square and the configured maximum ({@link ServerConfig#maxCraftGridSize()}). Re-lays the
     * dispatched pattern.
     */
    private void adjustCraftDimension(int dir) {
        int minDim = minCraftDim();
        int next = Mth.clamp(effectiveCraftDimension() + dir, minDim, maxCraftDim(minDim));
        if (next != craftDimension) {
            craftDimension = next;
            craftingIngredients = buildSquareArrangement(craftDimension);
            playScrollSound();
        }
    }

    /** Upper bound for the crafter-grid size: the server-configured maximum (synced to the client), but never
     *  below a recipe's own minimum bounding square. */
    private static int maxCraftDim(int minDim) {
        return Math.max(minDim, ServerConfig.maxCraftGridSize());
    }

    /** One drawn cell of the aggregated crafting view: an ingredient and how many sit in this grid slot. */
    private record CraftSlot(ItemStack stack, int amount) {}

    /**
     * Collapses the recipe arrangement into its distinct ingredient types (first-appearance order) and the
     * total cell-count each uses. Aggregating by item means a recipe that reuses one ingredient across many
     * cells (most recipes) needs only one type slot — the key to showing a &gt;3×3 recipe in the 3×3 grid.
     */
    private void craftingTypeAggregate(List<ItemStack> typesOut, List<Integer> cellsOut) {
        for (BigItemStack b : craftingIngredients) {
            if (b.stack.isEmpty()) continue;
            int amt = Math.max(1, b.stack.getCount());
            int idx = -1;
            for (int i = 0; i < typesOut.size(); i++)
                if (ItemStack.isSameItemSameComponents(typesOut.get(i), b.stack)) { idx = i; break; }
            if (idx < 0) { typesOut.add(b.stack); cellsOut.add(amt); }
            else cellsOut.set(idx, cellsOut.get(idx) + amt);
        }
    }

    /**
     * The aggregated grid view of a large (&gt;3×3) crafting recipe: one slot per distinct ingredient type,
     * its amount being the type's total usage (cells × batch). Crafting slots never overflow into a second
     * slot, so a count above the item's stack size simply shows as a large number in the one slot. Display
     * only — the dispatched package still carries the real N×M arrangement.
     */
    private List<CraftSlot> craftingDisplaySlots() {
        List<ItemStack> types = new ArrayList<>();
        List<Integer> cells = new ArrayList<>();
        craftingTypeAggregate(types, cells);
        List<CraftSlot> slots = new ArrayList<>();
        int batch = effectiveBatch();
        for (int t = 0; t < types.size() && slots.size() < MAX_INPUT_SLOTS; t++)
            slots.add(new CraftSlot(types.get(t), cells.get(t) * batch));
        return slots;
    }

    /**
     * Whether one craft fits a single 9-slot package: each distinct ingredient type occupies exactly one slot
     * (no stack-size overflow), and batch is irrelevant because re-packaging splits a multi-craft order into
     * one package per craft. A recipe with &gt;9 distinct types can't be packaged at all.
     */
    private boolean craftingFitsPackage() {
        List<ItemStack> types = new ArrayList<>();
        List<Integer> cells = new ArrayList<>();
        craftingTypeAggregate(types, cells);
        return types.size() <= MAX_INPUT_SLOTS;
    }

    /** True when the recipe is larger than 3×3 (shaped width or height &gt; 3) — the aggregated/Ctrl path. */
    private boolean craftingIsLarge() {
        return availableCraftingRecipe instanceof ShapedRecipe s && (s.getWidth() > 3 || s.getHeight() > 3);
    }

    /** Minimum square crafter grid for the recipe: its bounding square, but never below 3×3 — Create's
     *  packaged crafting unpacks into a 3×3-and-up crafter array, so a smaller recipe is laid top-left of a
     *  3×3 grid rather than a too-small one a standard crafter can't satisfy. */
    private int minCraftDim() {
        return Math.max(3, Math.max(craftingPatternWidth(), craftingPatternHeight()));
    }

    /** The square grid size a recipe lays out into: the stored value, floored at its minimum bounding square
     *  and capped at the configured maximum. Valid even before crafting is toggled on (defaults to min). */
    private int effectiveCraftDimension() {
        int minDim = minCraftDim();
        if (craftingUsesIgnoreData()) return minDim;   // grid resizing is disabled with an ignore-data ingredient
        return Mth.clamp(craftDimension, minDim, maxCraftDim(minDim));   // 0 (unset) → minDim
    }

    /** Inserts a gold "Ignore Data" line directly below the header line of a slot tooltip when the relevant
     *  gauge ignores item data (reuses Create's filter lang key — no new key needed). */
    private static List<Component> withIgnoreDataLine(List<Component> base, boolean ignoreData) {
        if (!ignoreData) return base;
        List<Component> out = new ArrayList<>(base);
        out.add(1, CreateLang.translate("gui.filter.ignore_data").style(ChatFormatting.GOLD).component());
        return out;
    }

    /** The crafting toggle's tooltip: the activate hint, plus the current N×N grid size. Rendered last (in
     *  {@link #renderForeground}) so a later-drawn neighbouring widget can't paint over it. */
    private List<Component> craftingButtonTooltip() {
        List<Component> tip = new ArrayList<>();
        tip.add(CreateLang.translate("gui.factory_panel.activate_crafting").component());
        if (availableCraftingRecipe != null) {
            int dim = effectiveCraftDimension();
            tip.add(Component.translatable("createfactorycontroller.gui.crafting_dimension", dim, dim)
                .withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }

    private void updateConfigs() {
        inputConnections.clear();
        inputTotals.clear();
        inputConfig.clear();
        VirtualGaugeBehaviour g = gauge();
        if (g == null) return;
        outputCount = Math.max(1, g.recipeOutput);
        craftBatch = Math.max(1, g.craftBatch);
        craftDimension = Math.max(0, g.craftDimension);
        thresholdCount = Math.max(0, g.count);
        mode = g.unit;
        requestMode = g.requestMode;
        promiseLimitState = g.promiseLimit;
        // Fluid filter: amounts are millibuckets. Coerce the unit into the fluid group,
        // and default a fresh gauge's output to 1000 mB (one bucket).
        fluidMode = FluidCompat.isFluidFilter(g.filter);
        if (fluidMode && !mode.isFluid()) mode = ThresholdUnit.FLUID_BUCKET;
        if (!fluidMode && mode.isFluid()) mode = ThresholdUnit.ITEMS;
        if (fluidMode && outputCount <= 1) outputCount = 1000;
        for (Connection conn : g.targetedBy().values()) {
            // A gauge holds only logistics (ingredient) wires; the UI re-derives the slot layout from this total.
            if (!(conn instanceof LogisticsConnection lc)) continue;
            int total = lc.amount();
            inputConnections.add(conn.from);
            inputTotals.add(total);
            inputConfig.add(new BigItemStack(ingredientOf(conn.from), total));
        }

        craftingActive = !g.activeCraftingArrangement.isEmpty();
        searchForCraftingRecipe();
        applyCraftingResolution();
    }

    /**
     * After {@link #searchForCraftingRecipe}, lays the recipe into a square crafter grid, locks the output to
     * the recipe yield, and enforces the package-fit gate (a recipe with &gt;9 distinct ingredient types can't
     * be packaged, so it's rejected). The square (default = the recipe's minimum bounding square, restored from
     * the gauge or Ctrl-scrollable) is what we dispatch, so the flat pattern maps cell-for-cell onto an N×N
     * mechanical-crafter array — fixing the misalignment Create's 3-wide package layout caused.
     */
    private void applyCraftingResolution() {
        if (availableCraftingRecipe == null) {
            craftingActive = false;
            craftingIngredients = new ArrayList<>();
            craftDimension = 0;
            return;
        }
        if (!craftingActive) return;
        lockOutputToRecipe();
        craftDimension = effectiveCraftDimension();   // 0 (unset) → minDim; forced to minDim with ignore-data
        craftingIngredients = buildSquareArrangement(craftDimension);
        if (!craftingFitsPackage()) {
            craftingActive = false;
            availableCraftingRecipe = null;   // hides the crafting toggle; falls back to plain ingredient request
            craftingIngredients = new ArrayList<>();
            craftDimension = 0;
            return;
        }
        craftBatch = Mth.clamp(craftBatch, 1, maxCraftBatch());
    }

    /**
     * Resolves a recipe cell's ingredient to the connected input stack that satisfies it (count 1), so the
     * dispatched pattern matches the items the package box will carry; an empty cell stays empty.
     */
    private ItemStack resolveCraftCell(Ingredient ing) {
        if (ing.isEmpty()) return ItemStack.EMPTY;
        for (BigItemStack b : inputConfig)
            if (!b.stack.isEmpty() && ing.test(b.stack)) return b.stack.copyWithCount(1);
        ItemStack[] items = ing.getItems();
        return items.length > 0 ? items[0].copyWithCount(1) : ItemStack.EMPTY;
    }

    /**
     * Lays the recipe's true W×H ingredients into a {@code dim×dim} grid (row-major), the rest empty — the
     * pattern that maps cell-for-cell onto a dim×dim mechanical-crafter array on unpacking.
     *
     * <p>A ≤3×3 recipe is centred within the top-left 3×3 block (matching Create's factory-panel layout, e.g.
     * a vertical stick at top-middle + centre, not the corner); larger recipes anchor at the top-left corner.
     * Centring inside the fixed 3×3 block (rather than the whole grid) keeps the recipe in the same on-screen
     * slots when the grid is scrolled larger.</p>
     */
    private List<BigItemStack> buildSquareArrangement(int dim) {
        List<BigItemStack> out = new ArrayList<>(dim * dim);
        int w = craftingPatternWidth(), h = craftingPatternHeight();
        var ings = availableCraftingRecipe.getIngredients();
        int colOff = craftingIsLarge() ? 0 : (3 - w) / 2;
        int rowOff = craftingIsLarge() ? 0 : (3 - h) / 2;
        for (int r = 0; r < dim; r++)
            for (int c = 0; c < dim; c++) {
                int rr = r - rowOff, cc = c - colOff;
                ItemStack s = (rr >= 0 && rr < h && cc >= 0 && cc < w && rr * w + cc < ings.size())
                    ? resolveCraftCell(ings.get(rr * w + cc)) : ItemStack.EMPTY;
                out.add(new BigItemStack(s, 1));
            }
        return out;
    }

    /** In crafting mode the output count is fixed to the recipe's yield (not user-scrollable). */
    private void lockOutputToRecipe() {
        ClientLevel level = Minecraft.getInstance().level;
        if (availableCraftingRecipe != null && level != null)
            outputCount = availableCraftingRecipe.getResultItem(level.registryAccess()).getCount();
    }

    /** Re-evaluates crafting availability after the input connections change, then rebuilds the layout. */
    private void onConnectionsChanged() {
        searchForCraftingRecipe();
        applyCraftingResolution();
        rebuildWidgets();   // clears + re-runs init(); button appears/disappears with availability
    }

    /** Reimplements Create's FactoryPanelScreen#searchForCraftingRecipe for our gauge's inputs/output. */
    private void searchForCraftingRecipe() {
        availableCraftingRecipe = null;
        VirtualGaugeBehaviour g = gauge();
        if (g == null || g.filter.isEmpty() || inputConfig.isEmpty()) return;

        ItemStack output = g.filter;
        Set<Item> itemsToUse = inputConfig.stream()
            .map(b -> b.stack).filter(i -> !i.isEmpty()).map(ItemStack::getItem).collect(Collectors.toSet());

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        // Vanilla 3×3 first; then Create's mechanical-crafting recipes, which can be larger than 3×3
        // (MechanicalCraftingRecipe extends ShapedRecipe, so it satisfies the same CraftingRecipe contract).
        availableCraftingRecipe = matchCraftingRecipe(level, output, itemsToUse, RecipeType.CRAFTING);
        if (availableCraftingRecipe == null) {
            RecipeType<MechanicalCraftingRecipe> mechanical = AllRecipeTypes.MECHANICAL_CRAFTING.getType();
            availableCraftingRecipe = matchCraftingRecipe(level, output, itemsToUse, mechanical);
        }
        // Ignore-data ingredients are resolved to concrete variants per request, which works in a fixed 3×3
        // grid but not a resizable larger one — so hide the toggle for a >3×3 recipe that uses one.
        if (craftingIsLarge() && craftingUsesIgnoreData()) availableCraftingRecipe = null;
    }

    /** Whether any wired ingredient ignores item data — disables crafting batch & crafter-grid resizing. */
    private boolean craftingUsesIgnoreData() {
        for (VirtualComponentPosition pos : inputConnections)
            if (menu.componentAt(pos) instanceof VirtualGaugeBehaviour s && s.ignoreData) return true;
        return false;
    }

    /**
     * Finds a recipe of {@code type} that produces {@code output} and whose every ingredient is covered by a
     * wired input (mirrors Create's {@code FactoryPanelScreen#searchForCraftingRecipe}). Generic over the
     * crafting recipe type so it serves both vanilla {@code CRAFTING} and Create's {@code MECHANICAL_CRAFTING}.
     */
    @Nullable
    private <T extends CraftingRecipe> CraftingRecipe matchCraftingRecipe(
            ClientLevel level, ItemStack output, Set<Item> itemsToUse, RecipeType<T> type) {
        return level.getRecipeManager()
            .getAllRecipesFor(type)
            .parallelStream()
            .filter(r -> output.getItem() == r.value().getResultItem(level.registryAccess()).getItem())
            // Reject recipes whose minimum crafter grid exceeds the configured maximum — they'd need a larger
            // crafter array than allowed, so crafting mode is simply unavailable for them (button never shows).
            .filter(r -> recipeFitsConfiguredGrid(r.value()))
            .filter(r -> {
                if (AllRecipeTypes.shouldIgnoreInAutomation(r)) return false;
                Set<Item> itemsUsed = new HashSet<>();
                for (Ingredient ingredient : r.value().getIngredients()) {
                    if (ingredient.isEmpty()) continue;
                    boolean available = false;
                    for (BigItemStack bis : inputConfig)
                        if (!bis.stack.isEmpty() && ingredient.test(bis.stack)) {
                            available = true;
                            itemsUsed.add(bis.stack.getItem());
                            break;
                        }
                    if (!available) return false;
                }
                return itemsUsed.size() >= itemsToUse.size();
            })
            .findAny()
            .<CraftingRecipe>map(RecipeHolder::value)
            .orElse(null);
    }

    /** Whether a recipe's minimum bounding square (≥3) fits the configured maximum crafter-grid size. Only
     *  shaped recipes can exceed 3×3; anything else is at most 3×3 and always fits. */
    private static boolean recipeFitsConfiguredGrid(CraftingRecipe r) {
        int min = r instanceof ShapedRecipe s ? Math.max(3, Math.max(s.getWidth(), s.getHeight())) : 3;
        return min <= ServerConfig.maxCraftGridSize();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void onPanelSync() {
        controller.onPanelSync();
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        if (patternHovered) renderCraftingPattern(gfx, mouseX, mouseY);   // Ctrl-held layout, drawn on top
        renderTooltip(gfx, mouseX, mouseY);
    }

    private int craftingPatternWidth() {
        if (availableCraftingRecipe instanceof ShapedRecipe shaped)
            return Math.max(1, shaped.getWidth());
        if (availableCraftingRecipe == null) return 1;
        return Math.min(3, Math.max(1, availableCraftingRecipe.getIngredients().size()));
    }

    private int craftingPatternHeight() {
        if (availableCraftingRecipe instanceof ShapedRecipe shaped)
            return Math.max(1, shaped.getHeight());
        if (availableCraftingRecipe == null) return 1;
        return Math.min(3, availableCraftingRecipe.getIngredients().size() / 3 + 1);
    }

    /**
     * Ctrl-held layout popup near the cursor: a header (with the current N×N grid size) and a scroll hint
     * above the recipe laid out in its dim×dim crafter grid (the exact pattern the package will unpack into,
     * read from {@link #craftingIngredients}). Ctrl-scrolling an ingredient resizes the grid live.
     */
    private void renderCraftingPattern(GuiGraphics gfx, int mouseX, int mouseY) {
        if (craftingIngredients.isEmpty()) return;
        int dim = effectiveCraftDimension();
        int cell = 17;
        int headerColor = 0xFF000000 | (ScrollInput.HEADER_RGB.getRGB() & 0xFFFFFF);   // blue, like tooltip headers
        Component l1 = Component.translatable("createfactorycontroller.gui.crafting_pattern", dim, dim);
        Component l2 = Component.translatable("createfactorycontroller.gui.crafting_scroll_dim")
            .withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC);
        int gridSpan = dim * cell + 1;                    // include the closing grid line
        int textBlockH = font.lineHeight * 2 + 2;
        int gap = 3;
        int contentW = Math.max(gridSpan, Math.max(font.width(l1), font.width(l2)));
        int contentH = textBlockH + gap + gridSpan;
        // Place + clamp like a vanilla tooltip (content origin; the background border extends a few px out).
        int margin = 6;
        int cx = Mth.clamp(mouseX + 12, margin, Math.max(margin, width - contentW - margin));
        int cy = Mth.clamp(mouseY - 12, margin, Math.max(margin, height - contentH - margin));

        gfx.pose().pushPose();
        // Vanilla tooltip frame (gradient border + dark fill), then draw content above it at z 400.
        TooltipRenderUtil.renderTooltipBackground(gfx, cx, cy, contentW, contentH, 400);
        gfx.pose().translate(0, 0, 400);
        gfx.drawString(font, l1, cx, cy, headerColor, false);
        gfx.drawString(font, l2, cx, cy + font.lineHeight + 2, 0xFFA0A0A0, false);

        int gridX = cx, gridY = cy + textBlockH + gap;
        int lineColor = 0xFF556088;
        for (int k = 0; k <= dim; k++) {
            gfx.fill(gridX + k * cell, gridY, gridX + k * cell + 1, gridY + dim * cell, lineColor);   // verticals
            gfx.fill(gridX, gridY + k * cell, gridX + dim * cell + 1, gridY + k * cell + 1, lineColor); // horizontals
        }
        for (int i = 0; i < craftingIngredients.size(); i++) {
            ItemStack s = craftingIngredients.get(i).stack;
            if (s.isEmpty()) continue;
            gfx.renderItem(s, gridX + (i % dim) * cell + 1, gridY + (i / dim) * cell + 1);
        }
        gfx.pose().popPose();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        RenderSystem.enableBlend();
        gfx.blit(PANEL_TEX, panelX, panelY, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        VirtualGaugeBehaviour g = gauge();
        List<Component> tooltip = null;

        // INPUTS — a ≤3×3 recipe fits the grid, so each arrangement cell maps to one input slot (legacy view).
        // A >3×3 recipe can't, so we aggregate to one slot per distinct ingredient type (count = cells × batch,
        // no slot overflow) and offer the real N×M layout in a Ctrl-held tooltip (drawn in render()).
        patternHovered = false;
        if (craftingActive && craftingIsLarge()) {
            List<CraftSlot> slots = craftingDisplaySlots();
            boolean hovering = false;
            // Walk all 9 grid cells (not just the filled ones) so the tooltip covers every slot, empty too.
            for (int i = 0; i < MAX_INPUT_SLOTS; i++) {
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                if (i < slots.size()) {
                    CraftSlot slot = slots.get(i);
                    gfx.renderItem(slot.stack(), ix, iy);
                    drawItemCount(gfx, slot.stack(), ix, iy, String.valueOf(slot.amount()));
                }
                if (in(mouseX, mouseY, ix, iy, 16, 16)) hovering = true;
            }
            if (hovering) {
                if (hasControlDown())
                    patternHovered = true;   // draw the N×M layout grid in render()
                else {
                    int dim = effectiveCraftDimension();
                    tooltip = List.of(
                        CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component(),
                        Component.translatable("createfactorycontroller.gui.crafting_unpacked").withStyle(ChatFormatting.GRAY),
                        Component.translatable("createfactorycontroller.gui.crafting_crafters", dim, dim).withStyle(ChatFormatting.GRAY),
                        Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
                }
            }
        } else if (craftingActive) {
            // ≤3×3: render the recipe's own cells straight into the grid, like Create's factory panel. The
            // arrangement is a dim×dim square, so we read its top-left 3×3 — the recipe keeps its pattern
            // position here even when the dimension is scrolled up to a larger crafter grid.
            int dim = effectiveCraftDimension();
            boolean hovering = false;
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                int ix = panelX + 68 + col * 20;
                int iy = panelY + 28 + row * 20;
                int idx = row * dim + col;
                ItemStack stack = (col < dim && row < dim && idx < craftingIngredients.size())
                    ? craftingIngredients.get(idx).stack : ItemStack.EMPTY;
                gfx.renderItem(stack, ix, iy);
                // With a batch > 1 each grid slot consumes (slot amount × batch) of its item — show it.
                int dispBatch = effectiveBatch();
                if (!stack.isEmpty() && dispBatch > 1)
                    drawItemCount(gfx, stack, ix, iy,
                        String.valueOf(Math.max(1, stack.getCount()) * dispBatch));
                if (in(mouseX, mouseY, ix, iy, 16, 16)) hovering = true;
            }
            if (hovering) {
                boolean ignoreData = craftingUsesIgnoreData();   // grid resizing & Ctrl popup disabled then
                if (hasControlDown() && !ignoreData)
                    patternHovered = true;
                else if (dim > 3)
                    tooltip = List.of(
                            CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component(),
                            Component.translatable("createfactorycontroller.gui.crafting_unpacked").withStyle(ChatFormatting.GRAY),
                            Component.translatable("createfactorycontroller.gui.crafting_crafters", dim, dim).withStyle(ChatFormatting.GRAY),
                            Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
                else {
                    List<Component> t = new ArrayList<>();
                    t.add(CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component());
                    t.add(CreateLang.translate("gui.factory_panel.crafting_input_tip").style(ChatFormatting.GRAY).component());
                    t.add(CreateLang.translate("gui.factory_panel.crafting_input_tip_1").style(ChatFormatting.GRAY).component());
                    if (!ignoreData)   // no grid resizing → omit the "hold Ctrl to change grid size" hint
                        t.add(Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim")
                                .withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
                    tooltip = t;
                }
            }
        } else {
            List<InputSlot> slots = layoutInputSlots();
            for (int i = 0; i < slots.size(); i++) {
                InputSlot slot = slots.get(i);
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                boolean fluidIng = isFluidConn(slot.connectionIndex());
                ItemStack stack = ingredientOf(inputConnections.get(slot.connectionIndex()));
                FluidGuiRender.filterIcon(gfx, stack, ix, iy);
                if (!stack.isEmpty()) {
                    if (fluidIng) drawSlotCount(gfx, formatFluidShort(slot.amount()), ix, iy);
                    else drawItemCount(gfx, stack, ix, iy, String.valueOf(slot.amount()));
                }
                if (in(mouseX, mouseY, ix, iy, 16, 16)) {
                    // Every slot of a connection shows that connection's TOTAL, not the slot's own count.
                    int total = Math.max(1, inputTotals.get(slot.connectionIndex()));
                    String totalLabel = fluidIng ? ThresholdUnit.formatFluidAmount(total) : String.valueOf(total);
                    boolean srcIgnore = menu.componentAt(inputConnections.get(slot.connectionIndex()))
                            instanceof VirtualGaugeBehaviour s && s.ignoreData;
                    // Header: "Sending <Item> x<total>" + (item only) the dark-gray stack breakdown, like the output slot.
                    MutableComponent inHeader = CreateLang.translate("gui.factory_panel.sending_item",
                            FluidCompat.filterName(stack).getString() + " x" + totalLabel)
                            .color(ScrollInput.HEADER_RGB).component();
                    if (!fluidIng && total > stackSizeOf(stack)) inHeader.append(stackBreakdown(total, stackSizeOf(stack)));
                    tooltip = stack.isEmpty()
                        ? List.of(
                            CreateLang.translate("gui.factory_panel.empty_panel").color(ScrollInput.HEADER_RGB).component(),
                            Component.translatable("createfactorycontroller.gui.action_disconnect")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                        : withIgnoreDataLine(List.of(
                            inHeader,
                            CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                            CreateLang.translate("gui.factory_panel.left_click_disconnect")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()),
                            srcIgnore);
                }
            }
            if (inputConnections.isEmpty() && in(mouseX, mouseY, panelX + 68, panelY + 28, 58, 58))
                tooltip = List.of(
                    CreateLang.translate("gui.factory_panel.unconfigured_input").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.translate("gui.factory_panel.unconfigured_input_tip").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.unconfigured_input_tip_1").style(ChatFormatting.GRAY).component());
        }

        // OUTPUT — the gauge's filter and produced count. In crafting mode this is the whole batch
        // (per-craft yield × craft count); a single package carries every craft.
        if (g != null && !g.filter.isEmpty()) {
            int ox = panelX + 160, oy = panelY + 48;
            int producedCount = craftingActive ? outputCount * effectiveBatch() : outputCount;
            // Output is always magnitude-scaled (mB/B) regardless of the unit box: short ≤1-dp label, full tooltip.
            String producedBox = fluidMode ? formatFluidShort(producedCount) : String.valueOf(producedCount);
            String producedTip = fluidMode ? ThresholdUnit.formatFluidAmount(producedCount) : String.valueOf(producedCount);
            FluidGuiRender.filterIcon(gfx, g.filter, ox, oy);
            if (fluidMode) drawSlotCount(gfx, producedBox, ox, oy);
            else drawItemCount(gfx, g.filter, ox, oy, producedBox);
            if (in(mouseX, mouseY, ox, oy, 16, 16)) {
                // The output scroll changes batch in crafting mode / the output count otherwise; both are
                // disabled when an ignore-data ingredient is present, so the last line reflects that.
                Component scrollLine = craftingActive && craftingUsesIgnoreData()
                    ? Component.translatable("createfactorycontroller.gui.unable_to_change")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                    : CreateLang.translate("gui.factory_panel.expected_output_tip_2")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component();
                // Header: "Expecting <Item> x<count>", and for an item output a dark-gray stack breakdown
                // "| <stacks>▤ +<overflow>" (overflow shown only when the count isn't a whole number of stacks).
                MutableComponent header = CreateLang.translate("gui.factory_panel.expected_output",
                        FluidCompat.filterName(g.filter).getString() + " x" + producedTip)
                        .color(ScrollInput.HEADER_RGB).component();
                if (!fluidMode && producedCount > outputStackSize())
                    header.append(stackBreakdown(producedCount, outputStackSize()));
                tooltip = withIgnoreDataLine(List.of(
                    header,
                    CreateLang.translate("gui.factory_panel.expected_output_tip").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.expected_output_tip_1").style(ChatFormatting.GRAY).component(),
                    scrollLine),
                    g.ignoreData);
            }
        }

        renderThreshold(gfx, g);

        // Open-promise package box (left of the promise-interval scroll).
        int pbx = panelX + PROMISE_CLEAR_X, pby = panelY + PANEL_H - 24;
        int promised = g == null ? 0 : g.promisedCount;
        // A fluid gauge's promise is millibuckets — same short mB/B format as the output slot.
        String promisedLabel = g != null && FluidCompat.isFluidFilter(g.filter)
            ? formatFluidShort(promised) : String.valueOf(promised);
        ItemStack box = PackageStyles.getDefaultBox();
        gfx.renderItem(box, pbx, pby);
        gfx.renderItemDecorations(font, box, pbx, pby, promisedLabel);
        if (in(mouseX, mouseY, pbx, pby, 16, 16))
            tooltip = promised == 0
                ? List.of(
                    CreateLang.translate("gui.factory_panel.no_open_promises").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.translate("gui.factory_panel.recipe_promises_tip").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.recipe_promises_tip_1").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.promise_prevents_oversending").style(ChatFormatting.GRAY).component())
                : List.of(
                    CreateLang.translate("gui.factory_panel.promised_items").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.text(FluidCompat.filterName(g.filter).getString() + " x" + promisedLabel)
                        .component(),
                    CreateLang.translate("gui.factory_panel.left_click_reset")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());

        // 3D gauge preview
        GuiGameElement.of(g == null ? AllBlocks.FACTORY_GAUGE : g.getItem())
            .scale(4).at(0, 0, -200).render(gfx, panelX + 195, panelY + 139);
        if (g != null && !g.filter.isEmpty()) {
            if (FluidCompat.isFluidFilter(g.filter))
                FluidGuiRender.cube(gfx, FluidCompat.getFilterFluid(g.filter), panelX + 219, panelY + 157, 16);
            else
                GuiGameElement.of(g.filter).scale(1.625).at(0, 0, 100).render(gfx, panelX + 214, panelY + 152);
        }

        confirmButton.render(gfx, mouseX, mouseY, partialTick);
        deleteButton.render(gfx, mouseX, mouseY, partialTick);

        if (craftingButton != null) craftingButton.render(gfx, mouseX, mouseY, partialTick);
        newInputButton.render(gfx, mouseX, mouseY, partialTick);
        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        if (requestModeButton != null) requestModeButton.render(gfx, mouseX, mouseY, partialTick);

        promiseExpiration.render(gfx, mouseX, mouseY, partialTick);

        // Promise-interval label over the scroll box.
        int state = promiseExpiration.getState();
        String label = state == -1 ? "/" : state == 0 ? "30s" : state + "m";
        drawCenteredBoxLabel(gfx, promiseExpiration.getX(), promiseExpiration.getY(), promiseExpiration.getWidth(), label);

        int limit = promiseLimitState;
        int activePromises = g == null ? 0 : g.activePromiseCount;
        drawPromiseLimitLabel(gfx, panelX + PROMISE_LIMIT_X, panelY + PANEL_H - 24, PROMISE_LIMIT_W, activePromises, limit);

        if (in(mouseX, mouseY, panelX + PROMISE_LIMIT_X, panelY + PANEL_H - 24, PROMISE_LIMIT_W, 16))
            tooltip = List.of(
                Component.translatable("createfactorycontroller.gui.open_requests")
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ScrollInput.HEADER_RGB.getRGB())),
                Component.translatable("createfactorycontroller.gui.open_requests.desc1").withStyle(ChatFormatting.GRAY),
                Component.translatable("createfactorycontroller.gui.open_requests.desc2").withStyle(ChatFormatting.GRAY),
                Component.translatable("createfactorycontroller.gui.open_requests.scroll_limit")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        // Redstone-link reset slot (top-right), shown only when a redstone link is wired to this gauge; clicking it
        // disconnects them all (mirrors Create's FactoryPanelScreen).
        if (hasLinkConnections()) {
            int itemX = panelX + LINK_RESET_X, itemY = panelY + LINK_RESET_Y;
            AllGuiTextures.FROGPORT_SLOT.render(gfx, itemX - 1, itemY - 1);
            gfx.renderItem(AllBlocks.REDSTONE_LINK.asStack(), itemX, itemY);
            if (in(mouseX, mouseY, itemX, itemY, 16, 16))
                tooltip = List.of(
                    CreateLang.translate("gui.factory_panel.has_link_connections").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.translate("gui.factory_panel.left_click_disconnect")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());
        }

        // Count box tooltip
        if (in(mouseX, mouseY, panelX + COUNT_X, panelY + THRESH_TOP - 1, COUNT_W, THRESH_H))
            tooltip = requestMode.isPassive()
                ? List.of(
                    CreateLang.translate("factory_panel.target_amount").color(ScrollInput.HEADER_RGB).component(),
                    Component.translatable("createfactorycontroller.gui.threshold.auto_managed")
                        .withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC))
                : List.of(
                    CreateLang.translate("factory_panel.target_amount").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.translate("gui.scrollInput.scrollToModify")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                    CreateLang.translate("gui.scrollInput.shiftScrollsFaster")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());

        // Unit box tooltip
        if (in(mouseX, mouseY, panelX + UNIT_X, panelY + THRESH_TOP - 1, UNIT_W, THRESH_H)) {
            ThresholdUnit a = fluidMode ? ThresholdUnit.FLUID_MB : ThresholdUnit.ITEMS;
            ThresholdUnit b = fluidMode ? ThresholdUnit.FLUID_BUCKET : ThresholdUnit.STACKS;
            tooltip = List.of(
                CreateLang.translate("schedule.condition.threshold.item_measure").color(ScrollInput.HEADER_RGB).component(),
                a.tooltipLine(mode == a),
                b.tooltipLine(mode == b),
                CreateLang.translate("gui.scrollInput.scrollToSelect")
                    .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());
        }

        // Request-mode button tooltip: header, the three modes (arrow on the selected one), the selected mode's
        // two-line description, then "click to cycle".
        if (requestModeButton != null && requestModeButton.isMouseOver(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("createfactorycontroller.gui.request_mode")
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ScrollInput.HEADER_RGB.getRGB())));
            for (RequestMode m : RequestMode.values()) {
                boolean sel = m == requestMode;
                lines.add(Component.literal(sel ? "-> " : "> ")
                    .append(Component.translatable(requestModeKey(m)))
                    .withStyle(sel ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            }
            lines.add(Component.translatable(requestModeKey(requestMode) + ".desc1").withColor(0x777777));
            lines.add(Component.translatable(requestModeKey(requestMode) + ".desc2").withColor(0x777777));
            lines.add(Component.translatable("createfactorycontroller.gui.request_mode.change_tip")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            tooltip = lines;
        }

        // Filter/stock box tooltip — the filtered item's normal item tooltip.
        if (g != null && in(mouseX, mouseY, panelX + FILTER_X, panelY + THRESH_TOP, 16, 16))
            tooltip = g.filter.isEmpty()
                ? List.of(CreateLang.translate("gui.factory_panel.unconfigured_input").color(ScrollInput.HEADER_RGB).component())
                : FluidCompat.isFluidFilter(g.filter)
                    ? FluidCompat.fluidTooltip(FluidCompat.getFilterFluid(g.filter),
                        Minecraft.getInstance().options.advancedItemTooltips)
                    : getTooltipFromItem(Minecraft.getInstance(), g.filter);

        // Crafting toggle — rendered here (last) rather than self-rendered by the widget, so the buttons
        // drawn after it (new-input / relocate) can't paint over its now multi-line tooltip.
        if (craftingButton != null && craftingButton.isMouseOver(mouseX, mouseY))
            tooltip = craftingButtonTooltip();

        if (tooltip != null)
            gfx.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    // ── Fluid mode helpers ──────────────────────────────────────────────────────

    /** The fluid output slot is always capped at 10 B (10000 mB), independent of the threshold unit box. */
    private static final int FLUID_OUTPUT_CAP_MB = 10_000;

    private static int adjustFluidAmount(int cur, int dir, boolean shift, boolean ctrl, int min, int max) {
        int next = cur + dir * (ctrl ? 1 : shift ? 10 : 1000);
        if (!shift && !ctrl && cur % 1000 != 0) {           // 1-bucket step from an off-boundary value
            int boundary = dir > 0 ? (cur / 1000 + 1) * 1000 : (cur / 1000) * 1000;
            if (dir > 0 ? next > boundary : next < boundary) next = boundary;
        }
        return Mth.clamp(next, min, max);
    }

    private static String formatFluidShort(int millibuckets) {
        if (millibuckets < 1000) return millibuckets + "mB";
        return new java.math.BigDecimal(millibuckets).movePointLeft(3)
            .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "B";
    }

    private void drawCenteredBoxLabel(GuiGraphics gfx, int x, int y, int width, String label) {
        gfx.drawString(font, label, x + (width - font.width(label)) / 2, y + 4, 0xFFEEEEEE, true);
    }

    private void drawPromiseLimitLabel(GuiGraphics gfx, int x, int y, int width, int active, int limit) {
        String activeText = String.valueOf(active);
        if (limit == 0) {
            gfx.drawString(font, activeText, x + (width - font.width(activeText)) / 2, y + 4, 0xFFEEEEEE, true);
            return;
        }
        String limitText = "/" + limit;
        int totalWidth = font.width(activeText) + font.width(limitText);
        int textX = x + (width - totalWidth) / 2;
        gfx.drawString(font, activeText, textX, y + 4, active >= limit ? 0xFFFFBFA8 : 0xFFD7FFA8, true);
        gfx.drawString(font, limitText, textX + font.width(activeText), y + 4, 0xFFEEEEEE, true);
    }

    private void renderThreshold(GuiGraphics gfx, @Nullable VirtualGaugeBehaviour behaviour) {
        // stock level
        if (behaviour != null && !behaviour.filter.isEmpty()) {
            int fx = panelX + FILTER_X, fy = panelY + THRESH_TOP;
            FluidGuiRender.filterIcon(gfx, behaviour.filter, fx, fy);
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);
            // Fluid stock uses the bucket-aware format + glyphs (CreateFluidLogistic's mB/B/KB on the same
            // Create NUMBERS sprite); items use the k/m abbreviation. Both share our sprite renderer.
            SpriteNumbersRender.drawCount(gfx, FluidCompat.isFluidFilter(behaviour.filter)
                ? formatFluidStock(behaviour.stockLevel) : stockCountText(behaviour.stockLevel), fx, fy);
            gfx.pose().popPose();
        }
        // demand
        int displayCount = thresholdCount;
        if (requestMode.isPassive() && behaviour != null && behaviour.requestMode.isPassive()) {
            displayCount = Math.max(0, behaviour.count);
        }
        // Count box is a plain integer (fluid threshold is whole units of the unit box; items are whole items).
        // While typing (non-passive only) show the live buffer + a blinking caret; "/" only represents 0 when idle.
        String countStr;
        if (countEditing && !requestMode.isPassive()) {
            countStr = (countEdit.isEmpty() ? "" : countEdit) + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
        } else {
            countStr = displayCount == 0 && !requestMode.isPassive() ? "/" : String.valueOf(displayCount);
        }
        int countColor = requestMode.isPassive() ? 0xFF9ECFFC : 0xFFFFFFFF;
        gfx.drawString(font, countStr, panelX + COUNT_X + 4, panelY + THRESH_TOP + 5, countColor, true);
        // unit
        gfx.drawString(font, mode.label().getString(), panelX + UNIT_X + 4, panelY + THRESH_TOP + 5, 0xFFFFFFFF, true);

    }

    /** Abbreviated item stock text (k/m), mirroring StockKeeperRequestScreen#drawItemCount. */
    private static String stockCountText(int count) {
        if (count >= BigItemStack.INF) return "+";
        return count >= 1000000 ? (count / 1000000) + "m"
            : count >= 10000 ? (count / 1000) + "k"
            : count >= 1000 ? ((count * 10) / 1000) / 10f + "k"
            : count >= 100 ? count + "" : " " + count;
    }

    /**
     * Fluid stock text, replicating CreateFluidLogistic's {@code FluidAmountHelper.format}: {@code <100→"XmB"},
     * {@code <1,000,000→"Y.YB"} (buckets), {@code <1e9→"Y.YKB"} (kilobuckets), else "+". So our label reads the
     * same as fluid stock in Create's stock keeper, drawn from the same Create NUMBERS sprite.
     */
    private static String formatFluidStock(int mb) {
        if (mb >= 1_000_000_000) return "+";
        if (mb >= 1_000_000) return compactFluid(mb, 1_000_000, "KB");
        if (mb >= 100) return compactFluid(mb, 1000, "B");
        return mb + "mB";
    }

    private static String compactFluid(int amount, int divisor, String suffix) {
        if (amount % divisor == 0) return (amount / divisor) + suffix;          // exact: "2B"
        if (amount / divisor <= 10)                                            // ≤10 units: one decimal "1.5B"
            return String.format(java.util.Locale.ROOT, "%.1f%s", Math.floor(amount / (divisor / 10.0)) / 10.0, suffix);
        return String.format(java.util.Locale.ROOT, "%.0f%s", Math.floor(amount / (double) divisor), suffix);  // ">10: "12B"
    }

    /** Draws an input/output slot's item count: the vanilla item-decoration number, or — when the client's
     *  "Compact recipe count font" option is on — Create's compact NUMBERS sprite (same glyphs as the stock icon),
     *  z-lifted like the stock so it sits over the item. */
    private void drawItemCount(GuiGraphics gfx, ItemStack stack, int itemX, int itemY, String text) {
        if (ClientConfig.compactRecipeCountFont()) {
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);
            // Right-align to the slot's right edge, matching the vanilla item-count placement it replaces.
            SpriteNumbersRender.drawCountRightAligned(gfx, text, itemX + 17, itemY + 10);
            gfx.pose().popPose();
        } else {
            gfx.renderItemDecorations(font, stack, itemX, itemY, text);
        }
    }

    private void drawSlotCount(GuiGraphics gfx, String text, int slotX, int slotY) {
        if (text.isBlank()) return;
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 200);
        gfx.drawString(font, text, slotX + 19 - 2 - font.width(text), slotY + 6 + 3, 0xFFFFFF, true);
        gfx.pose().popPose();
    }

    @Override
    protected void renderForeground(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(font, title, panelX + 97 - font.width(title) / 2, panelY + 4, 0x3D3C48, false);

        addressBox.render(gfx, mouseX, mouseY, partialTicks);
        if (addressBox.isHovered() && !addressBox.isFocused())
            gfx.renderComponentTooltip(font, addressBox.getValue().isBlank()
                ? List.of(
                    CreateLang.translate("gui.factory_panel.recipe_address").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.translate("gui.factory_panel.recipe_address_tip").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.recipe_address_tip_1").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.schedule.lmb_edit")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component())
                : List.of(
                    CreateLang.translate("gui.factory_panel.recipe_address_given").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.text("'" + addressBox.getValue() + "'").style(ChatFormatting.GRAY).component()),
                mouseX, mouseY);

        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    // ── Input ────────────────────────────────────────────────────────────────

    /** Create's GUI button blip (UI_BUTTON_CLICK, soft) — for value-box / slot clicks. */
    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
    }

    /** Create's value-scroll blip (SCROLL_VALUE, pitch 1.5) — matches its ScrollInput widget. */
    private static void playScrollSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 1.5f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Deselect the address box when clicking away from it (mirrors FactoryPanelScreen).
        if (getFocused() != null && !getFocused().isMouseOver(mouseX, mouseY))
            setFocused(null);

        // Count box click-to-type (non-passive). Clicking elsewhere commits any in-progress edit.
        boolean onCountBox = in(mouseX, mouseY, panelX + COUNT_X, panelY + THRESH_TOP - 1, COUNT_W, THRESH_H);
        if (countEditing && !onCountBox) commitCountEdit();
        if (onCountBox && (button == 0 || button == 1) && !requestMode.isPassive()) {
            countEditing = true;
            countEdit = thresholdCount == 0 || button == 1 ? "" : String.valueOf(thresholdCount);
            setFocused(null);   // blur the address box while typing the count
            playClickSound();
            return true;
        }

        // Right-click the address field → clear it.
        if (button == 1 && addressBox.isMouseOver(mouseX, mouseY)) {
            addressBox.setValue("");
            return true;
        }

        // Let the address box and its suggestion dropdown consume the click first — the dropdown can
        // overlap the regions checked below, so it must win.
        if (addressBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(addressBox);
            return true;
        }

        // Click the open-promise box → clear promises (Create's left-click reset).
        if (in(mouseX, mouseY, panelX + PROMISE_CLEAR_X, panelY + PANEL_H - 24, 16, 16)) {
            sendConfig(true, false);
            playClickSound();
            return true;
        }

        // Click the redstone-link reset slot → disconnect every link wired to this gauge (Create's redstone reset).
        // A dedicated packet (like the ingredient disconnect) so it doesn't commit any pending recipe-config edits.
        if (hasLinkConnections() && in(mouseX, mouseY, panelX + LINK_RESET_X, panelY + LINK_RESET_Y, 16, 16)) {
            PacketDistributor.sendToServer(new DisconnectLinksPacket(menu.controllerPos, gaugePos));
            playClickSound();
            return true;
        }

        if (!craftingActive) {
            List<InputSlot> slots = layoutInputSlots();
            for (int i = 0; i < slots.size(); i++) {
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                if (!in(mouseX, mouseY, ix, iy, 16, 16)) continue;
                int c = slots.get(i).connectionIndex();
                VirtualComponentPosition from = inputConnections.get(c);
                inputConnections.remove(c);
                inputTotals.remove(c);
                inputConfig.remove(c);
                PacketDistributor.sendToServer(
                    new DisconnectIngredientPacket(menu.controllerPos, from, gaugePos));
                onConnectionsChanged();   // re-evaluate crafting recipe + rebuild the crafting button
                playClickSound();
                return true;
            }
        }

        // Click the unit box → cycle Items ↔ Stacks.
        if (in(mouseX, mouseY, panelX + UNIT_X, panelY + THRESH_TOP - 1, UNIT_W, THRESH_H)) {
            setMode(mode.cycle(1));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getEditCountValue(String editString) {
        try {
            int value = editString.isEmpty() ? 0 : Integer.parseInt(editString);
            return Mth.clamp(value, 0, mode.getMaxRequestCount());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Commits the typed count, clamped to the valid range, and leaves edit mode. */
    private void commitCountEdit() {
        if (!countEditing) return;
        thresholdCount = getEditCountValue(countEdit);
        countEditing = false;
        countEdit = "";
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (countEditing) {
            if (codePoint >= '0' && codePoint <= '9')
                countEdit = String.valueOf(getEditCountValue(countEdit + codePoint));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (countEditing) {
            switch (keyCode) {
                case 257, 335, 256 -> commitCountEdit();   // Enter / numpad Enter / Escape → commit + leave edit
                case 259 -> {                              // Backspace
                    if (!countEdit.isEmpty()) countEdit = countEdit.substring(0, countEdit.length() - 1);
                }
                default -> { }
            }
            return true;   // capture all keys while typing so screen shortcuts (e.g. inventory) don't fire
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Let the address suggestion list consume scrolling first (matches FactoryPanelScreen).
        if (addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        int step = hasControlDown() ? 100 : (hasShiftDown() ? 10 : 1);
        int dir = (int) Math.signum(scrollY);

        if (in(mouseX, mouseY, panelX + PROMISE_LIMIT_X, panelY + PANEL_H - 24, PROMISE_LIMIT_W, 16)) {
            if (dir != 0) {
                int limitStep = hasShiftDown() ? 10 : 1;
                promiseLimitState = Mth.clamp(promiseLimitState + dir * limitStep, 0, 999);
                playScrollSound();
            }
            return true;
        }

        if (!craftingActive) {
            List<InputSlot> slots = layoutInputSlots();
            for (int i = 0; i < slots.size(); i++) {
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                if (in(mouseX, mouseY, ix, iy, 16, 16)) {
                    adjustInputTotal(slots.get(i).connectionIndex(), dir, hasShiftDown(), hasControlDown());
                    playScrollSound();
                    return true;
                }
            }
        } else if (in(mouseX, mouseY, panelX + 68, panelY + 28, 58, 58)) {
            // Ctrl over the recipe's ingredients resizes the square crafter grid; otherwise tune the batch.
            // Both are disabled when an ignore-data ingredient is present (fixed 3×3, no batching).
            if (!craftingUsesIgnoreData()) {
                if (hasControlDown()) adjustCraftDimension(dir);
                else adjustCraftBatch(dir * step);
            }
            return true;
        }
        // Output slot. Outside crafting mode this is the free output count; in crafting mode the
        // per-craft yield is fixed, so scrolling instead changes how many crafts ride one request.
        if (in(mouseX, mouseY, panelX + 160, panelY + 48, 16, 16)) {
            if (craftingActive) {
                if (!craftingUsesIgnoreData()) adjustCraftBatch(dir * step);   // batching disabled with ignore-data
            } else if (fluidMode) {
                outputCount = adjustFluidAmount(outputCount, dir, hasShiftDown(), hasControlDown(), 1, FLUID_OUTPUT_CAP_MB);
                playScrollSound();
            } else if (hasControlDown()) {
                outputCount = Mth.clamp(snapToStack(outputCount, dir, outputStackSize()), 1, maxItemOutput());   // full-stack snap
                playScrollSound();
            } else {
                outputCount = Mth.clamp(outputCount + dir * step, 1, maxItemOutput());
                playScrollSound();
            }
            return true;
        }
        // Threshold count box
        if (in(mouseX, mouseY, panelX + COUNT_X, panelY + THRESH_TOP - 1, COUNT_W, THRESH_H)) {
            if (countEditing) commitCountEdit();
            if (!requestMode.isPassive()) {
                // Fluid threshold is a whole number in the unit box's unit.
                step = hasControlDown() ? 100 : hasShiftDown() ? 10 : 1;
                thresholdCount = Mth.clamp(thresholdCount + dir * step, 0, mode.getMaxRequestCount());
                playScrollSound();
            }
            return true;
        }
        // Unit box → cycle Items / Stacks. Scrolling up advances the list (negate: scroll-up is
        // a positive dir but should move forward through the modes, matching the click cycle).
        if (in(mouseX, mouseY, panelX + UNIT_X, panelY + THRESH_TOP - 1, UNIT_W, THRESH_H)) {
            if (dir != 0) setMode(mode.cycle(-dir));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        confirmAndReturn();
    }

    @Override
    public void removed() {
        // Chime on every exit path (confirm/delete buttons, Escape, connect/relocate) as the overlay closes.
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }

    /** JEI exclusion zone covering the 3D gauge preview that protrudes from the bottom-right corner. */
    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(new Rect2i(panelX + 195, panelY + 152, 52, 35));
    }

    // ── Commit ───────────────────────────────────────────────────────────────

    private void sendConfig(boolean clearPromises, boolean reset) {
        List<ItemStack> arrangement = craftingActive
            ? craftingIngredients.stream().map(b -> b.stack).toList()
            : List.of();

        List<VirtualComponentPosition> positions = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        if (craftingActive) {
            for (VirtualComponentPosition pos : inputConnections) {
                ItemStack ing = ingredientOf(pos);
                int c = (int) craftingIngredients.stream()
                    .filter(b -> !b.stack.isEmpty() && ItemStack.isSameItemSameComponents(b.stack, ing))
                    .count();
                positions.add(pos);
                amounts.add(Math.max(1, c));
            }
        } else {
            // One total per connection — the model stores a single amount and the UI re-derives the slot split.
            for (int c = 0; c < inputConnections.size(); c++) {
                positions.add(inputConnections.get(c));
                amounts.add(Math.max(1, inputTotals.get(c)));
            }
        }

        int batch = craftingActive ? effectiveBatch() : 1;
        int dimension = craftingActive ? effectiveCraftDimension() : 0;
        PacketDistributor.sendToServer(new ConfigureRecipePacket(
            menu.controllerPos, gaugePos, addressBox.getValue(), outputCount, batch, dimension,
            promiseExpiration.getState(), promiseLimitState, thresholdCount, mode, requestMode,
            positions, amounts, new ArrayList<>(arrangement), clearPromises, reset));
    }

    /** True if this gauge has any non-logistics wire (a redstone link or a logic tube, in either direction) — the
     *  ones the reset slot clears. Queried directly off the gauge's own edges (O(degree)), not by scanning the board. */
    private boolean hasLinkConnections() {
        VirtualComponentBehaviour gauge = menu.componentAt(gaugePos);
        if (gauge == null) return false;
        return hasNonLogistics(gauge.targetedBy().values()) || hasNonLogistics(gauge.outgoingConnections());
    }

    private static boolean hasNonLogistics(java.util.Collection<Connection> connections) {
        for (Connection c : connections)
            if (!Connection.Type.LOGISTICS.equals(c.type)) return true;
        return false;
    }

    private void setMode(ThresholdUnit newMode) {
        mode = newMode;
        thresholdCount = Mth.clamp(thresholdCount, 0, mode.getMaxRequestCount());
        if (fluidMode) {
            outputCount = Mth.clamp(outputCount, 1, FLUID_OUTPUT_CAP_MB);
        }
        playScrollSound();
    }

    /** Cycles NORMAL → PASSIVE → PASSIVE_AND_ALLOW_ORDER → NORMAL. Entering passive drops the editable target;
     *  leaving it carries the live server-computed count back into the editable target. */
    private void cycleRequestMode() {
        RequestMode next = RequestMode.byOrdinal(requestMode.ordinal() + 1);
        boolean wasPassive = requestMode.isPassive();
        boolean nowPassive = next.isPassive();
        if (wasPassive && !nowPassive) {
            VirtualGaugeBehaviour behaviour = gauge();
            thresholdCount = Mth.clamp(behaviour != null ? behaviour.count : 0, 0, mode.getMaxRequestCount());
        } else if (!wasPassive && nowPassive) {
            thresholdCount = 0;
        }
        requestMode = next;
        if (requestModeButton != null) requestModeButton.setIcon(iconFor(requestMode));
        playClickSound();
    }

    private static String requestModeKey(RequestMode m) {
        return switch (m) {
            case NORMAL -> "createfactorycontroller.gui.request_mode.normal";
            case PASSIVE -> "createfactorycontroller.gui.request_mode.passive";
            case PASSIVE_AND_ALLOW_ORDER -> "createfactorycontroller.gui.request_mode.allow_order";
        };
    }

    private void confirmAndReturn() {
        sendConfig(false, false);
        Minecraft.getInstance().setScreen(controller);
    }

    private void deleteAndReturn() {
        sendConfig(false, true);
        Minecraft.getInstance().setScreen(controller);
    }
}
