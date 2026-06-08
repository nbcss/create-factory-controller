package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.ThresholdMode;
import io.github.nbcss.content.factorycontroller.VirtualGaugeBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualPanelConnection;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import io.github.nbcss.content.factorycontroller.packet.ConfigureRecipePacket;
import io.github.nbcss.content.factorycontroller.packet.DisconnectIngredientPacket;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
public class ConfigureRecipeScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> {

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
    private static final int THRESH_TOP = 128;
    private static final int FILTER_X = 25;
    private static final int COUNT_X = 51, COUNT_W = 64;
    private static final int UNIT_X = 118, UNIT_W = 49;
    private static final int THRESH_H = 18;

    private final FactoryControllerScreen controller;
    private final VirtualPanelPosition gaugePos;

    private int panelX, panelY;

    // Editable / derived state.
    private int outputCount = 1;
    /** Crafts per request in crafting mode (≥1). The output slot shows outputCount × craftBatch. */
    private int craftBatch = 1;
    private int thresholdCount = 0;
    private ThresholdMode mode = ThresholdMode.ITEMS;
    private final List<VirtualPanelPosition> inputPositions = new ArrayList<>();
    private final List<Integer> inputAmounts = new ArrayList<>();
    private final List<BigItemStack> inputConfig = new ArrayList<>();   // for crafting-recipe search

    @Nullable private CraftingRecipe availableCraftingRecipe;
    private boolean craftingActive;
    private List<BigItemStack> craftingIngredients = new ArrayList<>();

