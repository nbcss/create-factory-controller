package io.github.nbcss.createfactorycontroller.content.component;

import com.simibubi.create.AllBlocks;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Central registry for virtual components.
 */
public final class ComponentRegistry {

    private ComponentRegistry() {}

    /** Deserializes a component of a known type from its NBT tag. */
    @FunctionalInterface
    public interface ComponentFactory {
        VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                          CompoundTag tag, HolderLookup.Provider registries);
    }

    private static final Set<ResourceLocation> ITEM_IDS = new HashSet<>();
    private static final Map<ResourceLocation, ComponentFactory> TYPE_FACTORIES = new HashMap<>();

    /** Item ids that don't require a logistics network to attach (e.g. the redstone link). */
    private static final Set<ResourceLocation> NETWORKLESS_ITEMS = new HashSet<>();

    static {
        // Create's factory gauge → virtual gauge component.
        ITEM_IDS.add(AllBlocks.FACTORY_GAUGE.getId());
        registerType(VirtualGaugeBehaviour.TYPE_ID, VirtualGaugeBehaviour::fromNBT);

        // Create's redstone link → virtual redstone-link component (no logistics network required).
        ITEM_IDS.add(AllBlocks.REDSTONE_LINK.getId());
        NETWORKLESS_ITEMS.add(AllBlocks.REDSTONE_LINK.getId());
        registerType(VirtualRedstoneLinkBehaviour.TYPE_ID, VirtualRedstoneLinkBehaviour::fromNBT);
    }

    /** Whether attaching {@code itemId} needs a controller logistics network (gauges do; redstone links don't). */
    public static boolean needsNetwork(ResourceLocation itemId) {
        return !NETWORKLESS_ITEMS.contains(itemId);
    }

    /**
     * Builds a fresh component for a freshly-placed item. {@code networkId} is the resolved logistics network for
     * gauges (ignored by networkless components).
     */
    public static VirtualComponentBehaviour createFromItem(FactoryControllerBlockEntity controller,
                                                           VirtualPanelPosition pos, ResourceLocation itemId,
                                                           @Nullable UUID networkId) {
        if (AllBlocks.REDSTONE_LINK.getId().equals(itemId))
            return new VirtualRedstoneLinkBehaviour(controller, pos, itemId);
        return new VirtualGaugeBehaviour(controller, pos, networkId, itemId);
    }

    // ── Item acceptance ───────────────────────────────────────────────────────

    public static void register(ResourceLocation id) {
        ITEM_IDS.add(id);
    }

    public static boolean contains(ResourceLocation id) {
        return ITEM_IDS.contains(id);
    }

    /** A stack is a valid component if its item id is registered. */
    public static boolean containsItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
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
