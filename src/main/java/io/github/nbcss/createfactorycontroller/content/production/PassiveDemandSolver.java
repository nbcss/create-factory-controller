package io.github.nbcss.createfactorycontroller.content.production;

import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller-wide passive-demand solve for the "total demand" request strategy (server config
 * {@code passiveTotalDemand}). Replaces the per-gauge {@link VirtualGaugeBehaviour#computeDemand()} ripple with a
 * single topological pass over the controller's gauge graph, so every passive gauge is sized from one consistent
 * snapshot in one tick (no map-order nondeterminism, no one-hop-per-tick latency).
 *
 * <p>Each gauge is a node; ingredient wires ({@code targetedBy}) are edges consumer→source. Demand flows upstream:
 * a consumer's craft count (sized from its own deficit = demand − stock − open promises) multiplies the per-craft
 * ingredient amount into each source's gross demand. Nodes are drained in topological order (a producer is sized only
 * once all of its consumers' demand is known), exactly mirroring {@link IngredientDemandResolver}. Only PASSIVE gauges
 * derive downstream demand; manual (NORMAL) gauges contribute their fixed target as a seed. A passive gauge's
 * configured {@code count} is its minimum and also participates in upstream propagation.</p>
 *
 * <p>Stock/promise figures are read from each gauge's last-tick {@code stockLevel}/{@code promisedCount} (already
 * computed by its storage monitor), so the solve makes no network-summary calls. Subtracting open promises at every
 * stage is what keeps the strategy from over-requesting: a just-promised batch shrinks the next solve's deficit.</p>
 */
public final class PassiveDemandSolver {

    private PassiveDemandSolver() {}

    public static void solve(FactoryControllerBlockEntity controller) {
        Level level = controller.getLevel();

        // skip non passive gauge
        boolean anyPassive = false;
        for (VirtualComponentBehaviour c : controller.components.values())
            if (c instanceof VirtualGaugeBehaviour g && g.requestMode.isPassive()) { anyPassive = true; break; }
        if (!anyPassive) return;

        // Index every gauge as a node.
        List<VirtualGaugeBehaviour> nodes = new ArrayList<>();
        Map<VirtualComponentPosition, Integer> idx = new HashMap<>();
        for (VirtualComponentBehaviour c : controller.components.values())
            if (c instanceof VirtualGaugeBehaviour g) { idx.put(g.position(), nodes.size()); nodes.add(g); }
        int n = nodes.size();
        if (n == 0) return;

        long[] dem = new long[n];                 // gross demand for this node's output (raw item count / mB)
        int[] indeg = new int[n];                 // number of consumers (must be sized before this node)
        List<List<long[]>> edges = new ArrayList<>(n);   // {sourceIndex, baseQty, excludedFromMultiplier ? 1 : 0}
        for (int i = 0; i < n; i++) edges.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            VirtualGaugeBehaviour g = nodes.get(i);
            int batch = craftBatch(g);
            for (Connection c : g.incomingConnections()) {
                if (!(c instanceof LogisticsConnection lc)) continue;   // ingredient wires only
                Integer si = idx.get(c.from);
                if (si == null) continue;
                boolean excluded = g.mode != GaugeWorkMode.CRAFTING && lc.excludeFromRequestMultiplier;
                edges.get(i).add(new long[]{ si, (long) lc.amount() * batch, excluded ? 1 : 0 });
                indeg[si]++;
            }
        }

        // Seed terminal demand from open production orders (player Stock-Keeper blueprints) on orderable passive gauges.
        for (int i = 0; i < n; i++) {
            VirtualGaugeBehaviour g = nodes.get(i);
            if (g.requestMode.isPassive() && g.gaugeId != null && level != null)
                dem[i] += ProductionOrderManager.externalDemand(level, g.networkId, g.gaugeId);
        }

        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (indeg[i] == 0) queue.add(i);

        long[] downstreamPassive = new long[n];   // downstream-only demand for passive nodes (others stay 0)
        while (!queue.isEmpty()) {
            int i = queue.poll();
            VirtualGaugeBehaviour g = nodes.get(i);
            int mult = Math.max(1, g.unit.toCountMultiplier(g.filter));

            // A passive gauge keeps the consumer/order portion as its dynamic target, then applies configured count as
            // a floor to the gross target that drives both itself and its upstream ingredients.
            long configured = (long) g.count * mult;
            long gross;
            if (g.requestMode.isPassive()) {
                downstreamPassive[i] = Math.max(0, dem[i]);
                gross = Math.max(downstreamPassive[i], configured);
            } else {
                gross = configured;
            }

            // Deficit drives how many crafts to push upstream. Subtract the gap-safe held sum (stock + promised held
            // against the promise→inventory settlement dip), not the live stock/promise pair, or a just-landed item —
            // promise cleared but summary not yet refreshed — would momentarily inflate the deficit and over-request.
            long net = gross - g.effectiveHeld();
            int output = Math.max(1, g.recipeOutput) * craftBatch(g);
            long crafts = (net > 0 && !g.filter.isEmpty()) ? Math.ceilDiv(net, output) : 0;
            long dispatches = crafts <= 0 ? 0
                : Math.ceilDiv(crafts, g.effectiveRequestMultiplierCeiling());

            for (long[] edge : edges.get(i)) {
                int s = (int) edge[0];
                if (crafts > 0) dem[s] += (edge[2] != 0 ? dispatches : crafts) * edge[1];
                if (--indeg[s] == 0) queue.add(s);
            }
        }

        // Apply the downstream-only part. The gauge combines it with configured count and commits a decrease through
        // its summary-refresh hold. A cycle leaves a node unprocessed (downstream 0), the safe degradation.
        for (int i = 0; i < n; i++) {
            VirtualGaugeBehaviour g = nodes.get(i);
            if (!g.requestMode.isPassive()) continue;
            int mult = Math.max(1, g.unit.toCountMultiplier(g.filter));
            long units = downstreamPassive[i] <= 0 ? 0 : Math.ceilDiv(downstreamPassive[i], mult);
            g.stagePassiveDemandTarget((int) Math.min(Integer.MAX_VALUE, units));
        }
    }

    private static int craftBatch(VirtualGaugeBehaviour g) {
        return g.mode == GaugeWorkMode.CRAFTING ? Math.max(1, g.craftBatch) : 1;
    }
}