    private AddressEditBox addressBox;
    private ScrollInput promiseExpiration;
    private IconButton confirmButton;
    private IconButton deleteButton;
    private IconButton newInputButton;
    private IconButton relocateButton;
    @Nullable private IconButton craftingButton;

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
            sendConfig(false, false);   // commit edits (incl. repeated slots) before leaving the screen
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
                if (craftingActive) {
                    craftingIngredients = FactoryPanelScreen.convertRecipeToPackageOrderContext(
                        availableCraftingRecipe, inputConfig, false);
                    lockOutputToRecipe();
                }
                rebuildWidgets();   // clears + re-runs init() (direct init() would duplicate widgets)
            });
            craftingButton.setToolTip(CreateLang.translate("gui.factory_panel.activate_crafting").component());
            addWidget(craftingButton);
        }
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        addressBox.tick();   // drives the address autocomplete (DestinationSuggestions)
    }

    // ── State ────────────────────────────────────────────────────────────────

    @Nullable
    private VirtualGaugeBehaviour gauge() {
        return menu.getComponent(gaugePos) instanceof VirtualGaugeBehaviour g ? g : null;
    }

    private ItemStack ingredientOf(VirtualPanelPosition pos) {
        return menu.getComponent(pos) instanceof VirtualGaugeBehaviour g ? g.filter : ItemStack.EMPTY;
    }

    private void updateConfigs() {
        inputPositions.clear();
        inputAmounts.clear();
        inputConfig.clear();
        VirtualGaugeBehaviour g = gauge();
        if (g == null) return;
        outputCount = Math.max(1, g.recipeOutput);
        craftBatch = Math.max(1, g.craftBatch);
        thresholdCount = Math.max(0, g.count);
        mode = g.mode;
        for (VirtualPanelConnection conn : g.targetedBy().values()) {
            // One grid slot per stored amount — a repeated connection expands into several slots.
            List<Integer> amts = conn.amounts.isEmpty() ? List.of(1) : conn.amounts;
            for (int raw : amts) {
                int amt = Math.max(1, raw);
                inputPositions.add(conn.from);
                inputAmounts.add(amt);
                inputConfig.add(new BigItemStack(ingredientOf(conn.from), amt));
            }
        }

        craftingActive = !g.activeCraftingArrangement.isEmpty();
        searchForCraftingRecipe();
        if (availableCraftingRecipe == null) {
            craftingActive = false;
            craftingIngredients = new ArrayList<>();
        } else if (craftingActive) {
            craftingIngredients =
                FactoryPanelScreen.convertRecipeToPackageOrderContext(availableCraftingRecipe, inputConfig, false);
            lockOutputToRecipe();
        }
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
        if (availableCraftingRecipe == null) {
            craftingActive = false;
            craftingIngredients = new ArrayList<>();
        } else if (craftingActive) {
            craftingIngredients =
                FactoryPanelScreen.convertRecipeToPackageOrderContext(availableCraftingRecipe, inputConfig, false);
            lockOutputToRecipe();
        }
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

        availableCraftingRecipe = level.getRecipeManager()
            .getAllRecipesFor(RecipeType.CRAFTING)
            .parallelStream()
            .filter(r -> output.getItem() == r.value().getResultItem(level.registryAccess()).getItem())
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
            .map(RecipeHolder::value)
            .orElse(null);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        RenderSystem.enableBlend();
        gfx.blit(PANEL_TEX, panelX, panelY, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        VirtualGaugeBehaviour g = gauge();
        List<Component> tooltip = null;

        // INPUTS — recipe arrangement when crafting is active, otherwise the connected ingredients.
        if (craftingActive) {
            for (int i = 0; i < craftingIngredients.size(); i++) {
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                ItemStack stack = craftingIngredients.get(i).stack;
                gfx.renderItem(stack, ix, iy);
                // With a batch > 1 each grid slot consumes (slot amount × batch) of its item — show it.
                if (!stack.isEmpty() && craftBatch > 1)
                    gfx.renderItemDecorations(font, stack, ix, iy,
                        String.valueOf(Math.max(1, stack.getCount()) * craftBatch));
                if (in(mouseX, mouseY, ix, iy, 16, 16))
                    tooltip = List.of(
                        CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component(),
                        CreateLang.translate("gui.factory_panel.crafting_input_tip").style(ChatFormatting.GRAY).component(),
                        CreateLang.translate("gui.factory_panel.crafting_input_tip_1").style(ChatFormatting.GRAY).component());
            }
        } else {
            for (int i = 0; i < inputPositions.size(); i++) {
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                ItemStack stack = ingredientOf(inputPositions.get(i));
                gfx.renderItem(stack, ix, iy);
                if (!stack.isEmpty())
                    gfx.renderItemDecorations(font, stack, ix, iy, String.valueOf(inputAmounts.get(i)));
                if (in(mouseX, mouseY, ix, iy, 16, 16))
                    tooltip = stack.isEmpty()
                        ? List.of(
                            CreateLang.translate("gui.factory_panel.empty_panel").color(ScrollInput.HEADER_RGB).component(),
                            Component.translatable("createfactorycontroller.gui.action_disconnect")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                        : List.of(
                            CreateLang.translate("gui.factory_panel.sending_item",
                                CreateLang.itemName(stack).add(CreateLang.text(" x" + inputAmounts.get(i))).string())
                                .color(ScrollInput.HEADER_RGB).component(),
                            CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                            Component.translatable("createfactorycontroller.gui.action_disconnect")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                            Component.translatable("createfactorycontroller.gui.action_repeat_ingredient")
                                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            if (inputPositions.isEmpty() && in(mouseX, mouseY, panelX + 68, panelY + 28, 58, 58))
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
                    CreateLang.text((g == null ? "" : g.filter.getHoverName().getString()) + " x" + promised)
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
        promiseExpiration.render(gfx, mouseX, mouseY, partialTick);
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
        deleteButton.render(gfx, mouseX, mouseY, partialTick);
        newInputButton.render(gfx, mouseX, mouseY, partialTick);
        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        if (craftingButton != null) craftingButton.render(gfx, mouseX, mouseY, partialTick);

        // Promise-interval label over the scroll box.
        int state = promiseExpiration.getState();
        String label = state == -1 ? " /" : state == 0 ? "30s" : state + "m";
        gfx.drawString(font, label, promiseExpiration.getX() + 3, promiseExpiration.getY() + 4, 0xFFEEEEEE, true);

        // Count box tooltip. In auto mode the count is system-managed, so the scroll hints are replaced
        // by a note that the target is driven by downstream requests.
        if (in(mouseX, mouseY, panelX + COUNT_X, panelY + THRESH_TOP - 1, COUNT_W, THRESH_H))
            tooltip = mode.isAuto()
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

        // Unit box tooltip — three selectable modes (items / stacks / auto), with the active one arrowed.
        if (in(mouseX, mouseY, panelX + UNIT_X, panelY + THRESH_TOP - 1, UNIT_W, THRESH_H))
            tooltip = List.of(
                CreateLang.translate("schedule.condition.threshold.item_measure").color(ScrollInput.HEADER_RGB).component(),
                ThresholdMode.ITEMS.tooltipLine(mode == ThresholdMode.ITEMS),
                ThresholdMode.STACKS.tooltipLine(mode == ThresholdMode.STACKS),
                ThresholdMode.AUTO.tooltipLine(mode == ThresholdMode.AUTO),
                CreateLang.translate("gui.scrollInput.scrollToSelect")
                    .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());

        // Filter/stock box tooltip — the filtered item's normal item tooltip.
        if (g != null && in(mouseX, mouseY, panelX + FILTER_X, panelY + THRESH_TOP, 16, 16))
            tooltip = g.filter.isEmpty()
                ? List.of(CreateLang.translate("gui.factory_panel.unconfigured_input").color(ScrollInput.HEADER_RGB).component())
                : getTooltipFromItem(Minecraft.getInstance(), g.filter);

        if (tooltip != null)
            gfx.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private void renderThreshold(GuiGraphics gfx, @Nullable VirtualGaugeBehaviour g) {
        // Left box: filter icon + current network stock, rendered with the stock-keeper number font.
        if (g != null && !g.filter.isEmpty()) {
            int fx = panelX + FILTER_X, fy = panelY + THRESH_TOP;
            gfx.renderItem(g.filter, fx, fy);
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);
            drawStockCount(gfx, g.stockLevel, fx, fy);
            gfx.pose().popPose();
        }

        // Middle box: target count, left-aligned. In auto mode the count is system-managed, so show the
        // live server value in gray (the player can't edit it here) — including a literal "0" when there
        // is currently no demand, rather than the "Inactive" label used for a manually-zeroed target.
        int displayCount = mode.isAuto() && g != null ? Math.max(0, g.count) : thresholdCount;
        String countStr = displayCount == 0 && !mode.isAuto()
            ? CreateLang.translate("gui.factory_panel.inactive").string().trim()
            : String.valueOf(displayCount);
        int countColor = mode.isAuto() ? 0xFFBBBBBB : 0xFFFFFFFF;
        gfx.drawString(font, countStr, panelX + COUNT_X + 4, panelY + THRESH_TOP + 5, countColor, true);

        // Right box: unit/mode label (Items / Stacks / Auto), left-aligned.
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

        // Input slots (only outside crafting mode). Shift-click repeats the ingredient into the next
        // slot (sharing the same connection link); a plain click removes that slot, severing the
        // link only once its last repeated slot is gone.
        if (!craftingActive)
            for (int i = 0; i < inputPositions.size(); i++) {
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                if (!in(mouseX, mouseY, ix, iy, 16, 16)) continue;
                VirtualPanelPosition from = inputPositions.get(i);

                if (hasShiftDown()) {   // shift-click → repeat into the next slot (always starts at x1)
                    ItemStack ing = ingredientOf(from);
                    if (!ing.isEmpty() && inputPositions.size() < MAX_INPUT_SLOTS) {
                        inputPositions.add(i + 1, from);
                        inputAmounts.add(i + 1, 1);
                        inputConfig.add(i + 1, new BigItemStack(ing, 1));
                        playClickSound();
                    }
                    return true;
                }

                inputPositions.remove(i);
                inputAmounts.remove(i);
                inputConfig.remove(i);
                if (!inputPositions.contains(from)) {   // last slot for this connection → drop the link
                    PacketDistributor.sendToServer(
                        new DisconnectIngredientPacket(menu.controllerPos, from, gaugePos));
                    onConnectionsChanged();   // re-evaluate crafting recipe + rebuild the crafting button
                }
                playClickSound();
                return true;
            }

        // Click the unit box → cycle Items → Stacks → Auto.
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

        // Input amounts (only outside crafting mode).
        if (!craftingActive)
            for (int i = 0; i < inputPositions.size(); i++) {
                int ix = panelX + 68 + (i % 3) * 20;
                int iy = panelY + 28 + (i / 3) * 20;
                if (in(mouseX, mouseY, ix, iy, 16, 16)) {
                    inputAmounts.set(i, Mth.clamp(inputAmounts.get(i) + dir * step, 1, 64));
                    playScrollSound();
                    return true;
                }
            }
        // Output slot. Outside crafting mode this is the free output count; in crafting mode the
        // per-craft yield is fixed, so scrolling instead changes how many crafts ride one request
        // (one craft per notch, capped so the produced total stays within 64 items).
        if (in(mouseX, mouseY, panelX + 160, panelY + 48, 16, 16)) {
            if (craftingActive) {
                int maxBatch = Math.max(1, 64 / Math.max(1, outputCount));
                int next = Mth.clamp(craftBatch + dir, 1, maxBatch);
                if (next != craftBatch) {
                    craftBatch = next;
                    playScrollSound();
                }
            } else {
                outputCount = Mth.clamp(outputCount + dir * step, 1, 64);
                playScrollSound();
            }
            return true;
        }
        // Threshold count box (max 100, regardless of unit). Locked in auto mode — the count is
        // system-managed, so swallow the scroll without changing it.
        if (in(mouseX, mouseY, panelX + COUNT_X, panelY + THRESH_TOP - 1, COUNT_W, THRESH_H)) {
            if (!mode.isAuto()) {
                thresholdCount = Mth.clamp(thresholdCount + dir * step, 0, 100);
                playScrollSound();
            }
            return true;
        }
        // Unit box → cycle Items / Stacks / Auto. Scrolling up advances the list (negate: scroll-up is
        // a positive dir but should move forward through the modes, matching the click cycle).
        if (in(mouseX, mouseY, panelX + UNIT_X, panelY + THRESH_TOP - 1, UNIT_W, THRESH_H)) {
            if (dir != 0) {
                setMode(mode.cycle(-dir));
                playScrollSound();
            }
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

        // Per-slot ingredient amounts. Outside crafting mode we send one entry per slot so repeats
        // survive (the server sums them per connection). In crafting mode the per-craft usage is one
        // value per unique connection (times the item appears in the arrangement, like Create's sendIt),
        // so repeated slots are collapsed to a single entry.
        List<VirtualPanelPosition> positions = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        if (craftingActive) {
            Set<VirtualPanelPosition> seen = new HashSet<>();
            for (VirtualPanelPosition pos : inputPositions) {
                if (!seen.add(pos)) continue;
                ItemStack ing = ingredientOf(pos);
                int c = (int) craftingIngredients.stream()
                    .filter(b -> !b.stack.isEmpty() && ItemStack.isSameItemSameComponents(b.stack, ing))
                    .count();
                positions.add(pos);
                amounts.add(Math.max(1, c));
            }
        } else {
            for (int i = 0; i < inputPositions.size(); i++) {
                positions.add(inputPositions.get(i));
                amounts.add(Math.max(1, inputAmounts.get(i)));
            }
        }

        int batch = craftingActive ? Math.max(1, craftBatch) : 1;
        PacketDistributor.sendToServer(new ConfigureRecipePacket(
            menu.controllerPos, gaugePos, addressBox.getValue(), outputCount, batch,
            promiseExpiration.getState(), thresholdCount, mode,
            positions, amounts, new ArrayList<>(arrangement), clearPromises, reset));
    }

    /**
     * Switches the threshold mode. When leaving auto, the live system-managed count becomes the new
     * editable target so the value carries over instead of snapping back to the stale snapshot.
     */
    private void setMode(ThresholdMode newMode) {
        if (mode.isAuto() && !newMode.isAuto()) {
            VirtualGaugeBehaviour g = gauge();
            if (g != null) thresholdCount = Mth.clamp(g.count, 0, 100);
        }
        mode = newMode;
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
