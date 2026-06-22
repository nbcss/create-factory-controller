package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveProductionOrderPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RequestProductionOrdersPacket;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderView;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrdersClient;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrder.Task;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.render.FluidGuiRender;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import net.minecraft.ChatFormatting;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import io.github.nbcss.createfactorycontroller.content.compat.DeployerCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone screen for the Production Orders page. Extends {@link AbstractSimiContainerScreen} so it goes through
 * Create's exact render pipeline (same background handling as the Stock Keeper, so no stray blur/dim) and centres
 * the panel identically — which keeps the borrowed Deployer tab strip aligned.
 *
 * <p>Its background is our own {@code stock_keeper_production_orders.png}, laid out exactly like Create's keeper
 * (header 36, body rows 20) but with a footer that is 3 body-rows shorter; to keep the overall frame the same
 * height (so the borrowed tab strip and keeper entity stay aligned) we draw 3 extra body rows before the short
 * footer. Each open {@link ProductionOrderView} renders as a framed entry: address + age timer + cancel button,
 * with a row of up to 9 task slots below. When Deployer is installed, navigation borrows the host
 * {@link StockKeeperRequestScreen}'s tab strip (via {@link ProductionOrdersStrip}) and the page is opened from
 * {@link ProductionOrdersTab}; when it's absent there is no strip, so the page is opened by a gutter button on the
 * Stock Keeper ({@code StockKeeperRequestScreenMixin}) and a matching gutter button here goes back.</p>
 */
public class ProductionOrdersScreen extends AbstractSimiContainerScreen<StockKeeperRequestMenu> {

    private static final ResourceLocation TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/stock_keeper_production_orders.png");

    private static final int WINDOW_W = 226;
    private static final int HEADER_H = 36;
    private static final int BODY_H = 20;
    private static final int FOOTER_H = 20;            // our short footer
    private static final int FOOTER_RESERVED = 80;     // Create's footer height — kept in the height math for alignment
    private static final int EXTRA_BODY_ROWS = (FOOTER_RESERVED - FOOTER_H) / BODY_H;   // = 3

    // Per-order entry geometry.
    private static final int FRAME_W = 184, FRAME_H = 36;
    private static final int ENTRY_H = FRAME_H;
    private static final int ENTRY_STEP = ENTRY_H;

    private final StockKeeperRequestScreen host;
    private final BlockPos keeperPos;
    private int refreshTimer = 0;

