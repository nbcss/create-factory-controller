package io.github.nbcss.createfactorycontroller.content.blueprint;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A blueprint placement in progress, client-side. Carries the parsed layout used for the cursor ghost and
 * the feasibility checks, plus the raw file bytes handed to the server once the player confirms.
 *
 * <p>Every check here is advisory — the server re-validates all of it in
 * {@link FactoryControllerBlockEntity#placeBlueprint}.</p>
 */
public class BlueprintPlacement {
    private final String name;
    private final BlueprintStorage.Info info;
    private final byte[] payload;
    /** Placeholder index (0-based) to the network it will be placed on; {@code null} while unassigned. */
    private final UUID[] assignments;

    public BlueprintPlacement(String name, BlueprintStorage.Info info, byte[] payload) {
        this.name = name;
        this.info = info;
        this.payload = payload;
        this.assignments = new UUID[info.networkCount()];
    }

    public String name() {
        return name;
    }

    public BlueprintStorage.Info info() {
        return info;
    }

    public byte[] payload() {
        return payload;
    }

    public int componentCount() {
        return info.placements().size();
    }

    @Nullable
    public UUID assignment(int placeholder) {
        return placeholder >= 0 && placeholder < assignments.length ? assignments[placeholder] : null;
    }

    public void assign(int placeholder, @Nullable UUID network) {
        if (placeholder >= 0 && placeholder < assignments.length) assignments[placeholder] = network;
    }

    public boolean allNetworksAssigned() {
        for (UUID assignment : assignments)
            if (assignment == null) return false;
        return true;
    }

    /** The assignment list in placeholder order, as sent to the server. */
    public List<UUID> assignments() {
        List<UUID> list = new ArrayList<>(assignments.length);
        for (UUID assignment : assignments) list.add(assignment);
        return list;
    }

    // ── Feasibility ───────────────────────────────────────────────────────────

    /** Compact-font tints of a material count, shared by the library and placement previews. */
    public static final int MATERIAL_HELD_COLOR = 0xFFD7FFA8;
    public static final int MATERIAL_MISSING_COLOR = 0xFFFCA4A4;

    /** Creative players place freely, mirroring the item consumption skip in {@code attachComponent}. */
    public boolean hasMaterials(Player player) {
        if (player.isCreative()) return true;
        Map<Item, Integer> held = inventoryCounts(player);
        for (BlueprintStorage.Material material : info.materials())
            if (!isMaterialSufficient(player, held, material)) return false;
        return true;
    }

    /** Whether {@code held} covers {@code material}; creative always does. */
    public static boolean isMaterialSufficient(Player player, Map<Item, Integer> held,
                                               BlueprintStorage.Material material) {
        return player.isCreative()
                || held.getOrDefault(BuiltInRegistries.ITEM.get(material.item()), 0) >= material.count();
    }

    public boolean hasCapacity(FactoryControllerMenu menu) {
        return menu.components.size() + componentCount() <= FactoryControllerBlockEntity.maxComponents();
    }

    /** Whether anchoring here lands every component on a free, in-board cell. */
    public boolean fits(VirtualComponentPosition anchor, FactoryControllerMenu menu) {
        for (BlueprintStorage.Placement placement : info.placements())
            if (!cellFree(cellFor(placement, anchor), menu)) return false;
        return true;
    }

    public boolean cellFree(VirtualComponentPosition cell, FactoryControllerMenu menu) {
        return !FactoryControllerBlockEntity.isOutBoard(cell) && menu.componentAt(cell) == null;
    }

    public static VirtualComponentPosition cellFor(BlueprintStorage.Placement placement,
                                                   VirtualComponentPosition anchor) {
        return new VirtualComponentPosition(anchor.x() + placement.pos().x(), anchor.y() + placement.pos().y());
    }

    /**
     * The anchor (top-left cell) that centres the blueprint under {@code cursor}. Odd spans centre exactly;
     * even spans bias toward the top-left, so the cursor always sits on a cell the blueprint occupies.
     */
    public VirtualComponentPosition anchorFor(VirtualComponentPosition cursor) {
        return new VirtualComponentPosition(cursor.x() - (info.width() - 1) / 2,
                cursor.y() - (info.height() - 1) / 2);
    }

    /** Item totals keyed by item, so stacks differing only in components still count toward a material. */
    public static Map<Item, Integer> inventoryCounts(Player player) {
        Map<Item, Integer> counts = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    /**
     * Networks a placeholder may be bound to: the controller's own, then any further network found on a
     * tuned component item in the player's inventory. Binding one of the trailing entries makes the
     * controller learn that network, which is what the green "+" marks.
     */
    public static List<UUID> networkOptions(FactoryControllerMenu menu, Player player) {
        Set<UUID> options = new LinkedHashSet<>(menu.knownNetworks);
        options.addAll(inventoryNetworks(player));
        return List.copyOf(options);
    }

    /** Networks carried on tuned component items in the player's inventory. */
    public static Set<UUID> inventoryNetworks(Player player) {
        Set<UUID> networks = new LinkedHashSet<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ComponentRegistry.containsNetworkItem(stack)) continue;
            if (!LogisticallyLinkedBlockItem.isTuned(stack)) continue;
            UUID network = LogisticallyLinkedBlockItem.networkFromStack(stack);
            if (network != null) networks.add(network);
        }
        return networks;
    }
}
