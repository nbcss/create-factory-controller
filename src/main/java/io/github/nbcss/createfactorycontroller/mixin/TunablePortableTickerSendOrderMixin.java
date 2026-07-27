package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.nbcss.createfactorycontroller.content.production.OrderNotificationCapture;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Subscribes the ordering player for Production Order completion toasts when they send an order from
 * createphantom
 */
@Mixin(targets = "com.yision.phantom.network.ticker.TunablePortableTickerSendOrderPacket", remap = false)
public abstract class TunablePortableTickerSendOrderMixin {

    @WrapMethod(method = "applySettings")
    private void cfc$captureOrderer(ServerPlayer player, Operation<Void> original) {
        OrderNotificationCapture.set(player);
        try {
            original.call(player);
        } finally {
            OrderNotificationCapture.clear();
        }
    }
}
