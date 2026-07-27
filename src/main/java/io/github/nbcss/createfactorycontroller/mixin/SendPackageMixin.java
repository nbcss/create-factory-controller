package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.nbcss.createfactorycontroller.content.production.OrderNotificationCapture;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Subscribes the ordering player for Production Order completion toasts when they send an order from
 * Create: Mobile Packages
 */
@Mixin(targets = "de.theidler.create_mobile_packages.items.portable_stock_ticker.SendPackage", remap = false)
public abstract class SendPackageMixin {

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
