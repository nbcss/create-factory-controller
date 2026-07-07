package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.RepackagedCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Central registry for virtual components.
 */
public final class ComponentRegistry {

    /** Registration order preserved ({@link LinkedHashMap}) so the allowed-components listing is stable. */
    private static final Map<String, VirtualComponentBehaviour.Type> TYPE_REGISTRY = new LinkedHashMap<>();
    private static final Map<ResourceLocation, VirtualComponentBehaviour.Type> ITEM_REGISTRY = new HashMap<>();

    public static void registerType(VirtualComponentBehaviour.Type type) {
        TYPE_REGISTRY.put(type.id(), type);
        for (ResourceLocation item : type.items())
            registerItem(item, type);
    }

    public static void registerItem(ResourceLocation item, VirtualComponentBehaviour.Type type) {
        ITEM_REGISTRY.put(item, type);
    }

    static {
        registerType(VirtualGaugeBehaviour.TYPE);
        registerType(VirtualRedstoneLinkBehaviour.TYPE);
        registerType(LogicalTubeBehaviour.TYPE);

        // Create: Repackaged's Fluid Gauge is registered only when the addon is present.
        if (RepackagedCompat.isLoaded())
            registerType(FluidGaugeBehaviour.TYPE);
    }

    /** Whether attaching {@code itemId} needs a controller logistics network (gauges do; redstone links don't). */
    public static boolean needsNetwork(ResourceLocation itemId) {
        VirtualComponentBehaviour.Type type = ITEM_REGISTRY.get(itemId);
        return type != null && type.isRequireNetwork();
    }

    /**
     * Builds a fresh component for a freshly-placed item. {@code networkId} is the resolved logistics network for
     * gauges (ignored by networkless components).
     */
    public static VirtualComponentBehaviour createFromItem(FactoryControllerBlockEntity controller,
                                                           VirtualComponentPosition pos,
                                                           Item item,
                                                           @Nullable UUID networkId) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        VirtualComponentBehaviour.Type type = ITEM_REGISTRY.get(itemId);
        return type == null ? null : type.create(controller, pos, item, networkId);
    }

    // ── Item acceptance ───────────────────────────────────────────────────────

    public static boolean contains(ResourceLocation id) {
        return ITEM_REGISTRY.containsKey(id);
    }

    public static Collection<VirtualComponentBehaviour.Type> types() {
        return TYPE_REGISTRY.values();
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

    /**
     * Reconstructs a component from NBT, dispatching on the stored {@code "Type"} id.
     * Returns {@code null} for an unknown type (e.g. a component from a mod no longer present).
     */
    @Nullable
    public static VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                    CompoundTag tag, HolderLookup.Provider registries) {
        String typeId = tag.getString("Type");
        VirtualComponentBehaviour.Type type = TYPE_REGISTRY.get(typeId);
        return type == null ? null : type.fromNBT(controller, tag, registries);
    }

    // ── Client sync (binary) ──────────────────────────────────────────────────

    public static void writeComponent(net.minecraft.network.RegistryFriendlyByteBuf buf, VirtualComponentBehaviour c) {
        buf.writeUtf(c.typeId());
        c.writeClient(buf);
    }

    /** Reconstructs a client component from {@link #writeComponent}. A null (unknown type) desyncs the rest of the
     *  buffer, but the sync path is only ever same-mod-version client↔server, so every written type is known here. */
    @Nullable
    public static VirtualComponentBehaviour readComponent(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        VirtualComponentBehaviour.Type type = TYPE_REGISTRY.get(buf.readUtf());
        return type == null ? null : type.fromClient(buf);
    }
}
