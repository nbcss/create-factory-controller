package io.github.nbcss.createfactorycontroller.content.gui.toast;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.packet.OrderNotificationPacket;
import io.github.nbcss.createfactorycontroller.content.packet.OrderNotificationPacket.RequestedItem;
import io.github.nbcss.createfactorycontroller.content.render.FluidGuiRender;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * A pop-out for order status: every requested item and amount, a coloured header ("Order Complete!" /
 * "Gauge Invalid"), and the order address. Modelled on
 * vanilla {@code AdvancementToast} — same background sprite, layout, and 5 s display window.
 */
@OnlyIn(Dist.CLIENT)
public class OrderStatusToast implements Toast {
    private static final ResourceLocation BOX = ResourceLocation.fromNamespaceAndPath(
            CreateFactoryController.MODID, "toast/production_order/box");
    private static final ResourceLocation TAB = ResourceLocation.fromNamespaceAndPath(
            CreateFactoryController.MODID, "toast/production_order/tab");
    private static final ResourceLocation TAG = ResourceLocation.fromNamespaceAndPath(
            CreateFactoryController.MODID, "toast/production_order/tag");
    private static final long DISPLAY_TIME = 5000L;
    private static final int MAX_LABEL_WIDTH = 150;
    private static final int HEADER_COMPLETE = 0x55FF55;   // green
    private static final int HEADER_INVALID  = 0xFF5555;   // red

    private final List<RequestedItem> requestedItems;
    private final Component header;
    private final Component address;
    private final int headerColor;

    @Override
    public int height() {
        return 52;
    }

    @Override
    public int width() {
        return 160;
    }

    private OrderStatusToast(int kind, List<RequestedItem> requestedItems, String address) {
        boolean complete = kind == OrderNotificationPacket.KIND_ORDER_COMPLETE;
        this.requestedItems = List.copyOf(requestedItems);
        this.header = Component.translatable(complete
            ? "createfactorycontroller.toast.order_complete" : "createfactorycontroller.toast.gauge_invalid");
        this.address = Component.literal(address);
        this.headerColor = complete ? HEADER_COMPLETE : HEADER_INVALID;
    }

    /** Queues the toast on the client (called from the packet handler). */
    public static void show(int kind, List<RequestedItem> requestedItems, String address) {
        Minecraft.getInstance().getToasts().addToast(new OrderStatusToast(kind, requestedItems, address));
    }

    @Override
    public Toast.Visibility render(GuiGraphics gfx, ToastComponent component, long timeSinceVisible) {
        Font font = component.getMinecraft().font;
        int headerWidth = Math.min(MAX_LABEL_WIDTH, font.width(header) + 8);
        int addressWidth = Math.min(MAX_LABEL_WIDTH, font.width(address) + 20);
        TiledSpriteRenderer.create(TAB).render(gfx, 5, 0, headerWidth, 12);
        gfx.blitSprite(BOX, -1, 11, 162, 32);
        TiledSpriteRenderer.create(TAG).render(gfx, 4, 37, addressWidth, 15);
        gfx.drawString(font, trimToWidth(font, header, headerWidth - 8), 9, 3, headerColor, true);
        gfx.drawString(font, trimToWidth(font, address, addressWidth - 20), 21, 41, 0x444444, false);

        for (int i = 0; i < requestedItems.size(); i++) {
            RequestedItem requested = requestedItems.get(i);
            ItemStack item = requested.item();
            int itemX = 4 + i * 17;
            int itemY = 16;
            boolean fluid = FluidCompat.isFluidFilter(item);
            if (fluid) FluidGuiRender.filterIcon(gfx, item, itemX, itemY);
            else gfx.renderFakeItem(item, itemX, itemY);
            String amount = fluid ? SpriteNumbersRender.abbreviate(requested.amount() / 1000) + "b"
                                  : SpriteNumbersRender.abbreviate(requested.amount());
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);
            SpriteNumbersRender.drawCount(gfx, amount, itemX, itemY);
            gfx.pose().popPose();
        }
        return timeSinceVisible >= DISPLAY_TIME * component.getNotificationDisplayTimeMultiplier()
            ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }

    private static String trimToWidth(Font font, Component text, int maxWidth) {
        String value = text.getString();
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        while (!value.isEmpty() && font.width(value + ellipsis) > maxWidth)
            value = value.substring(0, value.length() - 1);
        return value + ellipsis;
    }
}
