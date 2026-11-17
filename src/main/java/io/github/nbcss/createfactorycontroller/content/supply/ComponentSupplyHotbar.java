package io.github.nbcss.createfactorycontroller.content.supply;

import io.github.nbcss.createfactorycontroller.ClientSettings;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * The player's 9-slot Component Supply hotbar (client-side).
 */
public final class ComponentSupplyHotbar {
    public static final int SIZE = 9;

    /** {@code null} until edited; {@code ""} marks an empty slot. */
    @Nullable
    private static String[] custom;
    private static boolean loaded;

    private ComponentSupplyHotbar() {}

    @Nullable
    public static ComponentSupplyEntry get(int slot) {
        if (slot < 0 || slot >= SIZE) return null;
        String[] ids = custom();
        if (ids == null) {
            List<ComponentSupplyEntry> entries = ComponentSupplyCatalog.entries();
            return slot < entries.size() ? entries.get(slot) : null;
        }
        ResourceLocation id = ResourceLocation.tryParse(ids[slot]);
        return id == null ? null : ComponentSupplyCatalog.byId(id);
    }

    /** Puts {@code entry} in {@code slot}, emptying any other slot that held it. */
    public static void assign(int slot, ComponentSupplyEntry entry) {
        String[] ids = editable();
        String id = entry.id().toString();
        for (int i = 0; i < SIZE; i++)
            if (id.equals(ids[i])) ids[i] = "";
        ids[slot] = id;
        save(ids);
    }

    public static void clear(int slot) {
        String[] ids = editable();
        if (ids[slot].isEmpty()) return;
        ids[slot] = "";
        save(ids);
    }

    /** The stored ids, materialised from the default arrangement on the first edit. */
    private static String[] editable() {
        String[] ids = custom();
        if (ids != null) return ids;
        ids = new String[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ComponentSupplyEntry entry = get(i);
            ids[i] = entry == null ? "" : entry.id().toString();
        }
        custom = ids;
        return ids;
    }

    @Nullable
    private static String[] custom() {
        if (!loaded) {
            custom = read(ClientSettings.componentSupplyHotbar());
            loaded = true;
        }
        return custom;
    }

    @Nullable
    private static String[] read(@Nullable List<String> stored) {
        if (stored == null) return null;
        String[] ids = new String[SIZE];
        Arrays.fill(ids, "");
        for (int i = 0; i < SIZE && i < stored.size(); i++) ids[i] = stored.get(i);
        return ids;
    }

    private static void save(String[] ids) {
        ClientSettings.setComponentSupplyHotbar(Arrays.asList(ids));
    }
}
