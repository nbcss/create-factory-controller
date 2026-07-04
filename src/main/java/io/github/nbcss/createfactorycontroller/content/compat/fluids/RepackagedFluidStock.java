package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.liukrast.deployer.lib.logistics.packager.AbstractPackagerBlockEntity;
import net.liukrast.deployer.lib.logistics.packager.GenericPackagingRequest;
import net.liukrast.deployer.lib.logistics.packager.StockInventoryType;
import net.liukrast.deployer.lib.logistics.packagerLink.GenericRequestPromise;
import net.liukrast.deployer.lib.logistics.packagerLink.LogisticsGenericManager;
import net.liukrast.deployer.lib.logistics.stockTicker.GenericOrderContained;
import net.liukrast.deployer.lib.mixinExtensions.RPQExtension;
import net.liukrast.deployer.lib.registry.DeployerRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Reads and promises fluid stock through Create: Repackaged's Deployer-backed fluid stock system — the generic
 * equivalents of Create's item {@code LogisticsManager.getStockOf} / {@code RequestPromiseQueue}, over Deployer's
 * {@code repackaged:fluid} {@link StockInventoryType}.
 *
 * <p>All Deployer references live here, NOT in {@code VirtualGaugeBehaviour} — that class is loaded for item gauges on
 * installs without Deployer, so a direct link would break class loading. Every method is reached only from a
 * FLUID-gauge branch, which can only run when Repackaged (hence Deployer) is installed, so this class loads lazily.
 * Deployer is {@code compileOnly}, so this compiles without it on the runtime classpath.</p>
 */
final class RepackagedFluidStock {

    /** Registry id of Repackaged's fluid stock type (matches its panel type / {@code stock_inventory_type.repackaged.fluid}). */
    private static final ResourceLocation FLUID_STOCK_TYPE =
        ResourceLocation.fromNamespaceAndPath("repackaged", "fluid");

    private RepackagedFluidStock() {}

    private static StockInventoryType<?, ?, ?> fluidType() {
        return DeployerRegistries.STOCK_INVENTORY.get(FLUID_STOCK_TYPE);
    }

    /** This network's promise queue viewed as Deployer's generic-promise extension, or null if it has none yet. */
    private static RPQExtension promiseQueue(UUID network) {
        RequestPromiseQueue queue = Create.LOGISTICS.getQueuedPromises(network);
        return queue == null ? null : (RPQExtension) queue;
    }

    /** Network stock of {@code fluid} in millibuckets, or 0 if the fluid stock type isn't registered / args are empty. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static int stock(UUID network, FluidStack fluid) {
        if (network == null || fluid.isEmpty()) return 0;
        StockInventoryType<?, ?, ?> type = fluidType();
        if (type == null) return 0;
        return LogisticsGenericManager.getStockOf((StockInventoryType) type, network, fluid, null);
    }

    /** Open promised amount (mB) of {@code fluid} on the network, expiring stale promises older than {@code expiry}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static int promised(UUID network, FluidStack fluid, int expiry) {
        if (network == null || fluid.isEmpty()) return 0;
        StockInventoryType<?, ?, ?> type = fluidType();
        RPQExtension queue = promiseQueue(network);
        if (type == null || queue == null) return 0;
        return queue.deployer$getTotalPromisedAndRemoveExpired((StockInventoryType) type, fluid, expiry);
    }

    /** Promises {@code amount} mB of {@code fluid} on the network (so the gauge doesn't re-request until it arrives). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addPromise(UUID network, FluidStack fluid, int amount) {
        if (network == null || fluid.isEmpty() || amount <= 0) return;
        StockInventoryType<?, ?, ?> type = fluidType();
        RPQExtension queue = promiseQueue(network);
        if (type == null || queue == null) return;
        queue.deployer$add((StockInventoryType) type, new GenericRequestPromise(fluid.copyWithAmount(amount)));
    }

    /** As {@link #addPromise(UUID, FluidStack, int)} but tags the promise with the minting gauge/address (a
     *  {@link ControllerFluidPromise}), so it counts against that gauge's / address's promise limit. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addPromise(UUID network, FluidStack fluid, int amount, String ownerKey, String targetAddress) {
        if (network == null || fluid.isEmpty() || amount <= 0) return;
        StockInventoryType<?, ?, ?> type = fluidType();
        RPQExtension queue = promiseQueue(network);
        if (type == null || queue == null) return;
        queue.deployer$add((StockInventoryType) type,
            new ControllerFluidPromise(fluid.copyWithAmount(amount), ownerKey, targetAddress));
    }

    // ── Promise-limit counting (fluid analogue of PromiseCounts) ────────────────
    // Per-network, per-tick cache of ControllerFluidPromise counts bucketed by owner (gaugeId) and target address,
    // built lazily from one deployer$flatten pass and only for networks queried this tick.

    private static long countTick = Long.MIN_VALUE;
    private static final Map<UUID, Counts> countCache = new HashMap<>();

    private record Counts(Map<String, Integer> owned, Map<String, Integer> address) {
        Counts() { this(new HashMap<>(), new HashMap<>()); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Counts counts(UUID network, long gameTime) {
        if (gameTime != countTick) { countTick = gameTime; countCache.clear(); }
        return countCache.computeIfAbsent(network, n -> {
            Counts c = new Counts();
            StockInventoryType<?, ?, ?> type = fluidType();
            RPQExtension queue = promiseQueue(n);
            if (type == null || queue == null) return c;
            for (GenericRequestPromise<?> p : (List<GenericRequestPromise<?>>) (List<?>)
                    queue.deployer$flatten((StockInventoryType) type, false))
                if (p instanceof ControllerFluidPromise cp) {
                    if (cp.ownerKey != null && !cp.ownerKey.isBlank()) c.owned.merge(cp.ownerKey, 1, Integer::sum);
                    if (cp.targetAddress != null && !cp.targetAddress.isBlank())
                        c.address.merge(cp.targetAddress, 1, Integer::sum);
                }
            return c;
        });
    }

    /** Active fluid promises this tick minted by gauge {@code ownerKey} on {@code network}. */
    static int owned(UUID network, String ownerKey, long gameTime) {
        return ownerKey == null || ownerKey.isBlank() ? 0 : counts(network, gameTime).owned().getOrDefault(ownerKey, 0);
    }

