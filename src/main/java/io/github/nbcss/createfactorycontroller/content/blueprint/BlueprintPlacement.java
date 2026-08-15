package io.github.nbcss.createfactorycontroller.content.blueprint;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import net.minecraft.core.BlockPos;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.GhostPreview;
import net.minecraft.client.Minecraft;
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
 * A blueprint placement in progress, client-side.
 */
public class BlueprintPlacement {
    private final String name;
    private final BlueprintStorage.Info info;
    private final byte[] payload;
    private final UUID[] assignments;
    private final List<UUID> presetNetworks;
    @Nullable private final BlockPos boxMin;
    @Nullable private final BlockPos boxMax;
    /** Lazily-built translucent placement preview (reconstructed components + the blueprint's internal wires). */
    @Nullable private GhostPreview ghostPreview;

    public BlueprintPlacement(String name, BlueprintStorage.Info info, byte[] payload) {
        this(name, info, payload, List.of(), null, null);
    }

    private BlueprintPlacement(String name, BlueprintStorage.Info info, byte[] payload,
                               List<UUID> presetNetworks, @Nullable BlockPos boxMin, @Nullable BlockPos boxMax) {
        this.name = name;
        this.info = info;
        this.payload = payload;
        this.assignments = new UUID[info.networkCount()];
        this.presetNetworks = List.copyOf(presetNetworks);
        this.boxMin = boxMin;
        this.boxMax = boxMax;
        for (int i = 0; i < assignments.length && i < this.presetNetworks.size(); i++)
            assignments[i] = this.presetNetworks.get(i);
    }

    public static BlueprintPlacement schematic(String name, BlueprintStorage.Info info, byte[] payload,
                                               List<UUID> networks, BlockPos boxMin, BlockPos boxMax) {
        return new BlueprintPlacement(name, info, payload, networks, boxMin, boxMax);
    }

    @Nullable public BlockPos boxMin() { return boxMin; }

    @Nullable public BlockPos boxMax() { return boxMax; }

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

    /** The translucent placement preview (reconstructed components + internal wires), built once from the payload. */
    public GhostPreview ghostPreview() {
        if (ghostPreview == null) {
            var connection = Minecraft.getInstance().getConnection();
            ghostPreview = GhostPreview.fromBlueprint(payload,
                    connection == null ? null : connection.registryAccess());
        }
        return ghostPreview;
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

    public List<UUID> assignments() {
        List<UUID> list = new ArrayList<>(assignments.length);
        for (UUID assignment : assignments) list.add(assignment);
        return list;
    }

    // ── Feasibility ───────────────────────────────────────────────────────────

    public static final int MATERIAL_HELD_COLOR = 0xFFD7FFA8;
    public static final int MATERIAL_MISSING_COLOR = 0xFFFCA4A4;

    public boolean hasKnownItems() {
        return !info.hasUnknownItems();
    }

    public boolean hasMaterials(Player player) {
        if (!hasKnownItems()) return false;
        if (player.isCreative()) return true;
        Map<Item, Integer> held = inventoryCounts(player);
        for (BlueprintStorage.Material material : info.materials())
            if (!isMaterialSufficient(player, held, material)) return false;
        return true;
    }

    /** Whether {@code held} covers {@code material}; creative always does. */
    public static boolean isMaterialSufficient(Player player, Map<Item, Integer> held,
                                               BlueprintStorage.Material material) {
        return !material.isUnknown() && (player.isCreative()
                || held.getOrDefault(BuiltInRegistries.ITEM.get(material.item()), 0) >= material.count());
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
        return cellFor(placement.pos(), anchor);
    }

    public static VirtualComponentPosition cellFor(VirtualComponentPosition local,
                                                   VirtualComponentPosition anchor) {
        return new VirtualComponentPosition(anchor.x() + local.x(), anchor.y() + local.y());
    }

    public VirtualComponentPosition anchorFor(VirtualComponentPosition cursor) {
        return new VirtualComponentPosition(cursor.x() - (info.width() - 1) / 2,
                cursor.y() - (info.height() - 1) / 2);
    }

    public static Map<Item, Integer> inventoryCounts(Player player) {
        Map<Item, Integer> counts = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    public List<UUID> networkOptions(FactoryControllerMenu menu, Player player) {
        Set<UUID> options = new LinkedHashSet<>(menu.knownNetworks);
        options.addAll(inventoryNetworks(player));
        options.addAll(presetNetworks);
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
