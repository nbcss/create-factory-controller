package io.github.nbcss.mixin;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes two promise-over-clear bugs in Create's packager. Both stem from
 * {@code getAvailableItems()} → {@code submitNewArrivals(before, after)}, which credits every
 * positive {@code after - before} delta to the network's
 * {@link com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue} as "items that
 * entered the system" (settling outstanding promises). The
 * {@link com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue#itemEnteredSystem}
 * primitive is exact ({@code min(amount, promise.count)}); both bugs feed it a wrong {@code amount}.
 *
 * <h2>1. Baseline reset on transient capability loss ({@link #cfc$preserveBaselineOnTransientCapLoss})</h2>
 * {@code getAvailableItems()} caches {@code availableItems} as the baseline. When the target inv's
 * capability is momentarily absent (chest GUI open, neighbour/block update, arriving package's own
 * updates, chunk load → NeoForge {@code invalidateCapabilities}), {@code targetInventory.getInventory()}
 * returns {@code null} and vanilla overwrites the baseline with an <b>empty</b> (non-null) summary.
 * The next tick the cap re-resolves, so {@code before = ∅}, {@code after = }the chest's whole standing
 * stock, and that entire stock is reported as a phantom arrival — wiping promises by the amount already
 * in the chest with zero items actually entering. (The {@code before == null} guard in
 * {@code submitNewArrivals} only covers the first-ever scan; empty ≠ null slips past.)
 *
 * <p><b>Fix:</b> on the transient-null tick, short-circuit before vanilla can overwrite the baseline —
 * return the last-known summary and leave {@code availableItems} untouched, so the surviving baseline
 * yields the real delta (0 for a GUI open, just the package for a genuine arrival) once the cap returns.
 * Only the genuine null case is intercepted; an attached {@code PackagerItemHandler} keeps a non-null
 * cap ({@link InvManipulationBehaviour#hasInventory()} stays true) so its intended handling is intact.</p>
 *
 * <h2>2. Client double-credit in single-player ({@link #cfc$creditPromisesServerSideOnly})</h2>
 * The packager BE scans its inventory on <b>both</b> logical sides (the client does it to render stock).
 * Create puts no side-guard on {@code submitNewArrivals}, so the client credits promises too. On a
 * dedicated server that's harmless (the client's {@code Create.LOGISTICS} queue is a separate, empty
 * instance), but in single-player the integrated server and client share the <b>same static</b>
 * {@code Create.LOGISTICS}, so one physical arrival is credited twice — server delta + client delta —
 * clearing exactly 2× the promise. (Confirmed by logging: the same arrival fired once on the Server
 * thread and once on the Render thread for the same packager.)
 *
 * <p><b>Fix:</b> cancel {@code submitNewArrivals} when {@code level.isClientSide}. Promises are
 * server-authoritative; the client never mutates them. The client's stock <em>scan</em> in
 * {@code getAvailableItems} is unaffected (the cancel only skips the promise side-effect), so GUI
 * stock display is unchanged.</p>
 */
@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerBlockEntityMixin {

    @Shadow private InventorySummary availableItems;
    @Shadow public InvManipulationBehaviour targetInventory;

    @Inject(method = "getAvailableItems", at = @At("HEAD"), cancellable = true)
    private void cfc$preserveBaselineOnTransientCapLoss(CallbackInfoReturnable<InventorySummary> cir) {
        if (availableItems == null || targetInventory == null) return;
        if (!targetInventory.hasInventory())
            cir.setReturnValue(availableItems);
    }

    @Inject(method = "submitNewArrivals", at = @At("HEAD"), cancellable = true)
    private void cfc$creditPromisesServerSideOnly(InventorySummary before, InventorySummary after, CallbackInfo ci) {
        Level level = ((PackagerBlockEntity) (Object) this).getLevel();
        if (level == null || level.isClientSide)
            ci.cancel();
    }
}
