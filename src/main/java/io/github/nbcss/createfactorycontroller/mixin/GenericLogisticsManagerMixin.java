package io.github.nbcss.createfactorycontroller.mixin;

import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.GenericLogisticsManager;
import ru.zznty.create_factory_abstractions.generic.support.GenericOrder;

import java.util.UUID;

@Mixin(value = GenericLogisticsManager.class, remap = false)
public abstract class GenericLogisticsManagerMixin {

    @Inject(method = "broadcastPackageRequest", at = @At("HEAD"), cancellable = true)
    private static void cfc$interceptProductionOrder(UUID network, RequestType type, GenericOrder order,
                                                     IdentifiedInventory inv, String address,
                                                     CallbackInfoReturnable<Boolean> cir) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;

        for (GenericStack s : order.stacks())
            if (!(s.key() instanceof ItemKey)) return;

        PackageOrderWithCrafts crafting = order.asCrafting();
        switch (ProductionOrderManager.interceptProductionOrder(server, network, type, crafting, address)) {
            case HANDLED -> cir.setReturnValue(true);
            case REJECTED -> cir.setReturnValue(false);
            case NOT_A_PRODUCTION_ORDER -> { }
        }
    }
}
