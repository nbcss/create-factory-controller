package io.github.nbcss.createfactorycontroller.content.production;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
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
 * are resized; manual (NORMAL) gauges contribute their fixed target as a seed but keep their own {@code count}.</p>
 *
 * <p>Stock/promise figures are read from each gauge's last-tick {@code stockLevel}/{@code promisedCount} (already
 * computed by its storage monitor), so the solve makes no network-summary calls. Subtracting open promises at every
 * stage is what keeps the strategy from over-requesting: a just-promised batch shrinks the next solve's deficit.</p>
 */
public final class PassiveDemandSolver {

    private PassiveDemandSolver() {}

    public static void solve(FactoryControllerBlockEntity controller) {
        Level level = controller.getLevel();

        // Index every gauge as a node.
        List<VirtualGaugeBehaviour> nodes = new ArrayList<>();
        Map<VirtualComponentPosition, Integer> idx = new HashMap<>();
        for (VirtualComponentBehaviour c : controller.components.values())
            if (c instanceof VirtualGaugeBehaviour g) { idx.put(g.position(), nodes.size()); nodes.add(g); }
        int n = nodes.size();
        if (n == 0) return;

        long[] dem = new long[n];                 // gross demand for this node's output (raw item count / mB)
        int[] indeg = new int[n];                 // number of consumers (must be sized before this node)
        List<List<long[]>> edges = new ArrayList<>(n);   // edges[i] = {sourceIndex, perCraftQty}
        for (int i = 0; i < n; i++) edges.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            VirtualGaugeBehaviour g = nodes.get(i);
            int batch = craftBatch(g);
            for (Map.Entry<VirtualComponentPosition, ?> e : g.targetedBy().entrySet()) {
                if (!(e.getValue() instanceof LogisticsConnection lc)) continue;   // ingredient wires only
                Integer si = idx.get(e.getKey());
                if (si == null) continue;
                edges.get(i).add(new long[]{ si, (long) lc.amount() * batch });
                indeg[si]++;
            }
        }

        // Seed terminal demand from open production orders (player Stock-Keeper blueprints) on orderable passive gauges.
        for (int i = 0; i < n; i++) {
            VirtualGaugeBehaviour g = nodes.get(i);
            if (g.requestMode.isPassive() && g.patternId != null && level != null)
                dem[i] += ProductionOrderManager.externalDemand(level, g.networkId, g.patternId);
        }

        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (indeg[i] == 0) queue.add(i);

        long[] grossPassive = new long[n];   // assigned gross demand for passive nodes (others stay 0)
        while (!queue.isEmpty()) {
            int i = queue.poll();
            VirtualGaugeBehaviour g = nodes.get(i);
            int mult = Math.max(1, g.unit.toCountMultiplier(g.filter));

            // A passive gauge's gross demand is what its consumers (and orders) accumulated; a manual gauge is a fixed
            // seed at its own target and is never resized.
            long gross = g.requestMode.isPassive() ? dem[i] : (long) g.count * mult;
            if (g.requestMode.isPassive()) grossPassive[i] = Math.max(0, gross);

            // Deficit drives how many crafts to push upstream. Subtract the gap-safe held sum (stock + promised held
            // against the promise→inventory settlement dip), not the live stock/promise pair, or a just-landed item —
            // promise cleared but summary not yet refreshed — would momentarily inflate the deficit and over-request.
            long net = gross - g.effectiveHeld();
            int output = Math.max(1, g.recipeOutput) * craftBatch(g);
            long crafts = (net > 0 && !g.filter.isEmpty()) ? Math.ceilDiv(net, output) : 0;

            for (long[] edge : edges.get(i)) {
                int s = (int) edge[0];
                if (crafts > 0) dem[s] += crafts * edge[1];
                if (--indeg[s] == 0) queue.add(s);
            }
        }

        // Apply: passive gauges take their gross demand as the new raw target (in their unit); manual gauges untouched.
        // The gauge's storage monitor folds passiveDemandTarget into count, holding a transient decrease against its
        // summary-refresh signal (see VirtualGaugeBehaviour). A cycle leaves a node unprocessed (grossPassive 0) → it
        // sizes to 0, the safe degradation.
        for (int i = 0; i < n; i++) {
            VirtualGaugeBehaviour g = nodes.get(i);
            if (!g.requestMode.isPassive()) continue;
            int mult = Math.max(1, g.unit.toCountMultiplier(g.filter));
            long units = grossPassive[i] <= 0 ? 0 : Math.ceilDiv(grossPassive[i], mult);
            g.passiveDemandTarget = (int) Math.min(Integer.MAX_VALUE, units);
        }
    }

    private static int craftBatch(VirtualGaugeBehaviour g) {
        return g.activeCraftingArrangement.isEmpty() ? 1 : Math.max(1, g.craftBatch);
    }
}
