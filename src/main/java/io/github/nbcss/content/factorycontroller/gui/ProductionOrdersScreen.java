package io.github.nbcss.content.factorycontroller.gui;

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
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.github.nbcss.content.factorycontroller.packet.RemoveProductionOrderPacket;
import io.github.nbcss.content.factorycontroller.packet.RequestProductionOrdersPacket;
import io.github.nbcss.content.factorycontroller.production.ProductionOrderView;
import io.github.nbcss.content.factorycontroller.production.ProductionOrdersClient;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.liukrast.deployer.lib.logistics.packager.screen.TabsWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone screen for the Promise Orders page. Extends {@link AbstractSimiContainerScreen} so it goes through
 * Create's exact render pipeline (same background handling as the Stock Keeper, so no stray blur/dim) and centres
 * the panel identically — which keeps the borrowed Deployer tab strip aligned. It reuses Create's dynamic-height
 * request-panel background ({@code STOCK_KEEPER_REQUEST_{HEADER,BODY,FOOTER}}) and borrows the host
 * {@link StockKeeperRequestScreen}'s {@code TabsWidget} for navigation. Opened from {@link ProductionOrdersTab}.
 */
public class ProductionOrdersScreen extends AbstractSimiContainerScreen<StockKeeperRequestMenu> {

    private static final AllGuiTextures HEADER = AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER;
    private static final AllGuiTextures BODY = AllGuiTextures.STOCK_KEEPER_REQUEST_BODY;
    private static final AllGuiTextures FOOTER = AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;
    private static final int WINDOW_W = 226;
    private static final int ROW_H = 20;
    private static final int REMOVE_W = 12;

    private final StockKeeperRequestScreen host;
    private final BlockPos keeperPos;
    private int refreshTimer = 0;
    // The keeper sitting beside the Stock Ticker (rendered at the panel's left), mirroring Create's screen.
    private WeakReference<LivingEntity> stockKeeper = new WeakReference<>(null);
    private WeakReference<BlazeBurnerBlockEntity> blaze = new WeakReference<>(null);
    /** Remove-button hitboxes built each render: [x0,y0,x1,y1,orderId]. */
    private final List<int[]> removeButtons = new ArrayList<>();

    public ProductionOrdersScreen(StockKeeperRequestScreen host, StockKeeperRequestMenu menu) {
        super(menu, Minecraft.getInstance().player.getInventory(),
              Component.translatable("createfactorycontroller.gui.production_orders"));
        this.host = host;
        this.keeperPos = menu.contentHolder.getBlockPos();
    }

    /** Window height like Create's StockKeeperRequestScreen#init: fill the screen height (minus a small margin),
     *  snapped to whole body tiles, capped at header + footer + 17 body tiles. */
    private static int appropriateHeight() {
        int h = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 10;
        h -= Mth.positiveModulo(h - HEADER.getHeight() - FOOTER.getHeight(), BODY.getHeight());
        return Math.min(h, HEADER.getHeight() + FOOTER.getHeight() + BODY.getHeight() * 17);
    }

    private int bodyCount() {
        return (imageHeight - HEADER.getHeight() - FOOTER.getHeight()) / BODY.getHeight();
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, appropriateHeight());
        super.init();
        // Re-layout the (inactive) host so its borrowed tab strip recentres to match our panel after a resize.
        if (minecraft != null) host.resize(minecraft, width, height);
        findKeeper();
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

    /** The host's Deployer tab strip (same instance as the host's field), or null if absent. */
    @Nullable
    private TabsWidget<?> tabStrip() {
        for (GuiEventListener c : host.children())
            if (c instanceof TabsWidget<?> t) return t;
        return null;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (refreshTimer-- <= 0) {
            refreshTimer = 20;
            PacketDistributor.sendToServer(new RequestProductionOrdersPacket(keeperPos));
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        HEADER.render(gfx, x - 15, y);
        int by = y + HEADER.getHeight();
        for (int i = 0; i < bodyCount(); i++) { BODY.render(gfx, x - 15, by); by += BODY.getHeight(); }
        FOOTER.render(gfx, x - 15, by);

        renderKeeper(gfx, mouseX, mouseY);

        Font font = this.font;
        gfx.drawString(font, getTitle(), x + WINDOW_W / 2 - font.width(getTitle()) / 2, y + 4, 0x715425, false);
        renderOrders(gfx, mouseX, mouseY, x + 22, y + 18);
    }

        /** The keeper entity / blaze burner at the panel's left — a faithful replica of StockKeeperRequestScreen#renderBg. */
    private void renderKeeper(GuiGraphics gfx, int mouseX, int mouseY) {
        PoseStack ms = gfx.pose();
        int x = leftPos, y = topPos;

        // Render keeper
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

    private void renderOrders(GuiGraphics gfx, int mouseX, int mouseY, int x, int y) {
        Font font = this.font;
        removeButtons.clear();
        List<ProductionOrderView> orders = ProductionOrdersClient.get();
        if (orders.isEmpty()) {
            gfx.drawString(font, Component.translatable("createfactorycontroller.gui.promise_orders_empty"),
                x, y, 0xFF808080, false);
            return;
        }
        int maxY = topPos + imageHeight - FOOTER.getHeight() - ROW_H;
        for (ProductionOrderView order : orders) {
            if (y > maxY) break;
            gfx.drawString(font, Component.translatable("createfactorycontroller.gui.promise_order_title", order.orderId()),
                x, y, 0xFFB0B0B0, false);
            int bx = leftPos + WINDOW_W - REMOVE_W - 8;
            boolean hovered = mouseX >= bx && mouseX < bx + REMOVE_W && mouseY >= y - 1 && mouseY < y + 9;
            gfx.drawString(font, "✖", bx, y, hovered ? 0xFFFF6060 : 0xFFAA4040, false);
            removeButtons.add(new int[]{bx, y - 1, bx + REMOVE_W, y + 9, order.orderId()});
            y += 11;

            for (ProductionOrderView.RequestView r : order.requests()) {
                if (y > maxY) break;
                ItemStack display = r.display();
                gfx.renderItem(display, x + 2, y - 4);
                gfx.drawString(font, display.getHoverName().getString() + " ×" + r.amount(), x + 22, y, 0xFFE0E0E0, false);
                Component stateLabel = switch (r.stateEnum()) {
                    case DONE -> Component.translatable("createfactorycontroller.gui.promise_request_done").withStyle(ChatFormatting.GREEN);
                    case ABORTED -> Component.literal("✖").withStyle(ChatFormatting.RED);
                    default -> Component.translatable("createfactorycontroller.gui.promise_request_pending").withStyle(ChatFormatting.YELLOW);
                };
                gfx.drawString(font, stateLabel, x + 22, y + 9, 0xFFFFFFFF, false);
                y += ROW_H;
            }
            y += 4;
        }
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
        TabsWidget<?> strip = tabStrip();
        if (strip != null) strip.render(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}   // no default container labels

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0)
            for (int[] b : removeButtons)
                if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                    PacketDistributor.sendToServer(new RemoveProductionOrderPacket(keeperPos, b[4]));
                    return true;
                }
        TabsWidget<?> strip = tabStrip();
        if (strip != null && strip.mouseClicked(mouseX, mouseY, button)) {
            if (!(strip.getSelected() instanceof ProductionOrdersTab))
                Minecraft.getInstance().setScreen(host);   // selection moved off our tab → back to the keeper
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        host.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }
}
