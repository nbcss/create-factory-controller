package io.github.nbcss.createfactorycontroller.mixin;

import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StockTickerBlockEntity.class)
public abstract class StockTickerBlockEntityMixin extends StockCheckingBlockEntity {

    protected StockTickerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "broadcastPackageRequest", at = @At("HEAD"), cancellable = true)
    private void cfc$splitProductionOrder(RequestType type, PackageOrderWithCrafts order,
                                       IdentifiedInventory ignoredHandler, String address,
                                       CallbackInfoReturnable<Boolean> cir) {
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (this.behaviour == null || this.behaviour.freqId == null) return;
        if (ProductionOrderManager.interceptProductionOrder(serverLevel.getServer(), this.behaviour.freqId, type, order, address)) {
            setChanged();
            cir.setReturnValue(true);
        }
    }
}
