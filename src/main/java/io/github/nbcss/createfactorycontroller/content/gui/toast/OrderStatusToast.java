package io.github.nbcss.createfactorycontroller.content.gui.toast;

import io.github.nbcss.createfactorycontroller.content.packet.OrderNotificationPacket;
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

/**
 * An advancement-style pop-out for order status: the produced item's icon, a coloured header ("Order Complete!"
 * / "Gauge Invalid"), and a subtitle (the order address, or the item name when addressless). Modelled on
 * vanilla {@code AdvancementToast} — same background sprite, layout, and 5 s display window.
 */
@OnlyIn(Dist.CLIENT)
public class OrderStatusToast implements Toast {

    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_TIME = 5000L;
    private static final int HEADER_COMPLETE = 0x55FF55;   // green
    private static final int HEADER_INVALID  = 0xFF5555;   // red

    private final ItemStack icon;
    private final Component header;
    private final Component subtitle;
    private final int headerColor;

    private OrderStatusToast(int kind, ItemStack icon, String address) {
        boolean complete = kind == OrderNotificationPacket.KIND_ORDER_COMPLETE;
        this.icon = icon;
        this.header = Component.translatable(complete
            ? "createfactorycontroller.toast.order_complete" : "createfactorycontroller.toast.gauge_invalid");
        this.subtitle = address.isBlank() ? icon.getHoverName() : Component.literal(address);
        this.headerColor = complete ? HEADER_COMPLETE : HEADER_INVALID;
    }

    /** Queues the toast on the client (called from the packet handler). */
    public static void show(int kind, ItemStack icon, String address) {
        Minecraft.getInstance().getToasts().addToast(new OrderStatusToast(kind, icon, address));
    }

    @Override
    public Toast.Visibility render(GuiGraphics gfx, ToastComponent component, long timeSinceVisible) {
        gfx.blitSprite(BACKGROUND, 0, 0, width(), height());
        Font font = component.getMinecraft().font;
        gfx.drawString(font, header, 30, 7, headerColor, false);
        gfx.drawString(font, subtitle, 30, 18, 0xFFFFFF, false);
        gfx.renderFakeItem(icon, 8, 8);
        return timeSinceVisible >= DISPLAY_TIME * component.getNotificationDisplayTimeMultiplier()
            ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }
}
