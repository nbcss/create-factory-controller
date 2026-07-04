package io.github.nbcss.createfactorycontroller.mixin;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.ControllerFluidPromise;
import net.liukrast.deployer.lib.logistics.packager.StockInventoryType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps a {@link ControllerFluidPromise}'s owner/address tags durable across save/load — the fluid analogue of the
 * item-side {@code RequestPromiseQueue.CODEC} redirect in {@link RequestPromiseQueueMixin}.
 *
 * <p>Deployer serializes each generic promise via {@code StockInventoryType.INetworkHandler#requestCodec()} inside the
 * {@code deployer$writeSingle}/{@code deployer$readSingle} methods it mixes into {@link RequestPromiseQueue}. We
 * redirect that call to {@link ControllerFluidPromise#wrapCodec} (transparent for plain promises; layers our tags for
 * ours). Because these target methods are added by Deployer's own mixin, this runs at a higher {@code priority} so it
 * applies after Deployer's, and it is gated to installs where Deployer is present ({@code CfcMixinPlugin}).</p>
 */
@Mixin(value = RequestPromiseQueue.class, priority = 1500, remap = false)
public abstract class GenericPromiseCodecMixin {

    private static final String REQUEST_CODEC =
        "Lnet/liukrast/deployer/lib/logistics/packager/StockInventoryType$INetworkHandler;requestCodec()Lcom/mojang/serialization/Codec;";

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "deployer$writeSingle", at = @At(value = "INVOKE", target = REQUEST_CODEC))
    private Codec cfc$wrapWriteCodec(StockInventoryType.INetworkHandler handler) {
        return ControllerFluidPromise.wrapCodec(handler.requestCodec());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "deployer$readSingle", at = @At(value = "INVOKE", target = REQUEST_CODEC))
    private static Codec cfc$wrapReadCodec(StockInventoryType.INetworkHandler handler) {
        return ControllerFluidPromise.wrapCodec(handler.requestCodec());
    }
}
