package io.github.nbcss.createfactorycontroller.content.production;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Server-side ingredient pre-check for a Stock-Keeper order containing Production Patterns. Builds the recipe DAG
 * reachable from the ordered patterns (intra-controller {@code targetedBy} connections), then totals raw-ingredient
 * demand and reports the raw ingredients the network can't supply.
 *
 * <p>The walk descends into passive+connected gauges (which scale production on demand) and stops at leaves —
 * non-passive gauges, passive gauges without ingredients, or items with no producer — where the network must
 * already hold the item. Demand is accumulated <b>per item</b> across every branch and the DAG is processed in
 * topological order (a product is sized only once all of its consumers' demand is known), so an item needed by
 * several recipes is crafted in one rounded batch rather than over-counted per branch.</p>
 *
 * <p>Performance: each pattern's gauge is found in O(1) via {@link OrderableGaugeRegistry#locate}; the network's
 * item {@link InventorySummary} is fetched once and each item's stock is read at most once.</p>
 */
public final class IngredientDemandResolver {

    /** One ordered pattern: the gauge it targets and how many of the produced item the player wants. */
    public record PatternDemand(UUID patternId, int demand) {}

    /** A real (non-pattern) item also in the same order: it will ship out, so it's subtracted from available stock
     *  before the ingredient demand is computed. */
    public record Reserved(ItemStack stack, int amount) {}

    /** A raw ingredient the network can't supply enough of: how much is in stock vs. how much the order needs. */
    public record Shortfall(ItemStack item, int inStock, int required) {}

    /** {@code patternMissing} true means a pattern's gauge couldn't be found (controller unloaded / removed). */
    public record Result(boolean patternMissing, List<Shortfall> shortfalls) {}

    private IngredientDemandResolver() {}

    public static Result resolve(MinecraftServer server, UUID network, List<PatternDemand> demands,
                                 List<Reserved> reserved, long now) {
        Graph g = new Graph(network, LogisticsManager.getSummaryOfNetwork(network, true), reserved);

        for (PatternDemand pd : demands) {
            if (pd.demand() <= 0) continue;
            OrderableGaugeRegistry.Located loc = OrderableGaugeRegistry.locate(network, pd.patternId(), now);
            FactoryControllerBlockEntity controller = controllerAt(server, loc);
            VirtualGaugeBehaviour gauge = controller == null ? null : findGauge(controller, pd.patternId());
            if (gauge == null) return new Result(true, List.of());

            int rootId = g.intern(gauge.filter);
            g.explore(rootId, gauge, controller, true);   // the order always produces the root, even with no recipe
            g.addDemand(rootId, pd.demand());
        }
        return new Result(false, g.solve());
    }

    private static FactoryControllerBlockEntity controllerAt(MinecraftServer server, OrderableGaugeRegistry.Located loc) {
        if (loc == null) return null;
        ServerLevel level = server.getLevel(loc.dim());
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(loc.pos());
        return be instanceof FactoryControllerBlockEntity fc ? fc : null;
    }

    private static VirtualGaugeBehaviour findGauge(FactoryControllerBlockEntity controller, UUID patternId) {
        for (VirtualComponentBehaviour c : controller.components.values())
            if (c instanceof VirtualGaugeBehaviour g && patternId.equals(g.patternId)) return g;
        return null;
    }

    /**
     * The recipe DAG: one node per distinct item. A node is a <em>producer</em> (passive+connected gauge, or an
     * ordered root) with an output size + ingredient edges, or a <em>leaf</em> (must come from stock). Built lazily
     * by {@link #explore}, then drained in topological order by {@link #solve} accumulating per-item demand.
     */
    private static final class Graph {
        private final UUID network;
        private final InventorySummary summary;
        private final List<Reserved> reserved;

        private final List<ItemStack> items = new ArrayList<>();
        private final List<Long> stock = new ArrayList<>();          // network stock, read once per item
        private final List<Long> seed = new ArrayList<>();           // demand injected directly (ordered roots)
        private final List<VirtualGaugeBehaviour> producer = new ArrayList<>();   // null = leaf
        private final List<Integer> outputPerCraft = new ArrayList<>();
        private final List<List<long[]>> edges = new ArrayList<>();   // [childId, perCraftQty]
        private final List<Boolean> explored = new ArrayList<>();

        Graph(UUID network, InventorySummary summary, List<Reserved> reserved) {
            this.network = network;
            this.summary = summary;
            this.reserved = reserved;
        }

        int intern(ItemStack item) {
            for (int i = 0; i < items.size(); i++)
                if (ItemStack.isSameItemSameComponents(items.get(i), item)) return i;
            long s = FluidCompat.isFluidFilter(item)
                ? LogisticsManager.getStockOf(network, item, null)
                : summary.getCountOf(item);
            s = Math.max(0, s - reservedOf(item));   // items also shipping out in this order aren't available
            items.add(item.copy());
            stock.add(s);
            seed.add(0L);
            producer.add(null);
            outputPerCraft.add(0);
            edges.add(new ArrayList<>());
            explored.add(false);
            return items.size() - 1;
        }

        void addDemand(int id, int amount) {
            seed.set(id, seed.get(id) + amount);
        }

        private long reservedOf(ItemStack item) {
            long sum = 0;
            for (Reserved r : reserved)
                if (ItemStack.isSameItemSameComponents(r.stack(), item)) sum += r.amount();
            return sum;
        }

        /** Classify item {@code id} produced by {@code source} and recurse into its ingredients. {@code root} forces
         *  producer status (the order makes the output regardless of the gauge's request mode). */
        void explore(int id, VirtualGaugeBehaviour source, FactoryControllerBlockEntity controller, boolean root) {
            if (explored.get(id)) return;
            explored.set(id, true);

            boolean producible = root || (source != null && source.requestMode.isPassive() && !source.targetedBy().isEmpty());
            if (!producible) return;   // leaf — stays producer=null

            int batch = source.activeCraftingArrangement.isEmpty() ? 1 : Math.max(1, source.craftBatch);
            producer.set(id, source);
            outputPerCraft.set(id, Math.max(1, source.recipeOutput) * batch);

            for (var e : source.targetedBy().entrySet()) {
                if (!(e.getValue() instanceof LogisticsConnection conn)) continue;   // ingredient wires only
                VirtualGaugeBehaviour src = controller.components.get(e.getKey()) instanceof VirtualGaugeBehaviour gg ? gg : null;
                ItemStack ingredient = src == null ? ItemStack.EMPTY : src.filter;
                if (ingredient.isEmpty()) continue;
                int childId = intern(ingredient);
                edges.get(id).add(new long[]{childId, (long) conn.amount() * batch});
                explore(childId, src, controller, false);
            }
        }

        /** Topologically drains accumulated demand product→ingredient, returning the leaf shortfalls. */
        List<Shortfall> solve() {
            int n = items.size();
            long[] dem = new long[n];
            for (int id = 0; id < n; id++) dem[id] = seed.get(id);

            int[] indeg = new int[n];
            for (int id = 0; id < n; id++)
                for (long[] edge : edges.get(id)) indeg[(int) edge[0]]++;

            Deque<Integer> queue = new ArrayDeque<>();
            for (int id = 0; id < n; id++) if (indeg[id] == 0) queue.add(id);

            List<Shortfall> shortfalls = new ArrayList<>();
            while (!queue.isEmpty()) {
                int id = queue.poll();
                long net = dem[id] - stock.get(id);
                if (producer.get(id) != null) {                 // producer (or root): size crafts, push to ingredients
                    long crafts = net > 0 ? Math.ceilDiv(net, outputPerCraft.get(id)) : 0;
                    for (long[] edge : edges.get(id)) {
                        int child = (int) edge[0];
                        if (crafts > 0) dem[child] += crafts * edge[1];
                        if (--indeg[child] == 0) queue.add(child);
                    }
                } else if (net > 0) {                            // leaf short of stock
                    shortfalls.add(new Shortfall(items.get(id).copy(), clampInt(stock.get(id)), clampInt(dem[id])));
                }
            }
            return shortfalls;
        }

        private static int clampInt(long v) {
            return (int) Math.min(v, Integer.MAX_VALUE);
        }
    }
}
