package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.RepackagedCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central registry for virtual components.
 */
public final class ComponentRegistry {

    private static final Map<String, VirtualComponentBehaviour> TYPE_REGISTRY = new HashMap<>();

    /**
     * Properties of an item that can be put into the factory controller.
     * @param requireNetwork Need to connect to a logistics network (e.g. gauges).
     */
    private record AcceptedItem(boolean requireNetwork) {}

    /** Deserializes a component of a known type from its NBT tag. */
    @FunctionalInterface
    public interface ComponentFactory {
        VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                          CompoundTag tag, HolderLookup.Provider registries);
    }

    private static final Map<ResourceLocation, AcceptedItem> ACCEPTED_ITEMS = new HashMap<>();
    private static final Map<ResourceLocation, ComponentFactory> TYPE_FACTORIES = new HashMap<>();

    /** Per-gauge-item stock type. Absent ⇒ {@link GaugeType#ITEM} (Create's factory gauge); only the optional
     *  fluid/energy gauges register a non-item type. */
    private static final Map<ResourceLocation, GaugeType> GAUGE_TYPES = new HashMap<>();

    static {
        // Create's factory gauge → virtual gauge component (item stock).
        ACCEPTED_ITEMS.put(com.simibubi.create.AllBlocks.FACTORY_GAUGE.getId(), new AcceptedItem(true));
        registerType(VirtualGaugeBehaviour.TYPE_ID, VirtualGaugeBehaviour::fromNBT);

        // Create's redstone link → virtual redstone-link component (no logistics network required).
        ACCEPTED_ITEMS.put(com.simibubi.create.AllBlocks.REDSTONE_LINK.getId(), new AcceptedItem(false));
        registerType(VirtualRedstoneLinkBehaviour.TYPE_ID, VirtualRedstoneLinkBehaviour::fromNBT);

        // Create: Repackaged's Fluid Gauge → a virtual gauge with FLUID stock type. Registered only when the addon is
        // present; it's a network-tuned gauge, so it stays network-requiring. Reuses the gauge component (and its
        // TYPE_ID factory) — the stock type is derived from the item id via typeOf().
        if (RepackagedCompat.isLoaded()) {
            ACCEPTED_ITEMS.put(RepackagedCompat.FLUID_GAUGE, new AcceptedItem(false));
            GAUGE_TYPES.put(RepackagedCompat.FLUID_GAUGE, GaugeType.FLUID);
        }
    }

    /** The stock type a placed gauge of {@code itemId} manages (defaults to {@link GaugeType#ITEM}). */
    public static GaugeType typeOf(ResourceLocation itemId) {
        return GAUGE_TYPES.getOrDefault(itemId, GaugeType.ITEM);
    }

    /** Whether attaching {@code itemId} needs a controller logistics network (gauges do; redstone links don't). */
    public static boolean needsNetwork(ResourceLocation itemId) {
        return ACCEPTED_ITEMS.get(itemId).requireNetwork;
    }

    /**
     * Builds a fresh component for a freshly-placed item. {@code networkId} is the resolved logistics network for
     * gauges (ignored by networkless components).
     */
    public static VirtualComponentBehaviour createFromItem(FactoryControllerBlockEntity controller,
                                                           VirtualComponentPosition pos, ResourceLocation itemId,
                                                           @Nullable UUID networkId) {
        if (com.simibubi.create.AllBlocks.REDSTONE_LINK.getId().equals(itemId))
            return new VirtualRedstoneLinkBehaviour(controller, pos, itemId);
        return new VirtualGaugeBehaviour(controller, pos, networkId, itemId);
    }

    // ── Item acceptance ───────────────────────────────────────────────────────

    public static boolean contains(ResourceLocation id) {
        return ACCEPTED_ITEMS.containsKey(id);
    }

    /** A stack is a valid component if its item id is registered. */
    public static boolean containsItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * A stack is a <i>network</i> component if it is a registered component that attaches to a logistics
     * network (a gauge) — i.e. not a networkless item like the redstone link. The network selector only
     * tunes / highlights for these, so a held redstone link is left alone.
     */
    public static boolean containsNetworkItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return contains(id) && needsNetwork(id);
    }

    // ── Type dispatch ─────────────────────────────────────────────────────────

    public static void registerType(ResourceLocation typeId, ComponentFactory factory) {
        TYPE_FACTORIES.put(typeId, factory);
    }

    /**
     * Reconstructs a component from NBT, dispatching on the stored {@code "Type"} id.
     * Returns {@code null} for an unknown type (e.g. a component from a mod no longer present).
     */
    @Nullable
    public static VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                    CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation typeId = ResourceLocation.tryParse(tag.getString("Type"));
        ComponentFactory factory = typeId == null ? null : TYPE_FACTORIES.get(typeId);
        if (factory == null) return null;
        return factory.fromNBT(controller, tag, registries);
    }
}
