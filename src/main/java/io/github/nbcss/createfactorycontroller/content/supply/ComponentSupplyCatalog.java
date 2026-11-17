package io.github.nbcss.createfactorycontroller.content.supply;

import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every item the controller accepts as a component, sorted by type order. */
public final class ComponentSupplyCatalog {
    private static final Comparator<VirtualComponentBehaviour.Type> TYPE_ORDER =
            Comparator.comparing(VirtualComponentBehaviour.Type::order)
                    .thenComparing(VirtualComponentBehaviour.Type::id);

    private static volatile List<ComponentSupplyEntry> entries = List.of();
    private static volatile Map<ResourceLocation, ComponentSupplyEntry> byId = Map.of();
    // ComponentRegistry.init() registers late types (CFL), so rebuild when the type count changes.
    private static volatile int builtForTypeCount = -1;

    private ComponentSupplyCatalog() {}

    public static List<ComponentSupplyEntry> entries() {
        refresh();
        return entries;
    }

    @Nullable
    public static ComponentSupplyEntry byId(ResourceLocation id) {
        refresh();
        return byId.get(id);
    }

    /** Whether {@code item} is a component item. */
    public static boolean contains(Item item) {
        return byId(BuiltInRegistries.ITEM.getKey(item)) != null;
    }

    private static void refresh() {
        if (ComponentRegistry.types().size() != builtForTypeCount) rebuild();
    }

    private static synchronized void rebuild() {
        int typeCount = ComponentRegistry.types().size();
        if (typeCount == builtForTypeCount) return;
        List<VirtualComponentBehaviour.Type> types = new ArrayList<>(ComponentRegistry.types());
        types.sort(TYPE_ORDER);
        Map<ResourceLocation, ComponentSupplyEntry> map = new LinkedHashMap<>();
        for (VirtualComponentBehaviour.Type type : types) {
            for (ResourceLocation id : type.items()) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == Items.AIR || map.containsKey(id)) continue;
                map.put(id, new ComponentSupplyEntry(item, type));
            }
        }
        byId = Map.copyOf(map);
        entries = List.copyOf(map.values());
        builtForTypeCount = typeCount;
    }
}