    // Vertical pixel scroll of the order list (Create-style smooth chaser).
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);
    private boolean scrollHandleActive;

    /** Green gutter button that returns to the Stock Keeper; only present (and only built) when Deployer is absent. */
    private IconButton backButton;

    // The keeper sitting beside the Stock Ticker (rendered at the panel's left), mirroring Create's screen.
    private WeakReference<LivingEntity> stockKeeper = new WeakReference<>(null);
    private WeakReference<BlazeBurnerBlockEntity> blaze = new WeakReference<>(null);
    /** Cancel-button hitboxes built each render: [x0,y0,x1,y1,orderId]. */
    private final List<int[]> cancelButtons = new ArrayList<>();
    /** Task-slot hover targets built each render (for tooltips). */
    private final List<SlotTip> slotTips = new ArrayList<>();

    private record SlotTip(int x0, int y0, int x1, int y1, ProductionOrderView.RequestView req) {
        boolean contains(double mx, double my) { return mx >= x0 && mx < x1 && my >= y0 && my < y1; }
    }

    public ProductionOrdersScreen(StockKeeperRequestScreen host, StockKeeperRequestMenu menu) {
        super(menu, Minecraft.getInstance().player.getInventory(),
              Component.translatable("createfactorycontroller.gui.production_orders"));
        this.host = host;
        this.keeperPos = menu.contentHolder.getBlockPos();
    }

    /** Window height like Create's StockKeeperRequestScreen#init: fill the screen height (minus a small margin),
     *  snapped to whole body tiles, capped at header + footer + 17 body tiles. Uses Create's footer height so the
     *  total stays identical to a Stock Keeper window. */
    private static int appropriateHeight() {
        int h = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 10;
        h -= Mth.positiveModulo(h - HEADER_H - FOOTER_RESERVED, BODY_H);
        return Math.min(h, HEADER_H + FOOTER_RESERVED + BODY_H * 17);
    }

    /** How many of Create's body rows the window reserves (footer space included as if it were Create's tall footer). */
    private int bodyCount() {
        return (imageHeight - HEADER_H - FOOTER_RESERVED) / BODY_H;
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, appropriateHeight());
        super.init();
        // Re-layout the (inactive) host so its borrowed tab strip recentres to match our panel after a resize.
        if (minecraft != null) host.resize(minecraft, width, height);
        findKeeper();

        // Without Deployer there's no tab strip — add a green gutter button that returns to the Stock Keeper.
        backButton = null;
        if (!DeployerCompat.isLoaded()) {
            backButton = new IconButton(gutterButtonX(leftPos), gutterButtonY(topPos), PRODUCTION_ORDER_ICON);
            backButton.green = true;   // green = currently on the Production Orders page
            backButton.withCallback(() -> { if (minecraft != null) minecraft.setScreen(host); });
            backButton.setToolTip(Component.translatable("createfactorycontroller.gui.production_orders"));
            addWidget(backButton);
        }
    }

    /** Locates the seated keeper / blaze burner beside the Stock Ticker, replicating StockKeeperRequestScreen#init. */
    private void findKeeper() {
        stockKeeper = new WeakReference<>(null);
        blaze = new WeakReference<>(null);
        StockTickerBlockEntity be = menu.contentHolder;
        if (be == null || be.getLevel() == null) return;
        for (int yOffset : Iterate.zeroAndOne)
            for (Direction side : Iterate.horizontalDirections) {
                BlockPos seatPos = be.getBlockPos().below(yOffset).relative(side);
                for (SeatEntity seat : be.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(seatPos)))
                    if (!seat.getPassengers().isEmpty() && seat.getPassengers().get(0) instanceof LivingEntity keeper)
                        stockKeeper = new WeakReference<>(keeper);
                if (yOffset == 0 && be.getLevel().getBlockEntity(seatPos) instanceof BlazeBurnerBlockEntity bbbe) {
                    blaze = new WeakReference<>(bbbe);
                    return;
                }
            }
    }

    // ── Gutter button (Deployer-absent navigation) ───────────────────────────
    // When Deployer is installed it draws a tab strip in the keeper's left gutter to switch pages; without it we put a
    // single Create IconButton there instead — on the Stock Keeper it opens this page (white; see
    // StockKeeperRequestScreenMixin), and here it goes back to the keeper (green = currently on this page). Both reuse
    // this geometry and the same Production Pattern icon drawn onto Create's button texture.

    private static final ItemStack GUTTER_ICON = new ItemStack(CreateFactoryController.PRODUCTION_PATTERN.get());
    /** The Production Pattern item drawn as a Create IconButton icon (16×16). */
    public static final ScreenElement PRODUCTION_ORDER_ICON = (gfx, x, y) -> gfx.renderItem(GUTTER_ICON, x, y);

    public static int gutterButtonX(int guiLeft) { return guiLeft - 14; }
    public static int gutterButtonY(int guiTop)  { return guiTop + 24; }

    // ── Layout helpers ──────────────────────────────────────────────────────

    private int frameX()    { return leftPos + 22; }
    private int slotX0()    { return leftPos + 24; }
    private int viewTop()   { return topPos + 17; }
    private int viewBottom(){ return topPos + imageHeight - 17; }

    private int maxScroll() {
        int orders = ProductionOrdersClient.get().size();
        if (orders == 0) return 0;
        int totalH = orders * ENTRY_STEP;
        return Math.max(0, totalH - (viewBottom() - viewTop()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        scroll.tickChaser();
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, maxScroll());
        if (clamped != scroll.getChaseTarget()) scroll.chase(clamped, 0.5, Chaser.EXP);
        // Snap once within half a pixel, otherwise the EXP chaser keeps inching toward the target for many ticks
        // (the bar appears to drift after the scroll has visually settled). Note scroll is in PIXELS, so the
        // threshold must be a sub-pixel value — Create's 0.0625 is for its much smaller row-unit scroll.
        if (Math.abs(scroll.getValue() - scroll.getChaseTarget()) < 0.5F)
            scroll.setValue(scroll.getChaseTarget());
        if (refreshTimer-- <= 0) {
            refreshTimer = 20;
            PacketDistributor.sendToServer(new RequestProductionOrdersPacket(keeperPos));
        }
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // Header + (bodyCount + 3 extra) body rows + short footer = same total height as a Stock Keeper window.
        gfx.blit(TEX, x - 15, y, 0, 0f, 0f, 256, HEADER_H, 256, 256);
        int by = y + HEADER_H;
        int rows = bodyCount() + EXTRA_BODY_ROWS;
        for (int i = 0; i < rows; i++) { gfx.blit(TEX, x - 15, by, 0, 0f, 48f, 256, BODY_H, 256, 256); by += BODY_H; }
        gfx.blit(TEX, x - 15, by, 0, 0f, 80f, 256, FOOTER_H, 256, 256);

        renderKeeper(gfx, mouseX, mouseY);

        Component title = getTitle();
        gfx.drawString(font, title, x + WINDOW_W / 2 - font.width(title) / 2, y + 4, 0x715425, false);

        renderOrders(gfx, mouseX, mouseY, partialTicks);
    }

    private void renderOrders(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        cancelButtons.clear();
        slotTips.clear();
        List<ProductionOrderView> orders = ProductionOrdersClient.get();

        if (orders.isEmpty()) {
            // Centered, exactly like Create's "no search results" message (shadow + same colors).
            Component msg = Component.translatable("createfactorycontroller.gui.no_production_orders");
            int w = font.width(msg);
            int mx = leftPos + WINDOW_W / 2 - w / 2;
            int my = topPos + 53;   // itemsY(=top+33) + 20, matching Create
            gfx.drawString(font, msg, mx + 1, my + 1, 0x4a2f31, false);
            gfx.drawString(font, msg, mx, my, 0xf8f8ec, false);
            return;
        }

        float s = scroll.getValue(partialTicks);
        int vt = viewTop(), vb = viewBottom();
        gfx.enableScissor(leftPos + 16, vt, leftPos + 210, vb);
        for (int i = 0; i < orders.size(); i++) {
            int oy = (int) (viewTop() - s + i * ENTRY_STEP);
            if (oy > vb) break;
            if (oy + ENTRY_H < vt) continue;
            renderEntry(gfx, orders.get(i), oy, mouseX, mouseY);
        }
        gfx.disableScissor();

        renderScrollbar(gfx, s);
    }

    private void renderEntry(GuiGraphics gfx, ProductionOrderView order, int oy, int mouseX, int mouseY) {
        int fx = frameX();
        gfx.blit(TEX, fx - 1, oy, 0, 36f, 114f, FRAME_W, FRAME_H, 256, 256);

        int lineY = oy + 4;

        // Cancel button (top-right), hover-swapped; record hitbox in screen coords.
        int btnW = 9, btnH = 10;
        int btnX = fx + FRAME_W - 5 - btnW;
        int btnY = oy + 3;
        boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH
            && mouseY >= viewTop() && mouseY < viewBottom();
        gfx.blit(TEX, btnX, btnY, 0, hovered ? 69f : 59f, 153f, btnW, btnH, 256, 256);
        if (btnY >= viewTop() - btnH && btnY < viewBottom())
            cancelButtons.add(new int[]{btnX, btnY, btnX + btnW, btnY + btnH, order.orderId()});

        // Age timer (mm:ss, or hh:mm:ss past an hour) just left of the cancel button, with the clock glyph. While the
        // order is unfinished it counts up; once every task is sent the timer freezes (server-synced) and turns green.
        boolean sent = allSent(order);
        int ageTicks = sent ? order.ageTicks() : ProductionOrdersClient.ageTicksNow(order);
        String timer = formatTime(ageTicks);
        int timerW = font.width(timer);
        int timerX = btnX - 4 - timerW;
        int timerY = oy + 4;
        int clockX = timerX - 9;
        int timerColor = sent ? 0x55FF55 : 0xF5F5E9;
        gfx.blit(TEX, clockX, timerY, 0, 49f, 154f, 7, 8, 256, 256);
        gfx.drawString(font, timer, timerX, lineY, timerColor, true);

        // Address (top-left), trimmed to the space left of the clock.
        String address = order.address().isBlank() ? "—" : order.address();
        int addrX = fx + 13;
        int addrMaxW = clockX - 4 - addrX;
        gfx.blit(TEX, fx + 2, timerY, 0, 39f, 154f, 8, 8, 256, 256);
        gfx.drawString(font, trim(address, addrMaxW), addrX, lineY, 0xF5F5E9, true);

        // Task slots (up to 9) below the frame.
        int sy = oy + 15;
        int sx0 = slotX0();
        List<ProductionOrderView.RequestView> reqs = order.requests();
        for (int i = 0; i < reqs.size() && i < 9; i++) {
            int sx = sx0 + i * 20;
            gfx.blit(TEX, sx, sy, 0, 39f, 165f, 18, 18, 256, 256);   // slot background (from our texture)
            ProductionOrderView.RequestView r = reqs.get(i);
            ItemStack stack = r.display();
            // A fluid task draws the fluid (not its filter-wrapper item) and counts in buckets (B = amount mB / 1000).
            boolean fluid = FluidCompat.isFluidFilter(stack);
            if (fluid) FluidGuiRender.filterIcon(gfx, stack, sx + 1, sy + 1);
            else gfx.renderItem(stack, sx + 1, sy + 1);
            if (sy + 1 >= viewTop() && sy + 1 < viewBottom())
                slotTips.add(new SlotTip(sx + 1, sy + 1, sx + 17, sy + 17, r));
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);   // count sprite + state symbol above the item model
            String amount = fluid ? SpriteNumbersRender.abbreviate(r.amount() / 1000) + "b"
                                  : SpriteNumbersRender.abbreviate(r.amount());
            SpriteNumbersRender.drawCount(gfx, amount, sx + 2, sy);
            drawStateSymbol(gfx, r.stateEnum(), sx, sy);
            gfx.pose().popPose();
        }
    }

    /** Renders the per-state symbol (from our texture) in the slot's top-right; nothing for WAITING / READY. */
    private void drawStateSymbol(GuiGraphics gfx, Task.State state, int sx, int sy) {
        int u, v, w, h;
        switch (state) {
            case SENT            -> { u = 80;  v = 155; w = 7; h = 6; }   // tick
            case INVALID_PATTERN -> { u = 88;  v = 154; w = 7; h = 7; }   // cross
            case PROCESSING      -> { u = 103; v = 155; w = 7; h = 5; }   // up arrow
            default              -> { return; }
        }
        gfx.blit(TEX, sx + 19 - w, sy, 0, (float) u, (float) v, w, h, 256, 256);
    }

    private void renderScrollbar(GuiGraphics gfx, float currentScroll) {
        int max = maxScroll();
        if (max <= 0) return;
        int visibleH = viewBottom() - viewTop();
        int totalH = visibleH + max;
        int barSize = Math.max(10, (int) ((long) visibleH * visibleH / totalH));
        int barX = leftPos + 204;
        // The track's top guide is 2px above viewTop, its bottom guide is at viewBottom — so the travel range is
        // (visibleH + 2) - barSize, letting the bar touch both the top and bottom lines.
        int trackTop = viewTop() - 2;
        int travel = (visibleH + 2) - barSize;
        int barY = trackTop + Math.round((currentScroll / max) * travel);
        AllGuiTextures pad = AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
        gfx.blit(pad.location, barX, barY, pad.getWidth(), barSize,
            (float) pad.getStartX(), (float) pad.getStartY(), pad.getWidth(), pad.getHeight(), 256, 256);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(gfx, barX, barY);
        if (barSize > 16) AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(gfx, barX, barY + barSize / 2 - 4);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(gfx, barX, barY + barSize - 5);
    }

    /** True once every task has been sent — the order is then complete (timer freezes + turns green). */
    private static boolean allSent(ProductionOrderView order) {
        for (ProductionOrderView.RequestView r : order.requests())
            if (r.stateEnum() != Task.State.SENT) return false;
        return true;
    }

    private static String formatTime(int ticks) {
        int sec = Math.max(0, ticks) / 20;
        int hours = sec / 3600;
        if (hours >= 99) return "99:00:00";
        if (hours > 0) return String.format("%02d:%02d:%02d", hours, (sec % 3600) / 60, sec % 60);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        while (!text.isEmpty() && font.width(text + ellipsis) > maxWidth) text = text.substring(0, text.length() - 1);
        return text + ellipsis;
    }

    /** The keeper entity / blaze burner at the panel's left — a faithful replica of StockKeeperRequestScreen#renderBg. */
    private void renderKeeper(GuiGraphics gfx, int mouseX, int mouseY) {
        PoseStack ms = gfx.pose();
        int x = leftPos, y = topPos;

        LivingEntity keeper = stockKeeper.get();
        if (keeper != null && keeper.isAlive()) {
            ms.pushPose();
            ms.translate(0.0F, 0.0F, 50.0F);
            int entitySizeOffset = (int) (Math.max(0.0, keeper.getBoundingBox().getXsize() - 1.0) * 50.0);
            int entitySizeOffsetY = (int) (Math.max(0.0, keeper.getBoundingBox().getYsize() - 1.0) * 25.0);
            int entityX = x - 35 - entitySizeOffset;
            int entityY = y + imageHeight - 47 - entitySizeOffsetY;
            InventoryScreen.renderEntityInInventoryFollowsMouse(gfx, entityX - 100, entityY - 100, entityX + 100,
                entityY + 100, 50, 0.0F, (float) mouseX, (float) Mth.clamp(mouseY, entityY - 50, entityY + 10), keeper);
            ms.popPose();
        }

        BlazeBurnerBlockEntity keeperBE = blaze.get();
        if (keeperBE != null && !keeperBE.isRemoved()) {
            ms.pushPose();
            int entityX = x - 35;
            int entityY = y + imageHeight - 43;
            ms.translate(entityX, entityY, 0.0F);
            ms.mulPose(Axis.XP.rotationDegrees(-22.5F));
            ms.mulPose(Axis.YP.rotationDegrees(-45.0F));
            ms.scale(48.0F, -48.0F, 48.0F);
            float animation = keeperBE.headAnimation.getValue(AnimationTickHolder.getPartialTicks()) * 0.175F;
            float horizontalAngle = AngleHelper.rad(270.0);
            HeatLevel heatLevel = keeperBE.getHeatLevelForRender();
            boolean canDrawFlame = heatLevel.isAtLeast(HeatLevel.FADING);
            boolean drawGoggles = keeperBE.goggles;
            PartialModel drawHat = AllPartialModels.LOGISTICS_HAT;
            int hashCode = keeperBE.hashCode();
            Lighting.setupForEntityInInventory();
            VertexConsumer cutout = gfx.bufferSource().getBuffer(RenderType.cutoutMipped());
            CachedBuffers.partial(AllPartialModels.BLAZE_CAGE, keeperBE.getBlockState())
                .rotateCentered(horizontalAngle + (float) Math.PI, Direction.UP)
                .light(15728880)
                .renderInto(ms, cutout);
            BlazeBurnerRenderer.renderShared(ms, null, gfx.bufferSource(), minecraft.level, keeperBE.getBlockState(),
                heatLevel, animation, horizontalAngle, canDrawFlame, drawGoggles, drawHat, hashCode);
            Lighting.setupFor3DItems();
            ms.popPose();
        }
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
        if (DeployerCompat.isLoaded())
            ProductionOrdersStrip.render(host, gfx, mouseX, mouseY, partialTicks);
        else if (backButton != null)
            backButton.render(gfx, mouseX, mouseY, partialTicks);   // draws the button + its own hover tooltip

        if (mouseY >= viewTop() && mouseY < viewBottom())
            for (SlotTip tip : slotTips)
                if (tip.contains(mouseX, mouseY)) {
                    gfx.renderComponentTooltip(font, slotTooltip(tip.req()), mouseX, mouseY);
                    return;
                }
    }

    /** Per-task tooltip: gold item name, request progress, and coloured status. */
    private List<Component> slotTooltip(ProductionOrderView.RequestView r) {
        List<Component> lines = new ArrayList<>();
        // A fluid task: name from the fluid (not its filter-wrapper) and amounts in buckets (B) with a "B" suffix.
        boolean fluid = FluidCompat.isFluidFilter(r.display());
        lines.add((fluid ? FluidCompat.filterName(r.display()) : r.display().getHoverName()).copy().withColor(0xFBDC7D));

        Task.State state = r.stateEnum();
        boolean active = state.isActive();
        String unit = fluid ? " B" : "";
        String amount = (fluid ? r.amount() / 1000 : r.amount()) + unit;
        String value = active
            ? (fluid ? r.inStock() / 1000 : r.inStock()) + "/" + amount   // progress: current network stock → request
            : amount;
        lines.add(Component.translatable("createfactorycontroller.gui.production_tooltip_request")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE)));

        lines.add(Component.translatable("createfactorycontroller.gui.production_tooltip_status")
            .withStyle(ChatFormatting.GRAY).append(state.getComponent()));
        return lines;
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}   // no default container labels

    // ── Input ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int[] b : cancelButtons)
                if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                    PacketDistributor.sendToServer(new RemoveProductionOrderPacket(keeperPos, b[4]));
                    ProductionOrdersClient.removeLocally(b[4]);   // optimistic: entry disappears immediately
                    return true;
                }
            // Scrollbar grab.
            int max = maxScroll();
            int barX = leftPos + 205;
            if (max > 0 && mouseX >= barX && mouseX < barX + 8 && mouseY >= viewTop() && mouseY < viewBottom()) {
                scrollHandleActive = true;
                dragScrollTo(mouseY);
                return true;
            }
            // Back-to-keeper button (only present without Deployer's tab strip); delegates hit-test + callback.
            if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (DeployerCompat.isLoaded()) {
            int result = ProductionOrdersStrip.mouseClicked(host, mouseX, mouseY, button);
            if (result == ProductionOrdersStrip.GO_BACK) {
                Minecraft.getInstance().setScreen(host);   // selection moved off our tab → back to the keeper
                return true;
            }
            if (result == ProductionOrdersStrip.STAY) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) scrollHandleActive = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && scrollHandleActive) { dragScrollTo(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void dragScrollTo(double mouseY) {
        int max = maxScroll();
        if (max <= 0) return;
        int visibleH = viewBottom() - viewTop();
        double frac = Mth.clamp((mouseY - viewTop()) / visibleH, 0.0, 1.0);
        scroll.chase(frac * max, 0.7, Chaser.EXP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        if (max > 0) {
            double target = Mth.clamp(scroll.getChaseTarget() - scrollY * 32, 0, max);
            scroll.chase(target, 0.5, Chaser.EXP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        host.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }
}
