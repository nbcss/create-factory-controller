package io.github.nbcss.createfactorycontroller.content.compat.computercraft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.gauge.LogicalTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.network.MissingLinkStatus;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderView;
import io.github.nbcss.createfactorycontroller.content.promise.PromiseCounts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Allow computers from CC: Tweaked to access information from a factory controller.
 * The factory controller acts as a peripheral.
 *
 * <p>Lua usage:</p>
 * {@snippet lang="lua" :
 * local fc = peripheral.find("factory_controller")
 * fc.getInfo()
 * }
 *
 * <p>The controller's state is read-only.</p>
 */
public class FactoryControllerPeripheral implements IPeripheral {

    private final FactoryControllerBlockEntity controller;

    FactoryControllerPeripheral(FactoryControllerBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public @NonNull String getType() {
        return "factory_controller";
    }

    @Override
    public Object getTarget() {
        return controller;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof FactoryControllerPeripheral p && p.controller == controller;
    }

    // ── Lua-facing getters ──────────────────────────────────────────────────

    /**
     * Get general information about the factory controller.
     * @return {@snippet lang="txt" :
     * {
     *     pos: {
     *         x: int
     *         y: int
     *         z: int
     *     }
     *     name: string -- Custom name of the controller, if any
     *     redstonePowered: boolean -- Whether the controller is powered by redstone
     *     componentCount: int -- The number of components in the factory controller
     * }
     * }
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        BlockPos pos = controller.getBlockPos();
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("x", pos.getX());
        position.put("y", pos.getY());
        position.put("z", pos.getZ());
        info.put("pos", position);
        info.put("name", controller.customName);
        info.put("redstonePowered", controller.isRedstonePowered());
        info.put("componentCount", controller.components.size());
        return info;
    }

    /**
     * Get the UUIDs of all networks known to the factory controller.
     * @return {@snippet lang="txt" :
     * string[] -- Network UUIDs
     * }
     */
    @LuaFunction(mainThread = true)
    public final List<String> getNetworks() {
        List<String> networks = new ArrayList<>();
        for (UUID network : controller.networks) networks.add(network.toString());
        return networks;
    }

    /**
     * Get summaries for all components placed in the factory controller.
     * @return {@snippet lang="txt" :
     * {
     *     pos: string -- Component position as "x,y"
     *     type: string -- Component type ID
     * }[]
     * }
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getComponents() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (VirtualComponentBehaviour c : controller.components.values())
            result.add(componentSummary(c));
        return result;
    }

    /**
     * Get information about all gauges placed in the factory controller.
     * @return {@snippet lang="txt" :
     * {
     *     pos: string -- Component position as "x,y"
     *     type: string -- Component type ID
     *     item: string -- Item ID, or an empty string when no item is set
     *     count: int -- Requested item count
     *     unit: string -- Gauge unit
     *     requestMode: string -- Gauge request mode
     *     workMode: string -- Gauge work mode
     *     stock: int -- Current stock level
     *     promised: int -- Current promised item count
     *     satisfied: boolean -- Whether the gauge is satisfied
     *     waitingForNetwork: boolean -- Whether the gauge is waiting for a network
     *     redstonePowered: boolean -- Whether the gauge is powered by redstone
     *     address: string -- Recipe address, if any
     *     network: string -- Network UUID, if any
     *     gaugeId: string -- Gauge UUID, if any
     * }[]
     * }
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getGauges() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (VirtualComponentBehaviour c : controller.components.values()) {
            if (!(c instanceof VirtualGaugeBehaviour gauge)) continue;
            result.add(gaugeSummary(gauge));
        }
        return result;
    }

    /**
     * Get information about all logical tubes attached to the factory controller.
     * @return {@snippet lang="txt" :
     * {
     *     pos: string -- Component position as "x,y"
     *     type: string -- Component type ID
     *     mode: string -- Logical tube mode
     *     powered: boolean -- Whether the logical tube is powered
     * }[]
     * }
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getLogicTubes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (VirtualComponentBehaviour c : controller.components.values()) {
            if (!(c instanceof LogicalTubeBehaviour tube)) continue;
            Map<String, Object> map = componentSummary(tube);
            map.put("mode", tube.getMode().name());
            map.put("powered", tube.isPowered());
            result.add(map);
        }
        return result;
    }

    /**
     * Get missing links in the factory controller's known networks.
     * @return {@snippet lang="txt" :
     * {
     *     network: string -- Network UUID
     *     links: string[] -- Missing link positions as "dimension@x,y,z"
     * }[]
     * }
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getMissingLinks() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MissingLinkStatus status : controller.missingLinkStatuses()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("network", status.network().toString());
            List<String> links = new ArrayList<>();
            for (GlobalPos link : status.links())
                links.add(link.dimension().location() + "@" + link.pos().getX() + "," + link.pos().getY() + "," + link.pos().getZ());
            map.put("links", links);
            result.add(map);
        }
        return result;
    }

    // Removed -- Production order is not bound by controller but a global state, so API endpoint should not be here.
    //    /** Production orders for one of this controller's networks (get the id from {@link #getNetworks()}). Empty
    //     *  list for an unknown/invalid id -- never throws back into Lua for a bad handle. */
    //    @LuaFunction(mainThread = true)
    //    public final List<Map<String, Object>> getProductionOrders(String network) {
    //        Level level = controller.getLevel();
    //        UUID net = tryParseUuid(network);
    //        if (level == null || net == null) return List.of();
    //        List<Map<String, Object>> result = new ArrayList<>();
    //        for (ProductionOrderView view : ProductionOrderManager.get(level).viewsForNetwork(net, level.getGameTime()))
    //            result.add(orderSummary(view));
//        return result;
//    }

    /**
     * Get the number of active promises associated with one gauge on a network.
     * @param network Network UUID from {@link #getNetworks()}
     * @param gaugeId Gauge UUID from {@link #getGauges()}
     * @return {@snippet lang="txt" :
     * int -- Active promise count
     * }
     */
    @LuaFunction(mainThread = true)
    public final int getPromiseCountForGauge(String network, String gaugeId) {
        Level level = controller.getLevel();
        UUID net = tryParseUuid(network);
        if (level == null || net == null || gaugeId == null || gaugeId.isBlank()) return 0;
        return PromiseCounts.owned(net, gaugeId, level.getGameTime());
    }

    /**
     * Get the number of active promises targeting a packager address on a network.
     * @param network Network UUID from {@link #getNetworks()}
     * @param address Packager address
     * @return {@snippet lang="txt" :
     * int -- Active promise count
     * }
     */
    @LuaFunction(mainThread = true)
    public final int getPromiseCountForAddress(String network, String address) {
        Level level = controller.getLevel();
        UUID net = tryParseUuid(network);
        if (level == null || net == null) return 0;
        return PromiseCounts.address(net, address, level.getGameTime());
    }

    // ── Shared summary builders ─────────────────────────────────────────────

    private static Map<String, Object> componentSummary(VirtualComponentBehaviour c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pos", posKey(c.position()));
        map.put("type", c.typeId());
        return map;
    }

    private static Map<String, Object> gaugeSummary(VirtualGaugeBehaviour gauge) {
        Map<String, Object> map = componentSummary(gauge);
        map.put("item", itemId(gauge.filter));
        map.put("count", gauge.count);
        map.put("unit", gauge.unit.name());
        map.put("requestMode", gauge.requestMode.name());
        map.put("workMode", gauge.mode.name());
        map.put("stock", gauge.stockLevel);
        map.put("promised", gauge.promisedCount);
        map.put("satisfied", gauge.satisfied);
        map.put("waitingForNetwork", gauge.waitingForNetwork);
        map.put("redstonePowered", gauge.redstonePowered);
        map.put("address", gauge.recipeAddress);
        map.put("network", gauge.networkId == null ? null : gauge.networkId.toString());
        map.put("gaugeId", gauge.gaugeId == null ? null : gauge.gaugeId.toString());
        return map;
    }

    private static Map<String, Object> orderSummary(ProductionOrderView view) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", view.orderId());
        map.put("address", view.address());
        map.put("ageTicks", view.ageTicks());
        List<Map<String, Object>> requests = new ArrayList<>();
        for (ProductionOrderView.RequestView r : view.requests()) {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("item", itemId(r.display()));
            req.put("amount", r.amount());
            req.put("inStock", r.inStock());
            req.put("state", r.stateEnum().name());
            requests.add(req);
        }
        map.put("requests", requests);
        return map;
    }

    private static String posKey(VirtualComponentPosition pos) {
        return pos.x() + "," + pos.y();
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Nullable
    private static UUID tryParseUuid(@Nullable String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
