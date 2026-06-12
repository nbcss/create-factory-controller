package io.github.nbcss.content.factorycontroller.gui;

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
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import net.createmod.catnip.gui.element.ScreenElement;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.ServerConfig;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.ThresholdUnit;
import io.github.nbcss.content.factorycontroller.VirtualGaugeBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualPanelConnection;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import io.github.nbcss.content.factorycontroller.packet.ConfigureRecipePacket;
import io.github.nbcss.content.factorycontroller.packet.DisconnectIngredientPacket;
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
    private static final int MAX_THRESHOLD_COUNT = 100;

    private static final ResourceLocation PANEL_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/configure_recipe.png");
    private static final int PANEL_W = 200, PANEL_H = 184;

    // Stock-keeper number font (create:textures/gui/stock_keeper.png, NUMBERS region 48,176 5x8).
    private static final ResourceLocation NUMBERS_TEX =
        ResourceLocation.fromNamespaceAndPath("create", "textures/gui/stock_keeper.png");
    private static final int NUM_SX = 48, NUM_SY = 176, NUM_W = 5, NUM_H = 8;

    // Threshold-row geometry (filter slot x24-40, count box x48-112, unit box x118-167, band y≈128-144).
    /** The input arrangement is a 3×3 grid, so at most 9 slots (incl. repeats) can be shown. */
    private static final int MAX_INPUT_SLOTS = 9;
    /** A request's produced output must fit one stack; batch is capped so {@code batch × yield ≤ 64}. */
    private static final int MAX_CRAFT_OUTPUT = 64;
    private static final int THRESH_TOP = 128;
    private static final int FILTER_X = 25;
    private static final int COUNT_X = 51, COUNT_W = 40;
    private static final int UNIT_X = 95, UNIT_W = 50;
    private static final int PASSIVE_BTN_X = 150;
    private static final int THRESH_H = 18;

    private final FactoryControllerScreen controller;
    private final VirtualPanelPosition gaugePos;

    private int panelX, panelY;

    // Editable / derived state.
    private int outputCount = 1;
    /** Crafts per request in crafting mode (≥1). The output slot shows outputCount × craftBatch. */
    private int craftBatch = 1;
    /** Square crafter-grid size (N→N×N) a recipe is laid out for; defaults to its minimum (max of recipe
     *  width/height), Ctrl-scrollable up to {@link ServerConfig#maxCraftGridSize()}. 0 until a recipe loads. */
    private int craftDimension = 0;
    private int thresholdCount = 0;
    private ThresholdUnit mode = ThresholdUnit.ITEMS;
    private boolean passiveMode = false;
    // One entry per input CONNECTION (not per grid slot): the source gauge and its TOTAL item count.
    // The 3×3 grid layout — full stacks first, one partial last slot, contiguous per connection, packed
    // in connection order — is derived on demand from these via layoutInputSlots().
    private final List<VirtualPanelPosition> inputConnections = new ArrayList<>();
    private final List<Integer> inputTotals = new ArrayList<>();
    private final List<BigItemStack> inputConfig = new ArrayList<>();   // for crafting-recipe search (per connection)

    @Nullable private CraftingRecipe availableCraftingRecipe;
    private boolean craftingActive;
    private List<BigItemStack> craftingIngredients = new ArrayList<>();
    /** Set in renderBg when Ctrl is held over a crafting ingredient, so renderForeground draws the N×M layout. */
    private boolean patternHovered;

    private AddressEditBox addressBox;
    private ScrollInput promiseExpiration;
    private IconButton confirmButton;
    private IconButton deleteButton;
    private IconButton newInputButton;
    private IconButton relocateButton;
    @Nullable private IconButton craftingButton;
    @Nullable private IconButton passiveModeButton;

    public ConfigureRecipeScreen(FactoryControllerScreen controller, VirtualPanelPosition gaugePos) {
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

        // Create's address box with frogport-address autocomplete (DestinationSuggestions). It caps
        // length at 25 and renders its own suggestion dropdown; we only style it to match the panel.
        addressBox = new AddressEditBox(this, new NoShadowFontWrapper(font),
            panelX + 36, panelY + PANEL_H - 77, 108, 10, false);
        addressBox.setBordered(false);
        addressBox.setTextColor(0x555555);
        addressBox.setValue(address);
        addWidget(addressBox);

        promiseExpiration = new ScrollInput(panelX + 97, panelY + PANEL_H - 24, 28, 16)
            .withRange(-1, 31)
            .titled(CreateLang.translate("gui.factory_panel.promises_expire_title").component());
        promiseExpiration.setState(promiseState);
        addWidget(promiseExpiration);

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
            if (layoutInputSlots().size() >= MAX_INPUT_SLOTS) {
                sendConfig(false, false);   // save current edits before leaving
                controller.denyWithMessage(
                    CreateLang.translate("factory_panel.cannot_add_more_inputs")
                            .style(ChatFormatting.RED).component(), 3000);
                Minecraft.getInstance().setScreen(controller);
                return;
            }
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

        // Passive mode toggle — right of the unit box. Green when passive mode is on.
        ScreenElement followsDemandIcon = (gfx, x, y) -> gfx.blitSprite(
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "icons/follows_demand"),
            x, y, 16, 16);
        passiveModeButton = new IconButton(panelX + PASSIVE_BTN_X, panelY + THRESH_TOP - 1, followsDemandIcon);
        passiveModeButton.green = passiveMode;
        passiveModeButton.withCallback(this::togglePassiveMode);
        addWidget(passiveModeButton);
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
        return menu.getComponent(gaugePos) instanceof VirtualGaugeBehaviour g ? g : null;
    }

    private ItemStack ingredientOf(VirtualPanelPosition pos) {
        return menu.getComponent(pos) instanceof VirtualGaugeBehaviour g ? g.filter : ItemStack.EMPTY;
    }

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
    private List<InputSlot> layoutInputSlots() {
        List<InputSlot> slots = new ArrayList<>();
        for (int c = 0; c < inputConnections.size() && slots.size() < MAX_INPUT_SLOTS; c++) {
            int ss = stackSizeOf(ingredientOf(inputConnections.get(c)));
            int remaining = Math.max(1, inputTotals.get(c));
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
        int ss = stackSizeOf(ingredientOf(inputConnections.get(connectionIndex)));
        int cur = Math.max(1, inputTotals.get(connectionIndex));

        int maxSlots = Math.max(1, MAX_INPUT_SLOTS - slotsUsedExcept(connectionIndex));
        int maxTotal = maxSlots * ss;

        int next;
        if (ctrl) {
            next = cur + dir * ss;                       // ±1 full stack
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
        int batch = Math.max(1, craftBatch);
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
        return Mth.clamp(craftDimension, minDim, maxCraftDim(minDim));   // 0 (unset) → minDim
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
        passiveMode = g.passiveMode;
        for (VirtualPanelConnection conn : g.targetedBy().values()) {
            // Collapse the stored per-slot breakdown into one total; the canonical slot layout (full
            // stacks first, one partial last) is re-derived on render/scroll/send.
            int total = conn.totalAmount();
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
        int minDim = minCraftDim();
        craftDimension = Mth.clamp(craftDimension, minDim, maxCraftDim(minDim));   // 0 (unset) → minDim
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

    // Sub-screen renders the controller board as its background (renderBg → controller.renderBoard),
    // so refresh the parent's gauge-widget cache when a sync lands while this screen is open. Our own
    // gauge reads (gauge()) are live lookups into the menu, so they need no extra refresh here.
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

    // Recipe-grid dimensions, matching Create's convertRecipeToPackageOrderContext exactly: a shaped recipe
    // uses its own width/height; any other (shapeless) uses width = min(3, count), height = min(3, count/3+1).
    // Getting these right is what centres narrow recipes correctly (e.g. a shapeless 1-ingredient → 1×1 →
    // centre cell); a hardcoded 3 wide would have pushed it to the left column instead.

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
                    gfx.renderItemDecorations(font, slot.stack(), ix, iy, String.valueOf(slot.amount()));
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
                if (!stack.isEmpty() && craftBatch > 1)
                    gfx.renderItemDecorations(font, stack, ix, iy,
                        String.valueOf(Math.max(1, stack.getCount()) * craftBatch));
                if (in(mouseX, mouseY, ix, iy, 16, 16)) hovering = true;
            }
            if (hovering) {
                if (hasControlDown())
                    patternHovered = true;
                else if (dim > 3)
                    tooltip = List.of(
                            CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component(),
                            Component.translatable("createfactorycontroller.gui.crafting_unpacked").withStyle(ChatFormatting.GRAY),
                            Component.translatable("createfactorycontroller.gui.crafting_crafters", dim, dim).withStyle(ChatFormatting.GRAY),
                            Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
                else
                    tooltip = List.of(
                            CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component(),
                            CreateLang.translate("gui.factory_panel.crafting_input_tip").style(ChatFormatting.GRAY).component(),
                            CreateLang.translate("gui.factory_panel.crafting_input_tip_1").style(ChatFormatting.GRAY).component(),
                            Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
            }
        } else {
            List<InputSlot> slots = layoutInputSlots();
            for (int i = 0; i < slots.size(); i++) {
                InputSlot slot = slots.get(i);
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                ItemStack stack = ingredientOf(inputConnections.get(slot.connectionIndex()));
                gfx.renderItem(stack, ix, iy);
                if (!stack.isEmpty())
                    gfx.renderItemDecorations(font, stack, ix, iy, String.valueOf(slot.amount()));
                if (in(mouseX, mouseY, ix, iy, 16, 16)) {
                    // Every slot of a connection shows that connection's TOTAL, not the slot's own count.
                    int total = Math.max(1, inputTotals.get(slot.connectionIndex()));
                    tooltip = stack.isEmpty()
                        ? List.of(
                            CreateLang.translate("gui.factory_panel.empty_panel").color(ScrollInput.HEADER_RGB).component(),
                            Component.translatable("createfactorycontroller.gui.action_disconnect")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                        : List.of(
                            CreateLang.translate("gui.factory_panel.sending_item",
                                CreateLang.itemName(stack).add(CreateLang.text(" x" + total)).string())
                                .color(ScrollInput.HEADER_RGB).component(),
                            CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                            CreateLang.translate("gui.factory_panel.left_click_disconnect")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());
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
            int producedCount = craftingActive ? outputCount * Math.max(1, craftBatch) : outputCount;
            gfx.renderItem(g.filter, ox, oy);
            gfx.renderItemDecorations(font, g.filter, ox, oy, String.valueOf(producedCount));
            if (in(mouseX, mouseY, ox, oy, 16, 16))
                tooltip = List.of(
                    CreateLang.translate("gui.factory_panel.expected_output",
                        CreateLang.itemName(g.filter).add(CreateLang.text(" x" + producedCount)).string())
                        .color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.translate("gui.factory_panel.expected_output_tip").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.expected_output_tip_1").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.expected_output_tip_2")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());
        }

        renderThreshold(gfx, g);

        // Open-promise package box (left of the promise-interval scroll).
        int pbx = panelX + 68, pby = panelY + PANEL_H - 24;
        int promised = g == null ? 0 : g.promisedCount;
        ItemStack box = PackageStyles.getDefaultBox();
        gfx.renderItem(box, pbx, pby);
        gfx.renderItemDecorations(font, box, pbx, pby, String.valueOf(promised));
        if (in(mouseX, mouseY, pbx, pby, 16, 16))
            tooltip = promised == 0
                ? List.of(
                    CreateLang.translate("gui.factory_panel.no_open_promises").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.translate("gui.factory_panel.recipe_promises_tip").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.recipe_promises_tip_1").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.promise_prevents_oversending").style(ChatFormatting.GRAY).component())
                : List.of(
                    CreateLang.translate("gui.factory_panel.promised_items").color(ScrollInput.HEADER_RGB).component(),
                    CreateLang.text(g.filter.getHoverName().getString() + " x" + promised)
                        .component(),
                    CreateLang.translate("gui.factory_panel.left_click_reset")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());

        // 3D gauge preview + the filter floating in front of it.
        GuiGameElement.of(AllBlocks.FACTORY_GAUGE.asStack())
            .scale(4).at(0, 0, -200).render(gfx, panelX + 195, panelY + 139);
        if (g != null && !g.filter.isEmpty())
            GuiGameElement.of(g.filter).scale(1.625).at(0, 0, 100).render(gfx, panelX + 214, panelY + 152);

        // Widgets (added via addWidget; drawn manually on top of the panel). The address box is drawn
        // later in renderForeground (a clean render pass) so its clipboard hint + suggestion dropdown
        // aren't clobbered by the 3D gauge preview's render state or covered by later panel draws.
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
        deleteButton.render(gfx, mouseX, mouseY, partialTick);
        // Left-side action stack drawn top→bottom (crafting y+27, new-input y+47, relocate y+67): a button's
        // hover tooltip extends upward from the cursor over the buttons above it, so the upper ones must be
        // drawn first or a later neighbour (e.g. crafting) would paint over the tooltip.
        if (craftingButton != null) craftingButton.render(gfx, mouseX, mouseY, partialTick);
        newInputButton.render(gfx, mouseX, mouseY, partialTick);
        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        if (passiveModeButton != null) passiveModeButton.render(gfx, mouseX, mouseY, partialTick);
        // Rendered last of the widgets so the ScrollInput's own hover tooltip draws on top of the other
        // buttons instead of being covered by them (the value label below still paints over its box).
        promiseExpiration.render(gfx, mouseX, mouseY, partialTick);

        // Promise-interval label over the scroll box.
        int state = promiseExpiration.getState();
        String label = state == -1 ? " /" : state == 0 ? "30s" : state + "m";
        gfx.drawString(font, label, promiseExpiration.getX() + 3, promiseExpiration.getY() + 4, 0xFFEEEEEE, true);

        // Count box tooltip. In passive mode the count is system-managed, so the scroll hints are replaced
        // by a note that the target is driven by downstream requests.
        if (in(mouseX, mouseY, panelX + COUNT_X, panelY + THRESH_TOP - 1, COUNT_W, THRESH_H))
            tooltip = passiveMode
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

        // Unit box tooltip — two selectable modes (items / stacks), with the active one arrowed.
        if (in(mouseX, mouseY, panelX + UNIT_X, panelY + THRESH_TOP - 1, UNIT_W, THRESH_H))
            tooltip = List.of(
                CreateLang.translate("schedule.condition.threshold.item_measure").color(ScrollInput.HEADER_RGB).component(),
                ThresholdUnit.ITEMS.tooltipLine(mode == ThresholdUnit.ITEMS),
                ThresholdUnit.STACKS.tooltipLine(mode == ThresholdUnit.STACKS),
                CreateLang.translate("gui.scrollInput.scrollToSelect")
                    .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());

        // Passive mode button tooltip.
        if (passiveModeButton != null && passiveModeButton.isMouseOver(mouseX, mouseY))
            tooltip = List.of(
                CreateLang.text(Component.translatable("createfactorycontroller.gui.passive_mode").getString())
                    .color(ScrollInput.HEADER_RGB).component(),
                Component.translatable("createfactorycontroller.gui.passive_mode_tip_1")
                    .withStyle(ChatFormatting.GRAY),
                Component.translatable("createfactorycontroller.gui.passive_mode_tip_2")
                    .withStyle(ChatFormatting.GRAY));

        // Filter/stock box tooltip — the filtered item's normal item tooltip.
        if (g != null && in(mouseX, mouseY, panelX + FILTER_X, panelY + THRESH_TOP, 16, 16))
            tooltip = g.filter.isEmpty()
                ? List.of(CreateLang.translate("gui.factory_panel.unconfigured_input").color(ScrollInput.HEADER_RGB).component())
                : getTooltipFromItem(Minecraft.getInstance(), g.filter);

        // Crafting toggle — rendered here (last) rather than self-rendered by the widget, so the buttons
        // drawn after it (new-input / relocate) can't paint over its now multi-line tooltip.
        if (craftingButton != null && craftingButton.isMouseOver(mouseX, mouseY))
            tooltip = craftingButtonTooltip();

        if (tooltip != null)
            gfx.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private void renderThreshold(GuiGraphics gfx, @Nullable VirtualGaugeBehaviour behaviour) {
        // stock level
        if (behaviour != null && !behaviour.filter.isEmpty()) {
            int fx = panelX + FILTER_X, fy = panelY + THRESH_TOP;
            gfx.renderItem(behaviour.filter, fx, fy);
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);
            drawStockCount(gfx, behaviour.stockLevel, fx, fy);
            gfx.pose().popPose();
        }
        // demand
        int displayCount = thresholdCount;
        if (passiveMode && behaviour != null && behaviour.passiveMode) {
            displayCount = Math.max(0, behaviour.count);
        }
        String countStr = displayCount == 0 && !passiveMode
            ? "/" : String.valueOf(displayCount);
        int countColor = passiveMode ? 0xFF9ECFFC : 0xFFFFFFFF;
        gfx.drawString(font, countStr, panelX + COUNT_X + 4, panelY + THRESH_TOP + 5, countColor, true);
        // unit
        gfx.drawString(font, mode.label().getString(), panelX + UNIT_X + 4, panelY + THRESH_TOP + 5, 0xFFFFFFFF, true);

    }

    /** Replica of StockKeeperRequestScreen#drawItemCount — abbreviated count via the NUMBERS sprites. */
    private void drawStockCount(GuiGraphics gfx, int count, int itemX, int itemY) {
        String text = count >= 1000000 ? (count / 1000000) + "m"
            : count >= 10000 ? (count / 1000) + "k"
            : count >= 1000 ? ((count * 10) / 1000) / 10f + "k"
            : count >= 100 ? count + "" : " " + count;
        if (count >= BigItemStack.INF) text = "+";
        if (text.isBlank()) return;

        int x = (int) Math.floor(-text.length() * 2.5);
        for (char c : text.toCharArray()) {
            int index = c - '0';
            int xOffset = index * 6;
            int spriteWidth = NUM_W;
            switch (c) {
                case ' ': x += 4; continue;
                case '.': spriteWidth = 3; xOffset = 60; break;
                case 'k': xOffset = 64; break;
                case 'm': spriteWidth = 7; xOffset = 70; break;
                case '+': spriteWidth = 9; xOffset = 84; break;
                default: break;
            }
            RenderSystem.enableBlend();
            gfx.blit(NUMBERS_TEX, itemX + 13 + x, itemY + 10, 0, NUM_SX + xOffset, NUM_SY, spriteWidth, NUM_H, 256, 256);
            x += spriteWidth - 1;
        }
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
        if (in(mouseX, mouseY, panelX + 68, panelY + PANEL_H - 24, 16, 16)) {
            sendConfig(true, false);
            playClickSound();
            return true;
        }

        // Input slots (only outside crafting mode). A click on any slot of a connection disconnects that
        // whole connection (its slots are just a visual breakdown of one shared total; the repeat amount
        // is now driven by scrolling, not shift-click).
        if (!craftingActive) {
            List<InputSlot> slots = layoutInputSlots();
            for (int i = 0; i < slots.size(); i++) {
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                if (!in(mouseX, mouseY, ix, iy, 16, 16)) continue;
                int c = slots.get(i).connectionIndex();
                VirtualPanelPosition from = inputConnections.get(c);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Let the address suggestion list consume scrolling first (matches FactoryPanelScreen).
        if (addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        int step = hasShiftDown() ? 10 : 1;
        int dir = (int) Math.signum(scrollY);

        // Inputs. Outside crafting mode, scrolling any slot of a connection changes that connection's
        // shared total: plain ±1, shift ±10 (snapping at stack boundaries), ctrl ±1 stack. In crafting
        // mode the inputs are the fixed 3×3 recipe, so scrolling anywhere over them tunes the batch —
        // identical to scrolling the output slot.
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
            if (hasControlDown()) adjustCraftDimension(dir);
            else adjustCraftBatch(dir * step);
            return true;
        }
        // Output slot. Outside crafting mode this is the free output count; in crafting mode the
        // per-craft yield is fixed, so scrolling instead changes how many crafts ride one request.
        if (in(mouseX, mouseY, panelX + 160, panelY + 48, 16, 16)) {
            if (craftingActive) {
                adjustCraftBatch(dir * step);
            } else {
                outputCount = Mth.clamp(outputCount + dir * step, 1, 64);
                playScrollSound();
            }
            return true;
        }
        // Threshold count box. Locked in passive mode.
        if (in(mouseX, mouseY, panelX + COUNT_X, panelY + THRESH_TOP - 1, COUNT_W, THRESH_H)) {
            if (!passiveMode) {
                thresholdCount = Mth.clamp(thresholdCount + dir * step, 0, MAX_THRESHOLD_COUNT);
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

        // Per-slot ingredient amounts. Outside crafting mode we send one entry per derived grid slot
        // (full stacks first, partial last) so the server sums them back into the connection total. In
        // crafting mode the per-craft usage is one value per connection (times the item appears in the
        // arrangement, like Create's sendIt).
        List<VirtualPanelPosition> positions = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        if (craftingActive) {
            for (VirtualPanelPosition pos : inputConnections) {
                ItemStack ing = ingredientOf(pos);
                int c = (int) craftingIngredients.stream()
                    .filter(b -> !b.stack.isEmpty() && ItemStack.isSameItemSameComponents(b.stack, ing))
                    .count();
                positions.add(pos);
                amounts.add(Math.max(1, c));
            }
        } else {
            for (InputSlot slot : layoutInputSlots()) {
                positions.add(inputConnections.get(slot.connectionIndex()));
                amounts.add(Math.max(1, slot.amount()));
            }
        }

        int batch = craftingActive ? Math.max(1, craftBatch) : 1;
        int dimension = craftingActive ? effectiveCraftDimension() : 0;
        PacketDistributor.sendToServer(new ConfigureRecipePacket(
            menu.controllerPos, gaugePos, addressBox.getValue(), outputCount, batch, dimension,
            promiseExpiration.getState(), thresholdCount, mode, passiveMode,
            positions, amounts, new ArrayList<>(arrangement), clearPromises, reset));
    }

    private void setMode(ThresholdUnit newMode) {
        mode = newMode;
        playScrollSound();
    }

    /** Toggles passive mode. When turning off, the live server-computed count carries into the editable target. */
    private void togglePassiveMode() {
        if (passiveMode) {
            VirtualGaugeBehaviour behaviour = gauge();
            thresholdCount = Mth.clamp(behaviour != null ? behaviour.count : 0, 0, MAX_THRESHOLD_COUNT);
        } else {
            thresholdCount = 0;
        }
        passiveMode = !passiveMode;
        if (passiveModeButton != null) passiveModeButton.green = passiveMode;
        playClickSound();
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