    /** Active fluid promises this tick targeting {@code address} on {@code network}. */
    static int address(UUID network, String address, long gameTime) {
        return address == null || address.isBlank() ? 0 : counts(network, gameTime).address().getOrDefault(address, 0);
    }

    /** Fold a just-dispatched promise into this tick's cache (if built) so a gauge firing later this tick sees it. */
    static void onAdded(UUID network, String ownerKey, String address, long gameTime) {
        if (gameTime != countTick) return;
        Counts c = countCache.get(network);
        if (c == null) return;
        if (ownerKey != null && !ownerKey.isBlank()) c.owned().merge(ownerKey, 1, Integer::sum);
        if (address != null && !address.isBlank()) c.address().merge(address, 1, Integer::sum);
    }

    /** Force-clears all open promises of {@code fluid} (a player-triggered reset). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void forceClear(UUID network, FluidStack fluid) {
        if (network == null || fluid.isEmpty()) return;
        StockInventoryType<?, ?, ?> type = fluidType();
        RPQExtension queue = promiseQueue(network);
        if (type == null || queue == null) return;
        queue.deployer$forceClear((StockInventoryType) type, fluid);
    }

    /**
     * Broadcasts a whole recipe request — its item ingredients ({@code itemOrder}, possibly empty) and its Repackaged
     * fluid ingredients ({@code fluids}, mB amounts) — to the network's packagers at {@code address}, as ONE order.
     *
     * <p>Uses Deployer's {@code broadcastAllPackageRequest}, which derives a single orderId (from the item request, or
     * random) and reuses it for the fluid dispatch. This unified orderId is essential: Repackaged's Package Shelf
     * (re-packager) groups incoming package fragments by orderId, so item and fluid fragments of the same recipe must
     * share one — dispatching the fluids as a separate order leaves the shelf unable to assemble the recipe.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean broadcastAll(UUID network, PackageOrderWithCrafts itemOrder, List<FluidStack> fluids, String address) {
        if (network == null) return false;
        StockInventoryType<?, ?, ?> type = fluidType();
        if (type == null) return false;
        GenericOrderContained order = ((StockInventoryType) type).valueHandler().createContained(fluids);
        Map<StockInventoryType<?, ?, ?>, GenericOrderContained<?>> orders = new HashMap<>();
        orders.put(type, order);
        return LogisticsGenericManager.broadcastAllPackageRequest(itemOrder, network,
            LogisticallyLinkedBehaviour.RequestType.RESTOCK, orders, address);
    }

    /**
     * Ships {@code fluid} (its amount = the mB to send) to {@code address} as one <b>link</b> ({@code linkIndex},
     * {@code finalLink}) of the shared order {@code orderId} — the fluid analogue of
     * {@code ProductionOrderManager.dispatchWithOrderId}. Finds the fluid packagers, re-stamps each request with the
     * order's link metadata (so Repackaged's Package Shelf merges this link with the order's item/fluid links), and
     * performs them. Returns whether it shipped.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean dispatchWithOrderId(UUID network, FluidStack fluid, String address,
                                       int orderId, int linkIndex, boolean finalLink) {
        if (network == null || fluid.isEmpty()) return false;
        StockInventoryType<?, ?, ?> type = fluidType();
        if (type == null) return false;
        GenericOrderContained order = ((StockInventoryType) type).valueHandler().createContained(List.of(fluid));
        Multimap found = LogisticsGenericManager.findPackagersForRequest(
            (StockInventoryType) type, network, order, null, address, (IntSupplier) () -> orderId);
        if (found.isEmpty()) return false;
        for (Object packager : found.keySet())
            if (((AbstractPackagerBlockEntity) packager).isTooBusyFor(LogisticallyLinkedBehaviour.RequestType.RESTOCK))
                return false;

        // One shared final-link flag for this dispatch's link (mirrors the item path's stamping).
        MutableBoolean finalFlag = new MutableBoolean(finalLink);
        Multimap stamped = HashMultimap.create();
        for (Object entry : found.entries()) {
            Map.Entry e = (Map.Entry) entry;
            GenericPackagingRequest o = (GenericPackagingRequest) e.getValue();
            stamped.put(e.getKey(), GenericPackagingRequest.create(
                o.item(), o.getCount(), o.address(), linkIndex, finalFlag,
                o.packageCounter().intValue(), orderId, o.context()));
        }
        LogisticsGenericManager.performPackageRequests(stamped, 0, true);
        return true;
    }
}
